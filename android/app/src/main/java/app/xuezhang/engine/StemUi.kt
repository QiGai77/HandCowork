package app.xuezhang.engine

import app.xuezhang.data.Card
import app.xuezhang.data.NoteSection
import org.json.JSONArray
import org.json.JSONObject

/**
 * v0.0.7.6：工科 / 理科课程的笔记必须「图文并茂」——3~5 个交互组件，至少 1 个函数图像。
 * 1) 生成时在提示词里强制要求；2) 生成后校验，不够就让模型补；3) 还不够就从公式本地生成兜底；4) 最多保留 5 个。
 */
object StemUi {
    const val MIN = 3
    const val MAX = 5

    private val STEM = listOf(
        "信号", "系统", "电路", "电子", "电工", "电机", "电力", "微机", "单片机", "嵌入式", "数学", "高数", "微积分", "线性代数", "线代", "概率", "统计", "离散",
        "复变", "数值", "物理", "力学", "热", "流体", "光学", "量子", "控制", "自控", "自动化", "通信", "通讯", "电磁", "机械", "材料", "化学", "计算机", "算法",
        "数据结构", "编程", "程序", "操作系统", "网络", "数据库", "数电", "模电", "数字逻辑", "传感", "机器人", "工程", "测量", "测试", "结构", "建筑", "土木",
        "Signal", "Circuit", "Math", "Physics", "Calculus", "Algebra", "Control", "Algorithm",
    )
    private val NON_STEM = listOf("思想", "政治", "马克思", "毛泽东", "习近平", "思修", "近代史", "形势与政策", "英语", "语文", "文学", "历史", "法律", "法学", "艺术", "体育", "心理", "管理学", "社会")

    /** 课程名（或模型给的学科标记）判断是否工科 / 理科。 */
    fun isStem(courseName: String?, subjectFlag: String? = null): Boolean {
        subjectFlag?.lowercase()?.let { f -> if (f in setOf("stem", "engineering", "science", "工科", "理科")) return true; if (f in setOf("humanities", "文科")) return false }
        val n = courseName?.replace(Regex("\\s"), "").orEmpty()
        if (n.isBlank()) return false
        if (NON_STEM.any { n.contains(it) }) return false
        return STEM.any { n.contains(it, ignoreCase = true) }
    }

    data class Count(val total: Int, val plots: Int)

    fun count(bodies: List<String>): Count {
        var t = 0; var p = 0
        bodies.forEach { b -> runCatching { UiBlocks.split(b) }.getOrDefault(emptyList()).forEach { if (it is NotePart.Ui) { t++; if (it.block is UiBlock.Plot) p++ } } }
        return Count(t, p)
    }

    fun needsMore(sections: List<NoteSection>): Boolean = count(sections.map { it.body }).let { it.total < MIN || it.plots < 1 }

    /** 补充组件的提示词（用户内容是各节标题与正文摘录）。 */
    fun fixPrompt(c: Count): String = """你是「课堂学长」。下面是一份工科课程笔记各节的标题和正文摘录，它的交互组件不够（现有 ${c.total} 个，其中函数图像 ${c.plots} 个）。
请为最重要的概念补充 ${(MIN - c.total).coerceAtLeast(if (c.plots == 0) 1 else 0).coerceAtLeast(1)}~${(MAX - c.total).coerceAtLeast(1)} 个交互组件，至少 ${if (c.plots == 0) "1~2" else "0~1"} 个是函数图像（plot，带可拖动参数）。
输出 JSON：{"blocks":[{"section":节下标(从0开始),"caption":"一两句图注：这张图/这个组件说明了什么、拖动参数时看什么","ui":{组件 JSON}}]}
组件 JSON 只允许：
- {"type":"plot","title":"标题","expr":"A*sin(2*pi*f*x)","x":[0,1],"y":[-2,2],"params":[{"name":"A","label":"幅值","min":0,"max":2,"value":1}],"xlabel":"t","ylabel":"u","caption":"图注"}
  expr 只能用 x、params 里声明的参数（最多 2 个，英文名）、数字、+ - * / ^、括号和 sin cos tan exp ln log sqrt abs min max pow；可用 "series":[{"label":"…","expr":"…"}] 画 2~3 条曲线。离散序列 x[n] 可用 floor 等表达，频率响应、指数衰减、阶跃响应、积分面积都可画成曲线。
- {"type":"steps","title":"标题","steps":[{"text":"这一步在做什么","tex":"LaTeX，不带 $"}]}
- {"type":"quiz","q":"题目","options":["A","B","C"],"answer":正确下标,"explain":"解析"}
只输出 JSON。"""

    /** 把模型返回的补充组件插进对应小节（图注写在组件前）。 */
    fun applyFix(sections: List<NoteSection>, o: JSONObject?): List<NoteSection> {
        val a = o?.optJSONArray("blocks") ?: return sections
        val add = HashMap<Int, MutableList<String>>()
        for (i in 0 until a.length()) {
            val b = a.optJSONObject(i) ?: continue
            val ui = b.optJSONObject("ui") ?: continue
            if (UiBlocks.parse(ui.toString()) == null) continue
            val si = b.optInt("section", 0).coerceIn(0, maxOf(0, sections.size - 1))
            val cap = b.optString("caption").ifBlank { ui.optString("caption") }
            add.getOrPut(si) { mutableListOf() }.add((if (cap.isNotBlank()) "**图注**：$cap\n\n" else "") + "```ui\n$ui\n```")
        }
        if (add.isEmpty() || sections.isEmpty()) return sections
        return sections.mapIndexed { i, s -> add[i]?.let { s.copy(body = s.body.trimEnd() + "\n\n" + it.joinToString("\n\n")) } ?: s }
    }

    // ---------- 本地兜底 ----------

    /** 从 LaTeX 公式里尽量转出一个可画的表达式（自变量统一成 x，其余单字母当参数）。 */
    fun texToExpr(tex: String): Pair<String, List<String>>? {
        var s = tex.substringAfter('=', tex).let { if (tex.count { it == '=' } >= 1) it else tex }.trim()
        if (s.length > 120 || s.isBlank()) return null
        s = s.replace("\\left", "").replace("\\right", "").replace("\\cdot", "*").replace("\\times", "*").replace("\\,", "").replace("\\!", "")
            .replace("\\pi", "pi").replace("\\omega", "w").replace("\\Omega", "w").replace("\\tau", "T").replace("\\alpha", "a").replace("\\varphi", "p").replace("\\phi", "p")
            .replace("\\theta", "x").replace("\\sin", "sin").replace("\\cos", "cos").replace("\\tan", "tan").replace("\\exp", "exp").replace("\\ln", "ln").replace("\\log", "log")
        repeat(4) { s = s.replace(Regex("\\\\frac\\{([^{}]*)\\}\\{([^{}]*)\\}"), "(($1)/($2))") }
        repeat(3) { s = s.replace(Regex("\\\\sqrt\\{([^{}]*)\\}"), "sqrt($1)") }
        s = s.replace(Regex("\\^\\{([^{}]*)\\}"), "^($1)").replace(Regex("_\\{[^{}]*\\}"), "").replace(Regex("_[A-Za-z0-9]"), "")
        s = s.replace("{", "(").replace("}", ")").replace("\\", "")
        // 自变量：t / n / ω(w)… 统一为 x
        s = s.replace(Regex("(?<![A-Za-z])[tn](?![A-Za-z])"), "x")
        // 隐式乘法：2x → 2*x，)( → )*(，x( → x*(
        s = s.replace(Regex("([A-Za-z0-9)])\\s+(?=[A-Za-z0-9(])"), "$1*").replace(Regex("\\s+"), "")
        s = s.replace(Regex("(\\d)([A-Za-z(])"), "$1*$2").replace(")(", ")*(").replace(Regex("(?<![A-Za-z])([A-Za-z])\\("), "$1*(")
        val e = Expr.tryParse(s) ?: return null
        val vs = Expr.vars(e) - setOf("pi", "e")
        if ("x" !in vs) return null
        val params = (vs - "x").toList()
        if (params.size > 2 || params.any { !it.matches(Regex("[A-Za-z]")) }) return null
        return s to params
    }

    private fun plotJson(title: String, expr: String, params: List<String>, caption: String, x0: Double = 0.0, x1: Double = 6.0): String {
        val o = JSONObject().put("type", "plot").put("title", title).put("expr", expr).put("x", JSONArray().put(x0).put(x1))
            .put("xlabel", "x").put("ylabel", "y").put("caption", caption)
        val ps = JSONArray(); params.forEach { p -> ps.put(JSONObject().put("name", p).put("label", "参数 $p").put("min", 0.2).put("max", 3).put("value", 1)) }
        o.put("params", ps)
        return o.toString()
    }

    /** 按学科给一张通用但有意义的函数图像。 */
    fun templatePlot(courseName: String): String {
        val n = courseName
        return when {
            n.contains("信号") || n.contains("通信") -> plotJson("正弦信号 x(t)=A·sin(2πft)", "A*sin(2*pi*f*x)", listOf("A", "f"), "拖动幅值 A 与频率 f：A 决定波峰高度，f 越大一秒内振荡次数越多（周期 T=1/f）。", 0.0, 2.0)
            n.contains("控制") || n.contains("自动化") -> plotJson("一阶系统阶跃响应 y(t)=K(1-e^{-t/T})", "K*(1-exp(-x/T))", listOf("K", "T"), "时间常数 T 越小响应越快；t=T 时达到稳态值的 63.2%，约 4T 进入 2% 误差带。", 0.0, 10.0)
            n.contains("电路") || n.contains("电工") || n.contains("电子") -> plotJson("RC 充电 u(t)=U(1-e^{-t/τ})", "U*(1-exp(-x/T))", listOf("U", "T"), "τ=RC 是时间常数：拖动 T 观察电容电压上升快慢，约 5τ 后视为充满。", 0.0, 10.0)
            n.contains("物理") || n.contains("力学") -> plotJson("阻尼振动 x(t)=A·e^{-βt}cos(ωt)", "A*exp(-b*x)*cos(6*x)", listOf("A", "b"), "β 越大衰减越快；β=0 时为简谐振动，振幅保持不变。", 0.0, 6.0)
            n.contains("数学") || n.contains("微积分") || n.contains("高数") -> plotJson("幂函数 y=a·x^k", "a*x^k", listOf("a", "k"), "k>1 时曲线上凹、增长越来越快；0<k<1 时增长变慢；a 只做纵向拉伸。", 0.0, 3.0)
            else -> plotJson("指数衰减 y=A·e^{-x/T}", "A*exp(-x/T)", listOf("A", "T"), "T 越大衰减越慢；x=T 时下降到初值的 36.8%。", 0.0, 10.0)
        }
    }

    private val DISPLAY = Regex("\\$\\$([\\s\\S]+?)\\$\\$")

    /** 在笔记里找第一个能转成曲线的公式。 */
    fun plotFromFormulas(sections: List<NoteSection>): Pair<Int, String>? {
        sections.forEachIndexed { i, s ->
            DISPLAY.findAll(s.body).forEach { m ->
                val tex = m.groupValues[1].trim()
                texToExpr(tex)?.let { (e, ps) -> return i to plotJson(s.title.ifBlank { "公式图像" }.take(14), e, ps, "上式的函数图像：拖动参数看曲线如何变化，对照正文理解每个量的作用。") }
            }
        }
        return null
    }

    private fun stepsFromFormulas(sections: List<NoteSection>): Pair<Int, String>? {
        sections.forEachIndexed { i, s ->
            val f = DISPLAY.findAll(s.body).map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.take(5).toList()
            if (f.size >= 2) {
                val a = JSONArray(); f.forEachIndexed { k, t -> a.put(JSONObject().put("text", if (k == 0) "从定义出发" else "第 ${k + 1} 步").put("tex", t)) }
                return i to JSONObject().put("type", "steps").put("title", "${s.title.ifBlank { "公式" }.take(12)} · 分步看").put("steps", a).toString()
            }
        }
        return null
    }

    private fun quizFromCards(cards: List<Card>): String? {
        val pool = cards.filter { it.q.isNotBlank() && it.a.isNotBlank() && it.a.length <= 60 }
        if (pool.size < 3) return null
        val q = pool[0]
        val opts = listOf(q.a, pool[1].a, pool[2].a)
        return JSONObject().put("type", "quiz").put("q", q.q).put("options", JSONArray(opts)).put("answer", 0).put("explain", q.a).toString()
    }

    /** 本地兜底：保证至少 MIN 个组件且至少 1 个函数图像；最后裁到 MAX 个。 */
    fun enforce(courseName: String, sections: List<NoteSection>, cards: List<Card>): List<NoteSection> {
        if (sections.isEmpty()) return sections
        val adds = HashMap<Int, MutableList<String>>()
        fun put(i: Int, json: String, caption: String? = null) = adds.getOrPut(i) { mutableListOf() }.add((caption?.let { "**图注**：$it\n\n" } ?: "") + "```ui\n$json\n```")
        var c = count(sections.map { it.body })
        if (c.plots < 1) {
            val p = plotFromFormulas(sections)
            if (p != null) put(p.first, p.second) else put(0, templatePlot(courseName))
            c = Count(c.total + 1, 1)
        }
        if (c.total < MIN) stepsFromFormulas(sections)?.let { put(it.first, it.second); c = c.copy(total = c.total + 1) }
        if (c.total < MIN) quizFromCards(cards)?.let { put(sections.lastIndex, it); c = c.copy(total = c.total + 1) }
        var k = 0
        while (c.total < MIN && k < 3) { // 实在没有素材：再补学科模板图（不同参数区间）
            put((k + 1).coerceAtMost(sections.lastIndex), templatePlot(if (k == 0) courseName + "控制" else courseName + "物理"))
            c = c.copy(total = c.total + 1); k++
        }
        val out = sections.mapIndexed { i, s -> adds[i]?.let { s.copy(body = s.body.trimEnd() + "\n\n" + it.joinToString("\n\n")) } ?: s }
        return cap(out)
    }

    private val FENCE = Regex("```[ \\t]*([A-Za-z]*)[ \\t]*\\r?\\n?([\\s\\S]*?)```")

    /** 最多保留 MAX 个可渲染组件（优先保留函数图像），多余的删掉。 */
    fun cap(sections: List<NoteSection>, max: Int = MAX): List<NoteSection> {
        data class B(val si: Int, val range: IntRange, val plot: Boolean)
        val all = sections.flatMapIndexed { si, s ->
            FENCE.findAll(s.body).mapNotNull { m ->
                val lang = m.groupValues[1].lowercase()
                val blk = if (lang == "ui" || lang == "json" || lang.isEmpty() || lang == "uijson") UiBlocks.parse(m.groupValues[2].trim()) else null
                blk?.let { B(si, m.range, it is UiBlock.Plot) }
            }.toList()
        }
        if (all.size <= max) return sections
        val keep = (all.filter { it.plot }.take(2) + all).distinct().take(max).toSet()
        val drop = all.filter { it !in keep }.groupBy { it.si }
        return sections.mapIndexed { si, s ->
            val d = drop[si] ?: return@mapIndexed s
            var body = s.body
            d.sortedByDescending { it.range.first }.forEach { b -> body = body.removeRange(b.range).replace(Regex("\n{3,}"), "\n\n") }
            s.copy(body = body.trim())
        }
    }
}
