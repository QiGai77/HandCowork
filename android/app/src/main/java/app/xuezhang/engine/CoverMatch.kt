package app.xuezhang.engine

/** 预制封面：key、底色、图案；按课程名自动匹配。 */
object CoverMatch {
    data class Preset(val key: String, val color: Long, val motif: String, val keywords: List<String>, val glyph: String)

    val PRESETS = listOf(
        Preset("电机学", 0xFF4A78E0, "motor", listOf("电机学", "电机与拖动", "电机"), "电"),
        Preset("信号与系统", 0xFF7A5AD6, "signal", listOf("信号与系统", "信号", "数字信号处理"), "信"),
        Preset("电力电子技术", 0xFFE07A2E, "pwm", listOf("电力电子"), "电"),
        Preset("微机原理", 0xFF2E9E6A, "chip", listOf("微机原理", "单片机", "微机", "嵌入式", "计算机组成", "接口技术"), "微"),
        Preset("习思想", 0xFFC9372C, "xi", listOf("习近平新时代", "习思想", "习概", "思想政治", "马克思", "毛泽东思想", "毛概", "思修", "思想道德", "近代史", "形势与政策"), "思"),
        Preset("高等数学", 0xFF2F6FD0, "int", listOf("高等数学", "高数", "微积分", "数学分析", "复变函数"), "高"),
        Preset("线性代数", 0xFF5468C4, "matrix", listOf("线性代数", "线代", "矩阵"), "线"),
        Preset("电路原理", 0xFF2D8F9E, "circuit", listOf("电路原理", "电路分析", "电路"), "电"),
        Preset("自动控制原理", 0xFF8A5BB8, "control", listOf("自动控制", "自控", "控制原理", "现代控制"), "自"),
        Preset("大学物理", 0xFF3D86C6, "atom", listOf("大学物理", "物理"), "大"),
        Preset("模拟电子技术", 0xFFD2603A, "opamp", listOf("模拟电子", "模电"), "模"),
        Preset("数字电子技术", 0xFF3C9C8C, "gate", listOf("数字电子", "数电", "数字逻辑", "数字电路"), "数"),
    )

    /** 关键词越长越优先，避免「电路」抢走「电力电子」「数字电路」。 */
    fun match(name: String): Preset? {
        val n = name.replace(Regex("\\s"), "")
        if (n.isBlank()) return null
        return PRESETS.flatMap { p -> p.keywords.map { k -> k to p } }
            .filter { (k, _) -> n.contains(k) }
            .maxByOrNull { it.first.length }?.second
    }

    fun byKey(key: String): Preset? = PRESETS.firstOrNull { it.key == key }

    private val FALLBACK = listOf(0xFF4A78E0, 0xFF7A5AD6, 0xFFE07A2E, 0xFF2E9E6A, 0xFFC9372C, 0xFF2D8F9E, 0xFF8A5BB8, 0xFFD2603A, 0xFF3C9C8C, 0xFF5468C4)

    /** 通用封面：颜色由课程名哈希决定（稳定），大字是第一个字符。 */
    fun fallbackColor(name: String): Long {
        var h = 0
        name.forEach { h = h * 31 + it.code }
        return FALLBACK[Math.floorMod(h, FALLBACK.size)]
    }

    fun glyph(name: String): String = name.trim().firstOrNull { !it.isWhitespace() && it != '《' && it != '(' && it != '（' }?.toString() ?: "课"

    /** 课程的封面描述：preset:key / file:path / 空（自动）。返回实际要用的 preset（自动匹配时）。 */
    fun resolve(name: String, cover: String): Preset? = when {
        cover.startsWith("preset:") -> byKey(cover.removePrefix("preset:"))
        cover.startsWith("file:") -> null
        cover == "generic" -> null
        else -> match(name)
    }
}
