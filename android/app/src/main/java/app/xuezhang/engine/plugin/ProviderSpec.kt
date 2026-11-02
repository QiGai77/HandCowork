package app.xuezhang.engine.plugin

import org.json.JSONArray
import org.json.JSONObject

/**
 * v0.0.7.8「供应商兼容」：任意 OpenAI 兼容 / Anthropic / Gemini 风格的服务商与中转（one-api / new-api 一类）。
 * 配置是纯数据；Key 只存在本机加密存储（[app.xuezhang.engine.plugin.Plugins] 用 Keystore AES-GCM），
 * 不进插件文件、不进导出 JSON、不进备份。
 */
data class ProviderConfig(
    val id: String,
    val name: String,
    /** openai / anthropic / gemini */
    val style: String = "openai",
    val baseUrl: String,
    /** 认证头名：Authorization / x-api-key / api-key / x-goog-api-key；query:key 表示放在 URL 参数 ?key= */
    val authHeader: String = "Authorization",
    /** 认证值前缀：「Bearer 」或空 */
    val authPrefix: String = "Bearer ",
    val headers: Map<String, String> = emptyMap(),
    val models: List<String> = emptyList(),
    val defaultModel: String = "",
    val caps: Set<String> = setOf("chat"),
    /** 可选 HTTP 代理 host:port（本机直连不了时用） */
    val proxy: String = "",
    /** 中转/上游说明（如 new-api 的渠道名），只做展示 */
    val upstream: String = "",
    val chatPath: String = "",
    val modelsPath: String = "",
    val asrPath: String = "",
    val asrModel: String = "",
    val note: String = "",
) {
    fun base() = baseUrl.trim().trimEnd('/')
    fun has(cap: String) = cap in caps

    /** 导出：不含任何 Key（Key 不在这个对象里）。 */
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("style", style).put("baseUrl", baseUrl)
        .put("authHeader", authHeader).put("authPrefix", authPrefix).put("headers", JSONObject(headers as Map<*, *>))
        .put("models", JSONArray(models)).put("defaultModel", defaultModel).put("caps", JSONArray(caps.sorted()))
        .put("proxy", proxy).put("upstream", upstream).put("chatPath", chatPath).put("modelsPath", modelsPath)
        .put("asrPath", asrPath).put("asrModel", asrModel).put("note", note)

    companion object {
        val STYLES = setOf("openai", "anthropic", "gemini")
        val CAPS = listOf("chat", "vision", "image", "asr", "embeddings")
        /** 导入时会被丢掉的「像密钥」的字段（防止有人把 Key 写进配置分享出去）。 */
        private val SECRET_KEYS = setOf("key", "apikey", "api_key", "apiKey", "token", "secret", "authorization")

        fun parse(o: JSONObject): ProviderConfig {
            val base = o.optString("baseUrl", o.optString("base_url", o.optString("base"))).trim()
            require(base.startsWith("https://") || base.startsWith("http://")) { "baseUrl 需要 http(s):// 开头" }
            val style = o.optString("style", guessStyle(base)).lowercase().let { if (it in STYLES) it else "openai" }
            val name = o.optString("name").ifBlank { runCatching { java.net.URI(base).host }.getOrNull() ?: "自定义" }
            val id = o.optString("id").ifBlank { slug(name) }
            val hdr = o.optJSONObject("headers")?.let { h -> h.keys().asSequence().filter { it.lowercase() !in SECRET_KEYS.map(String::lowercase) && it.lowercase() != "authorization" }
                .associateWith { h.optString(it) } }.orEmpty()
            val defAuth = when (style) { "anthropic" -> "x-api-key"; "gemini" -> "x-goog-api-key"; else -> "Authorization" }
            val authHeader = o.optString("authHeader", defAuth).ifBlank { defAuth }
            val authPrefix = if (o.has("authPrefix")) o.optString("authPrefix") else if (authHeader.equals("Authorization", true)) "Bearer " else ""
            val caps = o.optJSONArray("caps")?.strings()?.map { it.lowercase() }?.filter { it in CAPS }?.toSet()?.ifEmpty { null } ?: setOf("chat")
            return ProviderConfig(id, name, style, base, authHeader, authPrefix, hdr, o.optJSONArray("models")?.strings().orEmpty(), o.optString("defaultModel"),
                caps, o.optString("proxy"), o.optString("upstream"), o.optString("chatPath"), o.optString("modelsPath"), o.optString("asrPath"), o.optString("asrModel"),
                o.optString("note"))
        }

        /** 导入：一个对象、数组、或 {providers:[…]}。坏条目跳过。 */
        fun parseMany(json: String): List<ProviderConfig> {
            val t = json.trim()
            val arr = when {
                t.startsWith("[") -> JSONArray(t)
                else -> JSONObject(t).let { o -> o.optJSONArray("providers") ?: JSONArray().put(o) }
            }
            return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { runCatching { parse(it) }.getOrNull() } }
        }

        fun exportMany(list: List<ProviderConfig>): String = JSONObject().put("format", "xuezhang-providers").put("version", 1)
            .put("providers", JSONArray(list.map { it.toJson() })).toString(2)

        fun guessStyle(base: String): String = when {
            base.contains("anthropic.com") -> "anthropic"
            base.contains("generativelanguage.googleapis.com") && !base.contains("/openai") -> "gemini"
            else -> "openai"
        }

        fun slug(s: String) = s.lowercase().map { if (it.isLetterOrDigit() && it.code < 128) it else '-' }.joinToString("").trim('-').replace(Regex("-+"), "-").ifBlank { "custom" }.take(32)
    }
}

/** 一个要发出的 HTTP 请求（纯数据，由 Api 层交给 OkHttp）。 */
data class HttpCall(val method: String, val url: String, val headers: Map<String, String>, val body: String? = null)

object ProviderRequests {
    private fun auth(c: ProviderConfig, key: String, url: String): Pair<Map<String, String>, String> {
        val h = LinkedHashMap<String, String>()
        var u = url
        if (key.isNotBlank()) {
            if (c.authHeader.startsWith("query:")) {
                val p = c.authHeader.removePrefix("query:").ifBlank { "key" }
                u += (if ('?' in u) "&" else "?") + p + "=" + java.net.URLEncoder.encode(key, "UTF-8")
            } else h[c.authHeader] = c.authPrefix + key
        }
        if (c.style == "anthropic") h.putIfAbsent("anthropic-version", "2023-06-01")
        h.putAll(c.headers)
        h["Content-Type"] = "application/json"
        return h to u
    }

    /** 对话请求：system + user（可带 JPEG base64 图）。 */
    fun chat(c: ProviderConfig, key: String, model: String, system: String, user: String, images: List<String> = emptyList(),
             json: Boolean = false, temperature: Double = 0.3, stream: Boolean = false): HttpCall {
        val m = model.ifBlank { c.defaultModel.ifBlank { c.models.firstOrNull().orEmpty() } }
        return when (c.style) {
            "anthropic" -> {
                val content = JSONArray()
                images.forEach { content.put(JSONObject().put("type", "image").put("source", JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", it))) }
                content.put(JSONObject().put("type", "text").put("text", user))
                val o = JSONObject().put("model", m).put("max_tokens", 8192).put("temperature", temperature)
                    .put("system", system + if (json) "\n\n只输出一个 JSON，不要任何解释或代码块。" else "")
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
                if (stream) o.put("stream", true)
                val (h, u) = auth(c, key, c.base() + c.chatPath.ifBlank { "/messages" })
                HttpCall("POST", u, h, o.toString())
            }
            "gemini" -> {
                val parts = JSONArray()
                images.forEach { parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", it))) }
                parts.put(JSONObject().put("text", user))
                val gc = JSONObject().put("temperature", temperature).apply { if (json) put("responseMimeType", "application/json") }
                val o = JSONObject().put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                    .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts))).put("generationConfig", gc)
                val path = c.chatPath.ifBlank { "/models/{model}:" + if (stream) "streamGenerateContent?alt=sse" else "generateContent" }.replace("{model}", m)
                val (h, u) = auth(c, key, c.base() + path)
                HttpCall("POST", u, h, o.toString())
            }
            else -> {
                val content: Any = if (images.isEmpty()) user else JSONArray().also { a ->
                    images.forEach { a.put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$it"))) }
                    a.put(JSONObject().put("type", "text").put("text", user))
                }
                val o = JSONObject().put("model", m).put("temperature", temperature)
                    .put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", system)).put(JSONObject().put("role", "user").put("content", content)))
                if (json) o.put("response_format", JSONObject().put("type", "json_object"))
                if (stream) o.put("stream", true)
                val (h, u) = auth(c, key, c.base() + c.chatPath.ifBlank { "/chat/completions" })
                HttpCall("POST", u, h, o.toString())
            }
        }
    }

    fun models(c: ProviderConfig, key: String): HttpCall {
        val path = c.modelsPath.ifBlank { if (c.style == "anthropic") "/models?limit=100" else "/models" }
        val (h, u) = auth(c, key, c.base() + path)
        return HttpCall("GET", u, h - "Content-Type")
    }

    /** 解析回答文本。 */
    fun parseChat(c: ProviderConfig, body: String): String {
        val o = JSONObject(body)
        return when (c.style) {
            "anthropic" -> o.optJSONArray("content")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.takeIf { b -> b.optString("type") == "text" }?.optString("text") }.joinToString("") }.orEmpty()
            "gemini" -> o.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.let { a ->
                (0 until a.length()).joinToString("") { a.optJSONObject(it)?.optString("text").orEmpty() } }.orEmpty()
            else -> o.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.let { m ->
                m.optString("content").takeIf { it.isNotBlank() && it != "null" } ?: m.optString("reasoning_content") }.orEmpty()
        }
    }

    /** 流式增量（SSE 每行 data 的 JSON）→ 本次新增文本。 */
    fun parseDelta(c: ProviderConfig, j: JSONObject): String = when (c.style) {
        "anthropic" -> if (j.optString("type") == "content_block_delta") j.optJSONObject("delta")?.optString("text").orEmpty() else ""
        "gemini" -> parseChat(c, j.toString())
        else -> j.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content").orEmpty().let { if (it == "null") "" else it }
    }

    /** /models 返回 → 模型 id 列表（OpenAI data[].id、Anthropic data[].id、Gemini models[].name）。 */
    fun parseModels(body: String): List<String> {
        val o = JSONObject(body)
        val a = o.optJSONArray("data") ?: o.optJSONArray("models") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            a.optJSONObject(i)?.let { m -> m.optString("id").ifBlank { m.optString("name").removePrefix("models/") }.takeIf { it.isNotBlank() } }
                ?: a.optString(i, null)
        }.distinct()
    }
}
