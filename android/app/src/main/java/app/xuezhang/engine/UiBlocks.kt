package app.xuezhang.engine

import org.json.JSONArray
import org.json.JSONObject

/**
 * 笔记中的「生成式交互组件」：模型在正文里输出 ```ui {json} ``` 代码块，
 * 这里把它解析成固定的、白名单内的组件。任何无法识别/校验失败的块都退回纯文本。
 */
sealed class NotePart {
    data class Text(val text: String) : NotePart()
    data class Ui(val block: UiBlock) : NotePart()
}

data class Param(val name: String, val label: String, val min: Double, val max: Double, val value: Double, val unit: String = "")

sealed class UiBlock {
    abstract val title: String

    data class Plot(
        override val title: String,
        val exprs: List<Pair<String, String>>, // label to expression
        val xMin: Double, val xMax: Double,
        val yMin: Double?, val yMax: Double?,
        val params: List<Param>,
        val xLabel: String, val yLabel: String,
    ) : UiBlock()

    data class Steps(override val title: String, val steps: List<Pair<String, String>>) : UiBlock() // text to tex

    data class Quiz(override val title: String, val q: String, val options: List<String>, val answer: Int, val explain: String) : UiBlock()

    data class Compare(override val title: String, val columns: List<String>, val rows: List<List<String>>) : UiBlock()

    data class Phasor(override val title: String, val vectors: List<PVec>, val param: Param?) : UiBlock()

    /** mag / angle 是表达式（可引用参数），angle 单位为度；from = 起点接在哪个向量的末端（-1 为原点）。 */
    data class PVec(val label: String, val mag: String, val angle: String, val from: Int = -1, val color: String = "blue",
                    val sum: List<Int> = emptyList(), val dashed: Boolean = false)
}

object UiBlocks {
    const val MAX_BLOCK_CHARS = 6000
    private val FENCE = Regex("```\\s*([A-Za-z]*)\\s*\\n([\\s\\S]*?)```")

    /** 把一段笔记正文拆成文本和交互组件。 */
    fun split(body: String): List<NotePart> {
        val out = mutableListOf<NotePart>()
        var last = 0
        for (m in FENCE.findAll(body)) {
            val before = body.substring(last, m.range.first)
            if (before.isNotBlank()) out.add(NotePart.Text(before.trim()))
            val lang = m.groupValues[1].lowercase()
            val raw = m.groupValues[2].trim()
            val blk = if (lang == "ui" || lang == "json" || lang.isEmpty()) parse(raw) else null
            out.add(if (blk != null) NotePart.Ui(blk) else NotePart.Text(fallbackText(raw)))
            last = m.range.last + 1
        }
        val tail = body.substring(last.coerceAtMost(body.length))
        if (tail.isNotBlank()) out.add(NotePart.Text(tail.trim()))
        return out
    }

    /** 无法渲染的块：尽量把 JSON 里的文字取出来，避免整坨 JSON 糊在笔记里。 */
    fun fallbackText(raw: String): String {
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return raw.take(800)
        val parts = mutableListOf<String>()
        fun walk(v: Any?) {
            when (v) {
                is String -> if (v.isNotBlank() && v.length > 1 && !v.matches(Regex("[a-z_]+"))) parts.add(v)
                is JSONObject -> v.keys().forEach { k -> if (k != "type" && k != "color") walk(v.opt(k)) }
                is JSONArray -> for (i in 0 until v.length()) walk(v.opt(i))
            }
        }
        walk(o)
        return parts.joinToString("\n").take(800).ifBlank { raw.take(800) }
    }

    fun parse(raw: String): UiBlock? {
        if (raw.length > MAX_BLOCK_CHARS) return null
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        return runCatching {
            when (o.optString("type").lowercase()) {
                "plot" -> plot(o)
                "steps" -> steps(o)
                "quiz" -> quiz(o)
                "compare", "table" -> compare(o)
                "phasor" -> phasor(o)
                else -> null
            }
        }.getOrNull()
    }

    private fun JSONArray?.strs(): List<String> = if (this == null) emptyList() else (0 until length()).map { opt(it)?.toString().orEmpty() }

    private fun param(o: JSONObject?): Param? {
        o ?: return null
        val name = o.optString("name").trim()
        if (!name.matches(Regex("[A-Za-z_][A-Za-z0-9_]{0,11}"))) return null
        val min = o.optDouble("min", Double.NaN); val max = o.optDouble("max", Double.NaN)
        if (!min.isFinite() || !max.isFinite() || max <= min) return null
        val v = o.optDouble("value", (min + max) / 2).let { if (it.isFinite()) it.coerceIn(min, max) else (min + max) / 2 }
        return Param(name, o.optString("label", name).take(16), min, max, v, o.optString("unit").take(6))
    }

    private fun plot(o: JSONObject): UiBlock.Plot? {
        val params = (o.optJSONArray("params") ?: JSONArray()).let { a -> (0 until minOf(a.length(), 2)).mapNotNull { param(a.optJSONObject(it)) } }
        val allowed = params.map { it.name }.toSet() + setOf("x", "pi", "e")
        val exprs = mutableListOf<Pair<String, String>>()
        o.optString("expr").takeIf { it.isNotBlank() }?.let { exprs.add(o.optString("label", "y") to it) }
        o.optJSONArray("series")?.let { a ->
            for (i in 0 until minOf(a.length(), 3)) a.optJSONObject(i)?.let { s ->
                s.optString("expr").takeIf { it.isNotBlank() }?.let { exprs.add(s.optString("label", "y${i + 1}") to it) }
            }
        }
        if (exprs.isEmpty()) return null
        // 每个表达式都必须能解析，且只用到 x 与已声明的参数
        exprs.forEach { (_, e) -> val p = Expr.parse(e); if (!allowed.containsAll(Expr.vars(p))) return null }
        val xr = o.optJSONArray("x")
        val x0 = xr?.optDouble(0, 0.0) ?: o.optDouble("xmin", 0.0)
        val x1 = xr?.optDouble(1, 1.0) ?: o.optDouble("xmax", 1.0)
        if (!x0.isFinite() || !x1.isFinite() || x1 <= x0) return null
        val yr = o.optJSONArray("y")
        val y0 = yr?.optDouble(0, Double.NaN)?.takeIf { it.isFinite() }
        val y1 = yr?.optDouble(1, Double.NaN)?.takeIf { it.isFinite() }
        return UiBlock.Plot(o.optString("title"), exprs.take(3), x0, x1, if (y0 != null && y1 != null && y1 > y0) y0 else null,
            if (y0 != null && y1 != null && y1 > y0) y1 else null, params, o.optString("xlabel", "x").take(12), o.optString("ylabel", "y").take(12))
    }

    private fun steps(o: JSONObject): UiBlock.Steps? {
        val a = o.optJSONArray("steps") ?: return null
        val st = (0 until minOf(a.length(), 12)).mapNotNull { i ->
            when (val v = a.opt(i)) {
                is JSONObject -> (v.optString("text") to v.optString("tex")).takeIf { it.first.isNotBlank() || it.second.isNotBlank() }
                is String -> v to ""
                else -> null
            }
        }
        return if (st.isEmpty()) null else UiBlock.Steps(o.optString("title"), st)
    }

    private fun quiz(o: JSONObject): UiBlock.Quiz? {
        val opts = o.optJSONArray("options").strs().filter { it.isNotBlank() }.take(6)
        val ans = o.optInt("answer", -1)
        val q = o.optString("q").ifBlank { o.optString("question") }
        if (q.isBlank() || opts.size < 2 || ans !in opts.indices) return null
        return UiBlock.Quiz(o.optString("title"), q, opts, ans, o.optString("explain"))
    }

    private fun compare(o: JSONObject): UiBlock.Compare? {
        val cols = o.optJSONArray("columns").strs()
        if (cols.size !in 2..5) return null
        val rows = (o.optJSONArray("rows") ?: return null).let { a ->
            (0 until minOf(a.length(), 12)).mapNotNull { i -> a.optJSONArray(i)?.strs()?.let { r -> (r + List(cols.size) { "" }).take(cols.size) } }
        }
        if (rows.isEmpty()) return null
        return UiBlock.Compare(o.optString("title"), cols, rows)
    }

    private fun phasor(o: JSONObject): UiBlock.Phasor? {
        val p = param(o.optJSONObject("param"))
        val allowed = setOfNotNull(p?.name) + setOf("pi", "e")
        val a = o.optJSONArray("vectors") ?: return null
        val vs = (0 until minOf(a.length(), 8)).mapNotNull { i ->
            val v = a.optJSONObject(i) ?: return@mapNotNull null
            val sum = v.optJSONArray("sum")?.let { s -> (0 until s.length()).map { s.optInt(it, -1) } } ?: emptyList()
            val mag = v.opt("mag")?.toString() ?: "1"
            val ang = v.opt("angle")?.toString() ?: "0"
            if (sum.isEmpty()) {
                listOf(mag, ang).forEach { e -> val px = Expr.parse(e); if (!allowed.containsAll(Expr.vars(px))) return null }
            } else if (sum.any { it !in 0 until i }) return null
            val from = v.optInt("from", -1)
            if (from >= i) return null
            UiBlock.PVec(v.optString("label").take(10), mag, ang, from, v.optString("color", "blue"), sum, v.optBoolean("dashed"))
        }
        if (vs.isEmpty()) return null
        return UiBlock.Phasor(o.optString("title"), vs, p)
    }

    /** 计算相量端点（复平面坐标）；返回每个向量的 (起点, 终点)。 */
    fun phasorPoints(b: UiBlock.Phasor, paramValue: Double?): List<Pair<DoubleArray, DoubleArray>> {
        val env = HashMap<String, Double>()
        if (b.param != null && paramValue != null) env[b.param.name] = paramValue
        val res = ArrayList<Pair<DoubleArray, DoubleArray>>()
        b.vectors.forEachIndexed { i, v ->
            val start = if (v.from in 0 until i) res[v.from].second else doubleArrayOf(0.0, 0.0)
            val end = if (v.sum.isNotEmpty()) {
                var x = 0.0; var y = 0.0
                v.sum.forEach { k -> val (s, e) = res[k]; x += e[0] - s[0]; y += e[1] - s[1] }
                doubleArrayOf(start[0] + x, start[1] + y)
            } else {
                val m = Expr.safeEval(Expr.tryParse(v.mag), env).let { if (it.isNaN()) 0.0 else it }
                val an = Math.toRadians(Expr.safeEval(Expr.tryParse(v.angle), env).let { if (it.isNaN()) 0.0 else it })
                doubleArrayOf(start[0] + m * kotlin.math.cos(an), start[1] + m * kotlin.math.sin(an))
            }
            res.add(start to end)
        }
        return res
    }
}
