package app.xuezhang.engine

import android.content.Context
import android.net.Uri
import app.xuezhang.data.Slide
import app.xuezhang.ui.Island
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 课前资料：上传 PPT / PDF → 逐页读取 → 大模型提炼热词与公式。统一进度（灵动岛 + 进度条 + 小彩蛋）。 */
object SlideImport {
    data class Result(val name: String, val local: String, val hot: List<String>, val slides: List<Slide>, val pages: Int)

    fun key(ownerId: String) = "slides:$ownerId"

    suspend fun run(ctx: Context, uri: Uri, name: String, ownerId: String): Result =
        AiProgress.run(key(ownerId), "解析课件", doneMsg = ImportFun.done()) { job ->
            job.phase("读取 $name", 0.05f, 1500)
            val local = withContext(Dispatchers.IO) { SlideParser.copyLocal(ctx, uri, name, ownerId) }
            val src = if (local.isNotBlank()) Uri.fromFile(File(local)) else uri
            job.phase("逐页读取课件", 0.6f, 20_000)
            var tick = 0
            val pages = withContext(Dispatchers.IO) {
                SlideParser.extract(ctx, src, name) { p, n ->
                    if (n > 0) job.sub(p.toFloat() / n)
                    job.detail.value = ImportFun.line(0, p, n, (tick++) / 4)
                }
            }
            job.phase("大模型提炼热词和公式", 0.97f, 40_000, "${pages.size} 页 · " + ImportFun.line(1, 0, pages.size, 0))
            val (hot, slides) = withContext(Dispatchers.IO) {
                runCatching { SlideParser.analyze(pages) }.getOrElse { e ->
                    Log.err("解析课件", e)
                    Island.show("没连上大模型，只保存了页面文字", Island.Kind.Warn); emptyList<String>() to SlideParser.plainSlides(pages)
                }
            }
            Log.ok("解析课件", "$name · ${pages.size} 页 · ${hot.size} 个热词")
            Result(name, local, hot, slides, pages.size)
        }
}
