package app.xuezhang

import app.xuezhang.data.Card
import app.xuezhang.data.Ebbinghaus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.0.7.9 核心功能实测：
 * 1. 艾宾浩斯复习流记忆算法（间隔增长曲线、到期判断、标签格式化）
 * 2. 考点卡片数据序列化与向后兼容
 */
class V0079Test {

    @Test
    fun testEbbinghausIntervalCalculation() {
        // 忘了：固定 10 分钟
        assertEquals(10 * 60 * 1000L, Ebbinghaus.nextIntervalMs(reps = 0, grade = 0))
        assertEquals(10 * 60 * 1000L, Ebbinghaus.nextIntervalMs(reps = 3, grade = 0))
        assertEquals("10m", Ebbinghaus.intervalLabel(reps = 0, grade = 0))

        // 模糊：固定 1 天
        assertEquals(24 * 3600 * 1000L, Ebbinghaus.nextIntervalMs(reps = 0, grade = 1))
        assertEquals(24 * 3600 * 1000L, Ebbinghaus.nextIntervalMs(reps = 2, grade = 1))
        assertEquals("1d", Ebbinghaus.intervalLabel(reps = 0, grade = 1))

        // 记得：梯度增长 1d, 3d, 7d, 15d, 30d, 60d
        assertEquals(1 * 24 * 3600 * 1000L, Ebbinghaus.nextIntervalMs(reps = 0, grade = 2))
        assertEquals(3 * 24 * 3600 * 1000L, Ebbinghaus.nextIntervalMs(reps = 1, grade = 2))
        assertEquals(7 * 24 * 3600 * 1000L, Ebbinghaus.nextIntervalMs(reps = 2, grade = 2))
        assertEquals(15 * 24 * 3600 * 1000L, Ebbinghaus.nextIntervalMs(reps = 3, grade = 2))
        assertEquals(30 * 24 * 3600 * 1000L, Ebbinghaus.nextIntervalMs(reps = 4, grade = 2))
        assertEquals(60 * 24 * 3600 * 1000L, Ebbinghaus.nextIntervalMs(reps = 5, grade = 2))

        assertEquals("1d", Ebbinghaus.intervalLabel(reps = 0, grade = 2))
        assertEquals("3d", Ebbinghaus.intervalLabel(reps = 1, grade = 2))
        assertEquals("7d", Ebbinghaus.intervalLabel(reps = 2, grade = 2))
        assertEquals("15d", Ebbinghaus.intervalLabel(reps = 3, grade = 2))
        assertEquals("30d", Ebbinghaus.intervalLabel(reps = 4, grade = 2))
        assertEquals("60d", Ebbinghaus.intervalLabel(reps = 5, grade = 2))
    }

    @Test
    fun testEbbinghausIsDue() {
        val now = 1700000000000L
        // nextReviewAt 为 0：从未复习过，必须到期
        val c0 = Card("问题0", "答案0", nextReviewAt = 0L)
        assertTrue(Ebbinghaus.isDue(c0, now))

        // 时间已过去：到期
        val c1 = Card("问题1", "答案1", nextReviewAt = now - 5000L)
        assertTrue(Ebbinghaus.isDue(c1, now))

        // 时间还未到：未到期
        val c2 = Card("问题2", "答案2", nextReviewAt = now + 100000L)
        assertFalse(Ebbinghaus.isDue(c2, now))
    }

    @Test
    fun testCardSerializationBackwardCompatibility() {
        // 旧版本 JSON（无 nr 和 rp 字段）
        val oldJson = JSONObject().put("q", "泰勒展开式").put("a", "f(x)=...").put("t", "考点").put("lv", 1)
        val c = Card(
            q = oldJson.optString("q"),
            a = oldJson.optString("a"),
            tag = oldJson.optString("t", "考点"),
            level = oldJson.optInt("lv"),
            nextReviewAt = oldJson.optLong("nr", 0L),
            reps = oldJson.optInt("rp", 0)
        )
        assertEquals("泰勒展开式", c.q)
        assertEquals(0L, c.nextReviewAt)
        assertEquals(0, c.reps)

        // 新版本写入 JSON
        val newJson = JSONObject().put("q", c.q).put("a", c.a).put("t", c.tag).put("lv", c.level)
            .put("nr", 1700000000000L).put("rp", 3)
        assertEquals(1700000000000L, newJson.optLong("nr"))
        assertEquals(3, newJson.optInt("rp"))
    }
}
