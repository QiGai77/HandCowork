package app.xuezhang.engine.plugin

import org.json.JSONArray
import org.json.JSONObject

/**
 * v0.0.7.8 插件「脚本」：一个只能处理文本的小规则 DSL（代替 JS 引擎，见 README）。
 *
 * 规则文件（如识别优化插件的 skills/refine.json）：
 * ```
 * { "kind":"transcript-pipeline",
 *   "steps":[ {"op":"fillers","level":1,"words":["嗯","呃","那个"]},
 *             {"op":"stutter","level":1}, {"op":"selfCorrection","level":1,"markers":["不对","说错了"]},
 *             {"op":"particles","level":2,"words":["啊","呀"]}, {"op":"tags","level":2,"words":["对吧","是吧"]},
 *             {"op":"dropSentence","level":2,"patterns":["手机收(起来|一下)"]},
 *             {"op":"replace","level":1,"pattern":"…","with":"…"},
 *             {"op":"mark","tag":"exam","patterns":["要考","重点"]}, {"op":"tidy"} ],
 *   "llm": { "refine":"prompts/refine.md", "segment":"prompts/segment.md" } }
 * ```
 * level：1 轻 / 2 中 / 3 强；只执行 level ≤ 当前强度的步骤。所有正则都有长度上限，避免灾难性回溯。
 */
object TextRules {
    data class Result(
        val text: String,
        val fillers: Int = 0,
        val repeats: Int = 0,
        val corrections: Int = 0,
        val dropped: List<String> = emptyList(),
        val marks: Map<String, List<String>> = emptyMap(),
    ) {
        val exam get() = marks["exam"].orEmpty()
    }

    sealed interface Step { val level: Int }
    data class Fillers(override val level: Int, val words: List<String>) : Step
    data class Particles(override val level: Int, val words: List<String>) : Step
    data class Tags(override val level: Int, val words: List<String>) : Step
    data class Stutter(override val level: Int, val maxLen: Int = 4, val chars: String = "的了是在把这那我你他它就") : Step
    data class SelfCorrection(override val level: Int, val markers: List<String>) : Step
    data class DropSentence(override val level: Int, val patterns: List<Regex>) : Step
    data class Replace(override val level: Int, val pattern: Regex, val with: String) : Step
    data class Mark(override val level: Int, val tag: String, val patterns: List<Regex>) : Step
    data class Tidy(override val level: Int = 1) : Step

    data class Program(val steps: List<Step>, val llm: Map<String, String> = emptyMap())

    private const val MAX_PATTERN = 400
    private fun rx(s: String): Regex {
        require(s.length <= MAX_PATTERN) { "规则正则太长" }
        return Regex(s)
    }

    fun parse(json: String): Program {
        val o = JSONObject(json)
        val arr = o.optJSONArray("steps") ?: JSONArray()
        val steps = (0 until arr.length()).mapNotNull { i ->
            val s = arr.optJSONObject(i) ?: return@mapNotNull null
            val lv = s.optInt("level", 1).coerceIn(1, 3)
            fun words() = s.optJSONArray("words")?.strings().orEmpty().filter { it.isNotBlank() }.sortedByDescending { it.length }
            fun pats() = s.optJSONArray("patterns")?.strings().orEmpty().map(::rx)
            when (s.optString("op")) {
                "fillers" -> Fillers(lv, words())
                "particles" -> Particles(lv, words())
                "tags" -> Tags(lv, words())
                "stutter" -> Stutter(lv, s.optInt("maxLen", 4).coerceIn(1, 8), s.optString("chars", "的了是在把这那我你他它就"))
                "selfCorrection" -> SelfCorrection(lv, s.optJSONArray("markers")?.strings().orEmpty().sortedByDescending { it.length })
                "dropSentence" -> DropSentence(lv, pats())
                "replace" -> Replace(lv, rx(s.optString("pattern")), s.optString("with"))
                "mark" -> Mark(lv, s.optString("tag", "exam"), pats())
                "tidy" -> Tidy(lv)
                else -> null // 未知步骤：忽略（向前兼容）
            }
        }
        val llm = o.optJSONObject("llm")?.let { l -> l.keys().asSequence().associateWith { l.optString(it) } }.orEmpty()
        return Program(steps, llm)
    }

    private const val B = "，。！？、；：,.!?;:\\s"
    private fun alt(ws: List<String>) = ws.joinToString("|") { Regex.escape(it) }

    fun run(p: Program, input: String, level: Int): Result {
        var t = input.replace('\n', '。').trim()
        var fillers = 0; var repeats = 0; var corr = 0
        val dropped = mutableListOf<String>(); val marks = linkedMapOf<String, MutableList<String>>()
        for (st in p.steps) {
            if (st.level > level) continue
            when (st) {
                is Fillers -> if (st.words.isNotEmpty()) {
                    // 只删「独立成分」：前面是句首/标点，后面跟标点或句末。「那个电阻」里的「那个」不删。
                    val r = Regex("(^|[$B])(?:${alt(st.words)})+(?:[，,、\\s]+|(?=[。！？.!?；;]|$))")
                    var guard = 0
                    while (guard++ < 20) {
                        var n = 0
                        val nt = r.replace(t) { n++; it.groupValues[1] }
                        if (n == 0) break
                        fillers += n; t = nt
                    }
                }
                is Particles -> if (st.words.isNotEmpty()) {
                    val r = Regex("(?<=[\\u4e00-\\u9fff])(?:${alt(st.words)})+(?=[$B]|$)")
                    t = r.replace(t) { fillers++; "" }
                }
                is Tags -> if (st.words.isNotEmpty()) {
                    // 句末反问尾巴：「…的，对吧？」「…是吧。」
                    val r = Regex("[，,]?\\s*(?:${alt(st.words)})(?=[$B]|$)")
                    t = r.replace(t) { fillers++; "" }
                }
                is Stutter -> {
                    val multi = Regex("([\\u4e00-\\u9fff]{2,${st.maxLen}})(?:[，,、\\s]*\\1)+")
                    t = multi.replace(t) { repeats += it.value.length / it.groupValues[1].length - 1; it.groupValues[1] }
                    val cs = st.chars.filter { it.isLetter() }
                    if (cs.isNotEmpty()) {
                        val single = Regex("([$cs])\\1+")
                        t = single.replace(t) { repeats += it.value.length - 1; it.groupValues[1] }
                    }
                    val en = Regex("\\b([A-Za-z]{1,12})(\\s+\\1\\b)+", RegexOption.IGNORE_CASE)
                    t = en.replace(t) { repeats++; it.groupValues[1] }
                }
                is SelfCorrection -> if (st.markers.isNotEmpty()) {
                    val r = Regex("[，,]\\s*(?:${alt(st.markers)})[，,]?\\s*")
                    t = sentences(t).joinToString("") { s ->
                        var cur = s; var g = 0
                        while (g++ < 5) {
                            val m = r.find(cur) ?: break
                            val a = cur.substring(0, m.range.first); val b = cur.substring(m.range.last + 1)
                            if (a.isBlank() || b.isBlank()) break
                            cur = correct(a, b); corr++
                        }
                        cur
                    }
                }
                is DropSentence -> if (st.patterns.isNotEmpty()) {
                    t = sentences(t).filter { s -> val hit = st.patterns.any { it.containsMatchIn(s) }; if (hit) dropped.add(s.trim()); !hit }.joinToString("")
                }
                is Replace -> t = st.pattern.replace(t, st.with)
                is Mark -> sentences(t).forEach { s -> if (st.patterns.any { it.containsMatchIn(s) }) marks.getOrPut(st.tag) { mutableListOf() }.add(s.trim()) }
                is Tidy -> t = tidy(t)
            }
        }
        return Result(tidy(t), fillers, repeats, corr, dropped, marks)
    }

    /** 「电压是220伏，不对，是380伏」→「电压是380伏」：用更正部分开头的字在前文里找锚点，替换锚点之后的内容。 */
    internal fun correct(a: String, b0: String): String {
        val b = b0.removePrefix("应该是").let { if (it != b0) "是$it" else it }
        for (len in minOf(4, b.length) downTo 1) {
            val head = b.substring(0, len)
            val idx = a.lastIndexOf(head)
            if (idx >= 0) return a.substring(0, idx) + b
        }
        // 找不到锚点：用更正替换前一小句（最后一个逗号之后）
        val cut = a.lastIndexOfAny(charArrayOf('，', ','))
        return if (cut >= 0) a.substring(0, cut + 1) + b else b
    }

    /** 按 。！？ 切句，保留标点。 */
    fun sentences(t: String): List<String> {
        val out = mutableListOf<String>(); val sb = StringBuilder()
        for (c in t) { sb.append(c); if (c in "。！？!?；;") { out.add(sb.toString()); sb.clear() } }
        if (sb.isNotBlank()) out.add(sb.toString())
        return out
    }

    fun tidy(s: String): String {
        var t = s
        t = t.replace(Regex("[，,、]+(?=[。！？!?；;])"), "")
        t = t.replace(Regex("([，,、])[，,、\\s]+"), "$1")
        t = t.replace(Regex("([。！？!?])[。，,、\\s]+"), "$1")
        t = t.replace(Regex("^[，,、。；;\\s]+"), "")
        t = t.replace(Regex("(?<=[\\u4e00-\\u9fff])\\s+(?=[\\u4e00-\\u9fff])"), "")
        return t.trim()
    }

    /** 精炼强度：插件设置里的「轻 / 中 / 强」→ 1/2/3。 */
    fun level(name: String): Int = when (name.trim()) { "轻", "light", "1" -> 1; "强", "strong", "3" -> 3; else -> 2 }
}
