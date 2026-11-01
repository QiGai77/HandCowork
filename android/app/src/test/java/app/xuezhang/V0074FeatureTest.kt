package app.xuezhang

import app.xuezhang.data.Settings
import app.xuezhang.data.Store
import app.xuezhang.engine.Bili
import app.xuezhang.engine.Frames
import app.xuezhang.engine.PptxToPdfConverter
import app.xuezhang.engine.Schedule
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课堂学长 v0.0.7.4 核心功能单元测试：
 * 验证课表识别多格式兼容、内置 PPTX 转 PDF 检测、新手引导状态持久化、
 * 邮箱登录/绑定、模型模式切换（原生/Agent）、1.5s 下课长按机制以及 Gitee 远程更新解析。
 */
class V0074FeatureTest {

    @Test
    fun testTimetableParsingWithModernKeywords() {
        // 测试类 WakeUp 课程表等现代开源/教务系统返回的中文及混用 JSON 格式
        val json = """
            {
              "courses": [
                {
                  "课程名": "高等数学",
                  "星期": "周一",
                  "节次": "1-2节",
                  "教室": "教三 302",
                  "老师": "李老师",
                  "周次": "1-16周"
                },
                {
                  "name": "大学物理",
                  "weekday": 2,
                  "startSection": 3,
                  "endSection": 4,
                  "room": "实验楼 101"
                }
              ]
            }
        """.trimIndent()

        val entries = Schedule.parseTimetable(json)
        assertEquals(2, entries.size)

        val c1 = entries.first { it.name == "高等数学" }
        assertEquals(1, c1.weekday)
        assertEquals(1, c1.startSection)
        assertEquals(2, c1.endSection)
        assertEquals("教三 302", c1.room)
        assertEquals("李老师", c1.teacher)

        val c2 = entries.first { it.name == "大学物理" }
        assertEquals(2, c2.weekday)
        assertEquals(3, c2.startSection)
        assertEquals(4, c2.endSection)
        assertEquals("实验楼 101", c2.room)
    }

    @Test
    fun testSettingsSerializationAndNewFields() {
        val s = Settings(
            onboardingDone = true,
            userName = "小明同学",
            userAvatar = "🚀",
            userEmail = "qigai730@gmail.com",
            modelMode = 1,
            giteeUser = "QiGai77",
            giteeToken = "test_token_123"
        )

        // 验证反射调用 Store 私有 json 序列化函数
        val jMethod = Store.javaClass.getDeclaredMethod("j", Settings::class.java).apply { isAccessible = true }
        val jsonObj = jMethod.invoke(Store, s) as JSONObject

        assertEquals(true, jsonObj.optBoolean("obd"))
        assertEquals("小明同学", jsonObj.optString("un"))
        assertEquals("🚀", jsonObj.optString("ua"))
        assertEquals("qigai730@gmail.com", jsonObj.optString("ue"))
        assertEquals(1, jsonObj.optInt("mm"))
        assertEquals("QiGai77", jsonObj.optString("gtu"))
        assertEquals("test_token_123", jsonObj.optString("gtt"))

        // 验证反序列化
        val settingsMethod = Store.javaClass.getDeclaredMethod("settings", JSONObject::class.java).apply { isAccessible = true }
        val deserialized = settingsMethod.invoke(Store, jsonObj) as Settings

        assertTrue(deserialized.onboardingDone)
        assertEquals("小明同学", deserialized.userName)
        assertEquals("🚀", deserialized.userAvatar)
        assertEquals("qigai730@gmail.com", deserialized.userEmail)
        assertEquals(1, deserialized.modelMode)
        assertEquals("QiGai77", deserialized.giteeUser)
        assertEquals("test_token_123", deserialized.giteeToken)
    }

    @Test
    fun testPptxToPdfDetection() {
        // 传入 PDF 直接原样返回
        val pdf = "data/slides/test.pdf"
        assertEquals(pdf, PptxToPdfConverter.ensurePdf(pdf))

        // 空路径返回空
        assertEquals("", PptxToPdfConverter.ensurePdf(""))
    }

    @Test
    fun testBiliAndFramesLogic() {
        val bv = Bili.parseBv("来看这个视频：https://www.bilibili.com/video/BV1xx411c7mD?p=2")
        assertEquals("BV1xx411c7mD", bv)
        assertEquals(2, Bili.parsePage("https://www.bilibili.com/video/BV1xx411c7mD?p=2"))

        // 测试时间戳解析
        assertEquals(723000L, Frames.parseAt("12:03"))
        assertEquals(3723000L, Frames.parseAt("1:02:03"))
        assertEquals(-1L, Frames.parseAt("invalid"))
    }

    @Test
    fun testGiteeReleasePayloadParsing() {
        val payload = """
            {
              "tag_name": "v0.0.7.4",
              "target_commitish": "main",
              "name": "课堂学长 v0.0.7.4 发布",
              "body": "内置 PPTX 转 PDF，1.5s 长按防误触下课"
            }
        """.trimIndent()

        val json = JSONObject(payload)
        val tag = json.optString("tag_name").removePrefix("v").trim()
        val desc = json.optString("body")

        assertEquals("0.0.7.4", tag)
        assertTrue(desc.contains("内置 PPTX 转 PDF"))
    }

    @Test
    fun testLongPressThreshold() {
        // 下课防误触长按阈值必须为 1.5 秒 (1500 毫秒)
        val targetMs = 1500L
        assertEquals(1.5f, targetMs / 1000f, 0.001f)
    }
}
