package app.xuezhang

import app.xuezhang.engine.JsonLoose
import app.xuezhang.engine.NotePart
import app.xuezhang.engine.Schedule
import app.xuezhang.engine.UiBlock
import app.xuezhang.engine.UiBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableParserTest {
    private val courses = """{"courses":[{"name":"电机学","teacher":"王老师","room":"西十二 N101","weekday":1,"startSection":3,"endSection":4,"startTime":"","endTime":"","weeks":"1-16"},{"name":"信号与系统","teacher":"","room":"东九 A201","weekday":3,"startSection":1,"endSection":2,"startTime":"08:00","endTime":"09:40","weeks":"1-8,10-16(单)"}]}"""

    @Test fun codeFenceWithProse() {
        val e = Schedule.parseTimetable("好的，以下是识别结果：\n```json\n$courses\n```\n如有遗漏请告诉我。")
        assertEquals(2, e.size); assertEquals("信号与系统", e[1].name); assertEquals(3, e[1].weekday)
    }

    @Test fun reasoningPrefixWithBrackets() {
        val t = "<think>先看表头 [周一][周二]，第 1 列是 {节次}… 电机学在周一第3-4节。</think>\n$courses"
        assertEquals(2, Schedule.parseTimetable(t).size)
    }

    @Test fun unterminatedThinkAndTrailingBraces() {
        val t = "<think>图片里有 {很多} 课\n$courses\n注意：{周次} 可能有误}"
        assertEquals(2, Schedule.parseTimetable(t).size)
    }

    @Test fun otherWrapperKeysAndTrailingComma() {
        val t = """{"timetable":{"semester":"2026秋","items":[{"courseName":"高等数学","day":"星期二","sections":"5-6节","location":"教一",},]}}"""
        val e = Schedule.parseTimetable(t)
        assertEquals("高等数学", e[0].name); assertEquals(2, e[0].weekday); assertEquals(5, e[0].startSection)
    }

    @Test fun groupedByDay() {
        val t = """[[{"name":"英语","weekday":1,"startSection":1,"endSection":2}],[{"name":"物理","weekday":2,"startSection":3,"endSection":4}]]"""
        assertEquals(2, Schedule.parseTimetable(t).size)
    }

    @Test(expected = IllegalArgumentException::class) fun refusal() { Schedule.parseTimetable("抱歉，图片太模糊，我无法识别课程表。") }
}

class RealModelUiBlocksTest {
    /** 真实模型输出的典型样子：一行代码块、LaTeX 单反斜杠、answer 是字母、表格行是对象、正文里裸 JSON。 */
    @Test fun nonDemoNoteBody() {
        val body = "变压器的等效电路如下。\n```ui {\"type\":\"steps\",\"title\":\"折算\",\"steps\":[{\"text\":\"变比\",\"tex\":\"k=\\frac{N_1}{N_2}\"},{\"text\":\"折算电阻\",\"tex\":\"R_2'=k^2 R_2\\sqrt{1}\"}]}```\n" +
            "再做一道题：{\"type\":\"quiz\",\"q\":\"空载损耗主要是？\",\"options\":[\"A. 铁耗\",\"B. 铜耗\"],\"answer\":\"A\",\"explain\":\"空载电流很小\"}\n" +
            "```ui\n{\"type\":\"table\",\"title\":\"对比\",\"columns\":[\"项目\",\"空载\",\"短路\"],\"rows\":[{\"项目\":\"测得\",\"空载\":\"铁耗\",\"短路\":\"铜耗\"}],}\n```"
        val parts = UiBlocks.split(body)
        val ui = parts.filterIsInstance<NotePart.Ui>().map { it.block }
        assertEquals(3, ui.size)
        val st = ui[0] as UiBlock.Steps
        assertTrue(st.steps[0].second, st.steps[0].second.contains("\\frac"))
        assertTrue(st.steps[1].second.contains("\\sqrt"))
        assertEquals(0, (ui[1] as UiBlock.Quiz).answer)
        assertEquals("铁耗", (ui[2] as UiBlock.Compare).rows[0][1])
    }

    @Test fun noteJsonWithSingleBackslashLatex() {
        val raw = "```json\n{\"title\":\"变压器\",\"sections\":[{\"title\":\"1. 变比\",\"body\":\"[b:变比] \$k=\\frac{U_1}{U_2}\$，角度 \$\\theta\$，\$\\sqrt{2}\$\"}]}\n```"
        val o = JsonLoose.obj(raw)!!
        val b = o.getJSONArray("sections").getJSONObject(0).getString("body")
        assertTrue(b, b.contains("\\frac") && b.contains("\\theta") && b.contains("\\sqrt"))
    }

    @Test fun unknownBlockFallsBackToText() {
        val p = UiBlocks.split("```ui\n{\"type\":\"mindmap\",\"title\":\"总结\",\"nodes\":[\"磁路\",\"电路\"]}\n```")
        assertTrue(p.single() is NotePart.Text)
    }
}

class V006MiscTest {
    @Test fun codexModelsParsedAndHiddenSkipped() {
        val l = app.xuezhang.net.Api.parseCodexModels("""{"models":[{"slug":"gpt-6.1-sol","visibility":"list"},{"slug":"gpt-5.1","visibility":"hide"},{"id":"gpt-6-luna"},{"slug":"old","deprecated":true}]}""")
        assertEquals(listOf("gpt-6.1-sol", "gpt-6-luna"), l)
        assertTrue(app.xuezhang.net.Api.sortOpenAiChat(listOf("whisper-1", "gpt-5", "gpt-6.1-sol", "text-embedding-3")).first() == "gpt-6.1-sol")
    }

    @Test fun offTopicMarker() {
        assertTrue(app.xuezhang.engine.Prompts.offTopic(org.json.JSONObject("""{"voice":"怎么砍不死他呀","senior":"SKIP","relevant":false}""")))
        assertTrue(!app.xuezhang.engine.Prompts.offTopic(org.json.JSONObject("""{"voice":"梯度","senior":"梯度是方向","relevant":true}""")))
    }

    @Test fun subtitleBodyGroups() {
        val body = org.json.JSONArray((0 until 20).joinToString(",", "[", "]") { """{"from":${it * 4.0},"to":${it * 4 + 3.5},"content":"句$it"}""" })
        val g = app.xuezhang.engine.Bili.parseSubtitleBody(body, 30)
        assertTrue(g.size in 2..3); assertEquals(0L, g[0].first)
    }

    @Test fun mergeChunks() {
        val a = org.json.JSONObject("""{"title":"变压器","sections":[{"title":"1"}],"nodes":[{"label":"变压器","parent":-1},{"label":"变比","parent":0}],"cards":[{"q":"a"}]}""")
        val b = org.json.JSONObject("""{"title":"","sections":[{"title":"2"}],"nodes":[{"label":"损耗","parent":-1},{"label":"铁耗","parent":0}],"cards":[{"q":"a"},{"q":"b"}]}""")
        val m = app.xuezhang.engine.Live.mergeNotes(listOf(a, b))
        assertEquals(2, m.getJSONArray("sections").length()); assertEquals(2, m.getJSONArray("cards").length())
        val n = m.getJSONArray("nodes"); assertEquals(4, n.length()); assertEquals(0, n.getJSONObject(2).getInt("parent")); assertEquals(2, n.getJSONObject(3).getInt("parent"))
    }
}
