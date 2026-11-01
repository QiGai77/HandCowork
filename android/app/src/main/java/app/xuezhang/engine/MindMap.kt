package app.xuezhang.engine

import androidx.compose.runtime.mutableIntStateOf
import app.xuezhang.data.Lecture
import app.xuezhang.data.Store
import app.xuezhang.net.Api
import java.io.File

/** AI 导图：把概念树交给 gpt-image-2.5-flare 画成一张图，存为 conceptmap_<id>.png。 */
object MindMap {
    const val MODEL = "gpt-image-2.5-flare"
    /** 每画完一张 +1，界面据此重新读图。 */
    val version = mutableIntStateOf(0)

    fun file(id: String) = File(Store.dir, "conceptmap_$id.png")

    fun tree(l: Lecture): String = buildString {
        fun walk(i: Int, d: Int) { append("  ".repeat(d)).append("- ").append(l.nodes[i].label).append('\n'); l.nodes.indices.filter { l.nodes[it].parent == i && it != i }.forEach { if (d < 6) walk(it, d + 1) } }
        l.nodes.indices.filter { l.nodes[it].parent !in l.nodes.indices }.forEach { walk(it, 0) }
    }

    /** 阻塞到画完（IO 线程 / 协程里调用）。带灵动岛 + 进度条。 */
    suspend fun generate(id: String) {
        val l = Store.lecture(id) ?: return
        if (l.nodes.isEmpty()) return
        AiProgress.run("mindmap:$id", "画 AI 导图", doneMsg = "AI 导图画好了") { job ->
            job.phase("用 $MODEL 构图中", 0.9f, 50_000, "${l.nodes.size} 个概念节点")
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val u = Api.images(Prompts.get("mindmap") + "\n" + tree(l), 1, "1536x1024", MODEL).first()
                job.phase("下载图片", 0.98f, 3000)
                if (u.startsWith("b64:")) android.util.Base64.decode(u.removePrefix("b64:"), android.util.Base64.DEFAULT) else Api.download(u)
            }
            file(id).writeBytes(bytes)
            version.intValue++
            Log.ok("概念图", "$MODEL 生成成功 · ${bytes.size / 1024} KB")
        }
    }
}
