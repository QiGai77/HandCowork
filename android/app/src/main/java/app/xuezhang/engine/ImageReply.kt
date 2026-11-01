package app.xuezhang.engine

import org.json.JSONArray
import org.json.JSONObject

/**
 * 从 ChatGPT（Codex Responses 流）/ OpenAI 图片接口的回复里收集图片。
 * 兼容：output_item.done 里的 image_generation_call.result（b64 或 data URL），
 * partial_image 事件（最后一张部分图做兜底），response.completed 里 response.output[]，
 * 消息内容里的 output_image / image_url，文本里的 data:image/...;base64 或 markdown 图片链接。
 * 返回 "b64:..." 或 http(s) URL。
 */
class ImageReply {
    val images = LinkedHashSet<String>()
    var partial: String? = null
    val text = StringBuilder()
    private val raw = StringBuilder()

    fun event(type: String, j: JSONObject) {
        if (raw.length < 4000) raw.append(type).append(' ').append(strip(j.toString()).take(600)).append('\n')
        when {
            type.endsWith("partial_image") -> j.optString("partial_image_b64").ifBlank { j.optString("b64_json") }.takeIf { it.isNotBlank() }?.let { partial = "b64:$it" }
            type == "response.output_item.done" || type == "response.output_item.added" -> j.optJSONObject("item")?.let { item(it) }
            type == "response.completed" || type == "response.done" -> j.optJSONObject("response")?.optJSONArray("output")?.let { arr(it) }
            type == "response.output_text.delta" -> text.append(j.optString("delta"))
            type == "response.image_generation_call.completed" -> j.optString("result").takeIf { it.isNotBlank() }?.let { add(it) }
        }
    }

    private fun arr(a: JSONArray) { for (i in 0 until a.length()) a.optJSONObject(i)?.let { item(it) } }

    fun item(it: JSONObject) {
        when (it.optString("type")) {
            "image_generation_call" -> {
                listOf("result", "b64_json", "image", "url").firstNotNullOfOrNull { k -> it.optString(k).takeIf { v -> v.isNotBlank() && v != "null" } }?.let { add(it) }
            }
            "message" -> it.optJSONArray("content")?.let { c ->
                for (i in 0 until c.length()) {
                    val p = c.optJSONObject(i) ?: continue
                    when (p.optString("type")) {
                        "output_image", "image", "image_url" -> (p.opt("image_url") as? JSONObject)?.optString("url")?.let { add(it) }
                            ?: p.optString("image_url").ifBlank { p.optString("url").ifBlank { p.optString("b64_json") } }.takeIf { it.isNotBlank() }?.let { add(it) }
                        "output_text", "text" -> scanText(p.optString("text"))
                    }
                }
            }
        }
    }

    /** OpenAI 兼容 /images/generations 的 JSON：data[] / images[]，元素是 {url} / {b64_json} / 字符串。 */
    fun generations(j: JSONObject) {
        val a = j.optJSONArray("data") ?: j.optJSONArray("images") ?: j.optJSONArray("output") ?: JSONArray()
        for (i in 0 until a.length()) {
            when (val e = a.opt(i)) {
                is JSONObject -> e.optString("b64_json").ifBlank { e.optString("url").ifBlank { e.optString("image") } }.takeIf { it.isNotBlank() }?.let { add(it) }
                is String -> add(e)
            }
        }
    }

    fun scanText(t: String) {
        Regex("data:image/[a-zA-Z+]+;base64,([A-Za-z0-9+/=]{200,})").findAll(t).forEach { images.add("b64:" + it.groupValues[1]) }
        Regex("!\\[[^]]*]\\((https?://[^)\\s]+)\\)").findAll(t).forEach { images.add(it.groupValues[1]) }
    }

    private fun add(v0: String) {
        val v = v0.trim()
        when {
            v.startsWith("data:image") -> v.substringAfter("base64,", "").takeIf { it.isNotBlank() }?.let { images.add("b64:$it") }
            v.startsWith("http://") || v.startsWith("https://") -> images.add(v)
            v.startsWith("b64:") -> images.add(v)
            v.length > 200 && Regex("^[A-Za-z0-9+/=\\s]+$").matches(v.take(400)) -> images.add("b64:" + v.replace(Regex("\\s"), ""))
        }
    }

    /** 最终结果：完整图优先，没有就用最后一张部分图，再看文本里有没有。 */
    fun result(): List<String> {
        if (images.isEmpty()) scanText(text.toString())
        if (images.isEmpty()) partial?.let { return listOf(it) }
        return images.toList()
    }

    /** 给日志用的原始回复：把长 base64 打码，便于排查「没有返回图片」。 */
    fun rawForLog(): String = strip(raw.toString() + if (text.isNotBlank()) "\ntext: " + text.take(400) else "").take(2000)

    companion object {
        fun strip(s: String) = s.replace(Regex("[A-Za-z0-9+/=]{120,}")) { "<base64 ${it.value.length} 字符>" }
    }
}
