package app.xuezhang.ui

import androidx.compose.animation.core.animateFloat
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.*
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.xuezhang.data.Course
import app.xuezhang.data.Lecture
import app.xuezhang.data.Store
import app.xuezhang.engine.Live
import com.kyant.shapes.Capsule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** 书的外观参数（纯函数，按笔记 id 稳定）。 */
object BookLook {
    fun hash(id: String) = id.fold(7) { h, ch -> h * 31 + ch.code } and 0x7fffffff
    fun widthDp(l: Lecture) = (38 + minOf(l.sections.size, 10) * 3 + hash(l.id) % 6).coerceIn(38, 72)
    fun heightDp(l: Lecture) = 150 + hash(l.id) % 26
    /** 书脊竖排标题：去掉课程名前缀和标点，最多 maxChars 个字。 */
    fun spineTitle(title: String, course: String, maxChars: Int = 7): String {
        val t = title.removePrefix(course).trim(' ', '·', '-', '：', ':').ifBlank { title }
        return t.filterNot { it in " ·-—：:，,。（）()「」" }.take(maxChars)
    }
}

// =================== 笔记库：每门课一层书架 ===================
@Composable
fun LibraryTab() {
    val lectures = Store.lectures
    val filter = Nav.libFilter.value
    val view = Store.settings.value.noteView
    val groups = remember(lectures.toList(), Store.courses.toList(), filter) {
        val known = Store.courses.filter { filter == null || it.id == filter }.map { c -> c to lectures.filter { it.courseId == c.id }.sortedBy { it.date } }.filter { it.second.isNotEmpty() }
        val orphan = if (filter != null) emptyList() else lectures.filter { l -> Store.courses.none { it.id == l.courseId } }
        known.map { Triple(it.first.id, it.first as Course?, it.second) } + if (orphan.isNotEmpty()) listOf(Triple("orphan", null as Course?, orphan)) else emptyList()
    }
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            TopInset()
            SerifHeader("笔记", "${Store.courses.count { c -> lectures.any { it.courseId == c.id } }} 门课 · ${lectures.size} 本笔记") { NoteViewToggle() }
        }
        if (view == 1 || filter != null) item {
            // 列表模式（或从课程详情进来）：按课程筛选
            CourseFilterChips(filter) { Nav.libFilter.value = it }
            Spacer(Modifier.height(6.dp))
        }
        if (lectures.isEmpty()) item { EmptyHint("书架还是空的", "上一节课，就会多一本笔记") }
        if (view == 1) {
            val ls = lectures.filter { filter == null || it.courseId == filter }.sortedByDescending { it.date }
            items(ls, key = { "row_" + it.id }) { l -> LectureListRow(l) }
        } else groups.forEach { (key, c, ls) -> item(key = "shelf_$key") { Bookshelf(c?.name ?: "未分类", c, ls) } }
        item { BottomSpacer() }
    }
}

@Composable
private fun Bookshelf(name: String, course: Course?, books: List<Lecture>) {
    val base = course?.let { C.course[it.colorIdx % C.course.size] } ?: C.FillGray
    Column(Modifier.padding(top = 18.dp)) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(Capsule()).background(base)); Spacer(Modifier.width(8.dp))
            BasicText(name, Modifier.weight(1f).then(if (course != null) Modifier.clickable { Nav.push(Route.CourseDetail(course.id)) } else Modifier),
                style = T.Item.copy(color = base, fontSize = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText("${books.size} 本", style = T.Sub)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(190.dp).padding(end = 18.dp), verticalAlignment = Alignment.Bottom) {
            // 书脊（竖排标题、烫金线、科目配色）
            LazyRow(Modifier.weight(1f).height(190.dp), contentPadding = PaddingValues(start = 24.dp, end = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom) {
                items(books, key = { it.id }) { l -> BookSpine(l, course, base) }
            }
            // 最新一本正面朝外的精装封面
            books.lastOrNull()?.let { l ->
                Box(Modifier.size(104.dp, 150.dp).shadow(10.dp, RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp, topStart = 3.dp, bottomStart = 3.dp))
                    .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp, topStart = 3.dp, bottomStart = 3.dp))
                    .background(Brush.horizontalGradient(listOf(lerp(base, Color.Black, 0.25f), base, lerp(base, Color.White, 0.08f))))
                    .clickable { Nav.push(if (l.status == 1) Route.Generating(l.id) else Route.Book(l.id)) }) {
                    Box(Modifier.padding(start = 7.dp).width(1.5.dp).fillMaxHeight().background(Color(0x44FFFFFF)))
                    Box(Modifier.align(Alignment.Center).size(46.dp, 36.dp).border(1.5.dp, Color(0xCCF3D27A), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                        BasicText((course?.name ?: l.title).take(1), style = T.Head.copy(color = Color(0xFFF3D27A)))
                    }
                    BasicText(BookLook.spineTitle(l.title, course?.name.orEmpty(), 10), Modifier.align(Alignment.BottomCenter).padding(8.dp),
                        style = T.Cap.copy(color = Color.White.copy(alpha = 0.9f), fontSize = 10.sp, textAlign = TextAlign.Center), maxLines = 2)
                    // 书签丝带
                    Box(Modifier.align(Alignment.BottomCenter).offset(y = 6.dp).size(8.dp, 16.dp).background(lerp(base, Color.Black, 0.35f)))
                }
            }
        }
        // 玻璃隔板
        Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(12.dp).shadow(8.dp, RoundedCornerShape(6.dp), ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
            .clip(RoundedCornerShape(6.dp)).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), Color(0xFFE9E4EE)))).border(0.6.dp, Color.White, RoundedCornerShape(6.dp)))
    }
}

@Composable
private fun BookSpine(l: Lecture, course: Course?, base: Color) {
    val h = BookLook.hash(l.id)
    val color = lerp(base, if (h % 2 == 0) Color.Black else Color.White, (h % 5) * 0.06f)
    val date = remember(l.date) { SimpleDateFormat("M/d", Locale.CHINA).format(Date(l.date)) }
    val title = remember(l.title) { BookLook.spineTitle(l.title, course?.name.orEmpty()) }
    Box(Modifier.padding(bottom = 0.dp).size(BookLook.widthDp(l).dp, BookLook.heightDp(l).dp)
        .shadow(3.dp, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
        .background(Brush.horizontalGradient(listOf(lerp(color, Color.Black, 0.18f), color, lerp(color, Color.White, 0.12f), color, lerp(color, Color.Black, 0.22f))))
        .clickable { Nav.push(if (l.status == 1) Route.Generating(l.id) else Route.Book(l.id)) }
    ) {
        // 烫金线
        Box(Modifier.align(Alignment.TopCenter).padding(top = 12.dp).fillMaxWidth().height(2.dp).background(Color(0xCCF3D27A)))
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp).fillMaxWidth().height(2.dp).background(Color(0xCCF3D27A)))
        BasicText(title.toList().joinToString("\n"), Modifier.align(Alignment.Center).padding(vertical = 22.dp),
            style = T.Btn.copy(color = Color.White, fontSize = 13.sp, lineHeight = 15.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold), maxLines = 8)
        BasicText(date, Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp), style = T.Cap.copy(color = Color.White.copy(alpha = 0.85f), fontSize = 9.sp))
        if (l.status == 1 || l.status == 3) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(7.dp).clip(Capsule()).background(if (l.status == 1) C.Orange else C.Red))
    }
}

// =================== 打开一本笔记：翻页阅读 ===================
private sealed interface BookPage {
    data object Cover : BookPage
    data object Toc : BookPage
    data class Sec(val i: Int) : BookPage
    data object Mnemonics : BookPage
    data object Exams : BookPage
    data object End : BookPage
}

@Composable
fun BookScreen(id: String) {
    val l = Store.lecture(id) ?: run { Nav.pop(); return }
    val course = Store.course(l.courseId)
    val sections = remember(l.sections, l.notes) {
        l.sections.ifEmpty { l.notes.split(Regex("\n\\s*\n")).filter { it.isNotBlank() }.map { app.xuezhang.data.NoteSection("", emptyList(), it.trim()) } }
    }
    val pages = remember(sections.size, l.mnemonics.size, l.segments.count { it.exam }) {
        buildList {
            add(BookPage.Cover); if (sections.size > 1) add(BookPage.Toc)
            sections.indices.forEach { add(BookPage.Sec(it)) }
            if (l.mnemonics.isNotEmpty()) add(BookPage.Mnemonics)
            if (l.segments.any { it.exam }) add(BookPage.Exams)
            add(BookPage.End)
        }
    }
    val state = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val base = course?.let { C.course[it.colorIdx % C.course.size] } ?: C.Blue
    fun label(p: Int): String = when (val pg = pages.getOrNull(p)) {
        is BookPage.Sec -> "第 ${pg.i + 1} 节 · ${p + 1}/${pages.size}"
        BookPage.Cover -> "封面 · 1/${pages.size}"
        BookPage.Toc -> "目录 · ${p + 1}/${pages.size}"
        else -> "${p + 1}/${pages.size}"
    }
    Box(Modifier.fillMaxSize()) {
        // 精装书壳：科目色硬封面，书页叠在上面
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(top = 62.dp, bottom = 118.dp).windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 6.dp).shadow(14.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(lerp(base, Color.Black, 0.25f), base, lerp(base, Color.Black, 0.1f)))))
        HorizontalPager(state, Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(top = 68.dp, bottom = 124.dp)
            .windowInsetsPadding(WindowInsets.navigationBars), beyondViewportPageCount = 1) { p ->
            // 翻页：当前页以左侧书脊为轴向外翻（3D 旋转 + 阴影），下一页留在原地被逐渐露出
            val off = (state.currentPage - p) + state.currentPageOffsetFraction
            BoxWithConstraints(Modifier.fillMaxSize().zIndex(off.coerceIn(-1f, 1f))) {
                val w = constraints.maxWidth.toFloat()
                Box(Modifier.fillMaxSize().padding(horizontal = 14.dp)
                    .graphicsLayer {
                        if (off in -1f..1f) translationX = off * w
                        if (off > 0f) {
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            rotationY = -100f * off.coerceAtMost(1f)
                            cameraDistance = 18f * density
                        }
                    }
                    .shadow(10.dp, RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp, topStart = 4.dp, bottomStart = 4.dp))
                    .clip(RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp, topStart = 4.dp, bottomStart = 4.dp))
                    .background(Color(0xFFFFFAEE))
                    .drawWithContent {
                        drawContent()
                        // 书脊处的折痕阴影
                        drawRect(Brush.horizontalGradient(listOf(Color(0x33000000), Color.Transparent), endX = 18.dp.toPx()))
                        if (off > 0f) {
                            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f * off.coerceAtMost(1f))), startX = size.width * 0.3f))
                            // 卷页近似：右下角折起的纸背 + 高光（真实卷曲物理没有做，用渐变三角近似）
                            val k = off.coerceIn(0f, 1f); val fw = size.width * (0.18f + 0.5f * k); val fh = size.height * (0.12f + 0.4f * k)
                            val path = androidx.compose.ui.graphics.Path().apply { moveTo(size.width, size.height - fh); lineTo(size.width - fw, size.height); lineTo(size.width, size.height); close() }
                            drawPath(path, Brush.linearGradient(listOf(Color(0xFFF3E9D2), Color(0xFFD9CBB0), Color(0xFFFFFAEE)), start = androidx.compose.ui.geometry.Offset(size.width - fw, size.height), end = androidx.compose.ui.geometry.Offset(size.width, size.height - fh)))
                        }
                        if (off < 0f) drawRect(Color.Black.copy(alpha = 0.32f * (-off).coerceAtMost(1f)))
                    }
                ) {
                    when (val pg = pages[p]) {
                        BookPage.Cover -> CoverPage(l, course, base)
                        BookPage.Toc -> PageScroll(p, pages.size) {
                            BasicText("目录", style = T.Title)
                            Spacer(Modifier.height(10.dp))
                            sections.forEachIndexed { i, s ->
                                Row(Modifier.fillMaxWidth().clickable { scope.launch { state.animateScrollToPage(pages.indexOf(BookPage.Sec(i))) } }.padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    BasicText(s.title.ifBlank { "第 ${i + 1} 节" }, Modifier.weight(1f), style = T.Label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    BasicText("${pages.indexOf(BookPage.Sec(i)) + 1}", style = T.Sub)
                                }
                                Divider()
                            }
                        }
                        is BookPage.Sec -> PageScroll(p, pages.size) {
                            val s = sections[pg.i]
                            if (s.title.isNotBlank()) { BasicText(s.title, style = T.Head.copy(fontSize = 19.sp)); Spacer(Modifier.height(10.dp)) }
                            if (s.frame.isNotBlank()) { FrameImage(l, pg.i, s); Spacer(Modifier.height(8.dp)) }
                            SlideStrip(course, s.pages)
                            NoteBody(s.body, onMath = { Nav.sheet.value = Sheet.Formula(it, s.body) })
                        }
                        BookPage.Mnemonics -> PageScroll(p, pages.size) {
                            BasicText("助记", style = T.Title); Spacer(Modifier.height(10.dp))
                            l.mnemonics.forEach { m ->
                                BasicText(m.title, style = T.Item.copy(color = C.HlOrange)); Spacer(Modifier.height(4.dp))
                                RichText(m.body, style = T.Body.copy(fontSize = 15.sp)); Spacer(Modifier.height(14.dp))
                            }
                        }
                        BookPage.Exams -> PageScroll(p, pages.size) {
                            BasicText("课上标记的考点", style = T.Title); Spacer(Modifier.height(10.dp))
                            l.segments.filter { it.exam }.forEach { s ->
                                Row { Tag(Live.fmt(s.startMs), C.HlRed); if (s.page > 0) { Spacer(Modifier.width(6.dp)); Tag("P.${s.page}", C.Blue) } }
                                Spacer(Modifier.height(6.dp))
                                RichText(s.voice, onMath = { Nav.sheet.value = Sheet.Formula(it, s.voice) })
                                Spacer(Modifier.height(14.dp))
                            }
                        }
                        BookPage.End -> Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            BasicText("读完啦", style = T.Large.copy(fontSize = 30.sp))
                            Spacer(Modifier.height(6.dp))
                            BasicText("${l.cards.size} 张复习卡等你来过一遍", style = T.Sub)
                            Spacer(Modifier.height(22.dp))
                            Btn("复习卡 / 概念图 / 实录", { Nav.replace(Route.Note(id)) }, Modifier.fillMaxWidth(), icon = "note", tint = C.Blue)
                            Spacer(Modifier.height(10.dp))
                            Btn("回到封面", { scope.launch { state.animateScrollToPage(0) } }, Modifier.fillMaxWidth(), icon = "back")
                        }
                    }
                }
            }
        }
        // 顶栏
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).shadow(4.dp, Capsule()).clip(Capsule()).background(Color.White.copy(alpha = 0.85f)).clickable { Nav.pop() }, contentAlignment = Alignment.Center) { Icon("back", C.Ink, 20.dp, 2.2f) }
            BasicText(listOfNotNull(course?.name?.take(6), l.title).joinToString(" · "), Modifier.weight(1f).padding(horizontal = 10.dp), style = T.Item.copy(fontSize = 17.sp, textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText("完整笔记", Modifier.clip(Capsule()).background(Color.White.copy(alpha = 0.85f)).clickable { Nav.replace(Route.Note(id)) }
                .padding(horizontal = 12.dp, vertical = 8.dp), style = T.Btn.copy(fontSize = 13.sp))
        }
        // 底部：可拖动跳页的进度条 + 目录 / 复习卡 / 导图
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 6.dp)) {
            PageScrubber(state.currentPage, pages.size, ::label, thumb = { p ->
                when (val pg = pages.getOrNull(p)) {
                    is BookPage.Sec -> {
                        val sec = sections[pg.i]
                        val sp = course?.slidePath.orEmpty()
                        if (sec.pages.isNotEmpty() && sp.isNotBlank() && java.io.File(sp).exists()) SlideThumb(sp, sec.pages.first(), widthPx = 320)
                        else BasicText(sec.title.ifBlank { sec.body.take(40) }, Modifier.padding(6.dp), style = T.Cap.copy(fontSize = 10.sp, color = C.Ink), maxLines = 4)
                    }
                    else -> BasicText(label(p), Modifier.padding(6.dp), style = T.Cap.copy(fontSize = 10.sp, color = C.Ink))
                }
            }) { p -> scope.launch { state.scrollToPage(p) } }
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                DockItem("list", "目录") { val t = pages.indexOf(BookPage.Toc).takeIf { it >= 0 } ?: 0; scope.launch { state.animateScrollToPage(t) } }
                DockItem("clipboard", "复习卡") { Nav.push(Route.Cards(id)) }
                DockItem("grid", "导图") { Nav.push(Route.MindMap(id)) }
            }
        }
    }
}

@Composable
private fun PageScroll(page: Int, total: Int, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 26.dp, end = 20.dp, top = 22.dp, bottom = 12.dp)) { content() }
        BasicText("— ${page + 1} / $total —", Modifier.fillMaxWidth().padding(bottom = 10.dp), style = T.Cap.copy(fontSize = 11.sp, textAlign = TextAlign.Center))
    }
}

@Composable
private fun CoverPage(l: Lecture, course: Course?, base: Color) {
    val date = remember(l.date) { SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(l.date)) }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(base, lerp(base, Color.Black, 0.35f)))).padding(28.dp)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xCCF3D27A)))
        Spacer(Modifier.height(28.dp))
        BasicText(course?.name ?: "笔记", style = T.Sub.copy(color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp))
        Spacer(Modifier.height(8.dp))
        BasicText(l.title, style = T.Large.copy(color = Color.White, fontSize = 30.sp, lineHeight = 38.sp))
        Spacer(Modifier.weight(1f))
        if (course != null) CourseCover(course, Modifier.fillMaxWidth().aspectRatio(16f / 9f), radius = 14.dp)
        Spacer(Modifier.height(18.dp))
        BasicText(listOfNotNull(date, "${l.durationMs / 60000} 分钟", "${l.sections.size} 节", if (l.cards.isNotEmpty()) "${l.cards.size} 张复习卡" else null).joinToString(" · "),
            style = T.Cap.copy(color = Color.White.copy(alpha = 0.85f), fontSize = 12.5.sp))
        Spacer(Modifier.height(10.dp))
        BasicText("← 向左滑动翻页", style = T.Cap.copy(color = Color.White.copy(alpha = 0.6f)))
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xCCF3D27A)))
    }
}

/** 录播课这一节对应的视频原画面；B 站来源点按可换一张。 */
@Composable
fun FrameImage(l: Lecture, secIdx: Int, s: app.xuezhang.data.NoteSection) {
    val img by produceState<ImageBitmap?>(null, s.frame) { value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(s.frame)?.asImageBitmap() }.getOrNull() } }
    val bili = app.xuezhang.engine.Bili.srcBv(l.source) != null && app.xuezhang.engine.Bili.srcCid(l.source) != null
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1D1D1F))
        .then(if (bili) Modifier.clickable { Nav.sheet.value = Sheet.Frames(l.id, secIdx) } else Modifier)) {
        img?.let { Image(it, "视频画面", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) } ?: Shimmer(Modifier.fillMaxSize())
        if (s.atMs >= 0) BasicText("▶ " + Live.fmt(s.atMs), Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xCC1D1D1F))
            .padding(horizontal = 7.dp, vertical = 2.dp), style = T.Cap.copy(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp))
        if (bili) BasicText("换一张", Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(Capsule()).background(Color(0x991D1D1F))
            .padding(horizontal = 9.dp, vertical = 3.dp), style = T.Cap.copy(color = Color.White, fontSize = 11.sp))
    }
}

/** 加载占位：一道斜光扫过。 */
@Composable
fun Shimmer(modifier: Modifier = Modifier) {
    val x = androidx.compose.animation.core.rememberInfiniteTransition(label = "shim").animateFloat(-1f, 2f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1200)), label = "shimx")
    Box(modifier.drawWithContent {
        drawRect(Color(0xFFE9E9EE))
        val w = size.width
        drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.7f), Color.Transparent),
            start = androidx.compose.ui.geometry.Offset(w * x.value - w * 0.3f, 0f), end = androidx.compose.ui.geometry.Offset(w * x.value + w * 0.3f, size.height)))
    })
}

/** B 站截图选择器：在这一节时间点前后挑一张视频原画面。 */
@Composable
fun FramePickerContent(s: Sheet.Frames) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val l = Store.lecture(s.lectureId)
    val sec = l?.sections?.getOrNull(s.secIdx)
    val bv = l?.let { app.xuezhang.engine.Bili.srcBv(it.source) }
    val cid = l?.let { app.xuezhang.engine.Bili.srcCid(it.source) }
    val shots by produceState<List<Pair<Int, android.graphics.Bitmap>>?>(null, s) {
        value = if (l == null || sec == null || bv == null || cid == null) emptyList()
        else withContext(Dispatchers.IO) { app.xuezhang.engine.Frames.nearby(bv, cid, (l.durationMs / 1000).toInt(), (maxOf(0, sec.atMs) / 1000).toInt()) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText("换一张视频截图", Modifier.weight(1f), style = T.Title)
        Box(Modifier.size(36.dp).clip(Capsule()).background(C.Fill).clickable { Nav.sheet.value = null }, contentAlignment = Alignment.Center) { Icon("x", C.Ink, 16.dp, 2.2f) }
    }
    BasicText(sec?.title.orEmpty() + (sec?.atMs?.takeIf { it >= 0 }?.let { " · 前后约 1 分钟" } ?: ""), style = T.Cap)
    Spacer(Modifier.height(10.dp))
    when {
        shots == null -> Shimmer(Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(14.dp)))
        shots!!.isEmpty() -> BasicText("B 站没有返回这段视频的截图", style = T.Sub)
        else -> Column(Modifier.height(380.dp).verticalScroll(rememberScrollState())) {
            shots!!.chunked(3).forEach { row ->
                Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (t, bm) ->
                        Box(Modifier.weight(1f).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).clickable {
                            val path = app.xuezhang.engine.Frames.saveChosen(ctx, bm, s.lectureId, s.secIdx)
                            Store.lecture(s.lectureId)?.let { cur -> Store.upsertLecture(cur.copy(sections = cur.sections.mapIndexed { i, x -> if (i == s.secIdx) x.copy(frame = path, atMs = t * 1000L) else x })) }
                            Island.show("已换成 ${Live.fmt(t * 1000L)} 的画面", Island.Kind.Done); Nav.sheet.value = null
                        }) {
                            Image(bm.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            BasicText(Live.fmt(t * 1000L), Modifier.align(Alignment.BottomStart).background(Color(0x991D1D1F)).padding(horizontal = 4.dp),
                                style = T.Cap.copy(color = Color.White, fontSize = 10.sp))
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
