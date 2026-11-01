package app.xuezhang.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import app.xuezhang.data.NoteSection
import app.xuezhang.data.Segment
import app.xuezhang.data.Store
import app.xuezhang.net.Api
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/**
 * 录播课笔记配图：只用视频原画面（本地视频 MediaMetadataRetriever 截帧 / B 站 videoshot 雪碧图裁切），
 * 绝不使用 AI 生成的图片。每一节笔记映射到它在视频里开始的时间点。
 */
object Frames {
    // ---------- 纯函数（可单测） ----------
    /** "mm:ss" / "h:mm:ss" / "[12:03]" / "12:03 P.4" → 毫秒；认不出返回 -1。 */
    fun parseAt(s: String?): Long {
        val m = Regex("(\\d{1,3}):(\\d{2})(?::(\\d{2}))?").find(s ?: return -1) ?: return -1
        val a = m.groupValues[1].toLong(); val b = m.groupValues[2].toLong(); val c = m.groupValues[3]
        return if (c.isNotEmpty()) (a * 3600 + b * 60 + c.toLong()) * 1000 else (a * 60 + b) * 1000
    }

    private fun grams(t: String): Set<String> {
        val clean = t.replace(Regex("```[\\s\\S]*?```"), " ").replace(Regex("\\$[^$]*\\$"), " ")
            .replace(Regex("\\[[brouf]:"), " ").lowercase()
        val out = HashSet<String>()
        val cjk = clean.filter { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
        for (i in 0 until cjk.length - 1) out.add(cjk.substring(i, i + 2))
        Regex("[a-z0-9]{3,}").findAll(clean).forEach { out.add(it.value) }
        return out
    }

    /**
     * 每一节的开始时间（毫秒）。优先用模型给的 at；否则按文字重合度在实录片段里找（尽量保持时间顺序）；
     * 再不行按节次均分视频时长。
     */
    fun assignTimes(sections: List<NoteSection>, segs: List<Segment>, durationMs: Long): List<Long> {
        val n = sections.size
        if (n == 0) return emptyList()
        val dur = if (durationMs > 0) durationMs else (segs.maxOfOrNull { it.startMs } ?: 0L) + 30_000
        val segG = segs.map { grams(it.voice.ifBlank { it.raw }) }
        var prev = 0L
        return sections.mapIndexed { i, s ->
            val t = when {
                s.atMs in 0..(dur + 60_000) -> s.atMs
                else -> {
                    val g = grams(s.title + " " + s.body)
                    var best = -1; var bestScore = 1
                    segs.indices.forEach { k ->
                        val sc = segG[k].count { it in g } - (if (segs[k].startMs < prev) 3 else 0)
                        if (sc > bestScore) { bestScore = sc; best = k }
                    }
                    if (best >= 0) segs[best].startMs else dur * i / n
                }
            }
            prev = maxOf(prev, t); t
        }
    }

    data class Shot(val images: List<String>, val xLen: Int, val yLen: Int, val w: Int, val h: Int, val index: List<Int>)

    /** B 站 /x/player/videoshot?index=1 的返回。 */
    fun parseVideoshot(json: String): Shot? {
        val o = JSONObject(json)
        if (o.optInt("code", -1) != 0) return null
        val d = o.optJSONObject("data") ?: return null
        val imgs = d.optJSONArray("image") ?: return null
        val list = (0 until imgs.length()).map { imgs.optString(it) }.filter { it.isNotBlank() }
            .map { if (it.startsWith("//")) "https:$it" else it.replace("http://", "https://") }
        if (list.isEmpty()) return null
        val idx = d.optJSONArray("index")?.let { a -> (0 until a.length()).map { a.optInt(it) } } ?: emptyList()
        return Shot(list, d.optInt("img_x_len", 10), d.optInt("img_y_len", 10), d.optInt("img_x_size", 160), d.optInt("img_y_size", 90), idx)
    }

    /** 某一秒对应的截图：(第几张雪碧图, 列, 行)。 */
    fun locate(shot: Shot, sec: Int, durationSec: Int): Triple<Int, Int, Int> {
        val per = shot.xLen * shot.yLen
        val total = per * shot.images.size
        val i = if (shot.index.size > 1) {
            var k = 0
            for (j in shot.index.indices) if (shot.index[j] <= sec) k = j else break
            k
        } else if (durationSec > 0) (sec.toLong() * total / durationSec).toInt() else 0
        val f = i.coerceIn(0, total - 1)
        val within = f % per
        return Triple(f / per, within % shot.xLen, within / shot.xLen)
    }

    // ---------- 截帧 ----------
    private fun dir(ctx: Context) = File(ctx.filesDir, "frames").apply { mkdirs() }

    private fun save(ctx: Context, b: Bitmap, lectureId: String, i: Int): String {
        val f = File(dir(ctx), "${lectureId}_$i.jpg")
        val scaled = if (b.width > 1280) Bitmap.createScaledBitmap(b, 1280, (b.height * 1280f / b.width).toInt(), true) else b
        f.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 86, it) }
        return f.absolutePath
    }

    /** 本地视频：在每节开始后约 3 秒处截一帧原画面。返回与 times 一一对应的路径（失败为空串）。 */
    fun fromLocalVideo(ctx: Context, path: String, lectureId: String, times: List<Long>, onProgress: (Float) -> Unit = {}): List<String> {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(path)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes") return times.map { "" }
            times.mapIndexed { i, t ->
                onProgress((i + 1f) / times.size)
                val at = (t + 3000).coerceAtMost(if (dur > 0) dur - 500 else t + 3000).coerceAtLeast(0)
                runCatching { r.getFrameAtTime(at * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { save(ctx, it, lectureId, i) } }.getOrNull().orEmpty()
            }
        } catch (e: Exception) { Log.err("录播截图", e); times.map { "" } } finally { runCatching { r.release() } }
    }

    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    private fun bytes(url: String): ByteArray = Api.http.newCall(Request.Builder().url(url).header("User-Agent", UA).header("Referer", "https://www.bilibili.com/").build())
        .execute().use { r -> if (!r.isSuccessful) throw IllegalStateException("截图下载失败 ${r.code}"); r.body!!.bytes() }

    fun videoshot(bvid: String, cid: Long): Shot? = runCatching {
        parseVideoshot(Api.http.newCall(Request.Builder().url("https://api.bilibili.com/x/player/videoshot?bvid=$bvid&cid=$cid&index=1")
            .header("User-Agent", UA).header("Referer", "https://www.bilibili.com/").build()).execute().use { it.body?.string().orEmpty() })
    }.getOrNull()

    private val sheetCache = HashMap<String, Bitmap>()
    /** 从雪碧图里裁出某一秒的画面。 */
    fun cropShot(shot: Shot, sec: Int, durationSec: Int): Bitmap? {
        val (img, col, row) = locate(shot, sec, durationSec)
        val url = shot.images.getOrNull(img) ?: return null
        val sheet = synchronized(sheetCache) { sheetCache[url] } ?: bytes(url).let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.also { synchronized(sheetCache) { if (sheetCache.size > 6) sheetCache.clear(); sheetCache[url] = it } } ?: return null
        // 有的雪碧图实际尺寸与 img_x_size 不一致：按实际宽高等分
        val w = sheet.width / shot.xLen; val h = sheet.height / shot.yLen
        if (w <= 0 || h <= 0) return null
        return Bitmap.createBitmap(sheet, (col * w).coerceAtMost(sheet.width - w), (row * h).coerceAtMost(sheet.height - h), w, h)
    }

    fun fromBilibili(ctx: Context, bvid: String, cid: Long, durationSec: Int, lectureId: String, times: List<Long>, onProgress: (Float) -> Unit = {}): List<String> {
        val shot = videoshot(bvid, cid) ?: return times.map { "" }.also { Log.add("录播截图", "B 站没有返回视频截图（videoshot）", false) }
        return times.mapIndexed { i, t ->
            onProgress((i + 1f) / times.size)
            runCatching { cropShot(shot, ((t + 3000) / 1000).toInt(), durationSec)?.let { save(ctx, it, lectureId, i) } }.getOrNull().orEmpty()
        }
    }

    /** 截图选择器：某一时间点前后若干张 B 站截图（秒, 图）。 */
    fun nearby(bvid: String, cid: Long, durationSec: Int, centerSec: Int, count: Int = 12, stepSec: Int = 6): List<Pair<Int, Bitmap>> {
        val shot = videoshot(bvid, cid) ?: return emptyList()
        val start = (centerSec - stepSec * count / 2).coerceAtLeast(0)
        return (0 until count).mapNotNull { k -> val s = start + k * stepSec; if (durationSec > 0 && s > durationSec) null else runCatching { cropShot(shot, s, durationSec) }.getOrNull()?.let { s to it } }
    }

    fun saveChosen(ctx: Context, b: Bitmap, lectureId: String, i: Int): String = save(ctx, b, lectureId, 1000 + i + (System.currentTimeMillis() % 1000).toInt())

    /** 把截图写回笔记各节（主线程调用）。 */
    fun apply(lectureId: String, times: List<Long>, paths: List<String>) {
        val l = Store.lecture(lectureId) ?: return
        val secs = l.sections.mapIndexed { i, s -> s.copy(atMs = times.getOrElse(i) { s.atMs }, frame = paths.getOrNull(i)?.takeIf { it.isNotBlank() } ?: s.frame) }
        Store.upsertLecture(l.copy(sections = secs))
    }
}
