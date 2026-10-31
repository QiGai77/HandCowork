package app.xuezhang.ui

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
        val bmp = synchronized(lock) {
            runCatching {
                when {
                    path.endsWith(".pdf", true) -> renderPdf(path, page, widthPx)
                    path.endsWith(".pptx", true) -> renderPptx(path, page, widthPx)
                    else -> null
                }
            }.getOrNull()
        }
        if (bmp != null) cache.put(key, bmp)
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
fun SlideThumb(path: String, page: Int, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val img by produceState<ImageBitmap?>(null, path, page) {
        value = withContext(Dispatchers.IO) { SlideImages.render(path, page, 900)?.asImageBitmap() }
    }
    Box(
        modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Color.White)
            .border(0.5.dp, Color(0x1A000000), RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        img?.let { Image(it, "第 $page 页", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            ?: BasicText("第 $page 页", Modifier.align(Alignment.Center), style = T.Sub)
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
        ps.forEach { p -> SlideThumb(path, p, Modifier.weight(1f)) { SlideViewer.open.value = Triple(path, p, total) } }
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
            Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("第 $page 页" + if (total > 0) " / $total" else "", Modifier.weight(1f), style = T.Btn.copy(color = Color.White))
                BasicText("关闭", Modifier.clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF)).clickable { SlideViewer.open.value = null }
                    .padding(horizontal = 16.dp, vertical = 8.dp), style = T.Btn.copy(color = Color.White))
            }
            Row(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                BasicText("上一页", Modifier.clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF)).clickable(enabled = page > 1) { page-- }
                    .padding(horizontal = 18.dp, vertical = 10.dp), style = T.Btn.copy(color = if (page > 1) Color.White else Color(0x66FFFFFF)))
                BasicText("下一页", Modifier.clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF)).clickable(enabled = total == 0 || page < total) { page++ }
                    .padding(horizontal = 18.dp, vertical = 10.dp), style = T.Btn.copy(color = if (total == 0 || page < total) Color.White else Color(0x66FFFFFF)))
            }
        }
    }
}
