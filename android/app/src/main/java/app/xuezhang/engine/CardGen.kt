package app.xuezhang.engine

import app.xuezhang.data.Card
import app.xuezhang.data.Mnemonic
import app.xuezhang.data.Node
import app.xuezhang.data.NoteSection
import org.json.JSONArray
import org.json.JSONObject

/**
 * 复习卡兜底：短课/模型没给 cards 时出现「0 张卡」。
 * 1) 宽松解析（q/question/front/问题…）；2) 不够 MIN 张时再单独请求一次模型；3) 仍不够就从助记、概念、小节本地生成。
 */
object CardGen {
    const val MIN = 3

    const val PROMPT = "你是大学课程助教。根据给出的课堂笔记，出 4~8 张复习卡，覆盖最重要的概念、公式和易错点。" +
        "只输出 JSON：{\"cards\":[{\"q\":\"问题\",\"a\":\"答案（可含 LaTeX，用 $ 包裹）\",\"tag\":\"考点|概念|易错|公式\"}]}，不要输出其他文字。"

    private fun JSONObject.pick(vararg k: String): String = k.firstNotNullOfOrNull { key -> optString(key).takeIf { it.isNotBlank() && it != "null" } } ?: ""

    fun fromJson(a: JSONArray?): List<Card> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            when (val e = a.opt(i)) {
                is JSONObject -> {
                    val q = e.pick("q", "question", "front", "问题", "题目", "Q")
                    val ans = e.pick("a", "answer", "back", "答案", "解答", "A")
                    if (q.isBlank() || ans.isBlank()) null else Card(q, ans, e.pick("tag", "type", "标签").ifBlank { "考点" })
                }
                else -> null
            }
        }
    }

    /** 从任意模型回复里找卡片数组：{cards:[…]} / {flashcards:[…]} / 顶层数组。 */
    fun parseReply(text: String): List<Card> {
        JsonLoose.obj(text)?.let { o ->
            for (k in listOf("cards", "flashcards", "复习卡", "items", "data")) o.optJSONArray(k)?.let { return fromJson(it) }
        }
        val t = text.trim()
        val s = t.indexOf('['); val e = t.lastIndexOf(']')
        if (s >= 0 && e > s) runCatching { return fromJson(JSONArray(t.substring(s, e + 1))) }
        return emptyList()
    }

    private fun clean(s: String, max: Int = 140): String = s.replace(Regex("```[\\s\\S]*?```"), "").replace(Regex("\\[[a-z]:([^\\]]*)]"), "$1")
        .replace(Regex("\\*\\*"), "").replace(Regex("\\s+"), " ").trim().let { if (it.length > max) it.take(max) + "…" else it }

    /** 本地兜底：不调用模型，保证至少有几张卡。 */
    fun local(sections: List<NoteSection>, mnemonics: List<Mnemonic>, nodes: List<Node>, existing: List<Card> = emptyList(), min: Int = MIN): List<Card> {
        val out = existing.toMutableList()
        fun add(c: Card) { if (out.none { it.q == c.q } && c.q.isNotBlank() && c.a.isNotBlank()) out.add(c) }
        mnemonics.forEach { add(Card("「${it.title}」说的是什么？", clean(it.body), "助记")) }
        nodes.filter { it.detail.isNotBlank() }.forEach { add(Card("${it.label} 是什么？", clean(it.detail), "概念")) }
        sections.filter { it.title.isNotBlank() && clean(it.body).length >= 8 }.forEach { add(Card("${it.title} 的要点是什么？", clean(it.body), "考点")) }
        if (out.size < min) nodes.filter { it.parent >= 0 }.forEach { n -> nodes.getOrNull(n.parent)?.let { p -> add(Card("「${n.label}」属于哪个概念？", p.label, "概念")) } }
        return if (out.size >= min || existing.size >= out.size) out else out
    }

    fun needMore(cards: List<Card>, min: Int = MIN) = cards.size < min
}
