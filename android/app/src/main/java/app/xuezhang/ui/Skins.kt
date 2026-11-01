package app.xuezhang.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** 复习卡 10 种默认样式（均非白卡）。 */
data class CardSkin(val name: String, val top: Color, val bottom: Color, val ink: Color, val sub: Color, val accent: Color, val dark: Boolean = false) {
    val brush get() = Brush.linearGradient(listOf(top, bottom))
}

object Skins {
    val cards = listOf(
        CardSkin("晨雾蓝", Color(0xFFE3EEFF), Color(0xFFC9DBFF), Color(0xFF14284B), Color(0xFF4A6290), Color(0xFF2F6FE4)),
        CardSkin("薄荷", Color(0xFFDDF6EC), Color(0xFFB8EAD6), Color(0xFF0F3B2C), Color(0xFF3F7564), Color(0xFF1E9D6E)),
        CardSkin("杏桃", Color(0xFFFFE9DA), Color(0xFFFFCFB3), Color(0xFF4A2410), Color(0xFF8A5A3E), Color(0xFFE8770F)),
        CardSkin("薰衣草", Color(0xFFEDE6FF), Color(0xFFD5C8FF), Color(0xFF2B1E57), Color(0xFF63578F), Color(0xFF7D5BD9)),
        CardSkin("樱粉", Color(0xFFFFE6EE), Color(0xFFFFC9D9), Color(0xFF4D1427), Color(0xFF8D4D62), Color(0xFFE0457B)),
        CardSkin("柠檬", Color(0xFFFFF7D1), Color(0xFFFFEA9C), Color(0xFF433A0B), Color(0xFF7C6E32), Color(0xFFC59A00)),
        CardSkin("牛皮纸", Color(0xFFF3E6CF), Color(0xFFE2CBA4), Color(0xFF3B2A12), Color(0xFF75603F), Color(0xFFA0702A)),
        CardSkin("黑板", Color(0xFF29433A), Color(0xFF1B2F28), Color(0xFFF1F5EE), Color(0xFFA9C2B5), Color(0xFFF7D774), dark = true),
        CardSkin("夜空", Color(0xFF26304F), Color(0xFF141A30), Color(0xFFF0F2FA), Color(0xFFA6AEC9), Color(0xFF8DB4FF), dark = true),
        CardSkin("鎏金墨", Color(0xFF2A2621), Color(0xFF15130F), Color(0xFFF5EBD6), Color(0xFFB9A887), Color(0xFFD9B565), dark = true),
    )
    fun card(i: Int) = cards[((i % cards.size) + cards.size) % cards.size]

    /** 10 种万能封面：渐变 + 图案类型（0 波纹 1 网格 2 圆点 3 斜纹 4 同心圆 5 山峦 6 星点 7 方块 8 弧线 9 纯色鎏金框） */
    data class Cover(val name: String, val a: Color, val b: Color, val ink: Color, val pattern: Int)
    val covers = listOf(
        Cover("海波", Color(0xFF6FB6FF), Color(0xFF2E5FD8), Color.White, 0),
        Cover("方格本", Color(0xFFF4F1E8), Color(0xFFE4DDC8), Color(0xFF2B2A26), 1),
        Cover("圆点", Color(0xFFFFC2A8), Color(0xFFF2737B), Color.White, 2),
        Cover("斜纹", Color(0xFF7DD3B0), Color(0xFF2E8F78), Color.White, 3),
        Cover("年轮", Color(0xFFC9B6FF), Color(0xFF6E58D8), Color.White, 4),
        Cover("远山", Color(0xFFBFE3F0), Color(0xFF6B9BB8), Color(0xFF13324A), 5),
        Cover("星夜", Color(0xFF2B3566), Color(0xFF0E1330), Color(0xFFF5E7B8), 6),
        Cover("积木", Color(0xFFFFE07A), Color(0xFFF2A93B), Color(0xFF3A2A06), 7),
        Cover("弧光", Color(0xFFFF9FC6), Color(0xFF9C6BFF), Color.White, 8),
        Cover("墨金", Color(0xFF2B2722), Color(0xFF14120E), Color(0xFFE8CF8C), 9),
    )
    fun cover(i: Int) = covers[((i % covers.size) + covers.size) % covers.size]
    fun coverFor(id: String, style: Int) = if (style in covers.indices) covers[style] else cover(id.hashCode())
}
