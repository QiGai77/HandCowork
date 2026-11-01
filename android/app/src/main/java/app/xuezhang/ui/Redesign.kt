package app.xuezhang.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.xuezhang.data.Course
import app.xuezhang.data.Lecture
import app.xuezhang.data.Store
import app.xuezhang.engine.AiProgress
import app.xuezhang.engine.AppJobs
import app.xuezhang.engine.Live
import app.xuezhang.engine.Log
import app.xuezhang.engine.MindMap
import app.xuezhang.engine.NavOrder
import app.xuezhang.engine.Schedule
import app.xuezhang.net.Api
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ============================================================================
// 重构版 v0.0.1Ex：新增 / 重做的页面。编号对应 design/redesign-v0.0.1Ex/screens/NN.png
// ============================================================================

fun courseColor(c: Course?): Color = c?.let { C.course[it.colorIdx % C.course.size] } ?: C.FillGray

/** 页面大标题（衬线），可带左侧返回、右侧按钮。 */
@Composable
fun SerifHeader(title: String, sub: String? = null, modifier: Modifier = Modifier, right: @Composable RowScope.() -> Unit = {}) {
    Column(modifier.padding(start = 20.dp, end = 16.dp, top = 40.dp, bottom = 6.dp)) {
        if (sub != null) BasicText(sub, style = T.Sub)
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(title, Modifier.weight(1f), style = T.Large.copy(fontSize = 36.sp, lineHeight = 44.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            right()
        }
    }
}

@Composable
fun TopBar(b: com.kyant.backdrop.Backdrop, title: String, right: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassCircle(b, "back", { Nav.pop() })
        BasicText(title, Modifier.weight(1f).padding(horizontal = 10.dp), style = T.Item.copy(fontSize = 17.sp, textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
        right()
        if (true) Spacer(Modifier.width(0.dp))
    }
}

@Composable
fun JadeButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: String? = null, color: Color = C.Jade, enabled: Boolean = true) {
    Row(modifier.height(54.dp).shadow(if (enabled) 10.dp else 0.dp, Capsule(), ambientColor = color.copy(alpha = 0.4f), spotColor = color.copy(alpha = 0.4f))
        .clip(Capsule()).background(if (enabled) Brush.verticalGradient(listOf(color.copy(alpha = 0.92f), color)) else Brush.verticalGradient(listOf(C.Ter, C.Ter)))
        .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon != null) { Icon(icon, Color.White, 19.dp, 2.2f); Spacer(Modifier.width(8.dp)) }
        BasicText(text, style = T.Btn.copy(color = Color.White, fontSize = 16.sp))
    }
}

// ============================ 01 课程表（首页） ============================
@Composable
fun TimetableHome() {
    val cal = Schedule.clock()
    val today = Schedule.isoWeekday(cal)
    val nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    val courses = Store.courses.toList()
    val days = remember(courses, today) { Schedule.gridDays(courses, today) }
    val rows = remember(courses) { Schedule.gridRows(courses) }
    var focus by remember { mutableIntStateOf(today) }
    val dateStr = remember(cal.get(java.util.Calendar.DAY_OF_YEAR)) { SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(cal.time) }
    val b = LocalBg.current!!
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            TopInset()
            SerifHeader("课程表", "$dateStr · ${semesterLabel(cal)}") {
                Row(Modifier.height(42.dp).glass(b, Capsule(), surface = Color.White.copy(alpha = 0.72f)).clip(Capsule())
                    .clickable { Nav.push(Route.ImportTimetable) }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon("camera", C.Ink, 17.dp, 2f); Spacer(Modifier.width(6.dp)); BasicText("拍照导入", style = T.Btn.copy(fontSize = 14.sp))
                }
                Spacer(Modifier.width(8.dp))
                GlassCircle(b, "plus", { Nav.draft.value = null; Nav.push(Route.EditCourse(null)) }, size = 42.dp)
            }
        }
        item {
            // 星期选择条（液态玻璃胶囊），点一下高亮那一列
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(46.dp).glass(b, Capsule(), surface = Color.White.copy(alpha = 0.6f)).padding(4.dp)) {
                (1..7).forEach { d ->
                    val on = d == focus
                    Box(Modifier.weight(1f).fillMaxHeight().clip(Capsule()).background(if (on) Color.White else Color.Transparent)
                        .clickable { focus = d }, contentAlignment = Alignment.Center) {
                        BasicText("一二三四五六日"[d - 1].toString(), style = T.Btn.copy(fontSize = 15.sp,
                            color = when { d == today -> C.Red; on -> C.Jade; else -> C.Ink }, fontWeight = if (on || d == today) FontWeight.Bold else FontWeight.Medium))
                    }
                }
            }
        }
        if (Live.running.value) item { LiveBanner() }
        item {
            val rowH = 58.dp; val timeW = 52.dp
            PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), radius = 26.dp, padding = PaddingValues(start = 4.dp, end = 6.dp, top = 8.dp, bottom = 10.dp)) {
                // 表头
                Row(Modifier.fillMaxWidth().height(30.dp)) {
                    Spacer(Modifier.width(timeW))
                    days.forEach { d ->
                        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(if (d == focus) C.JadeSoft else Color.Transparent), contentAlignment = Alignment.Center) {
                            BasicText("一二三四五六日"[d - 1].toString(), style = T.Btn.copy(fontSize = 13.5.sp, color = if (d == today) C.Red else C.Ink))
                        }
                    }
                }
                BoxWithConstraints(Modifier.fillMaxWidth().height(rowH * rows)) {
                    val colW = (maxWidth - timeW) / days.size
                    // 网格线 + 节次
                    Canvas(Modifier.fillMaxSize()) {
                        val rh = rowH.toPx(); val tw = timeW.toPx(); val cw = colW.toPx()
                        for (r in 0..rows) drawLine(Color(0x12000000), Offset(0f, r * rh), Offset(size.width, r * rh), 1f)
                        for (c in 0..days.size) drawLine(Color(0x0D000000), Offset(tw + c * cw, 0f), Offset(tw + c * cw, size.height), 1f)
                    }
                    Column(Modifier.width(timeW)) {
                        (1..rows).forEach { r ->
                            Column(Modifier.height(rowH).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                BasicText("第${r}节", style = T.Btn.copy(fontSize = 11.5.sp))
                                Schedule.SECTIONS.getOrNull(r - 1)?.let { BasicText(Schedule.hm(it.first), style = T.Cap.copy(fontSize = 9.5.sp)) }
                            }
                        }
                    }
                    // 课程块
                    courses.forEach { c ->
                        c.slots.forEach { sl ->
                            val col = days.indexOf(sl.weekday)
                            if (col >= 0 && sl.start in 1..rows) {
                                val end = maxOf(sl.start, sl.end).coerceAtMost(rows)
                                val running = sl.weekday == today && (Schedule.startMin(sl) ?: 9999) <= nowMin && nowMin <= (Schedule.endMin(sl) ?: -1)
                                val base = courseColor(c)
                                Box(Modifier.offset(x = timeW + colW * col + 2.dp, y = rowH * (sl.start - 1) + 2.dp).size(colW - 4.dp, rowH * (end - sl.start + 1) - 4.dp)
                                    .shadow(6.dp, RoundedCornerShape(12.dp), ambientColor = base.copy(alpha = 0.5f), spotColor = base.copy(alpha = 0.5f))
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Brush.verticalGradient(listOf(base.copy(alpha = 0.78f), base.copy(alpha = 0.95f))))
                                    .border(if (running) 2.dp else 0.6.dp, if (running) Color.White else Color.White.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                    .clickable { Nav.push(Route.CourseDetail(c.id)) }
                                    .padding(4.dp)) {
                                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        BasicText(c.name, style = TextStyle(Color.White, 11.5.sp, FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 14.sp), maxLines = 4, overflow = TextOverflow.Ellipsis)
                                        val room = sl.room.ifBlank { c.room }
                                        if (room.isNotBlank()) BasicText(room, style = TextStyle(Color.White.copy(alpha = 0.85f), 9.5.sp, textAlign = TextAlign.Center), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (running) BasicText("正在上", Modifier.align(Alignment.TopEnd).clip(Capsule()).background(Color.White.copy(alpha = 0.9f)).padding(horizontal = 4.dp, vertical = 1.dp),
                                        style = TextStyle(base, 8.5.sp, FontWeight.Bold))
                                }
                            }
                        }
                    }
                    // now 线
                    Schedule.gridPos(nowMin)?.let { pos ->
                        val y = rowH * pos
                        Box(Modifier.offset(y = y - 1.dp).fillMaxWidth().height(1.6.dp).background(C.Red.copy(alpha = 0.85f)).zIndex(5f))
                        BasicText("now", Modifier.align(Alignment.TopEnd).offset(y = y - 15.dp).zIndex(6f), style = T.Cap.copy(color = C.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
        if (courses.isEmpty()) item { EmptyHint("还没有课程", "点「拍照导入」拍一张课程表，或点 + 新建") }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(48.dp)
                .glass(b, Capsule(), surface = Color.White.copy(alpha = 0.6f)).clip(Capsule())
                .clickable { Nav.push(Route.Courses) }.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon("list", C.Ink, 17.dp); Spacer(Modifier.width(10.dp))
                BasicText("全部课程", Modifier.weight(1f), style = T.Btn)
                BasicText("共 ${courses.size} 门", style = T.Sub); Spacer(Modifier.width(6.dp)); Icon("chev", C.Sec, 14.dp)
            }
        }
        item { BottomSpacer() }
    }
}

// ============================ 11 下课 · 简单/详细 ============================
@Composable
fun EndClassScreen(id: String) {
    val l = Store.lecture(id) ?: run { Nav.pop(); return }
    val c = Store.course(l.courseId)
    var depth by remember { mutableIntStateOf(Store.settings.value.noteDepth) }
    var cards by remember { mutableStateOf(true) }
    var map by remember { mutableStateOf(Api.isGpt()) }
    GlassScaffold(content = {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.statusBars).padding(bottom = 40.dp)) {
            Spacer(Modifier.height(58.dp))
            // 上课页的课件预览（淡化），下方是下课面板
            val sp = c?.slidePath.orEmpty()
            if (sp.isNotBlank() && java.io.File(sp).exists()) {
                PlainCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth().graphicsLayer { alpha = 0.55f }, radius = 22.dp, padding = PaddingValues(8.dp)) {
                    SlideThumb(sp, l.segments.lastOrNull { it.page > 0 }?.page ?: 1, widthPx = 900)
                }
            }
            PlainCard(Modifier.padding(horizontal = 12.dp, vertical = 12.dp).fillMaxWidth(), radius = 34.dp, padding = PaddingValues(20.dp)) {
                Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 5.dp).clip(Capsule()).background(Color(0x22000000)))
                Spacer(Modifier.height(12.dp))
                BasicText("下课啦", style = T.Large.copy(fontSize = 34.sp))
                BasicText(listOfNotNull(c?.name, l.title).joinToString(" · "), style = T.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                PlainCard(Modifier.fillMaxWidth(), radius = 20.dp, padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            BasicText("录制 ${Live.fmt(l.durationMs)} · ${l.segments.size} 段", style = T.Btn)
                            BasicText("跳过闲聊 ${l.segments.count { it.offTopic }} 段 · 考点 ${l.segments.count { it.exam }} 处 · 课件 ${c?.slides?.size ?: 0} 页", style = T.Sub)
                        }
                        Waveform(List(22) { i -> (0.3f + 0.7f * kotlin.math.abs(kotlin.math.sin(i * 1.7f + l.durationMs % 7))) }, C.Ink.copy(alpha = 0.55f), Modifier.size(88.dp, 34.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                BasicText("笔记详略", style = T.Item)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(Triple(0, "简单", "考前速览 · 约 1 分钟"), Triple(1, "详细", "第一次学 · 约 3 分钟")).forEach { (v, t, sub) ->
                        val on = depth == v
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = if (on) 0.95f else 0.6f))
                            .border(if (on) 2.dp else 0.6.dp, if (on) C.Jade else Color(0x14000000), RoundedCornerShape(20.dp))
                            .clickable { depth = v }.padding(vertical = 14.dp, horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            BasicText(t, style = T.Head.copy(color = if (on) C.JadeDeep else C.Ink))
                            BasicText(sub, style = T.Cap)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText("生成复习卡", Modifier.weight(1f), style = T.Label); Toggle(cards) { cards = it }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        BasicText("生成 AI 导图", style = T.Label)
                        if (!Api.isGpt()) BasicText("当前服务商不能画图，会生成可拖动概念图", style = T.Cap.copy(fontSize = 11.sp))
                    }
                    Toggle(map && Api.isGpt()) { map = it }
                }
                Spacer(Modifier.height(18.dp))
                JadeButton("生成笔记", {
                    Live.genCards = cards; Live.autoMindMap = map
                    Store.updateSettings(Store.settings.value.copy(noteDepth = depth))
                    Live.ended.value = null
                    Live.regenerate(id, depth)
                    Nav.replace(Route.Generating(id))
                }, Modifier.fillMaxWidth(), icon = "wand")
                BasicText("稍后再说", Modifier.fillMaxWidth().clip(Capsule()).clickable { Live.ended.value = null; Nav.tabTo(2) }.padding(14.dp),
                    style = T.Btn.copy(color = C.Sec, textAlign = TextAlign.Center))
            }
        }
    }, overlay = { b -> BackBar(b) })
}

// ============================ 13/14 笔记 · 书架 / 列表 ============================
@Composable
fun NoteViewToggle(modifier: Modifier = Modifier) {
    val v = Store.settings.value.noteView
    LiquidSegmented(items = listOf("书架", "列表"), selected = v, onSelect = { Store.updateSettings(Store.settings.value.copy(noteView = it)) },
        modifier = modifier.width(132.dp), height = 40.dp)
}

@Composable
fun CourseFilterChips(selected: String?, onPick: (String?) -> Unit) {
    val counts = Store.lectures.groupingBy { it.courseId }.eachCount()
    LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            BasicText("全部", Modifier.clip(Capsule()).background(if (selected == null) C.Ink else Color.White.copy(alpha = 0.7f)).clickable { onPick(null) }
                .padding(horizontal = 14.dp, vertical = 7.dp), style = T.Btn.copy(fontSize = 13.5.sp, color = if (selected == null) Color.White else C.Ink))
        }
        items(Store.courses.filter { (counts[it.id] ?: 0) > 0 }, key = { it.id }) { c ->
            val on = selected == c.id; val col = courseColor(c)
            Row(Modifier.clip(Capsule()).background(if (on) col else Color.White.copy(alpha = 0.7f)).clickable { onPick(if (on) null else c.id) }
                .padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(Capsule()).background(if (on) Color.White else col)); Spacer(Modifier.width(6.dp))
                BasicText(c.name, style = T.Btn.copy(fontSize = 13.5.sp, color = if (on) Color.White else C.Ink), maxLines = 1)
                Spacer(Modifier.width(4.dp)); BasicText("${counts[c.id] ?: 0}", style = T.Cap.copy(fontSize = 10.5.sp, color = if (on) Color.White.copy(alpha = 0.8f) else C.Sec))
            }
        }
    }
}

@Composable
fun MiniBook(c: Course?, modifier: Modifier = Modifier) {
    val base = courseColor(c)
    Box(modifier.size(52.dp, 64.dp).shadow(5.dp, RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp, topStart = 2.dp, bottomStart = 2.dp))
        .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp, topStart = 2.dp, bottomStart = 2.dp))
        .background(Brush.horizontalGradient(listOf(base.copy(alpha = 0.75f), base, base.copy(alpha = 0.9f))))) {
        Box(Modifier.padding(start = 5.dp).width(1.5.dp).fillMaxHeight().background(Color(0x55FFFFFF)))
        Box(Modifier.align(Alignment.Center).size(22.dp, 16.dp).border(1.dp, Color(0xCCE8D9A8), RoundedCornerShape(3.dp)))
    }
}

@Composable
fun LectureListRow(l: Lecture) {
    val c = Store.course(l.courseId)
    val date = remember(l.date) { SimpleDateFormat("MMM d", Locale.ENGLISH).format(Date(l.date)) }
    val job = aiJob("notes:${l.id}")
    PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth(), radius = 20.dp, padding = PaddingValues(12.dp),
        onClick = { Nav.push(Route.Book(l.id)) }) {
        Row {
            MiniBook(c)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(l.title, Modifier.weight(1f), style = T.Item, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText(date, style = T.Cap)
                }
                val summary = (l.sections.firstOrNull()?.body ?: l.notes).replace(Regex("```[\\s\\S]*?```"), "").replace(Regex("\\[[a-z]:([^\\]]*)]"), "$1").replace(Regex("[*$#\\n]+"), " ").trim()
                BasicText((if (Store.settings.value.noteDepth == 0) "简单总结：" else "详细总解：") + summary.ifBlank { if (l.status == 1) "生成中…" else "还没生成笔记" },
                    style = T.Sub.copy(fontSize = 12.5.sp, lineHeight = 18.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (job != null && !job.finished.value) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText("生成中 ${(job.fraction.floatValue * 100).toInt()}%", style = T.Cap.copy(color = C.Jade))
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f).height(5.dp).clip(Capsule()).background(C.Fill)) {
                            Box(Modifier.fillMaxWidth(job.fraction.floatValue.coerceIn(0.02f, 1f)).fillMaxHeight().background(C.Jade))
                        }
                    }
                } else if (l.cards.isNotEmpty() || l.segments.any { it.exam }) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (l.cards.isNotEmpty()) Tag("${l.cards.size} 张复习卡", C.Ink)
                        val ex = l.segments.count { it.exam }
                        if (ex > 0) Tag("★ $ex 考点", C.HlRed)
                    }
                }
            }
        }
    }
}

// ============================ 15 翻书阅读：底部拖动跳页 ============================
object Scrub {
    /** 拖动位置（0..1）→ 页码（0 起）。 */
    fun pageAt(fraction: Float, total: Int): Int = if (total <= 1) 0 else Math.round(fraction.coerceIn(0f, 1f) * (total - 1))
    /** 页码 → 拖块位置（0..1）。 */
    fun fractionOf(page: Int, total: Int): Float = if (total <= 1) 0f else page.coerceIn(0, total - 1).toFloat() / (total - 1)
}

/**
 * 可拖动的底部进度条：拖动时上方冒出缩略气泡（第 x 节 · p/总页），松手跳到那一页；点按也能直接跳。
 */
@Composable
fun PageScrubber(page: Int, total: Int, label: (Int) -> String, thumb: @Composable (Int) -> Unit, modifier: Modifier = Modifier, onSeek: (Int) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var frac by remember { mutableFloatStateOf(Scrub.fractionOf(page, total)) }
    LaunchedEffect(page, total) { if (!dragging) frac = Scrub.fractionOf(page, total) }
    val shown by animateFloatAsState(frac, label = "scrub")
    val b = LocalBg.current
    BoxWithConstraints(modifier.fillMaxWidth().height(if (dragging) 150.dp else 44.dp)) {
        val w = maxWidth
        val target = Scrub.pageAt(frac, total)
        if (dragging) {
            val x = (w - 20.dp) * frac + 10.dp
            Column(Modifier.offset(x = (x - 60.dp).coerceIn(0.dp, w - 120.dp)).width(120.dp).align(Alignment.TopStart)
                .shadow(10.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(66.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFFFFCF5))) { thumb(target) }
                Spacer(Modifier.height(4.dp))
                BasicText(label(target), style = T.Cap.copy(fontSize = 11.sp, color = C.Ink), maxLines = 1)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(40.dp)
            .then(if (b != null) Modifier.glass(b, Capsule(), surface = Color.White.copy(alpha = 0.7f)) else Modifier.clip(Capsule()).background(Color.White.copy(alpha = 0.8f)))
            .pointerInput(total) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragging = true; frac = ((o.x - 10.dp.toPx()) / (size.width - 20.dp.toPx())).coerceIn(0f, 1f) },
                    onDragEnd = { dragging = false; onSeek(Scrub.pageAt(frac, total)) },
                    onDragCancel = { dragging = false },
                ) { ch, dx -> ch.consume(); frac = (frac + dx / (size.width - 20.dp.toPx())).coerceIn(0f, 1f) }
            }
            .pointerInput(total) {
                detectTapGestures { o -> val f = ((o.x - 10.dp.toPx()) / (size.width - 20.dp.toPx())).coerceIn(0f, 1f); frac = f; onSeek(Scrub.pageAt(f, total)) }
            }) {
            Canvas(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                val n = total.coerceAtMost(60)
                for (i in 0 until n) {
                    val x = size.width * i / (n - 1).coerceAtLeast(1)
                    val tall = i % 5 == 0
                    drawLine(Color(0x55000000), Offset(x, size.height * (if (tall) 0.3f else 0.4f)), Offset(x, size.height * (if (tall) 0.7f else 0.6f)), 1.4f)
                }
                val kx = size.width * shown
                drawCircle(Color(0x33000000), 11.dp.toPx(), Offset(kx, size.height / 2 + 1.5f))
                drawCircle(C.Red, 10.dp.toPx(), Offset(kx, size.height / 2))
                drawCircle(Color.White, 4.dp.toPx(), Offset(kx, size.height / 2))
            }
        }
    }
}

@Composable
fun DockItem(icon: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, C.Ink, 21.dp); BasicText(label, style = T.Cap.copy(fontSize = 11.sp, color = C.Ink))
    }
}

// ============================ 17 概念图 ============================
@Composable
fun MindMapScreen(id: String) {
    val l = Store.lecture(id) ?: run { Nav.pop(); return }
    val gpt = Api.isGpt()
    var mode by remember { mutableIntStateOf(if (gpt) 0 else 1) }
    val ver = MindMap.version.intValue
    val file = MindMap.file(id)
    val img = remember(id, ver) { runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull() }
    val pending = remember(id, ver) { MindMap.pending(id) }
    val job = aiJob("mindmap:$id")
    val busy = job != null && !job.finished.value
    var resetKey by remember { mutableIntStateOf(0) }
    fun gen() { if (!busy) AppJobs.launch { runCatching { MindMap.generate(id) }.onFailure { Log.err("概念图", it) } } }
    GlassScaffold(content = {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(top = 60.dp)) {
            LiquidSegmented(items = listOf("AI 导图", "可拖动"), selected = mode, onSelect = { mode = it }, modifier = Modifier.align(Alignment.CenterHorizontally).width(220.dp), height = 42.dp)
            Spacer(Modifier.height(10.dp))
            if (mode == 1) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    key(resetKey) { ConceptMap(l) }
                    BasicText("长按节点拖动 · 位置自动保存", Modifier.fillMaxWidth().padding(8.dp), style = T.Cap.copy(textAlign = TextAlign.Center))
                    BasicText("重新排布", Modifier.align(Alignment.CenterHorizontally).clip(Capsule()).background(Color.White.copy(alpha = 0.8f)).clickable {
                        runCatching { java.io.File(Store.dir, "mappos_$id.json").delete() }; resetKey++
                    }.padding(horizontal = 16.dp, vertical = 8.dp), style = T.Btn.copy(color = C.Jade))
                    BottomSpacer()
                }
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    if (!gpt) PlainCard(Modifier.fillMaxWidth()) { BasicText("AI 导图需要 OpenAI / ChatGPT 账号（gpt-image）。当前服务商请用「可拖动」。", style = T.Sub) }
                    if (busy) PlainCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) { AiProgressBar(job) }
                    if (pending.isNotEmpty()) {
                        PlainCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            BasicText("候选导图 · ${pending.size} 张（灵动岛里也能选）", style = T.Item)
                            Spacer(Modifier.height(8.dp))
                            pending.forEachIndexed { i, f ->
                                val bm = remember(f.path, ver) { runCatching { app.xuezhang.engine.ImgUtil.thumb(f.path, 1200)?.asImageBitmap() }.getOrNull() }
                                bm?.let { Image(it, "候选 ${i + 1}", Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.FillWidth) }
                                Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Btn("应用这张", { Island.candidates.value = null; MindMap.apply(id, i) }, Modifier.weight(1f), icon = "check", tint = C.Jade)
                                }
                            }
                            BasicText("全部丢弃", Modifier.clickable { Island.candidates.value = null; MindMap.discard(id) }.padding(6.dp), style = T.Btn.copy(color = C.Red))
                        }
                    }
                    PlainCard(Modifier.fillMaxWidth().padding(vertical = 6.dp), radius = 26.dp, padding = PaddingValues(10.dp)) {
                        if (img != null) ZoomImage(img)
                        else BasicText(if (l.nodes.isEmpty()) "这节课还没有概念节点（先生成笔记）" else "用 ${MindMap.MODEL} 把这节课的概念树画成一张导图", Modifier.fillMaxWidth().padding(30.dp), style = T.Sub.copy(textAlign = TextAlign.Center))
                    }
                    if (gpt && l.nodes.isNotEmpty()) JadeButton(if (busy) "生成中…" else if (img == null) "生成导图" else "重新生成", { gen() }, Modifier.fillMaxWidth().padding(vertical = 8.dp), icon = "refresh", enabled = !busy)
                    BottomSpacer()
                }
            }
        }
    }, overlay = { b -> TopBar(b, listOfNotNull(Store.course(l.courseId)?.name, l.title).joinToString(" · ")) })
}

@Composable
private fun ZoomImage(img: androidx.compose.ui.graphics.ImageBitmap) {
    var scale by remember { mutableFloatStateOf(1f) }
    var off by remember { mutableStateOf(Offset.Zero) }
    Column {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 4f); off = if (scale == 1f) Offset.Zero else off + pan }
        }) {
            Image(img, "概念图", Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale; translationX = off.x; translationY = off.y }, contentScale = ContentScale.FillWidth)
        }
        Row(Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally).clip(Capsule()).background(Color.White.copy(alpha = 0.85f)).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("＋", Modifier.clickable { scale = (scale * 1.25f).coerceAtMost(4f) }.padding(horizontal = 12.dp, vertical = 6.dp), style = T.Btn)
            BasicText("－", Modifier.clickable { scale = (scale / 1.25f).coerceAtLeast(1f); if (scale == 1f) off = Offset.Zero }.padding(horizontal = 12.dp, vertical = 6.dp), style = T.Btn)
            BasicText("${(scale * 100).toInt()}%", Modifier.padding(horizontal = 10.dp), style = T.Cap)
        }
    }
}

// ============================ 18 复习卡 ============================
@Composable
fun CardsScreen(id: String) {
    val l = Store.lecture(id) ?: run { Nav.pop(); return }
    GlassScaffold(content = {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(top = 64.dp).verticalScroll(rememberScrollState())) {
            CardDeck(l)
            if (l.cards.isEmpty() && l.status == 2) {
                Btn("从笔记补几张复习卡", {
                    val cur = Store.lecture(id) ?: return@Btn
                    val more = app.xuezhang.engine.CardGen.local(cur.sections, cur.mnemonics, cur.nodes, cur.cards)
                    Store.upsertLecture(cur.copy(cards = more)); Island.show("补了 ${more.size} 张复习卡", Island.Kind.Done)
                }, Modifier.padding(16.dp).fillMaxWidth(), icon = "plus", tint = C.Jade)
            }
            BottomSpacer()
        }
    }, overlay = { b -> TopBar(b, "复习卡 · ${Store.course(l.courseId)?.name ?: l.title}") })
}

// ============================ 19 问学长（流式，基于笔记 + 课件） ============================
object AskState {
    data class Msg(val me: Boolean, val text: String)
    val threads = mutableStateMapOf<String, List<Msg>>()
    val streaming = mutableStateMapOf<String, String>()
    val busy = mutableStateMapOf<String, Boolean>()

    fun context(l: Lecture): String = buildString {
        val c = Store.course(l.courseId)
        append("课程：${c?.name.orEmpty()}\n标题：${l.title}\n")
        l.sections.forEach { append("## ${it.title}${if (it.pages.isNotEmpty()) "（课件第 ${it.pages.joinToString(",")} 页）" else ""}\n${it.body.replace(Regex("```[\\s\\S]*?```"), "")}\n") }
        if (l.sections.isEmpty()) append(l.notes)
        c?.slides?.takeIf { it.isNotEmpty() }?.let { s -> append("\n课件：\n"); s.take(40).forEach { append("P.${it.page} ${it.title} ${it.text.take(120)}\n") } }
    }.take(9000)

    fun ask(id: String, q: String) {
        if (busy[id] == true || q.isBlank()) return
        val l = Store.lecture(id) ?: return
        val hist = threads[id].orEmpty()
        threads[id] = hist + Msg(true, q)
        busy[id] = true; streaming[id] = ""
        AppJobs.launch {
            val job = AiProgress.start("ask:$id", "问学长", origin = Route.Ask(id))
            try {
                if (!Api.llmReady()) throw app.xuezhang.net.ApiException("先在「我的」里配置大模型，学长才能回答")
                job.phase("学长在翻笔记和课件", 0.95f, 15_000)
                val convo = hist.takeLast(6).joinToString("\n") { (if (it.me) "学生：" else "学长：") + it.text.take(600) }
                val r = withContext(Dispatchers.IO) {
                    Api.gen(app.xuezhang.engine.Prompts.get("ask") + "\n回答时如果用到课件内容，注明「课件第 N 页」。", listOf(Api.Part("笔记与课件：\n${context(l)}\n\n" +
                        (if (convo.isNotBlank()) "之前的对话：\n$convo\n\n" else "") + "问题：$q")), Store.settings.value.llmModel, json = false) { acc ->
                        streaming[id] = acc; job.stream(acc.length, 300, "已写 ${acc.length} 字")
                    }
                }
                threads[id] = threads[id].orEmpty() + Msg(false, r.ifBlank { streaming[id].orEmpty() })
                AiProgress.finish(job, true, "学长回答好了 · 轻点查看", island = Nav.stack.lastOrNull() != Route.Ask(id))
            } catch (e: Exception) {
                threads[id] = threads[id].orEmpty() + Msg(false, "（回答失败：${e.message ?: "未知错误"}）")
                AiProgress.finish(job, false, "问学长失败"); Log.err("问学长", e)
            } finally { busy[id] = false; streaming[id] = "" }
        }
    }
}

@Composable
fun AskScreen(id: String, q0: String) {
    val l = Store.lecture(id) ?: run { Nav.pop(); return }
    var input by remember { mutableStateOf("") }
    LaunchedEffect(q0) { if (q0.isNotBlank() && AskState.threads[id].isNullOrEmpty()) AskState.ask(id, q0) }
    val msgs = AskState.threads[id].orEmpty()
    val stream = AskState.streaming[id].orEmpty()
    val busy = AskState.busy[id] == true
    val list = rememberLazyListState()
    LaunchedEffect(msgs.size, stream.length / 80) { if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.size + 1) }
    GlassScaffold(content = {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 170.dp)) {
            item {
                TopInset(); Spacer(Modifier.height(40.dp))
                SerifHeader("问学长", null)
                Row(Modifier.padding(horizontal = 16.dp).clip(Capsule()).background(Color.White.copy(alpha = 0.75f)).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon("book", C.Ink, 16.dp); Spacer(Modifier.width(6.dp))
                    BasicText("基于《${l.title}》笔记 + 课件", style = T.Btn.copy(fontSize = 13.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(12.dp))
            }
            if (msgs.isEmpty() && !busy) item { EmptyHint("有什么不懂的？", "学长只根据这节课的笔记和课件回答") }
            itemsIndexed(msgs) { _, m ->
                if (m.me) Box(Modifier.fillMaxWidth().padding(start = 60.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), contentAlignment = Alignment.CenterEnd) {
                    BasicText(m.text, Modifier.clip(RoundedCornerShape(20.dp)).background(Brush.horizontalGradient(listOf(C.Jade.copy(alpha = 0.85f), C.Jade))).padding(horizontal = 16.dp, vertical = 10.dp),
                        style = T.Label.copy(color = Color.White))
                } else PlainCard(Modifier.padding(start = 16.dp, end = 40.dp, top = 6.dp, bottom = 6.dp).fillMaxWidth(), radius = 22.dp) {
                    RichText(m.text, onMath = { Nav.sheet.value = Sheet.Formula(it, m.text) })
                }
            }
            if (busy) item {
                PlainCard(Modifier.padding(start = 16.dp, end = 40.dp, top = 6.dp).fillMaxWidth(), radius = 22.dp) {
                    if (stream.isBlank()) aiJob("ask:$id")?.let { AiProgressBar(it, showTip = true) } else RichText("$stream ▍")
                }
            }
        }
    }, overlay = { b ->
        BackBar(b)
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).imePadding().padding(horizontal = 12.dp, vertical = 10.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                items(listOf("举个例子", "出一道题", "和考点有关吗", "再讲简单点")) { t ->
                    BasicText(t, Modifier.glass(b, Capsule(), surface = Color.White.copy(alpha = 0.75f)).clip(Capsule()).clickable(enabled = !busy) { AskState.ask(id, t) }
                        .padding(horizontal = 14.dp, vertical = 9.dp), style = T.Btn.copy(fontSize = 14.sp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().height(54.dp).glass(b, Capsule(), surface = Color.White.copy(alpha = 0.8f)).padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Field(input, { input = it }, "问点什么…", Modifier.weight(1f), style = T.Label)
                Box(Modifier.size(44.dp).clip(Capsule()).background(if (input.isNotBlank() && !busy) C.Red else C.Fill).clickable(enabled = input.isNotBlank() && !busy) {
                    AskState.ask(id, input.trim()); input = ""
                }, contentAlignment = Alignment.Center) { Icon("upload", if (input.isNotBlank() && !busy) Color.White else C.Sec, 20.dp, 2.2f) }
            }
        }
    })
}

// ============================ 20 分享导出 ============================
@Composable
fun ShareScreen(id: String) {
    val l = Store.lecture(id) ?: run { Nav.pop(); return }
    val ctx = LocalContext.current
    val fmts = app.xuezhang.engine.NoteExport.Fmt.values().toList()
    var fmt by remember { mutableStateOf(fmts.firstOrNull { it == app.xuezhang.engine.NoteExport.Fmt.IMG } ?: fmts.first()) }
    var withCards by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    GlassScaffold(content = {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.statusBars).padding(top = 60.dp, bottom = 40.dp)) {
            PlainCard(Modifier.padding(horizontal = 12.dp).fillMaxWidth(), radius = 34.dp, padding = PaddingValues(18.dp)) {
                BasicText("分享笔记", Modifier.fillMaxWidth(), style = T.Title.copy(textAlign = TextAlign.Center))
                Spacer(Modifier.height(12.dp))
                // 预览：纸张样式的长图缩略
                Column(Modifier.align(Alignment.CenterHorizontally).width(190.dp).shadow(8.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(Color(0xFFFFFCF5)).padding(12.dp)) {
                    BasicText(l.title, style = T.Item.copy(fontSize = 13.sp), maxLines = 2)
                    Spacer(Modifier.height(6.dp))
                    l.sections.take(3).forEach { s ->
                        BasicText(s.title, style = T.Btn.copy(fontSize = 10.sp), maxLines = 1)
                        BasicText(s.body.replace(Regex("```[\\s\\S]*?```"), "").replace(Regex("\\[[a-z]:([^\\]]*)]"), "$1").take(90), style = T.Cap.copy(fontSize = 8.5.sp, lineHeight = 11.sp), maxLines = 3)
                        Spacer(Modifier.height(4.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(16.dp).background(C.Ink)); Spacer(Modifier.width(6.dp)); BasicText("课堂学长", style = T.Cap.copy(fontSize = 9.sp)) }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    fmts.forEach { f ->
                        val on = f == fmt
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = if (on) 0.95f else 0.6f))
                            .border(if (on) 2.dp else 0.6.dp, if (on) C.Jade else Color(0x14000000), RoundedCornerShape(16.dp)).clickable { fmt = f }.padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(when (f) { app.xuezhang.engine.NoteExport.Fmt.IMG -> "image"; app.xuezhang.engine.NoteExport.Fmt.PDF -> "file"; app.xuezhang.engine.NoteExport.Fmt.MD -> "list"; else -> "filetext" }, if (on) C.Jade else C.Ink, 22.dp)
                            Spacer(Modifier.height(4.dp))
                            BasicText(f.label, style = T.Cap.copy(color = C.Ink, fontSize = 11.5.sp), maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { BasicText("包含复习卡", Modifier.weight(1f), style = T.Label); Toggle(withCards) { withCards = it } }
                Spacer(Modifier.height(18.dp))
                JadeButton(if (busy) "正在生成…" else "导出并分享", {
                    if (busy) return@JadeButton
                    busy = true
                    AppJobs.launch {
                        try {
                            val lec = (Store.lecture(id) ?: return@launch).let { if (withCards) it else it.copy(cards = emptyList()) }
                            val intent = withContext(Dispatchers.IO) { app.xuezhang.engine.NoteExport.export(ctx, lec, fmt) }
                            ctx.startActivity(intent)
                        } catch (e: Exception) { Island.show("导出失败：${e.message?.take(24)}", Island.Kind.Warn); Log.err("分享", e) } finally { busy = false }
                    }
                }, Modifier.fillMaxWidth(), icon = "share", enabled = !busy)
                BasicText("系统分享面板里可以选微信、QQ、保存到相册或文件", Modifier.fillMaxWidth().padding(top = 10.dp), style = T.Cap.copy(textAlign = TextAlign.Center))
            }
        }
    }, overlay = { b -> TopBar(b, l.title) })
}

// ============================ 22 我的 · 底部导航栏拖动排序 ============================
@Composable
fun NavOrderEditor() {
    val order = NavOrder.parse(Store.settings.value.navOrder)
    val rowH = 50.dp
    val rowPx = with(LocalDensity.current) { rowH.toPx() }
    var dragging by remember { mutableIntStateOf(-1) }
    var dy by remember { mutableFloatStateOf(0f) }
    PlainCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(vertical = 4.dp)) {
        Box(Modifier.fillMaxWidth().height(rowH * order.size)) {
            order.forEachIndexed { i, id ->
                val target = if (dragging >= 0) NavOrder.targetIndex(dragging, dy, rowPx, order.size) else -1
                // 其他行给拖动的行让位
                val shift = when {
                    dragging < 0 || i == dragging -> 0
                    dragging < target && i in (dragging + 1)..target -> -1
                    dragging > target && i in target until dragging -> 1
                    else -> 0
                }
                val y by animateFloatAsState(if (i == dragging) i * rowPx + dy else (i + shift) * rowPx, label = "nav$i")
                Row(Modifier.fillMaxWidth().height(rowH).graphicsLayer { translationY = y; scaleX = if (i == dragging) 1.03f else 1f; scaleY = scaleX }
                    .zIndex(if (i == dragging) 2f else 0f)
                    .then(if (i == dragging) Modifier.shadow(10.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Color.White) else Modifier)
                    .pointerInput(order) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragging = i; dy = 0f },
                            onDragEnd = {
                                val to = NavOrder.targetIndex(i, dy, rowPx, order.size)
                                if (to != i) {
                                    val n = NavOrder.move(order, i, to)
                                    Store.updateSettings(Store.settings.value.copy(navOrder = NavOrder.encode(n)))
                                    Island.show("「${NavOrder.LABELS[n.first()]}」是启动页", Island.Kind.Done)
                                }
                                dragging = -1; dy = 0f
                            },
                            onDragCancel = { dragging = -1; dy = 0f },
                        ) { ch, d -> ch.consume(); dy += d.y }
                    }
                    .padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon("list", C.Sec, 18.dp)
                    Spacer(Modifier.width(14.dp))
                    Icon(NavOrder.ICONS[id], C.Ink, 20.dp)
                    Spacer(Modifier.width(10.dp))
                    BasicText(NavOrder.LABELS[id], Modifier.weight(1f), style = T.Label)
                    if (i == 0) Tag("启动页", C.Jade)
                }
            }
        }
    }
    BasicText("长按拖动调整顺序 · 第一个即启动页", Modifier.fillMaxWidth().padding(top = 6.dp), style = T.Cap.copy(textAlign = TextAlign.Center))
}

// ============================ 23 模型与供应商 ============================
@Composable
fun ModelsScreen() {
    var kind by remember { mutableIntStateOf(1) }
    val s = Store.settings.value
    GlassScaffold(content = {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.statusBars).padding(top = 20.dp, bottom = 60.dp)) {
            SerifHeader("模型", "${Providers.nameOf(s.llmBase)} · ${if (Api.llmReady()) "已配置" else "未配置"}")
            if ((s.llmAuth == "chatgpt" && app.xuezhang.net.Auth.chatgpt.value != null) || (s.llmAuth == "claude" && app.xuezhang.net.Auth.claude.value != null)) {
                PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) { QuotaBars(s.llmAuth) }
            }
            LiquidSegmented(items = listOf("语音识别", "大模型 · 看图 · 画图"), selected = kind, onSelect = { kind = it }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), height = 42.dp)
            PlainCard(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth(), radius = 30.dp, padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
                key(kind) { ModelSheetContent(kind) }
            }
        }
    }, overlay = { b -> BackBar(b) })
}

// ============================ 26 Google 授权 ============================
@Composable
fun GoogleConnectScreen() {
    val ctx = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val acc = app.xuezhang.net.Auth.google.value
    GlassScaffold(content = {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.statusBars).padding(top = 20.dp, bottom = 60.dp)) {
            SerifHeader("云同步", null)
            PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), radius = 30.dp, padding = PaddingValues(22.dp)) {
                Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(70.dp).shadow(6.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Color(0xFFFFF6E6)), contentAlignment = Alignment.Center) { BasicText("学", style = T.Large.copy(fontSize = 34.sp)) }
                    BasicText("  ····  🔗  ····  ", style = T.Sub)
                    Box(Modifier.size(70.dp).shadow(6.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Color.White), contentAlignment = Alignment.Center) { DriveLogo(40.dp) }
                }
                Spacer(Modifier.height(16.dp))
                BasicText("连接 Google Drive", Modifier.fillMaxWidth(), style = T.Title.copy(textAlign = TextAlign.Center))
                BasicText("只访问课堂学长创建的文件（drive.file）", Modifier.fillMaxWidth(), style = T.Sub.copy(textAlign = TextAlign.Center))
                Spacer(Modifier.height(14.dp))
                listOf("备份课程与笔记", "上传录音与课件", "不会读取你的其他文件").forEach { t ->
                    Row(Modifier.padding(vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x0A000000)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(22.dp).clip(Capsule()).background(C.Jade), contentAlignment = Alignment.Center) { Icon("check", Color.White, 14.dp, 2.6f) }
                        Spacer(Modifier.width(10.dp)); BasicText(t, style = T.Label)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().height(54.dp).shadow(6.dp, Capsule()).clip(Capsule()).background(Color.White).clickable(enabled = !busy) {
                    if (!app.xuezhang.net.GDrive.configured()) { err = "还没有配置 Google OAuth 客户端：在「云同步」页填写客户端 ID 后再连接"; return@clickable }
                    busy = true; err = null
                    AppJobs.launch {
                        try {
                            withContext(Dispatchers.IO) { app.xuezhang.net.GDrive.login(ctx) }
                            Island.show("已连接 Google Drive", Island.Kind.Done)
                            Nav.replace(Route.Drive)
                        } catch (e: Exception) { err = e.message ?: "连接失败"; Log.err("Google Drive", e) } finally { busy = false }
                    }
                }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    BasicText("G", style = TextStyle(Color(0xFF4285F4), 20.sp, FontWeight.Bold)); Spacer(Modifier.width(10.dp))
                    BasicText(if (busy) "正在浏览器里授权…" else "使用 Google 账号继续", style = T.Btn.copy(fontSize = 16.sp))
                }
                err?.let { BasicText(it, Modifier.padding(top = 10.dp), style = T.Sub.copy(color = C.Red)) }
                if (!app.xuezhang.net.GDrive.configured()) BasicText("去填写客户端 ID", Modifier.padding(top = 8.dp).clickable { Nav.replace(Route.Drive) }, style = T.Btn.copy(color = C.Jade))
            }
            if (acc != null) PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), radius = 26.dp) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(44.dp).clip(Capsule()).background(C.Jade), contentAlignment = Alignment.Center) { Icon("check", Color.White, 26.dp, 3f) }
                    Spacer(Modifier.height(8.dp))
                    BasicText("已连接 ${acc.email.ifBlank { "Google 账号" }}", style = T.Item)
                    BasicText("返回云同步", Modifier.padding(top = 6.dp).clickable { Nav.replace(Route.Drive) }, style = T.Btn.copy(color = C.Jade))
                }
            }
        }
    }, overlay = { b -> BackBar(b) })
}

@Composable
fun DriveLogo(size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        fun tri(c: Color, vararg p: Float) = drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(p[0] * w, p[1] * h); lineTo(p[2] * w, p[3] * h); lineTo(p[4] * w, p[5] * h); lineTo(p[6] * w, p[7] * h); close() }, c)
        tri(Color(0xFF0F9D58), 0.33f, 0.08f, 0.0f, 0.66f, 0.17f, 0.93f, 0.5f, 0.36f)
        tri(Color(0xFFFFCD40), 0.33f, 0.08f, 0.67f, 0.08f, 1.0f, 0.66f, 0.66f, 0.66f)
        tri(Color(0xFF4285F4), 0.17f, 0.93f, 0.83f, 0.93f, 1.0f, 0.66f, 0.34f, 0.66f)
    }
}
