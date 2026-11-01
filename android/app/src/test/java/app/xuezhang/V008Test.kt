package app.xuezhang

import app.xuezhang.data.Card
import app.xuezhang.data.Mnemonic
import app.xuezhang.data.Node
import app.xuezhang.data.NoteSection
import app.xuezhang.data.Settings
import app.xuezhang.data.Store
import app.xuezhang.engine.Bili
import app.xuezhang.engine.CardGen
import app.xuezhang.engine.ImageReply
import app.xuezhang.engine.NavOrder
import app.xuezhang.engine.Schedule
import app.xuezhang.ui.Scrub
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 底部导航栏顺序：解析、拖动移动、启动页、持久化。 */
class NavOrderTest {
    @Test fun parseNormalizes() {
        assertEquals(listOf(0, 1, 2, 3), NavOrder.parse(null))
        assertEquals(listOf(0, 1, 2, 3), NavOrder.parse(""))
        assertEquals(listOf(1, 0, 2, 3), NavOrder.parse("1,0,2,3"))
        // 重复、非法 id、缺项都能修好
        assertEquals(listOf(2, 1, 0, 3), NavOrder.parse("2, 2, 9, 1, x"))
        assertEquals(listOf(3, 0, 1, 2), NavOrder.parse("3"))
    }
    @Test fun moveAndStartPage() {
        val o = NavOrder.move(listOf(0, 1, 2, 3), 1, 0) // 把「上课」拖到第一位
        assertEquals(listOf(1, 0, 2, 3), o)
        assertEquals(1, NavOrder.start(NavOrder.encode(o)))
        assertEquals(listOf(1, 2, 3, 0), NavOrder.move(listOf(0, 1, 2, 3), 0, 9))
        assertEquals(listOf(0, 1, 2, 3), NavOrder.move(listOf(0, 1, 2, 3), 7, 0))
    }
    @Test fun dragTargetIndex() {
        assertEquals(0, NavOrder.targetIndex(1, -60f, 100f))
        assertEquals(1, NavOrder.targetIndex(1, 40f, 100f))
        assertEquals(3, NavOrder.targetIndex(1, 900f, 100f))
        assertEquals(2, NavOrder.targetIndex(2, 5f, 0f))
    }
    @Test fun persistsInSettingsJson() {
        with(Store) {
            val s = Settings(navOrder = "1,0,2,3", noteView = 1)
            val back = JSONObject(s.j().toString()).settings()
            assertEquals("1,0,2,3", back.navOrder)
            assertEquals(1, back.noteView)
            assertEquals(1, NavOrder.start(back.navOrder))
            // 旧版本数据没有这个字段：默认顺序、书架
            val old = JSONObject(s.j().toString()).apply { remove("no"); remove("nv") }.settings()
            assertEquals(NavOrder.DEFAULT, old.navOrder)
            assertEquals(0, old.noteView)
            // 坏数据被修正
            val bad = JSONObject(s.j().toString()).put("no", "3,3,7").settings()
            assertEquals("3,0,1,2", bad.navOrder)
        }
    }
}

/** 概念图「没有返回图片」：ChatGPT 流 / 图片接口的各种返回格式。 */
class ImageReplyTest {
    private val b64 = "iVBORw0KGgo" + "A".repeat(400)

    @Test fun outputItemDoneResult() {
        val r = ImageReply()
        r.event("response.output_item.added", JSONObject().put("item", JSONObject().put("type", "image_generation_call").put("result", JSONObject.NULL)))
        r.event("response.output_item.done", JSONObject().put("item", JSONObject().put("type", "image_generation_call").put("result", b64)))
        assertEquals(listOf("b64:$b64"), r.result())
    }
    @Test fun completedResponseOutput() {
        val r = ImageReply()
        val out = JSONArray().put(JSONObject().put("type", "reasoning")).put(JSONObject().put("type", "image_generation_call").put("result", "data:image/png;base64,$b64"))
        r.event("response.completed", JSONObject().put("response", JSONObject().put("output", out)))
        assertEquals(listOf("b64:$b64"), r.result())
    }
    @Test fun partialImageFallbackAndTextDataUrl() {
        val r = ImageReply()
        r.event("response.image_generation_call.partial_image", JSONObject().put("partial_image_b64", b64))
        assertEquals(listOf("b64:$b64"), r.result())
        val t = ImageReply()
        t.event("response.output_text.delta", JSONObject().put("delta", "好的 ![导图](https://cdn.example.com/a.png) 以及 data:image/png;base64,$b64"))
        assertEquals(2, t.result().size)
        assertTrue(t.result().contains("https://cdn.example.com/a.png"))
    }
    @Test fun messageOutputImage() {
        val r = ImageReply()
        val msg = JSONObject().put("type", "message").put("content", JSONArray().put(JSONObject().put("type", "output_image").put("image_url", JSONObject().put("url", "https://x.y/z.png"))))
        r.event("response.output_item.done", JSONObject().put("item", msg))
        assertEquals(listOf("https://x.y/z.png"), r.result())
    }
    @Test fun generationsEndpoint() {
        val r = ImageReply()
        r.generations(JSONObject().put("data", JSONArray().put(JSONObject().put("b64_json", b64)).put(JSONObject().put("url", "https://u/1.png"))))
        assertEquals(listOf("b64:$b64", "https://u/1.png"), r.result())
        val sf = ImageReply(); sf.generations(JSONObject().put("images", JSONArray().put(JSONObject().put("url", "https://sf/2.png"))))
        assertEquals(listOf("https://sf/2.png"), sf.result())
    }
    @Test fun emptyReplyGivesNothingAndLogIsMasked() {
        val r = ImageReply()
        r.event("response.output_text.delta", JSONObject().put("delta", "我无法生成图片"))
        r.event("response.created", JSONObject().put("blob", b64))
        assertTrue(r.result().isEmpty())
        val log = r.rawForLog()
        assertFalse(log.contains(b64))
        assertTrue(log.contains("base64"))
    }
}

/** B 站音轨 403：候选地址顺序、backupUrl、官方镜像。 */
class BiliAudioTest {
    private val json = JSONObject().put("code", 0).put("data", JSONObject().put("dash", JSONObject().put("audio", JSONArray()
        .put(JSONObject().put("bandwidth", 132000).put("baseUrl", "https://xy1.mcdn.bilivideo.cn:4483/upgcxcode/12/34/a-30280.m4s?e=1&deadline=2"))
        .put(JSONObject().put("bandwidth", 67000).put("base_url", "https://upos-sz-estgcos.bilivideo.com/upgcxcode/12/34/a-30216.m4s?e=1")
            .put("backup_url", JSONArray().put("https://upos-hz-mirrorakam.akamaized.net/upgcxcode/12/34/a-30216.m4s?e=1")))))).toString()

    @Test fun lowestBandwidthFirstWithBackups() {
        val c = Bili.audioCandidates(json)
        assertEquals("https://upos-sz-estgcos.bilivideo.com/upgcxcode/12/34/a-30216.m4s?e=1", c[0])
        assertEquals("https://upos-hz-mirrorakam.akamaized.net/upgcxcode/12/34/a-30216.m4s?e=1", c[1])
        assertTrue(c[2].startsWith("https://xy1.mcdn.bilivideo.cn"))
        // PCDN 地址换成官方 upos 镜像作为后备
        assertTrue(c.contains("https://upos-sz-mirrorcos.bilivideo.com/upgcxcode/12/34/a-30280.m4s?e=1&deadline=2"))
        assertEquals(c.size, c.distinct().size)
    }
    @Test fun durlFallbackAndErrors() {
        val mp4 = JSONObject().put("code", 0).put("data", JSONObject().put("durl", JSONArray().put(JSONObject().put("url", "https://upos-sz-mirror08c.bilivideo.com/upgcxcode/1/2/v.mp4?x=1")))).toString()
        assertEquals("https://upos-sz-mirror08c.bilivideo.com/upgcxcode/1/2/v.mp4?x=1", Bili.audioCandidates(mp4).first())
        assertTrue(Bili.audioCandidates("{\"code\":-404}").isEmpty())
        assertTrue(Bili.audioCandidates("not json").isEmpty())
        assertTrue(Bili.mirrorsOf("https://example.com/foo.m4s").isEmpty())
    }
}

/** 短课「0 张卡」：宽松解析 + 本地兜底。 */
class CardFallbackTest {
    @Test fun lenientKeys() {
        val a = JSONArray().put(JSONObject().put("question", "什么是梯度？").put("answer", "偏导数组成的向量"))
            .put(JSONObject().put("front", "Q2").put("back", "A2").put("type", "易错"))
            .put(JSONObject().put("q", "缺答案"))
        val cs = CardGen.fromJson(a)
        assertEquals(2, cs.size)
        assertEquals("考点", cs[0].tag)
        assertEquals("易错", cs[1].tag)
    }
    @Test fun parseReplyShapes() {
        assertEquals(1, CardGen.parseReply("```json\n{\"flashcards\":[{\"q\":\"a\",\"a\":\"b\"}]}\n```").size)
        assertEquals(2, CardGen.parseReply("好的：[{\"q\":\"a\",\"a\":\"b\"},{\"问题\":\"c\",\"答案\":\"d\"}]").size)
        assertTrue(CardGen.parseReply("抱歉").isEmpty())
    }
    @Test fun localFallbackReachesMinimum() {
        val secs = listOf(NoteSection("离散时间信号", body = "离散时间信号只在整数时刻有定义，记作 x[n]。"), NoteSection("单位脉冲", body = "δ[n] 在 n=0 时为 1，其余为 0，是最基本的信号。"))
        val cards = CardGen.local(secs, listOf(Mnemonic("脉冲看零点", "δ[n] 只在 n=0 处为 1")), listOf(Node("信号"), Node("x[n]", 0, "离散序列")))
        assertTrue(cards.size >= CardGen.MIN)
        assertEquals(cards.size, cards.map { it.q }.distinct().size)
        assertTrue(CardGen.needMore(emptyList()))
        assertFalse(CardGen.needMore(cards))
        // 已有的卡保留在前面
        val kept = CardGen.local(secs, emptyList(), emptyList(), listOf(Card("原有", "卡")))
        assertEquals("原有", kept.first().q)
    }
}

/** 课程表 now 线与翻书拖动跳页。 */
class GridAndScrubTest {
    @Test fun nowLinePosition() {
        assertNull(Schedule.gridPos(7 * 60))
        assertEquals(0f, Schedule.gridPos(8 * 60)!!, 0.001f)
        assertEquals(0.5f, Schedule.gridPos(8 * 60 + 22)!!, 0.03f)
        assertEquals(2f, Schedule.gridPos(10 * 60)!!, 0.001f) // 课间：落在下一节开始处
        assertNull(Schedule.gridPos(23 * 60))
    }
    @Test fun scrubMapping() {
        assertEquals(0, Scrub.pageAt(0f, 36))
        assertEquals(35, Scrub.pageAt(1f, 36))
        assertEquals(18, Scrub.pageAt(0.5f, 37))
        assertEquals(0, Scrub.pageAt(0.7f, 1))
        assertEquals(1f, Scrub.fractionOf(35, 36), 0.0001f)
        assertEquals(22, Scrub.pageAt(Scrub.fractionOf(22, 36), 36))
    }
}
