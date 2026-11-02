package app.xuezhang.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.xuezhang.R

/** 主题状态：0 跟随系统 / 1 浅色 / 2 深色；在 App() 根部根据设置与系统同步。 */
object ThemeState {
    var dark by androidx.compose.runtime.mutableStateOf(false)
}

object C {
    private inline fun d(light: Long, dark: Long) = if (ThemeState.dark) Color(dark) else Color(light)
    /** v0.0.7.8 主题插件覆盖的颜色（未启用主题时为 null）。 */
    private fun t(k: String): Color? = app.xuezhang.engine.plugin.ThemeRegistry.active.value?.spec?.color(k)?.let { Color(it) }
    val Bg get() = t("bg") ?: d(0xFFF2F2F7, 0xFF0E1014)
    val Card get() = t("card") ?: d(0xFFFFFFFF, 0xFF1C1E24)
    val Ink get() = t("ink") ?: d(0xFF1D1D1F, 0xFFF2F2F5)
    val Sec get() = t("sec") ?: d(0xFF8A8A8E, 0xFF9C9CA3)
    val Ter get() = t("ter") ?: d(0xFFC7C7CC, 0xFF55565C)
    val Line get() = t("line") ?: d(0x14000000, 0x1FFFFFFF)
    val Blue get() = t("accent2") ?: Color(0xFF0A72E8)
    /** 主题强调色（墨韵江南 = 朱砂）。 */
    val Accent get() = t("accent") ?: Blue
    val Red = Color(0xFFE5342B)
    val Orange = Color(0xFFE8770F)
    val Green = Color(0xFF2DA44E)
    val Purple = Color(0xFF7D5BD9)
    val Fill get() = t("fill") ?: d(0x14787880, 0x29787880)

    val HlBlueBg get() = t("hlBlueBg") ?: d(0xFFE4EEFF, 0xFF1B2A44); val HlBlue get() = t("hlBlue") ?: d(0xFF1758C7, 0xFF8DB4FF)
    val HlRedBg get() = t("hlRedBg") ?: d(0xFFFFE3E1, 0xFF3D1E1C); val HlRed get() = t("hlRed") ?: d(0xFFC2271D, 0xFFFF9A92)
    val HlOrangeBg get() = t("hlOrangeBg") ?: d(0xFFFFEBD3, 0xFF3B2A14); val HlOrange get() = t("hlOrange") ?: d(0xFFB25000, 0xFFFFB866)
    val FillGray = Color(0xFF8E8E93)

    val course = listOf(Blue, Orange, Green, Red, Purple, Color(0xFF5856D6), Color(0xFF30B0C7))
}

val SerifDefault = FontFamily(
    Font(R.font.serif_bold, FontWeight.Bold),
    Font(R.font.serif_semibold, FontWeight.SemiBold),
)
/** 标题衬线字体：主题插件可替换（墨韵江南 = 霞鹜文楷）。 */
val Serif: FontFamily get() = ThemeFonts.serif() ?: SerifDefault
/** 正文字体：主题插件可替换；null = 系统默认。 */
val BodyFont: FontFamily? get() = ThemeFonts.body()

object T {
    val Large get() = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 32.sp, color = C.Ink, lineHeight = 40.sp)
    val Title get() = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = C.Ink, lineHeight = 32.sp)
    val Head get() = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = C.Ink)
    val Item get() = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 16.5.sp, color = C.Ink)
    val Body get() = TextStyle(fontFamily = BodyFont, fontSize = 15.5.sp, color = C.Ink, lineHeight = 25.sp)
    val Label get() = TextStyle(fontFamily = BodyFont, fontSize = 15.sp, color = C.Ink)
    val Sub get() = TextStyle(fontFamily = BodyFont, fontSize = 13.sp, color = C.Sec)
    val Cap get() = TextStyle(fontFamily = BodyFont, fontSize = 12.sp, color = C.Sec)
    val Btn get() = TextStyle(fontFamily = BodyFont, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
}
