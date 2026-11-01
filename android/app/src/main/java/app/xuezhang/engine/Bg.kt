package app.xuezhang.engine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 后台任务：所有 AI 生成都在这个进程级 scope 里跑，离开页面不会被取消；
 * 页面上的中间状态（识别结果、候选图、报错…）存在这里，回到原处原样恢复。
 */
object Bg {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val states = HashMap<String, Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T> state(key: String, init: () -> T): MutableState<T> =
        synchronized(states) { states.getOrPut(key) { mutableStateOf(init()) } as MutableState<T> }

    @Suppress("UNCHECKED_CAST")
    fun <T> list(key: String): SnapshotStateList<T> =
        synchronized(states) { states.getOrPut(key) { mutableStateListOf<T>() } as SnapshotStateList<T> }

    fun clear(prefix: String) = synchronized(states) { states.keys.removeAll { it.startsWith(prefix) } }
}

/** 像 remember { mutableStateOf(..) }，但离开页面后还在。 */
@Composable
fun <T> keep(key: String, init: () -> T): MutableState<T> = remember(key) { Bg.state(key, init) }

@Composable
fun <T> keepList(key: String): SnapshotStateList<T> = remember(key) { Bg.list(key) }
