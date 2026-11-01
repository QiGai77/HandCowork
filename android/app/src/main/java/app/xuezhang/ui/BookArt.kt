package app.xuezhang.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/** 万能封面图案（10 种）+ AI 生成图片封面。 */
@Composable
fun UniCover(style: Int, seed: String, img: String = "", modifier: Modifier = Modifier) {
    if (img.isNotBlank()) {
        val bmp = remember(img) { CoverImages.load(img.removePrefix("file:")) }
        if (bmp != null) { Image(bmp, null, modifier, contentScale = ContentScale.Crop); return }
    }
    val cv = Skins.coverFor(seed, style)
    Canvas(modifier) {
        val w = size.width; val h = size.height
        drawRect(Brush.linearGradient(listOf(cv.a, cv.b), Offset(0f, 0f), Offset(w, h)))
        val ink = cv.ink.copy(alpha = 0.22f)
        val sw = 1.4.dp.toPx()
        when (cv.pattern) {
            0 -> for (k in 0 until 6) {
                val p = Path(); val y0 = h * (0.35f + k * 0.11f)
                var x = 0f; p.moveTo(0f, y0)
                while (x <= w) { p.lineTo(x, y0 + sin(x / w * 6.28f * 1.5f + k) * h * 0.04f); x += 4f }
                drawPath(p, ink, style = Stroke(sw))
            }
            1 -> { val st = w / 8; var x = st; while (x < w) { drawLine(ink, Offset(x, 0f), Offset(x, h), sw * 0.6f); x += st }
                var y = st; while (y < h) { drawLine(ink, Offset(0f, y), Offset(w, y), sw * 0.6f); y += st } }
            2 -> { val st = w / 6; var y = st / 2; var r = 0; while (y < h) { var x = if (r % 2 == 0) st / 2 else st; while (x < w) { drawCircle(ink, st * 0.16f, Offset(x, y)); x += st }; y += st; r++ } }
            3 -> clipRect { var x = -h; while (x < w) { drawLine(ink, Offset(x, h), Offset(x + h, 0f), sw * 2.2f); x += w / 6 } }
            4 -> for (k in 1..7) drawCircle(ink, w * 0.13f * k, Offset(w * 0.85f, h * 0.2f), style = Stroke(sw))
            5 -> { val p = Path(); p.moveTo(0f, h); p.lineTo(0f, h * 0.7f); p.lineTo(w * 0.3f, h * 0.5f); p.lineTo(w * 0.5f, h * 0.64f); p.lineTo(w * 0.78f, h * 0.42f); p.lineTo(w, h * 0.58f); p.lineTo(w, h); p.close()
                drawPath(p, cv.ink.copy(alpha = 0.16f)); drawCircle(Color.White.copy(alpha = 0.55f), w * 0.09f, Offset(w * 0.74f, h * 0.2f)) }
            6 -> { val r = java.util.Random(seed.hashCode().toLong()); repeat(40) { drawCircle(cv.ink.copy(alpha = 0.25f + r.nextFloat() * 0.6f), (0.6f + r.nextFloat() * 1.6f).dp.toPx(), Offset(r.nextFloat() * w, r.nextFloat() * h)) } }
            7 -> { val st = w / 4; for (i in 0 until 4) for (j in 0 until 6) if ((i + j + seed.length) % 3 == 0) drawRoundRect(ink, Offset(i * st + 3f, j * st + 3f), Size(st - 6f, st - 6f), CornerRadius(4.dp.toPx())) }
            8 -> for (k in 0 until 5) drawArc(ink, 180f, 180f, false, Offset(-w * 0.2f + k * w * 0.12f, h * 0.5f - k * h * 0.04f), Size(w * 1.4f - k * w * 0.24f, h * 0.9f), style = Stroke(sw * 1.4f))
            else -> { val m = 6.dp.toPx(); drawRoundRect(cv.ink.copy(alpha = 0.8f), Offset(m, m), Size(w - 2 * m, h - 2 * m), CornerRadius(3.dp.toPx()), style = Stroke(sw))
                drawRoundRect(cv.ink.copy(alpha = 0.5f), Offset(m * 1.7f, m * 1.7f), Size(w - 3.4f * m, h - 3.4f * m), CornerRadius(2.dp.toPx()), style = Stroke(sw * 0.6f)) }
        }
        // 轻微光泽
        drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent, Color.Black.copy(alpha = 0.10f)), Offset(0f, 0f), Offset(w, h)))
    }
}

/** 小色块预览（选择器里用）。 */
@Composable
fun UniCoverSwatch(style: Int, modifier: Modifier) = Box(modifier) { UniCover(style, "swatch$style", "", Modifier.fillMaxSize()) }
