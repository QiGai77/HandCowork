package app.xuezhang.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.Base64
import androidx.compose.runtime.mutableStateListOf
import app.xuezhang.net.Api
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

/**
 * AI 生图任务队列：最多 10 个同时进行（超出的排队）。
 * 灵动岛只显示一条「AI 生图 · n 个任务」的总进度；每个任务可再次对话修改，或框选区域局部重绘。
 */
object ImageGen {
    const val MAX = 10
    const val KEY = "imagegen"

    /** 框选区域，0..1 相对坐标。 */
    data class Region(val l: Float, val t: Float, val r: Float, val b: Float) {
        fun label() = "左上(${(l * 100).toInt()}%,${(t * 100).toInt()}%) 到 右下(${(r * 100).toInt()}%,${(b * 100).toInt()}%)"
    }

    data class Task(
        val id: String = java.util.UUID.randomUUID().toString().take(8),
        val prompt: String,
        val history: List<String> = emptyList(),   // 已对话的修改要求
        val region: Region? = null,
        val baseFile: String = "",                  // 修改所基于的上一张图
        val target: String = "",                    // cover:<lectureId> / course:<courseId> / ""
        val size: String = "768x1024",
        val status: Int = 0,                        // 0 排队 1 生成中 2 完成 3 失败
        val file: String = "",
        val error: String = "",
        val createdAt: Long = System.currentTimeMillis(),
    )

    val tasks = mutableStateListOf<Task>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(MAX)

    fun running() = tasks.count { it.status == 0 || it.status == 1 }

    private fun update(id: String, f: (Task) -> Task) {
        val i = tasks.indexOfFirst { it.id == id }; if (i >= 0) tasks[i] = f(tasks[i])
    }

    private fun fullPrompt(t: Task): String = buildString {
        append(t.prompt)
        if (t.history.isNotEmpty()) {
            append("\n\n在上一版画面基础上按以下要求修改（其余构图、配色、风格保持一致）：")
            t.history.forEachIndexed { i, h -> append("\n${i + 1}. $h") }
        }
        t.region?.let { append("\n\n只重绘画面中 ${it.label()} 这一矩形区域，区域外保持原样。") }
    }

    /** 新建任务；返回任务 id。 */
    fun submit(ctx: Context, prompt: String, target: String = "", size: String = "768x1024"): String {
        val t = Task(prompt = prompt.trim(), target = target, size = size)
        tasks.add(0, t); launch(ctx.applicationContext, t.id); return t.id
    }

    /** 再次对话：基于某张结果追加修改要求（整图），生成一张新版本。 */
    fun refine(ctx: Context, from: Task, instruction: String): String {
        val t = Task(prompt = from.prompt, history = from.history + instruction.trim(), baseFile = from.file, target = from.target, size = from.size)
        tasks.add(0, t); launch(ctx.applicationContext, t.id); return t.id
    }

    /** 框选局部重绘：生成后只把框选区域贴回原图。 */
    fun inpaint(ctx: Context, from: Task, region: Region, instruction: String): String {
        val t = Task(prompt = from.prompt, history = from.history + instruction.trim(), region = region, baseFile = from.file, target = from.target, size = from.size)
        tasks.add(0, t); launch(ctx.applicationContext, t.id); return t.id
    }

    fun retry(ctx: Context, id: String) { update(id) { it.copy(status = 0, error = "") }; launch(ctx.applicationContext, id) }
    fun remove(id: String) { tasks.removeAll { it.id == id } }

    private fun launch(ctx: Context, id: String) {
        refreshSummary()
        scope.launch {
            gate.withPermit {
                update(id) { it.copy(status = 1) }; refreshSummary()
                val t = tasks.firstOrNull { it.id == id } ?: return@withPermit
                try {
                    val url = Api.images(fullPrompt(t), 1, t.size).first()
                    val bytes = if (url.startsWith("b64:")) Base64.decode(url.removePrefix("b64:"), Base64.DEFAULT) else Api.download(url)
                    val dir = File(ctx.filesDir, "gen").apply { mkdirs() }
                    val out = File(dir, "img_${t.id}.png")
                    var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw IllegalStateException("图片解码失败")
                    if (t.region != null && t.baseFile.isNotBlank()) bmp = composite(t.baseFile, bmp, t.region) ?: bmp
                    out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    update(id) { it.copy(status = 2, file = out.absolutePath) }
                    applyTarget(tasks.first { it.id == id })
                } catch (e: Exception) {
                    update(id) { it.copy(status = 3, error = e.message ?: "失败") }
                    Log.add("API", "AI 生图失败：${e.message}", false)
                }
                refreshSummary()
            }
        }
    }

    /** 把新图里框选区域贴回原图（带 6% 羽化边）。 */
    private fun composite(basePath: String, gen: Bitmap, r: Region): Bitmap? {
        val base = BitmapFactory.decodeFile(basePath) ?: return null
        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        val g = Bitmap.createScaledBitmap(gen, out.width, out.height, true)
        val rect = Rect((r.l * out.width).toInt(), (r.t * out.height).toInt(), (r.r * out.width).toInt(), (r.b * out.height).toInt())
        if (rect.width() < 2 || rect.height() < 2) return null
        Canvas(out).drawBitmap(g, rect, rect, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** 结果自动应用：笔记封面 / 课程封面（第一次生成完成时）。 */
    private fun applyTarget(t: Task) {
        when {
            t.target.startsWith("cover:") -> app.xuezhang.data.Store.lectures.firstOrNull { it.id == t.target.removePrefix("cover:") }?.let {
                app.xuezhang.data.Store.upsertLecture(it.copy(coverImg = t.file))
            }
            t.target.startsWith("course:") -> app.xuezhang.data.Store.course(t.target.removePrefix("course:"))?.let {
                app.xuezhang.data.Store.upsertCourse(it.copy(cover = "file:" + t.file))
            }
        }
        app.xuezhang.ui.CoverImages.evict(t.file)
    }

    /** 灵动岛：所有生图任务合并成一条总进度。 */
    private fun refreshSummary() {
        val active = tasks.filter { it.status == 0 || it.status == 1 }
        val job = AiProgress.find(KEY)
        if (active.isEmpty()) {
            if (job != null && !job.finished.value) {
                val failed = tasks.take(MAX).count { it.status == 3 }
                AiProgress.finish(job, failed == 0, if (failed == 0) "AI 生图完成" else "AI 生图完成，$failed 张失败")
            }
            return
        }
        val j = if (job == null || job.finished.value) AiProgress.start(KEY, "AI 生图", island = true).also { it.phase("生成中", 0.97f, 20000L) } else job
        val total = active.size + tasks.count { it.status == 2 && System.currentTimeMillis() - it.createdAt < 10 * 60_000 && it.createdAt >= j.startedAt - 1000 }
        val done = total - active.size
        val gen = active.count { it.status == 1 }
        j.step.value = "$gen 张生成中" + if (active.size > gen) " · ${active.size - gen} 张排队" else ""
        j.detail.value = "$done / $total"
        if (total > 0) j.set(done.toFloat() / total)
    }
}
