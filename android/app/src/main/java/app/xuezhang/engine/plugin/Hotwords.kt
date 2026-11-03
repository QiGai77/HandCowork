package app.xuezhang.engine.plugin

/**
 * v0.0.8.0 插件通用钩子 transcript.hotwords：热词纠错引擎（纯 Kotlin，无网络，便于单元测试）。
 *
 * 由插件规则里的 `{"op":"hotwords"}` 步骤启用；词表来自课程热词、课件自动提取、各插件的 `{"op":"lexicon"}`。
 * 三遍替换，替换处写成 `[h:正确词|原词]` 标记（界面画蓝色虚线 + 上标「原:xx」，点一下还原）：
 *  1. 别名：词表里显式写的误识别写法（如 AO → A口）；
 *  2. 字母数字串：大小写 / 形近字符（O↔0、I↔1）/ 编辑距离（82555 → 8255，唯一最近才替换）；
 *  3. 拼音模糊：中文热词按拼音（含 zh/z、n/l、ang/an 等模糊音）在原文里滑窗匹配。
 * 思路借鉴 CapsWriter-Offline（MIT，https://github.com/HaujetZhao/CapsWriter-Offline）的拼音模糊热词替换。
 */
object Hotwords {
    data class Term(val term: String, val alias: List<String> = emptyList())
    data class Fix(val from: String, val to: String)

    /** 汉字 → 拼音（无声调，多音字多个读音）。表文件格式：每行「pinyin 汉字汉字…」，# 开头为注释。 */
    class Pinyin(private val map: Map<Char, List<String>>) {
        fun of(c: Char): List<String> = map[c] ?: listOf(c.lowercaseChar().toString())
        val size get() = map.size
        companion object {
            fun parse(text: String): Pinyin {
                val m = HashMap<Char, MutableList<String>>()
                text.lineSequence().forEach { l ->
                    val t = l.trim(); if (t.isEmpty() || t.startsWith("#")) return@forEach
                    val sp = t.indexOf(' '); if (sp <= 0) return@forEach
                    val py = t.substring(0, sp)
                    t.substring(sp + 1).forEach { ch -> if (!ch.isWhitespace()) m.getOrPut(ch) { mutableListOf() }.add(py) }
                }
                return Pinyin(m)
            }
        }
    }

    /** 模糊音规则，如 "zh=z"（声母）或 "ang=an"（韵母）。 */
    class Fuzzy(rules: List<String>) {
        private val ini = mutableListOf<Pair<String, String>>()
        private val fin = mutableListOf<Pair<String, String>>()
        init {
            rules.forEach { r ->
                val (a, b) = r.split('=').map { it.trim() }.takeIf { it.size == 2 } ?: return@forEach
                if (a in INITIALS || b in INITIALS) ini.add(a to b) else fin.add(a to b)
            }
        }
        fun norm(py: String): String {
            var initial = INITIALS.firstOrNull { py.startsWith(it) } ?: ""
            var final = py.removePrefix(initial)
            ini.forEach { (a, b) -> if (initial == a) initial = b }
            fin.forEach { (a, b) -> if (final == a) final = b }
            return initial + final
        }
        companion object {
            // 两字母声母放前面，startsWith 先匹配到
            val INITIALS = listOf("zh", "ch", "sh", "b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "h", "j", "q", "x", "r", "z", "c", "s", "y", "w")
            val DEFAULT = listOf("zh=z", "ch=c", "sh=s", "n=l", "ang=an", "eng=en", "ing=in")
        }
    }

    private val TOKEN = Regex("\\[h:([^|\\]]*)\\|([^\\]]*)]")
    private val PROTECT = Regex("\\[h:[^\\]]*]|\\$[^$]*\\$")
    private fun isCjk(c: Char) = c in '\u4e00'..'\u9fff'
    private fun isAlnum(c: Char) = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9'

    /** 去掉标记，只留正确词（给旧界面 / 大模型看）。 */
    fun strip(s: String): String = TOKEN.replace(s) { it.groupValues[1] }
    /** 还原：把某个标记换回原词。 */
    fun restore(s: String, from: String): String {
        val m = TOKEN.findAll(s).firstOrNull { it.groupValues[2] == from } ?: return s
        return s.replaceRange(m.range, m.groupValues[2])
    }
    fun fixes(s: String): List<Fix> = TOKEN.findAll(s).map { Fix(it.groupValues[2], it.groupValues[1]) }.toList()

    /** 只在没被保护（已有标记、公式）的片段上做替换。 */
    fun mapFree(s: String, f: (String) -> String): String {
        val sb = StringBuilder(); var last = 0
        PROTECT.findAll(s).forEach { m -> sb.append(f(s.substring(last, m.range.first))); sb.append(m.value); last = m.range.last + 1 }
        sb.append(f(s.substring(last)))
        return sb.toString()
    }

    fun correct(text: String, terms0: List<Term>, py: Pinyin? = null, fuzzy: Fuzzy = Fuzzy(Fuzzy.DEFAULT), maxEdit: Int = 1): String {
        val terms = terms0.filter { it.term.isNotBlank() }.distinctBy { it.term }
        if (terms.isEmpty() || text.isBlank()) return text
        var t = text
        // 1) 别名
        val aliases = terms.flatMap { tm -> tm.alias.filter { it.isNotBlank() && it != tm.term }.map { it to tm.term } }.sortedByDescending { it.first.length }
        for ((a, to) in aliases) {
            val asciiEdge = isAlnum(a.first()) || isAlnum(a.last())
            t = mapFree(t) { seg ->
                val sb = StringBuilder(); var i = 0
                while (i < seg.length) {
                    val j = seg.indexOf(a, i, ignoreCase = a.all { it.code < 128 })
                    if (j < 0) { sb.append(seg.substring(i)); break }
                    val okL = !asciiEdge || j == 0 || !isAlnum(seg[j - 1])
                    val okR = !asciiEdge || j + a.length >= seg.length || !isAlnum(seg[j + a.length])
                    sb.append(seg.substring(i, j))
                    sb.append(if (okL && okR) "[h:$to|${seg.substring(j, j + a.length)}]" else seg.substring(j, j + a.length))
                    i = j + a.length
                }
                sb.toString()
            }
        }
        // 2) 字母数字串
        val alnumTerms = terms.map { it.term }.filter { s -> s.length >= 2 && s.all { isAlnum(it) } }
        if (alnumTerms.isNotEmpty()) t = mapFree(t) { seg ->
            Regex("[A-Za-z0-9]{2,}").replace(seg) { m ->
                val w = m.value
                val r = m.range
                // 小数、时间等跟着 . : 的数字不碰
                if ((r.first > 0 && seg[r.first - 1] in ".:：") || (r.last + 1 < seg.length && seg[r.last + 1] in ".:：%")) return@replace w
                bestAlnum(w, alnumTerms, maxEdit)?.let { "[h:$it|$w]" } ?: w
            }
        }
        // 3) 拼音模糊
        if (py != null) {
            val cjkTerms = terms.map { it.term }.filter { s -> s.length in 2..8 && s.any(::isCjk) && s.all { isCjk(it) || isAlnum(it) } }
                .sortedByDescending { it.length }
            for (term in cjkTerms) {
                val want = term.map { c -> py.of(c).map(fuzzy::norm).toSet() }
                t = mapFree(t) { seg ->
                    val sb = StringBuilder(); var i = 0
                    while (i < seg.length) {
                        if (i + term.length <= seg.length) {
                            val win = seg.substring(i, i + term.length)
                            if (win != term && win.any(::isCjk) && win.indices.all { k ->
                                    val c = win[k]
                                    c == term[k] || (isCjk(c) && isCjk(term[k]) && py.of(c).any { fuzzy.norm(it) in want[k] }) ||
                                        (!isCjk(c) && c.equals(term[k], ignoreCase = true))
                                } && win.count { isCjk(it) } >= 1 && win.indices.count { win[it] != term[it] } <= (if (term.length <= 2) 1 else term.length * 2 / 3)) {
                                sb.append("[h:$term|$win]"); i += term.length; continue
                            }
                        }
                        sb.append(seg[i]); i++
                    }
                    sb.toString()
                }
            }
        }
        return t
    }

    private fun look(s: String) = s.uppercase().replace('O', '0').replace('I', '1').replace('L', '1')

    /** 字母数字串的最近热词：完全相同（忽略大小写形近）→ 规范写法；否则编辑距离 ≤ maxEdit 且唯一最近才替换。 */
    internal fun bestAlnum(w: String, terms: List<String>, maxEdit: Int): String? {
        if (terms.any { it == w }) return null
        // 全小写的普通英文单词（out、cs…）不当成型号/引脚名
        if (w.none { it.isDigit() } && w == w.lowercase()) return null
        terms.firstOrNull { look(it) == look(w) }?.let { return it }
        if (w.length < 4) return null
        val scored = terms.filter { it.length >= 4 && kind(it) == kind(w) }.map { it to edit(look(it), look(w)) }.filter { it.second in 1..maxEdit }
        val best = scored.minOfOrNull { it.second } ?: return null
        val top = scored.filter { it.second == best }
        return top.singleOrNull()?.first
    }
    private fun kind(s: String) = if (s.all { it.isDigit() }) 0 else 1

    fun edit(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..b.length) { val tmp = d[j]; d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1); prev = tmp }
        }
        return d[b.length]
    }

    /** 从课件页标题 / 公式里自动提取热词：芯片型号、大写缩写、3–6 字中文短语。 */
    fun fromSlides(titles: List<String>): List<Term> {
        val out = LinkedHashSet<String>()
        titles.forEach { s ->
            Regex("[A-Za-z0-9]{2,12}").findAll(s).map { it.value }.filter { w -> w.any { it.isDigit() } && w.any { !it.isDigit() } || w.length >= 3 && w.all { it.isDigit() } || w.length >= 2 && w.all { it.isUpperCase() } }
                .filterNot { it.matches(Regex("P?\\d{1,2}")) }.forEach { out.add(it) }
            Regex("[\\u4e00-\\u9fff]+").findAll(s).map { it.value }.filter { it.length in 3..6 }.filterNot { it.startsWith("第") }.forEach { out.add(it) }
        }
        return out.map { Term(it) }
    }
}

/**
 * v0.0.8.0 插件通用钩子 transcript.split：分句（纯规则，不上模型）。
 * 按 。？！； 切句；超过 maxLen 字按逗号再切；不足 minLen 字的碎句并入前一句；不会切开 [x:…] 标记和 $公式$。
 * 规则参考 FunASR ct-punc 的分句输出（MIT，https://github.com/modelscope/FunASR）与
 * wtpsplit 的长句切分思路（MIT，https://github.com/segment-any-text/wtpsplit）。
 */
object Splitter {
    fun visibleLen(s: String) = Hotwords.strip(s).replace(Regex("\\[[a-z]:"), "").replace("]", "").length

    fun split(text: String, breakers: String = "。？！；?!;", soft: String = "，,、", maxLen: Int = 60, minLen: Int = 6): List<String> {
        val raw = cut(text.trim()) { it in breakers }
        val pieces = raw.flatMap { s -> if (visibleLen(s) > maxLen) splitLong(s, soft, maxLen) else listOf(s) }
        val out = mutableListOf<String>()
        for (p in pieces.map { it.trim() }.filter { it.isNotEmpty() }) {
            if (out.isNotEmpty() && (visibleLen(p) < minLen || visibleLen(out.last()) < minLen && !endsHard(out.last(), breakers))) out[out.lastIndex] = out.last() + p
            else out.add(p)
        }
        // 第一句太短：并到下一句
        if (out.size >= 2 && visibleLen(out[0]) < minLen) { out[1] = out[0] + out[1]; out.removeAt(0) }
        return out
    }

    private fun endsHard(s: String, breakers: String) = s.isNotEmpty() && s.last() in breakers

    /** 在不处于 [..] / $..$ 里的位置，遇到断点字符就切（断点字符留在前一句末尾）。 */
    private fun cut(s: String, isBreak: (Char) -> Boolean): List<String> {
        val out = mutableListOf<String>(); val sb = StringBuilder()
        var depth = 0; var math = false
        for (c in s) {
            sb.append(c)
            when {
                c == '$' -> math = !math
                !math && c == '[' -> depth++
                !math && c == ']' -> depth = maxOf(0, depth - 1)
                depth == 0 && !math && isBreak(c) -> { out.add(sb.toString()); sb.clear() }
            }
        }
        if (sb.isNotBlank()) out.add(sb.toString())
        return out
    }

    /** 长句按逗号贪心切：每块尽量接近 maxLen 但不超过；末尾逗号换成句号。 */
    private fun splitLong(s: String, soft: String, maxLen: Int): List<String> {
        val parts = cut(s) { it in soft }
        if (parts.size <= 1) return listOf(s)
        val out = mutableListOf<String>(); val cur = StringBuilder()
        for (p in parts) {
            if (cur.isNotEmpty() && visibleLen(cur.toString() + p) > maxLen) { out.add(cur.toString()); cur.clear() }
            cur.append(p)
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out.map { x -> if (x.isNotEmpty() && x.last() in soft) x.dropLast(1) + "。" else x }
    }

    /** 每句的估算开始时间：段内按字数线性分配（WhisperX 按时间戳重切句的简化版，BSD-2，https://github.com/m-bain/whisperX）。 */
    fun timestamps(sents: List<String>, startMs: Long, endMs: Long): List<Long> {
        val lens = sents.map { maxOf(1, visibleLen(it)) }
        val total = lens.sum().toDouble()
        val dur = maxOf(0L, endMs - startMs)
        var acc = 0
        return lens.map { l -> (startMs + dur * (acc / total)).toLong().also { acc += l } }
    }
}
