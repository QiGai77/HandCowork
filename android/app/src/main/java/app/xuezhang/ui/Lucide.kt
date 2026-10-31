package app.xuezhang.ui

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser

/** Lucide 图标（ISC License, lucide.dev），24 网格描边路径。圆/矩形/线已换成 path。 */
object Lucide {
    private fun c(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"
    private fun rr(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"
    private fun l(x1: Float, y1: Float, x2: Float, y2: Float) = "M$x1 ${y1}L$x2 $y2"

    val D: Map<String, List<String>> = mapOf(
        "download" to listOf("M12 15V3", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "m7 10 5 5 5-5"),
        "cloudup" to listOf("M12 13v8", "M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242", "m8 17 4-4 4 4"),
        "clouddown" to listOf("M12 13v8l-4-4", "m12 21 4-4", "M4.393 15.269A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.436 8.284"),
        "server" to listOf(rr(2f, 2f, 20f, 8f, 2f), rr(2f, 14f, 20f, 8f, 2f), "M6 6h.01", "M6 18h.01"),
        "login" to listOf("m10 17 5-5-5-5", "M15 12H3", "M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"),
        "gauge" to listOf("m12 14 4-4", "M3.34 19a10 10 0 1 1 17.32 0"),
        "folder" to listOf("M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z"),
        "search" to listOf(c(11f, 11f, 8f), "m21 21-4.3-4.3"),
        "mic" to listOf("M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z", "M19 10v2a7 7 0 0 1-14 0v-2", l(12f, 19f, 12f, 22f)),
        "box" to listOf("M21 8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z", "m3.3 7 8.7 5 8.7-5", "M12 22V12"),
        "zap" to listOf("M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z"),
        "key" to listOf("M2.586 17.414A2 2 0 0 0 2 18.828V21a1 1 0 0 0 1 1h3a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h1a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h.172a2 2 0 0 0 1.414-.586l.814-.814a6.5 6.5 0 1 0-4-4z", c(16.5f, 7.5f, 0.5f)),
        "eye" to listOf("M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0", c(12f, 12f, 3f)),
        "link" to listOf("M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71", "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"),
        "logout" to listOf("m16 17 5-5-5-5", "M21 12H9", "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"),
        "phone" to listOf(rr(5f, 2f, 14f, 20f, 2f), "M12 18h.01"),
        "mail" to listOf(rr(2f, 4f, 20f, 16f, 2f), "m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"),
        "cloud" to listOf("M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z"),
        "shield" to listOf("M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z", "m9 12 2 2 4-4"),
        "chat" to listOf("M7.9 20A9 9 0 1 0 4 16.1L2 22Z"),
        "x" to listOf("M18 6 6 18", "m6 6 12 12"),
        "file" to listOf("M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z", "M14 2v4a2 2 0 0 0 2 2h4", "M10 9H8", "M16 13H8", "M16 17H8"),
        "scan" to listOf("M3 7V5a2 2 0 0 1 2-2h2", "M17 3h2a2 2 0 0 1 2 2v2", "M21 17v2a2 2 0 0 1-2 2h-2", "M7 21H5a2 2 0 0 1-2-2v-2", "M7 12h10"),
        "mega" to listOf("m3 11 18-5v12L3 14v-3z", "M11.6 16.8a3 3 0 1 1-5.8-1.6"),
        "tv" to listOf(rr(2f, 7f, 20f, 15f, 2f), "M17 2 12 7 7 2"),
        "pres" to listOf("M2 3h20", "M21 3v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V3", "m7 21 5-5 5 5"),
        "cal" to listOf(rr(3f, 4f, 18f, 18f, 2f), "M16 2v4", "M8 2v4", "M3 10h18"),
        "pin" to listOf("M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0", c(12f, 10f, 3f)),
        "clock" to listOf(c(12f, 12f, 10f), "M12 6v6l4 2"),
        "list" to listOf("M3 12h.01", "M3 18h.01", "M3 6h.01", "M8 12h13", "M8 18h13", "M8 6h13"),
        "image" to listOf(rr(3f, 3f, 18f, 18f, 2f), c(9f, 9f, 2f), "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"),
        "camera" to listOf("M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z", c(12f, 13f, 3f)),
        "chevd" to listOf("m6 9 6 6 6-6"),
        "chevu" to listOf("m18 15-6-6-6 6"),
        "sparkle" to listOf("M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z"),
        "wand" to listOf("m21.64 3.64-1.28-1.28a1.21 1.21 0 0 0-1.72 0L2.36 18.64a1.21 1.21 0 0 0 0 1.72l1.28 1.28a1.2 1.2 0 0 0 1.72 0L21.64 5.36a1.2 1.2 0 0 0 0-1.72", "m14 7 3 3", "M5 6v4", "M19 14v4", "M10 2v2", "M7 8H3", "M21 16h-4", "M11 3H9"),
        "clipboard" to listOf(rr(8f, 2f, 8f, 4f, 1f), "M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"),
        "filetext" to listOf("M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z", "M14 2v4a2 2 0 0 0 2 2h4", "M16 13H8", "M16 17H8"),
        "grid" to listOf(rr(3f, 3f, 7f, 7f, 1f), rr(14f, 3f, 7f, 7f, 1f), rr(14f, 14f, 7f, 7f, 1f), rr(3f, 14f, 7f, 7f, 1f)),
    )
    private val cache = HashMap<String, List<Path>>()
    fun paths(name: String): List<Path>? {
        val d = D[name] ?: return null
        return cache.getOrPut(name) { d.map { PathParser().parsePathString(it).toPath() } }
    }
}
