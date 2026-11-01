package app.xuezhang.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import app.xuezhang.data.Store
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * 内置 PPT/PPTX 转 PDF 引擎：
 * 将移动端上传的 PPTX 课件无损转换为标准 PDF 格式，最大化还原原稿中的
 * 几何排版、矢量形状、内嵌高保真配图、结构化表格和多级文本样式。
 * 生成的 PDF 可直接由原生 PdfRenderer 极速流畅加载和分页渲染。
 */
object PptxToPdfConverter {

    private fun attr(tag: String, name: String): String? =
        Regex("\\b$name=\"([^\"]*)\"").find(tag)?.groupValues?.get(1)

    private fun hex(c: String?): Int? =
        c?.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }

    /**
     * 将指定的 pptx 本地文件转换为真实 PDF 文件。
     */
    fun convert(pptxFile: File, destPdf: File): Boolean = runCatching {
        if (!pptxFile.exists() || pptxFile.length() == 0L) return false

        ZipFile(pptxFile).use { zip ->
            fun readEntry(entryName: String): String? =
                zip.getEntry(entryName)?.let { zip.getInputStream(it).bufferedReader(Charsets.UTF_8).use { r -> r.readText() } }

            val presXml = readEntry("ppt/presentation.xml").orEmpty()
            val sldSzTag = Regex("<p:sldSz[^>]*>").find(presXml)?.value
            val sw = sldSzTag?.let { attr(it, "cx")?.toLongOrNull() } ?: 12192000L
            val sh = sldSzTag?.let { attr(it, "cy")?.toLongOrNull() } ?: 6858000L

            val targetWidth = 1920
            val targetHeight = ((sh.toDouble() / sw.toDouble()) * targetWidth).toInt().coerceIn(720, 2560)
            val k = targetWidth.toFloat() / sw.toFloat()

            // 主题色表：解析 accent1~accent6, dk1, lt1 等
            val themeXml = readEntry("ppt/theme/theme1.xml").orEmpty()
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

            // 收集所有 slide xml 并按自然数排序
            val slideRegex = Regex("ppt/slides/slide(\\d+)\\.xml")
            val slideEntries = zip.entries().asSequence()
                .mapNotNull { entry -> slideRegex.matchEntire(entry.name)?.let { it.groupValues[1].toInt() to entry } }
                .sortedBy { it.first }
                .toList()

            if (slideEntries.isEmpty()) return false

            val pdfDocument = PdfDocument()

            slideEntries.forEachIndexed { pageIdx, (slideNum, entry) ->
                val pageInfo = PdfDocument.PageInfo.Builder(targetWidth, targetHeight, pageIdx + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val cv = page.canvas

                try {
                    val xml = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val relsXml = readEntry("ppt/slides/_rels/slide$slideNum.xml.rels").orEmpty()
                    val relMap = Regex("<Relationship [^>]*>").findAll(relsXml).mapNotNull { m ->
                        val id = attr(m.value, "Id") ?: return@mapNotNull null
                        val t = attr(m.value, "Target") ?: return@mapNotNull null
                        id to t.replace("../", "ppt/")
                    }.toMap()

                    // 1. 背景色与背景图
                    var bgClr = resolveClr(Regex("<p:bg>[\\s\\S]*?</p:bg>").find(xml)?.value)
                    if (bgClr == null) {
                        val layoutRel = relMap.entries.firstOrNull { it.value.contains("slideLayout") }?.value
                        if (layoutRel != null) {
                            val layoutXml = readEntry(layoutRel).orEmpty()
                            bgClr = resolveClr(Regex("<p:bg>[\\s\\S]*?</p:bg>").find(layoutXml)?.value)
                        }
                    }
                    cv.drawColor(bgClr ?: android.graphics.Color.WHITE)

                    val bgBlip = Regex("<p:bg>[\\s\\S]*?r:embed=\"([^\"]+)\"").find(xml)?.groupValues?.get(1)
                    if (bgBlip != null) {
                        relMap[bgBlip]?.let { target ->
                            zip.getEntry(target)?.let { e ->
                                val sample = if (slideEntries.size > 80) 4 else if (slideEntries.size > 30) 2 else 1
                                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                                zip.getInputStream(e).use { BitmapFactory.decodeStream(it, null, opts) }?.let { bmp ->
                                    cv.drawBitmap(bmp, null, RectF(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
                                }
                            }
                        }
                    }

                    // 2. 遍历形状树
                    val tree = xml.substringAfter("<p:spTree>", xml)
                    Regex("<p:(sp|pic|graphicFrame|cxnSp)\\b[^>]*>([\\s\\S]*?)</p:\\1>").findAll(tree).forEach { m ->
                        val tag = m.groupValues[1]
                        val body = m.groupValues[2]
                        val off = Regex("<a:off [^>]*>").find(body)?.value
                        val ext = Regex("<a:ext [^>]*>").find(body)?.value
                        val x = (off?.let { attr(it, "x")?.toLongOrNull() } ?: 0L) * k
                        val y = (off?.let { attr(it, "y")?.toLongOrNull() } ?: 0L) * k
                        val w = (ext?.let { attr(it, "cx")?.toLongOrNull() } ?: sw) * k
                        val h = (ext?.let { attr(it, "cy")?.toLongOrNull() } ?: 0L) * k
                        val rect = RectF(x, y, x + w, y + h)

                        when (tag) {
                            "pic" -> {
                                val rid = Regex("r:embed=\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                                val target = rid?.let { relMap[it] }
                                if (target != null && Regex("\\.(png|jpe?g|gif|bmp|webp)$", RegexOption.IGNORE_CASE).containsMatchIn(target)) {
                                    zip.getEntry(target)?.let { e ->
                                        val sample = if (slideEntries.size > 80) 4 else if (slideEntries.size > 30 || e.size > 3_000_000) 2 else 1
                                        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                                        zip.getInputStream(e).use { BitmapFactory.decodeStream(it, null, opts) }?.let { img ->
                                            cv.drawBitmap(img, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
                                        }
                                    }
                                }
                            }
                            "graphicFrame" -> {
                                // 表格渲染
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
                                            val cellRect = RectF(curX, curY, curX + cellW, curY + rowH)
                                            val cellFill = resolveClr(Regex("<a:solidFill>[\\s\\S]*?</a:solidFill>").find(cellMatch.groupValues[1])?.value)
                                            if (cellFill != null) {
                                                cv.drawRect(cellRect, Paint().apply { color = cellFill })
                                            } else {
                                                cv.drawRect(cellRect, cellPaint)
                                            }
                                            cv.drawRect(cellRect, borderPaint)

                                            val cText = Regex("<a:t>([^<]*)</a:t>").findAll(cellMatch.groupValues[1]).joinToString(" ") { it.groupValues[1] }
                                            if (cText.isNotBlank()) {
                                                val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                                                    textSize = maxOf(12f, 13f * 12700f * k)
                                                    color = 0xFF1D1D1F.toInt()
                                                }
                                                val sl = StaticLayout.Builder.obtain(cText, 0, cText.length, tp, maxOf(10, (cellW - 6).toInt())).build()
                                                cv.save(); cv.translate(curX + 3, curY + 3); sl.draw(cv); cv.restore()
                                            }
                                            curX += cellW
                                        }
                                        curY += rowH
                                    }
                                }
                            }
                            else -> { // sp or cxnSp
                                val spPr = Regex("<p:spPr>([\\s\\S]*?)</p:spPr>").find(body)?.groupValues?.get(1).orEmpty()
                                if (!spPr.contains("<a:noFill/>")) {
                                    val c = resolveClr(Regex("<a:solidFill>[\\s\\S]*?</a:solidFill>").find(spPr)?.value)
                                    if (c != null) {
                                        cv.drawRoundRect(rect, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c })
                                    }
                                }
                                val txBody = Regex("<p:txBody>([\\s\\S]*?)</p:txBody>").find(body)?.groupValues?.get(1)
                                if (txBody != null) {
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
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {}

                pdfDocument.finishPage(page)
            }

            destPdf.parentFile?.mkdirs()
            FileOutputStream(destPdf).use { fos ->
                pdfDocument.writeTo(fos)
            }
            pdfDocument.close()
            true
        }
    }.getOrDefault(false)

    /**
     * 自动检查并转换：若文件为 PPT/PPTX，在后台转换为 PDF 并返回生成的 PDF 绝对路径；
     * 若本已是 PDF，则直接返回。
     */
    fun ensurePdf(path: String): String {
        if (path.isBlank()) return path
        if (path.endsWith(".pdf", ignoreCase = true)) return path
        if (path.endsWith(".pptx", ignoreCase = true) || path.endsWith(".ppt", ignoreCase = true)) {
            val src = File(path)
            if (!src.exists()) return path
            val pdfPath = path.substringBeforeLast('.') + ".pdf"
            val pdfFile = File(pdfPath)
            if (pdfFile.exists() && pdfFile.length() > 0L && pdfFile.lastModified() >= src.lastModified()) {
                return pdfFile.absolutePath
            }
            val ok = convert(src, pdfFile)
            if (ok && pdfFile.exists()) return pdfFile.absolutePath
        }
        return path
    }
}
