package app.xuezhang

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.xuezhang.data.Course
import app.xuezhang.data.Lecture
import app.xuezhang.data.Segment
import app.xuezhang.data.Store
import app.xuezhang.engine.Live
import app.xuezhang.engine.plugin.Plugins
import app.xuezhang.engine.plugin.TranscriptHooks
import app.xuezhang.ui.LiveScreen
import app.xuezhang.ui.NT
import com.github.takahirom.roborazzi.captureRoboImage
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * v0.0.8.0 上课页截图：原声 / 学长 × 插件开 / 关（只在 -Pshots=true 时运行）。
 * 数据走真实插件管线：两份官方插件文件 → TextRules（清洗 / 热词 / 分句）→ 模拟的大模型回复 → RefinedReply。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h1000dp-xxhdpi")
class V0080ShotsTest {
    private val out = File(System.getProperty("xz.shotsDir") ?: "/workspace/work/v0080-shots").apply { mkdirs() }
    private val pluginsSrc = File("/workspace/work/hc-plugins")

    private fun installPlugins(on: Boolean, strength: String = "强") {
        val dir = File(System.getProperty("java.io.tmpdir"), "xz-plugins80-" + System.nanoTime()).apply { mkdirs() }
        listOf("skill-speech-refine", "skill-stem-formula").forEach { File(pluginsSrc, it).copyRecursively(File(dir, it), true) }
        val st = JSONObject().put("enabled", org.json.JSONArray(if (on) listOf("skill-speech-refine", "skill-stem-formula") else emptyList()))
            .put("settings", JSONObject().put("skill-speech-refine", JSONObject().put("strength", strength))).put("marketUrl", "")
        File(dir, "state.json").writeText(st.toString())
        Plugins.init(dir, "0.0.8.0")
    }

    private val replies = ArrayDeque<String>()

    private fun seed(): List<Segment> {
        Store.dir = File(System.getProperty("java.io.tmpdir"), "xz-shots80").apply { mkdirs() }
        Store.courses.clear(); Store.lectures.clear()
        val c = Course(id = "c1", name = "微机原理与接口技术", teacher = "林嵘", defaultMode = 0, hotwords = listOf("控制字", "端口地址"))
        Store.courses.add(c)
        installPlugins(true)
        TranscriptHooks.chat = { _, _ -> JSONObject(replies.removeFirst()) }
        replies.addLast("""{"sentences":[],"breaks":[],"refined":"","points":[],"question":{"q":"A 口接 LED 是输入还是输出？","a":"输出。","ts":"03:52"},"ratio":40,"page":12,"exam":false,"relevant":true}""")
        replies.addLast("""{"sentences":[],"breaks":[3],"refined":"8255 初始化是最简单的题：写对[r:端口地址（2 分）]和[r:控制字（1 分）]就行，可历年均分只有 0.6 分。8253 更难，要先判断工作方式再算初值。",
            "points":[{"type":"考点","text":"8255 初始化：地址 2 分 + 控制字 1 分"},{"type":"定义","text":"控制字：设定各端口工作方式和方向"},{"type":"公式","text":"8253 初值 ${'$'}N = f_{CLK} / f_{OUT}${'$'}"}],
            "question":{"q":"","a":"","ts":""},"ratio":46,"page":12,"exam":true,"relevant":true}""")
        val s1 = TranscriptHooks.process(c, "嗯，我先问一下，我刚刚讲的几个LED。如果外面接LED，AO接LED是输入还是输出？你们先把这些东西搞清楚。", 228_000, 244_000, 11, emptyList(), false)!!
        val s2 = TranscriptHooks.process(c, "82555的初始化其实是最简单的。每次考初始化，就是2分地址，1分控制字。你们知道每年这个8255初始化的均分是多少吗？才0.6分。地址和控制字你们都能写，这是最简单的。像8253，还得知道工作方式，再算初值。", 245_000, 273_000, 12, listOf(s1), false)!!
        // 本页小结（正常由翻页时的 summary 提示词生成）
        val e2 = app.xuezhang.engine.plugin.TranscriptExt.parse(s2.ext)!!
        val s2b = s2.copy(ext = e2.copy(summary = app.xuezhang.engine.plugin.TranscriptExt.Summary(12, "送分题别丢：先写地址，再写控制字。")).toJson())
        val pending = Segment(startMs = 274_000, pending = true)
        val segs = listOf(s1, s2b, pending)
        Store.lectures.add(Lecture(id = "l1", courseId = "c1", title = "微机原理 · 8255", status = 0, segments = segs))
        return segs
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        val act = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        ru.noties.jlatexmath.JLatexMathAndroid.init(act.applicationContext)
        act.setContent { Box(Modifier.fillMaxSize().background(NT.Brushes.Background)) { content() } }
        repeat(6) { shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400)) }
        act.window.decorView.captureRoboImage(File(out, "$name.png").absolutePath)
    }

    private fun live(segs: List<Segment>, mode: Int) {
        Store.courses[0] = Store.courses[0].copy(defaultMode = mode)
        Live.lectureId.value = "l1"
        Live.segments.clear(); Live.segments.addAll(segs)
        Live.elapsed.longValue = 274_000
    }

    @Test fun shots() {
        assumeTrue(System.getProperty("xz.shots") == "true")
        assumeTrue(pluginsSrc.isDirectory)
        val segs = seed()
        File(out, "segments.json").writeText(segs.joinToString(",\n", "[", "]") { JSONObject().put("voice", it.voice).put("senior", it.senior).put("ext", it.ext).toString(1) })
        // 插件开
        installPlugins(true, "强")
        live(segs, 0); runCatching { shot("1-yuan-plugin-on") { LiveScreen() } }.onFailure { it.printStackTrace() }
        live(segs, 1); runCatching { shot("2-xz-plugin-on") { LiveScreen() } }.onFailure { it.printStackTrace() }
        // 插件关：同一份数据回落到 v0.0.7.9 原界面（Segment.voice / senior）
        installPlugins(false)
        live(segs, 0); runCatching { shot("3-yuan-plugin-off") { LiveScreen() } }.onFailure { it.printStackTrace() }
        live(segs, 1); runCatching { shot("4-xz-plugin-off") { LiveScreen() } }.onFailure { it.printStackTrace() }
    }
}
