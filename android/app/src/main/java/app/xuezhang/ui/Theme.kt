package app.xuezhang.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.xuezhang.R

object C {
    // 重构版 v0.0.1Ex：象牙白 → 淡薰衣草渐变底，玉绿色强调
    val Bg = Color(0xFFF7F3EC)
    val Bg2 = Color(0xFFEEEAF6)
    val Jade = Color(0xFF2E8B6A)
    val JadeDeep = Color(0xFF1F6B50)
    val JadeSoft = Color(0xFFDDF0E6)
    val Card = Color.White
    val Ink = Color(0xFF1D1D1F)
    val Sec = Color(0xFF8A8A8E)
    val Ter = Color(0xFFC7C7CC)
    val Line = Color(0x14000000)
    /** 强调色（历史上叫 Blue，重构版改为玉绿）。 */
    val Blue = Jade
    val Navy = Color(0xFF2F4E7E)
    val Red = Color(0xFFE5342B)
    val Orange = Color(0xFFE8770F)
    val Green = Color(0xFF2DA44E)
    val Purple = Color(0xFF7D5BD9)
    val Fill = Color(0x14787880)

    val HlBlueBg = Color(0xFFE4EEFF); val HlBlue = Color(0xFF1758C7)
    val HlRedBg = Color(0xFFFFE3E1); val HlRed = Color(0xFFC2271D)
    val HlOrangeBg = Color(0xFFFFEBD3); val HlOrange = Color(0xFFB25000)
    val FillGray = Color(0xFF8E8E93)

    /** 科目配色：玉绿、藏青、赭红、橙、玫瑰、紫、青。 */
    val course = listOf(Color(0xFF2F7D5B), Color(0xFF2F4E7E), Color(0xFFB0553A), Color(0xFFE0872E), Color(0xFFC9667A), Color(0xFF6E5BA8), Color(0xFF2E8A94))
}

val Serif = FontFamily(
    Font(R.font.serif_bold, FontWeight.Bold),
    Font(R.font.serif_semibold, FontWeight.SemiBold),
)

object T {
    val Large = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 32.sp, color = C.Ink, lineHeight = 40.sp)
    val Title = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = C.Ink, lineHeight = 32.sp)
    val Head = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = C.Ink)
    val Item = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 16.5.sp, color = C.Ink)
    val Body = TextStyle(fontSize = 15.5.sp, color = C.Ink, lineHeight = 25.sp)
    val Label = TextStyle(fontSize = 15.sp, color = C.Ink)
    val Sub = TextStyle(fontSize = 13.sp, color = C.Sec)
    val Cap = TextStyle(fontSize = 12.sp, color = C.Sec)
    val Btn = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
}
