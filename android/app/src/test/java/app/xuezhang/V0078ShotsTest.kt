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
import app.xuezhang.data.NoteSection
import app.xuezhang.data.Segment
import app.xuezhang.data.Slot
import app.xuezhang.data.Store
import app.xuezhang.ui.BookScreen
import app.xuezhang.ui.NT
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

/** v0.0.7.8 截图（只在 -Pshots=true 时运行）。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class V0078ShotsTest {
    private val out = File(System.getProperty("xz.shotsDir") ?: "/workspace/work/v0078-shots").apply { mkdirs() }

    companion object {
        val SEC6 = "\$I_0\$ 很小，约为额定电流的 [r:2%～10%]。它分成两部分：\n\n" +
            "\$\$\\dot I_0 = \\dot I_{0\\mu} + \\dot I_{0Fe}\$\$\n\n" +
            "[b:磁化分量] \$I_{0\\mu}\$ 产生磁通，[b:铁耗分量] \$I_{0Fe}\$ 对应铁耗，用[b:励磁阻抗] \$Z_m = R_m + jX_m\$ 表示：\$R_m\$ 是反映铁耗的等效电阻，\$X_m\$ 是对应主磁通的励磁电抗。\n\n" +
            "由于铁心磁化曲线是非线性的，\$X_m\$ 不是常数：电压越高、铁心越饱和，\$X_m\$ 越小。实际运行时电压基本恒定，所以可以近似把 \$Z_m\$ 当作常数。\n\n" +
            "铁心饱和时，磁通为正弦波，[o:磁化电流却是尖顶波]，其中含有明显的三次谐波。三相变压器里三次谐波电流能否流通，取决于绕组的连接方式，这一点在后面「三相变压器」一章还会讲到。\n\n" +
            "空载试验就是用来测 \$Z_m\$ 的：低压侧加额定电压、高压侧开路，测得 \$U_0, I_0, P_0\$，则\n\n" +
            "\$\$Z_m \\approx \\frac{U_0}{I_0},\\quad R_m \\approx \\frac{P_0}{I_0^2},\\quad X_m = \\sqrt{Z_m^2 - R_m^2}\$\$\n\n" +
            "[r:注意：空载试验一般在低压侧做]，测得的参数要折算到高压侧时乘以变比的平方 \$k^2\$。空载损耗 \$P_0\$ 近似等于铁耗 \$p_{Fe}\$，因为空载电流很小，铜耗可以忽略。"
    }

    private fun seed() {
        Store.dir = File(System.getProperty("java.io.tmpdir"), "xz-shots78").apply { mkdirs() }
        Store.courses.clear(); Store.lectures.clear()
        val pdf = File(Store.dir, "dianji.pdf").also { f -> File("/workspace/work/assets78/dianji.pdf").takeIf { it.exists() }?.copyTo(f, true) }
        // Robolectric 里 PdfRenderer 渲染不了：把课件页预先放进磁盘缓存（与 SlideImages.render 的文件名规则一致）
        val cache = File(Store.dir, "slidecache").apply { mkdirs() }
        for (n in 1..8) { val png = File("/workspace/work/assets78/slide$n.png"); if (png.exists()) for (w in listOf(1400, 320)) png.copyTo(File(cache, "${(pdf.absolutePath + pdf.lastModified()).hashCode()}_${n}_$w.png"), true) }
        val c = Course(id = "c1", name = "电机学", teacher = "周老师", room = "III-305", slots = listOf(Slot(2, 1, 2)), slideFile = "变压器.pdf", slidePath = pdf.absolutePath)
        Store.courses.add(c)
        val secs = (1..5).map { NoteSection("$it. 变压器原理（$it）", listOf(it), "主磁通 \$\\Phi_m\$ 与漏磁通的区别：主磁通经铁心闭合。\n\n[b:感应电动势] \$E = 4.44 f N \\Phi_m\$。") } +
            NoteSection("6. 空载电流与励磁阻抗", listOf(7), SEC6) +
            NoteSection("7. 变压器的负载运行", listOf(8), "负载时磁动势平衡：\$\\dot F_1 + \\dot F_2 = \\dot F_0\$。")
        Store.lectures.add(Lecture(id = "l1", courseId = "c1", title = "电机学·变压器（一）·空载运行与励磁阻抗", date = 1_793_000_000_000L, durationMs = 90 * 60_000L, status = 2,
            sections = secs, segments = listOf(Segment(startMs = 1_800_000, page = 7, exam = true, voice = "空载试验在低压侧做，参数折算乘 \$k^2\$，这个一定要记住"))))
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        val act = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        act.setContent { Box(Modifier.fillMaxSize().background(NT.Brushes.Background)) { content() } }
        repeat(6) { shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400)) }
        act.window.decorView.captureRoboImage(File(out, "$name.png").absolutePath)
    }

    @Test fun reader() {
        assumeTrue(System.getProperty("xz.shots") == "true")
        seed()
        // 封面 0、目录 1、第 1..5 节 2..6、第 6 节 = 7
        runCatching { shot("1-reader-sec6-page") { BookScreen("l1", startPage = 7, startPart = 0) } }.onFailure { it.printStackTrace() }
        runCatching { shot("2-reader-sec6-cont") { BookScreen("l1", startPage = 7, startPart = 1) } }.onFailure { it.printStackTrace() }
        runCatching { shot("2b-reader-sec6-cont2") { BookScreen("l1", startPage = 7, startPart = 2) } }.onFailure { it.printStackTrace() }
        runCatching { shot("3-reader-toc") { BookScreen("l1", startPage = 1) } }.onFailure { it.printStackTrace() }
    }
}
