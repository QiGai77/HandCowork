package app.xuezhang.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * 内置示例课：一节真实的 B 站电机学录播课（链接见 demo.json 的 source）+ 配套课件 PDF + 预先生成的笔记。
 * 资源在 assets/demo/ 下，格式与 db.json 相同。
 */
object Demo {
    const val SOURCE = "demo"

    fun seed(ctx: Context) {
        if (Store.courses.any { it.source.startsWith(SOURCE) }) return
        val o = JSONObject(ctx.assets.open("demo/demo.json").bufferedReader().readText())
        val slidesDir = File(ctx.filesDir, "slides").apply { mkdirs() }
        val pdf = File(slidesDir, "demo-transformer.pdf")
        runCatching { ctx.assets.open("demo/slides.pdf").use { i -> pdf.outputStream().use { i.copyTo(it) } } }
        val ca = o.getJSONArray("courses")
        with(Store) {
            for (i in 0 until ca.length()) {
                val c = ca.getJSONObject(i).course()
                courses.add(0, c.copy(slidePath = if (pdf.exists()) pdf.absolutePath else ""))
            }
            val la = o.getJSONArray("lectures")
            for (i in 0 until la.length()) lectures.add(0, la.getJSONObject(i).lec().copy(date = System.currentTimeMillis()))
        }
    }
}
