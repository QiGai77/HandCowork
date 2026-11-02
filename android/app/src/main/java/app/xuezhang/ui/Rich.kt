package app.xuezhang.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.noties.jlatexmath.JLatexMathDrawable

// ---------------- LaTeX bitmap rendering ----------------
object TeX {
    private val cache = LruCache<String, Bitmap?>(256)
    fun bitmap(latex: String, px: Float, color: Int): Bitmap? {
        val key = "$latex|$px|$color"
        cache.get(key)?.let { return it }
        val bmp = runCatching {
            val d = JLatexMathDrawable.builder(latex).textSize(px).color(color).padding(0).build()
            val w = d.intrinsicWidth.coerceAtLeast(1); val h = d.intrinsicHeight.coerceAtLeast(1)
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b -> d.setBounds(0, 0, w, h); d.draw(Canvas(b)) }
        }.getOrNull()
        if (bmp != null) cache.put(key, bmp)
        return bmp
    }
}

// ---------------- markup parsing ----------------
enum class Mk { B, R, O, U, F, BOLD }

private sealed class Inl {
    data class Txt(val s: String, val st: Set<Mk>) : Inl()
    data class M(val tex: String, val st: Set<Mk>) : Inl()
}

private fun findMatchingBracket(s: String, from: Int): Int {
    var depth = 1; var i = from; var inMath = false
    while (i < s.length) {
        val c = s[i]
        if (c == '\\' && i + 1 < s.length) { i += 2; continue }
        if (c == '$') inMath = !inMath
        else if (!inMath) {
            if (c == '[') depth++
            else if (c == ']') { depth--; if (depth == 0) return i }
        }
        i++
    }
    return -1
}

private fun parseInline(s: String, st: Set<Mk>, out: MutableList<Inl>) {
    val sb = StringBuilder()
    fun flush() { if (sb.isNotEmpty()) { out.add(Inl.Txt(sb.toString(), st)); sb.clear() } }
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '\\' && i + 1 < s.length && s[i + 1] == '$') { sb.append('$'); i += 2; continue }
        if (c == '$') {
            val dbl = s.startsWith("$$", i)
            val open = if (dbl) 2 else 1
            val end = s.indexOf(if (dbl) "$$" else "$", i + open)
            if (end > i + open) { flush(); out.add(Inl.M(s.substring(i + open, end).trim(), st)); i = end + open; continue }
        }
        if (s.startsWith("**", i)) {
            val end = s.indexOf("**", i + 2)
            if (end > i + 2) { flush(); parseInline(s.substring(i + 2, end), st + Mk.BOLD, out); i = end + 2; continue }
        }
        if (c == '[' && i + 2 < s.length && s[i + 2] == ':' && s[i + 1] in "broufBROUF") {
            val end = findMatchingBracket(s, i + 3)
            if (end > 0) {
                val mk = when (s[i + 1].lowercaseChar()) { 'b' -> Mk.B; 'r' -> Mk.R; 'o' -> Mk.O; 'u' -> Mk.U; else -> Mk.F }
                flush(); parseInline(s.substring(i + 3, end), st + mk, out); i = end + 1; continue
            }
        }
        sb.append(c); i++
    }
    flush()
}

sealed class Blk {
    data class P(val text: String) : Blk()
    data class Math(val tex: String) : Blk()
    object Gap : Blk()
}

fun splitBlocks(src: String): List<Blk> {
    // join newlines inside $$..$$
    val s = Regex("\\$\\$([\\s\\S]*?)\\$\\$").replace(src.replace("\\[", "$$").replace("\\]", "$$")
        .replace("\\(", "$").replace("\\)", "$")) { "$$" + it.groupValues[1].replace('\n', ' ') + "$$" }
    val out = mutableListOf<Blk>()
    for (line in s.split('\n')) {
        if (line.isBlank()) { if (out.isNotEmpty() && out.last() !is Blk.Gap) out.add(Blk.Gap); continue }
        var rest = line
        while (true) {
            val a = rest.indexOf("$$"); if (a < 0) break
            val b = rest.indexOf("$$", a + 2); if (b < 0) break
            val before = rest.substring(0, a)
            if (before.isNotBlank()) out.add(Blk.P(before.trimEnd()))
            out.add(Blk.Math(rest.substring(a + 2, b).trim()))
            rest = rest.substring(b + 2)
        }
        if (rest.isNotBlank()) out.add(Blk.P(rest.trim()))
    }
    while (out.lastOrNull() is Blk.Gap) out.removeAt(out.lastIndex)
    return out
}

// colors per mark
private fun fgFor(st: Set<Mk>, base: Color): Color = when {
    Mk.R in st -> C.HlRed; Mk.O in st -> C.HlOrange; Mk.B in st -> C.HlBlue; Mk.F in st -> C.FillGray; else -> base
}

private data class Deco(val start: Int, val end: Int, val mk: Mk)

@Composable
fun RichText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = T.Body,
    onMath: ((String) -> Unit)? = null,
    onFill: ((String) -> Unit)? = null,
) {
    val blocks = remember(text) { splitBlocks(text) }
    Column(modifier) {
        blocks.forEach { b ->
            when (b) {
                is Blk.Gap -> Spacer(Modifier.height(8.dp))
                is Blk.Math -> MathBlock(b.tex, style, onMath)
                is Blk.P -> RichPara(b.text, style, onMath, onFill)
            }
        }
    }
}

@Composable
fun MathBlock(tex: String, style: TextStyle = T.Body, onMath: ((String) -> Unit)? = null, scale: Float = 1.18f, color: Color = style.color) {
    val d = LocalDensity.current
    val px = with(d) { style.fontSize.toPx() } * scale
    val bmp = remember(tex, px, color) { TeX.bitmap(tex, px, color.toArgb()) }
    Box(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).horizontalScroll(rememberScrollState())
            .then(if (onMath != null) Modifier.clickable(interactionSource = null, indication = null) { onMath(tex) } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) Image(bmp.asImageBitmap(), tex, Modifier.size(with(d) { bmp.width.toDp() }, with(d) { bmp.height.toDp() }))
        else BasicText(tex, style = style.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = C.Sec))
    }
}

/** v0.0.7.8 翻书分页量高度时用：段落排版后把每行底部 y（相对段落块顶，px）交给分页器，好按行断页。 */
class LineSink { var lines: List<Float> = emptyList() }
val LocalLineSink = androidx.compose.runtime.staticCompositionLocalOf<LineSink?> { null }
/** v0.0.7.8 为 true 时只量尺寸：图片类组件不去加载/渲染位图。 */
val LocalMeasuring = androidx.compose.runtime.staticCompositionLocalOf { false }

@Composable
private fun RichPara(src: String, style: TextStyle, onMath: ((String) -> Unit)?, onFill: ((String) -> Unit)?) {
    val d = LocalDensity.current
    val fontPx = with(d) { style.fontSize.toPx() }
    val items = remember(src) { mutableListOf<Inl>().also { parseInline(src, emptySet(), it) } }
    val built = remember(src, style, fontPx) {
        val decos = mutableListOf<Deco>()
        val inline = mutableMapOf<String, InlineTextContent>()
        var mi = 0
        val ann = buildAnnotatedString {
            // merge consecutive same-mark ranges into decos
            items.forEach { it ->
                val st = when (it) { is Inl.Txt -> it.st; is Inl.M -> it.st }
                val start = length
                val fg = fgFor(st, style.color)
                when (it) {
                    is Inl.Txt -> {
                        val bold = Mk.BOLD in st || Mk.B in st || Mk.R in st || Mk.O in st
                        pushStyle(SpanStyle(color = fg, fontWeight = if (bold) FontWeight.SemiBold else null))
                        append(it.s); pop()
                    }
                    is Inl.M -> {
                        val key = "m${mi++}"
                        val texSrc = it.tex
                        val bmp = TeX.bitmap(it.tex, fontPx * 1.08f, fg.toArgb())
                        if (bmp != null) {
                            val w = with(d) { (bmp.width + 4).toSp() }
                            val h = with(d) { bmp.height.toSp() }
                            inline[key] = InlineTextContent(Placeholder(w, h, PlaceholderVerticalAlign.TextCenter)) {
                                Image(bmp.asImageBitmap(), texSrc, Modifier.padding(horizontal = 2.dp))
                            }
                            pushStringAnnotation("math", it.tex)
                            appendInlineContent(key, it.tex); pop()
                        } else {
                            pushStyle(SpanStyle(color = fg, fontFamily = FontFamily.Monospace)); append(it.tex); pop()
                        }
                    }
                }
                val end = length
                for (mk in listOf(Mk.B, Mk.R, Mk.O, Mk.U, Mk.F)) if (mk in st) {
                    val last = decos.lastOrNull()
                    if (last != null && last.mk == mk && last.end == start) decos[decos.lastIndex] = last.copy(end = end)
                    else decos.add(Deco(start, end, mk))
                }
                if (Mk.F in st) addStringAnnotation("fill", (it as? Inl.Txt)?.s ?: "", start, end)
            }
        }
        Triple(ann, inline.toMap(), decos.toList())
    }
    val (ann, inline, decos) = built
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val sink = LocalLineSink.current
    val padPx = with(d) { 1.dp.toPx() }
    BasicText(
        ann,
        style = style,
        inlineContent = inline,
        onTextLayout = { r -> layout = r; sink?.lines = (0 until r.lineCount).map { padPx + r.getLineBottom(it) } },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .drawBehind {
                val l = layout ?: return@drawBehind
                decos.forEach { dc -> drawDeco(l, dc, ann) }
            }
            .pointerInput(ann, onMath, onFill) {
                detectTapGestures { pos ->
                    val l = layout ?: return@detectTapGestures
                    val off = l.getOffsetForPosition(pos)
                    ann.getStringAnnotations("math", off, off).firstOrNull()?.let { onMath?.invoke(it.item); return@detectTapGestures }
                    ann.getStringAnnotations("fill", (off - 1).coerceAtLeast(0), off + 1).firstOrNull()?.let { onFill?.invoke(it.item) }
                }
            }
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDeco(l: TextLayoutResult, dc: Deco, ann: AnnotatedString) {
    if (dc.end <= dc.start || dc.start >= ann.length) return
    val end = dc.end.coerceAtMost(ann.length)
    val l0 = l.getLineForOffset(dc.start); val l1 = l.getLineForOffset((end - 1).coerceAtLeast(dc.start))
    val padX = 2.dp.toPx(); val r = 5.dp.toPx()
    for (line in l0..l1) {
        val s = maxOf(dc.start, l.getLineStart(line))
        val e = minOf(end, l.getLineEnd(line, visibleEnd = true))
        if (e <= s) continue
        val x0 = l.getHorizontalPosition(s, true)
        val x1 = if (e >= l.getLineEnd(line, true)) l.getLineRight(line) else l.getHorizontalPosition(e, true)
        val top = l.getLineTop(line); val bottom = l.getLineBottom(line)
        val h = bottom - top
        val bgTop = top + h * 0.12f; val bgBot = bottom - h * 0.10f
        when (dc.mk) {
            Mk.B -> drawRoundRect(C.HlBlueBg, Offset(x0 - padX, bgTop), Size(x1 - x0 + 2 * padX, bgBot - bgTop), CornerRadius(r))
            Mk.R -> drawRoundRect(C.HlRedBg, Offset(x0 - padX, bgTop), Size(x1 - x0 + 2 * padX, bgBot - bgTop), CornerRadius(r))
            Mk.O -> drawRoundRect(C.HlOrangeBg, Offset(x0 - padX, bgTop), Size(x1 - x0 + 2 * padX, bgBot - bgTop), CornerRadius(r))
            Mk.U -> {
                val y = l.getLineBaseline(line) + 3.5.dp.toPx()
                drawLine(C.HlBlue, Offset(x0, y), Offset(x1, y), 1.6.dp.toPx())
            }
            Mk.F -> {
                drawRoundRect(C.FillGray.copy(alpha = 0.13f), Offset(x0 - padX, bgTop), Size(x1 - x0 + 2 * padX, bgBot - bgTop), CornerRadius(r))
                val y = l.getLineBaseline(line) + 3.dp.toPx()
                drawLine(C.FillGray, Offset(x0, y), Offset(x1, y), 1.3.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5.dp.toPx(), 2.dp.toPx())))
            }
            else -> {}
        }
    }
}
