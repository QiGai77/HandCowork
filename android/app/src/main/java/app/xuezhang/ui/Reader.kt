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
import app.xuezhang.engine.NotePart
import app.xuezhang.engine.ReaderPaging
import app.xuezhang.engine.UiBlock
import app.xuezhang.engine.UiBlocks
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
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

private sealed interface BookPage {
    data object Cover : BookPage
    data object Toc : BookPage
    /** 一章（一节笔记/助记/考点）的第 part 页（0 起，共 parts 页）。slices 为 null 表示还没量完。 */
    data class Part(val ch: Int, val part: Int, val parts: Int, val slices: List<ReaderPaging.Slice>?) : BookPage
    data object End : BookPage
}

/** 书页里的一块内容（分页的最小单位）。 */
private sealed interface Piece {
    data class Head(val title: String) : Piece
    data class Pic(val sec: Int) : Piece
    data class Para(val text: String, val ctx: String) : Piece
    data class Formula(val tex: String, val ctx: String) : Piece
    data class Ui(val block: UiBlock) : Piece
    data class Exam(val caption: String?) : Piece
    data class Label(val text: String) : Piece
}

/** gapDp：与上一块的间距（页首忽略）；keep：与下一块第一行同页。 */
private data class Placed(val p: Piece, val gapDp: Float = 0f, val keep: Boolean = false)
private data class Chapter(val title: String, val items: List<Placed>, val sec: Int = -1, val thumb: Int? = null, val label: String = "")

/** 正文 → 段落/公式/互动块，保持 NoteBody / RichText 的间距。 */
private fun bodyPieces(body: String, firstGap: Float, out: MutableList<Placed>) {
    val parts = runCatching { UiBlocks.split(body) }.getOrElse { listOf(NotePart.Text(body)) }
    var gap = firstGap
    parts.forEachIndexed { pi, part ->
        if (pi > 0) gap += 10f
        when (part) {
            is NotePart.Ui -> { out.add(Placed(Piece.Ui(part.block), gap)); gap = 0f }
            is NotePart.Text -> splitBlocks(part.text).forEach { b ->
                when (b) {
                    is Blk.Gap -> gap += 8f
                    is Blk.Math -> { out.add(Placed(Piece.Formula(b.tex, body), gap)); gap = 0f }
                    is Blk.P -> { out.add(Placed(Piece.Para(b.text, body), gap)); gap = 0f }
                }
            }
        }
    }
}

private val ContPad = 30.dp

@OptIn(ExperimentalPageCurlApi::class)
@Composable
fun BookScreen(id: String, startPage: Int = 0, startPart: Int = 0) {
    val l = Store.lecture(id) ?: run { Nav.pop(); return }
    val course = Store.course(l.courseId)
    val hc = remember(l.coverStyle, course?.name, course?.cover) { lectureHc(l, course) }
    val sections = remember(l.sections, l.notes) {
        l.sections.ifEmpty { l.notes.split(Regex("\n\\s*\n")).filter { it.isNotBlank() }.map { app.xuezhang.data.NoteSection("", emptyList(), it.trim()) } }
    }
    val chapters = remember(sections, l.mnemonics, l.segments) {
        buildList {
            sections.forEachIndexed { i, s ->
                val items = mutableListOf<Placed>()
                val title = s.title.ifBlank { "第 ${i + 1} 节" }
                items.add(Placed(Piece.Head(title)))
                items.add(Placed(Piece.Pic(i)))
                bodyPieces(s.body, 0f, items)
                val exams = l.segments.filter { it.exam && (s.pages.isEmpty() || it.page in s.pages) }.take(if (s.pages.isEmpty()) 0 else 3)
                exams.forEach { e -> items.add(Placed(Piece.Exam(null), 12f, keep = true)); bodyPieces(e.voice, 4f, items) }
                add(Chapter(title, items, i, s.pages.firstOrNull(), "第 ${i + 1} 节"))
            }
            if (l.mnemonics.isNotEmpty()) {
                val items = mutableListOf(Placed(Piece.Head("助　记")))
                l.mnemonics.forEachIndexed { k, m -> items.add(Placed(Piece.Label(m.title), if (k > 0) 14f else 0f, keep = true)); bodyPieces(m.body, 4f, items) }
                add(Chapter("助　记", items, label = "助记"))
            }
            if (l.segments.any { it.exam }) {
                val items = mutableListOf(Placed(Piece.Head("课上标记的考点")))
                l.segments.filter { it.exam }.forEachIndexed { k, s ->
                    items.add(Placed(Piece.Exam(Live.fmt(s.startMs) + if (s.page > 0) " · 课件第 ${s.page} 页" else ""), if (k > 0) 14f else 0f, keep = true))
                    bodyPieces(s.voice, 4f, items)
                }
                add(Chapter("课上标记的考点", items, label = "考点"))
            }
        }
    }
    // 量好的块高度（每章一组）。null = 还没量。
    var measured by remember(chapters) { mutableStateOf<List<List<ReaderPaging.Block>>?>(null) }
    var pageHpx by remember { mutableStateOf(0f to 0f) }
    val pages = remember(chapters, measured, pageHpx) {
        buildList {
            add(BookPage.Cover); if (chapters.size > 1) add(BookPage.Toc)
            chapters.forEachIndexed { ci, ch ->
                val m = measured?.getOrNull(ci)
                if (m == null || pageHpx.first <= 0f) add(BookPage.Part(ci, 0, 1, null))
                else {
                    val split = ReaderPaging.paginate(m, pageHpx.first, pageHpx.second)
                    split.forEachIndexed { k, sl -> add(BookPage.Part(ci, k, split.size, sl)) }
                }
            }
            add(BookPage.End)
        }
    }
    val firstOf: (Int) -> Int = { ci -> pages.indexOfFirst { it is BookPage.Part && it.ch == ci && it.part == 0 }.coerceAtLeast(0) }
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
    // 重新分页（旋转/字号变化/首次量完）后保持在同一章的相近位置
    var anchor by remember { mutableStateOf<BookPage?>(null) }
    LaunchedEffect(pages) {
        val a = anchor
        if (a is BookPage.Part) {
            val parts = pages.count { it is BookPage.Part && it.ch == a.ch }
            val target = if (a.slices == null) firstOf(a.ch) + startPart.coerceIn(0, parts - 1)
                else ReaderPaging.remap(a.ch, a.part, a.parts, firstOf(a.ch), parts)
            if (target != curl.current) curl.snapTo(target.coerceIn(0, pages.size - 1))
        } else if (a == BookPage.End && curl.current != pages.size - 1) curl.snapTo(pages.size - 1)
    }
    LaunchedEffect(cur, pages) { anchor = pages.getOrNull(cur) }
    val navTitle = (course?.name?.takeIf { it.length <= 6 }?.let { "$it·" } ?: "") + l.title.removePrefix(course?.name.orEmpty()).trim(' ', '·', '-', '：', ':').ifBlank { l.title }
    val onMathFor: (String) -> ((String) -> Unit) = { ctx -> { tex -> Nav.sheet.value = Sheet.Formula(tex, ctx) } }

    Box(Modifier.fillMaxSize().background(NT.Brushes.Background).dotGrid()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
            // 顶栏：返回 + 衬线标题（长标题两行、字号略小，不再「…」截断）
            Box(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp)) {
                Box(Modifier.align(Alignment.CenterStart).size(42.dp).shadow(6.dp, Capsule(), ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
                    .clip(Capsule()).background(Color.White.copy(alpha = 0.92f)).clickable { Nav.pop() }, contentAlignment = Alignment.Center) { Icon("back", Color(0xFF1A1A1A), 20.dp, 2.2f) }
                val long = ReaderTitle.isLong(navTitle)
                BasicText(navTitle, Modifier.align(Alignment.Center).padding(start = 56.dp, end = 12.dp),
                    style = NT.Type.ReaderNav.copy(fontSize = if (long) 15.5.sp else 19.sp, lineHeight = if (long) 20.sp else 24.sp, textAlign = TextAlign.Center),
                    maxLines = if (long) 2 else 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            // 摊开的精装书
            OpenBook(hc, Modifier.fillMaxWidth().weight(1f)) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val dens = LocalDensity.current
                    val textW = maxWidth - PagePadStart - PagePadEnd
                    val firstH = maxHeight - PagePadTop - PagePadBottom - FooterH - 4.dp
                    val hp = with(dens) { firstH.toPx() to (firstH - ContPad).toPx() }
                    if (hp != pageHpx) SideEffect { pageHpx = hp }
                    // 隐形量高：每块按页宽排版一次
                    PieceMeasurer(chapters, textW, l, course) { res -> if (res != measured) measured = res }
                    PageCurl(count = pages.size, state = curl, config = config, modifier = Modifier.fillMaxSize()) { p ->
                        PaperPage(p, pages.size) {
                            when (val pg = pages[p]) {
                                BookPage.Cover -> CoverLeaf(l, course, hc)
                                BookPage.Toc -> PageScroll {
                                    ChapterHead("目　录")
                                    chapters.forEachIndexed { i, ch ->
                                        val parts = pages.count { it is BookPage.Part && it.ch == i }
                                        Row(Modifier.fillMaxWidth().clickable { jump(firstOf(i)) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                            BasicText(ch.title, Modifier.weight(1f), style = T.Label.copy(color = Ink, fontFamily = Serif, fontSize = 16.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            BasicText("${firstOf(i) + 1}" + if (parts > 1) "–${firstOf(i) + parts}" else "", style = T.Sub.copy(color = NT.Colors.Reader.Caption))
                                        }
                                        Box(Modifier.fillMaxWidth().height(0.6.dp).background(Orn.copy(alpha = 0.5f)))
                                    }
                                }
                                is BookPage.Part -> {
                                    val ch = chapters[pg.ch]
                                    val blocks = measured?.getOrNull(pg.ch)
                                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = PagePadStart, end = PagePadEnd, top = PagePadTop, bottom = PagePadBottom)) {
                                        if (pg.part > 0) RunningHeader(ch.title, pg.part, pg.parts)
                                        val slices = pg.slices
                                        if (slices == null || blocks == null) {
                                            // 还没量完（只会出现一帧）：先按原样整章可滚动显示
                                            ch.items.forEach { PieceView(it.p, l, course, onMathFor) }
                                        } else slices.forEachIndexed { k, s ->
                                            val b = blocks[s.block]
                                            if (k > 0 && s.from == 0f && b.gapBefore > 0f) Spacer(Modifier.height(with(dens) { b.gapBefore.toDp() }))
                                            SliceView(s, s.from == 0f && s.to >= b.height - 0.5f, with(dens) { (if (pg.part > 0) hp.second else hp.first).toDp() }, Modifier) { PieceView(ch.items[s.block].p, l, course, onMathFor) }
                                        }
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
            }
            Spacer(Modifier.height(6.dp))
            val majors = remember(pages) { pages.indices.filter { pages[it] !is BookPage.Part || (pages[it] as BookPage.Part).part == 0 }.toSet() }
            Scrubber(cur, pages.size, majors, label = { p -> pageLabel(pages, chapters, p) },
                thumb = { p -> (pages.getOrNull(p) as? BookPage.Part)?.let { chapters[it.ch].thumb } }, course = course) { jump(it) }
            // 工具栏
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolItem("list", "目录") { jump(if (chapters.size > 1) 1 else 0) }
                Spacer(Modifier.weight(1f))
                ToolItem("layers", "复习卡") { Nav.push(Route.Review(id)) }
                Spacer(Modifier.weight(1f))
                ToolItem("hexset", "导图") { NoteSeg.next = 2; Nav.push(Route.Note(id)) }
            }
        }
    }
}

private fun pageLabel(pages: List<BookPage>, chapters: List<Chapter>, p: Int): String {
    val n = "${p + 1}/${pages.size}"
    return when (val pg = pages.getOrNull(p)) {
        BookPage.Cover -> "封面 · $n"
        BookPage.Toc -> "目录 · $n"
        is BookPage.Part -> chapters[pg.ch].label.ifBlank { chapters[pg.ch].title.take(4) } + (if (pg.parts > 1) "（${pg.part + 1}/${pg.parts}）" else "") + " · $n"
        else -> n
    }
}

/** 顶栏长标题判断（纯函数，可测）。 */
object ReaderTitle { fun isLong(s: String) = s.length > 11 }

private val PagePadStart = 26.dp
private val PagePadEnd = 22.dp
private val PagePadTop = 26.dp
private val PagePadBottom = 10.dp
private val FooterH = 24.dp

/** 续页页眉：「6. 空载电流与励磁阻抗（续）」+ 细线。高度固定 = ContPad。 */
@Composable
private fun RunningHeader(title: String, part: Int, parts: Int) {
    Column(Modifier.fillMaxWidth().height(ContPad)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText("$title（续）", Modifier.weight(1f), style = T.Cap.copy(fontFamily = Serif, fontSize = 12.5.sp, color = NT.Colors.Reader.Caption), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText("${part + 1}/$parts", style = T.Cap.copy(fontSize = 11.sp, color = NT.Colors.Reader.Caption))
        }
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().height(0.6.dp).background(Orn.copy(alpha = 0.6f)))
    }
}

/** 一块内容在书页上的样子（量高和真正显示用同一个组合函数，高度才对得上）。 */
@Composable
private fun PieceView(p: Piece, l: Lecture, course: Course?, onMathFor: (String) -> ((String) -> Unit)) {
    CompositionLocalProvider(LocalPaperStyle provides true) {
        when (p) {
            is Piece.Head -> ChapterHead(p.title)
            is Piece.Pic -> {
                val s = l.sections.getOrNull(p.sec)
                if (s != null && s.frame.isNotBlank()) Column { FrameImage(l, p.sec, s); Spacer(Modifier.height(14.dp)) }
                else if (s != null) ReaderSlide(course, s.pages)
            }
            is Piece.Para -> RichText(p.text, style = ReaderBody, onMath = onMathFor(p.ctx))
            is Piece.Formula -> MathBlock(p.tex, ReaderBody, onMathFor(p.ctx))
            is Piece.Ui -> UiBlockView(p.block)
            is Piece.Exam -> Row(verticalAlignment = Alignment.CenterVertically) {
                ExamTag()
                if (p.caption != null) { Spacer(Modifier.width(8.dp)); BasicText(p.caption, style = T.Cap.copy(color = NT.Colors.Reader.Caption)) }
            }
            is Piece.Label -> BasicText(p.text, style = T.Item.copy(color = NT.Colors.Reader.ExamText))
        }
    }
}

/** 按 Slice 显示：整块 / 只露出 [from,to) 那几行 / 缩小 / 页内滚动。 */
@Composable
private fun SliceView(s: ReaderPaging.Slice, full: Boolean, scrollH: androidx.compose.ui.unit.Dp, modifier: Modifier, content: @Composable () -> Unit) {
    when {
        s.scroll -> Box(modifier.fillMaxWidth().height(scrollH).verticalScroll(rememberScrollState())) { content() }
        s.scale < 1f -> Box(modifier.fillMaxWidth().clipToBounds().layout { m, c ->
            val pl = m.measure(c.copy(minHeight = 0, maxHeight = Constraints.Infinity))
            layout(pl.width, (pl.height * s.scale).roundToInt()) { pl.placeWithLayer(0, 0) { scaleX = s.scale; scaleY = s.scale; transformOrigin = TransformOrigin(0.5f, 0f) } }
        }) { content() }
        !full -> Box(modifier.fillMaxWidth().clipToBounds().layout { m, c ->
            val pl = m.measure(c.copy(minHeight = 0, maxHeight = Constraints.Infinity))
            val top = s.from.roundToInt()
            val h = (minOf(s.to, pl.height.toFloat()) - s.from).roundToInt().coerceAtLeast(0)
            layout(pl.width, h) { pl.place(0, -top) }
        }) { content() }
        else -> Box(modifier) { content() }
    }
}

/** 隐形量高：每章每块在页宽下组合+测量一次，得出高度与段落行底 y，交给 ReaderPaging。 */
@Composable
private fun PieceMeasurer(chapters: List<Chapter>, width: androidx.compose.ui.unit.Dp, l: Lecture, course: Course?, onResult: (List<List<ReaderPaging.Block>>) -> Unit) {
    val sinks = remember(chapters) { HashMap<String, LineSink>() }
    val noMath: (String) -> ((String) -> Unit) = { { } }
    SubcomposeLayout(Modifier.size(0.dp)) { _ ->
        val w = width.roundToPx().coerceAtLeast(1)
        val res = chapters.mapIndexed { ci, ch ->
            ch.items.mapIndexed { pi, it ->
                val key = "$ci/$pi"
                val sink = sinks.getOrPut(key) { LineSink() }
                val ms = subcompose(key) {
                    CompositionLocalProvider(LocalMeasuring provides true, LocalLineSink provides sink) { Box { PieceView(it.p, l, course, noMath) } }
                }.map { m -> m.measure(Constraints(maxWidth = w)) }
                val h = ms.maxOfOrNull { m -> m.height }?.toFloat() ?: 0f
                ReaderPaging.Block(h, if (it.p is Piece.Para) sink.lines.filter { y -> y <= h + 1f } else emptyList(), it.gapDp * density, it.keep)
            }
        }
        onResult(res)
        layout(0, 0) {}
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
            Box(Modifier.fillMaxWidth().height(FooterH), contentAlignment = Alignment.TopCenter) {
                if (p > 0) BasicText("— ${p + 1} —", Modifier.fillMaxWidth(), style = T.Cap.copy(fontSize = 11.sp, color = NT.Colors.Reader.Caption, textAlign = TextAlign.Center))
            }
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
    val measuring = LocalMeasuring.current
    val img by produceState<ImageBitmap?>(null, path, page) { if (!measuring) value = withContext(Dispatchers.IO) { runCatching { SlideImages.render(path, page, 1400)?.asImageBitmap() }.getOrNull() } }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth(0.94f).shadow(5.dp, RoundedCornerShape(NT.Dim.Reader.SlideR), ambientColor = Color(0x44000000), spotColor = Color(0x44000000))
            .background(Color.White).padding(NT.Dim.Reader.SlideFrame).aspectRatio(16f / 10.5f)
            .clickable { SlideViewer.open.value = Triple(path, page, total) }) {
            img?.let { Image(it, "课件第 $page 页", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) } ?: if (measuring) Box(Modifier.fillMaxSize()) else Shimmer(Modifier.fillMaxSize())
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
private fun Scrubber(cur: Int, count: Int, majors: Set<Int>, label: (Int) -> String, thumb: (Int) -> Int?, course: Course?, onJump: (Int) -> Unit) {
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
                // v0.0.7.8 刻度 = 真实页：每页一根，章首页（封面/目录/每节第一页）为长刻度；页太多时只画章首
                val n = count.coerceAtLeast(1)
                for (i in 0 until n) {
                    val major = i in majors
                    if (n > 64 && !major) continue
                    val x = if (n <= 1) 0f else size.width * i / (n - 1)
                    val th = (if (major) d.TickMajorH else d.TickH).toPx()
                    drawLine(NT.Colors.Reader.Tick, Offset(x, (size.height - th) / 2), Offset(x, (size.height + th) / 2), if (major) 1.4.dp.toPx() else 1.dp.toPx())
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
