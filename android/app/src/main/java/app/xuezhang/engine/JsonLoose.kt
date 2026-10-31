package app.xuezhang.engine

import org.json.JSONArray
import org.json.JSONObject

/**
 * 宽松读取大模型输出里的 JSON：
 * - 去掉 <think>…</think> 推理段、```json 代码块、前后废话
 * - 修复字符串里没转义的 LaTeX 反斜杠（\frac 会被 JSON 当成换页符 \f + rac，\sqrt 会丢掉反斜杠）
 * - 修复字符串里的裸换行、尾逗号
 * - 按括号配对找出所有完整的 {…} / […]
 */
object JsonLoose {
    private val LATEX_AFTER_ESCAPE = listOf(
        "frac", "forall", "flat", "theta", "tau", "times", "tan", "text", "tfrac", "to", "top", "triangle", "tilde", "tanh",
        "beta", "bar", "bf", "begin", "binom", "big", "Big", "boldsymbol", "bot", "bullet", "backslash", "boxed",
        "nu", "neq", "nabla", "ne", "not", "nolimits", "neg", "ni", "rho", "right", "rightarrow", "rm", "rangle", "rfloor", "rceil"
    )

    fun stripThink(t: String): String {
        var s = t.replace(Regex("(?s)<think>.*?</think>"), "").replace(Regex("(?s)<reasoning>.*?</reasoning>"), "")
        val open = s.indexOf("<think>")
        if (open >= 0) s = s.substring(0, open) + s.substring(open).let { r -> val i = r.indexOfFirst { it == '{' || it == '[' }; if (i >= 0) r.substring(i) else "" }
        return s.replace(Regex("```[a-zA-Z]*"), "")
    }

    /** 修复字符串字面量内部的问题；字符串外的内容原样保留（只去尾逗号）。 */
    fun repair(src: String): String {
        val sb = StringBuilder(src.length + 16)
        var inStr = false; var i = 0
        while (i < src.length) {
            val c = src[i]
            if (inStr) {
                when {
                    c == '\\' && i + 1 < src.length -> {
                        val n = src[i + 1]
                        val rest = src.substring(i + 1, minOf(src.length, i + 16))
                        val word = Regex("^[A-Za-z]+").find(rest)?.value ?: ""
                        val latex = when {
                            n == '\\' || n == '"' || n == '/' -> false
                            n == 'u' -> !Regex("^u[0-9a-fA-F]{4}").containsMatchIn(rest)
                            n in "bfnrt" -> word.length >= 2 && LATEX_AFTER_ESCAPE.any { word.startsWith(it) && (word == it || it.length >= 3) }
                            else -> true // \s \l \a \( \{ 等不是合法 JSON 转义，按 LaTeX 处理
                        }
                        if (latex) { sb.append("\\\\"); i++; continue }
                        sb.append(c).append(n); i += 2; continue
                    }
                    c == '"' -> inStr = false
                    c == '\n' -> { sb.append("\\n"); i++; continue }
                    c == '\r' -> { i++; continue }
                    c == '\t' -> { sb.append("\\t"); i++; continue }
                }
                sb.append(c)
            } else {
                if (c == '"') inStr = true
                if (c == ',') { // 尾逗号
                    var j = i + 1; while (j < src.length && src[j].isWhitespace()) j++
                    if (j < src.length && (src[j] == '}' || src[j] == ']')) { i++; continue }
                }
                sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    /** 所有括号配对完整的顶层 JSON 片段（对象或数组），按出现顺序。 */
    fun spans(t: String, opens: String = "{["): List<String> {
        val out = mutableListOf<String>()
        var start = 0
        while (start < t.length) {
            val s = t.indexOfFirst(start) { it in opens }
            if (s < 0) break
            var depth = 0; var inStr = false; var esc = false; var end = -1
            for (i in s until t.length) {
                val ch = t[i]
                if (inStr) { if (esc) esc = false else if (ch == '\\') esc = true else if (ch == '"') inStr = false; continue }
                when (ch) { '"' -> inStr = true; '{', '[' -> depth++; '}', ']' -> { depth--; if (depth == 0) { end = i; break } } }
            }
            if (end < 0) { start = s + 1; continue }
            out.add(t.substring(s, end + 1)); start = end + 1
        }
        return out
    }

    private inline fun String.indexOfFirst(from: Int, p: (Char) -> Boolean): Int { for (i in from until length) if (p(this[i])) return i; return -1 }

    fun parseValue(raw: String): Any? {
        val s = raw.trim()
        // 先修复再解析：Android 的 org.json 对 \frac、\sqrt 很“宽容”，会悄悄解析成控制字符或丢掉反斜杠
        return runCatching { val r = repair(s); if (r.startsWith("[")) JSONArray(r) else JSONObject(r) }.getOrNull()
            ?: runCatching { if (s.startsWith("[")) JSONArray(s) else JSONObject(s) }.getOrNull()
    }

    /** 第一个能解析的 JSON 对象。 */
    fun obj(t: String): JSONObject? {
        val c = stripThink(t)
        for (sp in spans(c, "{")) (parseValue(sp) as? JSONObject)?.let { return it }
        // 截断的输出：尝试从第一个 { 到最后一个 }
        val a = c.indexOf('{'); val b = c.lastIndexOf('}')
        if (a in 0 until b) (parseValue(c.substring(a, b + 1)) as? JSONObject)?.let { return it }
        return null
    }

    /** 所有能解析的顶层 JSON 值（对象或数组）。 */
    fun values(t: String): List<Any> = spans(stripThink(t)).mapNotNull { parseValue(it) }

    /** 修复对象内字符串：用于已经被 JSONObject 正常解析、但 LaTeX 反斜杠被吞成控制字符的文本。 */
    fun fixControlChars(s: String): String = s
        .replace("\u000C", "\\f").replace("\u0008", "\\b")
        .replace(Regex("\t(?=(heta|au|imes|an|ext|frac|o\\b|op|riangle|ilde))"), "\\\\t")
        .replace(Regex("\r(?=(ho|ight|m\\b|angle))"), "\\\\r")
        .replace(Regex("\n(?=(abla|eq|u\\b|ot|e\\b|eg))"), "\\\\n")
}
