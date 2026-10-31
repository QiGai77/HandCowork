package app.xuezhang

import app.xuezhang.data.Course
import app.xuezhang.data.Slot
import app.xuezhang.engine.Bili
import app.xuezhang.engine.CoverMatch
import app.xuezhang.engine.Expr
import app.xuezhang.engine.NotePart
import app.xuezhang.engine.Schedule
import app.xuezhang.engine.UiBlock
import app.xuezhang.engine.UiBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ExprTest {
    private fun ev(s: String, v: Map<String, Double> = emptyMap()) = Expr.safeEval(Expr.parse(s), v)

    @Test fun arithmeticAndPrecedence() {
        assertEquals(7.0, ev("1+2*3"), 1e-9)
        assertEquals(9.0, ev("(1+2)*3"), 1e-9)
        assertEquals(-4.0, ev("-2^2"), 1e-9)
        assertEquals(512.0, ev("2^3^2"), 1e-9) // 右结合
        assertEquals(6.0, ev("2x", mapOf("x" to 3.0)), 1e-9) // 隐式乘法
    }

    @Test fun functionsAndConstants() {
        assertEquals(1.0, ev("sin(pi/2)"), 1e-9)
        assertEquals(0.0, ev("ln(1)"), 1e-9)
        assertEquals(3.0, ev("max(1,3,2)"), 1e-9)
        assertEquals(8.0, ev("pow(2,3)"), 1e-9)
    }

    @Test fun safety() {
        assertNull(Expr.tryParse("System.exit(0)"))
        assertNull(Expr.tryParse("sin"))
        assertNull(Expr.tryParse("1+"))
        assertNull(Expr.tryParse("(".repeat(100) + "1" + ")".repeat(100)))
        assertNull(Expr.tryParse("x".repeat(500)))
        assertTrue(Expr.safeEval(Expr.parse("1/0"), emptyMap()).isNaN())
        assertTrue(Expr.safeEval(Expr.parse("y+1"), emptyMap()).isNaN()) // 未知变量
        assertEquals(setOf("A", "f", "x", "pi"), Expr.vars(Expr.parse("A*sin(2*pi*f*x)")))
    }
}

class UiBlocksTest {
    @Test fun splitsTextAndBlocks() {
        val body = "前面的文字\n```ui\n{\"type\":\"quiz\",\"q\":\"1+1=?\",\"options\":[\"1\",\"2\"],\"answer\":1,\"explain\":\"就是 2\"}\n```\n后面的文字"
        val parts = UiBlocks.split(body)
        assertEquals(3, parts.size)
        assertTrue(parts[0] is NotePart.Text)
        val q = (parts[1] as NotePart.Ui).block as UiBlock.Quiz
        assertEquals(1, q.answer)
        assertTrue(parts[2] is NotePart.Text)
    }

    @Test fun allFiveTypesParse() {
        assertTrue(UiBlocks.parse("""{"type":"plot","expr":"A*sin(2*pi*f*x)","x":[0,1],"params":[{"name":"A","min":0,"max":2,"value":1},{"name":"f","min":1,"max":5}]}""") is UiBlock.Plot)
        assertTrue(UiBlocks.parse("""{"type":"steps","steps":[{"text":"a","tex":"x=1"},"b"]}""") is UiBlock.Steps)
        assertTrue(UiBlocks.parse("""{"type":"compare","columns":["","甲","乙"],"rows":[["维度","1","2"]]}""") is UiBlock.Compare)
        assertTrue(UiBlocks.parse("""{"type":"quiz","q":"?","options":["a","b","c"],"answer":2}""") is UiBlock.Quiz)
        val ph = UiBlocks.parse("""{"type":"phasor","param":{"name":"phi","min":-60,"max":60,"value":30},"vectors":[{"label":"U","mag":"1","angle":"0"},{"label":"I","mag":"0.5","angle":"-phi"},{"label":"S","sum":[0,1]}]}""")
        assertTrue(ph is UiBlock.Phasor)
        val pts = UiBlocks.phasorPoints(ph as UiBlock.Phasor, 0.0)
        assertEquals(1.5, pts[2].second[0], 1e-9)
        assertEquals(0.0, pts[2].second[1], 1e-9)
    }

    @Test fun invalidBlocksFallBackToText() {
        // 未知类型、坏 JSON、未声明变量、越界答案、超过 2 个参数之外的变量 → 都不渲染为组件
        listOf(
            """{"type":"video","url":"x"}""",
            """{"type":"plot","expr":"k*x","x":[0,1]}""",
            """{"type":"plot","expr":"x","x":[1,0]}""",
            """{"type":"quiz","q":"?","options":["a","b"],"answer":5}""",
            """{"type":"compare","columns":["only"],"rows":[["a"]]}""",
            """{"type":"phasor","vectors":[{"label":"U","mag":"z","angle":"0"}]}""",
            """{not json""",
        ).forEach { assertNull(it, UiBlocks.parse(it)) }
        val parts = UiBlocks.split("```ui\n{\"type\":\"video\",\"title\":\"标题文字\"}\n```")
        assertEquals(1, parts.size)
        assertTrue(parts[0] is NotePart.Text)
        assertTrue((parts[0] as NotePart.Text).text.contains("标题文字"))
    }

    @Test fun neverThrowsOnGarbage() {
        listOf("", "```", "```ui", "```ui\n```", "```ui\n{}\n```", "a```ui\n[1,2]\n```b", "```ui\n" + "{".repeat(10000) + "\n```").forEach { UiBlocks.split(it) }
    }
}

class ScheduleTest {
    @Test fun parsesStrictAndLooseJson() {
        val strict = """[{"name":"电机学","weekday":1,"startSection":3,"endSection":4,"room":"西十二 N101","teacher":"王老师","weeks":"1-16"},
            {"name":"电机学","weekday":4,"startSection":1,"endSection":2,"room":"西十二 N101","teacher":"王老师","weeks":"1-16"},
            {"name":"信号与系统","weekday":2,"startSection":5,"endSection":6,"room":"","teacher":"","weeks":""}]"""
        val e = Schedule.parseTimetable(strict)
        assertEquals(3, e.size)
        assertEquals(3, e[0].startSection)
        val loose = "```json\n{\"courses\":[{\"course\":\"高等数学\",\"day\":\"周三\",\"sections\":\"1-2节\",\"location\":\"教一\"}]}\n```"
        val l = Schedule.parseTimetable(loose)
        assertEquals("高等数学", l[0].name); assertEquals(3, l[0].weekday); assertEquals(1, l[0].startSection); assertEquals(2, l[0].endSection)
        // 分组 + 已存在去重
        val g = Schedule.group(e, listOf(Course(name = "信号与系统")))
        assertEquals(2, g.size)
        assertEquals(2, g.first { it.name == "电机学" }.entries.size)
        assertTrue(g.first { it.name == "信号与系统" }.exists)
        assertEquals(2, Schedule.toCourse(g.first { it.name == "电机学" }, 0).slots.size)
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsNonJson() { Schedule.parseTimetable("抱歉，我看不清这张图") }

    @Test fun todayAndNext() {
        val a = Course(name = "A", slots = listOf(Slot(1, 1, 2), Slot(3, 5, 6)))
        val b = Course(name = "B", slots = listOf(Slot(1, 5, 6)))
        val c = Course(name = "C", slots = listOf(Slot(2, 1, 2)))
        val today = Schedule.today(listOf(b, a, c), 1)
        assertEquals(listOf("A", "B"), today.map { it.course.name })
        assertEquals("A", Schedule.next(today, 7 * 60)?.course?.name)          // 早上：第一节
        assertEquals("A", Schedule.next(today, 9 * 60)?.course?.name)          // 正在上
        assertEquals("B", Schedule.next(today, 12 * 60)?.course?.name)         // 中午：下午那节
        assertNull(Schedule.next(today, 18 * 60))                              // 都上完了
        assertTrue(Schedule.today(listOf(a, b, c), 7).isEmpty())
    }

    @Test fun weekdayParsing() {
        assertEquals(1, Schedule.parseWeekday("周一")); assertEquals(7, Schedule.parseWeekday("星期日"))
        assertEquals(5, Schedule.parseWeekday("Fri")); assertEquals(7, Schedule.parseWeekday("0"))
        val m = Schedule.migrate(Course(name = "x", time = "周六 14:00"))
        assertEquals(6, m.slots[0].weekday); assertEquals(5, m.slots[0].start)
    }
}

class CoverMatchTest {
    @Test fun matchesAllPresets() {
        mapOf("电机学" to "电机学", "电机与拖动基础" to "电机学", "信号与系统 A" to "信号与系统", "电力电子技术" to "电力电子技术",
            "微机原理与接口技术" to "微机原理", "习近平新时代中国特色社会主义思想概论" to "习思想", "高等数学 A（二）" to "高等数学",
            "线性代数" to "线性代数", "电路原理" to "电路原理", "自动控制原理" to "自动控制原理", "大学物理 B" to "大学物理",
            "模拟电子技术基础" to "模拟电子技术", "数字电子技术" to "数字电子技术", "数字电路与逻辑设计" to "数字电子技术",
        ).forEach { (n, k) -> assertEquals(n, k, CoverMatch.match(n)?.key) }
        assertEquals(12, CoverMatch.PRESETS.size)
    }

    @Test fun fallback() {
        assertNull(CoverMatch.match("大学英语"))
        assertNull(CoverMatch.match(""))
        assertEquals(CoverMatch.fallbackColor("大学英语"), CoverMatch.fallbackColor("大学英语"))
        assertEquals("大", CoverMatch.glyph("  大学英语"))
        assertEquals("电机学", CoverMatch.resolve("随便", "preset:电机学")?.key)
        assertNull(CoverMatch.resolve("电机学", "file:/x.jpg"))
        assertNull(CoverMatch.resolve("电机学", "generic"))
    }
}

class BiliTest {
    @Test fun parsesLinks() {
        assertEquals("BV1fp411Z7uJ", Bili.parseBv("https://www.bilibili.com/video/BV1fp411Z7uJ/?p=16&spm_id_from=x"))
        assertEquals(16, Bili.parsePage("https://www.bilibili.com/video/BV1fp411Z7uJ/?p=16"))
        assertEquals(1, Bili.parsePage("BV1fp411Z7uJ"))
        assertNull(Bili.parseBv("没有链接"))
        assertEquals("44:01", Bili.fmtDur(2641))
    }

    @Test fun picksLowestBitrateAudio() {
        val j = """{"code":0,"data":{"dash":{"audio":[{"baseUrl":"hi","bandwidth":320000},{"base_url":"lo","bandwidth":64000}]}}}"""
        assertEquals("lo", Bili.pickAudio(j)?.first)
        assertNull(Bili.pickAudio("""{"code":-404}"""))
    }

    @Test fun parsesViewInfo() {
        val i = Bili.parseInfo("""{"code":0,"data":{"bvid":"BV1fp411Z7uJ","title":"华中科技大学 电机学","owner":{"name":"电气工程er"},"duration":192017,"pic":"p","pages":[{"cid":39379082,"part":"16 变压器（一）","duration":2641}]}}""")
        assertEquals(39379082L, i.pages[0].cid)
        assertTrue(abs(i.pages[0].duration - 2641) == 0)
        assertNotNull(i.owner)
    }
}

class V005Test {
    @Test fun timetableTimesMapToSections() {
        val e = Schedule.parseTimetable("""好的：```json
{"courses":[{"name":"电机学","teacher":"王老师","room":"教三 302","weekday":2,"startSection":0,"endSection":0,"startTime":"10:10","endTime":"11:45","weeks":"1-16"},
{"name":"高数","weekday":"周四","startSection":1,"endSection":2}]}
```""")
        assertEquals(2, e.size)
        assertEquals(3, e[0].startSection); assertEquals(4, e[0].endSection); assertEquals(2, e[0].weekday)
        assertEquals("王老师", e[0].teacher); assertEquals(4, e[1].weekday)
    }

    @Test fun parsesQuotaDefensively() {
        val g = app.xuezhang.net.Api.parseQuota("""{"plan_type":"plus","rate_limit":{"primary_window":{"used_percent":37,"reset_after_seconds":3600},"secondary_window":{"used_percent":12.5,"reset_at":1893456000}}}""")
        assertEquals(37.0, g.fivePct!!, 0.01); assertEquals(12.5, g.weekPct!!, 0.01)
        assertEquals(1893456000000L, g.weekReset)
        assertNotNull(g.fiveReset)
        val c = app.xuezhang.net.Api.parseQuota("""{"five_hour":{"utilization":64.0,"resets_at":"2030-01-01T00:00:00+00:00"},"seven_day":{"utilization":8.0,"resets_at":null}}""")
        assertEquals(64.0, c.fivePct!!, 0.01); assertEquals(8.0, c.weekPct!!, 0.01)
        assertEquals(1893456000000L, c.fiveReset)
    }

    @Test fun extractsJsonFromChatter() {
        val o = app.xuezhang.net.Api.extractJsonObject("当然！{\"a\":{\"b\":\"}\"}} 以上")
        assertEquals("}", o!!.getJSONObject("a").getString("b"))
    }

    @Test fun importLinesRotate() {
        val a = app.xuezhang.engine.ImportFun.line(0, 12, 40, 0, kotlin.random.Random(1))
        assertTrue(a.isNotBlank())
        assertTrue(app.xuezhang.engine.ImportFun.done().isNotBlank())
    }
}
