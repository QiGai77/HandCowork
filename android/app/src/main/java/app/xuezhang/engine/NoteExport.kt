package app.xuezhang.engine

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.core.content.FileProvider
import app.xuezhang.data.Lecture
import app.xuezhang.data.Store
import app.xuezhang.ui.TeX
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 笔记导出：Markdown / Word(DOCX) / PDF / 长图，然后走系统分享。 */
object NoteExport {
    enum class Fmt(val label: String, val ext: String, val mime: String) {
        MD("Markdown", "md", "text/markdown"),
        DOCX("Word 文档", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        PDF("PDF", "pdf", "application/pdf"),
        IMG("长图", "png", "image/png"),
    }

    // ---------------- 中间结构 ----------------
    data class Span(val text: String, val bold: Boolean = false, val color: Int = 0, val underline: Boolean = false, val math: Boolean = false)
    sealed class B {
        data class H(val level: Int, val text: String) : B()
        data class P(val spans: List<Span>, val bullet: Boolean = false) : B()
        data class M(val tex: String) : B()
        data class Meta(val text: String) : B()
    }

    private const val BLUE = 0xFF0A84FF.toInt(); private const val RED = 0xFFE5484D.toInt(); private const val ORANGE = 0xFFE08600.toInt()

    fun inline(src: String): List<Span> {
        val out = mutableListOf<Span>()
        val re = Regex("\\[([brouf]):([^\\]]*)\\]|\\*\\*(.+?)\\*\\*|\\$([^$]+)\\$")
        var i = 0
        re.findAll(src).forEach { m ->
            if (m.range.first > i) out.add(Span(src.substring(i, m.range.first)))
            when {
                m.groupValues[1].isNotEmpty() -> {
                    val t = m.groupValues[2]
                    out.add(when (m.groupValues[1]) {
                        "b" -> Span(t, bold = true, color = BLUE); "r" -> Span(t, bold = true, color = RED)
                        "o" -> Span(t, color = ORANGE); "u" -> Span(t, underline = true); else -> Span(t, color = 0xFF8E8E93.toInt())
                    })
                }
                m.groupValues[3].isNotEmpty() -> out.add(Span(m.groupValues[3], bold = true))
                else -> out.add(Span(m.groupValues[4], math = true))
            }
            i = m.range.last + 1
        }
        if (i < src.length) out.add(Span(src.substring(i)))
        return out
    }

    private fun body(text: String, out: MutableList<B>) {
        val t = text.replace(Regex("```ui[\\s\\S]*?```"), "\n（此处为交互组件，请在课堂学长里查看）\n").replace(Regex("```[a-z]*"), "")
        val parts = Regex("\\$\\$([\\s\\S]+?)\\$\\$").split(t)
        val maths = Regex("\\$\\$([\\s\\S]+?)\\$\\$").findAll(t).map { it.groupValues[1].trim() }.toList()
        parts.forEachIndexed { k, chunk ->
            chunk.lines().map { it.trimEnd() }.filter { it.isNotBlank() }.forEach { ln ->
                val l = ln.trim()
                when {
                    l.startsWith("### ") -> out.add(B.H(3, l.removePrefix("### ")))
                    l.startsWith("## ") -> out.add(B.H(2, l.removePrefix("## ")))
                    l.startsWith("# ") -> out.add(B.H(2, l.removePrefix("# ")))
                    l.startsWith("- ") || l.startsWith("* ") || l.startsWith("• ") -> out.add(B.P(inline(l.drop(2)), bullet = true))
                    else -> out.add(B.P(inline(l)))
                }
            }
            maths.getOrNull(k)?.let { out.add(B.M(it)) }
        }
    }

    fun blocks(l: Lecture): List<B> {
        val out = mutableListOf<B>()
        out.add(B.H(1, l.title))
        val course = Store.course(l.courseId)?.name.orEmpty()
        out.add(B.Meta(listOf(course, SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(l.date))).filter { it.isNotBlank() }.joinToString(" · ")))
        if (l.sections.isNotEmpty()) l.sections.forEach { s ->
            out.add(B.H(2, s.title + if (s.pages.isNotEmpty()) "（P.${s.pages.joinToString(",")}）" else ""))
            body(s.body, out)
        } else body(l.notes, out)
        if (l.mnemonics.isNotEmpty()) { out.add(B.H(2, "助记")); l.mnemonics.forEach { out.add(B.P(listOf(Span(it.title + "  ", bold = true)) + inline(it.body), bullet = true)) } }
        if (l.cards.isNotEmpty()) { out.add(B.H(2, "复习卡")); l.cards.forEach { out.add(B.P(listOf(Span("Q：", bold = true)) + inline(it.q))); out.add(B.P(listOf(Span("A：", bold = true)) + inline(it.a))) } }
        return out
    }

    // ---------------- Markdown ----------------
    fun markdown(l: Lecture): String = buildString {
        fun md(s: String) = s.replace(Regex("\\[[br]:([^\\]]*)\\]"), "**$1**").replace(Regex("\\[u:([^\\]]*)\\]"), "<u>$1</u>").replace(Regex("\\[[of]:([^\\]]*)\\]"), "$1")
        append("# ${l.title}\n\n")
        Store.course(l.courseId)?.name?.let { append("> $it · ${SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(l.date))}\n\n") }
        if (l.sections.isNotEmpty()) l.sections.forEach { append("## ${it.title}" + (if (it.pages.isNotEmpty()) "（P.${it.pages.joinToString(",")}）" else "") + "\n\n${md(it.body)}\n\n") }
        else append("${md(l.notes)}\n\n")
        if (l.mnemonics.isNotEmpty()) { append("## 助记\n\n"); l.mnemonics.forEach { append("- **${it.title}** ${md(it.body)}\n") }; append("\n") }
        if (l.cards.isNotEmpty()) { append("## 复习卡\n\n"); l.cards.forEach { append("**Q：** ${md(it.q)}\n\n**A：** ${md(it.a)}\n\n") } }
    }

    // ---------------- DOCX（手写最小 OOXML） ----------------
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .filter { it == '\t' || it == '\n' || it >= ' ' }

    private fun run(s: Span, size: Int = 22): String {
        val rpr = buildString {
            append("<w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:eastAsia=\"微软雅黑\"/>")
            if (s.bold) append("<w:b/>"); if (s.math) append("<w:i/>"); if (s.underline) append("<w:u w:val=\"single\"/>")
            if (s.color != 0) append("<w:color w:val=\"%06X\"/>".format(s.color and 0xFFFFFF))
            append("<w:sz w:val=\"$size\"/></w:rPr>")
        }
        return "<w:r>$rpr<w:t xml:space=\"preserve\">${esc(s.text)}</w:t></w:r>"
    }

    fun docx(l: Lecture, out: File) {
        val imgs = mutableListOf<ByteArray>()
        val bodyXml = StringBuilder()
        blocks(l).forEach { b ->
            when (b) {
                is B.H -> bodyXml.append("<w:p><w:pPr><w:spacing w:before=\"${if (b.level == 1) 0 else 240}\" w:after=\"120\"/></w:pPr>${run(Span(b.text, bold = true), when (b.level) { 1 -> 40; 2 -> 30; else -> 26 })}</w:p>")
                is B.Meta -> bodyXml.append("<w:p>${run(Span(b.text, color = 0xFF8E8E93.toInt()), 20)}</w:p>")
                is B.P -> bodyXml.append("<w:p><w:pPr><w:spacing w:after=\"80\"/>${if (b.bullet) "<w:ind w:left=\"360\" w:hanging=\"240\"/>" else ""}</w:pPr>" +
                        (if (b.bullet) run(Span("• ")) else "") + b.spans.joinToString("") { run(it) } + "</w:p>")
                is B.M -> {
                    val bmp = TeX.bitmap(b.tex, 64f, Color.BLACK)
                    if (bmp == null) bodyXml.append("<w:p>${run(Span(b.tex, math = true))}</w:p>")
                    else {
                        val png = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                        imgs.add(png); val id = imgs.size
                        val maxW = 5_900_000L // ≈ 16.4cm
                        var cx = bmp.width * 9525L * 72 / 96 / 2; var cy = bmp.height * 9525L * 72 / 96 / 2
                        if (cx > maxW) { cy = cy * maxW / cx; cx = maxW }
                        bodyXml.append("<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\"><wp:extent cx=\"$cx\" cy=\"$cy\"/><wp:docPr id=\"$id\" name=\"formula$id\"/>" +
                                "<a:graphic xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
                                "<pic:nvPicPr><pic:cNvPr id=\"$id\" name=\"f$id.png\"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed=\"rImg$id\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>" +
                                "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>")
                    }
                }
            }
        }
        val doc = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" " +
                "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\">" +
                "<w:body>$bodyXml<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1300\" w:right=\"1300\" w:bottom=\"1300\" w:left=\"1300\" w:header=\"720\" w:footer=\"720\" w:gutter=\"0\"/></w:sectPr></w:body></w:document>"
        val types = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Default Extension=\"png\" ContentType=\"image/png\"/>" +
                "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>"
        val rels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>"
        val docRels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                imgs.indices.joinToString("") { "<Relationship Id=\"rImg${it + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/f${it + 1}.png\"/>" } +
                "</Relationships>"
        ZipOutputStream(out.outputStream()).use { z ->
            fun put(n: String, b: ByteArray) { z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() }
            put("[Content_Types].xml", types.toByteArray()); put("_rels/.rels", rels.toByteArray())
            put("word/document.xml", doc.toByteArray()); put("word/_rels/document.xml.rels", docRels.toByteArray())
            imgs.forEachIndexed { i, b -> put("word/media/f${i + 1}.png", b) }
        }
    }

    // ---------------- 渲染（PDF 与长图共用） ----------------
    private class Item(val layout: StaticLayout?, val bmp: Bitmap?, val gapBefore: Float, val indent: Float)

    private fun layoutItems(l: Lecture, width: Int, scale: Float): List<Item> {
        val items = mutableListOf<Item>()
        fun paint(size: Float, bold: Boolean = false, color: Int = 0xFF1D1D1F.toInt()) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size * scale; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        fun mk(text: CharSequence, p: TextPaint, w: Int) = StaticLayout.Builder.obtain(text, 0, text.length, p, w).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.32f).setIncludePad(false).build()
        blocks(l).forEach { b ->
            when (b) {
                is B.H -> items.add(Item(mk(b.text, paint(when (b.level) { 1 -> 22f; 2 -> 16.5f; else -> 14.5f }, true), width), null, if (b.level == 1) 0f else 16f * scale, 0f))
                is B.Meta -> items.add(Item(mk(b.text, paint(10.5f, color = 0xFF8E8E93.toInt()), width), null, 4f * scale, 0f))
                is B.P -> {
                    val sb = SpannableStringBuilder()
                    if (b.bullet) sb.append("•  ")
                    b.spans.forEach { s ->
                        val st = sb.length; sb.append(s.text)
                        if (s.bold) sb.setSpan(StyleSpan(Typeface.BOLD), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        if (s.math) sb.setSpan(StyleSpan(Typeface.ITALIC), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        if (s.underline) sb.setSpan(UnderlineSpan(), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        if (s.color != 0) sb.setSpan(ForegroundColorSpan(s.color), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    val ind = if (b.bullet) 8f * scale else 0f
                    items.add(Item(mk(sb, paint(11.5f), (width - ind).toInt()), null, 5f * scale, ind))
                }
                is B.M -> {
                    val bmp = TeX.bitmap(b.tex, 15f * scale * 1.3f, Color.BLACK)
                    if (bmp != null) {
                        val s = if (bmp.width > width) width.toFloat() / bmp.width else 1f
                        items.add(Item(null, if (s < 1f) Bitmap.createScaledBitmap(bmp, width, (bmp.height * s).toInt().coerceAtLeast(1), true) else bmp, 8f * scale, 0f))
                    } else items.add(Item(mk(b.tex, paint(11.5f), width), null, 5f * scale, 0f))
                }
            }
        }
        return items
    }

    fun pdf(l: Lecture, out: File) {
        val pw = 595; val ph = 842; val m = 50f
        val w = (pw - 2 * m).toInt()
        val items = layoutItems(l, w, 1f)
        val doc = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = m
        fun newPage() { page?.let { doc.finishPage(it) }; pageNo++; page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, pageNo).create()); y = m
            page!!.canvas.drawText("课堂学长 · ${l.title.take(30)}", m, ph - 24f, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = 0xFFAEAEB2.toInt() })
            page!!.canvas.drawText("$pageNo", pw - m, ph - 24f, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = 0xFFAEAEB2.toInt(); textAlign = Paint.Align.RIGHT }) }
        newPage()
        val bottom = ph - m
        items.forEach { it ->
            y += it.gapBefore
            if (it.bmp != null) {
                if (y + it.bmp.height > bottom) newPage()
                page!!.canvas.drawBitmap(it.bmp, m + (w - it.bmp.width) / 2f, y, null); y += it.bmp.height
            } else it.layout?.let { lay ->
                for (li in 0 until lay.lineCount) {
                    val top = lay.getLineTop(li).toFloat(); val bot = lay.getLineBottom(li).toFloat()
                    if (y + (bot - top) > bottom) newPage()
                    val c = page!!.canvas
                    c.save(); c.translate(m + it.indent, y - top); c.clipRect(0f, top, lay.width.toFloat(), bot); lay.draw(c); c.restore()
                    y += bot - top
                }
            }
        }
        page?.let { doc.finishPage(it) }
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
    }

    fun longImage(l: Lecture, out: File) {
        val W = 1080; val scale = 3f; val m = 64f
        val w = (W - 2 * m).toInt()
        val items = layoutItems(l, w, scale)
        var h = m
        items.forEach { h += it.gapBefore + (it.bmp?.height ?: it.layout?.height ?: 0) }
        h += m + 90f
        val H = h.toInt().coerceAtMost(30000)
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        c.drawRect(0f, 0f, W.toFloat(), 14f, Paint().apply { color = BLUE })
        var y = m
        items.forEach { it ->
            y += it.gapBefore
            if (y > H) return@forEach
            if (it.bmp != null) { c.drawBitmap(it.bmp, m + (w - it.bmp.width) / 2f, y, null); y += it.bmp.height }
            else it.layout?.let { lay -> c.save(); c.translate(m + it.indent, y); lay.draw(c); c.restore(); y += lay.height }
        }
        val fp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30f; color = 0xFFAEAEB2.toInt(); textAlign = Paint.Align.CENTER }
        c.drawText("—— 由「课堂学长」生成 ——", W / 2f, H - 50f, fp)
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }

    /** IO 线程生成文件；返回分享用的 Intent。 */
    fun export(ctx: Context, l: Lecture, f: Fmt): Intent {
        val dir = File(ctx.cacheDir, "share").apply { deleteRecursively(); mkdirs() }
        val name = l.title.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").take(40).ifBlank { "笔记" }
        val file = File(dir, "$name.${f.ext}")
        when (f) {
            Fmt.MD -> file.writeText(markdown(l))
            Fmt.DOCX -> docx(l, file)
            Fmt.PDF -> pdf(l, file)
            Fmt.IMG -> longImage(l, file)
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).setType(f.mime).putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, l.title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (f == Fmt.MD) send.putExtra(Intent.EXTRA_TEXT, markdown(l))
        send.clipData = ClipData.newRawUri(l.title, uri)
        return Intent.createChooser(send, "分享笔记（${f.label}）").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
