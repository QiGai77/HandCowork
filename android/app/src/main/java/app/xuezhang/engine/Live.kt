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
        lectureId.value = lec.id
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

    fun stop(ctx: Context) {
        if (!running.value) return
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
        val o = Api.chatJson(Prompts.SEGMENT, user)
        val page = o.optInt("page", 0).let { if (it > 0) it else currentPage.intValue }
        if (page > 0) currentPage.intValue = page
        val fills = o.optInt("fills", 0)
        val seg = Segment(startMs = start, page = page, raw = raw, voice = o.optString("voice", raw),
            senior = o.optString("senior"), fillers = o.optInt("fillers"), repeats = o.optInt("repeats"),
            exam = o.optBoolean("exam") || markNext)
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
        persist()
        generate(id)
    }

    fun regenerate(id: String) { scope.launch { generate(id) } }
    suspend fun generateNow(id: String) = generate(id)

    private suspend fun generate(id: String) {
        val l = Store.lecture(id) ?: return
        val c = Store.course(l.courseId)
        if (l.segments.isEmpty()) { Store.upsertLecture(l.copy(status = 3)); genError.value = "这节课没有识别到内容"; return }
        generating.value = true; genError.value = null; genStep.intValue = 0
        Store.upsertLecture(l.copy(status = 1))
        try {
            for (s in 1..2) { delay(400); genStep.intValue = s }
            val slideIdx = c?.slides?.joinToString("\n") { "P.${it.page} ${it.title}" }?.take(3000).orEmpty()
            val transcript = l.segments.joinToString("\n") { s ->
                "[${fmt(s.startMs)}${if (s.page > 0) " P.${s.page}" else ""}${if (s.exam) " ★考点" else ""}] ${s.voice}"
            }.takeLast(24000)
            genStep.intValue = 3
            val o = withContext(Dispatchers.IO) {
                Api.chatJson(Prompts.NOTES, "课程：${c?.name ?: ""}\n热词：${c?.hotwords?.joinToString("、") ?: ""}\n" +
                    (if (slideIdx.isNotBlank()) "课件页目录：\n$slideIdx\n" else "") + "\n课堂整理稿：\n$transcript")
            }
            genStep.intValue = 4
            fun arr(k: String) = o.optJSONArray(k) ?: JSONArray()
            fun <T> JSONArray.objs(f: (JSONObject) -> T) = (0 until length()).mapNotNull { runCatching { f(getJSONObject(it)) }.getOrNull() }
            val sections = arr("sections").objs { s ->
                app.xuezhang.data.NoteSection(s.optString("title"),
                    s.optJSONArray("pages")?.let { p -> (0 until p.length()).map { p.optInt(it) }.filter { it > 0 } } ?: emptyList(),
                    s.optString("body"))
            }.filter { it.title.isNotBlank() || it.body.isNotBlank() }
            val notesText = o.optString("notes").ifBlank { sections.joinToString("\n\n") { "**${it.title}** ${it.body}" } }
            val done = (Store.lecture(id) ?: l).copy(
                title = o.optString("title").ifBlank { l.title },
                notes = notesText,
                sections = sections,
                mnemonics = arr("mnemonics").objs { Mnemonic(it.optString("title"), it.optString("body")) },
                nodes = arr("nodes").objs { Node(it.optString("label"), it.optInt("parent", -1), it.optString("detail")) },
                cards = arr("cards").objs { Card(it.optString("q"), it.optString("a"), it.optString("tag", "考点")) },
                status = 2)
            Store.upsertLecture(done)
            genStep.intValue = 5
            Island.show("笔记生成好了", Island.Kind.Done)
        } catch (e: Exception) {
            genError.value = e.message
            Store.lecture(id)?.let { Store.upsertLecture(it.copy(status = 3)) }
            Island.show("笔记生成失败", Island.Kind.Warn)
        } finally { generating.value = false }
    }

    fun fmt(ms: Long): String { val s = ms / 1000; return "%02d:%02d".format(s / 60, s % 60) }
}
