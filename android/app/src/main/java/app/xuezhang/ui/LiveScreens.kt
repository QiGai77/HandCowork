package app.xuezhang.ui

import androidx.compose.animation.core.animateFloat
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xuezhang.data.Segment
import app.xuezhang.data.Store
import app.xuezhang.engine.Live
import app.xuezhang.net.Api
import com.kyant.shapes.Capsule
import androidx.compose.ui.text.style.TextOverflow
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// =================== 上课中 ===================
@Composable
fun LiveScreen() {
    val ctx = LocalContext.current
    val lid = Live.lectureId.value
    val course = lid?.let { Store.lecture(it) }?.let { Store.course(it.courseId) }
    var mode by remember { mutableIntStateOf(course?.defaultMode ?: Store.settings.value.defaultMode) }
    val segs = Live.segments
    val list = rememberLazyListState()
    LaunchedEffect(segs.size, mode) { if (segs.isNotEmpty()) list.animateScrollToItem(segs.size + 2) }
    LaunchedEffect(Live.running.value) {
        if (!Live.running.value && lid != null && Nav.stack.lastOrNull() == Route.Live) {
            // 不足 30 秒的录音按误触作废：直接回去，不进「生成笔记」
            if (Live.discarded.value == lid) Nav.pop() else Nav.replace(if (Live.autoGenerate) Route.Generating(lid) else Route.EndClass(lid))
        }
    }
    val slide = course?.slides?.firstOrNull { it.page == Live.currentPage.intValue } ?: course?.slides?.firstOrNull()

    GlassScaffold(content = {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 170.dp)) {
            item {
                TopInset(); Spacer(Modifier.height(56.dp))
                Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val blink by animateFloatAsState(if (Live.elapsed.longValue / 1000 % 2 == 0L) 1f else 0.35f, tween(400), label = "b")
                    Box(Modifier.size(8.dp).clip(Capsule()).background(C.Red.copy(alpha = if (Live.paused.value) 0.3f else blink)))
                    Spacer(Modifier.width(6.dp))
                    BasicText(if (Live.paused.value) "已暂停 ${Live.fmt(Live.elapsed.longValue)}" else "录音中 ${Live.fmt(Live.elapsed.longValue)}",
                        style = T.Sub.copy(color = C.Red, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace))
                    Spacer(Modifier.width(10.dp))
                    BasicText(course?.teacher.orEmpty(), style = T.Sub, maxLines = 1)
                }
                BasicText(course?.name ?: "上课中", Modifier.padding(horizontal = 20.dp), style = T.Large.copy(fontSize = 32.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (slide != null) item {
                PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), radius = 20.dp) {
                    // 课前资料实时预览：当前页原图（随页码变化），点开全屏
                    val sp = course?.slidePath.orEmpty()
                    if (sp.isNotBlank() && java.io.File(sp).exists()) {
                        val total = remember(sp) { SlideImages.pageCount(sp) }
                        androidx.compose.animation.Crossfade(slide.page, label = "slide") { pg ->
                            SlideThumb(sp, pg, widthPx = 1200) { SlideViewer.open.value = Triple(sp, pg, total) }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(slide.title, Modifier.weight(1f), style = T.Item)
                        Tag("P.${slide.page} / ${course?.slides?.lastOrNull()?.page ?: ""}", C.Ink)
                    }
                    slide.formulas.firstOrNull()?.let { MathBlock(it, T.Body, onMath = { t -> Nav.sheet.value = Sheet.Formula(t) }) }
                }
            }
            if (mode == 0 && segs.isNotEmpty()) item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().glass(LocalBg.current!!, com.kyant.shapes.RoundedRectangle(18.dp), surface = Color.White.copy(alpha = 0.55f)).clip(RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon("check", C.Green, 16.dp, 2.4f); Spacer(Modifier.width(8.dp))
                    BasicText("已过滤 ${segs.sumOf { it.fillers }} 处语气词 · ${segs.sumOf { it.repeats }} 处重复", Modifier.weight(1f, fill = true), style = T.Label.copy(fontSize = 13.5.sp), maxLines = 2, softWrap = true)
                    Spacer(Modifier.width(8.dp))
                    // 固定宽度的小标签，不再用 RichText（它会占满整行把左边文字挤成一列）
                    BasicText("上下文补全", Modifier.wrapContentWidth().clip(RoundedCornerShape(6.dp)).background(Color(0x14000000))
                        .border(0.8.dp, Color(0x55000000), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                        style = T.Cap.copy(color = Color(0xFF6B7280)), maxLines = 1, softWrap = false)
                }
            }
            if (segs.isEmpty()) item {
                EmptyHint(if (Live.processing.intValue > 0) "学长在听…" else "开始讲课后，这里会实时出现整理内容", "每 ${Store.settings.value.chunkSec} 秒左右整理一次")
            }
            itemsIndexed(segs, key = { _, s -> s.id }) { _, s ->
                if (mode == 1) { if (!s.offTopic) SeniorBubble(s) } else VoiceCard(s, course?.teacher ?: "老师")
            }
            if (Live.processing.intValue > 0 && segs.isNotEmpty()) item {
                BasicText("学长在听…", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = T.Sub)
            }
            Live.error.value?.let { e -> item { BasicText(e, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = T.Sub.copy(color = C.Red)) } }
        }
    }, overlay = { b ->
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            GlassCircle(b, "back", { Nav.pop() })
            Spacer(Modifier.weight(1f))
            LiquidSegmented(b, listOf("原声", "学长"), mode, { mode = it }, Modifier.width(150.dp), height = 44.dp)
            Spacer(Modifier.width(12.dp))
            GlassCircle(b, "star", { Live.markExam() }, iconColor = C.Red)
        }
        // dock：标记考点 · 波形 · 暂停 · 红色停止（下课）
        Row(
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(12.dp).fillMaxWidth().height(76.dp)
                .glass(b, Capsule(), surface = Color.White.copy(alpha = 0.72f)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.clip(RoundedCornerShape(18.dp)).clickable { Live.markExam() }.padding(horizontal = 8.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon("star", C.Red, 22.dp); BasicText("标记考点", style = T.Cap.copy(fontSize = 10.5.sp, color = C.Ink))
            }
            Waveform(Live.levels.toList().takeLast(24), C.Jade, Modifier.weight(1f).height(34.dp).padding(horizontal = 8.dp))
            Box(Modifier.size(50.dp).clip(Capsule()).background(Color.White).clickable { Live.togglePause() }, contentAlignment = Alignment.Center) {
                Icon(if (Live.paused.value) "play" else "pause", C.Ink, 22.dp)
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(54.dp).clip(Capsule()).background(C.Red).clickable { Live.stop(ctx) }, contentAlignment = Alignment.Center) {
                Icon("stop", Color.White, 22.dp)
            }
        }
    })
}

@Composable
private fun DockBtn(text: String, icon: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.height(46.dp).clip(Capsule()).background(bg).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(icon, fg, 16.dp); Spacer(Modifier.width(6.dp)); BasicText(text, style = T.Btn.copy(color = fg))
    }
}

/** 与课程无关的片段：默认折叠成一行淡色，点开看原话。 */
@Composable
fun OffTopicRow(s: Segment) {
    var open by remember(s.id) { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 3.dp).fillMaxWidth().alpha(0.5f).clickable { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(Live.fmt(s.startMs), style = T.Cap.copy(fontFamily = FontFamily.Monospace))
            Spacer(Modifier.width(8.dp))
            BasicText("无关 · 已折叠", Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0x14000000)).padding(horizontal = 6.dp, vertical = 1.dp), style = T.Cap)
            Spacer(Modifier.width(6.dp))
            BasicText(if (open) "收起" else s.voice.ifBlank { s.raw }, Modifier.weight(1f), style = T.Cap, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        if (open) BasicText(s.voice.ifBlank { s.raw }, Modifier.padding(top = 4.dp), style = T.Sub)
    }
}

@Composable
fun SeniorBubble(s: Segment) {
    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)) {
        Box(Modifier.size(34.dp).clip(Capsule()).background(C.Blue.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            BasicText("学", style = T.Head.copy(fontSize = 15.sp, color = C.Blue))
        }
        Spacer(Modifier.width(8.dp))
        PlainCard(Modifier.weight(1f), radius = 20.dp, padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            if (s.exam) { Tag("考点", C.HlRed); Spacer(Modifier.height(6.dp)) }
            RichText(s.senior.ifBlank { s.voice }, onMath = { Nav.sheet.value = Sheet.Formula(it, s.voice) }, onFill = { Nav.sheet.value = Sheet.Fill(it, s.raw) })
        }
    }
}

@Composable
fun VoiceCard(s: Segment, teacher: String) {
    if (s.offTopic) { OffTopicRow(s); return }
    PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), radius = 20.dp, padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(Live.fmt(s.startMs), style = T.Cap.copy(fontFamily = FontFamily.Monospace))
            if (s.page > 0) { Spacer(Modifier.width(8.dp)); Tag("P.${s.page}", C.Blue) }
            if (s.exam) { Spacer(Modifier.width(6.dp)); Tag("考点", C.HlRed) }
            Spacer(Modifier.weight(1f))
            BasicText(teacher, style = T.Cap)
        }
        Spacer(Modifier.height(6.dp))
        RichText(s.voice, onMath = { Nav.sheet.value = Sheet.Formula(it, s.voice) }, onFill = { Nav.sheet.value = Sheet.Fill(it, s.raw) })
    }
}

// =================== 下课 · 生成笔记 ===================
@Composable
fun GeneratingScreen(id: String) {
    val l = Store.lecture(id)
    val step = Live.genStep.intValue
    LaunchedEffect(l?.status) { if (l?.status == 2) { kotlinx.coroutines.delay(700); Nav.replace(Route.Note(id)) } }
    val waitingChunks = Live.processing.intValue
    GlassScaffold(content = {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            TopInset(); Spacer(Modifier.height(90.dp))
            BasicText("下课啦", style = T.Large.copy(fontSize = 34.sp))
            BasicText(if (l?.status == 3) "生成失败了，可以重试" else "学长正在整理这节课的笔记", style = T.Sub.copy(fontSize = 15.sp))
            Spacer(Modifier.height(30.dp))
            val job = aiJob("notes:$id")
            val target = if (waitingChunks > 0) 0.08f else job?.fraction?.floatValue ?: if (step >= 3) Live.genPct.floatValue.coerceAtLeast(0.3f) else step / 10f + 0.05f
            val p by animateFloatAsState(target, spring(0.8f, 60f), label = "p")
            Box(Modifier.size(136.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val sw = 10.dp.toPx()
                    drawArc(Color(0x12000000), 0f, 360f, false, Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), style = Stroke(sw))
                    drawArc(C.Blue, -90f, 360f * p, false, Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = StrokeCap.Round))
                }
                BasicText("${(p * 100).toInt()}%", style = T.Title)
            }
            Spacer(Modifier.height(14.dp))
            // 实时步骤：当前在做什么 + 流式进度（字数、耗时、正在写的小节）
            Column(Modifier.padding(horizontal = 28.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText(if (waitingChunks > 0) "整理最后 $waitingChunks 段录音…" else Live.genDetail.value.ifBlank { "准备中" }, style = T.Label.copy(color = C.Blue))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(Capsule()).background(Color(0x12000000))) {
                    val sh = androidx.compose.animation.core.rememberInfiniteTransition(label = "gsh").animateFloat(-0.3f, 1.3f, androidx.compose.animation.core.infiniteRepeatable(tween(1400)), label = "gshx")
                    Box(Modifier.fillMaxWidth(p.coerceIn(0f, 1f)).fillMaxHeight().clip(Capsule()).background(C.Blue).then(if (Live.generating.value) Modifier.sheen(sh.value) else Modifier))
                }
                if (Live.genStream.value.isNotBlank() && Live.generating.value) BasicText(Live.genStream.value, Modifier.padding(top = 6.dp), style = T.Cap, maxLines = 1)
                else if (job != null && Live.generating.value) BasicText("已用 ${job.elapsed.longValue / 1000}s · 模型思考中，进度按预计耗时估算", Modifier.padding(top = 6.dp), style = T.Cap, maxLines = 1)
                if (job != null && Live.generating.value) androidx.compose.animation.Crossfade(job.tip.value, Modifier.padding(top = 10.dp), label = "gtip") { t ->
                    BasicText(t, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x0F0A72E8)).padding(horizontal = 12.dp, vertical = 8.dp),
                        style = T.Cap.copy(fontSize = 12.sp, color = C.HlBlue, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
                }
                if (l?.status == 3 || !Live.generating.value) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("简单" to 0, "详细" to 1).forEach { (n, d) ->
                        val on = (Live.genDepth ?: Store.settings.value.noteDepth) == d
                        BasicText(n, Modifier.clip(Capsule()).background(if (on) C.Blue else Color(0x10000000)).clickable { Live.genDepth = d; Store.updateSettings(Store.settings.value.copy(noteDepth = d)) }
                            .padding(horizontal = 14.dp, vertical = 5.dp), style = T.Btn.copy(fontSize = 13.sp, color = if (on) Color.White else C.Ink))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            val segs = l?.segments ?: emptyList()
            val steps = listOf(
                Triple("整理最后几段录音", if (waitingChunks > 0) "还剩 $waitingChunks 段" else "完成", if (waitingChunks > 0) 1 else 2),
                Triple("过滤语气词与重复", "${segs.sumOf { it.fillers }} 处语气词 · ${segs.sumOf { it.repeats }} 处重复", if (step >= 1) 2 else 0),
                Triple("对齐 PPT 页码", "${segs.map { it.page }.filter { it > 0 }.distinct().size} 页", if (step >= 2) 2 else 0),
                Triple("提炼考点、公式和助记", "${segs.count { it.exam }} 个考点标记", if (step >= 4) 2 else if (step == 3) 1 else 0),
                Triple("生成概念图与复习卡", "", if (step >= 5) 2 else 0),
            )
            PlainCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(0.dp)) {
                steps.forEachIndexed { i, (a, b, st) ->
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(24.dp).clip(Capsule()).background(if (st == 2) C.Green.copy(alpha = 0.14f) else Color.Transparent), contentAlignment = Alignment.Center) {
                            when (st) { 2 -> Icon("check", C.Green, 15.dp, 2.6f); 1 -> Icon("refresh", C.Blue, 18.dp); else -> Icon("dot", C.Ter, 18.dp) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            BasicText(a, style = T.Label.copy(color = if (st > 0) C.Ink else C.Sec))
                            if (b.isNotBlank()) BasicText(b, style = T.Cap)
                        }
                    }
                    if (i < steps.lastIndex) Divider(52.dp)
                }
            }
            Live.genError.value?.let { BasicText(it, Modifier.padding(20.dp), style = T.Sub.copy(color = C.Red)) }
            Spacer(Modifier.height(140.dp))
        }
    }, overlay = { b ->
        Row(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (l?.status == 3) GlassPill(b, "重新生成", { Live.regenerate(id, Live.genDepth) }, Modifier.weight(1f), tint = C.Blue.copy(alpha = 0.85f), color = Color.White)
            if (Live.generating.value) GlassPill(b, "后台运行", { Nav.tabTo(2) }, Modifier.weight(1f))
            GlassPill(b, if (l?.status == 2) "查看笔记" else "先看课堂实录", { Nav.replace(Route.Note(id)) }, Modifier.weight(1f), color = C.Blue)
        }
    })
}

// =================== 底部弹层：公式详情 / 补全说明 ===================
@Composable
fun SheetHost() {
    val sheet = Nav.sheet.value
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    AnimatedVisibility(sheet != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
        Box(Modifier.fillMaxSize().background(Color(0x40000000)).clickable(interactionSource = null, indication = null) { Nav.sheet.value = null })
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(sheet != null, enter = slideInVertically(spring(0.8f, 380f)) { it }, exit = slideOutVertically(tween(220)) { it }) {
            var explain by remember(sheet) { mutableStateOf<String?>(null) }
            var loading by remember(sheet) { mutableStateOf(false) }
            Column(
                Modifier.padding(8.dp).windowInsetsPadding(WindowInsets.navigationBars).fillMaxWidth()
                    .glass(LocalBg.current!!, com.kyant.shapes.RoundedRectangle(36.dp), surface = Color.White.copy(alpha = 0.78f), blurDp = 16f, lensH = 18f, lensA = 30f)
                    .clip(RoundedCornerShape(36.dp)).clickable(interactionSource = null, indication = null) {}
                    .padding(horizontal = 22.dp, vertical = 14.dp)
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 5.dp).clip(Capsule()).background(Color(0x22000000)))
                Spacer(Modifier.height(14.dp))
                when (val s = sheet) {
                    is Sheet.Formula -> {
                        BasicText("公式详情", style = T.Title)
                        Spacer(Modifier.height(6.dp))
                        MathBlock(s.tex, T.Body.copy(fontSize = 20.sp), scale = 1.2f)
                        if (explain != null) RichText(explain!!, style = T.Body.copy(fontSize = 15.sp))
                        else if (loading) aiJob("formula")?.let { Spacer(Modifier.height(8.dp)); AiProgressBar(it) }
                        Spacer(Modifier.height(10.dp))
                        BasicText("LaTeX 源码", style = T.Cap)
                        Spacer(Modifier.height(4.dp))
                        BasicText(s.tex, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x0D000000)).padding(12.dp),
                            style = T.Label.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp))
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Btn("复制 LaTeX", {
                                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("latex", s.tex))
                                Island.show("已复制 LaTeX", Island.Kind.Done)
                            }, Modifier.weight(1f), icon = "copy")
                            Btn(if (loading) "学长在想…" else "让学长讲讲", explain@{
                                if (loading || explain != null) return@explain
                                loading = true
                                app.xuezhang.engine.AppJobs.launch {
                                    val job = app.xuezhang.engine.AiProgress.start("formula", "讲解公式")
                                    job.phase("学长在想怎么讲", 0.95f, 10_000)
                                    explain = try {
                                        withContext(Dispatchers.IO) {
                                            Api.chat("你是课堂学长，用朋友口吻、大白话解释公式含义，2~4 句。数学用 LaTeX \$...\$。可用 [b:概念] [r:考点] [o:助记] [u:强调] 标注。不要输出 JSON。",
                                                "公式：${s.tex}\n上下文：${s.context.take(400)}", json = false)
                                        }
                                    } catch (e: Exception) { "解释失败：${e.message}" }
                                    app.xuezhang.engine.AiProgress.finish(job, explain?.startsWith("解释失败") != true, null)
                                    loading = false
                                }
                            }, Modifier.weight(1f), icon = "spark", tint = C.Blue)
                        }
                    }
                    is Sheet.Fill -> {
                        BasicText("上下文补全", style = T.Title)
                        Spacer(Modifier.height(8.dp))
                        RichText("录音这里没听清，学长根据前后文补成了 [f:${s.word}]。下面是语音识别的原始结果，可以对照判断：", style = T.Body.copy(fontSize = 15.sp))
                        Spacer(Modifier.height(10.dp))
                        BasicText(s.raw.ifBlank { "（示例内容没有原始识别）" }, Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x0D000000)).padding(12.dp),
                            style = T.Label.copy(fontSize = 14.sp, color = C.Sec))
                        Spacer(Modifier.height(14.dp))
                        Btn("知道了", { Nav.sheet.value = null }, Modifier.fillMaxWidth(), icon = "check", tint = C.Blue)
                    }
                    is Sheet.Model -> ModelSheetContent(s.kind)
                    Sheet.Sync -> SyncSheetContent()
                    is Sheet.Answer -> AnswerSheetContent(s)
                    is Sheet.Frames -> FramePickerContent(s)
                    null -> {}
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
