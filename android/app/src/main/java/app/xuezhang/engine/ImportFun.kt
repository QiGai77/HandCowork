package app.xuezhang.engine

import kotlin.random.Random

/** 导入课件时轮播的俏皮话（含少量彩蛋）。 */
object ImportFun {
    private val reading = listOf(
        "学长正在翻第 %d 页…", "第 %d 页，划重点中", "这页公式有点多，容我缓缓", "一页一页来，别催别催",
        "这页字好小，学长眯起了眼", "把第 %d 页抄进小本本", "第 %d 页：嗯，这个考过", "正在把板书翻译成人话",
        "学长翻书速度堪比考前一晚", "第 %d 页看完了，下一页！",
    )
    private val thinking = listOf(
        "学长在提取热词和公式…", "把专业名词记进词典", "给每一页起个好记的标题", "公式正在排版，LaTeX 好难伺候",
        "快好了，先喝口水", "学长在推导，别打扰他", "把重点圈出来，红笔都快没水了", "再等等，好饭不怕晚",
    )
    private val rare = listOf(
        "偷偷告诉你：第 %d 页期末大概率要考", "学长发现了彩蛋：老师在第 %d 页藏了一只小猫",
        "学长读得太投入，差点忘了自己是个 App", "这份课件的作者一定很爱咖啡",
    )
    const val DONE = "搞定！整份课件学长已经读完啦"
    const val DONE_RARE = "全部读完！今天的学长也是满分学长"

    /** phase 0 读页面，1 让大模型整理。tick 每 2 秒左右加 1，用来换句子。 */
    fun line(phase: Int, page: Int, total: Int, tick: Int, rnd: Random = Random(tick * 31 + page)): String {
        val p = page.coerceAtLeast(1)
        if (rnd.nextInt(40) == 0) return rare[rnd.nextInt(rare.size)].let { if (it.contains("%d")) it.format(if (total > 0) rnd.nextInt(1, total + 1) else p) else it }
        val pool = if (phase == 0) reading else thinking
        val s = pool[(tick + if (phase == 0) p / 3 else 0) % pool.size]
        val head = if (phase == 0 && total > 0) "$p/$total · " else ""
        return head + if (s.contains("%d")) s.format(p) else s
    }

    fun done(): String = if (Random.nextInt(20) == 0) DONE_RARE else DONE
}
