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
    val Bg get() = d(0xFFF2F2F7, 0xFF0E1014)
    val Card get() = d(0xFFFFFFFF, 0xFF1C1E24)
    val Ink get() = d(0xFF1D1D1F, 0xFFF2F2F5)
    val Sec get() = d(0xFF8A8A8E, 0xFF9C9CA3)
    val Ter get() = d(0xFFC7C7CC, 0xFF55565C)
    val Line get() = d(0x14000000, 0x1FFFFFFF)
    val Blue = Color(0xFF0A72E8)
    val Red = Color(0xFFE5342B)
    val Orange = Color(0xFFE8770F)
    val Green = Color(0xFF2DA44E)
    val Purple = Color(0xFF7D5BD9)
    val Fill get() = d(0x14787880, 0x29787880)

    val HlBlueBg get() = d(0xFFE4EEFF, 0xFF1B2A44); val HlBlue get() = d(0xFF1758C7, 0xFF8DB4FF)
    val HlRedBg get() = d(0xFFFFE3E1, 0xFF3D1E1C); val HlRed get() = d(0xFFC2271D, 0xFFFF9A92)
    val HlOrangeBg get() = d(0xFFFFEBD3, 0xFF3B2A14); val HlOrange get() = d(0xFFB25000, 0xFFFFB866)
    val FillGray = Color(0xFF8E8E93)

    val course = listOf(Blue, Orange, Green, Red, Purple, Color(0xFF5856D6), Color(0xFF30B0C7))
}

val Serif = FontFamily(
    Font(R.font.serif_bold, FontWeight.Bold),
    Font(R.font.serif_semibold, FontWeight.SemiBold),
)

object T {
    val Large get() = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 32.sp, color = C.Ink, lineHeight = 40.sp)
    val Title get() = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = C.Ink, lineHeight = 32.sp)
    val Head get() = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = C.Ink)
    val Item get() = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 16.5.sp, color = C.Ink)
    val Body get() = TextStyle(fontSize = 15.5.sp, color = C.Ink, lineHeight = 25.sp)
    val Label get() = TextStyle(fontSize = 15.sp, color = C.Ink)
    val Sub get() = TextStyle(fontSize = 13.sp, color = C.Sec)
    val Cap get() = TextStyle(fontSize = 12.sp, color = C.Sec)
    val Btn get() = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
}
