package app.xuezhang.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

// ---------------- glass ----------------
/** 当前可折射的背景层：内容层里的卡片/按钮折射背景，浮层折射整个内容。 */
val LocalBg = androidx.compose.runtime.staticCompositionLocalOf<Backdrop?> { null }

/** 玻璃需要底下有东西可折射：极淡的环境色 + 细点阵，整体仍是浅色纯净底。 */
@Composable
fun Ambient(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(C.Bg)
        fun blob(cx: Float, cy: Float, r: Float, c: Color) = drawCircle(
            androidx.compose.ui.graphics.Brush.radialGradient(listOf(c, c.copy(alpha = 0f)), Offset(cx, cy), r), r, Offset(cx, cy))
        val w = size.width; val h = size.height
        val k = if (ThemeState.dark) 0.45f else 1f
        blob(w * 0.05f, h * 0.10f, w * 0.75f, Color(0xFF8DB8FF).copy(alpha = 0.30f * k))
        blob(w * 1.00f, h * 0.38f, w * 0.70f, Color(0xFFFFC08A).copy(alpha = 0.26f))
        blob(w * 0.10f, h * 0.70f, w * 0.65f, Color(0xFF9EE6B8).copy(alpha = 0.22f))
        blob(w * 0.90f, h * 0.95f, w * 0.70f, Color(0xFFC9B6FF).copy(alpha = 0.24f))
        val step = 18.dp.toPx(); val dot = 1.dp.toPx()
        var y = step / 2
        while (y < h) { var x = step / 2; while (x < w) { drawCircle(if (ThemeState.dark) Color(0x12FFFFFF) else Color(0x0F000000), dot, Offset(x, y)); x += step }; y += step }
    }
}

/** 液态玻璃按钮（按压放大、跟手高光、透镜折射）。 */
@Composable
fun Btn(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: String? = null, tint: Color = Color.Unspecified,
        color: Color = if (tint == Color.Unspecified) C.Ink else Color.White, backdrop: Backdrop? = LocalBg.current) {
    if (backdrop == null) {
        Row(modifier.height(48.dp).clip(Capsule()).background(if (tint == Color.Unspecified) C.Fill else tint).clickable(onClick = onClick).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) { Icon(icon, color, 17.dp); Spacer(Modifier.width(6.dp)) }
            BasicText(text, style = T.Btn.copy(color = color))
        }
        return
    }
    app.xuezhang.glass.LiquidButton(onClick, backdrop, modifier, tint = tint,
        surfaceColor = if (tint == Color.Unspecified) Color.White.copy(alpha = 0.42f) else Color.Unspecified) {
        if (icon != null) Icon(icon, color, 17.dp)
        BasicText(text, style = T.Btn.copy(color = color), maxLines = 1)
    }
}
fun Modifier.glass(
    backdrop: Backdrop,
    shape: Shape = Capsule(),
    surface: Color = Color.White.copy(alpha = 0.5f),
    tint: Color = Color.Unspecified,
    blurDp: Float = 6f,
    lensH: Float = 14f,
    lensA: Float = 26f,
): Modifier = drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        vibrancy()
        blur(blurDp.dp.toPx())
        lens(lensH.dp.toPx(), lensA.dp.toPx())
    },
    onDrawSurface = {
        drawRect(surface)
        if (tint != Color.Unspecified) drawRect(tint)
    }
)

@Composable
fun GlassCircle(backdrop: Backdrop = LocalBg.current!!, icon: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Color.Unspecified, iconColor: Color = C.Ink, size: Dp = 44.dp) {
    Box(
        modifier.size(size).glass(backdrop, Capsule(), tint = tint).clip(Capsule())
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, iconColor, 22.dp) }
}

@Composable
fun GlassPill(backdrop: Backdrop = LocalBg.current!!, text: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Color.Unspecified, color: Color = C.Ink, icon: String? = null, height: Dp = 46.dp) {
    Row(
        modifier.height(height).glass(backdrop, Capsule(), tint = tint).clip(Capsule())
            .clickable(interactionSource = null, indication = null, onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) { Icon(icon, color, 18.dp); Spacer(Modifier.width(6.dp)) }
        BasicText(text, style = T.Btn.copy(color = color))
    }
}

// ---------------- plain card ----------------
@Composable
fun PlainCard(modifier: Modifier = Modifier, radius: Dp = 22.dp, padding: PaddingValues = PaddingValues(16.dp), onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(radius)
    val bg = LocalBg.current
    Column(
        (if (bg != null) modifier.glass(bg, RoundedRectangle(radius), surface = if (ThemeState.dark) Color(0xFF1C1E24).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.58f), blurDp = 12f, lensH = 10f, lensA = 18f)
        else modifier.shadow(10.dp, shape, ambientColor = Color(0x14000000), spotColor = Color(0x10000000)).background(C.Card, shape))
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) =
    BasicText(text, modifier.padding(start = 20.dp, top = 22.dp, bottom = 8.dp), style = T.Sub.copy(fontWeight = FontWeight.Medium))

@Composable
fun Divider(start: Dp = 0.dp) = Box(Modifier.padding(start = start).fillMaxWidth().height(0.5.dp).background(C.Line))

@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    BasicText(text, modifier.clip(RoundedCornerShape(7.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 7.dp, vertical = 2.dp),
        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color))
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, style: TextStyle = T.Label, singleLine: Boolean = true, mask: Boolean = false) {
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) BasicText(placeholder, style = style.copy(color = C.Ter))
        BasicTextField(value, onChange, singleLine = singleLine, textStyle = style, cursorBrush = SolidColor(C.Blue),
            visualTransformation = if (mask) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    app.xuezhang.glass.LiquidToggle({ on }, onChange, LocalBg.current)
}

// ---------------- liquid segmented control ----------------
@Composable
fun LiquidSegmented(backdrop: Backdrop = LocalBg.current!!, items: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, height: Dp = 40.dp) {
    BoxWithConstraints(modifier.height(height).glass(backdrop, Capsule()).padding(3.dp)) {
        val w = maxWidth / items.size
        val pos = remember { Animatable(selected.toFloat()) }
        val stretch = remember { Animatable(0f) }
        LaunchedEffect(selected) {
            kotlinx.coroutines.coroutineScope {
                launch { stretch.animateTo(1f, spring(1f, 900f)); stretch.animateTo(0f, spring(0.55f, 260f)) }
                launch { pos.animateTo(selected.toFloat(), spring(0.62f, 320f)) }
            }
        }
        Box(
            Modifier.offset(x = w * pos.value).width(w).fillMaxHeight()
                .graphicsLayer { scaleX = 1f + 0.18f * stretch.value; scaleY = 1f - 0.08f * stretch.value }
                .shadow(4.dp, Capsule(), ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
                .clip(Capsule()).background(Color.White)
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            items.forEachIndexed { i, s ->
                Box(Modifier.weight(1f).fillMaxHeight().clip(Capsule()).clickable(interactionSource = null, indication = null) { onSelect(i) },
                    contentAlignment = Alignment.Center) {
                    BasicText(s, style = T.Btn.copy(color = if (i == selected) C.Blue else C.Ink,
                        fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Medium, fontSize = 14.5.sp))
                }
            }
        }
    }
}


// ---------------- list rows ----------------
@Composable
fun Row2(title: String, value: String = "", onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(title, Modifier.weight(1f), style = T.Label.copy(fontSize = 15.5.sp))
        if (trailing != null) trailing() else {
            BasicText(value, Modifier.padding(start = 12.dp), style = T.Label.copy(color = C.Sec), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (onClick != null) { Spacer(Modifier.width(6.dp)); Icon("chev", C.Ter, 14.dp) }
        }
    }
}

// ---------------- stroke icons ----------------
@Composable
fun Icon(name: String, color: Color, size: Dp = 22.dp, stroke: Float = 1.9f, modifier: Modifier = Modifier, fill: Boolean = false) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        Lucide.paths(name)?.let { ps ->
            withTransform({ scale(s, s, Offset.Zero) }) {
                ps.forEach { if (fill) drawPath(it, color); drawPath(it, color, style = Stroke(stroke * 1.05f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
            }
            return@Canvas
        }
        val st = Stroke(stroke * s * 1.05f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(vararg pts: Float, close: Boolean = false): Path = Path().apply {
            moveTo(pts[0] * s, pts[1] * s); var i = 2
            while (i < pts.size) { lineTo(pts[i] * s, pts[i + 1] * s); i += 2 }
            if (close) close()
        }
        fun line(vararg pts: Float) = drawPath(p(*pts), color, style = st)
        when (name) {
            "back" -> line(15f, 4f, 7f, 12f, 15f, 20f)
            "chev" -> line(9f, 5f, 16f, 12f, 9f, 19f)
            "close" -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            "plus" -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
            "more" -> listOf(5f, 12f, 19f).forEach { drawCircle(color, 1.9f * s, Offset(it * s, 12f * s)) }
            "search" -> { drawCircle(color, 6.5f * s, Offset(10.5f * s, 10.5f * s), style = st); line(15.5f, 15.5f, 20f, 20f) }
            "book" -> {
                drawPath(Path().apply {
                    moveTo(12f * s, 6.5f * s); cubicTo(9f * s, 4.5f * s, 5.5f * s, 4.5f * s, 3f * s, 5.5f * s); lineTo(3f * s, 19f * s)
                    cubicTo(5.5f * s, 18f * s, 9f * s, 18f * s, 12f * s, 20f * s); cubicTo(15f * s, 18f * s, 18.5f * s, 18f * s, 21f * s, 19f * s)
                    lineTo(21f * s, 5.5f * s); cubicTo(18.5f * s, 4.5f * s, 15f * s, 4.5f * s, 12f * s, 6.5f * s); lineTo(12f * s, 20f * s)
                }, color, style = st)
            }
            "mic" -> {
                drawRoundRect(color, Offset(9f * s, 3f * s), Size(6f * s, 11f * s), androidx.compose.ui.geometry.CornerRadius(3f * s), style = st)
                drawArc(color, 0f, 180f, false, Offset(5.5f * s, 4.5f * s), Size(13f * s, 12f * s), style = st)
                line(12f, 16.5f, 12f, 21f)
            }
            "note" -> {
                drawRoundRect(color, Offset(5f * s, 3f * s), Size(14f * s, 18f * s), androidx.compose.ui.geometry.CornerRadius(3f * s), style = st)
                line(8.5f, 8.5f, 15.5f, 8.5f); line(8.5f, 12f, 15.5f, 12f); line(8.5f, 15.5f, 12.5f, 15.5f)
            }
            "person" -> {
                drawCircle(color, 3.8f * s, Offset(12f * s, 8f * s), style = st)
                drawArc(color, 200f, 140f, false, Offset(4.5f * s, 13.5f * s), Size(15f * s, 14f * s), style = st)
            }
            "share" -> { line(12f, 14f, 12f, 3.5f); line(8f, 7f, 12f, 3f, 16f, 7f); line(8f, 10.5f, 6f, 10.5f, 6f, 20.5f, 18f, 20.5f, 18f, 10.5f, 16f, 10.5f) }
            "upload" -> { line(12f, 16f, 12f, 4f); line(7f, 9f, 12f, 4f, 17f, 9f); line(5f, 20f, 19f, 20f) }
            "check" -> line(5f, 12.5f, 10f, 17.5f, 19f, 7.5f)
            "pause" -> { drawRoundRect(color, Offset(7f * s, 5f * s), Size(3.5f * s, 14f * s), androidx.compose.ui.geometry.CornerRadius(1.2f * s)); drawRoundRect(color, Offset(13.5f * s, 5f * s), Size(3.5f * s, 14f * s), androidx.compose.ui.geometry.CornerRadius(1.2f * s)) }
            "play" -> drawPath(p(8f, 5f, 19f, 12f, 8f, 19f, close = true), color)
            "stop" -> drawRoundRect(color, Offset(6.5f * s, 6.5f * s), Size(11f * s, 11f * s), androidx.compose.ui.geometry.CornerRadius(2.5f * s))
            "star" -> drawPath(p(12f, 3.5f, 14.6f, 9f, 20.5f, 9.6f, 16f, 13.6f, 17.3f, 19.5f, 12f, 16.5f, 6.7f, 19.5f, 8f, 13.6f, 3.5f, 9.6f, 9.4f, 9f, close = true), color)
            "copy" -> { drawRoundRect(color, Offset(8f * s, 8f * s), Size(12f * s, 12f * s), androidx.compose.ui.geometry.CornerRadius(2.5f * s), style = st); line(5f, 15.5f, 4f, 15.5f, 4f, 4f, 15.5f, 4f, 15.5f, 5f) }
            "trash" -> { line(4.5f, 6.5f, 19.5f, 6.5f); line(9.5f, 6.5f, 9.5f, 4f, 14.5f, 4f, 14.5f, 6.5f); line(6.5f, 6.5f, 7.5f, 20f, 16.5f, 20f, 17.5f, 6.5f) }
            "refresh" -> { drawArc(color, -60f, 300f, false, Offset(4.5f * s, 4.5f * s), Size(15f * s, 15f * s), style = st); line(16f, 3.5f, 17.8f, 5.8f, 15f, 7.2f) }
            "doc" -> { line(14f, 3f, 6f, 3f, 6f, 21f, 18f, 21f, 18f, 7f, 14f, 3f, 14f, 7f, 18f, 7f) }
            "spark" -> { line(12f, 3f, 12f, 21f); line(3f, 12f, 21f, 12f); line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            else -> drawCircle(color, 3f * s, center)
        }
    }
}

@Composable
fun Waveform(levels: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val n = levels.size; if (n == 0) return@Canvas
        val gap = size.width / n
        val bw = gap * 0.5f
        levels.forEachIndexed { i, v ->
            val h = (4.dp.toPx() + (size.height - 4.dp.toPx()) * v.coerceIn(0f, 1f))
            drawRoundRect(color, Offset(i * gap + (gap - bw) / 2, (size.height - h) / 2), Size(bw, h), androidx.compose.ui.geometry.CornerRadius(bw / 2))
        }
    }
}

/** Google 原生四色标志（蓝红黄绿高精度矢量绘制）。 */
@Composable
fun GoogleNativeLogo(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 24.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val radius = minOf(w, h) * 0.44f
        val strokeW = radius * 0.38f

        val red = Color(0xFFEA4335)
        val yellow = Color(0xFFFBBC05)
        val green = Color(0xFF34A853)
        val blue = Color(0xFF4285F4)

        val arcStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Butt)

        // 黄色 (左下弧)
        drawArc(color = yellow, startAngle = 125f, sweepAngle = 95f, useCenter = false,
            topLeft = Offset(cx - radius, cy - radius), size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2), style = arcStyle)
        // 绿色 (底部弧)
        drawArc(color = green, startAngle = 35f, sweepAngle = 90f, useCenter = false,
            topLeft = Offset(cx - radius, cy - radius), size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2), style = arcStyle)
        // 红色 (顶部弧)
        drawArc(color = red, startAngle = 220f, sweepAngle = 105f, useCenter = false,
            topLeft = Offset(cx - radius, cy - radius), size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2), style = arcStyle)
        // 蓝色 (右上弧)
        drawArc(color = blue, startAngle = 325f, sweepAngle = 45f, useCenter = false,
            topLeft = Offset(cx - radius, cy - radius), size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2), style = arcStyle)

        // 蓝色横杠
        val barH = strokeW * 0.95f
        drawRect(
            color = blue,
            topLeft = Offset(cx - radius * 0.05f, cy - barH / 2f),
            size = androidx.compose.ui.geometry.Size(radius * 1.05f, barH)
        )
    }
}

/** Gitee 官方品牌标志。 */
@Composable
fun GiteeLogo(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 24.dp) {
    Box(
        modifier = modifier.size(size).clip(Capsule()).background(Color(0xFFC71D23)),
        contentAlignment = Alignment.Center
    ) {
        BasicText("G", style = T.Title.copy(color = Color.White, fontSize = (size.value * 0.58f).sp, fontWeight = FontWeight.Black, fontFamily = null))
    }
}
