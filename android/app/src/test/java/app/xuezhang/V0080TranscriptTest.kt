package app.xuezhang

import app.xuezhang.engine.plugin.EnergyVad
import app.xuezhang.engine.plugin.Hotwords
import app.xuezhang.engine.plugin.RefinedReply
import app.xuezhang.engine.plugin.Splitter
import app.xuezhang.engine.plugin.TextRules
import app.xuezhang.engine.plugin.TranscriptExt
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v0.0.8.0 转写插件钩子：分句、长句切分、碎句合并、热词纠错、精炼 JSON 解析、VAD。 */
class V0080TranscriptTest {
    private val lexicon = listOf("8255", "8253", "8259", "8251", "INTE", "INTR", "IBF", "OBF", "STB", "ACK", "CS", "A0", "A1", "PC6", "PC7")
        .map { Hotwords.Term(it) } + Hotwords.Term("A口", listOf("AO", "A 口"))

    @Test fun splitBySentenceEnders() {
        val s = Splitter.split("我先问一下，我刚刚讲的几个LED。如果外面接LED，A口接LED是输入还是输出？你们先把这些东西搞清楚。")
        assertEquals(3, s.size)
        assertTrue(s[1].endsWith("？"))
        assertEquals("你们先把这些东西搞清楚。", s[2])
    }

    @Test fun longSentenceSplitAtCommas() {
        val long = "8255有三个端口，A口B口和C口，其中C口可以分成上下两半，各四位，分别跟A口和B口配合使用，" +
            "方式一的时候C口的几根线要当联络信号用，比如STB和ACK，还有INTE和IBF，这些都要记住。"
        assertTrue(Splitter.visibleLen(long) > 60)
        val s = Splitter.split(long, maxLen = 60)
        assertTrue("应被切成多句：$s", s.size >= 2)
        s.forEach { assertTrue("每句不超过 60 字：$it", Splitter.visibleLen(it) <= 60) }
        assertEquals(long.replace("，", "").replace("。", ""), s.joinToString("").replace("，", "").replace("。", ""))
    }

    @Test fun shortFragmentsMerged() {
        val s = Splitter.split("对。8255的初始化其实是最简单的。才0.6分。好。地址和控制字你们都能写。")
        // 「对。」并到下一句，「好。」并到前一句；「才0.6分。」6 字整，保留
        assertTrue(s.first().startsWith("对。8255"))
        assertTrue(s.none { Splitter.visibleLen(it) < 6 })
    }

    @Test fun splitDoesNotBreakTokensOrMath() {
        val s = Splitter.split("这里是[r:重点。不要切]开的地方。公式 \$a=b。c\$ 也不切。")
        assertEquals(2, s.size)
        assertTrue(s[0].contains("[r:重点。不要切]"))
    }

    @Test fun hotwordDigitsEditDistance() {
        val t = Hotwords.correct("82555的初始化其实是最简单的", lexicon)
        assertEquals("[h:8255|82555]的初始化其实是最简单的", t)
        assertEquals("8255的初始化其实是最简单的", Hotwords.strip(t))
        assertEquals("82555的初始化其实是最简单的", Hotwords.restore(t, "82555"))
        assertEquals(listOf(Hotwords.Fix("82555", "8255")), Hotwords.fixes(t))
    }

    @Test fun hotwordAliasAO() {
        val t = Hotwords.correct("如果外面接LED，AO接LED是输入还是输出？", lexicon)
        assertEquals("如果外面接LED，[h:A口|AO]接LED是输入还是输出？", t)
    }

    @Test fun hotwordDoesNotTouchExactAmbiguousOrDecimals() {
        assertEquals("8253和8259都要会", Hotwords.correct("8253和8259都要会", lexicon))
        // 8250 离 8251/8253/8255/8259 一样近：不猜
        assertEquals("8250", Hotwords.correct("8250", lexicon))
        assertEquals("均分才0.6分，2026年", Hotwords.correct("均分才0.6分，2026年", lexicon))
        assertEquals("out of range", Hotwords.correct("out of range", lexicon + Hotwords.Term("OUT")))
    }

    @Test fun hotwordPinyinFuzzy() {
        val py = Hotwords.Pinyin.parse("kong 控空\nzhi 制字自志\nzi 字自\nduan 端短\nkou 口扣\n")
        assertEquals("[h:控制字|空制自]写对", Hotwords.correct("空制自写对", listOf(Hotwords.Term("控制字")), py))
        assertEquals("[h:端口|短口]地址", Hotwords.correct("短口地址", listOf(Hotwords.Term("端口")), py))
        // 已经正确的不动
        assertEquals("控制字写对", Hotwords.correct("控制字写对", listOf(Hotwords.Term("控制字")), py))
    }

    @Test fun slideTermsExtracted() {
        val t = Hotwords.fromSlides(listOf("P.12 可编程并行接口8255A", "8253 计数初值 N=fCLK/fOUT")).map { it.term }
        assertTrue(t.contains("8255A")); assertTrue(t.contains("8253"))
        assertTrue(t.toString(), t.contains("计数初值"))
    }

    private val program = """{"kind":"transcript-pipeline","steps":[
        {"op":"fillers","level":1,"words":["嗯","呃","那个"]},
        {"op":"hotwords","level":1,"sources":["course","lexicon"]},
        {"op":"spacing","level":1},{"op":"tidy","level":1},
        {"op":"split","level":1,"maxLen":60,"minLen":6}],
        "hooks":{"capture.vad":{"minSec":3},"voice.render":"sentences","senior.render":"bubbles","segment.refine":{}}}"""

    @Test fun pipelineCleansCorrectsAndSplits() {
        val p = TextRules.parse(program)
        assertTrue(p.isPipeline)
        assertEquals("bubbles", p.hooks.optString("senior.render"))
        val r = TextRules.run(p, "嗯，82555的初始化其实是最简单的。每次考初始化，就是2分地址，1分控制字。", 2,
            TextRules.Ctx(mapOf("lexicon" to lexicon)))
        assertEquals(2, r.sentences.size)
        assertTrue(r.sentences[0], r.sentences[0].startsWith("[h:8255|82555]"))
        assertEquals(1, r.fillers)
    }

    @Test fun oldProgramsUnchanged() {
        // 没有 split / hotwords 步骤的旧规则：sentences 为空，文本照旧
        val p = TextRules.parse("""{"steps":[{"op":"fillers","level":1,"words":["嗯"]},{"op":"tidy"}]}""")
        val r = TextRules.run(p, "嗯，电压是220伏。", 2)
        assertEquals("电压是220伏。", r.text)
        assertTrue(r.sentences.isEmpty())
        assertEquals(0, p.hooks.length())
        // 只有词表的插件不算文本管线（不再抢占识别优化）
        assertFalse(TextRules.parse("""{"steps":[{"op":"lexicon","terms":["8255",{"term":"A口","alias":["AO"]}]}]}""").isPipeline)
        assertEquals(2, TextRules.parse("""{"steps":[{"op":"lexicon","terms":["8255",{"term":"A口","alias":["AO"]}]}]}""").lexicon.size)
    }

    private fun local(vararg s: String) = s.toList().let { l -> l.zip(Splitter.timestamps(l, 245_000, 273_000)) }

    @Test fun refinedJsonParsed() {
        val o = JSONObject("""{"sentences":[],"breaks":[3],"refined":"[b:8255] 初始化最简单：写对[r:端口地址（2 分）]和[r:控制字（1 分）]就行。",
            "points":[{"type":"考点","text":"8255 初始化：地址 2 分 + 控制字 1 分"},{"type":"definition","text":"控制字：设定各端口工作方式"}],
            "question":{"q":"A 口接 LED 是输入还是输出？","a":"输出。","ts":"03:52"},"ratio":46,"page":12,"exam":true,"relevant":true}""")
        val out = RefinedReply.parse(o, local("[h:8255|82555]的初始化其实是最简单的。", "每次考初始化，就是2分地址，1分控制字。", "你们知道每年这个8255初始化的均分是多少吗？", "才0.6分。"), 245_000, 273_000) { listOf(it) }
        val e = out.ext
        assertEquals(4, e.sentences.size)
        assertEquals(0, e.sentences[0].para); assertEquals(1, e.sentences[3].para)
        assertTrue(e.sentences[2].question)
        assertEquals("定义", e.points[1].type)
        assertEquals(3 * 60_000L + 52_000, e.question!!.ms)
        assertEquals(46, e.ratio); assertEquals(12, out.page); assertTrue(out.exam)
        assertTrue(out.voice.startsWith("8255的初始化"))
        // ext 往返
        val back = TranscriptExt.parse(e.toJson())!!
        assertEquals(e, back)
    }

    @Test fun refinedJsonOldFormatCompatible() {
        // 旧提示词格式：voice / senior / points 字符串数组，没有 refined / question
        val o = JSONObject("""{"voice":"电压是 220 伏","senior":"就是说电压 220 伏","points":["考点：电压 220 伏","记住单位"],"exam":false,"relevant":true}""")
        val out = RefinedReply.parse(o, local("电压是220伏。"), 0, 5_000) { listOf(it) }
        assertEquals("就是说电压 220 伏", out.ext.refined)
        assertEquals(listOf(TranscriptExt.Point("考点", "电压 220 伏"), TranscriptExt.Point("结论", "记住单位")), out.ext.points)
        assertNull(out.ext.question)
        // 没标点的一长串：用模型给的 sentences
        val o2 = JSONObject("""{"sentences":["我先问一下。","如果外面接 LED，A口接 LED 是输入还是输出？"],"refined":"SKIP","relevant":false}""")
        val out2 = RefinedReply.parse(o2, local("我先问一下如果外面接LED那个A口接LED是输入还是输出呢你们先把这些东西搞清楚好不好我们待会再讲"), 0, 9_000) { listOf(it) }
        assertEquals(2, out2.ext.sentences.size); assertTrue(out2.offTopic)
        // 旧课没有 ext
        assertNull(TranscriptExt.parse("")); assertNull(TranscriptExt.parse(null)); assertNull(TranscriptExt.parse("{oops"))
    }

    @Test fun timestampsLinear() {
        val ts = Splitter.timestamps(listOf("一二三四", "五六七八", "九十"), 10_000, 20_000)
        assertEquals(listOf(10_000L, 14_000L, 18_000L), ts)
        assertEquals(232_000L, TranscriptExt.parseTs("03:52"))
    }

    @Test fun vadCutsAtPause() {
        val vad = EnergyVad(EnergyVad.Config(minSec = 3, maxSec = 30, silenceMs = 600))
        val loud = ShortArray(1600) { i -> (if (i % 8 < 4) 4000 else -4000).toShort() }
        val quiet = ShortArray(1600) { i -> (if (i % 2 == 0) 20 else -20).toShort() }
        var cut = false
        repeat(25) { cut = cut || vad.feed(loud, 1600, 16000) } // 2.5s 说话
        assertFalse(cut)
        repeat(10) { cut = cut || vad.feed(loud, 1600, 16000) } // 3.5s
        assertFalse("没停顿不切", cut)
        var at = -1
        for (k in 0 until 10) if (vad.feed(quiet, 1600, 16000) && at < 0) at = k
        assertEquals("静音 600ms 后切", 5, at)
        assertTrue(vad.hasSpeech())
        vad.reset()
        repeat(40) { vad.feed(quiet, 1600, 16000) }
        assertFalse("纯静音段不送识别", vad.hasSpeech())
        // 到 maxSec 强制切
        val v2 = EnergyVad(EnergyVad.Config(minSec = 3, maxSec = 5))
        var n = 0; while (!v2.feed(loud, 1600, 16000)) n++
        assertEquals(49, n)
    }

    /** 插件市场里的真实插件文件（本地有 plugins 分支工作区时才跑）。 */
    @Test fun realPluginFilesParse() {
        val root = File("/workspace/work/hc-plugins")
        org.junit.Assume.assumeTrue(root.isDirectory)
        val p = TextRules.parse(File(root, "skill-speech-refine/skills/refine.json").readText())
        val stem = TextRules.parse(File(root, "skill-stem-formula/skills/stem.json").readText())
        val py = Hotwords.Pinyin.parse(File(root, "skill-speech-refine/data/pinyin.txt").readText())
        assertTrue(py.size > 6000)
        assertFalse(stem.isPipeline)
        assertTrue(stem.lexicon.any { it.term == "8255" })
        val ctx = TextRules.Ctx(mapOf("lexicon" to stem.lexicon, "course" to listOf(Hotwords.Term("控制字"))), py)
        val r = TextRules.run(p, "嗯，82555的初始化其实是最简单的。如果外面接LED，AO接LED是输入还是输出？每次考初始化，就是两分地址，一分空制字。", 2, ctx)
        val all = r.sentences.joinToString("|")
        assertTrue(all, all.contains("[h:8255|82555]"))
        assertTrue(all, all.contains("[h:A口|AO]"))
        assertTrue(all, all.contains("[h:控制字|空制字]"))
        assertTrue(all, all.contains("接 LED"))
        assertEquals(3, r.sentences.size)
        assertTrue(r.exam.isNotEmpty())
        assertEquals(3, p.hooks.optJSONObject("capture.vad")!!.optInt("minSec"))
    }
}
