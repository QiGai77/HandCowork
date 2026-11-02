package app.xuezhang.engine

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import app.xuezhang.audio.RecordService
import app.xuezhang.data.Card
import app.xuezhang.data.Course
import app.xuezhang.data.Lecture
import app.xuezhang.data.Mnemonic
import app.xuezhang.data.Node
import app.xuezhang.data.Segment
import app.xuezhang.data.Store
import app.xuezhang.net.Api
import app.xuezhang.net.ApiException
import app.xuezhang.ui.Island
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.sqrt

object Live {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    const val RATE = 16000

    val lectureId = mutableStateOf<String?>(null)
    val running = mutableStateOf(false)
    val paused = mutableStateOf(false)
    val elapsed = mutableLongStateOf(0L)
    val level = mutableFloatStateOf(0f)
    val levels = mutableStateListOf<Float>().apply { repeat(48) { add(0f) } }
    val segments = mutableStateListOf<Segment>()
    val processing = mutableIntStateOf(0)
    val error = mutableStateOf<String?>(null)
    val generating = mutableStateOf(false)
    val genStep = mutableIntStateOf(0)
    val genError = mutableStateOf<String?>(null)
    val genPct = androidx.compose.runtime.mutableFloatStateOf(0f)
    val genDetail = mutableStateOf("")
    val genStream = mutableStateOf("")
    /** 本次生成选的详略（null 用设置里的默认）。 */
    @Volatile var genDepth: Int? = null
    /** 录播课导入自己会在截帧之后再画导图，这里关掉自动导图。 */
    @Volatile var autoMindMap = true

    /** 长整理稿按行切块，每块不超过 max 字。 */
    fun chunkTranscript(t: String, max: Int): List<String> {
        if (t.length <= max) return listOf(t)
        val out = mutableListOf<String>(); val sb = StringBuilder()
        t.lines().forEach { l -> if (sb.length + l.length > max && sb.isNotEmpty()) { out.add(sb.toString()); sb.clear() }; sb.append(l).append('\n') }
        if (sb.isNotBlank()) out.add(sb.toString())
        return out
    }

    /** 合并分段生成的笔记：sections 顺接；节点挂到第一段的根下；口诀/复习卡去重。 */
    fun mergeNotes(parts: List<JSONObject>): JSONObject {
        if (parts.size == 1) return parts[0]
        val out = JSONObject().put("title", parts.firstNotNullOfOrNull { it.optString("title").takeIf { t -> t.isNotBlank() } } ?: "")
        val secs = JSONArray(); val mn = JSONArray(); val cards = JSONArray(); val nodes = JSONArray()
        val seenM = HashSet<String>(); val seenC = HashSet<String>()
        parts.forEachIndexed { pi, p ->
            p.optJSONArray("sections")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { secs.put(it) } }
            p.optJSONArray("mnemonics")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.takeIf { seenM.add(it.optString("title")) }?.let { mn.put(it) } }
            p.optJSONArray("cards")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.takeIf { seenC.add(it.optString("q")) }?.let { cards.put(it) } }
            p.optJSONArray("nodes")?.let { a ->
                val map = HashMap<Int, Int>()
                for (i in 0 until a.length()) a.optJSONObject(i)?.let { n ->
                    val par = n.optInt("parent", -1)
                    if (pi > 0 && par < 0 && n.optString("label") == nodes.optJSONObject(0)?.optString("label")) { map[i] = 0; return@let }
                    val np = if (par < 0) (if (nodes.length() == 0) -1 else 0) else map[par] ?: 0
                    map[i] = nodes.length(); nodes.put(JSONObject(n.toString()).put("parent", np))
                }
            }
        }
        return out.put("sections", secs).put("mnemonics", mn).put("cards", cards).put("nodes", nodes)
    }
    val currentPage = mutableIntStateOf(0)

    private var course: Course? = null
    private var appCtx: Context? = null
    private var chunks = Channel<Pair<File, Long>>(Channel.UNLIMITED)
    private var markNext = false

    fun start(ctx: Context, c: Course) {
        if (running.value) return
        appCtx = ctx.applicationContext
        course = c
        val lec = Lecture(courseId = c.id, title = "${c.name} · 第 ${Store.lectures.count { it.courseId == c.id } + 1} 讲", status = 0)
        Store.upsertLecture(lec)
        lectureId.value = lec.id; discardPending = false; discarded.value = null
        segments.clear(); elapsed.longValue = 0; error.value = null; paused.value = false
        currentPage.intValue = c.slides.firstOrNull()?.page ?: 0
        chunks = Channel(Channel.UNLIMITED)
        running.value = true
        ctx.startForegroundService(Intent(ctx, RecordService::class.java))
        scope.launch { consume() }
    }

    fun togglePause() { paused.value = !paused.value; Island.show(if (paused.value) "已暂停录音" else "继续录音", Island.Kind.Rec) }

    fun markExam() {
        val i = segments.lastIndex
        if (i >= 0) { segments[i] = segments[i].copy(exam = true); persist() } else markNext = true
        Island.show("已标为考点", Island.Kind.Exam)
    }

    /** 录音不足这个时长视为误触：不保存这节课、不生成笔记。 */
    const val MIN_RECORD_MS = 30_000L
    fun tooShort(recordedMs: Long): Boolean = recordedMs < MIN_RECORD_MS
    /** 被作废的那节课 id（界面据此直接返回而不是进入「生成笔记」）。 */
    val discarded = mutableStateOf<String?>(null)
    @Volatile private var discardPending = false

    fun stop(ctx: Context) {
        if (!running.value) return
        val ms = elapsed.longValue
        if (tooShort(ms)) {
            discardPending = true
            discarded.value = lectureId.value
            Log.ok("上课", "录制 ${ms / 1000} 秒，不足 30 秒，按误触作废（不保存、不生成笔记）")
            android.widget.Toast.makeText(ctx.applicationContext, "录制不足30秒，已作废", android.widget.Toast.LENGTH_SHORT).show()
        }
        running.value = false
        ctx.stopService(Intent(ctx, RecordService::class.java))
    }

    // ---------------- recording (called from service thread) ----------------
    @SuppressLint("MissingPermission")
    fun recordLoop() {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(min, RATE))
        val buf = ShortArray(RATE / 10)
        val pcm = ByteArrayOutputStream()
        var chunkStart = 0L
        var silentMs = 0L
        try {
            rec.startRecording()
            while (running.value) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                if (paused.value) { level.floatValue = 0f; continue }
                var sum = 0.0; for (i in 0 until n) sum += buf[i] * buf[i].toDouble()
                val rms = sqrt(sum / n).toFloat()
                val lv = (rms / 3000f).coerceIn(0f, 1f)
                level.floatValue = lv
                levels.removeAt(0); levels.add(lv)
                for (i in 0 until n) { pcm.write(buf[i].toInt() and 0xff); pcm.write((buf[i].toInt() shr 8) and 0xff) }
                elapsed.longValue += n * 1000L / RATE
                silentMs = if (rms < 350) silentMs + n * 1000L / RATE else 0
                val len = pcm.size() / 2 * 1000L / RATE
                val target = Store.settings.value.chunkSec * 1000L
                if ((len >= target && silentMs >= 250) || len >= target + 6000) {
                    flush(pcm, chunkStart); chunkStart = elapsed.longValue
                }
            }
        } catch (e: Exception) {
            error.value = "录音失败：${e.message}"
        } finally {
            runCatching { rec.stop() }; rec.release()
            if (pcm.size() > RATE) flush(pcm, chunkStart)
            chunks.close()
        }
    }

    private fun flush(pcm: ByteArrayOutputStream, startMs: Long) {
        val dir = File(appCtx!!.cacheDir, "chunks").apply { mkdirs() }
        val f = File(dir, "c_${System.currentTimeMillis()}.wav")
        writeWav(f, pcm.toByteArray()); pcm.reset()
        processing.intValue++
        chunks.trySend(f to startMs)
    }

    private fun writeWav(f: File, data: ByteArray) {
        RandomAccessFile(f, "rw").use { r ->
            fun i32(v: Int) { r.write(v and 0xff); r.write(v shr 8 and 0xff); r.write(v shr 16 and 0xff); r.write(v shr 24 and 0xff) }
            fun i16(v: Int) { r.write(v and 0xff); r.write(v shr 8 and 0xff) }
            r.writeBytes("RIFF"); i32(36 + data.size); r.writeBytes("WAVE"); r.writeBytes("fmt ")
            i32(16); i16(1); i16(1); i32(RATE); i32(RATE * 2); i16(2); i16(16); r.writeBytes("data"); i32(data.size); r.write(data)
        }
    }

    // ---------------- processing ----------------
    private suspend fun consume() {
        for ((f, start) in chunks) {
            try { handle(f, start) } catch (e: Exception) {
                error.value = e.message; Island.show(e.message ?: "处理失败", Island.Kind.Warn)
            } finally { processing.intValue--; f.delete() }
        }
        // recording ended
        finishLecture()
    }

    private fun handle(f: File, start: Long) {
        val c = course ?: return
        if (discardPending) return
        val raw = Api.transcribe(f, c.hotwords.joinToString("，"))
        if (raw.isBlank() || raw.length < 2) return
        val ctxText = segments.takeLast(3).joinToString("\n") { it.voice }
        val slides = c.slides.joinToString("\n") { "P.${it.page} ${it.title} ${it.formulas.joinToString(" ")}" }.take(2500)
        val user = buildString {
            append("课程：${c.name}\n热词：${c.hotwords.joinToString("、")}\n")
            if (slides.isNotBlank()) append("PPT 页：\n$slides\n当前大约在 P.${currentPage.intValue}\n")
            if (ctxText.isNotBlank()) append("前文（已整理）：\n$ctxText\n")
            append("\n本段语音识别原文：\n$raw")
        }
        val o = Api.chatJson(Prompts.get("segment"), user)
        val page = o.optInt("page", 0).let { if (it > 0) it else currentPage.intValue }
        if (page > 0) currentPage.intValue = page
        val fills = o.optInt("fills", 0)
        val seg = Segment(startMs = start, page = page, raw = raw, voice = o.optString("voice", raw),
            senior = o.optString("senior").let { if (Prompts.isSkip(it)) "" else it }, fillers = o.optInt("fillers"), repeats = o.optInt("repeats"),
            exam = o.optBoolean("exam") || markNext, offTopic = Prompts.offTopic(o) && !(o.optBoolean("exam") || markNext))
        markNext = false
        segments.add(seg); persist()
        when {
            seg.exam -> Island.show("老师划重点了 · 已记为考点", Island.Kind.Exam)
            fills > 0 && Store.settings.value.confirmFill -> Island.show("补全了 $fills 处没听清的字", Island.Kind.Fill)
        }
    }

    private fun persist() {
        val id = lectureId.value ?: return
        val l = Store.lecture(id) ?: return
        Store.upsertLecture(l.copy(segments = segments.toList(), durationMs = elapsed.longValue))
    }

    private suspend fun finishLecture() {
        val id = lectureId.value ?: return
        if (discardPending) {
            discardPending = false
            Store.deleteLecture(id); segments.clear()
            Island.show("录制不足30秒，已作废", Island.Kind.Info)
            return
        }
        discarded.value = null
        persist()
        generate(id)
    }

    fun regenerate(id: String, depth: Int? = null) { genDepth = depth; scope.launch { generate(id) } }
    suspend fun generateNow(id: String) = generate(id)

    private suspend fun generate(id: String) {
        val l = Store.lecture(id) ?: return
        val c = Store.course(l.courseId)
        if (l.segments.isEmpty()) { Store.upsertLecture(l.copy(status = 3)); genError.value = "这节课没有识别到内容"; return }
        generating.value = true; genError.value = null; genStep.intValue = 0; genPct.floatValue = 0.05f; genDetail.value = "整理最后几段录音"; genStream.value = ""
        Store.upsertLecture(l.copy(status = 1))
        val job = AiProgress.start("notes:$id", "整理笔记")
        job.phase("过滤语气词、对齐页码", 0.12f, 1500)
        try {
            for (s in 1..2) { delay(400); genStep.intValue = s }
            val slideIdx = c?.slides?.joinToString("\n") { "P.${it.page} ${it.title}" }?.take(3000).orEmpty()
            val transcript = l.segments.filterNot { it.offTopic }.joinToString("\n") { s ->
                "[${fmt(s.startMs)}${if (s.page > 0) " P.${s.page}" else ""}${if (s.exam) " ★考点" else ""}] ${s.voice}"
            }.takeLast(24000)
            genStep.intValue = 3
            val depth = genDepth ?: Store.settings.value.noteDepth
            val stem = StemUi.isStem(c?.name)
            val chunks = chunkTranscript(transcript, if (depth == 0) 14000 else 9000)
            val head = "课程：${c?.name ?: ""}\n热词：${c?.hotwords?.joinToString("、") ?: ""}\n" + (if (slideIdx.isNotBlank()) "课件页目录：\n$slideIdx\n" else "")
            val expect = if (depth == 0) 2500.0 else 6000.0
            val t0 = System.currentTimeMillis()
            Log.ok("整理笔记", "开始 · ${if (depth == 0) "简单" else "详细"} · ${chunks.size} 段 · ${transcript.length} 字")
            val parts = chunks.mapIndexed { i, ch ->
                genDetail.value = if (chunks.size > 1) "提炼考点 · 第 ${i + 1}/${chunks.size} 段" else "提炼考点、公式和助记"
                // 每段一个阶段：流式字数推进；首字前按预计耗时估算（深度思考模型可能很久才吐字）
                job.phase(genDetail.value, (0.15f + 0.75f * (i + 1) / chunks.size), if (depth == 0) 45_000 else 90_000)
                val r = withContext(Dispatchers.IO) {
                    Api.gen(Prompts.notesFor(depth, i + 1, chunks.size, stem), listOf(Api.Part(head + "\n课堂整理稿" + (if (chunks.size > 1) "（第 ${i + 1}/${chunks.size} 部分）" else "") + "：\n$ch")),
                        Store.settings.value.llmModel, json = true) { acc ->
                        val f = (acc.length / expect).coerceAtMost(0.95)
                        genPct.floatValue = (0.3 + 0.6 * (i + f) / chunks.size).toFloat()
                        genStream.value = "已生成 ${acc.length} 字 · ${(System.currentTimeMillis() - t0) / 1000}s" +
                            (Regex("\"title\"\\s*:\\s*\"([^\"]{1,30})\"").findAll(acc).lastOrNull()?.let { " · 正在写「${it.groupValues[1]}」" } ?: "")
                        job.stream(acc.length, expect.toInt(), genStream.value)
                    }
                }
                genPct.floatValue = (0.3 + 0.6 * (i + 1) / chunks.size).toFloat()
                Log.ok("整理笔记", "第 ${i + 1}/${chunks.size} 段完成 · ${r.length} 字")
                JsonLoose.obj(r) ?: throw ApiException("模型返回的笔记 JSON 不完整：" + r.take(200))
            }
            genDetail.value = "合并笔记、生成概念图与复习卡"
            job.phase("合并笔记、生成概念图与复习卡", 0.97f, 1500)
            val o = mergeNotes(parts)
            genStep.intValue = 4
            fun arr(k: String) = o.optJSONArray(k) ?: JSONArray()
            fun <T> JSONArray.objs(f: (JSONObject) -> T) = (0 until length()).mapNotNull { runCatching { f(getJSONObject(it)) }.getOrNull() }
            val sections0 = arr("sections").objs { s ->
                app.xuezhang.data.NoteSection(s.optString("title"),
                    s.optJSONArray("pages")?.let { p -> (0 until p.length()).map { p.optInt(it) }.filter { it > 0 } } ?: emptyList(),
                    s.optString("body"), Frames.parseAt(s.optString("at")))
            }.filter { it.title.isNotBlank() || it.body.isNotBlank() }
            val cardsParsed = arr("cards").objs { Card(it.optString("q"), it.optString("a"), it.optString("tag", "考点")) }
            val sections = ensureStemUi(c?.name, stem || StemUi.isStem(c?.name, o.optString("subject")), sections0, cardsParsed, job)
            val notesText = o.optString("notes").ifBlank { sections.joinToString("\n\n") { "**${it.title}** ${it.body}" } }
            val done = (Store.lecture(id) ?: l).copy(
                title = o.optString("title").ifBlank { l.title },
                notes = notesText,
                sections = sections,
                mnemonics = arr("mnemonics").objs { Mnemonic(it.optString("title"), it.optString("body")) },
                nodes = arr("nodes").objs { Node(it.optString("label"), it.optInt("parent", -1), it.optString("detail")) },
                cards = cardsParsed,
                status = 2)
            Store.upsertLecture(done)
            genStep.intValue = 5
            genPct.floatValue = 1f; genDetail.value = "完成"
            Log.ok("整理笔记", "完成 · ${done.sections.size} 节 · ${done.cards.size} 张卡")
            AiProgress.finish(job, true, "笔记生成好了")
            GhBackup.afterNote()
            // OpenAI / ChatGPT：笔记好了自动用 gpt-image-2.5-flare 画 AI 导图（后台，不阻塞）
            if (Api.isGpt() && done.nodes.isNotEmpty() && autoMindMap) scope.launch { runCatching { MindMap.generate(id) } }
        } catch (e: Exception) {
            genError.value = e.message
            AiProgress.finish(job, false, "笔记生成失败"); job.error.value = e.message
            Log.err("整理笔记", e)
            Store.lecture(id)?.let { Store.upsertLecture(it.copy(status = 3)) }
        } finally { generating.value = false }
    }

    /** 工科笔记：交互组件不足 3 个或没有函数图像 → 让模型补；仍不足 → 本地从公式兜底；最多 5 个。 */
    private suspend fun ensureStemUi(course: String?, stem: Boolean, sections: List<app.xuezhang.data.NoteSection>, cards: List<Card>, job: AiJob): List<app.xuezhang.data.NoteSection> {
        if (!stem || sections.isEmpty()) return StemUi.cap(sections)
        var out = sections
        if (StemUi.needsMore(out)) {
            val c0 = StemUi.count(out.map { it.body })
            genDetail.value = "补充交互图像（现有 ${c0.total} 个）"
            job.phase(genDetail.value, 0.95f, 30_000)
            val user = out.mapIndexed { i, s -> "[$i] ${s.title}\n${s.body.replace(Regex("```[\\s\\S]*?```"), "[组件]").take(700)}" }.joinToString("\n\n")
            out = runCatching { StemUi.applyFix(out, withContext(Dispatchers.IO) { Api.chatJson(StemUi.fixPrompt(c0), "课程：${course.orEmpty()}\n\n$user") }) }
                .onFailure { Log.err("补充交互组件", it) }.getOrDefault(out)
            Log.ok("补充交互组件", "模型补充后 ${StemUi.count(out.map { it.body })}")
        }
        if (StemUi.needsMore(out)) out = StemUi.enforce(course.orEmpty(), out, cards)
        return StemUi.cap(out)
    }

    fun fmt(ms: Long): String { val s = ms / 1000; return "%02d:%02d".format(s / 60, s % 60) }
}
