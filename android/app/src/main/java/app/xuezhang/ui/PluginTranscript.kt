package app.xuezhang.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xuezhang.data.Segment
import app.xuezhang.data.Store
import app.xuezhang.engine.Live
import app.xuezhang.engine.plugin.Hotwords
import app.xuezhang.engine.plugin.SkillRegistry
import app.xuezhang.engine.plugin.TranscriptExt
import app.xuezhang.engine.plugin.TranscriptHooks
import com.kyant.shapes.Capsule

/**
 * v0.0.8.0 转写插件的渲染钩子 voice.render=sentences / senior.render=bubbles。
 * 只有启用了声明这些钩子的插件、且这一段有插件数据（Segment.ext）时才接管；否则返回 false，由原界面渲染。
 */
object PluginTranscript {
    val QBg = Color(0xFFEFF5FF)
    val SumBg = Color(0xFFF5F1FF)
    val Lav = Color(0xFF7A4FD8)
    /** 要点类型 → (文字色, 底色)。 */
    fun typeColors(t: String): Pair<Color, Color> = when (t) {
        "考点" -> Color(0xFFD93A2B) to Color(0xFFFDE3DF)
        "结论" -> Color(0xFFE07A10) to Color(0xFFFDEBD5)
        "定义" -> Color(0xFF23955A) to Color(0xFFDDF3E6)
        "公式" -> Color(0xFF7A4FD8) to Color(0xFFECE5FB)
        "提问" -> Color(0xFF0A84FF) to Color(0xFFE3F0FF)
        else -> Color(0xFF0E8C8C) to Color(0xFFDAF2F2)
    }

    /** 点击纠错词还原：改这一段的 ext 和 voice，上课中改 Live.segments，回看时改课程记录。 */
    fun restore(segId: String, sentIdx: Int, from: String) {
        fun fix(s: Segment): Segment {
            val e = TranscriptExt.parse(s.ext) ?: return s
            val sents = e.sentences.mapIndexed { i, x -> if (i == sentIdx) x.copy(text = Hotwords.restore(x.text, from)) else x }
            val ne = e.copy(sentences = sents)
            return s.copy(ext = ne.toJson(), voice = ne.plain())
        }
        val i = Live.segments.indexOfFirst { it.id == segId }
        if (i >= 0) Live.segments[i] = fix(Live.segments[i])
        Store.lectures.firstOrNull { l -> l.segments.any { it.id == segId } }?.let { l ->
            Store.upsertLecture(l.copy(segments = l.segments.map { if (it.id == segId) fix(it) else it }))
        }
    }

    fun alnumTerms(): Set<String> = SkillRegistry.refine.value?.lexicon?.map { it.term }?.filter { t -> t.length >= 3 && t.any { it.isDigit() } && t.all { it.isLetterOrDigit() && it.code < 128 } }?.toSet().orEmpty()

    fun range(e: TranscriptExt, s: Segment): String {
        val end = e.endMs.takeIf { it > s.startMs } ?: e.sentences.lastOrNull()?.ms ?: s.startMs
        return Live.fmt(s.startMs) + " – " + Live.fmt(end)
    }
}

/** 原声：一句一行、左侧时间、同话题成段；老师提问淡蓝底；热词纠错蓝色虚线 + 上标「原:xx」，点一下还原。 */
@Composable
fun pluginVoiceCard(s: Segment, teacher: String): Boolean {
    if (!TranscriptHooks.voiceSentences()) return false
    val e = remember(s.ext) { TranscriptExt.parse(s.ext) } ?: return false
    if (e.sentences.isEmpty()) return false
    if (s.offTopic) { OffTopicRow(s); return true }
    val terms = remember(SkillRegistry.refine.value) { PluginTranscript.alnumTerms() }
    PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), radius = 20.dp, padding = PaddingValues(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 10.dp)) {
        Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(PluginTranscript.range(e, s), style = T.Cap.copy(fontSize = 13.sp))
            if (e.sentences.any { it.question } || e.question != null) { Spacer(Modifier.width(8.dp)); Tag("提问", C.Blue) }
            if (s.exam) { Spacer(Modifier.width(6.dp)); Tag("考点", C.HlRed) }
            if (s.page > 0) { Spacer(Modifier.width(6.dp)); Tag("P.${s.page}", C.Sec) }
            Spacer(Modifier.weight(1f))
            BasicText(teacher, style = T.Cap.copy(fontSize = 13.sp))
        }
        Spacer(Modifier.height(6.dp))
        var lastPara = e.sentences.firstOrNull()?.para ?: 0
        e.sentences.forEachIndexed { i, st ->
            if (st.para != lastPara) { Spacer(Modifier.height(12.dp)); lastPara = st.para }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (st.question) PluginTranscript.QBg else Color.Transparent)
                .padding(start = 4.dp, end = 4.dp, top = 5.dp, bottom = 5.dp)) {
                BasicText(Live.fmt(st.ms), Modifier.width(44.dp).padding(top = 3.dp), style = T.Cap.copy(fontSize = 12.sp, color = C.Sec))
                SentenceText(st.text, terms, Modifier.weight(1f)) { from -> PluginTranscript.restore(s.id, i, from) }
            }
        }
    }
    return true
}

private data class HotSpan(val start: Int, val end: Int)

/** 一句话：[h:正确|原词] 纠错标记、[r:]/[b:]/[o:]/[u:]/[f:] 标注、$公式$、热词蓝色。 */
@Composable
fun SentenceText(src: String, terms: Set<String>, modifier: Modifier = Modifier, style: TextStyle = T.Body.copy(fontSize = 16.sp, lineHeight = 26.sp), onRestore: (String) -> Unit) {
    val d = LocalDensity.current
    val fontPx = with(d) { style.fontSize.toPx() }
    val built = remember(src, terms, fontPx) {
        val hots = mutableListOf<HotSpan>()
        val reds = mutableListOf<HotSpan>()
        val inline = mutableMapOf<String, InlineTextContent>()
        var mi = 0
        val ann = buildAnnotatedString {
            fun plain(t: String, mk: Char?) {
                val color = when (mk) { 'r' -> C.HlRed; 'b', 'u' -> C.Blue; 'o' -> C.Orange; 'f' -> C.Sec; else -> style.color }
                val bold = mk == 'r' || mk == 'b'
                if (mk == null && terms.isNotEmpty()) {
                    // 热词（芯片型号等）蓝色
                    var last = 0
                    Regex("[A-Za-z0-9]{3,}").findAll(t).filter { it.value in terms }.forEach { m ->
                        append(t.substring(last, m.range.first))
                        pushStyle(SpanStyle(color = C.Blue, fontWeight = FontWeight.SemiBold)); append(m.value); pop()
                        last = m.range.last + 1
                    }
                    append(t.substring(last))
                } else {
                    val st0 = length
                    pushStyle(SpanStyle(color = color, fontWeight = if (bold) FontWeight.SemiBold else null)); append(t); pop()
                    if (mk == 'r') reds.add(HotSpan(st0, length))
                }
            }
            var i = 0; var mk: Char? = null; val sb = StringBuilder()
            fun flush() { if (sb.isNotEmpty()) { plain(sb.toString(), mk); sb.clear() } }
            while (i < src.length) {
                val c = src[i]
                if (src.startsWith("[h:", i)) {
                    val bar = src.indexOf('|', i); val end = src.indexOf(']', i)
                    if (bar in (i + 3) until end) {
                        flush()
                        val to = src.substring(i + 3, bar); val from = src.substring(bar + 1, end)
                        val st0 = length
                        pushStringAnnotation("hot", from)
                        pushStyle(SpanStyle(color = C.Blue, fontWeight = FontWeight.SemiBold)); append(to); pop()
                        val toEnd = length
                        pushStyle(SpanStyle(color = C.Sec, fontSize = 9.5.sp, baselineShift = BaselineShift(0.55f), fontWeight = FontWeight.Normal)); append("原:$from"); pop()
                        pop()
                        hots.add(HotSpan(st0, toEnd))
                        i = end + 1; continue
                    }
                }
                if (c == '[' && i + 2 < src.length && src[i + 2] == ':' && src[i + 1] in "broufBROUF" && mk == null) { flush(); mk = src[i + 1].lowercaseChar(); i += 3; continue }
                if (c == ']' && mk != null) { flush(); mk = null; i++; continue }
                if (c == '$') {
                    val end = src.indexOf('$', i + 1)
                    if (end > i + 1) {
                        flush()
                        val tex = src.substring(i + 1, end)
                        val bmp = app.xuezhang.ui.TeX.bitmap(tex, fontPx * 1.05f, style.color.toArgb())
                        if (bmp != null) {
                            val key = "m${mi++}"
                            inline[key] = InlineTextContent(Placeholder(with(d) { (bmp.width + 4).toSp() }, with(d) { bmp.height.toSp() }, PlaceholderVerticalAlign.TextCenter)) {
                                Image(bmp.asImageBitmap(), tex, Modifier.padding(horizontal = 2.dp))
                            }
                            appendInlineContent(key, tex)
                        } else { pushStyle(SpanStyle(fontFamily = FontFamily.Monospace)); append(tex); pop() }
                        i = end + 1; continue
                    }
                }
                sb.append(c); i++
            }
            flush()
        }
        Triple(ann, inline.toMap(), hots to reds)
    }
    val (ann, inline, spans) = built
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    BasicText(ann, modifier.drawBehind {
        val l = layout ?: return@drawBehind
        fun each(sp: HotSpan, f: (Float, Float, Int) -> Unit) {
            if (sp.end <= sp.start || sp.end > ann.length) return
            for (line in l.getLineForOffset(sp.start)..l.getLineForOffset(sp.end - 1)) {
                val a = maxOf(sp.start, l.getLineStart(line)); val b = minOf(sp.end, l.getLineEnd(line, true))
                if (b <= a) continue
                f(l.getHorizontalPosition(a, true), if (b >= l.getLineEnd(line, true)) l.getLineRight(line) else l.getHorizontalPosition(b, true), line)
            }
        }
        spans.second.forEach { sp -> each(sp) { x0, x1, line ->
            val top = l.getLineTop(line); val h = l.getLineBottom(line) - top
            drawRoundRect(C.HlRedBg, Offset(x0 - 2.dp.toPx(), top + h * 0.1f), Size(x1 - x0 + 4.dp.toPx(), h * 0.8f), CornerRadius(5.dp.toPx()))
        } }
        spans.first.forEach { sp -> each(sp) { x0, x1, line ->
            val y = l.getLineBaseline(line) + 4.dp.toPx()
            drawLine(C.Blue, Offset(x0, y), Offset(x1, y), 1.4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.5.dp.toPx())))
        } }
    }.pointerInput(ann) {
        detectTapGestures { pos ->
            val l = layout ?: return@detectTapGestures
            val off = l.getOffsetForPosition(pos)
            ann.getStringAnnotations("hot", (off - 1).coerceAtLeast(0), off + 1).firstOrNull()?.let { onRestore(it.item) }
        }
    }, style = style, inlineContent = inline, onTextLayout = { layout = it })
}

/** 学长：对话气泡。轻 = 交回原气泡；中 = 精炼稿 + 老师提问；强 = 再加要点和本页小结。 */
@Composable
fun pluginSeniorBubbles(s: Segment): Boolean {
    if (!TranscriptHooks.seniorBubbles()) return false
    val e = remember(s.ext) { TranscriptExt.parse(s.ext) } ?: return false
    val lv = TranscriptHooks.level()
    if (lv <= 1 || (e.refined.isBlank() && e.points.isEmpty() && e.question == null)) return false
    Column {
        if (lv >= 3) e.summary?.let { sm ->
            Bubble("结", PluginTranscript.Lav, PluginTranscript.SumBg) {
                BasicText("本页小结 · P.${sm.page}", style = T.Label.copy(color = PluginTranscript.Lav, fontWeight = FontWeight.Bold, fontSize = 14.sp))
                Spacer(Modifier.height(6.dp))
                RichText(sm.text)
            }
        }
        if (e.refined.isNotBlank()) Bubble("学", C.Blue, null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(Live.fmt(s.startMs) + (if (s.page > 0) "  ·  P.${s.page}" else ""), style = T.Cap.copy(fontSize = 13.sp))
                if (s.exam) { Spacer(Modifier.width(8.dp)); Tag("考点", C.HlRed) }
                Spacer(Modifier.weight(1f))
                if (e.ratio > 0) BasicText("原文 ${e.ratio}%", style = T.Cap.copy(fontSize = 13.sp))
            }
            Spacer(Modifier.height(6.dp))
            RefinedText(e.refined) { Nav.sheet.value = Sheet.Formula(it, s.voice) }
        }
        if (lv >= 3 && e.points.isNotEmpty()) Bubble("学", C.Blue, null) {
            BasicText("这段的要点", style = T.Label.copy(color = C.Sec, fontWeight = FontWeight.Bold, fontSize = 14.sp))
            Spacer(Modifier.height(8.dp))
            e.points.forEach { p ->
                val (fg, bg) = PluginTranscript.typeColors(p.type)
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                    BasicText(p.type, Modifier.padding(top = 2.dp).clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp),
                        style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = fg))
                    Spacer(Modifier.width(10.dp))
                    RichText(p.text, Modifier.weight(1f), onMath = { Nav.sheet.value = Sheet.Formula(it, s.voice) })
                }
            }
        }
        e.question?.let { q ->
            Bubble("问", C.Blue, PluginTranscript.QBg) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText("老师提问", Modifier.clip(Capsule()).background(Color(0xFFDCEBFF)).padding(horizontal = 9.dp, vertical = 2.dp),
                        style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = C.Blue))
                    Spacer(Modifier.width(8.dp))
                    BasicText(Live.fmt(q.ms), style = T.Cap.copy(fontSize = 13.sp))
                }
                Spacer(Modifier.height(6.dp))
                RichText(q.q)
                if (q.a.isNotBlank()) RichText("**答：${q.a}**", style = T.Body.copy(color = Color(0xFF23955A)))
            }
        }
    }
    return true
}

@Composable
private fun Bubble(avatar: String, color: Color, bg: Color?, content: @Composable () -> Unit) {
    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)) {
        Box(Modifier.size(34.dp).clip(Capsule()).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            BasicText(avatar, style = T.Head.copy(fontSize = 15.sp, color = color))
        }
        Spacer(Modifier.width(8.dp))
        PlainCard(Modifier.weight(1f), radius = 20.dp, padding = PaddingValues(0.dp)) {
            Column(Modifier.fillMaxWidth().then(if (bg != null) Modifier.background(bg) else Modifier).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)) { content() }
        }
    }
}

/** 精炼稿：普通段落用 RichText，``` 代码块（理工科学长插件的口述代码）用等宽灰底块。 */
@Composable
fun RefinedText(src: String, onMath: (String) -> Unit) {
    val parts = remember(src) { src.split("```") }
    Column {
        parts.forEachIndexed { i, p ->
            if (i % 2 == 1) {
                val code = p.substringAfter('\n', p).trimEnd()
                BasicText(code, Modifier.padding(vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0x0F000000)).padding(10.dp),
                    style = T.Label.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp))
            } else if (p.isNotBlank()) RichText(p.trim('\n'), onMath = onMath)
        }
    }
}
