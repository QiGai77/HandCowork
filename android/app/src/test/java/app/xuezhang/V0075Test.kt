package app.xuezhang

import app.xuezhang.engine.Bili
import app.xuezhang.data.Card
import app.xuezhang.engine.Prompts
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V0075Test {

    @Test
    fun testBiliPickAudioCandidates() {
        val json = """
        {
            "code": 0,
            "data": {
                "dash": {
                    "audio": [
                        {
                            "id": 30280,
                            "baseUrl": "https://upos.bilibili.com/audio_main.m4a",
                            "backupUrl": ["https://backup1.bilibili.com/audio1.m4a"],
                            "bandwidth": 132000
                        },
                        {
                            "id": 30216,
                            "baseUrl": "https://upos.bilibili.com/audio_low.m4a",
                            "backup_url": ["https://backup2.bilibili.com/audio2.m4a"],
                            "bandwidth": 66000
                        }
                    ]
                }
            }
        }
        """.trimIndent()
        val candidates = Bili.pickAudioCandidates(json)
        assertEquals(4, candidates.size)
        // 码率低的排在前面
        assertEquals("https://upos.bilibili.com/audio_low.m4a", candidates[0])
        assertEquals("https://backup2.bilibili.com/audio2.m4a", candidates[1])
    }

    @Test
    fun testCardsRegenPromptAndParsing() {
        assertTrue(Prompts.CARDS_REGEN.contains("复习闪卡"))
        val mockJson = """
        {
            "cards": [
                {"q": "什么是降压斩波电路（Buck变换器）？", "a": "输出电压低于输入电压的直流-直流变换器，占空比为 D 时，U_o = D * U_i。", "tag": "概念"},
                {"q": "Buck变换器电感电流连续的临界条件是什么？", "a": "临界电感 L_crit = (1-D)*R / (2*f)。", "tag": "考点"}
            ]
        }
        """.trimIndent()
        val o = JSONObject(mockJson)
        val arr = o.getJSONArray("cards")
        val cards = (0 until arr.length()).map { i ->
            val c = arr.getJSONObject(i)
            Card(q = c.getString("q"), a = c.getString("a"), tag = c.getString("tag"))
        }
        assertEquals(2, cards.size)
        assertEquals("概念", cards[0].tag)
        assertEquals("考点", cards[1].tag)
    }
}
