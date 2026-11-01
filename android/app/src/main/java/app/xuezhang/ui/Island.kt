package app.xuezhang.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 灵动岛式消息：平时不显示任何东西，有消息时从前摄位置长出来，再缩回去。 */
object Island {
    enum class Kind { Rec, Exam, Fill, Done, Warn, Info }
    data class Msg(val text: String, val kind: Kind, val id: Long = System.nanoTime())
    val current = mutableStateOf<Msg?>(null)
    fun show(text: String, kind: Kind = Kind.Info) { current.value = Msg(text, kind) }

    /** 持续进度（导入课件等）：显示期间灵动岛常驻展开，带百分比和进度条。null = 无。 */
    data class Progress(val text: String, val fraction: Float, val tip: String = "")
    val progress = mutableStateOf<Progress?>(null)
    fun progress(text: String, fraction: Float, tip: String = "") { progress.value = Progress(text, fraction.coerceIn(0f, 1f), tip) }
    fun endProgress() { progress.value = null }
}

@Composable
private fun ProgressIsland(p: Island.Progress, x: Dp, top: Dp, baseH: Dp) {
    val anim = androidx.compose.animation.core.animateFloatAsState(p.fraction, spring(0.9f, 120f), label = "ip")
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
        val w = 320.dp
        Box(
            Modifier
                .then(if (x != Dp.Unspecified) Modifier.offset(x = x - w / 2) else Modifier.fillMaxWidth().wrapContentSize(Alignment.TopCenter))
                .offset(y = top)
                .size(w, baseH + if (p.tip.isNotBlank()) 44.dp else 26.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black)
                .padding(horizontal = 16.dp, vertical = 7.dp)
        ) {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().align(Alignment.Center)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon("upload", Color(0xFF64D2FF), 15.dp, 2.4f)
                    Spacer(Modifier.width(8.dp))
                    BasicText(p.text, Modifier.weight(1f), style = TextStyle(Color.White, 12.5.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    BasicText("${(p.fraction * 100).toInt()}%", style = TextStyle(Color(0xFF64D2FF), 12.5.sp, FontWeight.Bold))
                }
                Spacer(Modifier.size(5.dp))
                // 进度条上有一道流光，即使估算进度走得慢也能看出「还在动」
                val sheen = androidx.compose.animation.core.rememberInfiniteTransition(label = "sh").animateFloat(-0.3f, 1.3f,
                    androidx.compose.animation.core.infiniteRepeatable(tween(1400)), label = "shx")
                Box(Modifier.fillMaxWidth().size(width = 0.dp, height = 4.dp).clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF))) {
                    Box(Modifier.fillMaxWidth(anim.value.coerceIn(0.02f, 1f)).size(width = 0.dp, height = 4.dp).clip(RoundedCornerShape(50))
                        .background(Color(0xFF0A84FF))
                        .sheen(sheen.value))
                }
                if (p.tip.isNotBlank()) {
                    Spacer(Modifier.size(5.dp))
                    androidx.compose.animation.Crossfade(p.tip, label = "tip") { t ->
                        BasicText(t, style = TextStyle(Color(0xFFAEAEB2), 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun IslandHost() {
    val msg = Island.current.value
    val view = LocalView.current
    val d = LocalDensity.current
    // locate the camera cutout (fallback: centered, 10dp from top)
    val cut = remember(view) {
        val r = view.rootWindowInsets?.displayCutout?.boundingRects?.firstOrNull()
        if (r != null && r.width() < view.width / 2) with(d) { Triple(r.centerX().toDp(), r.top.toDp(), r.height().toDp()) }
        else Triple(Dp.Unspecified, 10.dp, 30.dp)
    }
    val baseH = cut.third.coerceIn(24.dp, 36.dp)
    val w = remember { Animatable(0f) }
    val h = remember { Animatable(0f) }
    val a = remember { Animatable(0f) }
    val content = remember { Animatable(0f) }
    val shown = remember { mutableStateOf<Island.Msg?>(null) }

    LaunchedEffect(msg?.id) {
        val m = msg ?: return@LaunchedEffect
        shown.value = m
        coroutineScope {
            if (a.value < 0.5f) { w.snapTo(baseH.value); h.snapTo(baseH.value); content.snapTo(0f) }
            launch { a.animateTo(1f, tween(90)) }
            val target = (64 + m.text.length * 15f).coerceIn(150f, 330f)
            launch { w.animateTo(target, spring(0.62f, 340f)) }
            launch { h.animateTo(baseH.value + 10f, spring(0.6f, 420f)) }
            launch { delay(110); content.animateTo(1f, tween(160)) }
        }
        delay(2600)
        coroutineScope {
            launch { content.animateTo(0f, tween(110)) }
            launch { delay(60); w.animateTo(baseH.value, spring(0.85f, 420f)) }
            launch { delay(60); h.animateTo(baseH.value, spring(0.85f, 420f)) }
        }
        a.animateTo(0f, tween(140))
        if (Island.current.value?.id == m.id) Island.current.value = null
    }

    Island.progress.value?.let { p -> ProgressIsland(p, cut.first, cut.second, baseH); return }
    val m = shown.value ?: return
    if (a.value <= 0.01f) return
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
        val x = if (cut.first == Dp.Unspecified) null else cut.first
        Box(
            Modifier
                .then(if (x != null) Modifier.offset(x = x - (w.value / 2).dp) else Modifier.fillMaxWidth().wrapContentSize(Alignment.TopCenter))
                .offset(y = cut.second + (baseH - h.value.dp) / 2 + 0.dp)
                .graphicsLayer { alpha = a.value }
                .size(w.value.dp, h.value.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp).graphicsLayer { alpha = content.value; scaleX = 0.9f + 0.1f * content.value; scaleY = scaleX },
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (ic, col) = when (m.kind) {
                    Island.Kind.Rec -> "mic" to Color(0xFFFF453A)
                    Island.Kind.Exam -> "star" to Color(0xFFFF6961)
                    Island.Kind.Fill -> "spark" to Color(0xFFAEAEB2)
                    Island.Kind.Done -> "check" to Color(0xFF30D158)
                    Island.Kind.Warn -> "close" to Color(0xFFFF9F0A)
                    Island.Kind.Info -> "note" to Color(0xFF64D2FF)
                }
                Icon(ic, col, 16.dp, 2.4f)
                Spacer(Modifier.width(8.dp))
                BasicText(m.text, style = TextStyle(Color.White, 13.5.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** 流光：一条半透明白色斜带在进度条上从左扫到右。 */
fun Modifier.sheen(x: Float): Modifier = this.drawWithContent {
    drawContent()
    val w = size.width
    val cx = w * x
    drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(
        listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0f)),
        startX = cx - w * 0.18f, endX = cx + w * 0.18f))
}
