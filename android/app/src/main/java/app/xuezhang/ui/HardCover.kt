package app.xuezhang.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.drawText
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.xuezhang.R
import app.xuezhang.engine.HardCovers
import kotlin.math.ceil
import kotlin.math.min

// =================== v0.0.7.6 统一精装封面（预设图 + Compose 烫金标题 / 淡水印）===================

private val COVER_RES = intArrayOf(
    R.drawable.hc_cover_01, R.drawable.hc_cover_02, R.drawable.hc_cover_03, R.drawable.hc_cover_04, R.drawable.hc_cover_05,
    R.drawable.hc_cover_06, R.drawable.hc_cover_07, R.drawable.hc_cover_08, R.drawable.hc_cover_09, R.drawable.hc_cover_10,
)
private val SPINE_RES = intArrayOf(
    R.drawable.hc_spine_01, R.drawable.hc_spine_02, R.drawable.hc_spine_03, R.drawable.hc_spine_04, R.drawable.hc_spine_05,
    R.drawable.hc_spine_06, R.drawable.hc_spine_07, R.drawable.hc_spine_08, R.drawable.hc_spine_09, R.drawable.hc_spine_10,
)

fun hcCoverRes(hc: HardCovers.Hc) = COVER_RES[hc.index]
fun hcSpineRes(hc: HardCovers.Hc) = SPINE_RES[hc.index]
val HardCovers.Hc.color get() = Color(board)
val HardCovers.Hc.lightColor get() = Color(light)
val HardCovers.Hc.darkColor get() = Color(dark)

/** 烫金渐变（同 SVG #foil）。 */
object Foil {
    val stops = arrayOf(0f to Color(0xFFFFF3C4), 0.18f to Color(0xFFE2BC63), 0.38f to Color(0xFFB98A32), 0.5f to Color(0xFFFCEFC2),
        0.62f to Color(0xFFC99A42), 0.85f to Color(0xFF9C7226), 1f to Color(0xFFEBCB7C))
    fun brush(w: Float, h: Float, x: Float = 0f, y: Float = 0f) = Brush.linearGradient(*stops, start = Offset(x, y), end = Offset(x + w, y + h))
    val text = Brush.linearGradient(*stops)
    val gold = Color(0xFFD9B565)
}

/** 徽章图标（SVG 100×100 坐标系里的描边路径，取自 design/figma-v0.0.9/covers）。 */
private val ICON_PATHS = mapOf(
    "chip" to "M30 30h40v40H30z M38 38h24v24H38z M38 30V18 M46 30V18 M54 30V18 M62 30V18 M38 70v12 M46 70v12 M54 70v12 M62 70v12 M30 38H18 M30 46H18 M30 54H18 M30 62H18 M70 38h12 M70 46h12 M70 54h12 M70 62h12 M42 42l4 0",
    "wave" to "M10 50H90 M12 50C20 18 30 18 38 50S56 82 64 50 80 18 88 50 M25 50v-24 M51 50v24 M77 50v-24",
    "integral" to "M16 86H90 M18 88V12 M22 78C40 76 48 34 86 24 M44 86V60 M52 86V47 M60 86V38 M68 86V33 M14 16l4-4 4 4 M86 82l4 4-4 4",
    "globe" to "M50 14a36 36 0 1 1 0 72a36 36 0 1 1 0-72z M50 14c-20 18-20 54 0 72 M50 14c20 18 20 54 0 72 M14 50H86 M20 32H80 M20 68H80",
    "quill" to "M80 12C52 16 32 40 26 78 M80 12C78 40 58 62 30 72 M80 12C66 22 50 38 40 58 M50 30l-8 2 M60 34l-12 4 M66 42l-14 4 M64 52l-14 3 M26 78l-6 10 M20 88h30",
    "leaf" to "M50 10C82 28 84 68 50 90C16 68 18 28 50 10z M50 22V94 M50 40l-14-10 M50 40l14-10 M50 56l-18-12 M50 56l18-12 M50 72l-16-10 M50 72l16-10",
    "gear" to "M50 20 L55.94 12.47 L61.74 13.86 L63.62 23.27 L67.63 25.73 L76.87 23.13 L80.74 27.66 L76.73 36.38 L78.53 40.73 L87.53 44.06 L88 50 L79.63 54.69 L78.53 59.27 L83.86 67.25 L80.74 72.34 L71.21 71.21 L67.63 74.27 L67.25 83.86 L61.74 86.14 L54.69 79.63 L50 80 L44.06 87.53 L38.26 86.14 L36.38 76.73 L32.37 74.27 L23.13 76.87 L19.26 72.34 L23.27 63.62 L21.47 59.27 L12.47 55.94 L12 50 L20.37 45.31 L21.47 40.73 L16.14 32.75 L19.26 27.66 L28.79 28.79 L32.37 25.73 L32.75 16.14 L38.26 13.86 L45.31 20.37 Z M50 36a14 14 0 1 1 0 28a14 14 0 1 1 0-28z M50 45a5 5 0 1 1 0 10a5 5 0 1 1 0-10z",
    "flask" to "M40 14H60 M44 14V38L22 80Q19 87 27 87H73Q81 87 78 80L56 38V14 M30 64H70 M44 74a3 3 0 1 0 0.1 0 M56 70a2 2 0 1 0 0.1 0 M50 78a2.5 2.5 0 1 0 0.1 0",
    "code" to "M38 28L18 50L38 72 M62 28L82 50L62 72 M56 20L44 80",
)
private val parsedIcons = HashMap<String, Path>()
private fun iconPath(motif: String): Path? = ICON_PATHS[motif]?.let { d -> parsedIcons.getOrPut(motif) { PathParser().parsePathString(d).toPath() } }

/** 在 [topLeft] 处画 [side] 大小的烫金图标（带一点压印阴影）。 */
fun DrawScope.drawFoilIcon(motif: String, topLeft: Offset, side: Float, strokeScale: Float = 1f) {
    val k = side / 100f
    val sw = 2.6f * strokeScale
    fun body(brush: Brush) {
        translate(topLeft.x, topLeft.y) {
            scale(k, k, Offset.Zero) {
                if (motif == "atom") {
                    listOf(0f, 60f, 120f).forEach { a -> rotate(a, Offset(50f, 50f)) { drawOval(brush, Offset(10f, 36f), Size(80f, 28f), style = Stroke(sw)) } }
                    drawCircle(brush, 6f, Offset(50f, 50f))
                    rotate(60f, Offset(50f, 50f)) { drawCircle(brush, 3.2f, Offset(88f, 50f)) }
                } else iconPath(motif)?.let { drawPath(it, brush, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
            }
        }
    }
    translate(0f, side * 0.016f) { body(androidx.compose.ui.graphics.SolidColor(Color.Black.copy(alpha = 0.35f))) }
    body(Foil.brush(100f, 100f))
}

/** 竖版精装封面（3:4 板面）：预设图 + 烫金课程名 + 淡水印字。 */
@Composable
fun HardCover(hc: HardCovers.Hc, title: String?, modifier: Modifier = Modifier, glyph: String? = title?.let { HardCovers.watermark(it) }, showTitle: Boolean = true) {
    BoxWithConstraints(modifier.clipToBounds()) {
        val w = maxWidth; val h = maxHeight
        Image(painterResource(hcCoverRes(hc)), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        if (!glyph.isNullOrBlank() && w >= 40.dp) Watermark(glyph, 0.42f, 0.717f, 0.95f, 0.075f)
        if (showTitle && !title.isNullOrBlank() && h >= 84.dp) {
            val n = title.length
            val two = n > 5
            val perLine = if (two) ceil(n / 2f).toInt() else n
            val fs = min(h.value * (if (two) 0.062f else 0.08f), w.value * 0.44f / perLine.coerceAtLeast(1))
            val density = androidx.compose.ui.platform.LocalDensity.current
            Box(Modifier.offset(x = w * 0.30f, y = h * 0.53f).width(w * 0.49f).padding(top = 0.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(w * 0.49f, h * 0.185f), contentAlignment = Alignment.Center) {
                    BasicText(title, style = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = with(density) { fs.dp.toSp() },
                        lineHeight = with(density) { (fs * 1.18f).dp.toSp() }, brush = Foil.text, textAlign = TextAlign.Center),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** 横版/方形区域用的精装布面：渐变布 + 亚麻纹 + 织纹 + 光泽 + 烫金大图标 + 淡水印字。 */
@Composable
fun HcCloth(hc: HardCovers.Hc, glyph: String?, modifier: Modifier = Modifier, iconFrac: Float = 0.46f, iconCenter: Offset = Offset(0.5f, 0.48f)) {
    val linen = ImageBitmap.imageResource(R.drawable.hc_linen)
    val linenBrush = remember(linen) { ShaderBrush(ImageShader(linen, TileMode.Repeated, TileMode.Repeated)) }
    BoxWithConstraints(modifier.clipToBounds()) {
        val w = maxWidth; val h = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val W = size.width; val H = size.height
            drawRect(Brush.linearGradient(listOf(hc.lightColor, hc.color, hc.darkColor), Offset.Zero, Offset(W * 0.55f, H)))
            drawRect(linenBrush, alpha = 0.55f)
            val step = 4.dp.toPx().coerceAtLeast(3f)
            var x = 0f; while (x < W) { drawLine(Color.Black.copy(alpha = 0.045f), Offset(x, 0f), Offset(x, H), 1f); x += step }
            var y = 0f; while (y < H) { drawLine(Color.Black.copy(alpha = 0.045f), Offset(0f, y), Offset(W, y), 1f); y += step }
            drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent, Color.Black.copy(alpha = 0.12f)), Offset.Zero, Offset(W * 0.7f, H)))
            drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), Offset(W * 0.3f, H * 0.18f), W * 0.55f))
            val side = min(W, H) * iconFrac
            drawFoilIcon(hc.motif, Offset(W * iconCenter.x - side / 2, H * iconCenter.y - side / 2), side, strokeScale = 1.1f)
        }
        if (!glyph.isNullOrBlank()) Watermark(glyph, min(0.62f, 0.5f * w.value / h.value), 0.80f, 1.08f, 0.11f)
    }
}

/** 淡水印字：画在 Canvas 里并裁剪到封面范围内。sizeFrac 相对高度；cx / baseline 相对宽 / 高。 */
@Composable
private fun Watermark(glyph: String, sizeFrac: Float, cx: Float, baseline: Float, alpha: Float) {
    val tm = androidx.compose.ui.text.rememberTextMeasurer()
    Canvas(Modifier.fillMaxSize()) {
        val fsPx = size.height * sizeFrac
        val layout = tm.measure(glyph, TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = androidx.compose.ui.unit.TextUnit(fsPx / density / fontScale, androidx.compose.ui.unit.TextUnitType.Sp), color = Color.White.copy(alpha = alpha)))
        val fb = layout.firstBaseline
        clipRect {
            drawText(layout, topLeft = Offset(size.width * cx - layout.size.width / 2f, size.height * baseline - fb))
        }
    }
}

/** 书脊：预设书脊图三段拉伸（上下烫金带保持比例，中段布面拉伸）+ 竖排烫金标题 + 日期。 */
@Composable
fun HcSpine(hc: HardCovers.Hc, title: String, sub: String, modifier: Modifier = Modifier, fill: Float = 1f) {
    val img = ImageBitmap.imageResource(hcSpineRes(hc))
    BoxWithConstraints(modifier) {
        val h = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val W = size.width; val H = size.height
            val s = W / 100f
            val topSrc = 70; val botSrcStart = 722 // 跳过书脊底部图标，只保留烫金带
            val topH = topSrc * s; val botH = (800 - botSrcStart) * s
            val midH = (H - topH - botH).coerceAtLeast(0f)
            val wi = W.toInt().coerceAtLeast(1)
            drawImage(img, IntOffset(0, 0), IntSize(100, topSrc), IntOffset(0, 0), IntSize(wi, topH.toInt()))
            drawImage(img, IntOffset(0, 120), IntSize(100, 480), IntOffset(0, topH.toInt()), IntSize(wi, midH.toInt() + 1))
            drawImage(img, IntOffset(0, botSrcStart), IntSize(100, 800 - botSrcStart), IntOffset(0, (H - botH).toInt()), IntSize(wi, botH.toInt()))
            if (fill < 1f) drawRect(Color.White.copy(alpha = 0.55f), size = Size(W, H * (1f - fill)))
        }
        val maxChars = ((h.value - 44) / 14.5f).toInt().coerceAtLeast(2)
        BasicText(title.take(maxChars).toList().joinToString("\n"), Modifier.align(Alignment.TopCenter).padding(top = 19.dp),
            style = NT.Type.Spine.copy(brush = Foil.text, textAlign = TextAlign.Center, lineHeight = androidx.compose.ui.unit.TextUnit(14.5f, androidx.compose.ui.unit.TextUnitType.Sp)))
        if (sub.isNotBlank()) BasicText(sub, Modifier.align(Alignment.BottomCenter).padding(bottom = 15.dp),
            style = NT.Type.SpineDate.copy(fontSize = androidx.compose.ui.unit.TextUnit(7f, androidx.compose.ui.unit.TextUnitType.Sp), color = Color(0xFFEBD9A8).copy(alpha = 0.8f)))
    }
}

/** 封面选择器里的一格。 */
@Composable
fun HcSwatch(hc: HardCovers.Hc, modifier: Modifier = Modifier) = HardCover(hc, null, modifier, glyph = null, showTitle = false)

fun lerpInk(c: Color, f: Float) = lerp(c, Color.Black, f)
