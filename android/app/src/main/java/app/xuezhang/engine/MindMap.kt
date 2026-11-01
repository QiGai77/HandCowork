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

    fun cand(id: String, i: Int) = File(Store.dir, "conceptmap_${id}_c$i.png")
    /** 还没选的候选图（灵动岛里「应用 / 丢弃」）。 */
    fun pending(id: String): List<File> = (0 until 4).map { cand(id, it) }.filter { it.exists() && it.length() > 0 }

    fun apply(id: String, idx: Int) {
        val ps = pending(id)
        ps.getOrNull(idx)?.copyTo(file(id), overwrite = true)
        ps.forEach { it.delete() }
        version.intValue++
        Log.ok("概念图", "已应用候选 ${idx + 1}/${ps.size}")
    }
    fun discard(id: String) { pending(id).forEach { it.delete() }; version.intValue++ }

    fun offer(id: String) {
        val ps = pending(id); if (ps.isEmpty()) return
        app.xuezhang.ui.Island.offer(app.xuezhang.ui.Island.Candidates("AI 导图 · $MODEL", ps.map { it.absolutePath }, "小彩蛋：导图越简洁越好记",
            app.xuezhang.ui.Route.MindMap(id), onApply = { apply(id, it) }, onDiscard = { discard(id) }))
    }

    /** 阻塞到画完（IO 线程 / 协程里调用）。带灵动岛 + 进度条；画好的图先作为候选停在灵动岛里。 */
    suspend fun generate(id: String, n: Int = 2) {
        val l = Store.lecture(id) ?: return
        if (l.nodes.isEmpty()) return
        AiProgress.run("mindmap:$id", "画 AI 导图", doneMsg = "AI 导图画好了 · 在灵动岛里选一张",
            origin = app.xuezhang.ui.Route.MindMap(id), retry = { AppJobs.launch { runCatching { generate(id, n) } } }) { job ->
            job.phase("用 $MODEL 构图中", 0.9f, 50_000L * n, "${l.nodes.size} 个概念节点 · $n 张候选")
            val files = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val us = Api.images(Prompts.get("mindmap") + "\n" + tree(l), n, "1536x1024", MODEL)
                job.phase("下载图片", 0.98f, 3000)
                pending(id).forEach { it.delete() }
                us.take(4).mapIndexedNotNull { i, u ->
                    runCatching {
                        val bytes = if (u.startsWith("b64:")) android.util.Base64.decode(u.removePrefix("b64:"), android.util.Base64.DEFAULT) else Api.download(u)
                        cand(id, i).also { it.writeBytes(bytes) }
                    }.onFailure { Log.err("概念图", it, "第 ${i + 1} 张下载失败") }.getOrNull()
                }
            }
            if (files.isEmpty()) throw app.xuezhang.net.ApiException("没有返回图片（原始回复已写入日志）")
            version.intValue++
            Log.ok("概念图", "$MODEL 生成成功 · ${files.size} 张候选 · ${files.sumOf { it.length() } / 1024} KB")
            offer(id)
        }
    }
}
