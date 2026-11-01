package app.xuezhang.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.launch

/**
 * iOS 比例的开关：轨道 51×31，滑块 27dp 完全在轨道内（2dp 边距）。
 * 开：强调色轨道；关：浅灰轨道。按下时滑块横向拉长，松手弹簧回位；支持左右拖动。
 * 轨道顶部有一层很淡的玻璃高光，与液态玻璃风格一致。backdrop 参数保留以兼容旧调用。
 */
@Composable
fun LiquidToggle(
    selected: () -> Boolean,
    onSelect: (Boolean) -> Unit,
    @Suppress("UNUSED_PARAMETER") backdrop: Backdrop? = null,
    modifier: Modifier = Modifier
) {
    val dark = false
    val accent = if (dark) Color(0xFF30D158) else Color(0xFF34C759)
    val offTrack = if (dark) Color(0xFF39393D) else Color(0xFFE5E5EA)
    val scope = rememberCoroutineScope()
    val on = rememberUpdatedState(selected())
    val cb = rememberUpdatedState(onSelect)
    val pos = remember { Animatable(if (selected()) 1f else 0f) }     // 0 关 1 开
    val press = remember { Animatable(0f) }                             // 按压程度
    val anim = spring<Float>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)

    LaunchedEffect(on.value) { pos.animateTo(if (on.value) 1f else 0f, anim) }

    Box(
        modifier
            .size(51.dp, 31.dp)
            .semantics { role = Role.Switch; stateDescription = if (on.value) "开" else "关" }
            .pointerInput(Unit) {
                val travel = (51.dp - 31.dp).toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    scope.launch { press.animateTo(1f, spring(0.8f, 700f)) }
                    var dragged = false
                    var p = pos.value
                    horizontalDrag(down.id) { ch ->
                        val dx = ch.positionChange().x
                        if (kotlin.math.abs(dx) > 0.5f) dragged = true
                        p = (p + dx / travel).coerceIn(0f, 1f)
                        scope.launch { pos.snapTo(p) }
                        ch.consume()
                    }
                    scope.launch { press.animateTo(0f, spring(0.7f, 500f)) }
                    val target = if (dragged) p >= 0.5f else !on.value
                    scope.launch { pos.animateTo(if (target) 1f else 0f, anim) }
                    if (target != on.value) cb.value(target)
                }
            }
            .drawBehind {
                val h = size.height; val w = size.width
                val r = CornerRadius(h / 2, h / 2)
                val f = pos.value.coerceIn(0f, 1f)
                // 轨道
                drawRoundRect(lerp(offTrack, accent, f), cornerRadius = r)
                // 玻璃高光（顶部淡白渐变 + 细描边）
                drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), 0f, h * 0.6f), cornerRadius = r)
                drawRoundRect(Color.Black.copy(alpha = 0.04f * (1f - f)), cornerRadius = r, style = Stroke(1.dp.toPx()))
                // 滑块：27dp 圆，按下时拉长到 33dp，始终留 2dp 边距
                val pad = 2.dp.toPx()
                val d = h - pad * 2
                val stretch = 6.dp.toPx() * press.value
                val tw = d + stretch
                val x = pad + (w - pad * 2 - tw) * f
                val tr = CornerRadius(d / 2, d / 2)
                // 阴影
                drawRoundRect(Color.Black.copy(alpha = 0.10f), Offset(x, pad + 1.5.dp.toPx()), Size(tw, d), tr)
                drawRoundRect(Color.Black.copy(alpha = 0.06f), Offset(x - 0.5f, pad + 0.5.dp.toPx()), Size(tw + 1f, d + 1f), tr)
                drawRoundRect(Color.White, Offset(x, pad), Size(tw, d), tr)
                drawRoundRect(Brush.verticalGradient(listOf(Color.White, Color(0xFFF4F4F6)), pad, pad + d), Offset(x, pad), Size(tw, d), tr)
            }
    )
}
