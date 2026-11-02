package app.xuezhang.engine

/**
 * v0.0.7.6 统一精装封面：10 款预设万能封面（布面 + 烫金框 + 徽章图标）。
 * 列表缩略图、书架封面/书脊、上课卡片、课程详情、翻书页、复习卡色带都用同一款。
 *
 * 课程的 cover 字段：
 *  - "hc:N"        用户选定第 N 款（1..10）
 *  - "file:/path"  相册自定义封面（覆盖）
 *  - "preset:键"   旧版学科预设，按学科映射到对应精装款
 *  - "" / generic  自动：先按学科关键词匹配，否则按课程名哈希稳定分配
 * 笔记（Lecture）的 coverStyle 0..9 表示单本笔记换成第 coverStyle+1 款；coverImg（AI 封面）优先级最高。
 */
object HardCovers {
    data class Hc(val n: Int, val key: String, val label: String, val board: Long, val light: Long, val dark: Long, val motif: String) {
        val index get() = n - 1
    }

    val ALL = listOf(
        Hc(1, "jade", "翡翠", 0xFF2F7A5C, 0xFF43876C, 0xFF27664D, "chip"),
        Hc(2, "navy", "藏青", 0xFF25395F, 0xFF3A4C6F, 0xFF1F2F4F, "wave"),
        Hc(3, "terracotta", "赭陶", 0xFFA9533D, 0xFFB16450, 0xFF8D4533, "integral"),
        Hc(4, "plum", "梅紫", 0xFF673A5C, 0xFF764D6C, 0xFF56304D, "atom"),
        Hc(5, "teal", "青碧", 0xFF1E5D63, 0xFF346D72, 0xFF194E53, "globe"),
        Hc(6, "burgundy", "酒红", 0xFF77283A, 0xFF843D4D, 0xFF632130, "quill"),
        Hc(7, "olive", "橄榄", 0xFF5B6A34, 0xFF6B7848, 0xFF4C592B, "leaf"),
        Hc(8, "slate", "石板", 0xFF4A5564, 0xFF5C6673, 0xFF3E4754, "gear"),
        Hc(9, "ochre", "赭黄", 0xFFA5742A, 0xFFAE813F, 0xFF8A6123, "flask"),
        Hc(10, "midnight", "午夜", 0xFF1C2242, 0xFF323854, 0xFF171C37, "code"),
    )

    /** 旧版学科预设（CoverMatch.motif）→ 精装款编号。 */
    private val SUBJECT = mapOf(
        "chip" to 1, "signal" to 2, "opamp" to 2, "int" to 3, "matrix" to 3, "atom" to 4,
        "circuit" to 5, "xi" to 6, "control" to 7, "motor" to 8, "pwm" to 9, "gate" to 10,
    )

    fun byN(n: Int): Hc = ALL[Math.floorMod(n - 1, ALL.size)]

    /** 稳定哈希（不依赖 String.hashCode 的实现细节，跨版本、跨设备一致）。 */
    fun stableHash(s: String): Int {
        var h = 0
        s.trim().forEach { h = h * 31 + it.code }
        return h
    }

    fun byName(name: String): Hc = ALL[Math.floorMod(stableHash(name), ALL.size)]

    /** 课程的精装款：hc:N > 旧 preset 学科映射 > 自动学科匹配 > 名字哈希。 */
    fun forCourse(name: String, cover: String): Hc {
        cover.removePrefix("hc:").takeIf { cover.startsWith("hc:") }?.toIntOrNull()?.let { if (it in 1..ALL.size) return byN(it) }
        if (cover.startsWith("preset:")) CoverMatch.byKey(cover.removePrefix("preset:"))?.let { p -> SUBJECT[p.motif]?.let { return byN(it) } }
        if (cover == "generic") return byName(name)
        CoverMatch.match(name)?.let { p -> SUBJECT[p.motif]?.let { return byN(it) } }
        return byName(name)
    }

    /** 一本笔记的精装款：单本覆盖（coverStyle 0..9）> 所属课程。 */
    fun forLecture(coverStyle: Int, courseName: String?, courseCover: String?, lectureTitle: String): Hc =
        if (coverStyle in ALL.indices) ALL[coverStyle]
        else if (courseName != null) forCourse(courseName, courseCover.orEmpty())
        else byName(lectureTitle)

    fun coverValue(hc: Hc) = "hc:${hc.n}"

    /** 封面上的淡水印字：课程名第一个有效字符。 */
    fun watermark(name: String): String = CoverMatch.glyph(name)
}
