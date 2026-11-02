package app.xuezhang

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.xuezhang.data.Card
import app.xuezhang.data.Course
import app.xuezhang.data.Lecture
import app.xuezhang.data.NoteSection
import app.xuezhang.data.Segment
import app.xuezhang.data.Slot
import app.xuezhang.data.Store
import app.xuezhang.engine.Bg
import app.xuezhang.ui.BookScreen
import app.xuezhang.ui.ClassTab
import app.xuezhang.ui.LibraryTab
import app.xuezhang.ui.NT
import app.xuezhang.ui.ReviewScreen
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** v0.0.7.6 截图（只在 -Pshots=true 时运行）：列表、书架、翻书页、复习卡、上课。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class V0076ShotsTest {
    private val out = File(System.getProperty("xz.shotsDir") ?: "/workspace/work/v0076-shots").apply { mkdirs() }

    private fun seed() {
        Store.dir = File(System.getProperty("java.io.tmpdir"), "xz-shots").apply { mkdirs() }
        Store.courses.clear(); Store.lectures.clear()
        val c1 = Course(id = "c1", name = "微机原理与接口技术", teacher = "林嵘", room = "IV-B303", slots = listOf(Slot(2, 3, 4)), slideFile = "第5章.pdf")
        val c2 = Course(id = "c2", name = "信号与系统", teacher = "赵磊", room = "IV-C101", slots = listOf(Slot(3, 1, 2)))
        val c3 = Course(id = "c3", name = "高等数学", teacher = "王芳", room = "III-201", slots = listOf(Slot(1, 5, 6)))
        Store.courses.addAll(listOf(c1, c2, c3))
        val day = 86_400_000L; val t0 = 1_793_000_000_000L
        val plot = "```ui\n{\"type\":\"plot\",\"title\":\"一阶系统阶跃响应\",\"expr\":\"K*(1-exp(-x/T))\",\"x\":[0,10],\"params\":[{\"name\":\"K\",\"label\":\"增益\",\"min\":0.5,\"max\":2,\"value\":1},{\"name\":\"T\",\"label\":\"时间常数\",\"min\":0.5,\"max\":4,\"value\":1.5}],\"xlabel\":\"t\",\"ylabel\":\"y\",\"caption\":\"T 越小响应越快；t=T 时达到稳态值的 63.2%。\"}\n```"
        val secs = listOf(
            NoteSection("第二节·8086 中断系统", listOf(14), "中断向量表，8086 把 256 个中断类型码对应的[b:中断向量]，依次存放在内存最低端 1KB，每个向量占 4 字节：段地址 + 偏移地址。\n\n[r:8086 如何由类型码找到服务程序入口？地址 = n × 4]"),
            NoteSection("第三节·中断响应过程", emptyList(), "CPU 在每条指令结束时检查 INTR 引脚。\n\n$plot"),
        )
        fun lec(id: String, c: String, title: String, i: Int, n: Int = 3) = Lecture(id = id, courseId = c, title = title, date = t0 + i * day, durationMs = 45 * 60_000L, status = 2,
            sections = secs, notes = "详细总解：寻址方式与数据传送指令，算术逻辑指令及标志位的变化规律…",
            cards = (1..n).map { Card("Z 变换的收敛域与因果性有什么关系？", "因果序列的收敛域在某个圆外。", "考点") },
            segments = listOf(Segment(startMs = 60_000, page = 14, exam = true, voice = "地址 = n × 4，这个一定要会")))
        Store.lectures.addAll(listOf(
            lec("l1", "c1", "1.1 微机原理", 1), lec("l2", "c1", "2.1 指令系统", 2), lec("l3", "c1", "3.1 中断系统", 3), lec("l4", "c1", "4.1 总线技术", 4), lec("l5", "c1", "5.1 存储器", 5),
            lec("l6", "c2", "1.1 连续时间信号", 6, 24), lec("l7", "c2", "2.1 系统分析", 7), lec("l8", "c2", "3.1 拉普拉斯变换", 8), lec("l9", "c2", "4.1 傅里叶级数", 9),
            lec("l10", "c3", "1.1 极限与连续", 10), lec("l11", "c3", "2.1 微积分", 11), lec("l12", "c3", "3.1 导数与微分", 12),
        ))
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        val act = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        act.setContent { Box(Modifier.fillMaxSize().background(NT.Brushes.Background)) { content() } }
        repeat(5) { shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400)) }
        act.window.decorView.captureRoboImage(File(out, "$name.png").absolutePath)
    }

    @Test fun shots() {
        assumeTrue(System.getProperty("xz.shots") == "true")
        seed()
        runCatching { shot("1-class") { ClassTab() } }.onFailure { it.printStackTrace() }
        Bg.state("lib.mode") { 0 }.value = 0
        runCatching { shot("2-shelf") { LibraryTab() } }.onFailure { it.printStackTrace() }
        Bg.state("lib.mode") { 1 }.value = 1
        runCatching { shot("3-list") { LibraryTab() } }.onFailure { it.printStackTrace() }
        runCatching { shot("4-reader") { BookScreen("l3", startPage = 2) } }.onFailure { it.printStackTrace() }
        runCatching { shot("4b-reader-plot") { BookScreen("l3", startPage = 3) } }.onFailure { it.printStackTrace() }
        runCatching { shot("5-review") { ReviewScreen("l6") } }.onFailure { it.printStackTrace() }
    }
}
