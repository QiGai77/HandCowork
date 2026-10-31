package app.xuezhang.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.xuezhang.data.Course
import app.xuezhang.data.Store
import app.xuezhang.engine.Live
import app.xuezhang.engine.SlideParser
import app.xuezhang.engine.Schedule
import androidx.compose.foundation.layout.aspectRatio
import com.kyant.shapes.Capsule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TopInset() = Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars).height(8.dp))

@Composable
fun LargeHeader(sub: String?, title: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 52.dp, bottom = 8.dp)) {
        if (sub != null) BasicText(sub, style = T.Sub)
        BasicText(title, style = T.Large)
    }
}

/** 开始上课：申请麦克风权限后开始录音。 */
@Composable
fun rememberStartClass(): (Course) -> Unit {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<Course?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val c = pending ?: return@rememberLauncherForActivityResult
        if (res[Manifest.permission.RECORD_AUDIO] == true) { Live.start(ctx, c); Nav.push(Route.Live) }
        else Island.show("需要麦克风权限才能听课", Island.Kind.Warn)
    }
    return { c ->
        when {
            Live.running.value -> Nav.push(Route.Live)
            Store.settings.value.asrKey.isBlank() || !app.xuezhang.net.Api.llmReady() -> {
                Island.show("先在「我的」里填好 API Key", Island.Kind.Warn); Nav.home(); Nav.tab.intValue = 3
            }
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                Live.start(ctx, c); Nav.push(Route.Live)
            }
            else -> {
                pending = c
                launcher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
            }
        }
    }
}

// =================== 首页：今天 ===================
@Composable
fun BetaPill(modifier: Modifier = Modifier) {
    BasicText("测试版", modifier.clip(RoundedCornerShape(10.dp)).background(C.Orange.copy(alpha = 0.15f)).padding(horizontal = 7.dp, vertical = 2.dp),
        style = T.Cap.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Orange))
}

@Composable
fun ImportPill(onClick: () -> Unit) {
    Row(Modifier.height(40.dp).glass(LocalBg.current!!, Capsule(), surface = Color.White.copy(alpha = 0.6f)).clip(Capsule())
        .clickable(onClick = onClick).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon("scan", C.Ink, 18.dp, 2.1f); Spacer(Modifier.width(6.dp)); BasicText("导入", style = T.Btn.copy(fontSize = 14.5.sp))
    }
}

@Composable
fun DarkPlus(onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(Capsule()).background(C.Ink.copy(alpha = 0.92f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon("plus", Color.White, 20.dp, 2.2f)
    }
}

@Composable
fun HomeTab() {
    val start = rememberStartClass()
    val cal = Schedule.clock()
    val wd = Schedule.isoWeekday(cal)
    val nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    val today = Schedule.today(Store.courses, wd)
    val next = Schedule.next(today, nowMin)
    val later = if (next == null) emptyList() else today.drop(today.indexOf(next) + 1)
    val dateStr = remember(cal.get(java.util.Calendar.DAY_OF_YEAR)) { SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(cal.time) }
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            TopInset()
            Column(Modifier.padding(start = 20.dp, end = 16.dp, top = 40.dp, bottom = 8.dp)) {
                BasicText(dateStr + if (today.isNotEmpty()) " · 今天 ${today.size} 节" else "", style = T.Sub)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText("今天", style = T.Large.copy(fontSize = 34.sp))
                    Spacer(Modifier.width(8.dp)); BetaPill()
                    Spacer(Modifier.weight(1f))
                    ImportPill { Nav.push(Route.ImportTimetable) }
                    Spacer(Modifier.width(10.dp))
                    DarkPlus { Nav.draft.value = null; Nav.push(Route.EditCourse(null)) }
                }
            }
        }
        if (Live.running.value) item { LiveBanner() }
        if (next != null) item { HeroCourse(next, nowMin, start) }
        else item { TodayEmpty(today.isNotEmpty()) }
        if (later.isNotEmpty()) {
            item { BasicText("稍后", Modifier.padding(start = 30.dp, top = 14.dp, bottom = 8.dp), style = T.Sub.copy(fontWeight = FontWeight.Medium)) }
            item {
                PlainCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), radius = 24.dp, padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    later.forEachIndexed { i, t ->
                        LaterRow(t)
                        if (i < later.lastIndex) Divider(116.dp)
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp).fillMaxWidth().height(50.dp)
                .glass(LocalBg.current!!, Capsule(), surface = Color.White.copy(alpha = 0.55f)).clip(Capsule())
                .clickable { Nav.push(Route.Courses) }.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon("list", C.Ink, 17.dp); Spacer(Modifier.width(10.dp))
                BasicText("课程", Modifier.weight(1f), style = T.Btn)
                BasicText("共 ${Store.courses.size} 门", style = T.Sub); Spacer(Modifier.width(6.dp)); Icon("chev", C.Sec, 14.dp)
            }
        }
        item { BottomSpacer() }
    }
}

@Composable
private fun TodayEmpty(allDone: Boolean) {
    PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), radius = 28.dp, padding = androidx.compose.foundation.layout.PaddingValues(22.dp)) {
        BasicText(if (allDone) "今天的课都上完了" else "今天没有课", style = T.Title)
        Spacer(Modifier.height(4.dp))
        BasicText(if (allDone) "去「笔记」里复习一下今天的内容吧。" else if (Store.courses.none { it.slots.isNotEmpty() }) "拍一张课程表，学长帮你把整学期的课排好。" else "休息一下，或者先预习下一节课。", style = T.Sub)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (allDone) Btn("看笔记", { Nav.tab.intValue = 2 }, Modifier.weight(1f), icon = "note", tint = C.Ink)
            else Btn("导入课程表", { Nav.push(Route.ImportTimetable) }, Modifier.weight(1f), icon = "scan", tint = C.Ink)
            Btn("课程", { Nav.push(Route.Courses) }, Modifier.weight(1f), icon = "list")
        }
    }
}

@Composable
private fun HeroCourse(t: Schedule.Today, nowMin: Int, start: (Course) -> Unit) {
    val c = t.course
    val ongoing = (Schedule.startMin(t.slot) ?: 0) <= nowMin
    PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), radius = 28.dp, padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Box(Modifier.fillMaxWidth().height(132.dp)) {
            CourseCover(c, Modifier.fillMaxWidth().height(132.dp), radius = 20.dp)
            BasicText(if (ongoing) "正在上" else "下一节", Modifier.padding(12.dp).clip(Capsule()).background(Color.Black.copy(alpha = 0.25f))
                .padding(horizontal = 12.dp, vertical = 3.dp), style = T.Cap.copy(color = Color.White, fontWeight = FontWeight.Bold))
        }
        Column(Modifier.padding(start = 6.dp, end = 6.dp, top = 12.dp)) {
            BasicText(c.name, style = T.Title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon("clock", C.Sec, 13.dp); Spacer(Modifier.width(4.dp))
                val room = t.slot.room.ifBlank { c.room }
                BasicText(listOf(Schedule.timeRange(t.slot), room, c.teacher).filter { it.isNotBlank() }.joinToString(" · "),
                    style = T.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn(if (Live.running.value) "回到课堂" else "开始上课", { start(c) }, Modifier.weight(1f), icon = "mic", tint = C.Ink)
            Btn("课前资料", { Nav.push(Route.CourseDetail(c.id)) }, Modifier.weight(1f), icon = "file")
        }
    }
}

@Composable
private fun LaterRow(t: Schedule.Today) {
    val c = t.course
    Row(Modifier.fillMaxWidth().clickable { Nav.push(Route.CourseDetail(c.id)) }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        CourseCover(c, Modifier.size(88.dp, 60.dp), radius = 14.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            BasicText(c.name, style = T.Item, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(listOf(Schedule.timeRange(t.slot), t.slot.room.ifBlank { c.room }).filter { it.isNotBlank() }.joinToString(" · "), style = T.Cap.copy(fontSize = 12.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        BasicText("资料", Modifier.glass(LocalBg.current!!, Capsule(), surface = Color.White.copy(alpha = 0.6f), blurDp = 4f, lensH = 6f, lensA = 10f).clip(Capsule())
            .clickable { Nav.push(Route.CourseDetail(c.id)) }.padding(horizontal = 16.dp, vertical = 8.dp), style = T.Btn.copy(fontSize = 13.sp))
    }
}

// =================== 课程（全部） ===================
fun semesterLabel(cal: java.util.Calendar = Schedule.clock()): String {
    val y = cal.get(java.util.Calendar.YEAR); val m = cal.get(java.util.Calendar.MONTH) + 1
    return when { m >= 8 -> "$y 秋季学期"; m >= 2 -> "$y 春季学期"; else -> "${y - 1} 秋季学期" }
}

@Composable
fun ImportMenu(open: Boolean, onDismiss: () -> Unit) {
    if (!open) return
    Column(Modifier.padding(top = 6.dp).width(210.dp).glass(LocalBg.current!!, com.kyant.shapes.RoundedRectangle(22.dp), surface = Color.White.copy(alpha = 0.82f)).padding(vertical = 6.dp)) {
        listOf(Triple("拍照导入课程表", "scan", Route.ImportTimetable), Triple("导入 B 站录播课", "tv", Route.ImportBili)).forEach { (t, ic, r) ->
            Row(Modifier.fillMaxWidth().clickable { onDismiss(); Nav.push(r) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(ic, if (ic == "tv") Color(0xFFFB7299) else C.Ink, 18.dp); Spacer(Modifier.width(10.dp))
                BasicText(t, Modifier.weight(1f), style = T.Label)
                if (ic == "tv") BetaPill()
            }
        }
    }
}

@Composable
fun CoursesScreen() {
    var menu by remember { mutableStateOf(false) }
    val courses = Store.courses
    GlassScaffold(content = {
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(androidx.compose.foundation.lazy.grid.GridCells.Fixed(2), Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                Column { TopInset(); Spacer(Modifier.height(52.dp))
                    BasicText("课程", Modifier.padding(start = 4.dp), style = T.Large.copy(fontSize = 34.sp))
                    BasicText("${semesterLabel()} · ${courses.size} 门", Modifier.padding(start = 4.dp, bottom = 6.dp), style = T.Sub)
                }
            }
            if (courses.isEmpty()) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) { EmptyHint("还没有课程", "点右上角 + 新建，或拍照导入课程表") }
            items(courses.size, key = { courses[it].id }) { i -> CourseTile(courses[i]) }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) { BottomSpacer() }
        }
    }, overlay = { b ->
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassCircle(b, "back", { Nav.pop() })
                Spacer(Modifier.weight(1f))
                Row(Modifier.height(40.dp).glass(b, Capsule(), surface = Color.White.copy(alpha = 0.6f)).clip(Capsule())
                    .clickable { menu = !menu }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon("scan", C.Ink, 18.dp, 2.1f); Spacer(Modifier.width(6.dp)); BasicText("导入", style = T.Btn.copy(fontSize = 14.5.sp))
                }
                Spacer(Modifier.width(10.dp))
                DarkPlus { Nav.draft.value = null; Nav.push(Route.EditCourse(null)) }
            }
            Box(Modifier.align(Alignment.End)) { androidx.compose.runtime.CompositionLocalProvider(LocalBg provides b) { ImportMenu(menu) { menu = false } } }
        }
    })
}

@Composable
private fun CourseTile(c: Course) {
    val n = Store.lectures.count { it.courseId == c.id }
    PlainCard(Modifier.fillMaxWidth(), radius = 22.dp, padding = androidx.compose.foundation.layout.PaddingValues(8.dp), onClick = { Nav.push(Route.CourseDetail(c.id)) }) {
        CourseCover(c, Modifier.fillMaxWidth().aspectRatio(159f / 88f), radius = 16.dp)
        Column(Modifier.padding(start = 6.dp, end = 4.dp, top = 10.dp, bottom = 4.dp)) {
            BasicText(c.name, style = T.Item.copy(fontSize = 15.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(listOf(c.slots.firstOrNull()?.let { Schedule.slotLabel(it) } ?: "未排课", "$n 节笔记").joinToString(" · "),
                style = T.Cap.copy(fontSize = 11.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun CourseRow(c: Course, onClick: () -> Unit) {
    val n = Store.lectures.count { it.courseId == c.id }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        CourseCover(c, Modifier.size(56.dp, 40.dp), radius = 10.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            BasicText(c.name, style = T.Item, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(listOf(c.teacher, Schedule.courseSlotLabel(c)).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "点开设置课程信息" }, style = T.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        BasicText("$n 节", style = T.Sub)
        Spacer(Modifier.width(6.dp)); Icon("chev", C.Ter, 14.dp)
    }
}

@Composable
fun LiveBanner() {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
            .glass(LocalBg.current!!, com.kyant.shapes.RoundedRectangle(20.dp), surface = Color(0xD91D1D1F)).clip(RoundedCornerShape(20.dp))
            .clickable { Nav.push(Route.Live) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(Capsule()).background(C.Red))
        Spacer(Modifier.width(10.dp))
        BasicText("正在听课 · ${Live.fmt(Live.elapsed.longValue)}", Modifier.weight(1f), style = T.Btn.copy(color = Color.White))
        BasicText("回到课堂", style = T.Sub.copy(color = Color(0xFFB0B0B5)))
    }
}

// =================== 上课 Tab ===================
@Composable
fun ClassTab() {
    val start = rememberStartClass()
    LazyColumn(Modifier.fillMaxWidth()) {
        item { TopInset(); LargeHeader(if (Live.running.value) "正在录音" else "选一门课开始", "上课") }
        if (Live.running.value) item { LiveBanner() }
        item {
            PlainCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
                BasicText("学长会边听边整理", style = T.Item)
                Spacer(Modifier.height(6.dp))
                RichText("**原声**：过滤语气词和重复，保留老师原话，没听清的字按上下文补全并标成 [f:灰色虚线]。\n**学长**：用大白话转述，[b:概念] [r:考点] [o:助记] 都会标出来，公式 \$\\nabla f\$ 直接渲染。", style = T.Body.copy(fontSize = 14.sp))
            }
        }
        item { SectionLabel("我的课程") }
        item {
            PlainCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Store.courses.forEachIndexed { i, c ->
                    CourseRow(c) { start(c) }
                    if (i < Store.courses.lastIndex) Divider(70.dp)
                }
                if (Store.courses.isEmpty()) Row2("新建课程", "", { Nav.push(Route.EditCourse(null)) })
            }
        }
        item { BottomSpacer() }
    }
}

// =================== 课程详情 ===================
@Composable
fun CourseDetailScreen(id: String) {
    val c = Store.course(id) ?: run { Nav.pop(); return }
    val start = rememberStartClass()
    val lectures = Store.lectures.filter { it.courseId == id }
    GlassScaffold(content = {
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                Box(Modifier.fillMaxWidth()) {
                    CourseCover(c, Modifier.fillMaxWidth().height(190.dp), radius = 0.dp)
                    Box(Modifier.fillMaxWidth().padding(top = 110.dp).height(82.dp)
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(C.Bg.copy(alpha = 0f), C.Bg))))
                    BasicText("更换封面", Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 10.dp).clip(Capsule())
                        .background(Color.Black.copy(alpha = 0.28f)).clickable { Nav.push(Route.CoverPicker(id)) }
                        .padding(horizontal = 12.dp, vertical = 5.dp), style = T.Cap.copy(color = Color.White, fontWeight = FontWeight.Bold))
                }
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp)) {
                    BasicText(c.name, style = T.Large.copy(fontSize = 30.sp))
                    BasicText(listOf(c.teacher, Schedule.courseSlotLabel(c), c.room).filter { it.isNotBlank() }.joinToString(" · "), style = T.Sub)
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Btn("开始上课", { start(c) }, Modifier.weight(1f), icon = "mic", tint = C.Ink)
                    Btn("编辑 / 课件", { Nav.draft.value = null; Nav.push(Route.EditCourse(id)) }, Modifier.weight(1f), icon = "file")
                }
            }
            if (c.hotwords.isNotEmpty()) {
                item { SectionLabel("热词 · 提升识别准确率") }
                item { HotwordChips(c.hotwords, null) }
            }
            if (c.slides.isNotEmpty()) {
                item { SectionLabel("课件 · ${c.slides.size} 页有内容") }
                item {
                    PlainCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                        c.slides.take(12).forEachIndexed { i, s ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                                Tag("P.${s.page}", C.Blue); Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    BasicText(s.title, style = T.Label)
                                    s.formulas.firstOrNull()?.let { MathBlock(it, T.Body.copy(fontSize = 13.sp), scale = 1f) }
                                }
                            }
                            if (i < minOf(11, c.slides.lastIndex)) Divider()
                        }
                    }
                }
            }
            item { SectionLabel("课堂笔记 · ${lectures.size} 节") }
            item {
                PlainCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    if (lectures.isEmpty()) Row2("还没有上过课", "")
                    lectures.forEachIndexed { i, l ->
                        LectureRow(l)
                        if (i < lectures.lastIndex) Divider(16.dp)
                    }
                }
            }
            item {
                BasicText("删除这门课", Modifier.fillMaxWidth().padding(24.dp).clickable { Store.deleteCourse(id); Nav.pop() },
                    style = T.Label.copy(color = C.Red, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
            }
            item { BottomSpacer() }
        }
    }, overlay = { b -> BackBar(b) })
}

@Composable
fun LectureRow(l: app.xuezhang.data.Lecture) {
    val date = remember(l.date) { SimpleDateFormat("M月d日", Locale.CHINA).format(Date(l.date)) }
    Row(Modifier.fillMaxWidth().clickable {
        when (l.status) { 0 -> Nav.push(Route.Live); 1 -> Nav.push(Route.Generating(l.id)); else -> Nav.push(Route.Note(l.id)) }
    }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            BasicText(l.title, style = T.Item, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val exam = l.segments.count { it.exam }
            BasicText("$date · ${l.durationMs / 60000} 分钟" + (if (exam > 0) " · $exam 个考点" else "") +
                when (l.status) { 0 -> " · 录音中"; 1 -> " · 生成中"; 3 -> " · 待重试"; else -> "" }, style = T.Sub)
        }
        Icon("chev", C.Ter, 14.dp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HotwordChips(words: List<String>, onRemove: ((String) -> Unit)?) {
    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        words.forEach { w ->
            Row(
                Modifier.glass(LocalBg.current!!, Capsule(), surface = Color.White.copy(alpha = 0.5f), blurDp = 4f, lensH = 6f, lensA = 10f).clip(Capsule())
                    .then(if (onRemove != null) Modifier.clickable { onRemove(w) } else Modifier)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(w, style = T.Label.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Medium))
                if (onRemove != null) { Spacer(Modifier.width(4.dp)); Icon("close", C.Sec, 11.dp) }
            }
        }
    }
}

@Composable
fun BackBar(backdrop: com.kyant.backdrop.Backdrop, right: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        GlassCircle(backdrop, "back", { Nav.pop() })
        Spacer(Modifier.weight(1f))
        right()
    }
}

// =================== 新建 / 编辑课程 ===================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditCourseScreen(id: String?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val orig = id?.let { Store.course(it) }
    // 草稿放在 Nav 里：去选择封面/导入再回来不会丢
    if (Nav.draft.value == null || (id != null && Nav.draft.value?.id != id)) {
        Nav.draft.value = orig ?: Course(name = "", colorIdx = Store.courses.size % C.course.size, defaultMode = Store.settings.value.defaultMode)
    }
    val c = Nav.draft.value!!
    fun upd(n: Course) { Nav.draft.value = n }
    var parsing by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var menu by remember { mutableStateOf(false) }
    var addSlot by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { cur ->
            val i = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME); cur.moveToFirst(); if (i >= 0) cur.getString(i) else null
        } ?: "课件"
        scope.launch {
            // 实时进度：页面 n/N + 百分比，灵动岛与蓝色进度条同步；文案每 2 秒轮换
            var page = 0; var total = 0; var phase = 0; var tick = 0
            fun pct() = progress
            fun push() {
                val line = app.xuezhang.engine.ImportFun.line(phase, page, total, tick)
                parsing = line
                Island.progress(line, pct())
            }
            val ticker = launch {
                while (true) {
                    kotlinx.coroutines.delay(2000); tick++
                    if (phase == 1 && progress < 0.95f) progress += (0.95f - progress) * 0.12f
                    push()
                }
            }
            try {
                parsing = "正在读取 $name"; progress = 0.02f; Island.progress("学长拿到课件了：$name", 0.02f)
                val local = withContext(Dispatchers.IO) { SlideParser.copyLocal(ctx, uri, name, Nav.draft.value!!.id) }
                progress = 0.05f
                val src = if (local.isNotBlank()) Uri.fromFile(java.io.File(local)) else uri
                val pages = withContext(Dispatchers.IO) {
                    SlideParser.extract(ctx, src, name) { p, n ->
                        page = p; total = n
                        progress = if (n > 0) 0.05f + p.toFloat() / n * 0.55f else (0.05f + p * 0.01f).coerceAtMost(0.5f)
                        push()
                    }
                }
                phase = 1; progress = 0.6f; push()
                val (hot, slides) = withContext(Dispatchers.IO) {
                    runCatching { SlideParser.analyze(pages) }.getOrElse { e ->
                        Island.show("没连上大模型，只保存了页面文字", Island.Kind.Warn); emptyList<String>() to SlideParser.plainSlides(pages)
                    }
                }
                ticker.cancel()
                progress = 1f
                val done = app.xuezhang.engine.ImportFun.done()
                parsing = done; Island.progress(done, 1f)
                val cur = Nav.draft.value!!
                upd(cur.copy(hotwords = (cur.hotwords + hot).distinct(), slides = slides, slideFile = name, slidePath = local))
                kotlinx.coroutines.delay(1200)
                Island.endProgress()
                Island.show("课件解析完成 · ${pages.size} 页 · ${hot.size} 个热词", Island.Kind.Done)
            } catch (e: Exception) {
                Island.endProgress()
                Island.show("解析失败：${e.message?.take(30)}", Island.Kind.Warn)
            } finally { ticker.cancel(); Island.endProgress(); parsing = null }
        }
    }

    GlassScaffold(content = {
        LazyColumn(Modifier.fillMaxWidth()) {
            item { TopInset(); LargeHeader(null, if (orig == null) "新建课程" else "编辑课程") }
            item { SectionLabel("课程信息") }
            item {
                PlainCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    listOf(
                        Triple("课程名", c.name) { v: String -> upd(Nav.draft.value!!.copy(name = v)) },
                        Triple("老师", c.teacher) { v: String -> upd(Nav.draft.value!!.copy(teacher = v)) },
                        Triple("教室", c.room) { v: String -> upd(Nav.draft.value!!.copy(room = v)) },
                    ).forEachIndexed { i, (k, v, set) ->
                        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            BasicText(k, Modifier.width(82.dp), style = T.Label)
                            Field(v, set, if (i == 0) "如 电机学" else "未填写", Modifier.weight(1f))
                        }
                        if (i < 2) Divider(18.dp)
                    }
                }
            }
            item { SectionLabel("上课时间") }
            item {
                PlainCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    c.slots.forEachIndexed { i, sl ->
                        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon("cal", C.Blue, 17.dp); Spacer(Modifier.width(10.dp))
                            BasicText(Schedule.slotLabel(sl) + "  " + Schedule.timeRange(sl), Modifier.weight(1f), style = T.Label)
                            Icon("close", C.Sec, 14.dp, modifier = Modifier.clickable { upd(Nav.draft.value!!.copy(slots = Nav.draft.value!!.slots.filterIndexed { k, _ -> k != i })) })
                        }
                        Divider(18.dp)
                    }
                    if (addSlot) SlotEditor({ addSlot = false }) { sl -> upd(Nav.draft.value!!.copy(slots = Nav.draft.value!!.slots + sl)); addSlot = false }
                    else Row2("添加上课时间", "", { addSlot = true })
                }
            }
            item { SectionLabel("封面") }
            item {
                PlainCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), onClick = { Nav.push(Route.CoverPicker("draft")) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CoverView(c.name.ifBlank { "课程" }, c.cover, Modifier.size(96.dp, 54.dp), radius = 12.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            BasicText(when { c.cover.startsWith("file:") -> "自定义图片"; c.cover.startsWith("preset:") -> "预制 · " + c.cover.removePrefix("preset:"); else -> "自动匹配" }, style = T.Btn)
                            BasicText("按课程名自动匹配，可以换预制、相册或 AI 生成", style = T.Cap)
                        }
                        Icon("chev", C.Ter, 14.dp)
                    }
                }
            }
            item { SectionLabel("默认模式") }
            item {
                PlainCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    LiquidSegmented(items = listOf("原声", "学长"), selected = c.defaultMode, onSelect = { upd(Nav.draft.value!!.copy(defaultMode = it)) }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    RichText(if (c.defaultMode == 1) "**学长模式**：像朋友一样用大白话讲给你听，公式照样渲染，比如 \$\\nabla f\$ 就是「最陡上坡的方向」。"
                    else "**原声模式**：老师原话，去掉语气词与重复，没听清的字补全成 [f:这样]。", style = T.Body.copy(fontSize = 14.sp))
                }
            }
            item { SectionLabel("课前资料") }
            item {
                PlainCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clickable(enabled = parsing == null) {
                    picker.launch(arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.presentationml.presentation"))
                }) {
                    if (parsing != null) {
                        val shown by androidx.compose.animation.core.animateFloatAsState(progress, androidx.compose.animation.core.spring(0.9f, 120f), label = "imp")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicText(parsing!!, Modifier.weight(1f), style = T.Btn, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.width(8.dp))
                            BasicText("${(progress * 100).toInt()}%", style = T.Btn.copy(color = C.Blue))
                        }
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(Capsule()).background(C.Fill)) {
                            Box(Modifier.fillMaxWidth(shown.coerceIn(0.02f, 1f)).height(8.dp).clip(Capsule()).background(C.Blue))
                        }
                    } else {
                        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon("upload", C.Blue, 26.dp, 2.2f)
                            Spacer(Modifier.height(6.dp))
                            BasicText(if (c.slideFile.isBlank()) "上传 PPT / PDF" else "已解析 ${c.slideFile} · 点按更换", style = T.Btn.copy(color = C.Blue), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            BasicText("学长会提前读一遍：页码、热词、公式都会识别", style = T.Cap)
                        }
                    }
                }
            }
            if (c.hotwords.isNotEmpty()) {
                item { SectionLabel("识别热词 · 点按删除") }
                item { HotwordChips(c.hotwords) { w -> upd(Nav.draft.value!!.copy(hotwords = Nav.draft.value!!.hotwords - w)) } }
            }
            item {
                var hw by remember { mutableStateOf("") }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().glass(LocalBg.current!!, Capsule(), surface = Color.White.copy(alpha = 0.55f)).clip(Capsule())
                    .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Field(hw, { hw = it }, "手动添加热词，空格分隔", Modifier.weight(1f))
                    BasicText("添加", Modifier.clickable {
                        val add = hw.split(' ', '，', ',', '、').map { it.trim() }.filter { it.isNotEmpty() }
                        if (add.isNotEmpty()) upd(Nav.draft.value!!.copy(hotwords = (Nav.draft.value!!.hotwords + add).distinct())); hw = ""
                    }, style = T.Btn.copy(color = C.Blue))
                }
            }
            item { BottomSpacer() }
        }
    }, overlay = { b ->
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassCircle(b, "back", { Nav.draft.value = null; Nav.pop() })
                Spacer(Modifier.width(10.dp))
                Row(Modifier.height(44.dp).glass(b, Capsule(), surface = Color.White.copy(alpha = 0.6f)).clip(Capsule())
                    .clickable { menu = !menu }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon("scan", C.Ink, 18.dp, 2.1f); Spacer(Modifier.width(6.dp)); BasicText("导入", style = T.Btn.copy(fontSize = 14.5.sp))
                }
                Spacer(Modifier.weight(1f))
                GlassPill(b, "保存", {
                    val d = Nav.draft.value!!
                    if (d.name.isBlank()) { Island.show("给课程起个名字", Island.Kind.Warn); return@GlassPill }
                    Store.upsertCourse(d.copy(name = d.name.trim(), time = d.slots.firstOrNull()?.let { s -> "${Schedule.WEEK[s.weekday - 1]} ${Schedule.startMin(s)?.let { Schedule.hm(it) } ?: ""}".trim() } ?: d.time))
                    Nav.draft.value = null; Nav.pop()
                }, tint = C.Blue.copy(alpha = 0.85f), color = Color.White, height = 44.dp)
            }
            androidx.compose.runtime.CompositionLocalProvider(LocalBg provides b) { ImportMenu(menu) { menu = false } }
        }
    })
}

@Composable
private fun SlotEditor(onCancel: () -> Unit, onAdd: (app.xuezhang.data.Slot) -> Unit) {
    var wd by remember { mutableStateOf(1) }
    var st by remember { mutableStateOf(1) }
    var en by remember { mutableStateOf(2) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Schedule.WEEK.forEachIndexed { i, w ->
                BasicText(w.removePrefix("周"), Modifier.weight(1f).clip(Capsule()).background(if (wd == i + 1) C.Blue else C.Fill).clickable { wd = i + 1 }
                    .padding(vertical = 7.dp), style = T.Btn.copy(fontSize = 13.sp, color = if (wd == i + 1) Color.White else C.Ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText("第", style = T.Label); Stepper(st, 1..12) { st = it; if (en < it) en = it }
            BasicText("到", style = T.Label); Stepper(en, st..12) { en = it }; BasicText("节", style = T.Label)
            Spacer(Modifier.weight(1f))
            BasicText(Schedule.timeRange(app.xuezhang.data.Slot(wd, st, en)), style = T.Cap)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("取消", onCancel, Modifier.weight(1f))
            Btn("添加", { onAdd(app.xuezhang.data.Slot(wd, st, en)) }, Modifier.weight(1f), tint = C.Blue)
        }
    }
}

@Composable
private fun Stepper(v: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.padding(horizontal = 6.dp).clip(Capsule()).background(C.Fill), verticalAlignment = Alignment.CenterVertically) {
        BasicText("−", Modifier.clickable { if (v > range.first) onChange(v - 1) }.padding(horizontal = 10.dp, vertical = 4.dp), style = T.Btn)
        BasicText("$v", style = T.Btn.copy(color = C.Blue))
        BasicText("+", Modifier.clickable { if (v < range.last) onChange(v + 1) }.padding(horizontal = 10.dp, vertical = 4.dp), style = T.Btn)
    }
}
