package app.xuezhang.ui

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xuezhang.data.Lecture
import app.xuezhang.data.Store
import app.xuezhang.engine.Live
import com.kyant.shapes.Capsule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// =================== 笔记详情 ===================
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NoteScreen(id: String) {
    val l = Store.lecture(id) ?: run { Nav.pop(); return }
    val ctx = LocalContext.current
    val course = Store.course(l.courseId)
    var seg by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var shareMenu by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var toc by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf("") }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val date = remember(l.date) { SimpleDateFormat("M月d日", Locale.CHINA).format(Date(l.date)) }
    val lecNo = remember(l.id, Store.lectures.size) {
        Store.lectures.filter { it.courseId == l.courseId }.sortedBy { it.date }.indexOfFirst { it.id == l.id } + 1
    }
    val sections = remember(l.sections, l.notes) {
        l.sections.ifEmpty {
            l.notes.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }.map { p ->
                val m = Regex("^\\*\\*(.+?)\\*\\*\\s*").find(p)
                app.xuezhang.data.NoteSection(m?.groupValues?.get(1) ?: "", emptyList(), if (m != null) p.substring(m.range.last + 1) else p)
            }
        }
    }
    val examN = l.segments.count { it.exam }.let { if (it > 0) it else Regex("\\[r:").findAll(l.notes + sections.joinToString { it.body }).count() }
    val fillN = l.segments.sumOf { Regex("\\[f:").findAll(it.voice).count() }
    val coverH = 210.dp
    GlassScaffold(content = {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 120.dp)) {
            item(key = "head") {
                Box(Modifier.fillMaxWidth()) {
                    if (course != null) CourseCover(course, Modifier.fillMaxWidth().height(coverH), radius = 0.dp)
                    else Box(Modifier.fillMaxWidth().height(coverH).background(C.Blue))
                    Box(Modifier.fillMaxWidth().padding(top = coverH - 96.dp).height(100.dp)
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(C.Bg.copy(alpha = 0f), C.Bg))))
                    PlainCard(Modifier.padding(start = 16.dp, end = 16.dp, top = 150.dp).fillMaxWidth(), radius = 26.dp, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                        BasicText(listOfNotNull(course?.name, if (lecNo > 0) "第 $lecNo 讲" else null, date).joinToString(" · "),
                            style = T.Cap.copy(color = C.Blue, fontWeight = FontWeight.SemiBold))
                        Spacer(Modifier.height(4.dp))
                        BasicText(l.title, style = T.Title.copy(fontSize = 23.sp, lineHeight = 30.sp))
                        Spacer(Modifier.height(6.dp))
                        BasicText(listOfNotNull("${l.durationMs / 60000} 分钟", if ((course?.defaultMode ?: 1) == 0) "原声模式" else "学长模式",
                            if (examN > 0) "$examN 个考点" else null, if (fillN > 0) "$fillN 处补全待确认" else null).joinToString(" · "),
                            style = T.Cap.copy(fontSize = 11.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            item(key = "seg") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    LiquidSegmented(items = listOf("笔记", "助记", "概念图", "复习卡", "实录"), selected = seg, onSelect = { seg = it },
                        modifier = Modifier.fillMaxWidth(), height = 44.dp)
                }
            }
            if (l.status != 2 && l.notes.isBlank() && sections.isEmpty()) item {
                PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
                    BasicText(if (Live.generating.value) "笔记生成中…" else "笔记还没生成", style = T.Item)
                    if (!Live.generating.value) BasicText("点这里重新生成", Modifier.padding(top = 6.dp).clickable { Live.regenerate(id); Nav.push(Route.Generating(id)) },
                        style = T.Label.copy(color = C.Blue))
                }
            }
            item(key = "body") {
                Column {
                    AnimatedContent(seg, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) }, label = "seg") { s ->
                        Column {
                            when (s) {
                                0 -> {
                                    TocRow(sections, toc, { toc = !toc }) { i -> toc = false; scope.launch { listState.animateScrollToItem(3 + i) } }
                                }
                                1 -> MnemonicTab(l)
                                2 -> ConceptMap(l)
                                3 -> CardsTab(l)
                                else -> TranscriptTab(l)
                            }
                        }
                    }
                }
            }
            if (seg == 0) {
                sections.forEachIndexed { i, sec ->
                    item(key = "sec$i") {
                        PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), radius = 22.dp) {
                            if (sec.title.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    BasicText(sec.title, Modifier.weight(1f), style = T.Item.copy(fontSize = 16.sp))
                                    if (sec.pages.isNotEmpty()) Tag("P." + sec.pages.joinToString(","), C.Blue)
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            SlideStrip(course, sec.pages)
                            NoteBody(sec.body, onMath = { Nav.sheet.value = Sheet.Formula(it, sec.body) })
                        }
                    }
                }
                val exams = l.segments.filter { it.exam }
                if (exams.isNotEmpty()) {
                    item { Box { SectionLabel("课上标记的考点 · ${exams.size}") } }
                    exams.forEach { s ->
                        item {
                            PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth()) {
                                Row { Tag(Live.fmt(s.startMs), C.HlRed); if (s.page > 0) { Spacer(Modifier.width(6.dp)); Tag("P.${s.page}", C.Blue) } }
                                Spacer(Modifier.height(6.dp))
                                RichText(s.voice, onMath = { Nav.sheet.value = Sheet.Formula(it, s.voice) }, onFill = { Nav.sheet.value = Sheet.Fill(it, s.raw) })
                            }
                        }
                    }
                }
                if (l.source.startsWith("bilibili:") || course?.source == "demo") item {
                    BasicText(if (course?.source == "demo") "示例课：视频为 B 站公开课「华中科技大学 电机学」${l.source.removePrefix("bilibili:")}（变压器（一））。课件和笔记是按本讲主题预先整理的示例，实录为示意，不是这段视频的逐字转写；导入你自己的录播课会真实下载音轨并识别。"
                        else "来源：B 站 ${l.source.removePrefix("bilibili:")}", Modifier.padding(horizontal = 24.dp, vertical = 10.dp), style = T.Cap.copy(fontSize = 11.sp))
                }
            }
        }
    }, overlay = { b ->
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassCircle(b, "back", { Nav.pop() })
                Spacer(Modifier.weight(1f))
                GlassCircle(b, "share", { shareMenu = !shareMenu; menu = false })
                Spacer(Modifier.width(10.dp))
                GlassCircle(b, "more", { menu = !menu; shareMenu = false })
            }
            if (shareMenu) {
                Column(Modifier.align(Alignment.End).padding(top = 8.dp).width(200.dp).glass(b, RoundedRectangle(22.dp), surface = Color.White.copy(alpha = 0.8f)).padding(vertical = 6.dp)) {
                    BasicText(if (sharing) "正在生成…" else "分享为", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = T.Cap)
                    listOf(app.xuezhang.engine.NoteExport.Fmt.DOCX to "filetext", app.xuezhang.engine.NoteExport.Fmt.PDF to "file",
                        app.xuezhang.engine.NoteExport.Fmt.MD to "list", app.xuezhang.engine.NoteExport.Fmt.IMG to "image").forEach { (f, ic) ->
                        MenuItem(f.label, ic) {
                            if (sharing) return@MenuItem
                            sharing = true
                            scope.launch {
                                try {
                                    val lec = Store.lecture(id) ?: return@launch
                                    val intent = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.xuezhang.engine.NoteExport.export(ctx, lec, f) }
                                    shareMenu = false
                                    ctx.startActivity(intent)
                                } catch (e: Exception) {
                                    Island.show("导出失败：${e.message?.take(24)}", Island.Kind.Warn)
                                } finally { sharing = false }
                            }
                        }
                    }
                }
            }
            if (menu) {
                Column(Modifier.align(Alignment.End).padding(top = 8.dp).width(180.dp).glass(b, RoundedRectangle(22.dp), surface = Color.White.copy(alpha = 0.75f)).padding(vertical = 6.dp)) {
                    MenuItem("重新生成笔记", "refresh") { menu = false; Live.regenerate(id); Nav.push(Route.Generating(id)) }
                    MenuItem("删除这节课", "trash", C.Red) { menu = false; Store.deleteLecture(id); Nav.pop() }
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp).height(50.dp)
                .glass(b, Capsule(), surface = Color.White.copy(alpha = 0.72f)).padding(start = 18.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon("sparkle", C.Blue, 17.dp)
            Spacer(Modifier.width(10.dp))
            Field(ask, { ask = it }, "问学长：这一节哪里最容易考？", Modifier.weight(1f), style = T.Label.copy(fontSize = 14.5.sp))
            if (ask.isNotBlank()) BasicText("发送", Modifier.clip(Capsule()).background(C.Blue).clickable {
                Nav.sheet.value = Sheet.Answer(ask.trim(), id); ask = ""
            }.padding(horizontal = 14.dp, vertical = 8.dp), style = T.Btn.copy(color = Color.White, fontSize = 13.5.sp))
            else BasicText("提问", Modifier.clip(Capsule()).clickable { Nav.sheet.value = Sheet.Answer("这一节哪里最容易考？", id) }
                .padding(horizontal = 12.dp, vertical = 8.dp), style = T.Btn.copy(color = C.Blue, fontSize = 13.5.sp))
        }
    })
}

@Composable
private fun TocRow(sections: List<app.xuezhang.data.NoteSection>, open: Boolean, onToggle: () -> Unit, onPick: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.height(32.dp).glass(LocalBg.current!!, Capsule(), surface = Color.White.copy(alpha = 0.55f), blurDp = 4f, lensH = 6f, lensA = 10f)
                .clip(Capsule()).clickable(onClick = onToggle).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon("list", C.Ink, 14.dp); Spacer(Modifier.width(6.dp))
                BasicText("目录 · ${sections.size} 节", style = T.Btn.copy(fontSize = 12.5.sp)); Spacer(Modifier.width(4.dp))
                Icon(if (open) "chevu" else "chevd", C.Sec, 12.dp)
            }
            Spacer(Modifier.width(10.dp))
            Row(Modifier.weight(1f).horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(Triple("概念", C.HlBlueBg, C.HlBlue), Triple("考点", C.HlRedBg, C.HlRed), Triple("助记", C.HlOrangeBg, C.HlOrange)).forEach { (t, bg, fg) ->
                    BasicText(t, Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp), style = T.Cap.copy(color = fg, fontWeight = FontWeight.Bold, fontSize = 11.sp))
                }
                RichText("[u:强调]", style = T.Cap.copy(fontSize = 11.sp, color = C.Ink))
            }
        }
        if (open) {
            Column(Modifier.padding(top = 8.dp).fillMaxWidth().glass(LocalBg.current!!, RoundedRectangle(18.dp), surface = Color.White.copy(alpha = 0.7f)).padding(vertical = 4.dp)) {
                sections.forEachIndexed { i, s ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(i) }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText(s.title.ifBlank { "第 ${i + 1} 节" }, Modifier.weight(1f), style = T.Label.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (s.pages.isNotEmpty()) BasicText("P.${s.pages.first()}", style = T.Cap)
                    }
                }
            }
        }
    }
}

private typealias RoundedRectangle = com.kyant.shapes.RoundedRectangle

@Composable
private fun MenuItem(text: String, icon: String, color: Color = C.Ink, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(text, Modifier.weight(1f), style = T.Label.copy(color = color))
        Icon(icon, color, 18.dp)
    }
}

@Composable
fun Legend() {
    Row(Modifier.padding(horizontal = 18.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        RichText("[b:概念]  [r:考点]  [o:助记]  [u:老师强调]  [f:上下文补全]", style = T.Cap.copy(fontSize = 12.sp))
    }
}

@Composable
private fun MnemonicTab(l: Lecture) {
    if (l.mnemonics.isEmpty()) EmptyHint("还没有助记", "生成笔记后会出现")
    l.mnemonics.forEach { m ->
        PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), radius = 24.dp) {
            BasicText("「${m.title}」", Modifier.clip(RoundedCornerShape(8.dp)).background(C.HlOrangeBg).padding(horizontal = 10.dp, vertical = 5.dp),
                style = T.Item.copy(color = C.HlOrange, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(10.dp))
            RichText(m.body, onMath = { Nav.sheet.value = Sheet.Formula(it, m.body) })
        }
    }
}

@Composable
private fun ConceptMap(l: Lecture) {
    val nodes = l.nodes
    if (nodes.isEmpty()) { EmptyHint("还没有概念图", "生成笔记后会出现"); return }
    var picked by remember { mutableIntStateOf(-1) }
    val colors = listOf(C.Blue, Color(0xFF5856D6), C.Green, C.Orange, C.Red, C.Purple, Color(0xFF30B0C7))
    PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), radius = 30.dp, padding = PaddingValues(0.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(440.dp)) {
            val d = LocalDensity.current
            val w = with(d) { maxWidth.toPx() }; val h = with(d) { maxHeight.toPx() }
            val root = nodes.indexOfFirst { it.parent < 0 }.coerceAtLeast(0)
            val first = nodes.indices.filter { it != root && (nodes[it].parent == root || nodes[it].parent !in nodes.indices) }
            val pos = remember(nodes, w, h) {
                val p = HashMap<Int, Offset>(); p[root] = Offset(w / 2, h / 2)
                val r1 = minOf(w, h) * 0.34f
                first.forEachIndexed { k, i ->
                    val a = -Math.PI / 2 + 2 * Math.PI * k / first.size
                    p[i] = Offset(w / 2 + (r1 * cos(a)).toFloat(), h / 2 + (r1 * sin(a)).toFloat() * 1.05f)
                }
                nodes.indices.filter { it !in p }.forEach { i ->
                    val par = p[nodes[i].parent] ?: p[root]!!
                    val sib = nodes.indices.filter { nodes[it].parent == nodes[i].parent }
                    val k = sib.indexOf(i)
                    val base = kotlin.math.atan2(par.y - h / 2, par.x - w / 2)
                    val a = base + (k - (sib.size - 1) / 2f) * 0.55f
                    p[i] = Offset((par.x + 80f * d.density * cos(a)).coerceIn(50f * d.density, w - 50f * d.density),
                        (par.y + 64f * d.density * sin(a)).coerceIn(30f * d.density, h - 30f * d.density))
                }
                p
            }
            Canvas(Modifier.fillMaxSize()) {
                nodes.forEachIndexed { i, n ->
                    if (i == root) return@forEachIndexed
                    val from = pos[if (n.parent in nodes.indices) n.parent else root] ?: return@forEachIndexed
                    val to = pos[i] ?: return@forEachIndexed
                    drawLine(colors[i % colors.size].copy(alpha = 0.45f), from, to, 1.6.dp.toPx())
                }
            }
            nodes.forEachIndexed { i, n ->
                val p = pos[i] ?: return@forEachIndexed
                val isRoot = i == root
                val col = if (isRoot) C.Ink else colors[i % colors.size]
                var sz by remember { mutableStateOf(IntSize.Zero) }
                val sc by animateFloatAsState(if (picked == i) 1.12f else 1f, spring(0.5f, 400f), label = "n")
                Box(
                    Modifier.offset { androidx.compose.ui.unit.IntOffset((p.x - sz.width / 2).toInt(), (p.y - sz.height / 2).toInt()) }
                        .onSizeChanged { sz = it }
                        .graphicsLayer { scaleX = sc; scaleY = sc }
                        .clip(if (isRoot) RoundedCornerShape(50) else Capsule())
                        .background(if (isRoot) Color.White else col.copy(alpha = 0.12f))
                        .border(if (isRoot) 1.5.dp else 1.dp, col.copy(alpha = if (isRoot) 1f else 0.5f), if (isRoot) RoundedCornerShape(50) else Capsule())
                        .clickable { picked = if (picked == i) -1 else i }
                        .padding(horizontal = if (isRoot) 18.dp else 12.dp, vertical = if (isRoot) 16.dp else 7.dp)
                ) {
                    BasicText(n.label, style = if (isRoot) T.Item.copy(textAlign = TextAlign.Center) else T.Label.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = col))
                }
            }
        }
    }
    if (picked in nodes.indices) {
        val n = nodes[picked]
        PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
            BasicText(n.label, style = T.Item)
            if (n.detail.isNotBlank()) { Spacer(Modifier.height(4.dp)); RichText(n.detail, onMath = { Nav.sheet.value = Sheet.Formula(it) }) }
        }
    } else BasicText("点节点看说明", Modifier.padding(horizontal = 22.dp, vertical = 6.dp), style = T.Cap)
}

@Composable
private fun CardsTab(l: Lecture) {
    if (l.cards.isEmpty()) { EmptyHint("还没有复习卡", "生成笔记后会出现"); return }
    var idx by remember { mutableIntStateOf(0) }
    var reveal by remember(idx) { mutableStateOf(false) }
    val card = l.cards[idx % l.cards.size]
    val flip by animateFloatAsState(if (reveal) 1f else 0f, spring(0.75f, 300f), label = "f")
    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
        PlainCard(Modifier.fillMaxWidth().graphicsLayer { rotationX = 0f; scaleX = 1f - 0.02f * flip }, radius = 30.dp, padding = PaddingValues(22.dp),
            onClick = { reveal = !reveal }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText("复习卡 ${idx % l.cards.size + 1} / ${l.cards.size}", Modifier.weight(1f), style = T.Sub)
                Tag(card.tag, when (card.tag) { "概念" -> C.HlBlue; "易错" -> C.HlOrange; else -> C.HlRed })
            }
            Spacer(Modifier.height(14.dp))
            RichText(card.q, style = T.Body.copy(fontSize = 17.sp, lineHeight = 28.sp))
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(C.Line))
            Spacer(Modifier.height(14.dp))
            if (reveal) RichText(card.a, style = T.Body.copy(fontSize = 16.sp))
            else BasicText("点按卡片看答案", Modifier.fillMaxWidth().padding(vertical = 18.dp), style = T.Sub.copy(textAlign = TextAlign.Center))
        }
    }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Triple("忘了", C.Red, 0), Triple("模糊", C.Orange, 1), Triple("记得", C.Green, 2)).forEach { (t, col, lv) ->
            Btn(t, { card.level = lv; Store.save(); idx += 1 }, Modifier.weight(1f), tint = col)
        }
    }
}

private fun Modifier.matchParentSizeSafe() = this.fillMaxWidth().height(300.dp)

@Composable
private fun TranscriptTab(l: Lecture) {
    var mode by remember { mutableIntStateOf(0) }
    LiquidSegmented(items = listOf("原声", "学长"), selected = mode, onSelect = { mode = it }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth())
    if (l.segments.isEmpty()) EmptyHint("没有录到内容", "")
    val teacher = Store.course(l.courseId)?.teacher?.ifBlank { null } ?: "老师"
    l.segments.forEach { s -> if (mode == 1) SeniorBubble(s) else VoiceCard(s, teacher) }
}

// =================== 笔记库 Tab ===================
@Composable
fun LibraryTab() {
    val lectures = Store.lectures
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            TopInset()
            LargeHeader("共 ${lectures.size} 节课 · ${lectures.sumOf { it.cards.size }} 张复习卡", "笔记")
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().height(44.dp).glass(LocalBg.current!!, Capsule(), surface = Color.White.copy(alpha = 0.5f)).clip(Capsule())
                .clickable { Nav.push(Route.Search) }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon("search", C.Sec, 18.dp); Spacer(Modifier.width(8.dp)); BasicText("搜索笔记、公式或老师原话", style = T.Label.copy(color = C.Sec))
            }
        }
        if (lectures.isEmpty()) item { EmptyHint("还没有笔记", "上一节课就有了") }
        Store.courses.forEach { c ->
            val ls = lectures.filter { it.courseId == c.id }
            if (ls.isNotEmpty()) {
                item { BasicText(c.name, Modifier.padding(start = 20.dp, top = 22.dp, bottom = 8.dp), style = T.Item) }
                item {
                    PlainCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(0.dp)) {
                        ls.forEachIndexed { i, l -> LibRow(l); if (i < ls.lastIndex) Divider(92.dp) }
                    }
                }
            }
        }
        item { BottomSpacer() }
    }
}

@Composable
private fun LibRow(l: Lecture) {
    val date = remember(l.date) { SimpleDateFormat("M月d日", Locale.CHINA).format(Date(l.date)) }
    val tex = remember(l.notes) { Regex("\\$\\$?([^$]+)\\$\\$?").find(l.notes)?.groupValues?.get(1) }
    Row(Modifier.fillMaxWidth().clickable { Nav.push(if (l.status == 1) Route.Generating(l.id) else Route.Note(l.id)) }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(64.dp, 44.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF5F5F7)), contentAlignment = Alignment.Center) {
            if (tex != null) MathBlock(tex.trim(), T.Body.copy(fontSize = 9.sp), scale = 1f) else Icon("note", C.Sec, 20.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            BasicText(l.title, style = T.Item, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText("$date · ${l.segments.count { it.exam }} 个考点" + if (l.status == 1) " · 生成中" else if (l.status == 3) " · 待重试" else "", style = T.Sub)
        }
        Icon("chev", C.Ter, 14.dp)
    }
}

// =================== 问学长（底部弹层） ===================
@Composable
fun AnswerSheetContent(s: Sheet.Answer) {
    var ans by remember(s) { mutableStateOf<String?>(null) }
    var err by remember(s) { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(s) {
        val l = Store.lecture(s.lectureId)
        if (l == null) { err = "找不到这节课"; return@LaunchedEffect }
        if (!app.xuezhang.net.Api.llmReady()) { err = "先在「我的」里配置大模型 API Key，学长才能回答"; return@LaunchedEffect }
        val ctxText = buildString {
            append("课程：${Store.course(l.courseId)?.name.orEmpty()}\n标题：${l.title}\n")
            l.sections.forEach { append("## ${it.title}\n${it.body.replace(Regex("```[\\s\\S]*?```"), "")}\n") }
            if (l.sections.isEmpty()) append(l.notes)
        }.take(6000)
        try {
            ans = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                app.xuezhang.net.Api.chat(app.xuezhang.engine.Prompts.ASK, "笔记：\n$ctxText\n\n问题：${s.question}", json = false)
            }
        } catch (e: Exception) { err = e.message ?: "回答失败" }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon("sparkle", C.Blue, 18.dp, fill = true); Spacer(Modifier.width(8.dp))
        BasicText("问学长", style = T.Title)
    }
    Spacer(Modifier.height(8.dp))
    BasicText(s.question, Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Blue.copy(alpha = 0.08f)).padding(12.dp), style = T.Label.copy(color = C.Blue))
    Spacer(Modifier.height(12.dp))
    val sc = androidx.compose.foundation.rememberScrollState()
    Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(sc)) {
        when {
            err != null -> BasicText(err!!, style = T.Body.copy(fontSize = 14.5.sp, color = C.HlOrange))
            ans == null -> BasicText("学长在翻笔记…", style = T.Sub)
            else -> RichText(ans!!, onMath = { Nav.sheet.value = Sheet.Formula(it, ans!!) })
        }
    }
    Spacer(Modifier.height(12.dp))
    Btn("知道了", { Nav.sheet.value = null }, Modifier.fillMaxWidth(), icon = "check", tint = C.Blue)
}

// =================== 搜索 ===================
@Composable
fun SearchScreen() {
    var q by remember { mutableStateOf("") }
    data class Hit(val l: Lecture, val where: String, val text: String)
    val hits = remember(q, Store.lectures.size) {
        if (q.isBlank()) emptyList() else Store.lectures.flatMap { l ->
            val out = mutableListOf<Hit>()
            l.notes.split(Regex("\n\\s*\n")).filter { it.contains(q, true) }.forEach { out.add(Hit(l, "笔记", it)) }
            l.segments.filter { it.voice.contains(q, true) || it.senior.contains(q, true) }.forEach { out.add(Hit(l, if (it.page > 0) "P.${it.page}" else Live.fmt(it.startMs), it.voice)) }
            l.cards.filter { it.q.contains(q, true) || it.a.contains(q, true) }.forEach { out.add(Hit(l, "复习卡", it.q + "\n" + it.a)) }
            out
        }.take(60)
    }
    GlassScaffold(content = {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item { TopInset(); LargeHeader(null, "搜索"); Spacer(Modifier.height(56.dp)) }
            if (q.isNotBlank()) item { BasicText("找到 ${hits.size} 处", Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = T.Sub) }
            hits.forEach { h ->
                item {
                    PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), onClick = { Nav.push(Route.Note(h.l.id)) }) {
                        BasicText("${h.l.title} · ${h.where}", style = T.Item.copy(fontSize = 14.sp, color = C.Blue))
                        Spacer(Modifier.height(4.dp))
                        RichText(h.text.take(300), style = T.Body.copy(fontSize = 14.5.sp))
                    }
                }
            }
        }
    }, overlay = { b ->
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 6.dp)) {
            GlassCircle(b, "back", { Nav.pop() })
            Spacer(Modifier.height(64.dp))
            Row(Modifier.fillMaxWidth().height(46.dp).glass(b, Capsule(), surface = Color.White.copy(alpha = 0.6f)).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon("search", C.Sec, 18.dp); Spacer(Modifier.width(8.dp))
                Field(q, { q = it }, "梯度、拉格朗日、老师原话…", Modifier.weight(1f))
            }
        }
    })
}
