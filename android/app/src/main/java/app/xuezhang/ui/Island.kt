package app.xuezhang.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
    /** action：轻点灵动岛时执行（完成 → 查看结果；失败 → 重试）。 */
    data class Msg(val text: String, val kind: Kind, val id: Long = System.nanoTime(), val action: (() -> Unit)? = null)
    val current = mutableStateOf<Msg?>(null)
    fun show(text: String, kind: Kind = Kind.Info, action: (() -> Unit)? = null) { current.value = Msg(text, kind, action = action) }

    /** 持续进度：显示期间灵动岛常驻（发起页上是紧凑胶囊，其他页面展开），点一下回到发起任务的页面。 */
    data class Mini(val title: String, val fraction: Float, val key: String)
    data class Progress(val text: String, val fraction: Float, val tip: String = "", val key: String = "",
                        val others: List<Mini> = emptyList(), val elapsedMs: Long = 0)
    val progress = mutableStateOf<Progress?>(null)
    fun progress(text: String, fraction: Float, tip: String = "") { progress.value = Progress(text, fraction.coerceIn(0f, 1f), tip) }
    fun endProgress() { progress.value = null }

    /** 图片候选：AI 生成的多张图停在灵动岛里，选一张「应用」或全部「丢弃」。 */
    data class Candidates(val title: String, val files: List<String>, val tip: String = "", val origin: Route? = null,
                          val onApply: (Int) -> Unit, val onDiscard: () -> Unit, val id: Long = System.nanoTime())
    val candidates = mutableStateOf<Candidates?>(null)
    fun offer(c: Candidates) { candidates.value = c }

    /** 点进度岛：回到最上面那个任务的发起页。 */
    fun tapProgress(key: String) {
        val j = app.xuezhang.engine.AiProgress.find(key) ?: return
        j.origin?.let { Nav.resume(it, j.originTab) }
    }
}

private val IslandGreen = Color(0xFF3DDC97)

@Composable
private fun IslandBox(x: Dp, top: Dp, w: Dp, h: Dp, radius: Dp, onClick: (() -> Unit)?, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
        Box(
            Modifier
                .then(if (x != Dp.Unspecified) Modifier.offset(x = x - w / 2) else Modifier.fillMaxWidth().wrapContentSize(Alignment.TopCenter))
                .offset(y = top)
                .size(w, h)
                .shadow(14.dp, RoundedCornerShape(radius), ambientColor = Color(0x55000000), spotColor = Color(0x55000000))
                .clip(RoundedCornerShape(radius))
                .background(Color(0xFF0B0B0C))
                .then(if (onClick != null) Modifier.clickable(interactionSource = null, indication = null, onClick = onClick) else Modifier),
            content = content
        )
    }
}

@Composable
private fun Ring(f: Float, color: Color, size: Dp = 16.dp) {
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val st = androidx.compose.ui.graphics.drawscope.Stroke(2.4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawArc(Color(0x33FFFFFF), 0f, 360f, false, style = st)
        drawArc(color, -90f, 360f * f.coerceIn(0.02f, 1f), false, style = st)
    }
}

@Composable
private fun ProgressIsland(p: Island.Progress, x: Dp, top: Dp, baseH: Dp) {
    val anim = androidx.compose.animation.core.animateFloatAsState(p.fraction, spring(0.9f, 120f), label = "ip")
    val job = app.xuezhang.engine.AiProgress.find(p.key)
    val onOrigin = job?.origin != null && Nav.stack.lastOrNull() == job.origin && (job.origin != Route.Tabs || Nav.tab.intValue == job.originTab)
    val tap = { Island.tapProgress(p.key) }
    if (p.others.isNotEmpty()) {
        // 多任务：两个紧凑胶囊并排
        Box(Modifier.fillMaxWidth().offset(y = top), contentAlignment = Alignment.TopCenter) {
            Row(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF0B0B0C)).padding(4.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                (p.others + Island.Mini(p.text.substringBefore(" · "), p.fraction, p.key)).forEach { m ->
                    Row(Modifier.height(baseH).clip(RoundedCornerShape(50)).background(Color(0xFF1C1C1E))
                        .clickable(interactionSource = null, indication = null) { Island.tapProgress(m.key) }.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Ring(m.fraction, IslandGreen, 14.dp); Spacer(Modifier.width(6.dp))
                        BasicText("${m.title} ${(m.fraction * 100).toInt()}%", style = TextStyle(Color.White, 12.sp, FontWeight.SemiBold), maxLines = 1)
                    }
                }
            }
        }
        return
    }
    if (onOrigin) {
        // 紧凑：圆环 + 百分比 + 当前步骤
        IslandBox(x, top, 230.dp, baseH + 4.dp, 50.dp, tap) {
            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Ring(anim.value, IslandGreen)
                Spacer(Modifier.width(8.dp))
                BasicText("${(p.fraction * 100).toInt()}%", style = TextStyle(Color.White, 12.5.sp, FontWeight.Bold))
                Spacer(Modifier.weight(1f))
                BasicText(p.text.substringAfter(" · "), style = TextStyle(Color.White.copy(alpha = 0.85f), 12.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        return
    }
    val w = 330.dp
    IslandBox(x, top, w, baseH + if (p.tip.isNotBlank()) 52.dp else 32.dp, 26.dp, tap) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().align(Alignment.Center).padding(horizontal = 16.dp, vertical = 7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ring(anim.value, IslandGreen, 15.dp)
                Spacer(Modifier.width(8.dp))
                BasicText(p.text, Modifier.weight(1f), style = TextStyle(Color.White, 13.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp))
                BasicText("${(p.fraction * 100).toInt()}%", style = TextStyle(IslandGreen, 12.5.sp, FontWeight.Bold))
            }
            Spacer(Modifier.size(6.dp))
            // 进度条上有一道流光，即使估算进度走得慢也能看出「还在动」
            val sheen = androidx.compose.animation.core.rememberInfiniteTransition(label = "sh").animateFloat(-0.3f, 1.3f,
                androidx.compose.animation.core.infiniteRepeatable(tween(1400)), label = "shx")
            Box(Modifier.fillMaxWidth().size(width = 0.dp, height = 4.dp).clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF))) {
                Box(Modifier.fillMaxWidth(anim.value.coerceIn(0.02f, 1f)).size(width = 0.dp, height = 4.dp).clip(RoundedCornerShape(50))
                    .background(IslandGreen).sheen(sheen.value))
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

@Composable
private fun CandidateIsland(c: Island.Candidates, x: Dp, top: Dp) {
    var sel by remember(c.id) { mutableStateOf(0) }
    val bmps = androidx.compose.runtime.produceState<List<androidx.compose.ui.graphics.ImageBitmap?>>(emptyList(), c.id) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            c.files.map { f -> runCatching { app.xuezhang.engine.ImgUtil.thumb(f, 360)?.asImageBitmap() }.getOrNull() }
        }
    }.value
    IslandBox(x, top, 350.dp, 168.dp, 30.dp, null) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon("sparkle", IslandGreen, 15.dp, 2.2f); Spacer(Modifier.width(6.dp))
                BasicText(c.title + " · 已生成 ${c.files.size} 张", Modifier.weight(1f), style = TextStyle(Color.White, 12.5.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (c.origin != null) BasicText("打开", Modifier.clickable { Nav.resume(c.origin, 0) }, style = TextStyle(IslandGreen, 12.sp, FontWeight.Bold))
            }
            Spacer(Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.lazy.LazyRow(Modifier.weight(1f), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    items(c.files.size) { i ->
                        Box(Modifier.size(78.dp, 78.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)
                            .then(if (i == sel) Modifier.border(2.5.dp, IslandGreen, RoundedCornerShape(12.dp)) else Modifier)
                            .clickable { sel = i }) {
                            bmps.getOrNull(i)?.let { androidx.compose.foundation.Image(it, "候选 ${i + 1}", Modifier.matchParentSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    BasicText("应用", Modifier.width(66.dp).clip(RoundedCornerShape(50)).background(Color.White).clickable {
                        Island.candidates.value = null; c.onApply(sel)
                    }.padding(vertical = 8.dp), style = TextStyle(Color.Black, 13.sp, FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
                    BasicText("丢弃", Modifier.width(66.dp).clip(RoundedCornerShape(50)).background(Color(0xFF2C2C2E)).clickable {
                        Island.candidates.value = null; c.onDiscard()
                    }.padding(vertical = 8.dp), style = TextStyle(Color.White, 13.sp, FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
                }
            }
            if (c.tip.isNotBlank()) { Spacer(Modifier.size(6.dp)); BasicText(c.tip, style = TextStyle(Color(0xFF8E8E93), 10.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
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
            val target = (64 + m.text.length * 15f).coerceIn(150f, 340f)
            launch { w.animateTo(target, spring(0.62f, 340f)) }
            launch { h.animateTo(baseH.value + 10f, spring(0.6f, 420f)) }
            launch { delay(110); content.animateTo(1f, tween(160)) }
        }
        delay(if (m.action != null) 4800 else 2600)
        coroutineScope {
            launch { content.animateTo(0f, tween(110)) }
            launch { delay(60); w.animateTo(baseH.value, spring(0.85f, 420f)) }
            launch { delay(60); h.animateTo(baseH.value, spring(0.85f, 420f)) }
        }
        a.animateTo(0f, tween(140))
        if (Island.current.value?.id == m.id) Island.current.value = null
    }

    Island.candidates.value?.let { c -> CandidateIsland(c, cut.first, cut.second); return }
    val m0 = shown.value
    // 有可点的完成/失败消息时优先显示它（盖过进度岛几秒）
    if (!(m0 != null && m0.action != null && a.value > 0.01f)) Island.progress.value?.let { p -> ProgressIsland(p, cut.first, cut.second, baseH); return }
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
                .background(Color(0xFF0B0B0C))
                .then(if (m.action != null) Modifier.clickable(interactionSource = null, indication = null) { Island.current.value = null; shown.value = null; m.action.invoke() } else Modifier),
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
                    Island.Kind.Done -> "check" to IslandGreen
                    Island.Kind.Warn -> "close" to Color(0xFFFF453A)
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
