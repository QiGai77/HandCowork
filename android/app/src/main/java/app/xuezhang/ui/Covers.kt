package app.xuezhang.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.xuezhang.data.Course
import app.xuezhang.engine.CoverMatch
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** 课程封面：自定义图片 / 预制矢量封面 / 通用封面（名字哈希配色 + 首字）。 */
@Composable
fun CourseCover(c: Course, modifier: Modifier = Modifier, radius: Dp = 16.dp) = CoverView(c.name, c.cover, modifier, radius)

@Composable
fun CoverView(name: String, cover: String, modifier: Modifier = Modifier, radius: Dp = 16.dp) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(radius)
    if (cover.startsWith("file:")) {
        val img = remember(cover) { CoverImages.load(cover.removePrefix("file:")) }
        if (img != null) {
            Image(img, name, modifier.clip(shape), contentScale = ContentScale.Crop)
            return
        }
    }
    val p = CoverMatch.resolve(name, cover)
    Box(modifier.clip(shape)) { CoverArt(p, name, Modifier.fillMaxSize()) }
}

object CoverImages {
    private val cache = LruCache<String, ImageBitmap>(24)
    fun load(path: String): ImageBitmap? {
        cache.get(path)?.let { return it }
        val f = File(path); if (!f.exists()) return null
        return runCatching {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, o)
            var s = 1; while (o.outWidth / s > 1200) s *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
        }.getOrNull()?.also { cache.put(path, it) }
    }
    fun evict(path: String) { cache.remove(path) }
}

@Composable
fun CoverArt(p: CoverMatch.Preset?, name: String, modifier: Modifier) {
    val base = Color(p?.color ?: CoverMatch.fallbackColor(name))
    val glyph = p?.glyph ?: CoverMatch.glyph(name)
    Canvas(modifier.background(base)) {
        val w = size.width; val h = size.height
        // 巨大的半透明首字
        drawIntoCanvas2 { cv ->
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(if (p == null) 46 else 26, 255, 255, 255)
                textSize = h * 1.05f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
            }
            cv.drawText(glyph, w * 0.04f, h * 0.98f, paint)
            if (p == null) {
                // 通用封面：右侧再放一个清晰的首字
                paint.color = android.graphics.Color.argb(235, 255, 255, 255); paint.textSize = h * 0.46f
                paint.textAlign = android.graphics.Paint.Align.CENTER
                cv.drawText(glyph, w * 0.72f, h * 0.52f + h * 0.17f, paint)
            }
        }
        if (p != null) motif(p.motif, w, h)
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Black.copy(alpha = 0.06f))))
    }
}

private inline fun DrawScope.drawIntoCanvas2(block: (android.graphics.Canvas) -> Unit) {
    drawContext.canvas.nativeCanvas.let(block)
}

/** 移植自 v5/s5.py 的 motif()：坐标以封面宽高为基准，k 为相对 92dp 高度的缩放。 */
private fun DrawScope.motif(kind: String, w: Float, h: Float) {
    val k = h / 92.dp.toPx()
    val sw = (2.2.dp.toPx() * k).coerceAtLeast(1.2.dp.toPx())
    val st = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val white = Color.White
    val cx = w * 0.70f; val cy = h * 0.52f; val R = h * 0.36f
    fun u(v: Float) = v * k * density // design px → px
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, a: Float = 1f) = drawLine(white.copy(alpha = a), Offset(x1, y1), Offset(x2, y2), sw, StrokeCap.Round)
    when (kind) {
        "motor" -> {
            drawCircle(white, R, Offset(cx, cy), style = st); drawCircle(white, R * 0.55f, Offset(cx, cy), style = st)
            for (i in 0 until 12) {
                val a = i * PI / 6
                line(cx + R * 0.62f * cos(a).toFloat(), cy + R * 0.62f * sin(a).toFloat(), cx + R * 0.88f * cos(a).toFloat(), cy + R * 0.88f * sin(a).toFloat())
            }
            drawCircle(white.copy(alpha = 0.8f), R * 0.14f, Offset(cx, cy))
        }
        "signal" -> {
            val path = Path(); val n = (w * 0.28f / u(2f)).toInt().coerceAtLeast(10)
            for (i in 0 until n) {
                val x = w * 0.38f + i * u(2f); val y = cy + R * 0.6f * sin(i * 0.11 * (u(2f) / (2 * k * density))).toFloat() * exp(-i * 0.004).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, white, style = st)
            for (i in 0 until 5) {
                val xx = w * 0.42f + i * w * 0.11f; val hh = R * (1.1f - i * 0.18f)
                line(xx, cy + R, xx, cy + R - hh, 0.55f); drawCircle(white.copy(alpha = 0.55f), u(2.6f), Offset(xx, cy + R - hh))
            }
        }
        "pwm" -> {
            val xx = w * 0.40f; val p = Path(); var x = xx; p.moveTo(x, cy + R * 0.5f)
            for (i in 0 until 8) {
                val d = 0.5f + 0.45f * sin(i * 0.8f); val seg = w * 0.065f
                p.relativeLineTo(0f, -R); p.relativeLineTo(seg * d, 0f); p.relativeLineTo(0f, R); p.relativeLineTo(seg * (1 - d), 0f); x += seg
            }
            drawPath(p, white.copy(alpha = 0.75f), style = st)
            val q = Path(); val n = (w * 0.26f / u(2f)).toInt().coerceAtLeast(10)
            for (i in 0 until n) { val px = xx + i * u(2f); val py = cy - R * 0.1f - R * 0.45f * sin(i * 0.06f * (u(2f) / (2 * k * density))); if (i == 0) q.moveTo(px, py) else q.lineTo(px, py) }
            drawPath(q, white, style = st)
        }
        "chip" -> {
            val a = R * 1.3f
            drawRoundRect(white, Offset(cx - a / 2, cy - a / 2), Size(a, a), androidx.compose.ui.geometry.CornerRadius(u(8f)), style = st)
            drawRoundRect(white.copy(alpha = 0.35f), Offset(cx - a / 4, cy - a / 4), Size(a / 2, a / 2), androidx.compose.ui.geometry.CornerRadius(u(4f)))
            for (i in 0 until 5) {
                val o = -a / 2 + a * (i + 0.5f) / 5; val l = u(9f)
                line(cx + o, cy - a / 2, cx + o, cy - a / 2 - l); line(cx + o, cy + a / 2, cx + o, cy + a / 2 + l)
                line(cx - a / 2, cy + o, cx - a / 2 - l, cy + o); line(cx + a / 2, cy + o, cx + a / 2 + l, cy + o)
            }
        }
        "xi" -> {
            fun star(sx: Float, sy: Float, rr: Float) {
                val p = Path()
                for (i in 0 until 10) {
                    val ang = -PI / 2 + i * PI / 5; val rad = if (i % 2 == 0) rr else rr * 0.4f
                    val x = sx + rad * cos(ang).toFloat(); val y = sy + rad * sin(ang).toFloat()
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close(); drawPath(p, Color(0xFFFFD66B))
            }
            star(w * 0.62f, h * 0.36f, R * 0.42f)
            listOf(0.74f to 0.22f, 0.79f to 0.34f, 0.79f to 0.48f, 0.74f to 0.58f).forEach { (dx, dy) -> star(w * dx, h * dy, R * 0.13f) }
            for (i in 0 until 3) { val yy = h * (0.70f + i * 0.09f); line(w * 0.55f, yy, w * 0.9f - i * u(14f), yy, 0.5f) }
        }
        "int" -> drawIntoCanvas2 { cv ->
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(230, 255, 255, 255); textSize = R * 2.1f; textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.ITALIC)
            }
            cv.drawText("∫", cx, cy + R * 0.55f, paint)
            paint.textSize = R * 0.7f; paint.color = android.graphics.Color.argb(178, 255, 255, 255); paint.textAlign = android.graphics.Paint.Align.LEFT
            cv.drawText("dx", cx + R * 0.95f, cy + R * 0.3f, paint)
        }
        "matrix" -> {
            val a = R * 1.1f; val l = u(7f)
            val p = Path().apply {
                moveTo(cx - a, cy - a); relativeLineTo(-l, 0f); relativeLineTo(0f, 2 * a); relativeLineTo(l, 0f)
                moveTo(cx + a, cy - a); relativeLineTo(l, 0f); relativeLineTo(0f, 2 * a); relativeLineTo(-l, 0f)
            }
            drawPath(p, white, style = st)
            for (i in 0 until 3) for (j in 0 until 3)
                drawCircle(white.copy(alpha = if (i == j) 0.95f else 0.5f), u(if (i == j) 4f else 2.6f), Offset(cx - a * 0.62f + j * a * 0.62f, cy - a * 0.62f + i * a * 0.62f))
        }
        "circuit" -> {
            val xx = w * 0.42f
            val p = Path().apply {
                moveTo(xx, cy); relativeLineTo(u(20f), 0f)
                listOf(5f to -10f, 8f to 20f, 8f to -20f, 8f to 20f, 8f to -20f, 5f to 10f).forEach { (dx, dy) -> relativeLineTo(u(dx), u(dy)) }
                relativeLineTo(u(18f), 0f)
                moveTo(xx + u(98f), cy - u(12f)); relativeLineTo(0f, u(24f))
                moveTo(xx + u(106f), cy - u(12f)); relativeLineTo(0f, u(24f))
                moveTo(xx + u(106f), cy); relativeLineTo(u(22f), 0f)
            }
            drawPath(p, white, style = st)
            val q = Path().apply { moveTo(xx, cy); relativeLineTo(0f, R); relativeLineTo(u(128f), 0f); relativeLineTo(0f, -R) }
            drawPath(q, white.copy(alpha = 0.5f), style = st)
        }
        "control" -> {
            val xx = w * 0.42f
            drawRoundRect(white, Offset(xx + u(30f), cy - u(16f)), Size(u(44f), u(32f)), androidx.compose.ui.geometry.CornerRadius(u(6f)), style = st)
            drawCircle(white, u(9f), Offset(xx + u(10f), cy), style = st)
            val p = Path().apply {
                moveTo(xx - u(12f), cy); relativeLineTo(u(13f), 0f)
                moveTo(xx + u(19f), cy); relativeLineTo(u(11f), 0f)
                moveTo(xx + u(74f), cy); relativeLineTo(u(40f), 0f)
                moveTo(xx + u(96f), cy); relativeLineTo(0f, R * 0.9f); relativeLineTo(-u(86f), 0f); relativeLineTo(0f, -R * 0.9f + u(9f))
            }
            drawPath(p, white, style = st)
        }
        "atom" -> {
            listOf(0f, 60f, 120f).forEach { a -> rotate(a, Offset(cx, cy)) { drawOval(white, Offset(cx - R, cy - R * 0.36f), Size(2 * R, R * 0.72f), style = st) } }
            drawCircle(white, u(5f), Offset(cx, cy))
        }
        "opamp" -> {
            val a = R
            val p = Path().apply {
                moveTo(cx - a, cy - a); lineTo(cx + a, cy); lineTo(cx - a, cy + a); close()
                moveTo(cx - a - u(16f), cy - a * 0.45f); relativeLineTo(u(16f), 0f)
                moveTo(cx - a - u(16f), cy + a * 0.45f); relativeLineTo(u(16f), 0f)
                moveTo(cx + a, cy); relativeLineTo(u(18f), 0f)
            }
            drawPath(p, white, style = st)
            line(cx - a + u(8f), cy - a * 0.38f, cx - a + u(16f), cy - a * 0.38f)
            line(cx - a + u(8f), cy + a * 0.45f, cx - a + u(16f), cy + a * 0.45f); line(cx - a + u(12f), cy + a * 0.45f - u(4f), cx - a + u(12f), cy + a * 0.45f + u(4f))
        }
        "gate" -> {
            val a = R
            val p = Path().apply {
                moveTo(cx - a, cy - a); relativeLineTo(a, 0f)
                arcTo(androidx.compose.ui.geometry.Rect(cx - a, cy - a, cx + a, cy + a), -90f, 180f, false)
                lineTo(cx - a, cy + a); close()
                moveTo(cx - a - u(18f), cy - a * 0.5f); relativeLineTo(u(18f), 0f)
                moveTo(cx - a - u(18f), cy + a * 0.5f); relativeLineTo(u(18f), 0f)
                moveTo(cx + a, cy); relativeLineTo(u(18f), 0f)
            }
            drawPath(p, white, style = st)
        }
    }
}
