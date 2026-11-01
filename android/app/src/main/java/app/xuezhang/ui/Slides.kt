package app.xuezhang.ui

import androidx.compose.foundation.layout.width

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.size

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

    fun pageCount(path: String): Int = runCatching {
        when {
            path.endsWith(".pdf", true) -> ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { it.pageCount } }
            path.endsWith(".pptx", true) -> ZipFile(path).use { z -> z.entries().asSequence().count { it.name.matches(Regex("ppt/slides/slide\\d+\\.xml")) } }
            else -> 0
        }
    }.getOrDefault(0)

    /** page 从 1 开始。 */
    fun render(path: String, page: Int, widthPx: Int): Bitmap? {
        if (path.isBlank() || page < 1) return null
        val key = "$path#$page@$widthPx"
        cache.get(key)?.let { return it }
        // 磁盘缓存：原始课件页渲染一次后存 PNG，笔记里反复看不用重渲染
        val f = File(File(app.xuezhang.data.Store.dir, "slidecache").apply { mkdirs() }, "${(path + File(path).lastModified()).hashCode()}_${page}_$widthPx.png")
        if (f.exists()) runCatching { android.graphics.BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()?.let { cache.put(key, it); return it }
        val bmp = synchronized(lock) {
            runCatching {
                when {
                    path.endsWith(".pdf", true) -> renderPdf(path, page, widthPx)
                    path.endsWith(".pptx", true) -> renderPptx(path, page, widthPx)
                    else -> null
                }
            }.getOrNull()
        }
        if (bmp != null) { cache.put(key, bmp); runCatching { f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } } }
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

    private fun renderPptx(path: String, page: Int, widthPx: Int): Bitmap? {
        ZipFile(path).use { z ->
            val xml = z.getEntry("ppt/slides/slide$page.xml")?.let { e -> z.getInputStream(e).readBytes().toString(Charsets.UTF_8) } ?: return null
            val rels = z.getEntry("ppt/slides/_rels/slide$page.xml.rels")?.let { e -> z.getInputStream(e).readBytes().toString(Charsets.UTF_8) }.orEmpty()
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
        var page by remember(cur) { mutableStateOf(page0) }
        var scale by remember(page) { mutableFloatStateOf(1f) }
        var off by remember(page) { mutableStateOf(Offset.Zero) }
        val img by produceState<ImageBitmap?>(null, path, page) {
            value = withContext(Dispatchers.IO) { SlideImages.render(path, page, 1800)?.asImageBitmap() }
        }
        Box(Modifier.fillMaxSize().background(Color(0xF2000000)).clickable(interactionSource = null, indication = null) {}) {
            img?.let {
                Image(it, "课件第 $page 页", Modifier.fillMaxSize().pointerInput(page) {
                    detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 4f); off = if (scale == 1f) Offset.Zero else off + pan }
                }.graphicsLayer { scaleX = scale; scaleY = scale; translationX = off.x; translationY = off.y }, contentScale = ContentScale.Fit)
            }
            // 10 课件全屏：关闭、直播中跟随老师翻页、底部胶片条选页
            var follow by remember { mutableStateOf(true) }
            val live = app.xuezhang.engine.Live.running.value
            LaunchedEffect(follow, app.xuezhang.engine.Live.currentPage.intValue, live) {
                val lp = app.xuezhang.engine.Live.currentPage.intValue
                if (live && follow && lp > 0) page = lp
            }
            androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF)).clickable { SlideViewer.open.value = null }, contentAlignment = Alignment.Center) {
                        Icon("close", Color.White, 18.dp, 2.4f)
                    }
                    BasicText(if (live) "课件（直播中）" else "课件", Modifier.weight(1f), style = T.Btn.copy(color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
                    if (live) BasicText("● " + app.xuezhang.engine.Live.fmt(app.xuezhang.engine.Live.elapsed.longValue), Modifier.clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF)).padding(horizontal = 10.dp, vertical = 6.dp),
                        style = T.Cap.copy(color = Color(0xFFFF6B6B), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                    else Spacer(Modifier.width(40.dp))
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color(0x22FFFFFF)).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText("第 $page / ${if (total > 0) total else "?"} 页", Modifier.weight(1f), style = T.Btn.copy(color = Color.White, fontSize = 14.sp))
                    if (live) {
                        BasicText("跟随老师翻页", style = T.Cap.copy(color = Color.White.copy(alpha = 0.8f)))
                        Spacer(Modifier.width(8.dp))
                        BasicText(if (follow) "ON" else "OFF", Modifier.clip(RoundedCornerShape(50)).background(if (follow) C.Jade else Color(0x44FFFFFF)).clickable { follow = !follow }
                            .padding(horizontal = 10.dp, vertical = 4.dp), style = T.Btn.copy(color = Color.White, fontSize = 12.sp))
                    }
                }
            }
            if (total > 1) androidx.compose.foundation.lazy.LazyRow(Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 14.dp),
                state = androidx.compose.foundation.lazy.rememberLazyListState((page - 3).coerceAtLeast(0)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(total) { i ->
                    val pg = i + 1
                    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(86.dp, 52.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x22FFFFFF))
                            .then(if (pg == page) Modifier.border(2.dp, C.Jade, RoundedCornerShape(8.dp)) else Modifier)
                            .clickable { follow = false; page = pg }) { SlideThumb(path, pg, Modifier.fillMaxSize(), widthPx = 260) }
                        BasicText("$pg", style = T.Cap.copy(color = if (pg == page) Color.White else Color.White.copy(alpha = 0.55f), fontSize = 11.sp))
                    }
                }
            }
        }
    }
}
