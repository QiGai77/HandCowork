package app.xuezhang

import app.xuezhang.data.Card
import app.xuezhang.data.NoteSection
import app.xuezhang.engine.HardCovers
import app.xuezhang.engine.StemUi
import app.xuezhang.engine.UiBlock
import app.xuezhang.engine.UiBlocks
import app.xuezhang.ui.ReaderMath
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V0076Test {

    // ---------- 统一精装封面 ----------
    @Test fun tenPresetsWithDesignPalettes() {
        assertEquals(10, HardCovers.ALL.size)
        assertEquals(0xFF2F7A5CL, HardCovers.byN(1).board)
        assertEquals(0xFF25395FL, HardCovers.byN(2).board)
        assertEquals(0xFF1C2242L, HardCovers.byN(10).board)
        assertEquals((1..10).toList(), HardCovers.ALL.map { it.n })
    }

    @Test fun coverAssignmentIsStableAndSubjectAware() {
        // 同名课程永远同一款
        repeat(3) { assertEquals(HardCovers.forCourse("大学英语", ""), HardCovers.forCourse("大学英语", "")) }
        assertEquals(HardCovers.forCourse("大学英语", "generic"), HardCovers.byName("大学英语"))
        // 学科关键词：微机→翡翠芯片，信号→藏青波形，高数→赭陶积分，物理→梅紫原子
        assertEquals("chip", HardCovers.forCourse("微机原理与接口技术", "").motif)
        assertEquals("wave", HardCovers.forCourse("信号与系统", "").motif)
        assertEquals("integral", HardCovers.forCourse("高等数学A", "").motif)
        assertEquals("atom", HardCovers.forCourse("大学物理", "").motif)
        // 旧 preset 映射
        assertEquals("chip", HardCovers.forCourse("随便", "preset:微机原理").motif)
    }

    @Test fun userPickAndLectureOverride() {
        assertEquals(7, HardCovers.forCourse("信号与系统", "hc:7").n)
        assertEquals(HardCovers.forCourse("信号与系统", ""), HardCovers.forCourse("信号与系统", "hc:99")) // 越界忽略
        assertEquals(3, HardCovers.forLecture(2, "信号与系统", "", "x").n)
        assertEquals(HardCovers.forCourse("信号与系统", "hc:5"), HardCovers.forLecture(-1, "信号与系统", "hc:5", "x"))
        assertEquals(HardCovers.byName("孤儿笔记"), HardCovers.forLecture(-1, null, null, "孤儿笔记"))
        assertEquals("hc:4", HardCovers.coverValue(HardCovers.byN(4)))
        assertEquals("信", HardCovers.watermark("信号与系统"))
    }

    // ---------- 翻书进度条 ----------
    @Test fun scrubberMath() {
        assertEquals(0, ReaderMath.pageAt(0f, 300f, 10))
        assertEquals(9, ReaderMath.pageAt(300f, 300f, 10))
        assertEquals(5, ReaderMath.pageAt(160f, 300f, 10))
        assertEquals(0, ReaderMath.pageAt(100f, 300f, 1))
        assertEquals(1f, ReaderMath.fraction(9, 10))
        assertEquals(0f, ReaderMath.fraction(0, 1))
    }

    // ---------- 工科笔记交互组件 ----------
    @Test fun stemDetection() {
        listOf("信号与系统", "微机原理与接口技术", "电路分析", "高等数学", "大学物理", "自动控制原理", "数据结构", "机械设计基础", "通信原理", "有机化学").forEach {
            assertTrue(it, StemUi.isStem(it))
        }
        listOf("大学英语", "中国近代史纲要", "思想道德与法治", "马克思主义基本原理", "", null).forEach { assertFalse("$it", StemUi.isStem(it)) }
        assertTrue(StemUi.isStem("某某课", "stem"))
        assertFalse(StemUi.isStem("信号与系统", "humanities"))
    }

    private fun sec(t: String, b: String) = NoteSection(t, emptyList(), b)
    private val cards = listOf(Card("Z 变换收敛域与因果性？", "因果序列的 ROC 在某圆外"), Card("单位阶跃 u[n] 的 Z 变换？", "z/(z-1)"), Card("线性时不变指？", "满足叠加与时移不变"))

    @Test fun enforceReachesMinimumWithPlot() {
        val s = listOf(sec("1. 指数信号", "定义：\n\$\$x(t)=A e^{-a t}\$\$\n衰减。"), sec("2. 卷积", "\$\$y=x*h\$\$\n\$\$Y(s)=X(s)H(s)\$\$"), sec("3. 小结", "复习"))
        assertTrue(StemUi.needsMore(s))
        val out = StemUi.enforce("信号与系统", s, cards)
        val c = StemUi.count(out.map { it.body })
        assertTrue("total ${c.total}", c.total >= StemUi.MIN)
        assertTrue(c.plots >= 1)
        assertTrue(c.total <= StemUi.MAX)
        assertFalse(StemUi.needsMore(out))
        // 第一节的公式被转成了可画曲线
        val firstPlot = UiBlocks.split(out[0].body).filterIsInstance<app.xuezhang.engine.NotePart.Ui>().map { it.block }.filterIsInstance<UiBlock.Plot>().firstOrNull()
        assertNotNull(firstPlot)
    }

    @Test fun enforceWithoutFormulasUsesTemplates() {
        val out = StemUi.enforce("自动控制原理", listOf(sec("引言", "没有公式的一节"), sec("二", "还是文字")), emptyList())
        val c = StemUi.count(out.map { it.body })
        assertTrue(c.total >= 3); assertTrue(c.plots >= 1)
    }

    @Test fun capKeepsAtMostFiveAndPrefersPlots() {
        val quiz = """```ui
{"type":"quiz","q":"q","options":["a","b"],"answer":0,"explain":""}
```"""
        val plot = """```ui
{"type":"plot","title":"p","expr":"sin(x)","x":[0,6]}
```"""
        val s = listOf(sec("a", (1..4).joinToString("\n\n") { quiz }), sec("b", "$quiz\n\n$quiz\n\n$plot"))
        assertEquals(7, StemUi.count(s.map { it.body }).total)
        val out = StemUi.cap(s)
        val c = StemUi.count(out.map { it.body })
        assertEquals(5, c.total); assertEquals(1, c.plots)
    }

    @Test fun texToExprAndModelFix() {
        assertNotNull(StemUi.texToExpr("x(t)=A e^{-a t}"))
        assertNotNull(StemUi.texToExpr("u_C(t)=U(1-e^{-t/\\tau})"))
        assertNull(StemUi.texToExpr("E=mc^2"))
        val fix = JSONObject("""{"blocks":[{"section":1,"caption":"幅频特性","ui":{"type":"plot","title":"|H|","expr":"1/sqrt(1+(x/w)^2)","x":[0,10],"params":[{"name":"w","min":0.5,"max":5,"value":1}]}},{"section":0,"ui":{"type":"bogus"}}]}""")
        val out = StemUi.applyFix(listOf(sec("a", "x"), sec("b", "y")), fix)
        assertEquals("x", out[0].body)
        assertTrue(out[1].body.contains("图注") && out[1].body.contains("```ui"))
        assertEquals(1, StemUi.count(out.map { it.body }).plots)
        // 图注字段被解析
        val p = UiBlocks.parse("""{"type":"plot","expr":"x","x":[0,1],"caption":"看斜率"}""") as UiBlock.Plot
        assertEquals("看斜率", p.caption)
    }
}
