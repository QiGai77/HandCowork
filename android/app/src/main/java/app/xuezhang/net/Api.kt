package app.xuezhang.net

import app.xuezhang.data.Store
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

open class ApiException(msg: String) : Exception(msg)

object Api {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun base(u: String) = u.trimEnd('/')
    private val JSON = "application/json".toMediaType()

    fun transcribe(wav: File, prompt: String = ""): String =
        app.xuezhang.engine.Log.api("语音识别 ${wav.length() / 1024}KB", Providers0.name(Store.settings.value.asrBase), Store.settings.value.asrModel) { transcribeRaw(wav, prompt) }

    private object Providers0 { fun name(b: String) = runCatching { java.net.URI(b).host ?: b }.getOrDefault(b) }

    private fun transcribeRaw(wav: File, prompt: String): String {
        val s = Store.settings.value
        if (s.asrKey.isBlank()) throw ApiException("请先在「我的」里填写语音识别 API Key")
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", s.asrModel)
            .addFormDataPart("file", wav.name, wav.asRequestBody((if (wav.name.endsWith(".wav")) "audio/wav" else "audio/mpeg").toMediaType()))
            .apply { if (prompt.isNotBlank()) addFormDataPart("prompt", prompt.take(200)) }
            .build()
        val req = Request.Builder().url(base(s.asrBase) + "/audio/transcriptions")
            .header("Authorization", "Bearer ${s.asrKey}").post(body).build()
        http.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw ApiException("识别失败 ${r.code}：${t.take(160)}")
            return runCatching { JSONObject(t).optString("text") }.getOrDefault(t).trim()
        }
    }

    // ======================= 大模型：按服务商 / 登录方式分发 =======================
    enum class Mode { OPENAI, CHATGPT, CLAUDE_KEY, CLAUDE_OAUTH }
    const val CODEX_URL = "https://chatgpt.com/backend-api/codex/responses"
    const val ANTHROPIC = "https://api.anthropic.com/v1"
    fun isAnthropic(base: String) = base.contains("anthropic.com")

    fun mode(s: app.xuezhang.data.Settings = Store.settings.value): Mode = when {
        s.llmAuth == "chatgpt" -> Mode.CHATGPT
        s.llmAuth == "claude" -> Mode.CLAUDE_OAUTH
        isAnthropic(s.llmBase) -> Mode.CLAUDE_KEY
        else -> Mode.OPENAI
    }

    /** 大模型是否已配置（Key 或账号登录）。 */
    fun llmReady(): Boolean = when (mode()) {
        Mode.CHATGPT -> Auth.chatgpt.value != null
        Mode.CLAUDE_OAUTH -> Auth.claude.value != null
        else -> Store.settings.value.llmKey.isNotBlank()
    }
    private fun requireLlm() { if (!llmReady()) throw ApiException("请先在「我的」里配置大模型（填写 API Key 或登录账号）") }

    /** 一段用户输入：文字或 JPEG 图片。 */
    class Part(val text: String? = null, val jpeg: ByteArray? = null)

    fun chat(system: String, user: String, json: Boolean = true, temperature: Double = 0.3, model: String? = null): String =
        gen(system, listOf(Part(user)), model ?: Store.settings.value.llmModel, json, temperature)

    /** 统一入口：OpenAI 兼容 chat/completions、ChatGPT 账号（Codex Responses）、Claude Messages。onDelta 不为空时流式返回累计文本。 */
    fun gen(system: String, parts: List<Part>, model: String, json: Boolean = false, temperature: Double = 0.3, onDelta: ((String) -> Unit)? = null): String {
        requireLlm()
        val what = (if (parts.any { it.jpeg != null }) "看图" else "对话") + (if (onDelta != null) "·流式" else "")
        return app.xuezhang.engine.Log.api(what, mode().name, model) { genRaw(system, parts, model, json, temperature, onDelta) }
    }

    private fun genRaw(system: String, parts: List<Part>, model: String, json: Boolean, temperature: Double, onDelta: ((String) -> Unit)?): String {
        return when (mode()) {
            Mode.OPENAI -> openaiChat(system, parts, model, json, temperature, onDelta)
            Mode.CHATGPT -> codex(system + if (json) "\n\n只输出一个 JSON，不要任何解释或代码块。" else "", parts, model, onDelta, null).first
            Mode.CLAUDE_KEY, Mode.CLAUDE_OAUTH -> claude(system + if (json) "\n\n只输出一个 JSON，不要任何解释或代码块。" else "", parts, model, temperature, onDelta)
        }
    }

    private fun b64(b: ByteArray) = android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP)

    private fun openaiChat(system: String, parts: List<Part>, model: String, json: Boolean, temperature: Double, onDelta: ((String) -> Unit)?): String {
        val s = Store.settings.value
        val content: Any = if (parts.size == 1 && parts[0].jpeg == null) parts[0].text.orEmpty() else JSONArray().also { a ->
            parts.forEach { p ->
                p.jpeg?.let { a.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,${b64(it)}"))) }
                p.text?.let { a.put(JSONObject().put("type", "text").put("text", it)) }
            }
        }
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system)).put(JSONObject().put("role", "user").put("content", content))
        if (onDelta == null) return complete(msgs, model, json, temperature)
        val o = JSONObject().put("model", model).put("stream", true).put("temperature", temperature).put("messages", msgs)
        val req = Request.Builder().url(base(s.llmBase) + "/chat/completions").header("Authorization", "Bearer ${s.llmKey}")
            .post(o.toString().toRequestBody(JSON)).build()
        val sb = StringBuilder()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw ApiException("大模型请求失败 ${r.code}：${r.body?.string().orEmpty().take(160)}")
            sse(r) { _, j ->
                j.optJSONObject("usage")?.optInt("completion_tokens")?.takeIf { it > 0 }?.let { lastTokens = it }
                val d = j.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content").orEmpty()
                if (d.isNotEmpty()) { sb.append(d); onDelta(sb.toString()) }
                true
            }
        }
        return sb.toString()
    }

    @Volatile private var lastTokens = 0

    /** 读 SSE：回调 (event, data-json)，返回 false 停止。 */
    private fun sse(r: okhttp3.Response, onEvent: (String, JSONObject) -> Boolean) {
        val src = r.body!!.source(); var ev = ""
        while (!src.exhausted()) {
            val line = src.readUtf8Line() ?: break
            if (line.startsWith("event:")) { ev = line.removePrefix("event:").trim(); continue }
            if (!line.startsWith("data:")) continue
            val d = line.removePrefix("data:").trim()
            if (d == "[DONE]") break
            val j = runCatching { JSONObject(d) }.getOrNull() ?: continue
            if (!onEvent(j.optString("type").ifBlank { ev }, j)) break
        }
    }

    /** chat/completions 的回答文本：content 可能是 null、数组（多段），推理模型可能只给 reasoning_content。 */
    fun messageText(j: JSONObject): String {
        val m = j.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        val c = m.opt("content")
        val text = when (c) {
            is String -> c
            is JSONArray -> (0 until c.length()).joinToString("") { i -> c.optJSONObject(i)?.optString("text").orEmpty() }
            else -> ""
        }
        if (text.isNotBlank()) return text
        val r = m.optString("reasoning_content").takeIf { it.isNotBlank() && it != "null" } ?: m.optString("reasoning").takeIf { it != "null" }.orEmpty()
        if (r.isNotBlank()) return r
        val refusal = m.optString("refusal").takeIf { it.isNotBlank() && it != "null" }
        throw ApiException("模型回答为空" + (refusal?.let { "（$it）" } ?: "，finish_reason=" + j.getJSONArray("choices").getJSONObject(0).optString("finish_reason")))
    }

    private fun complete(msgs: JSONArray, model: String, json: Boolean, temperature: Double): String {
        val s = Store.settings.value
        val o = JSONObject().put("model", model).put("messages", msgs).put("stream", false)
        if (!temperature.isNaN()) o.put("temperature", temperature)
        if (json) o.put("response_format", JSONObject().put("type", "json_object"))
        val req = Request.Builder().url(base(s.llmBase) + "/chat/completions")
            .header("Authorization", "Bearer ${s.llmKey}")
            .post(o.toString().toRequestBody(JSON)).build()
        http.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                if (json && r.code == 400 && t.contains("response_format")) return complete(msgs, model, false, temperature)
                if (r.code == 400 && t.contains("temperature") && !temperature.isNaN()) return complete(msgs, model, json, Double.NaN)
                throw ApiException("大模型请求失败 ${r.code}：${t.take(160)}")
            }
            return runCatching { messageText(JSONObject(t)) }
                .getOrElse { throw ApiException("大模型返回格式不对：${t.take(160)}") }
        }
    }

    // ---------------- ChatGPT 账号：Codex Responses API ----------------
    /** 返回 (文本, 生成的图片 b64 列表)。 */
    private fun codex(instructions: String, parts: List<Part>, model: String, onDelta: ((String) -> Unit)?, imageTool: JSONObject?, retried: Boolean = false): Pair<String, List<String>> {
        val tok = Auth.gpt()
        val content = JSONArray().also { a ->
            parts.forEach { p ->
                p.text?.let { a.put(JSONObject().put("type", "input_text").put("text", it)) }
                p.jpeg?.let { a.put(JSONObject().put("type", "input_image").put("image_url", "data:image/jpeg;base64,${b64(it)}")) }
            }
        }
        val o = JSONObject().put("model", model).put("instructions", instructions.ifBlank { "You are a helpful assistant." })
            .put("input", JSONArray().put(JSONObject().put("type", "message").put("role", "user").put("content", content)))
            .put("stream", true).put("store", false).put("parallel_tool_calls", false)
            .put("tools", JSONArray().also { if (imageTool != null) it.put(imageTool) })
            .put("tool_choice", "auto")
        val req = Request.Builder().url(CODEX_URL)
            .header("Authorization", "Bearer ${tok.access}")
            .header("chatgpt-account-id", tok.accountId)
            .header("OpenAI-Beta", "responses=experimental")
            .header("originator", "codex_cli_rs")
            .header("session_id", java.util.UUID.randomUUID().toString())
            .header("Accept", "text/event-stream")
            .post(o.toString().toRequestBody(JSON)).build()
        val sb = StringBuilder(); val imgs = mutableListOf<String>(); var err: String? = null
        http.newCall(req).execute().use { r ->
            quotaFromHeaders(r.headers)?.let { Auth.gptQuota.value = it }
            if (r.code == 401 && !retried) { r.close(); Auth.refreshChatGPT(); return codex(instructions, parts, model, onDelta, imageTool, true) }
            if (!r.isSuccessful) {
                val t = r.body?.string().orEmpty()
                throw ApiException(if (r.code == 429) "ChatGPT 额度用完了，等额度重置后再试" else "ChatGPT 请求失败 ${r.code}：${t.take(160)}")
            }
            sse(r) { type, j ->
                when (type) {
                    "response.output_text.delta" -> { sb.append(j.optString("delta")); onDelta?.invoke(sb.toString()) }
                    "response.output_item.done" -> j.optJSONObject("item")?.let { it ->
                        if (it.optString("type") == "image_generation_call") it.optString("result").takeIf { r -> r.isNotBlank() }?.let { r -> imgs.add("b64:$r") }
                    }
                    "response.failed", "error" -> err = (j.optJSONObject("response")?.optJSONObject("error") ?: j.optJSONObject("error") ?: j).optString("message").ifBlank { "生成失败" }
                    "response.completed" -> return@sse false
                }
                true
            }
        }
        err?.let { throw ApiException("ChatGPT：$it") }
        return sb.toString() to imgs
    }

    // ---------------- Claude Messages API ----------------
    private fun claude(system: String, parts: List<Part>, model: String, temperature: Double, onDelta: ((String) -> Unit)?, retried: Boolean = false): String {
        val s = Store.settings.value
        val oauth = mode() == Mode.CLAUDE_OAUTH
        val content = JSONArray().also { a ->
            parts.forEach { p ->
                p.jpeg?.let { a.put(JSONObject().put("type", "image").put("source", JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", b64(it)))) }
                p.text?.let { a.put(JSONObject().put("type", "text").put("text", it)) }
            }
        }
        val sys = JSONArray()
        if (oauth) sys.put(JSONObject().put("type", "text").put("text", "You are Claude Code, Anthropic's official CLI for Claude."))
        if (system.isNotBlank()) sys.put(JSONObject().put("type", "text").put("text", system))
        val o = JSONObject().put("model", model).put("max_tokens", 8192).put("system", sys).put("temperature", temperature)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content))).put("stream", onDelta != null)
        val url = (if (oauth || s.llmBase.isBlank()) ANTHROPIC else base(s.llmBase)) + "/messages"
        val rb = Request.Builder().url(url).header("anthropic-version", "2023-06-01").header("content-type", "application/json")
        if (oauth) rb.header("Authorization", "Bearer ${Auth.cl().access}").header("anthropic-beta", "oauth-2025-04-20")
        else rb.header("x-api-key", s.llmKey)
        val sb = StringBuilder()
        http.newCall(rb.post(o.toString().toRequestBody(JSON)).build()).execute().use { r ->
            if (oauth && r.code == 401 && !retried) { r.close(); Auth.refreshClaude(); return claude(system, parts, model, temperature, onDelta, true) }
            if (!r.isSuccessful) throw ApiException("Claude 请求失败 ${r.code}：${r.body?.string().orEmpty().take(160)}")
            if (onDelta == null) {
                val j = runCatching { JSONObject(r.body!!.string()) }.getOrElse { throw ApiException("Claude 返回格式不对") }
                val a = j.optJSONArray("content") ?: JSONArray()
                for (i in 0 until a.length()) a.optJSONObject(i)?.takeIf { it.optString("type") == "text" }?.let { sb.append(it.optString("text")) }
                j.optJSONObject("usage")?.optInt("output_tokens")?.let { lastTokens = it }
            } else sse(r) { type, j ->
                when (type) {
                    "content_block_delta" -> j.optJSONObject("delta")?.optString("text")?.takeIf { it.isNotEmpty() }?.let { sb.append(it); onDelta(sb.toString()) }
                    "message_delta" -> j.optJSONObject("usage")?.optInt("output_tokens")?.takeIf { it > 0 }?.let { lastTokens = it }
                    "error" -> throw ApiException("Claude：" + (j.optJSONObject("error")?.optString("message") ?: "生成失败"))
                    "message_stop" -> return@sse false
                }
                true
            }
        }
        return sb.toString()
    }

    fun chatJson(system: String, user: String): JSONObject {
        val t = chat(system, user, true)
        return extractJsonObject(t) ?: throw ApiException("模型返回的 JSON 不完整")
    }

    /** 从模型回答里抠出第一个完整的 JSON 对象（容忍 ```json 包裹、前后废话）。 */
    fun extractJsonObject(t: String): JSONObject? = app.xuezhang.engine.JsonLoose.obj(t)

    /** 当前服务商是不是 OpenAI / ChatGPT 账号（概念图走 gpt-image 直接出图）。 */
    fun isGpt(): Boolean = mode() == Mode.CHATGPT || (mode() == Mode.OPENAI && Store.settings.value.llmBase.contains("api.openai.com"))

    /** 视觉模型（「看图」模型）。 */
    fun vision(system: String, prompt: String, jpeg: ByteArray): String {
        val s = Store.settings.value
        val vm = visionModelFor(mode(), s.visionModel, s.llmModel)
        return try { gen(system, listOf(Part(jpeg = jpeg), Part(prompt)), vm, false, 0.1) }
        catch (e: ApiException) {
            // 「看图」模型不存在 / 不支持图片时，退回对话模型再试一次
            if (vm != s.llmModel && Regex("\\b(400|404|422)\\b").containsMatchIn(e.message.orEmpty()))
                gen(system, listOf(Part(jpeg = jpeg), Part(prompt)), s.llmModel, false, 0.1) else throw e
        }
    }

    /** 「看图」模型和当前服务商对不上时（例如登录 ChatGPT 账号却还留着 SiliconFlow 的 Qwen-VL），改用对话模型。 */
    fun visionModelFor(m: Mode, vision: String, llm: String): String = when {
        vision.isBlank() -> llm
        m == Mode.CHATGPT && !Regex("^(gpt|o\\d|codex)", RegexOption.IGNORE_CASE).containsMatchIn(vision) -> llm
        (m == Mode.CLAUDE_KEY || m == Mode.CLAUDE_OAUTH) && !vision.startsWith("claude") -> llm
        else -> vision
    }

    /** 文生图（「画图」模型）：OpenAI 兼容 /images/generations；ChatGPT 账号走 Responses 的 image_generation 工具。 */
    fun images(prompt: String, n: Int = 2, size: String = "1024x576", model: String? = null): List<String> {
        val s = Store.settings.value.let { if (model != null) it.copy(imageModel = model) else it }
        when (mode()) {
            Mode.CHATGPT -> {
                val tool = JSONObject().put("type", "image_generation").put("size", "1536x1024").put("quality", "medium")
                    .apply { if (s.imageModel.startsWith("gpt-image")) put("model", s.imageModel) }
                val out = mutableListOf<String>(); var last: Throwable? = null
                repeat(n.coerceIn(1, 2)) {
                    runCatching { codex("Generate exactly one image with the image_generation tool. Do not reply with text.", listOf(Part(prompt)), s.llmModel, null, tool).second }
                        .onSuccess { r -> out.addAll(r) }.onFailure { e -> last = e }
                }
                if (out.isEmpty()) throw (last as? ApiException) ?: ApiException("没有返回图片")
                return out
            }
            Mode.CLAUDE_KEY, Mode.CLAUDE_OAUTH -> throw ApiException("Claude 不能画图，请把大模型换成 OpenAI、SiliconFlow 等支持画图的服务商")
            else -> {}
        }
        val key = s.llmKey.ifBlank { s.asrKey }
        if (key.isBlank()) throw ApiException("请先在「我的」里填写 API Key")
        val o = JSONObject().put("model", s.imageModel).put("prompt", prompt)
        when {
            s.llmBase.contains("siliconflow") -> o.put("image_size", size).put("batch_size", n.coerceIn(1, 4)).put("num_inference_steps", 20)
            s.imageModel.startsWith("gpt-image") -> o.put("n", n).put("size", "1536x1024")
            s.imageModel.startsWith("dall-e-3") -> o.put("n", 1).put("size", "1792x1024")
            else -> o.put("n", n).put("size", size)
        }
        val req = Request.Builder().url(base(s.llmBase) + "/images/generations")
            .header("Authorization", "Bearer $key").post(o.toString().toRequestBody(JSON)).build()
        http.newCall(req).execute().use { r ->
            val t = r.body?.string().orEmpty()
            app.xuezhang.engine.Log.add("API", "画图 · ${s.imageModel} · HTTP ${r.code}", r.isSuccessful)
            if (!r.isSuccessful) throw ApiException("生成失败 ${r.code}：${t.take(120)}")
            val j = runCatching { JSONObject(t) }.getOrElse { throw ApiException("图片接口返回格式不对") }
            val arr = j.optJSONArray("images") ?: j.optJSONArray("data") ?: JSONArray()
            val out = (0 until arr.length()).mapNotNull { i ->
                val e = arr.optJSONObject(i) ?: return@mapNotNull null
                e.optString("url").ifBlank { e.optString("b64_json").takeIf { it.isNotBlank() }?.let { "b64:$it" } }
            }.filter { it.isNotBlank() }
            if (out.isEmpty()) throw ApiException("没有返回图片")
            return out
        }
    }

    // ---------------- 额度（5 小时 / 每周） ----------------
    private fun num(o: JSONObject, vararg k: String): Double? = k.firstNotNullOfOrNull { key -> o.opt(key)?.let { v -> (v as? Number)?.toDouble() ?: v.toString().toDoubleOrNull() } }
    private fun resetOf(o: JSONObject): Long? {
        num(o, "reset_after_seconds", "resets_in_seconds", "reset_in_seconds")?.let { return System.currentTimeMillis() + (it * 1000).toLong() }
        for (k in listOf("reset_at", "resets_at", "reset_time")) {
            val v = o.opt(k) ?: continue
            (v as? Number)?.toLong()?.let { return if (it < 10_000_000_000L) it * 1000 else it }
            v.toString().toLongOrNull()?.let { return if (it < 10_000_000_000L) it * 1000 else it }
            runCatching { return java.time.OffsetDateTime.parse(v.toString()).toInstant().toEpochMilli() }
            runCatching { return java.time.Instant.parse(v.toString()).toEpochMilli() }
        }
        return null
    }
    private fun findObj(o: JSONObject, keys: List<String>, depth: Int = 0): JSONObject? {
        keys.forEach { k -> o.optJSONObject(k)?.let { return it } }
        if (depth > 3) return null
        o.keys().forEach { k -> o.optJSONObject(k)?.let { c -> findObj(c, keys, depth + 1)?.let { return it } } }
        return null
    }
    fun parseQuota(t: String): Quota {
        val o = JSONObject(t)
        val five = findObj(o, listOf("primary_window", "primary", "five_hour"))
        val week = findObj(o, listOf("secondary_window", "secondary", "seven_day", "weekly"))
        fun pct(w: JSONObject?) = w?.let { num(it, "used_percent", "utilization", "percent_used", "used_percentage") }
        return Quota(pct(five), five?.let { resetOf(it) }, pct(week), week?.let { resetOf(it) })
    }
    private fun quotaFromHeaders(h: okhttp3.Headers): Quota? {
        val p = h["x-codex-primary-used-percent"]?.toDoubleOrNull(); val sct = h["x-codex-secondary-used-percent"]?.toDoubleOrNull()
        if (p == null && sct == null) return null
        fun rs(k: String) = h[k]?.toDoubleOrNull()?.let { System.currentTimeMillis() + (it * 1000).toLong() }
        val old = Auth.gptQuota.value
        return Quota(p ?: old?.fivePct, rs("x-codex-primary-reset-after-seconds") ?: old?.fiveReset,
            sct ?: old?.weekPct, rs("x-codex-secondary-reset-after-seconds") ?: old?.weekReset)
    }
    fun quotaChatGPT(retried: Boolean = false): Quota {
        val tok = Auth.gpt()
        val req = Request.Builder().url("https://chatgpt.com/backend-api/wham/usage")
            .header("Authorization", "Bearer ${tok.access}").header("chatgpt-account-id", tok.accountId)
            .header("OpenAI-Beta", "responses=experimental").header("originator", "codex_cli_rs").build()
        http.newCall(req).execute().use { r ->
            if (r.code == 401 && !retried) { r.close(); Auth.refreshChatGPT(); return quotaChatGPT(true) }
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw ApiException("获取额度失败 ${r.code}")
            return runCatching { parseQuota(t) }.getOrElse { throw ApiException("额度接口返回格式不对") }.also { Auth.gptQuota.value = it }
        }
    }
    fun quotaClaude(retried: Boolean = false): Quota {
        val tok = Auth.cl()
        val req = Request.Builder().url("https://api.anthropic.com/api/oauth/usage")
            .header("Authorization", "Bearer ${tok.access}").header("anthropic-beta", "oauth-2025-04-20").build()
        http.newCall(req).execute().use { r ->
            if (r.code == 401 && !retried) { r.close(); Auth.refreshClaude(); return quotaClaude(true) }
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw ApiException("获取额度失败 ${r.code}")
            return runCatching { parseQuota(t) }.getOrElse { throw ApiException("额度接口返回格式不对") }.also { Auth.claudeQuota.value = it }
        }
    }

    fun download(url: String): ByteArray {
        if (url.startsWith("b64:")) return android.util.Base64.decode(url.removePrefix("b64:"), android.util.Base64.DEFAULT)
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw ApiException("下载失败 ${r.code}")
            return r.body!!.bytes()
        }
    }

    val CHATGPT_MODELS = listOf("gpt-6.1-sol", "gpt-6.1-terra", "gpt-6-astra", "gpt-6-sol", "gpt-6-luna")
    const val CODEX_CLIENT_VERSION = "0.98.0"
    @Volatile private var chatgptCache: List<String>? = null

    /** 和 Codex CLI 一样从上游取 ChatGPT 账号可用模型；失败时用缓存/内置列表。 */
    fun chatgptModels(refresh: Boolean = true): List<String> {
        if (!refresh) chatgptCache?.let { return it }
        val got = runCatching {
            val tok = Auth.gpt()
            val req = Request.Builder().url("https://chatgpt.com/backend-api/codex/models?client_version=$CODEX_CLIENT_VERSION")
                .header("Authorization", "Bearer ${tok.access}").header("chatgpt-account-id", tok.accountId)
                .header("originator", "codex_cli_rs").header("OpenAI-Beta", "responses=experimental").build()
            http.newCall(req).execute().use { r -> if (r.isSuccessful) parseCodexModels(r.body?.string().orEmpty()) else emptyList() }
        }.onFailure { app.xuezhang.engine.Log.err("模型列表", it, "ChatGPT 模型列表获取失败") }.getOrDefault(emptyList())
        val list = got.ifEmpty { chatgptCache ?: CHATGPT_MODELS }
        if (got.isNotEmpty()) { chatgptCache = got; runCatching { java.io.File(Store.dir, "chatgpt_models.txt").writeText(got.joinToString("\n")) } }
        else if (chatgptCache == null) runCatching { java.io.File(Store.dir, "chatgpt_models.txt").readLines().filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let { chatgptCache = it; return it } }
        return list
    }

    /** 解析 codex/models：{models:[{slug|id, visibility, deprecated, hidden}]} 或 {data:[…]}；跳过隐藏/弃用项。 */
    fun parseCodexModels(t: String): List<String> {
        val j = runCatching { JSONObject(t) }.getOrNull() ?: return emptyList()
        val a = j.optJSONArray("models") ?: j.optJSONArray("data") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            when (val v = a.opt(i)) {
                is String -> v
                is JSONObject -> {
                    val hidden = v.optBoolean("hidden") || v.optBoolean("deprecated") || v.optString("visibility").let { it == "hide" || it == "hidden" || it == "none" }
                    if (hidden) null else v.optString("slug").ifBlank { v.optString("id") }.ifBlank { v.optString("model") }
                }
                else -> null
            }
        }.filter { it.isNotBlank() }.distinct()
    }

    /** OpenAI /v1/models：对话用的 gpt-6* 排前面，过滤掉嵌入/语音/审核等非对话模型。 */
    fun sortOpenAiChat(ids: List<String>): List<String> {
        val bad = Regex("embedding|whisper|tts|transcribe|moderation|dall-e|davinci|babbage|realtime|audio|search|computer-use", RegexOption.IGNORE_CASE)
        val chat = ids.filterNot { bad.containsMatchIn(it) }
        return chat.filter { it.startsWith("gpt-6") }.sortedDescending() + chat.filterNot { it.startsWith("gpt-6") }.filterNot { it.startsWith("gpt-5") || it.startsWith("gpt-4") || it.startsWith("gpt-3") } +
            chat.filter { it.startsWith("gpt-5") || it.startsWith("gpt-4") || it.startsWith("gpt-3") } + ids.filter { bad.containsMatchIn(it) }
    }
    val CLAUDE_MODELS = listOf("claude-sonnet-4-5", "claude-opus-4-5", "claude-haiku-4-5")

    /** GET /models，返回模型 id 列表。auth = key / chatgpt / claude。 */
    fun models(baseUrl: String, key: String, type: String? = null, auth: String = "key"): List<String> {
        if (auth == "chatgpt") return chatgptModels(true)
        if (auth == "claude") return runCatching {
            val req = Request.Builder().url("$ANTHROPIC/models?limit=100").header("Authorization", "Bearer ${Auth.cl().access}")
                .header("anthropic-beta", "oauth-2025-04-20").header("anthropic-version", "2023-06-01").build()
            http.newCall(req).execute().use { r -> ids(r.body?.string().orEmpty()).ifEmpty { CLAUDE_MODELS } }
        }.getOrDefault(CLAUDE_MODELS)
        if (key.isBlank()) throw ApiException("先填写 API Key")
        val q = if (type != null && baseUrl.contains("siliconflow")) "?type=$type" else ""
        val rb = Request.Builder().url(base(baseUrl) + "/models$q")
        if (isAnthropic(baseUrl)) rb.header("x-api-key", key).header("anthropic-version", "2023-06-01") else rb.header("Authorization", "Bearer $key")
        http.newCall(rb.build()).execute().use { r ->
            val t = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw ApiException("获取模型失败 ${r.code}")
            return ids(t).let { if (baseUrl.contains("api.openai.com")) sortOpenAiChat(it) else it }
        }
    }
    private fun ids(t: String): List<String> {
        val a = runCatching { JSONObject(t).optJSONArray("data") }.getOrNull() ?: JSONArray()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("id") }.filter { it.isNotBlank() }
    }

    /** SiliconFlow 余额（其他平台返回 null）。 */
    fun balance(): String? {
        val s = Store.settings.value
        if (mode(s) != Mode.OPENAI || !s.llmBase.contains("siliconflow") || s.llmKey.isBlank()) return null
        return runCatching {
            val req = Request.Builder().url(base(s.llmBase) + "/user/info").header("Authorization", "Bearer ${s.llmKey}").build()
            http.newCall(req).execute().use { r ->
                val d = JSONObject(r.body!!.string()).optJSONObject("data") ?: return null
                val b = d.optString("totalBalance").ifBlank { d.optString("balance") }
                b.toDoubleOrNull()?.let { "¥%.2f".format(it) }
            }
        }.getOrNull()
    }

    data class Bench(val text: String, val firstMs: Long, val tokPerSec: Double)

    /** 流式请求，测首字延迟和速度。onDelta 在 IO 线程回调。 */
    fun benchChat(prompt: String, onDelta: (String) -> Unit): Bench {
        val t0 = System.currentTimeMillis()
        var first = 0L
        lastTokens = 0
        val text = gen("", listOf(Part(prompt)), Store.settings.value.llmModel, false, 0.5) { t ->
            if (first == 0L) first = System.currentTimeMillis() - t0
            onDelta(t)
        }
        val total = (System.currentTimeMillis() - t0 - first).coerceAtLeast(1)
        val tk = if (lastTokens > 0) lastTokens else (text.length / 1.4).toInt()
        return Bench(text, first, tk * 1000.0 / total)
    }

    /** OpenAI 兼容 TTS（SiliconFlow CosyVoice）生成测试音频。 */
    fun speech(text: String, out: File) {
        val s = Store.settings.value
        val key = s.asrKey.ifBlank { s.llmKey }
        val o = JSONObject().put("model", "FunAudioLLM/CosyVoice2-0.5B").put("input", text)
            .put("voice", "FunAudioLLM/CosyVoice2-0.5B:alex").put("response_format", "mp3")
        val req = Request.Builder().url(base(s.asrBase) + "/audio/speech").header("Authorization", "Bearer $key")
            .post(o.toString().toRequestBody(JSON)).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw ApiException("生成测试音频失败 ${r.code}")
            out.writeBytes(r.body!!.bytes())
        }
    }
}
