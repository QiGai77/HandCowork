package app.xuezhang.engine

/**
 * 底部导航栏顺序：tab id 0 课程 / 1 上课 / 2 笔记 / 3 我的。
 * 存成 "1,0,2,3" 这样的字符串；第一个就是启动页。
 */
object NavOrder {
    val LABELS = listOf("课程", "上课", "笔记", "我的")
    val ICONS = listOf("book", "mic", "note", "person")
    const val DEFAULT = "0,1,2,3"

    /** 宽松解析：去重、丢掉非法 id、补齐缺的（按默认顺序追加）。 */
    fun parse(s: String?): List<Int> {
        val got = (s ?: "").split(',', ' ', ';').mapNotNull { it.trim().toIntOrNull() }.filter { it in 0..3 }.distinct()
        return got + (0..3).filter { it !in got }
    }
    fun encode(order: List<Int>): String = parse(order.joinToString(",")).joinToString(",")
    fun start(s: String?): Int = parse(s).first()
    /** 把 from 位置的项移到 to 位置。 */
    fun move(order: List<Int>, from: Int, to: Int): List<Int> {
        val l = parse(order.joinToString(",")).toMutableList()
        if (from !in l.indices) return l
        val x = l.removeAt(from)
        l.add(to.coerceIn(0, l.size), x)
        return l
    }
    /** 拖动时：按位移（像素）和行高算目标位置。 */
    fun targetIndex(from: Int, dragPx: Float, rowPx: Float, size: Int = 4): Int =
        if (rowPx <= 0f) from else (from + Math.round(dragPx / rowPx)).coerceIn(0, size - 1)
}
