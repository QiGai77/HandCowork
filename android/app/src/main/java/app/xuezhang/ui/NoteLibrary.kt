package app.xuezhang.ui

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xuezhang.data.Course
import app.xuezhang.data.Lecture
import app.xuezhang.data.Store
import app.xuezhang.engine.HardCovers
import app.xuezhang.engine.keep
import com.kyant.shapes.Capsule
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// =================== 笔记库 v0.0.7.1：书架 / 列表（取值全部来自 NoteTokens.kt） ===================

private val GrayPalette = NT.BookPalette(Color(0xFF8C8C92), Color(0xFF7D7D84), Color(0xFF77777E), Color(0xFF3C3C42))

/** 课程的精装封面款（与书架、列表、上课卡片、复习卡统一）。 */
fun courseHc(c: Course): HardCovers.Hc = HardCovers.forCourse(c.name, c.cover)
fun lectureHc(l: Lecture, c: Course?): HardCovers.Hc = HardCovers.forLecture(l.coverStyle, c?.name, c?.cover, l.title)

/** 课程的书色：取自这门课的精装封面布色。 */
fun bookPalette(course: Course?): NT.BookPalette {
    course ?: return GrayPalette
    val hc = courseHc(course)
    return NT.BookPalette(hc.color, hc.color, hc.lightColor, hc.color)
}

/** 按课表算接下来的上课日期（含今天，今天已有笔记则跳过今天）。 */
private fun upcoming(c: Course, n: Int, lectures: List<Lecture>): List<Long> {
    if (c.slots.isEmpty() || n <= 0) return emptyList()
    val out = ArrayList<Long>()
    val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    val todayHas = lectures.any { sameDay(it.date, cal.timeInMillis) }
    for (d in 0 until 70) {
        if (d == 0 && todayHas) { cal.add(Calendar.DAY_OF_MONTH, 1); continue }
        val wd = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1 // 1=周一 … 7=周日
        if (c.slots.any { it.weekday == wd }) out.add(cal.timeInMillis)
        if (out.size >= n) break
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }
    return out
}
private fun sameDay(a: Long, b: Long): Boolean {
    val f = SimpleDateFormat("yyyyMMdd", Locale.CHINA); return f.format(Date(a)) == f.format(Date(b))
}
private fun md(t: Long) = SimpleDateFormat("M/d", Locale.CHINA).format(Date(t))

/** 笔记正文转纯文本摘要。 */
private fun summary(l: Lecture): String {
    val raw = l.sections.firstOrNull()?.body?.ifBlank { null } ?: l.notes
    return raw.replace(Regex("```[\\s\\S]*?```"), " ").replace(Regex("\\[[a-z]:([^\\]]*)\\]"), "$1")
        .replace(Regex("[#*>$`]"), "").replace(Regex("\\s+"), " ").trim()
}

private fun open(l: Lecture) = Nav.push(if (l.status == 1) Route.Generating(l.id) else Route.Book(l.id))

/** 点按打开；长按弹出书本菜单（封面朝外 / 侧放书脊、换封面、AI 封面）。 */
private fun Modifier.bookTap(l: Lecture) = this.pointerInput(l.id, l.status) {
    detectTapGestures(onTap = { open(l) }, onLongPress = { Nav.sheet.value = Sheet.BookMenu(l.id) })
}

@Composable
fun LibraryTab() {
    val lectures = Store.lectures
    var mode by keep("lib.mode") { 0 }
    val shelves = remember(lectures.toList(), Store.courses.toList()) {
        val known = Store.courses.map { c -> c as Course? to lectures.filter { it.courseId == c.id }.sortedBy { it.date } }
            .sortedByDescending { it.second.size.coerceAtMost(1) }
        val orphan = lectures.filter { l -> Store.courses.none { it.id == l.courseId } }.sortedBy { it.date }
        known + if (orphan.isNotEmpty()) listOf(null as Course? to orphan) else emptyList()
    }
    val courseCount = Store.courses.size
    Box(Modifier.fillMaxSize().background(NT.Brushes.Background)) {
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "head") {
                TopInset()
                Row(Modifier.padding(start = NT.Dim.Screen.PadH, end = NT.Dim.Screen.PadH, top = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        BasicText("笔记", style = NT.Type.PageTitle)
                        Spacer(Modifier.height(NT.Dim.Screen.SubGap))
                        BasicText("$courseCount 门课 · ${lectures.size} 本笔记", style = NT.Type.PageSub)
                    }
                    Segment(listOf("书架", "列表"), mode) { mode = it }
                }
                Spacer(Modifier.height(18.dp))
            }
            if (mode == 0 && Store.settings.value.shelfFold && lectures.isNotEmpty()) {
                item(key = "fold") { FoldedShelf(lectures.sortedBy { it.date }) }
            } else if (mode == 0) {
                if (shelves.isEmpty()) item(key = "noshelf") { NoCourseShelf() }
                else {
                    val mergeAll = shelves.size >= 2 && shelves.all { it.second.size <= 1 } && shelves.any { it.second.isNotEmpty() }
                    if (mergeAll) item(key = "term") { TermShelf(shelves) }
                    else shelves.forEach { (c, ls) -> item(key = "shelf_${c?.id ?: "orphan"}") { CourseShelf(c, ls) } }
                    if (courseCount == 1) item(key = "addshelf") { AddCourseShelf() }
                }
            } else {
                notesList(this, lectures.toList())
            }
            item(key = "bottom") { BottomSpacer() }
        }
    }
}

// ---------- 书架 / 列表 切换 ----------
@Composable
private fun Segment(labels: List<String>, sel: Int, onSel: (Int) -> Unit) {
    val d = NT.Dim.Segment
    val x by animateDpAsState(if (sel == 0) d.Inset else d.W - d.Inset - d.ThumbW, spring(0.8f, 380f), label = "seg")
    Box(Modifier.size(d.W, d.H).clip(Capsule()).background(NT.Colors.Segment.Track)) {
        Box(Modifier.offset(x = x, y = d.Inset).size(d.ThumbW, d.H - d.Inset * 2).shadow(3.dp, Capsule()).clip(Capsule()).background(NT.Colors.Segment.Thumb))
        Row(Modifier.fillMaxSize().padding(horizontal = d.Inset)) {
            labels.forEachIndexed { i, t ->
                Box(Modifier.width(d.ThumbW).fillMaxHeight().clip(Capsule()).clickable { onSel(i) }, contentAlignment = Alignment.Center) {
                    BasicText(t, style = NT.Type.Segment.copy(color = if (i == sel) NT.Colors.Segment.On else NT.Colors.Segment.Off))
                }
                if (i == 0) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ---------- 一门课一层 ----------
@Composable
private fun ShelfHeader(name: String, count: String, pal: NT.BookPalette, onClick: (() -> Unit)?) {
    val s = NT.Dim.Shelf
    Row(Modifier.padding(start = 28.dp, end = NT.Dim.Screen.PadH).height(s.HeaderH), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(s.Dot).clip(Capsule()).background(pal.chip))
        Spacer(Modifier.width(s.DotGap))
        BasicText(name, Modifier.weight(1f).then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
            style = NT.Type.ShelfSection.copy(color = pal.deep), maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicText(count, style = NT.Type.ShelfCount)
    }
}

@Composable
private fun Board() {
    val s = NT.Dim.Shelf
    Box(Modifier.padding(horizontal = s.BoardInsetH).fillMaxWidth().height(s.BoardH)
        .shadow(6.dp, RoundedCornerShape(s.BoardR), ambientColor = NT.Shadow.Board.third, spotColor = NT.Shadow.Board.third)
        .clip(RoundedCornerShape(s.BoardR))
        .background(if (ThemeState.dark) Brush.verticalGradient(listOf(Color(0xFF23252E), Color(0xFF16181E))) else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), Color(0xFFE7E2DA))))
        .border(1.dp, if (ThemeState.dark) Color(0x22FFFFFF) else Color.White, RoundedCornerShape(s.BoardR)))
}

private val AreaH = 140.dp

@Composable
private fun CourseShelf(c: Course?, books: List<Lecture>) {
    val pal = bookPalette(c)
    val n = books.size
    val ghosts = remember(c, n) { c?.let { upcoming(it, when { n == 0 -> 4; n <= 2 -> 3; n <= 5 -> 1; else -> 0 }, books) } ?: emptyList() }
    Column(Modifier.padding(bottom = 26.dp)) {
        ShelfHeader(c?.name ?: "未分类", if (n == 0) "还没有笔记" else "$n 本", pal, c?.let { cc -> { Nav.push(Route.CourseDetail(cc.id)) } })
        Spacer(Modifier.height(NT.Dim.Shelf.HeaderToBooks))
        Box(Modifier.fillMaxWidth().height(AreaH)) {
            when {
                n == 0 -> Row(Modifier.fillMaxSize().padding(start = 50.dp, end = 35.dp), verticalAlignment = Alignment.Bottom) {
                    ghostRow(ghosts.ifEmpty { listOf(-1L, -2L, -3L) }, pal)
                    Spacer(Modifier.weight(1f))
                    EmptyJacket(pal)
                }
                n <= 2 -> Row(Modifier.fillMaxSize().padding(start = 46.dp, end = 35.dp), verticalAlignment = Alignment.Bottom) {
                    books.reversed().forEachIndexed { i, l ->
                        FaceCover(l, c, pal, 104.dp, 134.dp)
                        if (i < n - 1) Spacer(Modifier.width(10.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    ghostRow(ghosts, pal)
                    Spacer(Modifier.width(4.dp))
                    Bookend()
                    Spacer(Modifier.weight(1f))
                }
                n <= 5 -> Row(Modifier.fillMaxSize().padding(start = 50.dp, end = 35.dp), verticalAlignment = Alignment.Bottom) {
                    val spines = books.dropLast(1)
                    spines.forEachIndexed { i, l ->
                        Spine(l, c, pal, lean = i == spines.lastIndex && ghosts.isEmpty() && spines.size >= 2)
                        Spacer(Modifier.width(NT.Dim.Shelf.SpineGap))
                    }
                    ghostRow(ghosts, pal)
                    Spacer(Modifier.weight(1f))
                    FaceCover(books.last(), c, pal, NT.Dim.Shelf.CoverW, NT.Dim.Shelf.CoverH)
                }
                else -> Row(Modifier.fillMaxSize().padding(end = 35.dp), verticalAlignment = Alignment.Bottom) {
                    LazyRow(Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(start = 50.dp, end = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(NT.Dim.Shelf.SpineGap), verticalAlignment = Alignment.Bottom,
                        reverseLayout = false) {
                        items(books.dropLast(1), key = { it.id }) { l -> Spine(l, c, pal, lean = false) }
                    }
                    Spacer(Modifier.width(12.dp))
                    FaceCover(books.last(), c, pal, NT.Dim.Shelf.CoverW, NT.Dim.Shelf.CoverH)
                }
            }
        }
        Board()
    }
}

/** 书架折叠成一行：所有笔记按日期排在同一层。 */
@Composable
private fun FoldedShelf(books: List<Lecture>) {
    Column(Modifier.padding(bottom = 26.dp)) {
        ShelfHeader("全部笔记", "${books.size} 本 · 已折叠", NT.palette(0).copy(deep = NT.Colors.Ink.Primary, chip = NT.Colors.Ink.Tertiary), null)
        Spacer(Modifier.height(NT.Dim.Shelf.HeaderToBooks))
        LazyRow(Modifier.fillMaxWidth().height(AreaH), contentPadding = PaddingValues(horizontal = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(NT.Dim.Shelf.SpineGap), verticalAlignment = Alignment.Bottom) {
            items(books, key = { it.id }) { l ->
                val c = Store.courses.find { it.id == l.courseId }
                Spine(l, c, bookPalette(c), lean = false)
            }
        }
        Board()
    }
}

@Composable
private fun ghostRow(dates: List<Long>, pal: NT.BookPalette) {
    dates.forEachIndexed { i, t ->
        GhostSpine(if (t > 0) md(t) else "", pal, i)
        Spacer(Modifier.width(3.dp))
    }
}

/** 每门课都只有 0~1 本时：合成一层「本学期」，每门课一本封面朝外，中间用书立隔开。 */
@Composable
private fun TermShelf(shelves: List<Pair<Course?, List<Lecture>>>) {
    Column(Modifier.padding(bottom = 26.dp)) {
        ShelfHeader("本学期", "${shelves.sumOf { it.second.size }} 本", NT.palette(0).copy(deep = NT.Colors.Ink.Primary, chip = NT.Colors.Ink.Tertiary), null)
        Spacer(Modifier.height(NT.Dim.Shelf.HeaderToBooks))
        LazyRow(Modifier.fillMaxWidth().height(AreaH + 22.dp), contentPadding = PaddingValues(horizontal = 46.dp), verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            shelves.forEachIndexed { i, (c, ls) ->
                item(key = c?.id ?: "orphan") {
                    val pal = bookPalette(c)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            if (ls.isEmpty()) GhostCover(c, pal) else FaceCover(ls.last(), c, pal, NT.Dim.Shelf.CoverW, NT.Dim.Shelf.CoverH)
                            if (i < shelves.lastIndex) { Spacer(Modifier.width(10.dp)); Bookend() }
                        }
                    }
                }
            }
        }
        Board()
    }
}

@Composable
private fun NoCourseShelf() {
    Column(Modifier.padding(bottom = 26.dp)) {
        ShelfHeader("第一层书架", "空", GrayPalette, null)
        Spacer(Modifier.height(NT.Dim.Shelf.HeaderToBooks))
        Row(Modifier.fillMaxWidth().height(AreaH).padding(start = 50.dp, end = 35.dp), verticalAlignment = Alignment.Bottom) {
            ghostRow(listOf(-1L, -2L, -3L, -4L), GrayPalette)
            Spacer(Modifier.weight(1f))
            Jacket("先导入课表\n书架会按课排好", "导入课表") { Nav.push(Route.ImportTimetable) }
        }
        Board()
    }
}

@Composable
private fun AddCourseShelf() {
    Column(Modifier.padding(bottom = 26.dp)) {
        ShelfHeader("再加一门课", "", GrayPalette, null)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(70.dp).padding(horizontal = 50.dp).clip(RoundedCornerShape(12.dp)).dashed(GrayPalette.spine, 12.dp)
            .clickable { Nav.push(Route.ImportTimetable) }, contentAlignment = Alignment.Center) {
            BasicText("＋ 导入课表或新建课程", style = NT.Type.Chip.copy(color = NT.Colors.Ink.Secondary))
        }
        Spacer(Modifier.height(6.dp))
        Board()
    }
}

// ---------- 书 ----------
private fun Modifier.dashed(color: Color, r: Dp) = this.drawBehind {
    drawRoundRect(color, cornerRadius = CornerRadius(r.toPx()), style = Stroke(1.3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
}

private fun spineH(l: Lecture): Dp = (104 + BookLook.hash(l.id) % 21).dp

@Composable
private fun Spine(l: Lecture, c: Course?, pal: NT.BookPalette, lean: Boolean) {
    if (l.lying) { FaceCoverRaw(l, c, pal, 96.dp, 126.dp); return }
    SpineRaw(l, c, pal, lean)
}

@Composable
private fun SpineRaw(l: Lecture, c: Course?, pal: NT.BookPalette, lean: Boolean) {
    val s = NT.Dim.Shelf
    val job = if (l.status == 1) aiJob("notes:${l.id}") else null
    val gen = l.status == 1
    val f by animateFloatAsState(job?.fraction?.floatValue ?: if (gen) 0.05f else 1f, label = "spf")
    val title = remember(l.title) { BookLook.spineTitle(l.title, c?.name.orEmpty(), 6) }
    val hc = remember(l.coverStyle, c?.name, c?.cover) { lectureHc(l, c) }
    val height = spineH(l)
    val shape = RoundedCornerShape(s.SpineR)
    Box(Modifier.width(s.SpineW).height(height)
        .graphicsLayer { if (lean) { rotationZ = NT.Dim.Shelf.LeanDeg.toFloat(); transformOrigin = TransformOrigin(1f, 1f) } }
        .shadow(if (gen) 0.dp else 3.dp, shape).clip(shape)
        .then(if (gen) Modifier.dashed(pal.spine.copy(alpha = 0.6f), s.SpineR) else Modifier)
        .bookTap(l)) {
        // 生成中：书脊从下往上「显影」
        HcSpine(hc, title, if (gen) "${(f * 100).toInt()}%" else md(l.date), Modifier.fillMaxSize(), fill = if (gen) f.coerceIn(0f, 1f) else 1f)
        if (l.status == 3) Box(Modifier.align(Alignment.TopEnd).padding(3.dp).size(6.dp).clip(Capsule()).background(C.Red))
    }
}

@Composable
private fun GhostSpine(label: String, pal: NT.BookPalette, i: Int) {
    val s = NT.Dim.Shelf
    val h = listOf(118, 106, 122, 110)[i % 4].dp
    Box(Modifier.width(s.SpineW).height(h).clip(RoundedCornerShape(s.SpineR)).background(pal.spine.copy(alpha = 0.10f)).dashed(pal.spine.copy(alpha = 0.45f), s.SpineR)) {
        BasicText("待\n写", Modifier.align(Alignment.TopCenter).padding(top = 18.dp), style = NT.Type.Spine.copy(color = pal.spine.copy(alpha = 0.55f), textAlign = TextAlign.Center, lineHeight = 14.5.sp))
        if (label.isNotEmpty()) BasicText(label, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), style = NT.Type.SpineDate.copy(fontSize = 7.sp, color = pal.deep.copy(alpha = 0.6f)))
    }
}

@Composable
private fun FaceCover(l: Lecture, c: Course?, pal: NT.BookPalette, w: Dp, h: Dp) {
    if (l.lying) { SpineRaw(l, c, pal, lean = false); return }
    FaceCoverRaw(l, c, pal, w, h)
}

@Composable
private fun FaceCoverRaw(l: Lecture, c: Course?, pal: NT.BookPalette, w: Dp, h: Dp) {
    val s = NT.Dim.Shelf
    val gen = l.status == 1
    val job = if (gen) aiJob("notes:${l.id}") else null
    val hc = remember(l.coverStyle, c?.name, c?.cover) { lectureHc(l, c) }
    Box(Modifier.graphicsLayer { rotationY = -7f; cameraDistance = 14f * density; transformOrigin = TransformOrigin(0f, 0.5f) }) {
        // 书签丝带（压在封面下面，露出一截）
        Box(Modifier.align(Alignment.BottomStart).offset(x = w * 0.62f, y = s.RibbonH - 4.dp).size(s.RibbonW, s.RibbonH).background(hc.darkColor))
        Box(Modifier.size(w, h).shadow(7.dp, RoundedCornerShape(s.CoverR)).clip(RoundedCornerShape(s.CoverR)).bookTap(l)) {
            LectureCoverArt(l, c, hc, Modifier.fillMaxSize(), showTitle = true)
            if (gen) {
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.35f)))
                BasicText("生成中 ${((job?.fraction?.floatValue ?: 0f) * 100).toInt()}%", Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                    style = NT.Type.ProgressLabel.copy(fontSize = 10.sp, color = pal.deep))
            }
        }
        // 倒影
        Box(Modifier.align(Alignment.BottomCenter).offset(y = 10.dp).size(w * 0.9f, 8.dp).clip(RoundedCornerShape(50))
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.10f), Color.Transparent))))
    }
}

/** 一本笔记的封面：AI 封面图优先，否则精装预设（标题为课程名）。 */
@Composable
fun LectureCoverArt(l: Lecture, c: Course?, hc: HardCovers.Hc = lectureHc(l, c), modifier: Modifier = Modifier, showTitle: Boolean = true) {
    if (l.coverImg.isNotBlank()) {
        val bmp = remember(l.coverImg) { CoverImages.load(l.coverImg.removePrefix("file:")) }
        if (bmp != null) { androidx.compose.foundation.Image(bmp, null, modifier, contentScale = androidx.compose.ui.layout.ContentScale.Crop); return }
    }
    if (c != null && l.coverStyle < 0 && c.cover.startsWith("file:")) { CourseCover(c, modifier, radius = 0.dp, showTitle = showTitle); return }
    val name = c?.name ?: l.title
    HardCover(hc, name, modifier, showTitle = showTitle)
}

@Composable
private fun GhostCover(c: Course?, pal: NT.BookPalette) {
    val s = NT.Dim.Shelf
    Box(Modifier.size(s.CoverW, s.CoverH).clip(RoundedCornerShape(s.CoverR)).background(pal.cover.copy(alpha = 0.10f)).dashed(pal.cover.copy(alpha = 0.5f), s.CoverR)
        .clickable { c?.let { Nav.push(Route.CourseDetail(it.id)) } }, contentAlignment = Alignment.Center) {
        BasicText((c?.name ?: "未分类") + "\n还没有笔记", Modifier.padding(6.dp), style = NT.Type.SpineDate.copy(fontSize = 10.sp, lineHeight = 14.sp, color = pal.deep, textAlign = TextAlign.Center))
    }
}

@Composable
private fun EmptyJacket(pal: NT.BookPalette) = Jacket("上完第一节课\n这里就有书了", "去上课", pal) { Nav.tab.intValue = 1 }

@Composable
private fun Jacket(text: String, action: String, pal: NT.BookPalette = GrayPalette, onClick: () -> Unit) {
    val s = NT.Dim.Shelf
    Column(Modifier.size(s.CoverW + 6.dp, s.CoverH).clip(RoundedCornerShape(s.CoverR)).background(Color.White.copy(alpha = 0.45f)).dashed(pal.cover.copy(alpha = 0.55f), s.CoverR)
        .clickable { onClick() }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        BasicText(text, style = NT.Type.SpineDate.copy(fontSize = 10.5.sp, lineHeight = 15.sp, color = NT.Colors.Ink.Secondary, textAlign = TextAlign.Center))
        Spacer(Modifier.height(8.dp))
        BasicText(action, Modifier.clip(Capsule()).background(pal.chip).padding(horizontal = 10.dp, vertical = 4.dp), style = NT.Type.Tag.copy(fontSize = 11.sp, color = Color.White))
    }
}

/** 金属书立。 */
@Composable
private fun Bookend() {
    Box(Modifier.width(6.dp).height(64.dp).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
        .background(Brush.horizontalGradient(listOf(Color(0xFF9A9AA2), Color(0xFFE6E6EA), Color(0xFFB4B4BB)))))
}

// =================== 列表 ===================
private fun notesList(scope: androidx.compose.foundation.lazy.LazyListScope, lectures: List<Lecture>) {
    scope.item(key = "chips") { Chips(lectures) }
    scope.item(key = "cards") { NoteCards(lectures) }
}

@Composable
private fun Chips(lectures: List<Lecture>) {
    var filter by keep<String?>("lib.filter") { null }
    val d = NT.Dim.Chip
    LazyRow(Modifier.fillMaxWidth().padding(bottom = 14.dp), contentPadding = PaddingValues(start = NT.Dim.Screen.PadH, end = NT.Dim.Screen.PadH, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(d.Gap)) {
        item {
            BasicText("全部", Modifier.height(d.H).shadow(if (filter == null) 2.dp else 0.dp, Capsule()).clip(Capsule())
                .background(if (filter == null) NT.Colors.Chip.AllBg else Color.Black.copy(alpha = 0.05f)).clickable { filter = null }
                .padding(horizontal = d.PadH, vertical = 5.dp), style = NT.Type.Chip.copy(color = NT.Colors.Chip.AllText))
        }
        items(Store.courses.toList(), key = { it.id }) { c ->
            val pal = bookPalette(c)
            val n = lectures.count { it.courseId == c.id }
            val on = filter == null || filter == c.id
            Box {
                Row(Modifier.height(d.H).clip(Capsule()).background(if (on) pal.chip else pal.chip.copy(alpha = 0.35f))
                    .clickable { filter = if (filter == c.id) null else c.id }.padding(horizontal = d.PadH), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(d.Dot).clip(Capsule()).background(Color.White.copy(alpha = 0.75f)))
                    Spacer(Modifier.width(d.DotGap))
                    BasicText(c.name, style = NT.Type.Chip.copy(color = NT.Colors.Chip.Text), maxLines = 1)
                }
                if (n > 0) Box(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-6).dp).size(d.Badge).clip(Capsule()).background(NT.Colors.Chip.BadgeBg)
                    .border(1.dp, Color.White, Capsule()), contentAlignment = Alignment.Center) {
                    BasicText("$n", style = NT.Type.ChipBadge.copy(color = NT.Colors.Chip.BadgeText))
                }
            }
        }
    }
}

@Composable
private fun NoteCards(all: List<Lecture>) {
    val filter by keep<String?>("lib.filter") { null }
    val ls = remember(all, filter) { all.filter { filter == null || it.courseId == filter }.sortedByDescending { it.date } }
    if (ls.isEmpty()) { EmptyHint("还没有笔记", "上一节课，就会多一本笔记"); return }
    val fmt = remember { SimpleDateFormat("MMM d", Locale.ENGLISH) }
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(NT.Dim.Card.Gap)) {
        ls.forEach { l ->
            val c = Store.course(l.courseId)
            val pal = bookPalette(c)
            val card = NT.Dim.Card
            val shape = RoundedCornerShape(card.R)
            Row(Modifier.fillMaxWidth().shadow(4.dp, shape, ambientColor = NT.Shadow.Card.third, spotColor = NT.Shadow.Card.third).clip(shape)
                .background(if (ThemeState.dark) Color(0xFF1C1E24).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.78f)).border(card.Border, if (ThemeState.dark) Color(0x22FFFFFF) else NT.Colors.Glass.CardBorder, shape)
                .clickable { open(l) }.padding(card.Pad)) {
                MiniCover(c, l, pal)
                Spacer(Modifier.width(card.ThumbGap))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(l.title, Modifier.weight(1f), style = NT.Type.CardTitle.copy(color = C.Ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(8.dp))
                        BasicText(fmt.format(Date(l.date)), style = NT.Type.CardDate)
                    }
                    Spacer(Modifier.height(card.TitleBodyGap))
                    val sum = remember(l.id, l.sections.size, l.notes.length) { summary(l) }
                    BasicText(if (l.status == 1) "学长正在整理这节课的笔记…" else sum.ifBlank { "（还没有内容）" }, style = NT.Type.CardBody.copy(color = C.Sec),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val job = if (l.status == 1) aiJob("notes:${l.id}") else null
                    val exams = l.segments.count { it.exam }
                    if (l.status == 1) {
                        Spacer(Modifier.height(card.ExtraTop))
                        val f by animateFloatAsState(job?.fraction?.floatValue ?: 0f, label = "lf")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicText("生成中 ${(f * 100).toInt()}%", style = NT.Type.ProgressLabel.copy(color = Color(0xFF333333)))
                            Spacer(Modifier.width(NT.Dim.Progress.LabelGap))
                            Box(Modifier.weight(1f).height(NT.Dim.Progress.H).clip(Capsule()).background(NT.Colors.Progress.Track)) {
                                Box(Modifier.fillMaxWidth(f.coerceIn(0.03f, 1f)).fillMaxHeight().clip(Capsule()).background(NT.Brushes.Generating))
                            }
                        }
                    } else if (l.cards.isNotEmpty() || exams > 0) {
                        Spacer(Modifier.height(card.ExtraTop))
                        Row(horizontalArrangement = Arrangement.spacedBy(NT.Dim.Tag.Gap)) {
                            if (l.cards.isNotEmpty()) Tag("${l.cards.size} 张复习卡")
                            if (exams > 0) Tag("★ $exams 考点")
                        }
                    }
                    if (l.status == 3) { Spacer(Modifier.height(6.dp)); BasicText("生成失败，点开重试", style = NT.Type.Tag.copy(color = C.Red)) }
                }
            }
        }
    }
}

@Composable
private fun Tag(t: String) {
    BasicText(t, Modifier.height(NT.Dim.Tag.H).clip(Capsule()).background(NT.Colors.Tag.Bg).padding(horizontal = NT.Dim.Tag.PadH, vertical = 4.dp),
        style = NT.Type.Tag.copy(color = NT.Colors.Tag.Text))
}

@Composable
private fun MiniCover(c: Course?, l: Lecture, pal: NT.BookPalette) {
    val card = NT.Dim.Card
    Box(Modifier.size(card.ThumbW, card.ThumbH).shadow(3.dp, RoundedCornerShape(card.ThumbR)).clip(RoundedCornerShape(card.ThumbR))) {
        LectureCoverArt(l, c, modifier = Modifier.fillMaxSize(), showTitle = false)
    }
}
