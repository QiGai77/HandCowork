package app.xuezhang.ui

import kotlinx.coroutines.launch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xuezhang.data.Course
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/**
 * 课件页面渲染：PDF 用系统 PdfRenderer 真实渲染；
 * PPTX 没有系统渲染器——优先取该页嵌入的图片，否则把该页文字排成一张简版幻灯片（保真度有限）。
 */
object SlideImages {
    private val cache = LruCache<String, Bitmap>(16)
    private val lock = Any()

    fun getSlideEntries(path: String): List<String> = runCatching {
        ZipFile(path).use { z ->
            val relsXml = z.getEntry("ppt/_rels/presentation.xml.rels")?.let { z.getInputStream(it).bufferedReader().use { r -> r.readText() } }.orEmpty()
            val presXml = z.getEntry("ppt/presentation.xml")?.let { z.getInputStream(it).bufferedReader().use { r -> r.readText() } }.orEmpty()
            val relMap = Regex("<Relationship [^>]*>").findAll(relsXml).mapNotNull { m ->
                val id = attr(m.value, "Id") ?: return@mapNotNull null
                val t = attr(m.value, "Target") ?: return@mapNotNull null
                id to if (t.startsWith("slides/")) "ppt/$t" else if (t.startsWith("../slides/")) "ppt/${t.removePrefix("../")}" else t
            }.toMap()
            val orderedIds = Regex("<p:sldId [^>]*r:id=\"([^\"]+)\"").findAll(presXml).map { it.groupValues[1] }.toList()
            val ordered = orderedIds.mapNotNull { relMap[it] }.filter { z.getEntry(it) != null }
            if (ordered.isNotEmpty()) return@runCatching ordered

            // Fallback: collect all slide*.xml and sort naturally
            val slideRegex = Regex("ppt/slides/slide(\\d+)\\.xml")
            z.entries().asSequence()
                .mapNotNull { e -> slideRegex.matchEntire(e.name)?.let { it.groupValues[1].toInt() to e.name } }
                .sortedBy { it.first }
                .map { it.second }
                .toList()
        }
    }.getOrDefault(emptyList())

    fun pageCount(path: String): Int = runCatching {
        if (path.isBlank()) return@runCatching 0
        val f = File(path)
        if (!f.exists()) return@runCatching 0
        when {
            path.endsWith(".pdf", true) -> ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { it.pageCount } }
            path.endsWith(".pptx", true) -> getSlideEntries(path).size
            else -> 0
        }
    }.getOrDefault(0)

    /** page 从 1 开始。 */
    fun render(path: String, page: Int, widthPx: Int): Bitmap? {
        if (path.isBlank() || page < 1) return null
        val f = File(path)
        if (!f.exists()) return null
        val key = "$path#$page@$widthPx"
        cache.get(key)?.let { return it }
        // 磁盘缓存：原始课件页渲染一次后存 PNG，笔记里反复看不用重渲染
        val cacheDir = File(app.xuezhang.data.Store.dir, "slidecache").apply { mkdirs() }
        val cacheFile = File(cacheDir, "${(path + f.lastModified()).hashCode()}_${page}_$widthPx.png")
        if (cacheFile.exists()) runCatching { android.graphics.BitmapFactory.decodeFile(cacheFile.absolutePath) }.getOrNull()?.let { cache.put(key, it); return it }
        val bmp = synchronized(lock) {
            runCatching {
                when {
                    path.endsWith(".pdf", true) -> renderPdf(path, page, widthPx)
                    path.endsWith(".pptx", true) -> {
                        val pdfPath = path.substringBeforeLast('.') + ".pdf"
                        val pdfFile = File(pdfPath)
                        if (pdfFile.exists() && pdfFile.length() > 0L) {
                            renderPdf(pdfFile.absolutePath, page, widthPx) ?: renderPptx(path, page, widthPx)
                        } else {
                            renderPptx(path, page, widthPx)
                        }
                    }
                    else -> null
                }
            }.getOrNull()
        }
        if (bmp != null) { cache.put(key, bmp); runCatching { cacheFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } } }
        return bmp
    }

    private fun renderPdf(path: String, page: Int, widthPx: Int): Bitmap? {
        ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                if (page > r.pageCount) return null
                r.openPage(page - 1).use { p ->
                    val h = (widthPx.toFloat() * p.height / p.width).toInt().coerceAtLeast(1)
                    val b = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                    b.eraseColor(android.graphics.Color.WHITE)
                    p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return b
                }
            }
        }
    }

    private fun renderPptx(path: String, page: Int, widthPx: Int): Bitmap? = runCatching { renderPptxLayout(path, page, widthPx) }.getOrNull() ?: renderPptxSimple(path, page, widthPx)

    private fun attr(tag: String, name: String): String? = Regex("\\b$name=\"([^\"]*)\"").find(tag)?.groupValues?.get(1)
    private fun hex(c: String?): Int? = c?.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }

    /** 按原始坐标排版：原生解析 sldSz、每个形状的 off/ext、图片、表格、填充色、字号，高保真还原原 PPTX 页面。 */
    private fun renderPptxLayout(path: String, page: Int, widthPx: Int): Bitmap? {
        val entries = getSlideEntries(path)
        val slideName = entries.getOrNull(page - 1) ?: "ppt/slides/slide$page.xml"
        ZipFile(path).use { z ->
            fun read(n: String) = z.getEntry(n)?.let { e -> z.getInputStream(e).readBytes().toString(Charsets.UTF_8) }
            val xml = read(slideName) ?: return null
            val pres = read("ppt/presentation.xml").orEmpty()
            val sz = Regex("<p:sldSz[^>]*>").find(pres)?.value
            val sw = sz?.let { attr(it, "cx")?.toLongOrNull() } ?: 12192000L
            val sh = sz?.let { attr(it, "cy")?.toLongOrNull() } ?: 6858000L
            val k = widthPx.toFloat() / sw
            val hPx = (sh * k).toInt().coerceAtLeast(1)

            val relsName = slideName.substringBeforeLast('/') + "/_rels/" + slideName.substringAfterLast('/') + ".rels"
            val rels = read(relsName).orEmpty()
            val relMap = Regex("<Relationship [^>]*>").findAll(rels).mapNotNull { m ->
                val id = attr(m.value, "Id") ?: return@mapNotNull null; val t = attr(m.value, "Target") ?: return@mapNotNull null
                id to t.replace("../", "ppt/")
            }.toMap()

            // 主题色表：解析 accent1~accent6, dk1, lt1 等
            val themeXml = read("ppt/theme/theme1.xml").orEmpty()
            val schemeMap = mutableMapOf<String, Int>()
            Regex("<a:(\\w+)>[\\s\\S]*?<a:srgbClr val=\"([0-9A-Fa-f]{6})\"").findAll(themeXml).forEach { tm ->
                hex(tm.groupValues[2])?.let { schemeMap[tm.groupValues[1]] = it }
            }

            fun resolveClr(snippet: String?): Int? {
                if (snippet == null) return null
                val srgb = Regex("<a:srgbClr val=\"([0-9A-Fa-f]{6})\"").find(snippet)?.groupValues?.get(1)
                if (srgb != null) return hex(srgb)
                val scheme = Regex("<a:schemeClr val=\"(\\w+)\"").find(snippet)?.groupValues?.get(1)
                if (scheme != null) return schemeMap[scheme]
                return null
            }

            val b = Bitmap.createBitmap(widthPx, hPx, Bitmap.Config.ARGB_8888)
            val cv = Canvas(b)

            // 背景色解析：优先 slide 背景，否则取 layout 背景
            var bgClr = resolveClr(Regex("<p:bg>[\\s\\S]*?</p:bg>").find(xml)?.value)
            if (bgClr == null) {
                val layoutRel = relMap.entries.firstOrNull { it.value.contains("slideLayout") }?.value
                if (layoutRel != null) {
                    val layoutXml = read(layoutRel).orEmpty()
                    bgClr = resolveClr(Regex("<p:bg>[\\s\\S]*?</p:bg>").find(layoutXml)?.value)
                }
            }
            cv.drawColor(bgClr ?: android.graphics.Color.WHITE)

            // 背景图片（blip）
            val bgBlip = Regex("<p:bg>[\\s\\S]*?r:embed=\"([^\"]+)\"").find(xml)?.groupValues?.get(1)
            if (bgBlip != null) {
                relMap[bgBlip]?.let { t ->
                    z.getEntry(t)?.let { e ->
                        z.getInputStream(e).use { BitmapFactory.decodeStream(it) }?.let { im ->
                            cv.drawBitmap(im, null, android.graphics.RectF(0f, 0f, widthPx.toFloat(), hPx.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
                        }
                    }
                }
            }

            var drew = 0
            val tree = xml.substringAfter("<p:spTree>", xml)

            // 匹配所有原生形状、图片与表格组件
            Regex("<p:(sp|pic|graphicFrame|cxnSp)\\b[^>]*>([\\s\\S]*?)</p:\\1>").findAll(tree).forEach { m ->
                val tag = m.groupValues[1]
                val body = m.groupValues[2]
                val off = Regex("<a:off [^>]*>").find(body)?.value
                val ext = Regex("<a:ext [^>]*>").find(body)?.value
                val x = (off?.let { attr(it, "x")?.toLongOrNull() } ?: 0L) * k
                val y = (off?.let { attr(it, "y")?.toLongOrNull() } ?: 0L) * k
                val w = (ext?.let { attr(it, "cx")?.toLongOrNull() } ?: sw) * k
                val h = (ext?.let { attr(it, "cy")?.toLongOrNull() } ?: 0L) * k
                val rect = android.graphics.RectF(x, y, x + w, y + h)

                when (tag) {
                    "pic" -> {
                        val rid = Regex("r:embed=\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                        val t = rid?.let { relMap[it] }
                        if (t != null && Regex("\\.(png|jpe?g|gif|bmp|webp)$", RegexOption.IGNORE_CASE).containsMatchIn(t)) {
                            z.getEntry(t)?.let { e ->
                                val o = BitmapFactory.Options().apply { inSampleSize = if (e.size > 3_000_000) 2 else 1 }
                                z.getInputStream(e).use { BitmapFactory.decodeStream(it, null, o) }?.let { im ->
                                    cv.drawBitmap(im, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
                                    drew++
                                }
                            }
                        }
                    }
                    "graphicFrame" -> {
                        // 表格原生渲染：解析 a:tbl、a:gridCol、a:tr、a:tc
                        val tbl = Regex("<a:tbl>([\\s\\S]*?)</a:tbl>").find(body)?.groupValues?.get(1)
                        if (tbl != null) {
                            val cols = Regex("<a:gridCol\\b[^>]*\\bw=\"(\\d+)\"").findAll(tbl).map { it.groupValues[1].toLong() * k }.toList()
                            val rows = Regex("<a:tr\\b[^>]*\\bh=\"(\\d+)\">([\\s\\S]*?)</a:tr>").findAll(tbl).toList()
                            var curY = y
                            val borderPaint = Paint().apply { color = 0x33000000; style = Paint.Style.STROKE; strokeWidth = 1f * k * 12700 }
                            val cellPaint = Paint().apply { color = 0x08000000; style = Paint.Style.FILL }
                            rows.forEach { rowMatch ->
                                val rowH = rowMatch.groupValues[1].toLong() * k
                                val cells = Regex("<a:tc>([\\s\\S]*?)</a:tc>").findAll(rowMatch.groupValues[2]).toList()
                                var curX = x
                                cells.forEachIndexed { colIdx, cellMatch ->
                                    val cellW = cols.getOrNull(colIdx) ?: (w / maxOf(1, cells.size))
                                    val cellRect = android.graphics.RectF(curX, curY, curX + cellW, curY + rowH)
                                    val cellFill = resolveClr(Regex("<a:solidFill>[\\s\\S]*?</a:solidFill>").find(cellMatch.groupValues[1])?.value)
                                    if (cellFill != null) {
                                        cv.drawRect(cellRect, Paint().apply { color = cellFill })
                                    } else {
                                        cv.drawRect(cellRect, cellPaint)
                                    }
                                    cv.drawRect(cellRect, borderPaint)

                                    // 单元格文字
                                    val cText = Regex("<a:t>([^<]*)</a:t>").findAll(cellMatch.groupValues[1]).joinToString(" ") { it.groupValues[1] }
                                    if (cText.isNotBlank()) {
                                        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                                            textSize = maxOf(10f, 12f * 12700f * k)
                                            color = 0xFF1D1D1F.toInt()
                                        }
                                        val sl = StaticLayout.Builder.obtain(cText, 0, cText.length, tp, maxOf(10, (cellW - 4).toInt())).build()
                                        cv.save(); cv.translate(curX + 2, curY + 2); sl.draw(cv); cv.restore()
                                    }
                                    curX += cellW
                                }
                                curY += rowH
                                drew++
                            }
                        }
                    }
                    else -> { // sp or cxnSp
                        val spPr = Regex("<p:spPr>([\\s\\S]*?)</p:spPr>").find(body)?.groupValues?.get(1).orEmpty()
                        if (!spPr.contains("<a:noFill/>")) {
                            val c = resolveClr(Regex("<a:solidFill>[\\s\\S]*?</a:solidFill>").find(spPr)?.value)
                            if (c != null) {
                                cv.drawRoundRect(rect, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c })
                                drew++
                            }
                        }
                        val txBody = Regex("<p:txBody>([\\s\\S]*?)</p:txBody>").find(body)?.groupValues?.get(1) ?: return@forEach
                        val isTitle = body.contains("type=\"title\"") || body.contains("type=\"ctrTitle\"")
                        var ty = y + 4 * k * 12700
                        Regex("<a:p>([\\s\\S]*?)</a:p>").findAll(txBody).forEach { pm ->
                            val pr = pm.groupValues[1]
                            val text = Regex("<a:t>([^<]*)</a:t>").findAll(pr).joinToString("") { it.groupValues[1] }
                                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                            val szv = Regex("<a:rPr[^>]*\\bsz=\"(\\d+)\"").find(pr)?.groupValues?.get(1)?.toFloatOrNull()
                            val pt = szv?.div(100f) ?: if (isTitle) 36f else 18f
                            val px = pt * 12700f * k
                            if (text.isBlank()) { ty += px * 0.6f; return@forEach }
                            val col = resolveClr(Regex("<a:rPr[\\s\\S]*?</a:rPr>").find(pr)?.value) ?: 0xFF1D1D1F.toInt()
                            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                                textSize = px
                                color = col
                                isFakeBoldText = isTitle || Regex("<a:rPr[^>]*\\bb=\"1\"").containsMatchIn(pr)
                            }
                            val algn = when (Regex("<a:pPr[^>]*algn=\"(\\w+)\"").find(pr)?.groupValues?.get(1)) {
                                "ctr" -> Layout.Alignment.ALIGN_CENTER
                                "r" -> Layout.Alignment.ALIGN_OPPOSITE
                                else -> Layout.Alignment.ALIGN_NORMAL
                            }
                            val bullet = !isTitle && pr.contains("<a:buChar")
                            val str = if (bullet) "• $text" else text
                            val lw = (w - 8 * k * 12700).toInt().coerceAtLeast(20)
                            val l = StaticLayout.Builder.obtain(str, 0, str.length, tp, lw).setAlignment(algn).build()
                            cv.save(); cv.translate(x + 4 * k * 12700, ty); l.draw(cv); cv.restore()
                            ty += l.height + px * 0.25f
                            drew++
                        }
                    }
                }
            }
            return b
        }
    }

    private fun renderPptxSimple(path: String, page: Int, widthPx: Int): Bitmap? {
        val entries = getSlideEntries(path)
        val slideName = entries.getOrNull(page - 1) ?: "ppt/slides/slide$page.xml"
        ZipFile(path).use { z ->
            val xml = z.getEntry(slideName)?.let { e -> z.getInputStream(e).readBytes().toString(Charsets.UTF_8) } ?: return null
            val relsName = slideName.substringBeforeLast('/') + "/_rels/" + slideName.substringAfterLast('/') + ".rels"
            val rels = z.getEntry(relsName)?.let { e -> z.getInputStream(e).readBytes().toString(Charsets.UTF_8) }.orEmpty()
            val media = Regex("Target=\"\\.\\./media/([^\"]+\\.(png|jpe?g))\"", RegexOption.IGNORE_CASE).findAll(rels)
                .mapNotNull { m -> z.getEntry("ppt/media/${m.groupValues[1]}") }.maxByOrNull { it.size }
            val paras = Regex("<a:p>([\\s\\S]*?)</a:p>").findAll(xml).map { p ->
                Regex("<a:t>([^<]*)</a:t>").findAll(p.groupValues[1]).joinToString("") { it.groupValues[1] }
            }.filter { it.isNotBlank() }.toList()
            val h = widthPx * 9 / 16
            val b = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
            val cv = Canvas(b); cv.drawColor(android.graphics.Color.WHITE)
            val pad = widthPx * 0.06f
            val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = widthPx * 0.05f; isFakeBoldText = true; color = 0xFF1D1D1F.toInt() }
            val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = widthPx * 0.032f; color = 0xFF3A3A3C.toInt() }
            var textRight = widthPx - pad
            if (media != null) {
                val img = z.getInputStream(media).use { BitmapFactory.decodeStream(it) }
                if (img != null) {
                    val boxW = if (paras.size > 1) widthPx * 0.42f else widthPx - 2 * pad
                    val sc = minOf(boxW / img.width, (h - 2 * pad) / img.height)
                    val dw = img.width * sc; val dh = img.height * sc
                    val left = widthPx - pad - dw
                    cv.drawBitmap(img, null, android.graphics.RectF(left, (h - dh) / 2, left + dw, (h + dh) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
                    textRight = if (paras.size > 1) left - pad * 0.5f else textRight
                }
            }
            val tw = (textRight - pad).toInt().coerceAtLeast(10)
            var y = pad
            paras.firstOrNull()?.let { t ->
                val l = StaticLayout.Builder.obtain(t, 0, t.length, title, tw).setMaxLines(2).build()
                cv.save(); cv.translate(pad, y); l.draw(cv); cv.restore(); y += l.height + pad * 0.5f
            }
            paras.drop(1).take(9).forEach { t ->
                if (y > h - pad) return@forEach
                val s = "• $t"
                val l = StaticLayout.Builder.obtain(s, 0, s.length, body, tw).setMaxLines(2).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
                cv.save(); cv.translate(pad, y); l.draw(cv); cv.restore(); y += l.height + pad * 0.25f
            }
            return b
        }
    }
}

/** 正在全屏查看的课件页。 */
object SlideViewer { val open = mutableStateOf<Triple<String, Int, Int>?>(null) } // path, page, total

@Composable
fun SlideThumb(path: String, page: Int, modifier: Modifier = Modifier, widthPx: Int = 900, onClick: (() -> Unit)? = null) {
    var loaded by remember(path, page) { androidx.compose.runtime.mutableStateOf(false) }
    val img by produceState<ImageBitmap?>(null, path, page) {
        value = withContext(Dispatchers.IO) { runCatching { SlideImages.render(path, page, widthPx)?.asImageBitmap() }.getOrNull() }
        loaded = true
    }
    Box(
        modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Color.White)
            .border(0.5.dp, Color(0x1A000000), RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        img?.let { Image(it, "第 $page 页", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            ?: if (loaded) BasicText("第 $page 页", Modifier.align(Alignment.Center), style = T.Sub) else Shimmer(Modifier.fillMaxSize())
        BasicText("P.$page", Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xCC1D1D1F))
            .padding(horizontal = 7.dp, vertical = 2.dp), style = T.Cap.copy(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp))
    }
}

/** 一节笔记锚定的课件页（可多页，横向并排两张）。 */
@Composable
fun SlideStrip(c: Course?, pages: List<Int>) {
    val path = c?.slidePath.orEmpty()
    if (path.isBlank() || pages.isEmpty() || !File(path).exists()) return
    val total = remember(path) { SlideImages.pageCount(path) }
    val ps = pages.filter { total == 0 || it <= total }.take(2)
    if (ps.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ps.forEach { p -> SlideThumb(path, p, Modifier.weight(1f), widthPx = if (ps.size == 1) 1600 else 1000) { SlideViewer.open.value = Triple(path, p, total) } }
    }
    Spacer(Modifier.padding(top = 10.dp))
}

@Composable
fun SlideViewerHost() {
    val cur = SlideViewer.open.value
    AnimatedVisibility(cur != null, enter = fadeIn(), exit = fadeOut()) {
        val (path, page0, total) = cur ?: return@AnimatedVisibility
        val n = total.coerceAtLeast(page0)
        val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = (page0 - 1).coerceIn(0, n - 1)) { n }
        val page = pager.currentPage + 1
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        Box(Modifier.fillMaxSize().background(Color(0xF2000000)).clickable(interactionSource = null, indication = null) {}) {
            androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { idx ->
                var scale by remember(idx) { mutableFloatStateOf(1f) }
                var off by remember(idx) { mutableStateOf(Offset.Zero) }
                val img by produceState<ImageBitmap?>(null, path, idx) {
                    value = withContext(Dispatchers.IO) { SlideImages.render(path, idx + 1, 1800)?.asImageBitmap() }
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    img?.let {
                        Image(it, "课件第 ${idx + 1} 页", Modifier.fillMaxSize().pointerInput(idx) {
                            detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; off = Offset.Zero })
                        }.then(if (scale > 1f) Modifier.pointerInput(idx, "zoom") {
                            detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 4f); off = if (scale == 1f) Offset.Zero else off + pan }
                        } else Modifier).graphicsLayer { scaleX = scale; scaleY = scale; translationX = off.x; translationY = off.y }, contentScale = ContentScale.Fit)
                    } ?: BasicText("加载中…", style = T.Sub.copy(color = Color.White))
                }
            }
            Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("第 $page 页" + if (total > 0) " / $total" else "", Modifier.weight(1f), style = T.Btn.copy(color = Color.White))
                BasicText("关闭", Modifier.clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF)).clickable { SlideViewer.open.value = null }
                    .padding(horizontal = 16.dp, vertical = 8.dp), style = T.Btn.copy(color = Color.White))
            }
            Row(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                BasicText("上一页", Modifier.clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF)).clickable(enabled = page > 1) { scope.launch { pager.animateScrollToPage(page - 2) } }
                    .padding(horizontal = 18.dp, vertical = 10.dp), style = T.Btn.copy(color = if (page > 1) Color.White else Color(0x66FFFFFF)))
                BasicText("下一页", Modifier.clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF)).clickable(enabled = page < n) { scope.launch { pager.animateScrollToPage(page) } }
                    .padding(horizontal = 18.dp, vertical = 10.dp), style = T.Btn.copy(color = if (page < n) Color.White else Color(0x66FFFFFF)))
            }
        }
    }
}
