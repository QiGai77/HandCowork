package app.xuezhang.engine.plugin

import app.xuezhang.data.Course
import app.xuezhang.data.Segment
import org.json.JSONObject

/**
 * v0.0.8.0 转写插件的通用钩子入口。核心（上课录音、上课页）只在这里问一句「有没有插件接管」，
 * 没有启用声明这些钩子的插件时全部返回 null / false，上课页走 v0.0.7.9 原来的流程和界面。
 *
 * 规则文件里的 hooks：
 * ```
 * "hooks": {
 *   "capture.vad":   {"minSec":3,"maxSec":30,"silenceMs":600},   // 按停顿切段（替代固定 chunkSec）
 *   "segment.refine": {"prompt":"segment.refined"},                // 用插件提示词输出结构化 JSON（分句 / 精炼 / 要点 / 提问）
 *   "voice.render":  "sentences",                                  // 原声：一句一行 + 时间 + 话题分段
 *   "senior.render": "bubbles",                                    // 学长：对话气泡（精炼稿 / 要点 / 提问 / 小结）
 *   "page.summary":  {"level":3,"prompt":"summary"}                 // 翻页时给上一页做小结
 * }
 * ```
 * 分句（split）与热词纠错（hotwords）是规则步骤，跟着插件的精炼强度走。
 */
object TranscriptHooks {
    private fun hooks(): JSONObject? = SkillRegistry.refine.value?.hooks?.takeIf { it.length() > 0 }
    fun level(): Int = SkillRegistry.refine.value?.level ?: 2

    fun vad(): EnergyVad? = EnergyVad.config(hooks()?.optJSONObject("capture.vad"))?.let { EnergyVad(it) }
    fun refineOn(): Boolean = hooks()?.has("segment.refine") == true
    fun voiceSentences(): Boolean = hooks()?.optString("voice.render") == "sentences"
    fun seniorBubbles(): Boolean = hooks()?.optString("senior.render") == "bubbles"
    /** 小结从哪一档强度起生效（默认 3 = 强）。 */
    fun summaryLevel(): Int? = hooks()?.optJSONObject("page.summary")?.optInt("level", 3)

    /** 本课热词：course（课程热词）、slides（课件标题 / 公式自动提取）、lexicon（插件词表）。 */
    fun ctx(c: Course): TextRules.Ctx {
        val r = SkillRegistry.refine.value ?: return TextRules.Ctx()
        return TextRules.Ctx(mapOf(
            "course" to c.hotwords.filter { it.isNotBlank() }.map { Hotwords.Term(it.trim()) },
            "slides" to Hotwords.fromSlides(c.slides.flatMap { listOf(it.title) + it.formulas }),
            "lexicon" to r.lexicon,
        ), r.pinyin)
    }

    private fun prompt(key: String, def: String): String {
        val r = SkillRegistry.refine.value
        return r?.prompts?.get(key)?.takeIf { it.isNotBlank() } ?: def
    }

    /** 调用大模型的函数（便于测试替换）。 */
    var chat: (String, String) -> JSONObject = { sys, user -> app.xuezhang.net.Api.chatJson(sys, user) }

    /**
     * 插件接管一段识别结果：清洗 → 热词纠错 → 分句（规则）→ 插件提示词出结构化 JSON → 组装 Segment。
     * 返回 null 表示整段是语气词 / 闲聊，丢弃。网络异常照常抛出，交给核心的断网重试队列。
     */
    fun process(c: Course, raw: String, startMs: Long, endMs: Long, page0: Int, recent: List<Segment>, markNext: Boolean): Segment? {
        val r = SkillRegistry.refine.value ?: return null
        val ctx = ctx(c)
        val cleaned = TextRules.run(r.program, raw, r.level, ctx)
        if (cleaned.text.isBlank()) return null
        val split = r.program.steps.filterIsInstance<TextRules.Split>().firstOrNull()
        val sents0 = cleaned.sentences.ifEmpty { listOf(cleaned.text) }
        val local = sents0.zip(Splitter.timestamps(sents0, startMs, endMs))
        val ctxText = recent.takeLast(3).joinToString("\n") { it.voice }
        val slides = c.slides.joinToString("\n") { "P.${it.page} ${it.title} ${it.formulas.joinToString(" ")}" }.take(2500)
        val user = buildString {
            append("课程：${c.name}\n热词：${(ctx.terms["course"].orEmpty() + ctx.terms["lexicon"].orEmpty()).joinToString("、") { it.term }.take(600)}\n")
            append("精炼强度：${listOf("轻", "中", "强")[r.level - 1]}\n")
            if (slides.isNotBlank()) append("PPT 页：\n$slides\n当前大约在 P.$page0\n")
            if (ctxText.isNotBlank()) append("前文（已整理）：\n$ctxText\n")
            if (cleaned.exam.isNotEmpty()) append("老师的考点信号：" + cleaned.exam.joinToString(" / ") + "\n")
            append("\n本段已清洗、纠错、分好句的原话（序号从 0 开始，方括号里是时间）：\n")
            local.forEachIndexed { i, (t, ms) -> append("$i [${app.xuezhang.engine.Live.fmt(ms)}] ${Hotwords.strip(t)}\n") }
        }
        val sys = prompt("segment.refined", DEFAULT_PROMPT) + SkillRegistry.append("segment")
        val o = chat(sys, user)
        val out = RefinedReply.parse(o, local, startMs, endMs) { s ->
            val sp = split ?: TextRules.Split(1)
            Splitter.split(Hotwords.correct(s, ctx.terms.values.flatten(), ctx.pinyin), sp.breakers, sp.soft, sp.maxLen, sp.minLen)
        }
        val page = out.page.takeIf { it > 0 } ?: page0
        val exam = out.exam || markNext || cleaned.exam.isNotEmpty()
        // 翻页小结：上一页讲完了，把它的要点汇总一次（强度够才做；失败不影响这一段）
        val prevPage = recent.lastOrNull { it.page > 0 }?.page ?: 0
        var ext = out.ext
        val sl = summaryLevel()
        if (sl != null && r.level >= sl && prevPage > 0 && page > 0 && page != prevPage) {
            val prev = recent.filter { it.page == prevPage }.mapNotNull { TranscriptExt.parse(it.ext) }
            val material = prev.flatMap { e -> e.points.map { "${it.type}：${it.text}" } + listOfNotNull(e.refined.takeIf { it.isNotBlank() }) }
            if (material.isNotEmpty()) runCatching {
                val so = chat(prompt("summary", SUMMARY_PROMPT), "课程：${c.name}\nP.$prevPage 的要点：\n" + material.joinToString("\n").take(3000))
                so.optString("summary").trim().takeIf { it.isNotBlank() }?.let { ext = ext.copy(summary = TranscriptExt.Summary(prevPage, it)) }
            }
        }
        return Segment(startMs = startMs, page = page, raw = raw, voice = out.voice,
            senior = out.senior + ext.points.takeIf { it.isNotEmpty() && out.senior.isNotBlank() }?.joinToString("") { "\n· ${it.type}：${it.text}" }.orEmpty(),
            fillers = cleaned.fillers, repeats = cleaned.repeats, exam = exam, offTopic = out.offTopic && !exam, ext = ext.toJson())
    }

    /** 插件没带提示词时的兜底（正常由插件 prompts/segment.md 提供）。 */
    const val DEFAULT_PROMPT = """你是大学课堂实时整理助手「课堂学长」。输入是一段已分好句的老师原话。只输出 JSON：
{"sentences":["如果输入只有一句且没有标点，在这里给出加好标点、分好的句子；否则留空数组"],"breaks":[话题换段处的句子序号],
"refined":"去掉重复与闲聊后的精炼稿，原文 40%~60%","points":[{"type":"考点|结论|定义|公式|提问|例题","text":"一句话"}],
"question":{"q":"老师提的问题（没有则空字符串）","a":"答案","ts":"mm:ss"},"ratio":精炼稿占原文百分比,"page":页码,"exam":true/false,"relevant":true/false}"""
    const val SUMMARY_PROMPT = """把这一页课件的要点汇总成一句不超过 30 字的「本页小结」，学长口吻。只输出 JSON：{"summary":"…"}"""
}
