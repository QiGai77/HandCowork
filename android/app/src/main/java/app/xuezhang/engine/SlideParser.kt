package app.xuezhang.engine

import android.content.Context
import android.net.Uri
import app.xuezhang.data.Slide
import app.xuezhang.net.Api
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.util.zip.ZipInputStream

object SlideParser {
    data class Result(val name: String, val pages: Int, val hotwords: List<String>, val slides: List<Slide>)

    fun extract(ctx: Context, uri: Uri, name: String, onProgress: (Int, Int) -> Unit): List<Pair<Int, String>> {
        val isPdf = (uri.path?.endsWith(".pdf", ignoreCase = true) == true) || name.endsWith(".pdf", ignoreCase = true)
        return if (isPdf) {
            PDFBoxResourceLoader.init(ctx)
            (if (uri.scheme == "file") java.io.FileInputStream(uri.path!!) else ctx.contentResolver.openInputStream(uri)!!).use { ins ->
                PDDocument.load(ins).use { doc ->
                    val st = PDFTextStripper()
                    (1..doc.numberOfPages).map { p ->
                        st.startPage = p; st.endPage = p
                        onProgress(p, doc.numberOfPages)
                        p to st.getText(doc).trim()
                    }
                }
            }
        } else {
            val re = Regex("ppt/slides/slide(\\d+)\\.xml")
            fun text(xml: String) = Regex("<a:t>([^<]*)</a:t>").findAll(xml).joinToString(" ") { it.groupValues[1] }
            if (uri.scheme == "file") {
                val file = java.io.File(uri.path!!)
                java.util.zip.ZipFile(file).use { zf ->
                    val entries = zf.entries().toList().mapNotNull { e -> re.matchEntire(e.name)?.let { it.groupValues[1].toInt() to e } }.sortedBy { it.first }
                    entries.mapIndexed { i, (n, e) ->
                        onProgress(i + 1, entries.size)
                        (i + 1) to text(zf.getInputStream(e).use { it.readBytes().toString(Charsets.UTF_8) })
                    }
                }
            } else {
                val map = sortedMapOf<Int, String>()
                ctx.contentResolver.openInputStream(uri)!!.use { ins ->
                    ZipInputStream(ins).use { z ->
                        var e = z.nextEntry
                        while (e != null) {
                            re.matchEntire(e.name)?.let { m -> map[m.groupValues[1].toInt()] = text(z.readBytes().toString(Charsets.UTF_8)); onProgress(map.size, 0) }
                            e = z.nextEntry
                        }
                    }
                }
                map.entries.mapIndexed { i, en -> onProgress(i + 1, map.size); (i + 1) to en.value }
            }
        }
    }

    /** 把课件复制到 app 私有目录，保留原文件并返回本地私有绝对路径。 */
    fun copyLocal(ctx: Context, uri: Uri, name: String, id: String): String = runCatching {
        val ext = name.substringAfterLast('.', "pdf").lowercase().take(5)
        val dir = java.io.File(ctx.filesDir, "slides").apply { mkdirs() }
        val f = java.io.File(dir, "$id.$ext")
        (if (uri.scheme == "file") java.io.FileInputStream(uri.path!!) else ctx.contentResolver.openInputStream(uri)!!).use { i ->
            f.outputStream().use { i.copyTo(it) }
        }
        f.absolutePath
    }.getOrDefault("")

    /** 没有大模型时的兜底：用每页第一行当标题。 */
    fun plainSlides(pages: List<Pair<Int, String>>): List<Slide> = pages.filter { it.second.isNotBlank() }.take(60).map { (p, t) ->
        Slide(p, t.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(20) ?: "第 $p 页", t.take(400))
    }

    fun analyze(pages: List<Pair<Int, String>>): Pair<List<String>, List<Slide>> {
        val text = pages.joinToString("\n") { (p, t) -> "【P.$p】${t.replace(Regex("\\s+"), " ").take(500)}" }.take(16000)
        val o = Api.chatJson(Prompts.get("slides"), text)
        val hot = o.optJSONArray("hotwords")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        val slides = o.optJSONArray("slides")?.let { a ->
            (0 until a.length()).map { i ->
                val s = a.getJSONObject(i)
                val p = s.optInt("page")
                Slide(p, s.optString("title"), pages.firstOrNull { it.first == p }?.second?.take(400) ?: "",
                    s.optJSONArray("formulas")?.let { f -> (0 until f.length()).map { f.getString(it) } } ?: emptyList())
            }
        } ?: emptyList()
        return hot to slides
    }
}
