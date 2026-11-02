package app.xuezhang.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xuezhang.engine.plugin.SplashSpec
import app.xuezhang.engine.plugin.ThemeRegistry
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// =================== v0.0.7.8 主题插件运行时：字体 / 纹理 / 背景 / 印章按钮 / 墨迹进度条 / 开屏动画 ===================

/** 主题字体（按文件路径缓存）。字体缺字时 Android 会自动回退到系统字体。 */
object ThemeFonts {
    private val cache = HashMap<String, FontFamily?>()
    private fun load(rel: String): FontFamily? {
        val a = ThemeRegistry.active.value ?: return null
        if (rel.isBlank()) return null
        val f = File(a.dir, rel)
        return cache.getOrPut(f.absolutePath) { runCatching { FontFamily(Font(f)) }.getOrNull() }
    }
    fun serif(): FontFamily? = ThemeRegistry.active.value?.spec?.serifFont?.let(::load)
    fun body(): FontFamily? = ThemeRegistry.active.value?.spec?.bodyFont?.let(::load)
    fun file(rel: String): FontFamily? = load(rel)
}

/** 主题纹理/插画位图（按路径缓存，限制解码尺寸）。 */
object ThemeArt {
    private val cache = HashMap<String, ImageBitmap?>()
    fun bitmap(f: File?, maxSide: Int = 1600): ImageBitmap? {
        if (f == null || !f.isFile) return null
        return cache.getOrPut(f.absolutePath + "@" + maxSide) {
            runCatching {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(f.absolutePath, o)
                var s = 1; while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= maxSide) s *= 2
                BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
            }.getOrNull()
        }
    }
    fun rel(rel: String, maxSide: Int = 1600): ImageBitmap? = ThemeRegistry.active.value?.let { a -> rel.takeIf { it.isNotBlank() }?.let { bitmap(File(a.dir, it), maxSide) } }
}

object ThemeLook {
    val active get() = ThemeRegistry.active.value != null
    val sealButtons get() = ThemeRegistry.active.value?.spec?.buttonStyle == "seal"
    val brushProgress get() = ThemeRegistry.active.value?.spec?.progressStyle == "brush"
    val inkNav get() = ThemeRegistry.active.value?.spec?.navStyle == "ink"
    fun color(k: String): Color? = ThemeRegistry.active.value?.spec?.color(k)?.let { Color(it) }
}

/** 主题背景：宣纸纹理平铺 + 顶部远山 / 底部水波插画（淡）。返回 false 表示没有主题背景，调用方画默认背景。 */
fun DrawScope.drawThemeBackground(): Boolean {
    val a = ThemeRegistry.active.value ?: return false
    val sp = a.spec
    val bg = sp.color("bg")?.let { Color(it) } ?: Color(0xFFF3EEE2)
    drawRect(bg)

    val w = size.width; val h = size.height

    // 1. 纹理图片（若有），否则程序化宣纸微纤维与纸质质感
    val tex = ThemeArt.rel(sp.texture, 1024)
    if (tex != null) {
        drawRect(ShaderBrush(ImageShader(tex, TileMode.Repeated, TileMode.Repeated)), alpha = sp.textureAlpha.coerceIn(0f, 1f))
    } else {
        val fiberColor = Color(0x0C2B2B2B)
        val fiberHighlight = Color(0x18FFFFFF)
        val count = 36
        for (i in 0 until count) {
            val fx = ((i * 137.5f) % w)
            val fy = ((i * 269.3f) % h)
            val fl = 16f + (i % 7) * 7f
            val rad = Math.toRadians((i * 47.0) % 180.0).toFloat()
            val dx = kotlin.math.cos(rad) * fl
            val dy = kotlin.math.sin(rad) * fl
            drawLine(fiberColor, Offset(fx, fy), Offset(fx + dx, fy + dy), strokeWidth = 0.8f)
            drawLine(fiberHighlight, Offset(fx + 0.5f, fy + 0.5f), Offset(fx + dx + 0.5f, fy + dy + 0.5f), strokeWidth = 0.5f)
        }
    }

    // 2. 顶部远山插画（若有图片用图片，否则程序化绘制写意水墨重峦迭嶂）
    val topImg = ThemeArt.rel(sp.topArt)
    if (topImg != null) {
        val ih = (w * topImg.height / topImg.width).toInt()
        drawImage(topImg, IntOffset.Zero, IntSize(topImg.width, topImg.height), IntOffset(0, 0), IntSize(w.toInt(), ih), alpha = sp.artAlpha)
    } else {
        val inkDark = sp.color("ink")?.let { Color(it) } ?: Color(0xFF2B2B2B)
        // 远山（缥缈淡墨）
        val pFar = Path().apply {
            moveTo(0f, h * 0.12f)
            cubicTo(w * 0.25f, h * 0.06f, w * 0.45f, h * 0.14f, w * 0.70f, h * 0.07f)
            cubicTo(w * 0.85f, h * 0.04f, w * 0.95f, h * 0.09f, w, h * 0.08f)
            lineTo(w, 0f)
            lineTo(0f, 0f)
            close()
        }
        drawPath(pFar, Brush.verticalGradient(
            colors = listOf(inkDark.copy(alpha = 0.07f), inkDark.copy(alpha = 0.02f), Color.Transparent),
            startY = 0f, endY = h * 0.14f
        ))

        // 中山（主峰挺秀，润泽水墨）
        val pMid = Path().apply {
            moveTo(0f, h * 0.18f)
            cubicTo(w * 0.18f, h * 0.11f, w * 0.35f, h * 0.16f, w * 0.52f, h * 0.09f)
            cubicTo(w * 0.68f, h * 0.04f, w * 0.82f, h * 0.15f, w, h * 0.12f)
            lineTo(w, 0f)
            lineTo(0f, 0f)
            close()
        }
        drawPath(pMid, Brush.verticalGradient(
            colors = listOf(inkDark.copy(alpha = 0.09f), inkDark.copy(alpha = 0.03f), Color.Transparent),
            startY = 0f, endY = h * 0.20f
        ))

        // 近岫（右侧低缓墨影）
        val pNear = Path().apply {
            moveTo(w * 0.40f, 0f)
            cubicTo(w * 0.60f, h * 0.16f, w * 0.78f, h * 0.18f, w, h * 0.15f)
            lineTo(w, 0f)
            close()
        }
        drawPath(pNear, Brush.verticalGradient(
            colors = listOf(inkDark.copy(alpha = 0.06f), Color.Transparent),
            startY = 0f, endY = h * 0.19f
        ))
    }

    // 3. 底部江南水波插画（若有图片用图片，否则程序化绘制静谧水波波纹）
    val botImg = ThemeArt.rel(sp.bottomArt)
    if (botImg != null) {
        val ih = (w * botImg.height / botImg.width).toInt()
        drawImage(botImg, IntOffset.Zero, IntSize(botImg.width, botImg.height), IntOffset(0, (h - ih).toInt()), IntSize(w.toInt(), ih), alpha = sp.artAlpha)
    } else {
        val inkDark = sp.color("ink")?.let { Color(it) } ?: Color(0xFF2B2B2B)
        for (j in 0 until 4) {
            val yBase = h - (14f + j * 16f) * density
            val pWave = Path().apply {
                moveTo(0f, yBase)
                cubicTo(w * 0.33f, yBase - 4f * density, w * 0.66f, yBase + 4f * density, w, yBase)
            }
            drawPath(pWave, inkDark.copy(alpha = 0.035f - j * 0.007f), style = Stroke(width = 1.2f * density))
        }
    }
    return true
}

/** 印章风格（朱砂底、方角、白字略带飞白）：给 Btn 用。 */
fun DrawScope.drawSeal(color: Color, corner: Float) {
    drawRoundRect(color, cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner))
    // 飞白：几道浅色斜纹，模拟印泥不匀
    val n = 7
    for (i in 0 until n) {
        val x = size.width * (i + 0.5f) / n
        drawLine(Color.White.copy(alpha = 0.06f + 0.04f * (i % 3)), Offset(x - size.height * 0.4f, size.height), Offset(x + size.height * 0.2f, 0f), 2.dp.toPx())
    }
    drawRoundRect(Color.White.copy(alpha = 0.55f), Offset(3.dp.toPx(), 3.dp.toPx()), Size(size.width - 6.dp.toPx(), size.height - 6.dp.toPx()),
        androidx.compose.ui.geometry.CornerRadius(corner * 0.6f), style = Stroke(0.9.dp.toPx()))
}

/** 墨迹进度条：轨道淡墨，进度是一笔浓→淡的毛笔刷痕，末端飞白。 */
fun DrawScope.drawInkProgress(f: Float, ink: Color = Color(0xFF2B2B2B)) {
    val h = size.height; val w = size.width
    drawRoundRect(ink.copy(alpha = 0.08f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2))
    val e = (w * f.coerceIn(0f, 1f))
    if (e <= 1f) return
    val p = Path().apply {
        moveTo(0f, h * 0.55f)
        cubicTo(e * 0.3f, h * 0.05f, e * 0.7f, h * 0.15f, e, h * 0.35f)
        lineTo(e, h * 0.75f)
        cubicTo(e * 0.7f, h * 0.95f, e * 0.3f, h * 1.0f, 0f, h * 0.85f)
        close()
    }
    drawPath(p, Brush.horizontalGradient(listOf(ink, ink.copy(alpha = 0.85f), ink.copy(alpha = 0.55f)), endX = e))
    // 飞白丝
    for (k in 0 until 4) {
        val y = h * (0.35f + k * 0.12f)
        drawLine(Color.White.copy(alpha = 0.35f), Offset(e * (0.55f + k * 0.08f), y), Offset(e, y + h * 0.02f), 0.6.dp.toPx())
    }
}

// ---------------- 开屏动画（主题插件扩展点）----------------

object PluginSplash {
    /** 每次冷启动只播一次。 */
    var shown = mutableStateOf(false)
    fun reducedMotion(ctx: android.content.Context): Boolean = runCatching {
        android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
}

/** 启用了带开屏动画的主题插件时，在 App 最上层播放；点按跳过；系统关闭动画时直接显示最后一帧 0.6 秒。 */
@Composable
fun PluginSplashHost() {
    val a = ThemeRegistry.active.value
    val spec = a?.splash ?: return
    if (PluginSplash.shown.value) return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val reduced = remember { PluginSplash.reducedMotion(ctx) }
    val t = remember { Animatable(if (reduced) 0.93f else 0f) }
    LaunchedEffect(spec) {
        if (reduced) { kotlinx.coroutines.delay(600); t.snapTo(1f) }
        else t.animateTo(1f, tween(spec.durationMs, easing = LinearEasing))
        PluginSplash.shown.value = true
    }
    InkSplash(spec, t.value, Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) { PluginSplash.shown.value = true })
}

/** 「墨韵」开屏：一滴墨落在宣纸上晕开 → 远山淡入 → 「课堂学长」以文楷逐字显现 → 朱砂印章盖下 → 淡出。纯绘制，t∈[0,1]。 */
@Composable
fun InkSplash(spec: SplashSpec, t: Float, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val font = remember(spec.font) { ThemeFonts.file(spec.font) } ?: Serif
    val paper = remember(spec.paper) { ThemeArt.rel(spec.paper, 1024) }
    val hills = remember(spec.mountains) { ThemeArt.rel(spec.mountains) }
    val tl = SplashSpec.timeline(t)
    val ink = Color(spec.ink); val seal = Color(spec.sealColor)
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val out = 1f - (tl["out"] ?: 0f)
        drawRect(Color(spec.paperColor).copy(alpha = out))
        paper?.let { drawRect(ShaderBrush(ImageShader(it, TileMode.Repeated, TileMode.Repeated)), alpha = out) }
        // 远山
        val m = tl["mountains"] ?: 0f
        hills?.let { img ->
            val ih = (w * img.height / img.width)
            drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(0, (h * 0.12f + (1 - m) * 24.dp.toPx()).toInt()), IntSize(w.toInt(), ih.toInt()), alpha = m * 0.75f * out)
        }
        // 墨滴落下 + 晕开（多圈不规则墨晕，边缘淡）
        val cx = w * 0.5f; val cy = h * 0.42f
        val drop = tl["drop"] ?: 0f; val bloom = tl["bloom"] ?: 0f
        if (drop < 1f) drawCircle(ink.copy(alpha = out), 6.dp.toPx() * (0.6f + drop * 0.4f), Offset(cx, h * 0.18f + (cy - h * 0.18f) * drop * drop))
        if (bloom > 0f) {
            val R = w * 0.36f * easeOut(bloom)
            for (ring in 0 until 6) {
                val k = ring / 6f
                val r = R * (0.35f + k * 0.75f)
                val pth = Path()
                val n = 48
                for (i in 0..n) {
                    val th = 2 * PI * i / n
                    val wob = 1f + 0.08f * sin(th * 3 + ring * 1.7).toFloat() + 0.05f * sin(th * 7 + ring).toFloat() + 0.03f * cos(th * 11 + ring * 2.3).toFloat()
                    val x = cx + (r * wob * cos(th)).toFloat(); val y = cy + (r * wob * sin(th)).toFloat() * 0.92f
                    if (i == 0) pth.moveTo(x, y) else pth.lineTo(x, y)
                }
                pth.close()
                val alpha = (0.22f * (1f - k) + 0.03f) * (1f - bloom * 0.55f) * out
                drawPath(pth, Brush.radialGradient(listOf(ink.copy(alpha = alpha * 1.6f), ink.copy(alpha = alpha * 0.4f), Color.Transparent), Offset(cx, cy), r * 1.1f))
            }
            // 墨心
            drawCircle(Brush.radialGradient(listOf(ink.copy(alpha = 0.55f * out * (1f - bloom * 0.5f)), Color.Transparent), Offset(cx, cy), R * 0.32f), R * 0.32f, Offset(cx, cy))
        }
        // 标题逐字显现（文楷，竖排更有书卷气：两字一列）
        val title = spec.title
        val tp = tl["title"] ?: 0f
        val fs = (w * 0.13f)
        val style = TextStyle(fontFamily = font, fontSize = (fs / density / fontScale).sp, color = ink)
        val chars = title.toList()
        val colH = fs * 1.15f
        val cols = (chars.size + 1) / 2
        val x0 = cx + (cols - 1) * fs * 0.62f
        chars.forEachIndexed { i, ch ->
            val local = ((tp * chars.size) - i).coerceIn(0f, 1f)
            if (local <= 0f) return@forEachIndexed
            val col = i / 2; val row = i % 2
            val layout = tm.measure(ch.toString(), style)
            val x = x0 - col * fs * 1.24f - layout.size.width / 2f
            val y = cy - colH + row * colH - layout.size.height / 2f + (1 - local) * 8.dp.toPx()
            drawText(layout, topLeft = Offset(x, y), alpha = local * out)
        }
        if (spec.subtitle.isNotBlank()) {
            val sl = tm.measure(spec.subtitle, TextStyle(fontFamily = font, fontSize = (fs * 0.26f / density / fontScale).sp, color = ink.copy(alpha = 0.7f), letterSpacing = 4.sp))
            drawText(sl, topLeft = Offset(cx - sl.size.width / 2f, cy + colH * 1.25f), alpha = tp * out)
        }
        // 朱砂印章：从大到小「盖」下，带一点旋转
        val sp = tl["seal"] ?: 0f
        if (sp > 0f) {
            val side = fs * 0.95f
            val sx = x0 - (cols - 1) * fs * 1.24f - fs * 1.15f; val sy = cy + colH * 0.35f
            val sc = 1f + (1f - easeOut(sp)) * 0.8f
            rotate(-4f, Offset(sx, sy)) {
                scale(sc, sc, Offset(sx, sy)) {
                    val tl0 = Offset(sx - side / 2, sy - side / 2)
                    drawRoundRect(seal.copy(alpha = sp * out), tl0, Size(side, side), androidx.compose.ui.geometry.CornerRadius(side * 0.08f))
                    drawRoundRect(Color(spec.paperColor).copy(alpha = 0.85f * sp * out), Offset(tl0.x + side * 0.07f, tl0.y + side * 0.07f), Size(side * 0.86f, side * 0.86f),
                        androidx.compose.ui.geometry.CornerRadius(side * 0.05f), style = Stroke(side * 0.03f))
                    val sealText = spec.seal.take(4)
                    val per = if (sealText.length >= 3) 2 else 1
                    val sfs = side * (if (per == 2) 0.36f else 0.38f)
                    val sStyle = TextStyle(fontFamily = font, fontSize = (sfs / density / fontScale).sp, color = Color(spec.paperColor))
                    sealText.chunked(per).forEachIndexed { c, colTxt ->
                        colTxt.forEachIndexed { r, ch ->
                            val lay = tm.measure(ch.toString(), sStyle)
                            val nCols = (sealText.length + per - 1) / per
                            val cxx = sx + (nCols - 1) * sfs * 0.55f - c * sfs * 1.1f
                            val cyy = sy - (colTxt.length - 1) * sfs * 0.55f + r * sfs * 1.1f
                            drawText(lay, topLeft = Offset(cxx - lay.size.width / 2f, cyy - lay.size.height / 2f), alpha = sp * out)
                        }
                    }
                }
            }
        }
    }
}

private fun easeOut(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)
