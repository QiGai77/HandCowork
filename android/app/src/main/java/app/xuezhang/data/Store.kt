package app.xuezhang.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().take(8)

/** 一次上课时段：星期(1=周一..7=周日)、起止节次、教室。 */
data class Slot(val weekday: Int, val start: Int, val end: Int, val room: String = "")

data class Course(
    val id: String = newId(),
    val name: String,
    val teacher: String = "",
    val time: String = "",
    val room: String = "",
    val colorIdx: Int = 0,
    val defaultMode: Int = 1,           // 0 原声 1 学长
    val hotwords: List<String> = emptyList(),
    val slides: List<Slide> = emptyList(),
    val slideFile: String = "",         // 课件显示名
    val slots: List<Slot> = emptyList(),
    val weeks: String = "",
    val cover: String = "",             // "" 自动 / preset:电机学 / file:/abs/path / generic
    val slidePath: String = "",         // 本地课件副本（用于渲染页面）
    val source: String = "",            // demo / bilibili:BV...
)

/** 笔记的一节：锚定到课件页码，正文可含 ```ui 交互组件。 */
data class NoteSection(val title: String, val pages: List<Int> = emptyList(), val body: String = "",
                       val atMs: Long = -1,         // 录播课：这一节在视频里开始的时间（毫秒），-1 未知
                       val frame: String = "")       // 录播课：这一节对应的视频原画面截图（本机文件路径）

data class Slide(val page: Int, val title: String, val text: String, val formulas: List<String> = emptyList())

data class Segment(
    val id: String = newId(),
    val startMs: Long,
    val page: Int = 0,
    val raw: String = "",
    val voice: String = "",      // 原声：过滤后的老师原话（带标注）
    val senior: String = "",     // 学长：大白话
    val fillers: Int = 0,
    val repeats: Int = 0,
    val exam: Boolean = false,
    val pending: Boolean = false,
    val offTopic: Boolean = false, // 与课程无关的闲聊：学长模式跳过，原声模式淡化折叠
)

data class Mnemonic(val title: String, val body: String)
data class Card(val q: String, val a: String, val tag: String = "考点", var level: Int = 0)
data class Node(val label: String, val parent: Int = -1, val detail: String = "")

data class Lecture(
    val id: String = newId(),
    val courseId: String,
    val title: String,
    val date: Long = System.currentTimeMillis(),
    val durationMs: Long = 0,
    val segments: List<Segment> = emptyList(),
    val notes: String = "",
    val mnemonics: List<Mnemonic> = emptyList(),
    val nodes: List<Node> = emptyList(),
    val cards: List<Card> = emptyList(),
    val status: Int = 0, // 0 recording 1 generating 2 done 3 failed
    val sections: List<NoteSection> = emptyList(),
    val source: String = "",
)

data class Settings(
    val asrBase: String = "https://api.siliconflow.cn/v1",
    val asrKey: String = "",
    val asrModel: String = "FunAudioLLM/SenseVoiceSmall",
    val llmBase: String = "https://api.siliconflow.cn/v1",
    val llmKey: String = "",
    val llmModel: String = "deepseek-ai/DeepSeek-V3",
    val defaultMode: Int = 1,
    val chunkSec: Int = 15,
    val confirmFill: Boolean = true,
    val latex: Boolean = true,
    val visionModel: String = "Qwen/Qwen2.5-VL-72B-Instruct",
    val imageModel: String = "Kwai-Kolors/Kolors",
    val provider: String = "siliconflow",
    val splashHidden: String = "",      // 勾选「本版本不再显示」时记录的 versionName
    val account: String = "",           // 本地登录态（登录后端未定，仅本机）："" 未选择 / guest / 手机号
    val testTarget: Int = 0,            // 闪电测试对象 0 语音 1 大模型
    val demoSeeded: Int = 0,
    val llmAuth: String = "key",        // key / chatgpt（ChatGPT 账号）/ claude（Claude 账号）
    val syncUri: String = "",           // 云同步：上次备份到的文档（SAF，可在 Google 云端硬盘 / 手机云盘里）
    val davUrl: String = "",
    val davUser: String = "",
    val davPass: String = "",
    val lastSync: Long = 0,
    val noteDepth: Int = 1,             // 笔记详略：0 简单 / 1 详细
    val promptOverrides: Map<String, String> = emptyMap(), // 自定义系统提示词（键见 Prompts.catalog）
    val gClientId: String = "",         // Google OAuth 客户端 ID（留空用编译时 BuildConfig.GOOGLE_CLIENT_ID）
    val gClientSecret: String = "",     // 「桌面应用」类型客户端附带的 secret（Google 视其为非机密，可留空）
    val gFolderId: String = "",         // Drive 上「课堂学长备份」文件夹 id
    val gLastSync: Long = 0,
    val navOrder: String = "0,1,2,3",   // 底部导航栏顺序（tab id：0 课程 1 上课 2 笔记 3 我的），第一个是启动页
    val noteView: Int = 0,              // 笔记显示：0 书架 / 1 列表
)

object Store {
    lateinit var dir: File
    val courses = mutableStateListOf<Course>()
    val lectures = mutableStateListOf<Lecture>()
    val settings = mutableStateOf(Settings())

    fun init(ctx: Context) {
        dir = File(ctx.filesDir, "data").apply { mkdirs() }
        app.xuezhang.engine.Log.init(dir)
        courses.clear(); lectures.clear()
        load()
        if (courses.isEmpty()) seed()
        // 旧版本课程只有「周六 14:00」这样的文本：迁移成上课时段
        for (i in courses.indices) { val m = app.xuezhang.engine.Schedule.migrate(courses[i]); if (m != courses[i]) courses[i] = m }
        if (settings.value.demoSeeded < 4) {
            runCatching { Demo.seed(ctx) }
            settings.value = settings.value.copy(demoSeeded = 4)
        }
        save()
    }

    fun course(id: String) = courses.firstOrNull { it.id == id }
    fun lecture(id: String) = lectures.firstOrNull { it.id == id }

    fun upsertCourse(c: Course) {
        val i = courses.indexOfFirst { it.id == c.id }
        if (i >= 0) courses[i] = c else courses.add(c); save()
    }
    fun deleteCourse(id: String) { courses.removeAll { it.id == id }; lectures.removeAll { it.courseId == id }; save() }
    fun upsertLecture(l: Lecture, persist: Boolean = true) {
        val i = lectures.indexOfFirst { it.id == l.id }
        if (i >= 0) lectures[i] = l else lectures.add(0, l)
        if (persist) save()
    }
    fun deleteLecture(id: String) { lectures.removeAll { it.id == id }; save() }
    fun updateSettings(s: Settings) { settings.value = s; save() }

    // ---------- json ----------
    private fun JSONArray.strs() = (0 until length()).map { getString(it) }
    private fun List<String>.arr() = JSONArray().also { a -> forEach { a.put(it) } }

    private fun Slide.j() = JSONObject().put("p", page).put("t", title).put("x", text).put("f", formulas.arr())
    private fun JSONObject.slide() = Slide(optInt("p"), optString("t"), optString("x"), optJSONArray("f")?.strs() ?: emptyList())

    private fun Course.j() = JSONObject().put("id", id).put("name", name).put("teacher", teacher).put("time", time)
        .put("room", room).put("color", colorIdx).put("mode", defaultMode).put("hot", hotwords.arr())
        .put("slides", JSONArray().also { a -> slides.forEach { a.put(it.j()) } }).put("sf", slideFile)
        .put("slots", JSONArray().also { a -> slots.forEach { a.put(JSONObject().put("w", it.weekday).put("s", it.start).put("e", it.end).put("r", it.room)) } })
        .put("weeks", weeks).put("cover", cover).put("sp", slidePath).put("src", source)
    fun JSONObject.course() = Course(
        optString("id").ifBlank { newId() }, optString("name"), optString("teacher"), optString("time"), optString("room"),
        optInt("color"), optInt("mode", 1), optJSONArray("hot")?.strs() ?: emptyList(),
        optJSONArray("slides")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).slide() } } ?: emptyList(),
        optString("sf"),
        optJSONArray("slots")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { Slot(it.optInt("w"), it.optInt("s"), it.optInt("e"), it.optString("r")) } } ?: emptyList(),
        optString("weeks"), optString("cover"), optString("sp"), optString("src"))

    private fun Segment.j() = JSONObject().put("id", id).put("s", startMs).put("p", page).put("r", raw).put("v", voice)
        .put("x", senior).put("f", fillers).put("rp", repeats).put("e", exam).put("ot", offTopic)
    private fun JSONObject.seg() = Segment(optString("id"), optLong("s"), optInt("p"), optString("r"), optString("v"),
        optString("x"), optInt("f"), optInt("rp"), optBoolean("e"), offTopic = optBoolean("ot"))

    private fun Lecture.j() = JSONObject().put("id", id).put("c", courseId).put("t", title).put("d", date).put("du", durationMs)
        .put("segs", JSONArray().also { a -> segments.forEach { a.put(it.j()) } }).put("n", notes)
        .put("m", JSONArray().also { a -> mnemonics.forEach { a.put(JSONObject().put("t", it.title).put("b", it.body)) } })
        .put("nodes", JSONArray().also { a -> nodes.forEach { a.put(JSONObject().put("l", it.label).put("p", it.parent).put("d", it.detail)) } })
        .put("cards", JSONArray().also { a -> cards.forEach { a.put(JSONObject().put("q", it.q).put("a", it.a).put("t", it.tag).put("lv", it.level)) } })
        .put("st", if (status == 0 || status == 1) 3 else status)
        .put("secs", JSONArray().also { a -> sections.forEach { a.put(JSONObject().put("t", it.title).put("p", JSONArray(it.pages)).put("b", it.body).put("at", it.atMs).put("fr", it.frame)) } })
        .put("src", source)
    fun JSONObject.lec(): Lecture {
        fun arr(k: String) = optJSONArray(k) ?: JSONArray()
        fun <T> JSONArray.map(f: (JSONObject) -> T) = (0 until length()).map { f(getJSONObject(it)) }
        return Lecture(optString("id"), optString("c"), optString("t"), optLong("d"), optLong("du"),
            arr("segs").map { it.seg() }, optString("n"),
            arr("m").map { Mnemonic(it.optString("t"), it.optString("b")) },
            arr("nodes").map { Node(it.optString("l"), it.optInt("p", -1), it.optString("d")) },
            arr("cards").map { Card(it.optString("q"), it.optString("a"), it.optString("t", "考点"), it.optInt("lv")) },
            optInt("st", 2),
            arr("secs").map { o -> NoteSection(o.optString("t"), o.optJSONArray("p")?.let { p -> (0 until p.length()).map { p.optInt(it) }.filter { it > 0 } } ?: emptyList(), o.optString("b"), o.optLong("at", -1), o.optString("fr")) },
            optString("src"))
    }
    fun Settings.j() = JSONObject().put("ab", asrBase).put("ak", asrKey).put("am", asrModel).put("lb", llmBase)
        .put("lk", llmKey).put("lm", llmModel).put("dm", defaultMode).put("cs", chunkSec).put("cf", confirmFill).put("lx", latex)
        .put("vm", visionModel).put("im", imageModel).put("pv", provider).put("sh", splashHidden).put("acc", account)
        .put("tt", testTarget).put("demo", demoSeeded).put("la", llmAuth).put("su", syncUri).put("du", davUrl).put("dus", davUser)
        .put("dp", davPass).put("ls", lastSync).put("nd", noteDepth)
        .put("po", JSONObject().also { o -> promptOverrides.forEach { (k, v) -> o.put(k, v) } })
        .put("gc", gClientId).put("gs", gClientSecret).put("gf", gFolderId).put("gl", gLastSync)
        .put("no", app.xuezhang.engine.NavOrder.encode(app.xuezhang.engine.NavOrder.parse(navOrder))).put("nv", noteView)
    fun JSONObject.settings(): Settings { val d = Settings(); return Settings(optString("ab", d.asrBase), optString("ak"), optString("am", d.asrModel),
        optString("lb", d.llmBase), optString("lk"), optString("lm", d.llmModel), optInt("dm", 1),
        optInt("cs", 15), optBoolean("cf", true), optBoolean("lx", true),
        optString("vm", d.visionModel).ifBlank { d.visionModel }, optString("im", d.imageModel).ifBlank { d.imageModel },
        optString("pv", d.provider), optString("sh"), optString("acc"), optInt("tt"), optInt("demo"),
        optString("la", "key"), optString("su"), optString("du"), optString("dus"), optString("dp"), optLong("ls"), optInt("nd", 1),
        optJSONObject("po")?.let { o -> o.keys().asSequence().associateWith { o.optString(it) }.filterValues { it.isNotBlank() } } ?: emptyMap(),
        optString("gc"), optString("gs"), optString("gf"), optLong("gl"),
        app.xuezhang.engine.NavOrder.encode(app.xuezhang.engine.NavOrder.parse(optString("no", app.xuezhang.engine.NavOrder.DEFAULT))), optInt("nv", 0)) }

    /** 云同步用的数据包：课程 + 笔记（不含 API Key 和登录令牌）。 */
    fun exportBundle(): JSONObject = JSONObject().put("app", "xuezhang").put("v", 1).put("at", System.currentTimeMillis())
        .put("courses", JSONArray().also { a -> courses.forEach { a.put(it.j()) } })
        .put("lectures", JSONArray().also { a -> lectures.forEach { a.put(it.j()) } })

    /** 合并导入：同 id 覆盖，其余新增。pathMap 把备份里的文件名映射成本机路径。返回 (课程数, 笔记数)。 */
    fun importBundle(o: JSONObject, fix: (Course) -> Course = { it }, fixLec: (Lecture) -> Lecture = { it }): Pair<Int, Int> {
        if (o.optString("app") != "xuezhang") throw IllegalArgumentException("这不是课堂学长的备份文件")
        var nc = 0; var nl = 0
        o.optJSONArray("courses")?.let { a -> for (i in 0 until a.length()) { val c = fix(a.getJSONObject(i).course()); val k = courses.indexOfFirst { it.id == c.id }; if (k >= 0) courses[k] = c else courses.add(c); nc++ } }
        o.optJSONArray("lectures")?.let { a -> for (i in 0 until a.length()) { val l = fixLec(a.getJSONObject(i).lec()); val k = lectures.indexOfFirst { it.id == l.id }; if (k >= 0) lectures[k] = l else lectures.add(l); nl++ } }
        save()
        return nc to nl
    }

    @Synchronized
    fun save() {
        val o = JSONObject()
            .put("courses", JSONArray().also { a -> courses.forEach { a.put(it.j()) } })
            .put("lectures", JSONArray().also { a -> lectures.forEach { a.put(it.j()) } })
            .put("settings", settings.value.j())
        val tmp = File(dir, "db.json.tmp"); tmp.writeText(o.toString()); tmp.renameTo(File(dir, "db.json"))
    }

    private fun load() {
        val f = File(dir, "db.json"); if (!f.exists()) return
        runCatching {
            val o = JSONObject(f.readText())
            o.optJSONArray("courses")?.let { a -> for (i in 0 until a.length()) courses.add(a.getJSONObject(i).course()) }
            o.optJSONArray("lectures")?.let { a -> for (i in 0 until a.length()) lectures.add(a.getJSONObject(i).lec()) }
            o.optJSONObject("settings")?.let { settings.value = it.settings().let { st ->
                // v0.0.6：ChatGPT 账号不再提供 gpt-5.x，迁移到当前默认模型
                if (st.llmAuth == "chatgpt" && st.llmModel.startsWith("gpt-5")) st.copy(llmModel = "gpt-6.1-sol", visionModel = if (st.visionModel.startsWith("gpt-5")) "gpt-6.1-sol" else st.visionModel) else st } }
        }
    }

    private fun seed() {
        val c = Course(name = "高等数学 A（二）", teacher = "李老师", time = "周六 14:00", room = "教三 302", colorIdx = 0,
            hotwords = listOf("偏导数", "全微分", "链式法则", "梯度", "方向导数", "拉格朗日乘数"),
            slides = listOf(Slide(18, "7.4 方向导数与梯度", "", listOf("\\frac{\\partial f}{\\partial l}=\\nabla f\\cdot\\vec{e}"))))
        courses.add(c)
        courses.add(Course(name = "线性代数", teacher = "王老师", time = "周一 8:00", room = "教二 105", colorIdx = 1))
        courses.add(Course(name = "大学物理 B", teacher = "陈老师", time = "周二 10:10", room = "教四 210", colorIdx = 2))
        lectures.add(
            Lecture(courseId = c.id, title = "方向导数与梯度（示例）", durationMs = 98 * 60_000L, status = 2,
                segments = listOf(
                    Segment(startMs = 31 * 60_000L + 2_000, page = 17,
                        voice = "我们来看[f:方向]导数的定义。函数 \$f\$ 在点 \$P_0\$ 沿方向 \$l\$ 的方向导数，定义为\$\$\\left.\\frac{\\partial f}{\\partial l}\\right|_{P_0}=\\lim_{t\\to0^+}\\frac{f(P_0+t\\vec e)-f(P_0)}{t}\$\$",
                        senior = "老师在讲[b:方向导数]：就是站在一点，朝某个方向走一小步，函数变了多少、变得多快。", fillers = 12, repeats = 3),
                    Segment(startMs = 31 * 60_000L + 48_000, page = 18, exam = true,
                        voice = "如果 \$f\$ 可微，那么 [b:方向导数等于梯度与单位向量的数量积]，即 \$\\frac{\\partial f}{\\partial l}=\\nabla f\\cdot\\vec e\$。[r:这个结论考试一定会考]，大家[f:务必]记住。",
                        senior = "[r:这块必考！]老师连说了三遍。[b:梯度方向]就是函数涨得最快的方向，涨多快？就是它的长度 \$|\\nabla f|\$。", fillers = 9, repeats = 4),
                    Segment(startMs = 32 * 60_000L + 10_000, page = 18,
                        voice = "这里的 \$\\vec e\$ [u:必须是单位向量]，不是的话要先除以它的[f:模]。",
                        senior = "给你个口诀：[o:「梯度指山顶，点乘看方向」]。注意 \$\\vec e\$ [u:一定要先单位化]，很多人这里丢分。", fillers = 4, repeats = 1),
                ),
                notes = "**1. 定义** [b:方向导数] 描述 \$f\$ 沿方向 \$l\$ 的变化率：\$\$\\frac{\\partial f}{\\partial l}=\\nabla f\\cdot\\vec e=f_x\\cos\\alpha+f_y\\cos\\beta\$\$\n\n**2. 梯度** \$\\nabla f=(f_x,\\,f_y)\$，[r:沿梯度方向，方向导数最大]，最大值为 [r:\$|\\nabla f|\$]。\n\n**3. 易错** 求 \$\\frac{\\partial f}{\\partial l}\$ 前必须把 \$\\vec l\$ [u:单位化]：\$\\vec e=\\frac{\\vec l}{|\\vec l|}\$",
                mnemonics = listOf(
                    Mnemonic("梯度指山顶，点乘看方向", "\$\\nabla f\$ 指向最陡上坡；方向导数是它在 \$\\vec e\$ 上的 [u:投影]。"),
                    Mnemonic("先单位，再点乘", "\$\\vec e=\\frac{\\vec l}{|\\vec l|}\$，不单位化 [r:必错]。")),
                nodes = listOf(Node("多元函数微分"), Node("方向导数", 0, "沿方向的变化率"), Node("梯度", 0, "\$\\nabla f=(f_x,f_y)\$"),
                    Node("最大变化率", 2, "\$|\\nabla f|\$"), Node("偏导数", 0), Node("单位向量", 1, "\$\\vec e=\\vec l/|\\vec l|\$")),
                cards = listOf(Card("已知 \$f(x,y)=x^2+y^2\$，求在点 \$(1,1)\$ 处沿 \$\\vec l=(3,4)\$ 的方向导数。",
                    "\$\\nabla f=(2,2)\$，\$\\vec e=(\\frac{3}{5},\\frac{4}{5})\$\$\$\\frac{\\partial f}{\\partial l}=2\\cdot\\frac{3}{5}+2\\cdot\\frac{4}{5}=\\frac{14}{5}\$\$"),
                    Card("梯度方向的方向导数等于多少？", "[r:\$|\\nabla f|\$]，即梯度的模，是该点的最大变化率。"))
            )
        )
        save()
    }
}
