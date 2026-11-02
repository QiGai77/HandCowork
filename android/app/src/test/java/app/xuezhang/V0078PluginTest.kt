package app.xuezhang

import app.xuezhang.engine.plugin.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** v0.0.7.8 插件系统与三大插件核心逻辑实测（DSL 清洗规则、供应商请求构造、主题规格、版本兼容与安全防护） */
class V0078PluginTest {

    // ================= 插件1：识别优化 (TextRules DSL) 实测 =================

    @Test
    fun testTextRulesFillersAndParticles() {
        val json = """
            {
              "steps": [
                { "op": "fillers", "level": 1, "words": ["嗯", "呃", "那个", "就是说"] },
                { "op": "particles", "level": 2, "words": ["啊", "呀", "嘛"] },
                { "op": "tags", "level": 2, "words": ["对吧", "是吧"] },
                { "op": "tidy", "level": 1 }
              ]
            }
        """.trimIndent()
        val prog = TextRules.parse(json)
        assertEquals(4, prog.steps.size)

        // 测试 Level 1：只去独立语气词（如「嗯，」「呃，」），不误伤语义词（如「那个电阻」）
        val res1 = TextRules.run(prog, "嗯，那个电阻阻值是十欧姆，呃，对吧？", level = 1)
        assertEquals("那个电阻阻值是十欧姆，对吧？", res1.text)

        // 测试 Level 2：中度清洗，去除语气词、助词与反问尾巴
        val res2 = TextRules.run(prog, "嗯，同学们，那个，这个电路的效率很高啊，对吧？", level = 2)
        assertTrue(!res2.text.contains("嗯"))
        assertTrue(!res2.text.contains("对吧"))
        assertTrue(!res2.text.contains("高啊"))
        assertTrue(res2.fillers > 0)
    }

    @Test
    fun testTextRulesStutterAndSelfCorrection() {
        val json = """
            {
              "steps": [
                { "op": "stutter", "level": 1, "maxLen": 4 },
                { "op": "selfCorrection", "level": 1, "markers": ["不对", "说错了"] },
                { "op": "tidy", "level": 1 }
              ]
            }
        """.trimIndent()
        val prog = TextRules.parse(json)

        // 口吃去重测试
        val resStutter = TextRules.run(prog, "这个这个电容的值是是一百微法。", level = 1)
        assertEquals("这个电容的值是一百微法。", resStutter.text)
        assertTrue(resStutter.repeats > 0)

        // 口误自纠测试：「锚点自纠」
        val resCorr = TextRules.run(prog, "变压器的原边电压是220伏，不对，是380伏。", level = 1)
        assertEquals("变压器的原边电压是380伏。", resCorr.text)
        assertEquals(1, resCorr.corrections)
    }

    @Test
    fun testTextRulesExamMarkAndDropSentence() {
        val json = """
            {
              "steps": [
                { "op": "mark", "level": 1, "tag": "exam", "patterns": ["要考", "重点"] },
                { "op": "dropSentence", "level": 3, "patterns": ["手机收(起来|一下)"] },
                { "op": "tidy", "level": 1 }
              ]
            }
        """.trimIndent()
        val prog = TextRules.parse(json)

        val input = "大家先把手机收一下。这个空载损耗公式期末肯定要考。"
        // Level 2：不删闲聊，但能标记考点
        val res2 = TextRules.run(prog, input, level = 2)
        assertEquals(1, res2.exam.size)
        assertTrue(res2.exam[0].contains("空载损耗公式"))
        assertTrue(res2.text.contains("手机收一下"))

        // Level 3：强力档，过滤闲聊
        val res3 = TextRules.run(prog, input, level = 3)
        assertTrue(!res3.text.contains("手机收一下"))
        assertTrue(res3.text.contains("空载损耗公式期末肯定要考"))
        assertEquals(listOf("大家先把手机收一下。"), res3.dropped)
    }

    // ================= 插件2：墨韵江南 (ThemeSpec & SplashSpec) 实测 =================

    @Test
    fun testThemeSpecColorAndAssetsParsing() {
        val json = """
            {
              "name": "墨韵江南",
              "forceLight": true,
              "colors": {
                "bg": "#F3EEE2",
                "ink": "#2B2B2B",
                "accent": "#B5442E",
                "accent2": "#2F4F4F"
              },
              "background": {
                "texture": "assets/xuan_paper.webp",
                "textureAlpha": 0.45,
                "top": "assets/mountains.webp"
              },
              "fonts": {
                "serif": "assets/lxgw-wenkai.ttf"
              },
              "nav": { "style": "ink" },
              "button": { "style": "seal" },
              "progress": { "style": "brush" }
            }
        """.trimIndent()
        val spec = ThemeSpec.parse(json)
        assertEquals("墨韵江南", spec.name)
        assertEquals(0xFFF3EEE2.toLong(), spec.color("bg"))
        assertEquals(0xFF2B2B2B.toLong(), spec.color("ink"))
        assertEquals(0xFFB5442E.toLong(), spec.color("accent"))
        assertEquals("assets/xuan_paper.webp", spec.texture)
        assertEquals("assets/lxgw-wenkai.ttf", spec.serifFont)
        assertEquals("seal", spec.buttonStyle)
        assertEquals("brush", spec.progressStyle)
        assertEquals("ink", spec.navStyle)
    }

    @Test
    fun testSplashSpecTimeline() {
        val json = """
            {
              "type": "ink-bloom",
              "title": "课堂学长",
              "seal": "学长",
              "durationMs": 2200,
              "ink": "#2B2B2B",
              "sealColor": "#B5442E"
            }
        """.trimIndent()
        val splash = SplashSpec.parse(json)
        assertEquals("ink-bloom", splash.type)
        assertEquals("课堂学长", splash.title)
        assertEquals("学长", splash.seal)
        assertEquals(2200, splash.durationMs)

        // 测试时间轴连续性与分段
        val t0 = SplashSpec.timeline(0.05f)
        assertTrue((t0["drop"] ?: 0f) > 0f)
        assertEquals(0f, t0["seal"] ?: 0f, 0.01f)

        val tMid = SplashSpec.timeline(0.5f)
        assertTrue((tMid["bloom"] ?: 0f) > 0.8f)
        assertTrue((tMid["mountains"] ?: 0f) > 0.5f)

        val tEnd = SplashSpec.timeline(0.85f)
        assertTrue((tEnd["seal"] ?: 0f) > 0.5f)
    }

    // ================= 插件3：供应商兼容 (ProviderSpec) 实测 =================

    @Test
    fun testProviderSpecAndSecurityNoSecretLeak() {
        val json = """
            {
              "providers": [
                {
                  "id": "deepseek",
                  "name": "DeepSeek 官方",
                  "style": "openai",
                  "baseUrl": "https://api.deepseek.com",
                  "authHeader": "Authorization",
                  "authPrefix": "Bearer ",
                  "models": ["deepseek-chat", "deepseek-reasoner"],
                  "defaultModel": "deepseek-chat",
                  "caps": ["chat"],
                  "key": "sk-should-never-be-imported",
                  "apiKey": "sk-drop-me"
                },
                {
                  "id": "claude",
                  "name": "Anthropic",
                  "style": "anthropic",
                  "baseUrl": "https://api.anthropic.com",
                  "models": ["claude-3-5-sonnet-20241022"],
                  "caps": ["chat", "vision"]
                }
              ]
            }
        """.trimIndent()
        val list = ProviderConfig.parseMany(json)
        assertEquals(2, list.size)

        val ds = list[0]
        assertEquals("deepseek", ds.id)
        assertEquals("openai", ds.style)
        assertEquals("https://api.deepseek.com", ds.baseUrl)
        assertTrue(ds.has("chat"))

        // 核心安全测试：导出的配置 JSON 绝对不包含任何 key 字段
        val exported = ProviderConfig.exportMany(list)
        assertTrue(!exported.contains("sk-should-never-be-imported"))
        assertTrue(!exported.contains("sk-drop-me"))
        assertTrue(!exported.contains("\"key\""))
        assertTrue(!exported.contains("\"apiKey\""))
    }

    @Test
    fun testProviderRequestBuilder() {
        val ds = ProviderConfig(
            id = "deepseek", name = "DeepSeek", style = "openai",
            baseUrl = "https://api.deepseek.com", defaultModel = "deepseek-chat",
            caps = setOf("chat")
        )
        val call = ProviderRequests.chat(ds, "fake-test-key", "deepseek-chat", "系统提示词", "用户问题", stream = false, json = true)
        assertEquals("POST", call.method)
        assertEquals("https://api.deepseek.com/chat/completions", call.url)
        assertEquals("Bearer fake-test-key", call.headers["Authorization"])
        val bodyObj = JSONObject(call.body)
        assertEquals("deepseek-chat", bodyObj.getString("model"))
        assertEquals("json_object", bodyObj.getJSONObject("response_format").getString("type"))
    }

    // ================= 市场、版本兼容与安全防护实测 =================

    @Test
    fun testSemverComparison() {
        assertTrue(Semver.compare("0.0.7.8", "0.0.7.7") > 0)
        assertTrue(Semver.compare("0.0.7.8", "0.0.7.8") == 0)
        assertTrue(Semver.compare("0.0.7.8", "0.0.8") < 0)
        assertTrue(Semver.compare("1.0.0", "0.9.9") > 0)
    }

    @Test
    fun testPluginFilesSecuritySafePath() {
        // 防止 Zip-Slip 路径穿越攻击
        assertTrue(PluginFiles.safePath("theme.json"))
        assertTrue(PluginFiles.safePath("assets/font.ttf"))
        assertTrue(PluginFiles.safePath("skills/deep/rule.json"))

        assertFalse(PluginFiles.safePath("../escape.json"))
        assertFalse(PluginFiles.safePath("foo/../../bar.json"))
        assertFalse(PluginFiles.safePath("/root/etc/passwd"))
        assertFalse(PluginFiles.safePath("C:\\Windows\\System32"))
        assertFalse(PluginFiles.safePath("assets\\font.ttf")) // 禁止反斜杠
    }

    @Test
    fun testPluginCompatibilityCheck() {
        val manifest = PluginManifest(
            id = "test-plugin",
            name = "测试插件",
            version = "1.0.0",
            types = listOf("theme"),
            apiVersion = 1,
            minApp = "0.0.7.8"
        )
        // 当前 App 为 0.0.7.8：兼容
        val okCompat = Compatibility.check(manifest, "0.0.7.8")
        assertTrue(okCompat.ok)

        // 旧版 App 0.0.7.7：不兼容，提示升级
        val oldCompat = Compatibility.check(manifest, "0.0.7.7")
        assertFalse(oldCompat.ok)
        assertTrue(oldCompat.reason.contains("0.0.7.8 以上"))
    }
}
