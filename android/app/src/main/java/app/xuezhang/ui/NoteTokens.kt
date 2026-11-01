// 由 design/notes/gen_tokens.py 生成，请改 token 源文件后重新生成，不要手改。
package app.xuezhang.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 笔记模块 token：NT.Colors / NT.Type / NT.Dim / NT.Shadow */
object NT {
    object Colors {
        object Bg {
            val Top = Color(0xFFFEF9E6)
            val Mid = Color(0xFFF6F1E9)
            val Bottom = Color(0xFFEAE7EE)
            val Dot = Color(0x0F000000)
        }
        object Ink {
            val Primary = Color(0xFF1A1A1A)
            val Secondary = Color(0xFF78766B)
            val Tertiary = Color(0xFF8B8784)
            val Count = Color(0xFF837E79)
            val OnDark = Color(0xFFF6F6F6)
            val OnDarkSec = Color(0x8CFFFFFF)
        }
        object Glass {
            val Card = Color(0x9EFFFFFF)
            val CardBorder = Color(0xB3FFFFFF)
            val Sheet = Color(0xB8FFFFFF)
            val Board = Color(0x8CFFFFFF)
            val BoardEdge = Color(0x66C6B69F)
            val Nav = Color(0xCCF2F1F6)
            val NavActive = Color(0xFFC7DBDC)
            val Button = Color(0xCCFFFFFF)
        }
        object Segment {
            val Track = Color(0x0F000000)
            val Thumb = Color(0xFFFCFCFC)
            val On = Color(0xFF111111)
            val Off = Color(0xFF8B8784)
        }
        object Chip {
            val AllBg = Color(0xFFF8F8F3)
            val AllText = Color(0xFF111111)
            val Text = Color(0xFFFFFFFF)
            val BadgeBg = Color(0xFFD9D7D2)
            val BadgeText = Color(0xFF6E6B66)
        }
        object Tag {
            val Bg = Color(0x0D000000)
            val Text = Color(0xFF111111)
        }
        object Progress {
            val Track = Color(0xFFD1CFDC)
            val G0 = Color(0xFFBBE7E8)
            val G1 = Color(0xFFADCEED)
            val G2 = Color(0xFFBAAFF4)
            val Spark = Color(0xFFFFF4E0)
        }
        object Course {
            object Green {
                val Spine = Color(0xFF4C906B)
                val Cover = Color(0xFF498C6B)
                val Chip = Color(0xFF3F8762)
                val Deep = Color(0xFF1F5F3F)
            }
            object Blue {
                val Spine = Color(0xFF4B607B)
                val Cover = Color(0xFF3A5475)
                val Chip = Color(0xFF325072)
                val Deep = Color(0xFF14284A)
            }
            object Rust {
                val Spine = Color(0xFFB06953)
                val Cover = Color(0xFFA65F4B)
                val Chip = Color(0xFFAA5F48)
                val Deep = Color(0xFF8A3C25)
            }
        }
        object Gold {
            val Line = Color(0xFFD4B06A)
            val Shade = Color(0xFFA8823E)
            val Icon = Color(0xFFE2C88A)
        }
        object Reader {
            val Paper = Color(0xFFF7F0DE)
            val PaperEdge = Color(0xFFE9DEC2)
            val Gutter = Color(0x14000000)
            val Text = Color(0xFF2A271E)
            val Heading = Color(0xFF111111)
            val Caption = Color(0xFF8E8A7E)
            val HlText = Color(0xFF2F79A8)
            val HlBg = Color(0xFFD6E9F5)
            val ExamText = Color(0xFFA6352A)
            val ExamBg = Color(0xFFF6D9D2)
            val Ornament = Color(0xFFB9AE95)
            val ScrubTrack = Color(0xB3FFFFFF)
            val Tick = Color(0xFFB8B4C2)
            val Knob = Color(0xFFE7E9EC)
        }
        object Organize {
            val Done = Color(0xFF3B886D)
            val Active = Color(0xFF4FA684)
            val Pending = Color(0xFFC9C9CE)
            val Connector = Color(0xFFC9C9CE)
            val BarTrack = Color(0xFFDCE3DC)
            val Bar0 = Color(0xFF3FA27C)
            val Bar1 = Color(0xFF7FD3B0)
            val QuoteBg = Color(0xB3FFFFFF)
        }
        object Island {
            val Bg = Color(0xFF000000)
            val Select = Color(0xFF4FA684)
            val ApplyBg = Color(0xFFFFFFFF)
            val ApplyText = Color(0xFF000000)
            val DiscardBg = Color(0xFF3A3A3C)
            val DiscardText = Color(0xFFFFFFFF)
        }
        object NavTab {
            val Active = Color(0xFF5696AB)
            val Inactive = Color(0xFF101011)
        }
    }
    object Type {
        val PageTitle = TextStyle(fontFamily = Serif, fontWeight = FontWeight.W700, fontSize = 46.sp, lineHeight = 52.sp, color = NT.Colors.Ink.Primary)
        val PageSub = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W400, fontSize = 15.sp, lineHeight = 20.sp, color = NT.Colors.Ink.Secondary)
        val Segment = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W600, fontSize = 15.sp, lineHeight = 20.sp, color = NT.Colors.Ink.Primary)
        val Chip = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W500, fontSize = 14.sp, lineHeight = 18.sp, color = NT.Colors.Ink.Primary)
        val ChipBadge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W600, fontSize = 10.sp, lineHeight = 12.sp, color = NT.Colors.Ink.Primary)
        val CardTitle = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W700, fontSize = 18.sp, lineHeight = 24.sp, color = NT.Colors.Ink.Primary)
        val CardBody = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W400, fontSize = 13.5.sp, lineHeight = 19.sp, color = NT.Colors.Ink.Primary)
        val CardDate = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W400, fontSize = 13.sp, lineHeight = 18.sp, color = NT.Colors.Ink.Primary)
        val Tag = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W500, fontSize = 12.5.sp, lineHeight = 16.sp, color = NT.Colors.Ink.Primary)
        val ProgressLabel = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W500, fontSize = 12.5.sp, lineHeight = 16.sp, color = NT.Colors.Ink.Primary)
        val ShelfSection = TextStyle(fontFamily = Serif, fontWeight = FontWeight.W700, fontSize = 20.sp, lineHeight = 26.sp, color = NT.Colors.Ink.Primary)
        val ShelfCount = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W400, fontSize = 15.sp, lineHeight = 20.sp, color = NT.Colors.Ink.Count)
        val Spine = TextStyle(fontFamily = Serif, fontWeight = FontWeight.W600, fontSize = 12.5.sp, lineHeight = 15.sp, color = NT.Colors.Ink.Primary)
        val SpineDate = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W400, fontSize = 5.5.sp, lineHeight = 7.sp, color = NT.Colors.Ink.Primary)
        val ReaderNav = TextStyle(fontFamily = Serif, fontWeight = FontWeight.W700, fontSize = 18.sp, lineHeight = 24.sp, color = NT.Colors.Ink.Primary)
        val Chapter = TextStyle(fontFamily = Serif, fontWeight = FontWeight.W700, fontSize = 20.sp, lineHeight = 28.sp, color = NT.Colors.Ink.Primary)
        val ReaderBody = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W400, fontSize = 15.sp, lineHeight = 24.sp, color = NT.Colors.Ink.Primary)
        val ReaderCaption = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W400, fontSize = 11.sp, lineHeight = 14.sp, color = NT.Colors.Ink.Primary)
        val ScrubLabel = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W500, fontSize = 11.sp, lineHeight = 14.sp, color = NT.Colors.Ink.Primary)
        val Toolbar = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W500, fontSize = 11.sp, lineHeight = 14.sp, color = NT.Colors.Ink.Primary)
        val SheetTitle = TextStyle(fontFamily = Serif, fontWeight = FontWeight.W700, fontSize = 24.sp, lineHeight = 30.sp, color = NT.Colors.Ink.Primary)
        val Step = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W500, fontSize = 17.sp, lineHeight = 22.sp, color = NT.Colors.Ink.Primary)
        val StepMeta = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W400, fontSize = 13.sp, lineHeight = 18.sp, color = NT.Colors.Ink.Primary)
        val Quote = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W400, fontSize = 13.5.sp, lineHeight = 19.sp, color = NT.Colors.Ink.Primary)
        val IslandTitle = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W600, fontSize = 15.sp, lineHeight = 20.sp, color = NT.Colors.Ink.Primary)
        val IslandMeta = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W400, fontSize = 13.sp, lineHeight = 18.sp, color = NT.Colors.Ink.Primary)
        val IslandHint = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W400, fontSize = 12.sp, lineHeight = 16.sp, color = NT.Colors.Ink.Primary)
        val IslandBtn = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W600, fontSize = 15.sp, lineHeight = 20.sp, color = NT.Colors.Ink.Primary)
        val Nav = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.W500, fontSize = 10.5.sp, lineHeight = 13.sp, color = NT.Colors.Ink.Primary)
    }
    object Dim {
        object Screen {
            val PadH = 24.dp
            val TitleTop = 52.dp
            val SubGap = 4.dp
            val ListTop = 220.dp
        }
        object Segment {
            val W = 150.dp
            val H = 40.dp
            val R = 20.dp
            val Inset = 3.dp
            val ThumbW = 72.dp
        }
        object Chip {
            val H = 28.dp
            val R = 14.dp
            val PadH = 12.dp
            val Gap = 8.dp
            val Dot = 6.dp
            val DotGap = 6.dp
            val Badge = 14.dp
            val BadgeOffX = -2.dp
            val BadgeOffY = -6.dp
            val RowTop = 170.dp
        }
        object Card {
            val MinH = 87.dp
            val R = 16.dp
            val Pad = 12.dp
            val Gap = 8.dp
            val ThumbW = 56.dp
            val ThumbH = 66.dp
            val ThumbR = 3.dp
            val ThumbGap = 14.dp
            val TitleBodyGap = 4.dp
            val ExtraTop = 8.dp
            val Border = 1.dp
        }
        object Tag {
            val H = 24.dp
            val R = 12.dp
            val PadH = 10.dp
            val Gap = 8.dp
            val Icon = 13.dp
        }
        object Progress {
            val H = 4.dp
            val R = 2.dp
            val LabelGap = 8.dp
            val Spark = 12.dp
        }
        object Shelf {
            val FirstTop = 196.dp
            val Pitch = 194.dp
            val HeaderH = 26.dp
            val Dot = 7.dp
            val DotGap = 9.dp
            val HeaderToBooks = 18.dp
            val BookAreaH = 124.dp
            val SpineW = 23.dp
            val SpineGap = 1.dp
            val SpineR = 3.dp
            const val LeanDeg = -12
            val Band1 = 8.dp
            val Band2 = 13.dp
            val BandH = 1.5.dp
            val CoverW = 90.dp
            val CoverH = 116.dp
            val CoverR = 4.dp
            val CoverX = 268.dp
            val CoverIcon = 32.dp
            val CoverBandFromBottom = 12.dp
            val RibbonW = 9.dp
            val RibbonH = 14.dp
            val BoardH = 12.dp
            val BoardR = 6.dp
            val BoardInsetH = 36.dp
            val SpineLeft = 50.dp
            const val MaxSpines = 5
        }
        object Reader {
            val BackSize = 38.dp
            val BackTop = 58.dp
            val NavTop = 62.dp
            val BookTop = 138.dp
            val BookBottom = 680.dp
            val Cloth = 6.dp
            val PagePadH = 34.dp
            val ChapterTop = 52.dp
            val OrnamentW = 200.dp
            val SlideW = 248.dp
            val SlideH = 178.dp
            val SlideFrame = 6.dp
            val SlideR = 2.dp
            val CaptionGap = 10.dp
            val BodyTop = 26.dp
            val ParaGap = 14.dp
            const val IndentEm = 2
            val RibbonW = 30.dp
            val RibbonH = 22.dp
            val RibbonX = 62.dp
            val CurlW = 70.dp
        }
        object Scrub {
            val ThumbW = 84.dp
            val ThumbH = 58.dp
            val PopR = 12.dp
            val PopPad = 6.dp
            val TrackW = 350.dp
            val TrackH = 26.dp
            val TrackR = 13.dp
            val TickStep = 9.dp
            val TickH = 8.dp
            val TickMajorH = 12.dp
            val Knob = 16.dp
            val Top = 730.dp
        }
        object Toolbar {
            val H = 48.dp
            val Icon = 22.dp
            val LabelGap = 2.dp
            val Bottom = 18.dp
        }
        object Sheet {
            val W = 256.dp
            val X = 76.dp
            val Top = 368.dp
            val R = 24.dp
            val Pad = 18.dp
            val Blur = 24.dp
            val StepH = 34.dp
            val StepIcon = 14.dp
            val StepIconGap = 18.dp
            val Connector = 2.dp
            val Ring = 20.dp
            val RingStroke = 3.dp
            val BarH = 10.dp
            val BarR = 5.dp
            val BarTop = 16.dp
            val QuoteR = 12.dp
            val QuotePad = 10.dp
            val QuoteTop = 14.dp
        }
        object Island {
            val CollapsedW = 118.dp
            val CollapsedH = 36.dp
            val W = 372.dp
            val X = 10.dp
            val Top = 126.dp
            val R = 30.dp
            val Pad = 18.dp
            val ThumbW = 78.dp
            val ThumbH = 76.dp
            val ThumbR = 10.dp
            val ThumbGap = 10.dp
            val SelectStroke = 2.5.dp
            val Check = 18.dp
            val BtnW = 72.dp
            val BtnH = 34.dp
            val BtnR = 17.dp
            val BtnGap = 10.dp
            val HintTop = 12.dp
        }
        object Nav {
            val H = 64.dp
            val R = 32.dp
            val InsetH = 18.dp
            val Bottom = 22.dp
            val BarW = 280.dp
            val PillW = 68.dp
            val PillH = 52.dp
            val Search = 64.dp
            val Icon = 24.dp
        }
    }
    /** y 偏移 / 模糊半径 / 颜色；用 Modifier.shadow(elevation=blur/2, spotColor=color) 近似 */
    object Shadow {
        val Card = Triple(4.dp, 16.dp, Color(0x143C3250))
        val Segment = Triple(2.dp, 6.dp, Color(0x1F000000))
        val Book = Triple(3.dp, 6.dp, Color(0x33000000))
        val Cover = Triple(6.dp, 14.dp, Color(0x38000000))
        val Board = Triple(8.dp, 14.dp, Color(0x265A4A30))
        val Sheet = Triple(18.dp, 40.dp, Color(0x262A2040))
        val Island = Triple(12.dp, 30.dp, Color(0x40000000))
        val Nav = Triple(8.dp, 24.dp, Color(0x1F2A2040))
    }
    object Brushes {
        val Background = Brush.verticalGradient(0f to NT.Colors.Bg.Top, 0.45f to NT.Colors.Bg.Mid, 1f to NT.Colors.Bg.Bottom)
        val Generating = Brush.horizontalGradient(listOf(NT.Colors.Progress.G0, NT.Colors.Progress.G1, NT.Colors.Progress.G2))
        val OrganizeBar = Brush.horizontalGradient(listOf(NT.Colors.Organize.Bar0, NT.Colors.Organize.Bar1))
    }

    data class BookPalette(val spine: androidx.compose.ui.graphics.Color, val cover: androidx.compose.ui.graphics.Color,
                           val chip: androidx.compose.ui.graphics.Color, val deep: androidx.compose.ui.graphics.Color)
    val Palettes = listOf(
        BookPalette(NT.Colors.Course.Green.Spine, NT.Colors.Course.Green.Cover, NT.Colors.Course.Green.Chip, NT.Colors.Course.Green.Deep),
        BookPalette(NT.Colors.Course.Blue.Spine, NT.Colors.Course.Blue.Cover, NT.Colors.Course.Blue.Chip, NT.Colors.Course.Blue.Deep),
        BookPalette(NT.Colors.Course.Rust.Spine, NT.Colors.Course.Rust.Cover, NT.Colors.Course.Rust.Chip, NT.Colors.Course.Rust.Deep),
    )
    /** 第 i 门课的书色；超过 3 门按色相旋转推导，保持同样的明度/饱和度 */
    fun palette(i: Int): BookPalette {
        if (i < Palettes.size) return Palettes[i]
        val hue = (i * 47f + 200f) % 360f
        fun hsl(s: Float, l: Float) = androidx.compose.ui.graphics.Color.hsl(hue, s, l)
        return BookPalette(hsl(0.30f, 0.43f), hsl(0.33f, 0.38f), hsl(0.37f, 0.36f), hsl(0.50f, 0.20f))
    }
}
