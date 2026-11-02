package app.xuezhang.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.xuezhang.R
import app.xuezhang.data.Course
import app.xuezhang.data.Lecture
import app.xuezhang.data.Store
import app.xuezhang.engine.Live
import com.kyant.shapes.Capsule
import eu.wewox.pagecurl.ExperimentalPageCurlApi
import eu.wewox.pagecurl.config.PageCurlConfig
import eu.wewox.pagecurl.config.rememberPageCurlConfig
import eu.wewox.pagecurl.page.PageCurl
import eu.wewox.pagecurl.page.rememberPageCurlState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =================== v0.0.7.6 翻书阅读：精装书摊开 + 真实卷页（oleksandrbalan/pagecurl）===================

private sealed interface BookPage {
    data object Cover : BookPage
    data object Toc : BookPage
    data class Sec(val i: Int) : BookPage
    data object Mnemonics : BookPage
    data object Exams : BookPage
    data object End : BookPage
}

private val Paper = NT.Colors.Reader.Paper
private val PaperEdge = NT.Colors.Reader.PaperEdge
private val Ink = NT.Colors.Reader.Text
private val Orn = NT.Colors.Reader.Ornament

/** 页码 → 第几节（翻书进度条气泡用）。纯函数，便于测试。 */
object ReaderMath {
    /** 拖动进度条：x 在 [0,w] → 第几页（0 起）。 */
    fun pageAt(x: Float, w: Float, count: Int): Int = if (count <= 1 || w <= 0f) 0 else ((x / w).coerceIn(0f, 1f) * (count - 1) + 0.5f).toInt()
    fun fraction(page: Int, count: Int): Float = if (count <= 1) 0f else page.toFloat() / (count - 1)
}

@OptIn(ExperimentalPageCurlApi::class)
@Composable
fun BookScreen(id: String, startPage: Int = 0) {
    val l = Store.lecture(id) ?: run { Nav.pop(); return }
    val course = Store.course(l.courseId)
    val hc = remember(l.coverStyle, course?.name, course?.cover) { lectureHc(l, course) }
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
    val curl = rememberPageCurlState(initialCurrent = startPage)
    val scope = rememberCoroutineScope()
    val config = rememberPageCurlConfig(
        backPageColor = Paper, backPageContentAlpha = 0.10f, shadowColor = Color(0xFF3A2E1A), shadowAlpha = 0.28f, shadowRadius = 18.dp,
        dragInteraction = PageCurlConfig.StartEndDragInteraction(
            forward = PageCurlConfig.StartEndDragInteraction.Config(start = Rect(0.55f, 0f, 1f, 1f), end = Rect(0f, 0f, 0.5f, 1f)),
            backward = PageCurlConfig.StartEndDragInteraction.Config(start = Rect(0f, 0f, 0.45f, 1f), end = Rect(0.5f, 0f, 1f, 1f)),
        ),
        tapInteraction = PageCurlConfig.TargetTapInteraction(
            forward = PageCurlConfig.TargetTapInteraction.Config(Rect(0.78f, 0f, 1f, 1f)),
            backward = PageCurlConfig.TargetTapInteraction.Config(Rect(0f, 0f, 0.22f, 1f)),
        ),
        tapCustomEnabled = false,
    )
    fun jump(p: Int) = scope.launch { curl.snapTo(p.coerceIn(0, pages.size - 1)) }
    val cur = curl.current.coerceIn(0, pages.size - 1)
    val secOf: (Int) -> Int = { p -> (pages.getOrNull(p) as? BookPage.Sec)?.i ?: -1 }
    val navTitle = (course?.name?.takeIf { it.length <= 6 }?.let { "$it·" } ?: "") + l.title.removePrefix(course?.name.orEmpty()).trim(' ', '·', '-', '：', ':').ifBlank { l.title }

    Box(Modifier.fillMaxSize().background(NT.Brushes.Background).dotGrid()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
            // 顶栏：返回 + 衬线标题
            Box(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp)) {
                Box(Modifier.align(Alignment.CenterStart).size(42.dp).shadow(6.dp, Capsule(), ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
                    .clip(Capsule()).background(Color.White.copy(alpha = 0.92f)).clickable { Nav.pop() }, contentAlignment = Alignment.Center) { Icon("back", Color(0xFF1A1A1A), 20.dp, 2.2f) }
                BasicText(navTitle, Modifier.align(Alignment.Center).padding(horizontal = 56.dp), style = NT.Type.ReaderNav.copy(fontSize = 19.sp, textAlign = TextAlign.Center),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            // 摊开的精装书
            OpenBook(hc, Modifier.fillMaxWidth().weight(1f)) {
                PageCurl(count = pages.size, state = curl, config = config, modifier = Modifier.fillMaxSize()) { p ->
                    PaperPage(p, pages.size) {
                        when (val pg = pages[p]) {
                            BookPage.Cover -> CoverLeaf(l, course, hc)
                            BookPage.Toc -> PageScroll {
                                ChapterHead("目　录")
                                sections.forEachIndexed { i, s ->
                                    Row(Modifier.fillMaxWidth().clickable { jump(pages.indexOf(BookPage.Sec(i))) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                        BasicText(s.title.ifBlank { "第 ${i + 1} 节" }, Modifier.weight(1f), style = T.Label.copy(color = Ink, fontFamily = Serif, fontSize = 16.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        BasicText("${pages.indexOf(BookPage.Sec(i)) + 1}", style = T.Sub.copy(color = NT.Colors.Reader.Caption))
                                    }
                                    Box(Modifier.fillMaxWidth().height(0.6.dp).background(Orn.copy(alpha = 0.5f)))
                                }
                            }
                            is BookPage.Sec -> PageScroll {
                                val s = sections[pg.i]
                                ChapterHead(s.title.ifBlank { "第 ${pg.i + 1} 节" })
                                if (s.frame.isNotBlank()) { FrameImage(l, pg.i, s); Spacer(Modifier.height(14.dp)) }
                                else ReaderSlide(course, s.pages)
                                CompositionLocalProvider(LocalPaperStyle provides true) {
                                    NoteBody(s.body, onMath = { Nav.sheet.value = Sheet.Formula(it, s.body) }, style = ReaderBody)
                                }
                                val exams = l.segments.filter { it.exam && (s.pages.isEmpty() || it.page in s.pages) }.take(if (s.pages.isEmpty()) 0 else 3)
                                exams.forEach { e ->
                                    Spacer(Modifier.height(12.dp))
                                    ExamTag()
                                    Spacer(Modifier.height(4.dp))
                                    RichText(e.voice, style = ReaderBody, onMath = { Nav.sheet.value = Sheet.Formula(it, e.voice) })
                                }
                            }
                            BookPage.Mnemonics -> PageScroll {
                                ChapterHead("助　记")
                                l.mnemonics.forEach { m ->
                                    BasicText(m.title, style = T.Item.copy(color = NT.Colors.Reader.ExamText)); Spacer(Modifier.height(4.dp))
                                    RichText(m.body, style = ReaderBody); Spacer(Modifier.height(14.dp))
                                }
                            }
                            BookPage.Exams -> PageScroll {
                                ChapterHead("课上标记的考点")
                                l.segments.filter { it.exam }.forEach { s ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        ExamTag(); Spacer(Modifier.width(8.dp))
                                        BasicText(Live.fmt(s.startMs) + if (s.page > 0) " · 课件第 ${s.page} 页" else "", style = T.Cap.copy(color = NT.Colors.Reader.Caption))
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    RichText(s.voice, style = ReaderBody, onMath = { Nav.sheet.value = Sheet.Formula(it, s.voice) })
                                    Spacer(Modifier.height(14.dp))
                                }
                            }
                            BookPage.End -> Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Ornament(true)
                                Spacer(Modifier.height(10.dp))
                                BasicText("读完啦", style = T.Large.copy(fontSize = 30.sp, color = Ink))
                                Spacer(Modifier.height(10.dp))
                                Ornament(false)
                                Spacer(Modifier.height(10.dp))
                                BasicText("${l.cards.size} 张复习卡等你来过一遍", style = T.Sub.copy(color = NT.Colors.Reader.Caption))
                                Spacer(Modifier.height(22.dp))
                                Btn("开始复习", { Nav.push(Route.Review(id)) }, Modifier.fillMaxWidth(), icon = "layers", tint = hc.color)
                                Spacer(Modifier.height(10.dp))
                                Btn("完整笔记 / 概念图 / 实录", { Nav.replace(Route.Note(id)) }, Modifier.fillMaxWidth(), icon = "filetext")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Scrubber(cur, pages.size, label = { p ->
                val si = secOf(p)
                when {
                    si >= 0 -> "第 ${si + 1} 节 · ${p + 1}/${pages.size}"
                    pages.getOrNull(p) == BookPage.Cover -> "封面 · ${p + 1}/${pages.size}"
                    pages.getOrNull(p) == BookPage.Toc -> "目录 · ${p + 1}/${pages.size}"
                    else -> "${p + 1}/${pages.size}"
                }
            }, thumb = { p -> sections.getOrNull(secOf(p))?.pages?.firstOrNull() }, course = course) { jump(it) }
            // 工具栏
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolItem("list", "目录") { jump(if (sections.size > 1) 1 else 0) }
                Spacer(Modifier.weight(1f))
                ToolItem("layers", "复习卡") { Nav.push(Route.Review(id)) }
                Spacer(Modifier.weight(1f))
                ToolItem("hexset", "导图") { NoteSeg.next = 2; Nav.push(Route.Note(id)) }
            }
        }
    }
}

private val ReaderBody get() = T.Body.copy(fontSize = 16.5.sp, lineHeight = 29.sp, color = Ink)

/** 背景细点阵（同概念图）。 */
fun Modifier.dotGrid(color: Color = NT.Colors.Bg.Dot) = this.drawBehind {
    val step = 18.dp.toPx(); val r = 1.1.dp.toPx()
    var y = step / 2
    while (y < size.height) { var x = step / 2; while (x < size.width) { drawCircle(color, r, Offset(x, y)); x += step }; y += step }
}

/** 摊开的精装书：布面书壳（露出上/下/右边）、左侧一摞已读页、右侧/下方书页厚度、顶部书签丝带。 */
@Composable
private fun OpenBook(hc: app.xuezhang.engine.HardCovers.Hc, modifier: Modifier, content: @Composable () -> Unit) {
    val linen = ImageBitmap.imageResource(R.drawable.hc_linen)
    val linenBrush = remember(linen) { ShaderBrush(ImageShader(linen, TileMode.Repeated, TileMode.Repeated)) }
    Box(modifier) {
        // 书壳
        Canvas(Modifier.fillMaxSize().padding(end = 10.dp)) {
            val r = 7.dp.toPx()
            drawRoundRect(Brush.verticalGradient(listOf(hc.lightColor, hc.color, hc.darkColor)), Offset(-r * 3, 0f), Size(size.width + r * 3, size.height), CornerRadius(r))
            drawRoundRect(linenBrush, Offset(-r * 3, 0f), Size(size.width + r * 3, size.height), CornerRadius(r), alpha = 0.5f)
            drawRoundRect(Color.Black.copy(alpha = 0.22f), Offset(-r * 3, size.height - 3.dp.toPx()), Size(size.width + r * 3, 3.dp.toPx()), CornerRadius(r))
        }
        // 书页块
        Box(Modifier.fillMaxSize().padding(top = 7.dp, bottom = 9.dp, end = 16.dp)) {
            // 左侧已读页（一摞纸边）
            Canvas(Modifier.fillMaxHeight().width(30.dp)) {
                val n = 9
                for (i in 0 until n) {
                    val x = i * size.width / n
                    drawRect(lerp(PaperEdge, Paper, i / n.toFloat()), Offset(x, 0f), Size(size.width - x, size.height))
                    drawLine(Color(0x22000000), Offset(x, 0f), Offset(x, size.height), 0.8f)
                }
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0x33000000)), startX = size.width * 0.6f, endX = size.width), Offset.Zero, size)
            }
            // 右侧 / 下方书页厚度
            Canvas(Modifier.fillMaxSize().padding(start = 30.dp)) {
                for (i in 1..4) {
                    val o = i * 1.6.dp.toPx()
                    drawRect(lerp(Paper, PaperEdge, i / 4f), Offset(o, o), Size(size.width, size.height))
                    drawLine(Color(0x1F000000), Offset(size.width + o, o), Offset(size.width + o, size.height + o - 1f), 0.8f)
                }
            }
            Box(Modifier.fillMaxSize().padding(start = 30.dp, end = 6.dp, bottom = 6.dp)) { content() }
        }
        // 书签丝带
        Canvas(Modifier.offset(x = 58.dp, y = (-10).dp).size(30.dp, 26.dp).zIndex(3f)) {
            val w = size.width; val h = size.height
            val rib = lerp(hc.lightColor, Color(0xFF6FD1A9), 0.35f)
            val p = Path().apply { moveTo(0f, h * 0.25f); lineTo(w * 0.5f, 0f); lineTo(w, h * 0.25f); lineTo(w, h); lineTo(0f, h); close() }
            drawPath(p, rib)
            drawPath(Path().apply { moveTo(0f, h * 0.25f); lineTo(w * 0.5f, 0f); lineTo(w, h * 0.25f); lineTo(w * 0.5f, h * 0.45f); close() }, Color.Black.copy(alpha = 0.15f))
            drawLine(Color.White.copy(alpha = 0.3f), Offset(2.dp.toPx(), h * 0.3f), Offset(2.dp.toPx(), h), 1.2.dp.toPx())
        }
    }
}

/** 一页纸：暖色纸 + 细纤维纹 + 书脊一侧的阴影 + 页脚页码。 */
@Composable
private fun PaperPage(p: Int, total: Int, content: @Composable () -> Unit) {
    val linen = ImageBitmap.imageResource(R.drawable.hc_linen)
    val fiber = remember(linen) { ShaderBrush(ImageShader(linen, TileMode.Repeated, TileMode.Repeated)) }
    Box(Modifier.fillMaxSize().drawBehind {
        drawRect(Brush.verticalGradient(listOf(Color(0xFFF8F2E2), Paper, Color(0xFFF2EAD4))))
        drawRect(fiber, alpha = 0.07f)
        drawRect(Brush.horizontalGradient(listOf(Color(0x30000000), Color(0x0A000000), Color.Transparent), endX = 22.dp.toPx()))
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0x10000000)), startX = size.width - 10.dp.toPx()))
    }) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
            if (p > 0) BasicText("— ${p + 1} —", Modifier.fillMaxWidth().padding(bottom = 8.dp), style = T.Cap.copy(fontSize = 11.sp, color = NT.Colors.Reader.Caption, textAlign = TextAlign.Center))
        }
    }
}

@Composable
private fun PageScroll(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 26.dp, end = 22.dp, top = 26.dp, bottom = 10.dp)) { content() }
}

@Composable
private fun Ornament(hollow: Boolean) {
    Canvas(Modifier.width(NT.Dim.Reader.OrnamentW).height(10.dp)) {
        val cy = size.height / 2; val cx = size.width / 2; val d = 4.dp.toPx()
        drawLine(Orn, Offset(0f, cy), Offset(cx - d * 2, cy), 1.dp.toPx())
        drawLine(Orn, Offset(cx + d * 2, cy), Offset(size.width, cy), 1.dp.toPx())
        val p = Path().apply { moveTo(cx, cy - d); lineTo(cx + d, cy); lineTo(cx, cy + d); lineTo(cx - d, cy); close() }
        if (hollow) drawPath(p, Orn, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())) else drawPath(p, Color(0xFFB59A72))
    }
}

@Composable
private fun ChapterHead(title: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Ornament(true)
        Spacer(Modifier.height(10.dp))
        BasicText(title, style = NT.Type.Chapter.copy(fontSize = 24.sp, lineHeight = 32.sp, color = NT.Colors.Reader.Heading, textAlign = TextAlign.Center))
        Spacer(Modifier.height(10.dp))
        Ornament(false)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ExamTag() {
    BasicText("★考点", Modifier.clip(RoundedCornerShape(5.dp)).background(NT.Colors.Reader.ExamBg).padding(horizontal = 6.dp, vertical = 1.dp),
        style = T.Btn.copy(fontSize = 14.sp, color = NT.Colors.Reader.ExamText, fontWeight = FontWeight.Bold))
}

/** 课件页：白色衬纸 + 投影 + 图注「课件第 N 页」。 */
@Composable
private fun ReaderSlide(c: Course?, pages: List<Int>) {
    val path = c?.slidePath.orEmpty()
    if (path.isBlank() || pages.isEmpty() || !File(path).exists()) return
    val total = remember(path) { SlideImages.pageCount(path) }
    val page = pages.firstOrNull { total == 0 || it <= total } ?: return
    val img by produceState<ImageBitmap?>(null, path, page) { value = withContext(Dispatchers.IO) { runCatching { SlideImages.render(path, page, 1400)?.asImageBitmap() }.getOrNull() } }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth(0.94f).shadow(5.dp, RoundedCornerShape(NT.Dim.Reader.SlideR), ambientColor = Color(0x44000000), spotColor = Color(0x44000000))
            .background(Color.White).padding(NT.Dim.Reader.SlideFrame).aspectRatio(16f / 10.5f)
            .clickable { SlideViewer.open.value = Triple(path, page, total) }) {
            img?.let { Image(it, "课件第 $page 页", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) } ?: Shimmer(Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(NT.Dim.Reader.CaptionGap))
        BasicText("课件第 $page 页" + if (pages.size > 1) " 等 ${pages.size} 页" else "", style = T.Cap.copy(fontSize = 13.sp, color = NT.Colors.Reader.Caption))
        Spacer(Modifier.height(18.dp))
    }
}

/** 扉页：精装封面缩小居中 + 课程信息。 */
@Composable
private fun CoverLeaf(l: Lecture, course: Course?, hc: app.xuezhang.engine.HardCovers.Hc) {
    val date = remember(l.date) { SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(l.date)) }
    Column(Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.fillMaxWidth(0.62f).aspectRatio(3f / 4f).shadow(10.dp, RoundedCornerShape(5.dp)).clip(RoundedCornerShape(5.dp))) {
            LectureCoverArt(l, course, hc, Modifier.fillMaxSize(), showTitle = true)
        }
        Spacer(Modifier.height(22.dp))
        Ornament(true)
        Spacer(Modifier.height(10.dp))
        BasicText(l.title, style = NT.Type.Chapter.copy(fontSize = 22.sp, lineHeight = 30.sp, color = NT.Colors.Reader.Heading, textAlign = TextAlign.Center), maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        BasicText(listOfNotNull(date, "${l.durationMs / 60000} 分钟", "${l.sections.size} 节", if (l.cards.isNotEmpty()) "${l.cards.size} 张复习卡" else null).joinToString(" · "),
            style = T.Cap.copy(color = NT.Colors.Reader.Caption, fontSize = 12.5.sp, textAlign = TextAlign.Center))
        Spacer(Modifier.height(16.dp))
        BasicText("轻点右侧或从右缘拖动翻页 →", style = T.Cap.copy(color = NT.Colors.Reader.Caption.copy(alpha = 0.8f)))
    }
}

/** 翻书进度条：刻度轨道 + 圆钮；拖动时气泡显示课件缩略图与「第 N 节 · n/N」，松手跳页。 */
@Composable
private fun Scrubber(cur: Int, count: Int, label: (Int) -> String, thumb: (Int) -> Int?, course: Course?, onJump: (Int) -> Unit) {
    var dragX by remember { mutableStateOf<Float?>(null) }
    var w by remember { mutableFloatStateOf(1f) }
    val shown = dragX?.let { ReaderMath.pageAt(it, w, count) } ?: cur
    val d = NT.Dim.Scrub
    BoxWithConstraints(Modifier.fillMaxWidth().height(d.ThumbH + 64.dp).padding(horizontal = 22.dp)) {
        val trackW = maxWidth
        val knobX = trackW * ReaderMath.fraction(shown, count)
        // 气泡
        Column(Modifier.offset(x = (knobX - 56.dp).coerceIn(0.dp, trackW - 112.dp)).width(112.dp)
            .shadow(8.dp, RoundedCornerShape(d.PopR), ambientColor = Color(0x262A2040), spotColor = Color(0x262A2040))
            .clip(RoundedCornerShape(d.PopR)).background(Color.White.copy(alpha = 0.94f)).padding(d.PopPad), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(d.ThumbW, d.ThumbH).clip(RoundedCornerShape(6.dp)).background(Paper)) {
                val sp = thumb(shown); val path = course?.slidePath.orEmpty()
                if (sp != null && path.isNotBlank() && File(path).exists()) {
                    val img by produceState<ImageBitmap?>(null, path, sp) { value = withContext(Dispatchers.IO) { runCatching { SlideImages.render(path, sp, 320)?.asImageBitmap() }.getOrNull() } }
                    img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                } else MiniBookGlyph(Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(4.dp))
            BasicText(label(shown), style = NT.Type.ScrubLabel.copy(fontSize = 12.sp), maxLines = 1)
        }
        // 轨道
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).fillMaxWidth().height(d.TrackH)
            .shadow(4.dp, Capsule(), ambientColor = Color(0x1A000000), spotColor = Color(0x1A000000)).clip(Capsule())
            .background(Color.White.copy(alpha = 0.85f)).border(1.dp, Color.White, Capsule())
            .pointerInput(count) {
                w = size.width.toFloat()
                detectTapGestures { o -> onJump(ReaderMath.pageAt(o.x, size.width.toFloat(), count)) }
            }
            .pointerInput(count, "drag") {
                w = size.width.toFloat()
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragX = o.x },
                    onHorizontalDrag = { ch, dd -> ch.consume(); dragX = ((dragX ?: 0f) + dd).coerceIn(0f, size.width.toFloat()) },
                    onDragEnd = { dragX?.let { onJump(ReaderMath.pageAt(it, size.width.toFloat(), count)) }; dragX = null },
                    onDragCancel = { dragX = null })
            }) {
            Canvas(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
                val step = d.TickStep.toPx(); var x = 0f; var i = 0
                while (x <= size.width) {
                    val th = (if (i % 5 == 0) d.TickMajorH else d.TickH).toPx()
                    drawLine(NT.Colors.Reader.Tick, Offset(x, (size.height - th) / 2), Offset(x, (size.height + th) / 2), 1.dp.toPx())
                    x += step; i++
                }
            }
        }
        // 圆钮
        Box(Modifier.align(Alignment.BottomStart).padding(bottom = 6.dp + (d.TrackH - 22.dp) / 2).offset(x = (knobX - 11.dp).coerceIn(0.dp, trackW - 22.dp)).size(22.dp)
            .shadow(4.dp, Capsule()).clip(Capsule()).background(Brush.verticalGradient(listOf(Color.White, NT.Colors.Reader.Knob))).border(0.8.dp, Color(0x22000000), Capsule()))
    }
}

@Composable
private fun MiniBookGlyph(modifier: Modifier) {
    Canvas(modifier.padding(6.dp)) {
        val w = size.width; val h = size.height
        drawRect(Color.White, Offset(0f, 0f), Size(w * 0.48f, h)); drawRect(Color.White, Offset(w * 0.52f, 0f), Size(w * 0.48f, h))
        drawRect(Color(0xFF1A1A1A), Offset(w * 0.08f, h * 0.2f), Size(w * 0.06f, h * 0.5f))
        drawRect(Color(0xFF1A1A1A).copy(alpha = 0.15f), Offset(w * 0.18f, h * 0.2f), Size(w * 0.24f, h * 0.36f), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
        for (k in 0 until 6) drawLine(Color(0x55000000), Offset(w * 0.58f, h * (0.18f + k * 0.12f)), Offset(w * 0.94f, h * (0.18f + k * 0.12f)), 1f)
    }
}

@Composable
private fun ToolItem(icon: String, label: String, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, Color(0xFF1A1A1A), NT.Dim.Toolbar.Icon, 2f)
        Spacer(Modifier.height(NT.Dim.Toolbar.LabelGap))
        BasicText(label, style = T.Cap.copy(color = Color(0xFF1A1A1A), fontSize = 13.sp, fontWeight = FontWeight.Medium))
    }
}
