package app.xuezhang.engine

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用级协程作用域：所有「长任务」（AI 生成、识别、导入、同步）都在这里跑，
 * 不再用 rememberCoroutineScope —— 离开页面时 composable 作用域会被取消，
 * 之前会报「rememberCoroutineScope left the composition」并丢掉任务。
 */
object AppJobs {
    private val handler = CoroutineExceptionHandler { _, e -> runCatching { Log.err("后台任务", e) } }
    val scope: CoroutineScope by lazy {
        val main = runCatching { Dispatchers.Main.immediate.also { it.isDispatchNeeded(kotlin.coroutines.EmptyCoroutineContext) } }.getOrNull()
        CoroutineScope(SupervisorJob() + (main ?: Dispatchers.Default) + handler)
    }
    fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)
}
