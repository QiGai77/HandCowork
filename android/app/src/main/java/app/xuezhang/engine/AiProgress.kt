package app.xuezhang.engine

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import app.xuezhang.ui.Island
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.exp

/** 等待 AI 时轮播的小彩蛋：提示词小技巧 + 俏皮话。 */
object AiTips {
    val ALL = listOf(
        "小彩蛋：「我的 › 提示词」里能改学长的语气，试试让他用东北话讲",
        "小彩蛋：笔记里点任意公式，能让学长用大白话再讲一遍",
        "小彩蛋：上课时点 ★ 标为考点，整理笔记时会被重点写进去",
        "小彩蛋：课前传 PPT，学长会认出热词，识别准确率更高",
        "小彩蛋：问学长时说「出 3 道题考我」，他真的会出题",
        "小彩蛋：提问加一句「用一个生活里的比喻」，解释会好懂很多",
        "小彩蛋：复习卡右滑记得、左滑忘了，忘了的下次会再出现",
        "小彩蛋：简单笔记适合考前速览，详细笔记适合第一次学",
        "小彩蛋：录播课有 B 站字幕时，学长会直接用字幕，省流量",
        "小彩蛋：不足 30 秒的录音会被当作误触自动作废",
        "小彩蛋：笔记库里每门课是一层书架，点开书可以翻页",
        "小彩蛋：长按概念图的节点可以拖动位置",
        "小彩蛋：提示词里写清「只输出 JSON」，模型更不容易跑题",
        "小彩蛋：给提示词加上「先列要点再展开」，笔记结构会更清楚",
        "学长正在奋笔疾书，红笔快没水了",
        "好饭不怕晚，好笔记也是",
        "学长偷偷翻了一下教材后面的答案",
        "这道题学长会，但学长先喝口水",
        "据说看进度条的人，期末都过了",
        "学长在把老师的口头禅过滤掉…",
        "正在把公式排版成 LaTeX，反斜杠有点多",
        "学长的脑子在冒烟，但很快乐",
        "稀有彩蛋：你发现了学长藏在进度条里的小猫 🐱",
        "稀有彩蛋：今天的学长也是满分学长",
    )
    fun at(i: Int): String = ALL[((i % ALL.size) + ALL.size) % ALL.size]
}

/**
 * 一次 AI 生成任务的进度：分阶段（phase），每个阶段有区间 [from, to] 和预计耗时。
 * 没有真实进度时按预计耗时做渐近估计（永远在动，但不会冲到阶段终点）；
 * 有真实进度（页数、流式字数、上传字节）时取二者较大值。进度单调不减。
 */
class AiJob internal constructor(val key: String, val title: String, val island: Boolean) {
    val fraction = mutableFloatStateOf(0f)
    val step = mutableStateOf("准备中")
    val detail = mutableStateOf("")
    val tip = mutableStateOf(AiTips.at((System.nanoTime() / 1000).toInt()))
    val error = mutableStateOf<String?>(null)
    val finished = mutableStateOf(false)
    val startedAt = System.currentTimeMillis()
    val elapsed = mutableLongStateOf(0L)
    @Volatile internal var from = 0f
    @Volatile internal var to = 0.05f
    @Volatile internal var phaseStart = System.currentTimeMillis()
    @Volatile internal var phaseEst = 4000L
    @Volatile internal var explicit = 0f
    internal var tipIdx = (System.nanoTime() % 997).toInt()

    /** 进入新阶段：进度会在 [当前, to] 之间推进，预计 estMs 毫秒。 */
    fun phase(step: String, to: Float, estMs: Long = 8000L, detail: String = "") {
        val cur = fraction.floatValue
        from = cur; this.to = to.coerceIn(cur, 1f); phaseStart = System.currentTimeMillis(); phaseEst = estMs.coerceAtLeast(500)
        explicit = cur; this.step.value = step; this.detail.value = detail
        tick()
    }
    /** 当前阶段内的真实进度 0..1。 */
    fun sub(f: Float, detail: String? = null) {
        explicit = maxOf(explicit, from + (to - from) * f.coerceIn(0f, 1f)); detail?.let { this.detail.value = it }; tick()
    }
    /** 直接设置整体进度 0..1。 */
    fun set(f: Float, detail: String? = null) { explicit = maxOf(explicit, f.coerceIn(0f, 1f)); detail?.let { this.detail.value = it }; tick() }
    /** 流式输出：按已生成字数/预计字数推进。 */
    fun stream(chars: Int, expect: Int, detail: String? = null) = sub((chars.toFloat() / expect.coerceAtLeast(1)).coerceAtMost(0.97f), detail)

    internal fun tick(now: Long = System.currentTimeMillis()) {
        if (finished.value) return
        val est = AiProgress.estimate(from, to, now - phaseStart, phaseEst)
        val v = maxOf(explicit, est).coerceAtMost(0.99f)
        if (v > fraction.floatValue) fraction.floatValue = v
        elapsed.longValue = now - startedAt
    }
}

object AiProgress {
    val jobs = mutableStateListOf<AiJob>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null

    /** 渐近估计：t = 预计耗时时约走完 80%，之后越来越慢但不停，最多到 97%。 */
    fun estimate(from: Float, to: Float, elapsedMs: Long, estMs: Long): Float {
        if (to <= from) return from
        val k = 1.0 - exp(-1.6 * elapsedMs.coerceAtLeast(0) / estMs.coerceAtLeast(1).toDouble())
        return (from + (to - from) * 0.97 * k).toFloat()
    }

    fun find(key: String): AiJob? = jobs.lastOrNull { it.key == key }

    fun start(key: String, title: String, island: Boolean = true): AiJob {
        jobs.removeAll { it.key == key }
        val j = AiJob(key, title, island)
        jobs.add(j)
        ensureTicker()
        return j
    }

    fun finish(j: AiJob, ok: Boolean = true, msg: String? = null, keepMs: Long = 900) {
        if (j.finished.value) return
        if (ok) { j.fraction.floatValue = 1f; j.step.value = msg ?: "完成" } else j.error.value = msg ?: "失败"
        j.finished.value = true
        if (j.island) Island.show(msg ?: if (ok) "${j.title}完成" else "${j.title}失败", if (ok) Island.Kind.Done else Island.Kind.Warn)
        scope.launch { delay(keepMs); jobs.remove(j); pushIsland() }
    }

    /** 包一层：开始 → 执行 → 成功/失败收尾。 */
    suspend fun <T> run(key: String, title: String, island: Boolean = true, doneMsg: String? = null, block: suspend (AiJob) -> T): T {
        val j = start(key, title, island)
        return try { block(j).also { finish(j, true, doneMsg) } }
        catch (e: Exception) { finish(j, false, "${title}失败"); j.error.value = e.message ?: "失败"; throw e }
    }

    private fun ensureTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            var n = 0
            while (true) {
                val now = System.currentTimeMillis()
                val list = jobs.toList()
                if (list.isEmpty()) { Island.endProgress(); break }
                list.forEach { it.tick(now) }
                if (n % 16 == 15) list.forEach { it.tipIdx++; it.tip.value = AiTips.at(it.tipIdx) }
                pushIsland()
                n++; delay(200)
            }
        }
    }

    private fun pushIsland() {
        val top = jobs.lastOrNull { it.island && !it.finished.value }
        if (top == null) { Island.endProgress(); return }
        Island.progress.value = Island.Progress("${top.title} · ${top.step.value}", top.fraction.floatValue, top.tip.value)
    }
}
