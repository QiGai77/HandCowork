package app.xuezhang.engine.plugin

import org.json.JSONObject

/** 主题插件 theme.json（纯数据，解析可测）。颜色写 #RRGGBB 或 #AARRGGBB。 */
data class ThemeSpec(
    val name: String,
    val colors: Map<String, Long> = emptyMap(),
    val forceLight: Boolean = true,
    val texture: String = "",
    val textureAlpha: Float = 1f,
    val topArt: String = "",
    val bottomArt: String = "",
    val artAlpha: Float = 0.9f,
    val dots: Boolean = false,
    val serifFont: String = "",
    val bodyFont: String = "",
    val navStyle: String = "glass",
    val buttonStyle: String = "glass",
    val progressStyle: String = "bar",
    val islandBg: Long? = null,
    val islandText: Long? = null,
    val splash: String = "",
) {
    fun color(k: String): Long? = colors[k]
    companion object {
        val COLOR_KEYS = listOf("bg", "card", "ink", "sec", "ter", "line", "fill", "accent", "accent2", "seal", "hlRed", "hlRedBg", "hlBlue", "hlBlueBg",
            "hlOrange", "hlOrangeBg", "navActive", "glass", "glassBorder", "paper", "readerInk")

        fun parseColor(s: String): Long? {
            val h = s.trim().removePrefix("#")
            if (!Regex("^[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$").matches(h)) return null
            return if (h.length == 6) (0xFF000000 or h.toLong(16)) else h.toLong(16)
        }

        fun parse(json: String): ThemeSpec {
            val o = JSONObject(json)
            val c = o.optJSONObject("colors")
            val colors = c?.keys()?.asSequence()?.mapNotNull { k -> parseColor(c.optString(k))?.let { k to it } }?.toMap().orEmpty()
            val bg = o.optJSONObject("background") ?: JSONObject()
            val f = o.optJSONObject("fonts") ?: JSONObject()
            fun safe(p: String) = p.takeIf { it.isBlank() || PluginFiles.safePath(it) }.orEmpty()
            return ThemeSpec(
                name = o.optString("name"), colors = colors, forceLight = o.optBoolean("forceLight", true),
                texture = safe(bg.optString("texture")), textureAlpha = bg.optDouble("textureAlpha", 1.0).toFloat(),
                topArt = safe(bg.optString("top")), bottomArt = safe(bg.optString("bottom")), artAlpha = bg.optDouble("artAlpha", 0.9).toFloat(),
                dots = bg.optBoolean("dots", false),
                serifFont = safe(f.optString("serif")), bodyFont = safe(f.optString("body")),
                navStyle = o.optJSONObject("nav")?.optString("style", "glass") ?: "glass",
                buttonStyle = o.optJSONObject("button")?.optString("style", "glass") ?: "glass",
                progressStyle = o.optJSONObject("progress")?.optString("style", "bar") ?: "bar",
                islandBg = o.optJSONObject("island")?.optString("bg")?.let(::parseColor),
                islandText = o.optJSONObject("island")?.optString("text")?.let(::parseColor),
                splash = safe(o.optString("splash")),
            )
        }
    }
}

/** 开屏动画（theme / splash 插件提供）：App 内置几种「动画配方」，插件只给参数和素材。 */
data class SplashSpec(
    val type: String = "ink-bloom",
    val title: String = "课堂学长",
    val subtitle: String = "",
    val seal: String = "学长",
    val durationMs: Int = 2200,
    val paper: String = "",
    val mountains: String = "",
    val font: String = "",
    val ink: Long = 0xFF2B2B2B,
    val sealColor: Long = 0xFFB5442E,
    val paperColor: Long = 0xFFF3EEE2,
) {
    companion object {
        val TYPES = setOf("ink-bloom", "fade")
        fun parse(json: String): SplashSpec {
            val o = JSONObject(json)
            fun safe(p: String) = p.takeIf { it.isBlank() || PluginFiles.safePath(it) }.orEmpty()
            val t = o.optString("type", "ink-bloom").takeIf { it in TYPES } ?: "fade"
            return SplashSpec(t, o.optString("title", "课堂学长").take(8), o.optString("subtitle").take(16), o.optString("seal", "学长").take(4),
                o.optInt("durationMs", 2200).coerceIn(800, 3000), safe(o.optString("paper")), safe(o.optString("mountains")), safe(o.optString("font")),
                ThemeSpec.parseColor(o.optString("ink")) ?: 0xFF2B2B2B, ThemeSpec.parseColor(o.optString("sealColor")) ?: 0xFFB5442E,
                ThemeSpec.parseColor(o.optString("paperColor")) ?: 0xFFF3EEE2)
        }

        /** 动画时间轴（0..1 进度 → 各元素进度），纯函数便于测试。 */
        fun timeline(t: Float): Map<String, Float> {
            fun seg(a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
            return mapOf("drop" to seg(0f, 0.18f), "bloom" to seg(0.08f, 0.55f), "mountains" to seg(0.25f, 0.7f),
                "title" to seg(0.4f, 0.82f), "seal" to seg(0.78f, 0.92f), "out" to seg(0.94f, 1f))
        }
    }
}

/** 封面包 covers.json：[{file, spine, name, titleBox:{x,y,w,h,vertical}, titleColor}]。 */
data class CoverPack(val name: String, val covers: List<Cover>) {
    data class Cover(val file: String, val spine: String = "", val name: String = "", val x: Float = 0.3f, val y: Float = 0.53f, val w: Float = 0.49f, val h: Float = 0.185f,
                     val vertical: Boolean = false, val titleColor: Long? = null)
    companion object {
        fun parse(json: String): CoverPack {
            val o = JSONObject(json)
            val a = o.optJSONArray("covers") ?: org.json.JSONArray()
            val list = (0 until a.length()).mapNotNull { i ->
                val c = a.optJSONObject(i) ?: return@mapNotNull null
                val file = c.optString("file"); if (!PluginFiles.safePath(file)) return@mapNotNull null
                val tb = c.optJSONObject("titleBox") ?: JSONObject()
                Cover(file, c.optString("spine").takeIf { it.isBlank() || PluginFiles.safePath(it) }.orEmpty(), c.optString("name"),
                    tb.optDouble("x", 0.3).toFloat(), tb.optDouble("y", 0.53).toFloat(), tb.optDouble("w", 0.49).toFloat(), tb.optDouble("h", 0.185).toFloat(),
                    tb.optBoolean("vertical", false), ThemeSpec.parseColor(c.optString("titleColor")))
            }
            return CoverPack(o.optString("name"), list)
        }
    }
}
