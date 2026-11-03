package app.xuezhang.engine.plugin

import org.json.JSONArray
import org.json.JSONObject

/**
 * v0.0.8.0 插件写在 Segment.ext 里的结构化转写（纯 Kotlin + org.json）。
 * 旧课（ext 为空）或插件关闭时，界面照旧显示 Segment.voice / senior。
 */
data class TranscriptExt(
    val endMs: Long = 0,
    val sentences: List<Sent> = emptyList(),
    val refined: String = "",
    val points: List<Point> = emptyList(),
    val question: Question? = null,
    val ratio: Int = 0,
    /** 上一页的小结（课件翻页时生成，显示在这段前面）。 */
    val summary: Summary? = null,
) {
    data class Sent(val text: String, val ms: Long, val question: Boolean = false, val para: Int = 0)
    data class Point(val type: String, val text: String)
    data class Question(val q: String, val a: String, val ms: Long)
    data class Summary(val page: Int, val text: String)

    fun toJson(): String = JSONObject().put("v", 1).put("end", endMs)
        .put("sents", JSONArray().also { a -> sentences.forEach { a.put(JSONObject().put("t", it.text).put("ms", it.ms).put("q", it.question).put("p", it.para)) } })
        .put("refined", refined)
        .put("points", JSONArray().also { a -> points.forEach { a.put(JSONObject().put("type", it.type).put("text", it.text)) } })
        .apply { question?.let { put("question", JSONObject().put("q", it.q).put("a", it.a).put("ms", it.ms)) } }
        .put("ratio", ratio)
        .apply { summary?.let { put("summary", JSONObject().put("page", it.page).put("text", it.text)) } }
        .toString()

    /** 原声里显示的整段文字（去掉纠错标记）：给旧界面、笔记生成用。 */
    fun plain(): String = sentences.joinToString("") { Hotwords.strip(it.text) }

    companion object {
        val TYPES = listOf("考点", "结论", "定义", "公式", "提问", "例题")

        fun parse(s: String?): TranscriptExt? {
            if (s.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(s)
                TranscriptExt(o.optLong("end"),
                    o.optJSONArray("sents")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { Sent(it.optString("t"), it.optLong("ms"), it.optBoolean("q"), it.optInt("p")) } }.orEmpty(),
                    o.optString("refined"), points(o.opt("points")),
                    o.optJSONObject("question")?.let { q -> Question(q.optString("q"), q.optString("a"), q.optLong("ms")).takeIf { it.q.isNotBlank() } },
                    o.optInt("ratio"),
                    o.optJSONObject("summary")?.let { m -> Summary(m.optInt("page"), m.optString("text")).takeIf { it.text.isNotBlank() } })
            }.getOrNull()
        }

        /** points 兼容两种写法：新 [{type,text}]，旧 ["…"]（旧版提示词 / 模型偶尔只给字符串）。 */
        fun points(x: Any?): List<Point> {
            val a = x as? JSONArray ?: return emptyList()
            return (0 until a.length()).mapNotNull { i ->
                when (val e = a.opt(i)) {
                    is JSONObject -> e.optString("text").ifBlank { e.optString("t") }.takeIf { it.isNotBlank() }?.let { Point(normType(e.optString("type")), it) }
                    is String -> e.trim().removePrefix("·").trim().takeIf { it.isNotBlank() }?.let { t ->
                        // 「考点：xxx」这种前缀写法也认
                        val m = Regex("^(考点|结论|定义|公式|提问|例题)[：:]\\s*(.+)$").find(t)
                        if (m != null) Point(m.groupValues[1], m.groupValues[2]) else Point("结论", t)
                    }
                    else -> null
                }
            }
        }

        fun normType(t: String): String = t.trim().let { x -> TYPES.firstOrNull { x.contains(it) } ?: when (x.lowercase()) {
            "exam", "key" -> "考点"; "definition", "def" -> "定义"; "formula" -> "公式"; "question" -> "提问"; "example" -> "例题"; else -> "结论"
        } }

        /** "03:52" / "1:03:52" / 毫秒数 → 毫秒。 */
        fun parseTs(x: Any?): Long = when (x) {
            is Number -> x.toLong().let { if (it in 1..86_400) it * 1000 else it }
            is String -> x.trim().split(':').mapNotNull { it.toIntOrNull() }.let { p -> if (p.isEmpty()) 0L else p.fold(0L) { a, v -> a * 60 + v } * 1000 }
            else -> 0L
        }
    }
}

/**
 * 解析插件精炼提示词的 JSON 回复，兼容旧格式（voice / senior / points 字符串数组）。
 * sents：本地分好的句子（含 [h:] 纠错标记）与估算时间；模型只给段落分界、提问句等。
 */
object RefinedReply {
    data class Out(val ext: TranscriptExt, val voice: String, val senior: String, val page: Int, val exam: Boolean, val offTopic: Boolean)

    fun parse(o: JSONObject, local: List<Pair<String, Long>>, startMs: Long, endMs: Long, split: (String) -> List<String>): Out {
        // 本地没切出句子（识别结果没标点）→ 用模型给的 sentences，再按规则切一遍
        var sents = local
        val modelSents = o.optJSONArray("sentences")?.let { a -> (0 until a.length()).mapNotNull { i -> (a.opt(i) as? String) ?: (a.opt(i) as? JSONObject)?.optString("text") } }.orEmpty().filter { it.isNotBlank() }
        val len1 = local.sumOf { Splitter.visibleLen(it.first) }
        val longOne = local.size <= 1 && (len1 > 60 || (len1 >= 20 && local.none { s -> s.first.any { it in "。？！；?!;，," } }))
        if ((local.isEmpty() || longOne) && modelSents.isNotEmpty()) {
            val texts = modelSents.flatMap(split)
            sents = texts.zip(Splitter.timestamps(texts, startMs, endMs))
        }
        val breaks = o.optJSONArray("breaks")?.let { a -> (0 until a.length()).map { a.optInt(it, -1) } }.orEmpty().filter { it > 0 }.toSet()
        val qObj = o.optJSONObject("question")
        val qIdx = o.optInt("questionIndex", -1)
        var para = 0
        val list = sents.mapIndexed { i, (t, ms) ->
            if (i in breaks) para++
            val plain = Hotwords.strip(t)
            TranscriptExt.Sent(t, ms, question = i == qIdx || plain.trimEnd().let { it.endsWith("？") || it.endsWith("?") }, para = para)
        }
        val question = qObj?.let { q ->
            val text = q.optString("q").trim()
            if (text.isBlank()) null else {
                val ms = TranscriptExt.parseTs(q.opt("ts")).takeIf { it > 0 } ?: list.firstOrNull { it.question }?.ms ?: startMs
                TranscriptExt.Question(text, q.optString("a").trim(), ms)
            }
        }
        val refined = o.optString("refined").ifBlank { o.optString("senior") }.let { if (app.xuezhang.engine.plugin.RefinedReply.isSkip(it)) "" else it.trim() }
        val plainAll = list.joinToString("") { Hotwords.strip(it.text) }
        val ratio = o.optInt("ratio", 0).takeIf { it in 1..100 } ?: if (plainAll.isNotEmpty() && refined.isNotEmpty()) (refined.length * 100 / plainAll.length).coerceIn(1, 100) else 0
        val ext = TranscriptExt(endMs, list, refined, TranscriptExt.points(o.opt("points")), question, ratio)
        val exam = o.optBoolean("exam") || ext.points.any { it.type == "考点" }
        val off = isSkip(o.optString("refined").ifBlank { o.optString("senior") }) || o.opt("relevant").let { it == false || it?.toString()?.lowercase() == "false" }
        return Out(ext, plainAll.ifBlank { o.optString("voice") }, refined, o.optInt("page", 0), exam, off)
    }

    fun isSkip(s: String): Boolean = s.trim().trim('"', '「', '」', '。', '.').let { it.equals("SKIP", true) || it == "跳过" || it == "无关" }
}
