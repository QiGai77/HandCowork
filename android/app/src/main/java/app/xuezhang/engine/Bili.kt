package app.xuezhang.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import app.xuezhang.data.Course
import app.xuezhang.data.Lecture
import app.xuezhang.data.Segment
import app.xuezhang.data.Store
import app.xuezhang.net.Api
import app.xuezhang.net.ApiException
import app.xuezhang.ui.Island
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

/** 导入 B 站录播课：解析链接 → 只下载音轨 → 对齐课件 → 识别 → 标注补全 → 生成笔记。 */
object Bili {
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    data class Page(val cid: Long, val part: String, val duration: Int)
    data class Info(val bvid: String, val title: String, val owner: String, val duration: Int, val pic: String, val pages: List<Page>)

    // ---------- 纯函数（可单测） ----------
    private val BV = Regex("BV[0-9A-Za-z]{10}")
    fun parseBv(text: String): String? = BV.find(text)?.value
    fun parsePage(text: String): Int = Regex("[?&]p=(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 1
    fun fmtDur(sec: Int) = if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)

    fun parseInfo(json: String): Info {
        val o = JSONObject(json)
        if (o.optInt("code", -1) != 0) throw ApiException("B 站返回：${o.optString("message").ifBlank { "视频不存在或无权访问" }}")
        val d = o.getJSONObject("data")
        val pa = d.optJSONArray("pages")
        val pages = if (pa == null) emptyList() else (0 until pa.length()).map { i ->
            val p = pa.getJSONObject(i); Page(p.optLong("cid"), p.optString("part"), p.optInt("duration"))
        }
        return Info(d.optString("bvid"), d.optString("title"), d.optJSONObject("owner")?.optString("name").orEmpty(),
            d.optInt("duration"), d.optString("pic"), pages)
    }

    /** playurl(fnval=16) 返回的 dash.audio 里挑码率最低的一条（够识别、最省流量）。 */
    fun pickAudio(json: String): Pair<String, Long>? {
        val o = JSONObject(json)
        if (o.optInt("code", -1) != 0) return null
        val a = o.optJSONObject("data")?.optJSONObject("dash")?.optJSONArray("audio") ?: return null
        var best: JSONObject? = null
        for (i in 0 until a.length()) {
            val x = a.getJSONObject(i)
            if (best == null || x.optLong("bandwidth") < best.optLong("bandwidth")) best = x
        }
        val b = best ?: return null
        return b.optString("baseUrl").ifBlank { b.optString("base_url") } to b.optLong("bandwidth")
    }

    // ---------- 网络 ----------
    private fun get(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", UA).header("Referer", "https://www.bilibili.com/").build()
        Api.http.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw ApiException("B 站请求失败 ${r.code}" + if (r.code == 412) "（被风控，稍后再试）" else "")
            return t
        }
    }

    fun resolveShort(text: String): String {
        val m = Regex("https?://b23\\.tv/[0-9A-Za-z]+").find(text) ?: return text
        val req = Request.Builder().url(m.value).header("User-Agent", UA).build()
        return runCatching { Api.http.newBuilder().followRedirects(true).build().newCall(req).execute().use { it.request.url.toString() } }.getOrDefault(text)
    }

    fun info(bvid: String): Info = parseInfo(get("https://api.bilibili.com/x/web-interface/view?bvid=$bvid"))

    fun downloadAudio(bvid: String, cid: Long, out: File, onProgress: (Long, Long) -> Unit) {
        val (url, _) = pickAudio(get("https://api.bilibili.com/x/player/playurl?bvid=$bvid&cid=$cid&fnval=16&fnver=0&fourk=0"))
            ?: throw ApiException("没有拿到音轨地址（可能需要登录或是付费视频）")
        val req = Request.Builder().url(url).header("User-Agent", UA).header("Referer", "https://www.bilibili.com/video/$bvid").build()
        Api.http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw ApiException("音轨下载失败 ${r.code}")
            val body = r.body!!
            val total = body.contentLength()
            body.byteStream().use { ins -> out.outputStream().use { os ->
                val buf = ByteArray(64 * 1024); var n: Int; var done = 0L
                while (ins.read(buf).also { n = it } > 0) { os.write(buf, 0, n); done += n; onProgress(done, total) }
            } }
        }
    }

    // ---------- 解码切片：m4a → 16k 单声道 wav，每片 chunkSec 秒 ----------
    fun decodeToWavChunks(src: File, dir: File, chunkSec: Int, onChunk: (File, Long) -> Unit, onProgress: (Float) -> Unit) {
        val ex = MediaExtractor(); ex.setDataSource(src.absolutePath)
        val ti = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            ?: throw ApiException("文件里没有音轨")
        ex.selectTrack(ti)
        val fmt = ex.getTrackFormat(ti)
        val durUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0); codec.start()
        var srcRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE); var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val pcm = ByteArrayOutputStream()
        var chunkStartMs = 0L; var writtenMs = 0L
        var frac = 0.0
        val info = MediaCodec.BufferInfo()
        var inDone = false; var outDone = false
        val target = chunkSec * 16000 * 2
        fun flush() {
            if (pcm.size() < 16000) { pcm.reset(); return }
            val f = File(dir, "b_${chunkStartMs}.wav"); writeWav(f, pcm.toByteArray()); pcm.reset()
            onChunk(f, chunkStartMs); chunkStartMs = writtenMs
        }
        try {
            while (!outDone) {
                if (!inDone) {
                    val ii = codec.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val n = ex.readSampleData(codec.getInputBuffer(ii)!!, 0)
                        if (n < 0) { codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { codec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        codec.outputFormat.let { srcRate = it.getInteger(MediaFormat.KEY_SAMPLE_RATE); ch = it.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }
                    }
                    oi >= 0 -> {
                        val buf = codec.getOutputBuffer(oi)!!
                        val shorts = ShortArray(info.size / 2); buf.position(info.offset); buf.order(java.nio.ByteOrder.nativeOrder()).asShortBuffer().get(shorts)
                        // 下混 + 线性抽取到 16k
                        val frames = shorts.size / ch
                        val step = srcRate / 16000.0
                        while (frac < frames) {
                            val fi = frac.toInt()
                            var sum = 0; for (c in 0 until ch) sum += shorts[fi * ch + c]
                            val v = (sum / ch)
                            pcm.write(v and 0xff); pcm.write((v shr 8) and 0xff)
                            frac += step
                        }
                        frac -= frames
                        writtenMs = (info.presentationTimeUs / 1000)
                        codec.releaseOutputBuffer(oi, false)
                        if (durUs > 0) onProgress((info.presentationTimeUs.toFloat() / durUs).coerceIn(0f, 1f))
                        if (pcm.size() >= target) flush()
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                    }
                }
            }
            flush()
        } finally {
            runCatching { codec.stop() }; codec.release(); ex.release()
        }
    }

    private fun writeWav(f: File, data: ByteArray) {
        RandomAccessFile(f, "rw").use { r ->
            fun i32(v: Int) { r.write(v and 0xff); r.write(v shr 8 and 0xff); r.write(v shr 16 and 0xff); r.write(v shr 24 and 0xff) }
            fun i16(v: Int) { r.write(v and 0xff); r.write(v shr 8 and 0xff) }
            r.writeBytes("RIFF"); i32(36 + data.size); r.writeBytes("WAVE"); r.writeBytes("fmt ")
            i32(16); i16(1); i16(1); i32(16000); i32(32000); i16(2); i16(16); r.writeBytes("data"); i32(data.size); r.write(data)
        }
    }

    // ---------- 后台任务状态（界面离开也继续） ----------
    val STEPS = listOf("下载音轨", "对齐课件页码", "语音识别", "实时标注与补全", "生成笔记")
    val step = mutableIntStateOf(-1)          // 当前步骤，-1 未开始，5 完成
    val progress = mutableFloatStateOf(0f)
    val detail = mutableStateOf("")
    val error = mutableStateOf<String?>(null)
    val lectureId = mutableStateOf<String?>(null)
    private var job: Job? = null
    val running get() = job?.isActive == true

    /** B 站 CC / AI 字幕（player 接口；AI 字幕常需登录，拿不到就返回 null 走语音识别）。返回 (开始毫秒, 文本)。 */
    fun subtitles(bvid: String, cid: Long): List<Pair<Long, String>>? = runCatching {
        val j = org.json.JSONObject(get("https://api.bilibili.com/x/player/wbi/v2?bvid=$bvid&cid=$cid"))
        val subs = j.optJSONObject("data")?.optJSONObject("subtitle")?.optJSONArray("subtitles") ?: return null
        if (subs.length() == 0) return null
        val pick = (0 until subs.length()).map { subs.getJSONObject(it) }.let { l -> l.firstOrNull { it.optString("lan").startsWith("zh") } ?: l.first() }
        val url = pick.optString("subtitle_url").let { if (it.startsWith("//")) "https:$it" else it.replace("http://", "https://") }
        if (url.isBlank()) return null
        val body = org.json.JSONObject(get(url)).optJSONArray("body") ?: return null
        parseSubtitleBody(body)
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    /** 字幕逐句 → 每约 30 秒一段。 */
    fun parseSubtitleBody(body: org.json.JSONArray, sec: Int = 30): List<Pair<Long, String>> {
        val out = mutableListOf<Pair<Long, String>>(); var start = -1L; val sb = StringBuilder()
        for (i in 0 until body.length()) {
            val o = body.optJSONObject(i) ?: continue
            val from = (o.optDouble("from", 0.0) * 1000).toLong()
            if (start < 0) start = from
            sb.append(o.optString("content")).append(' ')
            if (from - start >= sec * 1000L) { out.add(start to sb.toString().trim()); sb.clear(); start = -1 }
        }
        if (sb.isNotBlank()) out.add(start.coerceAtLeast(0) to sb.toString().trim())
        return out
    }

    fun start(ctx: Context, info: Info, page: Page, course: Course, slideUri: Uri?, slideName: String?, localAudio: Uri? = null) {
        if (running) return
        error.value = null; step.intValue = 0; progress.floatValue = 0f; detail.value = ""
        val app = ctx.applicationContext
        job = Live.scope.launch {
            try {
                // 1 下载音轨
                val dir = File(app.cacheDir, "bili").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                val m4a = File(dir, "${info.bvid}_${page.cid}.m4a")
                Log.ok("导入录播课", if (localAudio != null) "本地音频 ${page.part}" else "${info.bvid} P${info.pages.indexOf(page) + 1} ${page.part}")
                val subs = if (localAudio == null) { detail.value = "查找字幕…"; subtitles(info.bvid, page.cid) } else null
                when {
                    localAudio != null -> {
                        detail.value = "读取本地音频"
                        app.contentResolver.openInputStream(localAudio)!!.use { i -> m4a.outputStream().use { o -> i.copyTo(o) } }
                        progress.floatValue = 1f; detail.value = "%.1f MB".format(m4a.length() / 1e6)
                    }
                    subs != null -> { progress.floatValue = 1f; detail.value = "找到字幕 ${subs.size} 段，跳过语音识别"; Log.ok("导入录播课", "使用 B 站字幕 ${subs.size} 段") }
                    else -> downloadAudio(info.bvid, page.cid, m4a) { d, t ->
                        progress.floatValue = if (t > 0) d.toFloat() / t else 0f
                        detail.value = "%.1f / %.1f MB".format(d / 1e6, t.coerceAtLeast(d) / 1e6)
                    }
                }
                // 2 对齐课件
                step.intValue = 1; progress.floatValue = 0f
                var c = course
                if (slideUri != null && slideName != null) {
                    detail.value = "读取 $slideName"
                    val pages = SlideParser.extract(app, slideUri, slideName) { p, n -> progress.floatValue = p.toFloat() / n * 0.5f }
                    val local = SlideParser.copyLocal(app, slideUri, slideName, c.id)
                    val (hot, slides) = runCatching { SlideParser.analyze(pages) }.getOrElse { emptyList<String>() to SlideParser.plainSlides(pages) }
                    c = c.copy(hotwords = (c.hotwords + hot).distinct(), slides = slides, slideFile = slideName, slidePath = local)
                    Store.upsertCourse(c)
                    detail.value = "${pages.size} 页 · ${hot.size} 个热词"
                } else detail.value = "没有课件，跳过"
                progress.floatValue = 1f
                // 3 识别
                step.intValue = 2; progress.floatValue = 0f
                val lec = Lecture(courseId = c.id, title = page.part.ifBlank { info.title }.take(40), status = 1,
                    durationMs = page.duration * 1000L, source = if (localAudio != null) "audio:${page.part}" else "bilibili:${info.bvid}")
                Store.upsertLecture(lec); lectureId.value = lec.id
                val chunks = mutableListOf<Pair<File, Long>>()
                if (subs == null) decodeToWavChunks(m4a, dir, 30, { f, s -> chunks.add(f to s) }, { progress.floatValue = it * 0.3f })
                val raws = mutableListOf<Pair<Long, String>>().apply { subs?.let { addAll(it) } }
                chunks.forEachIndexed { i, (f, s) ->
                    detail.value = "语音识别 ${i + 1} / ${chunks.size} 段"
                    val t = runCatching { Api.transcribe(f, c.hotwords.joinToString("，")) }.getOrElse { e -> if (i == 0) throw e else "" }
                    if (t.isNotBlank()) { raws.add(s to t); detail.value = "“…${t.takeLast(28)}…”" }
                    f.delete()
                    progress.floatValue = 0.3f + 0.7f * (i + 1) / chunks.size
                }
                if (raws.isEmpty()) throw ApiException("没有识别到语音内容")
                // 4 标注补全（每 3 片合并一次请求，省调用次数）
                step.intValue = 3; progress.floatValue = 0f
                val segs = mutableListOf<Segment>()
                val groups = raws.chunked(3)
                var curPage = c.slides.firstOrNull()?.page ?: 0
                groups.forEachIndexed { gi, g ->
                    val raw = g.joinToString(" ") { it.second }
                    val slides = c.slides.joinToString("\n") { "P.${it.page} ${it.title}" }.take(2500)
                    val user = "课程：${c.name}\n热词：${c.hotwords.joinToString("、")}\n" +
                        (if (slides.isNotBlank()) "PPT 页：\n$slides\n当前大约在 P.$curPage\n" else "") +
                        "前文：\n${segs.takeLast(2).joinToString("\n") { it.voice }}\n\n本段语音识别原文：\n$raw"
                    val o = runCatching { Api.chatJson(Prompts.get("segment"), user) }.getOrNull()
                    val pg = o?.optInt("page", 0)?.takeIf { it > 0 } ?: curPage
                    curPage = pg
                    segs.add(Segment(startMs = g.first().first, page = pg, raw = raw, voice = o?.optString("voice")?.ifBlank { raw } ?: raw,
                        senior = o?.optString("senior").orEmpty().let { if (Prompts.isSkip(it)) "" else it }, offTopic = o?.let { Prompts.offTopic(it) } ?: false, fillers = o?.optInt("fillers") ?: 0, repeats = o?.optInt("repeats") ?: 0,
                        exam = o?.optBoolean("exam") ?: false))
                    Store.lecture(lec.id)?.let { Store.upsertLecture(it.copy(segments = segs.toList())) }
                    progress.floatValue = (gi + 1f) / groups.size
                    detail.value = "已整理 ${gi + 1} / ${groups.size} 段"
                }
                // 5 生成笔记
                step.intValue = 4; progress.floatValue = 0.3f; detail.value = "学长在写图文笔记…"
                Live.generateNow(lec.id)
                if (Live.genError.value != null) throw ApiException(Live.genError.value!!)
                step.intValue = 5; progress.floatValue = 1f; detail.value = ""
                Island.show("录播课笔记生成好了", Island.Kind.Done)
            } catch (e: Exception) {
                error.value = e.message ?: "导入失败"
                Log.err("导入录播课", e)
                Island.show("导入失败", Island.Kind.Warn)
            }
        }
    }
}
