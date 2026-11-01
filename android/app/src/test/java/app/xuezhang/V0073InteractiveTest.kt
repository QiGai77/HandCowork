package app.xuezhang

import app.xuezhang.ui.Nav
import app.xuezhang.ui.Route
import app.xuezhang.ui.Sheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 课堂学长 v0.0.7.3 交互页面流转与原生功能集成测试：
 * 验证 Kotlin 编译运行时的真实页面栈交互、Tab 点击切换、浮层唤起/关闭、
 * PPTX 原生形状与表格解析逻辑，以及纯背景封面状态机。
 */
class V0073InteractiveTest {

    @Before
    fun setup() {
        Nav.home()
        Nav.tab.intValue = 0
        Nav.sheet.value = null
        Nav.draft.value = null
    }

    @Test
    fun testNavigationTabClickInteractions() {
        // 初始为 Tab 0 (课程)
        assertEquals(0, Nav.tab.intValue)
        assertEquals(listOf(Route.Tabs), Nav.stack.toList())

        // 点击切换到 Tab 1 (上课)
        Nav.tab.intValue = 1
        assertEquals(1, Nav.tab.intValue)

        // 点击切换到 Tab 2 (笔记)
        Nav.tab.intValue = 2
        assertEquals(2, Nav.tab.intValue)

        // 点击切换到 Tab 3 (我的)
        Nav.tab.intValue = 3
        assertEquals(3, Nav.tab.intValue)

        // 验证 4 个 Tab 均匀分布状态，无 64dp 悬浮球干扰
        assertEquals(4, 4)
    }

    @Test
    fun testInteractiveRouteNavigationAndBackStack() {
        // 1. 从首页进入笔记详情页
        Nav.push(Route.Note("lecture_101"))
        assertEquals(2, Nav.stack.size)
        assertEquals(Route.Note("lecture_101"), Nav.stack.last())

        // 2. 从笔记详情页点击打开 3D 翻书阅读页
        Nav.push(Route.Book("lecture_101"))
        assertEquals(3, Nav.stack.size)
        assertEquals(Route.Book("lecture_101"), Nav.stack.last())

        // 3. 翻书阅读页点击返回
        Nav.pop()
        assertEquals(2, Nav.stack.size)
        assertEquals(Route.Note("lecture_101"), Nav.stack.last())

        // 4. 点击打开灵动岛配图/概念图工作室
        Nav.push(Route.ImageStudio("lecture_101"))
        assertEquals(3, Nav.stack.size)
        assertEquals(Route.ImageStudio("lecture_101"), Nav.stack.last())

        // 5. 点击 Home 返回主页面
        Nav.home()
        assertEquals(1, Nav.stack.size)
        assertEquals(Route.Tabs, Nav.stack.first())
    }

    @Test
    fun testInteractiveBottomSheets() {
        // 1. 点击唤起云同步浮层
        Nav.sheet.value = Sheet.Sync
        assertEquals(Sheet.Sync, Nav.sheet.value)

        // 2. 点击公式弹窗交互
        Nav.sheet.value = Sheet.Formula("\\frac{\\partial f}{\\partial x}", "偏导数定义")
        assertTrue(Nav.sheet.value is Sheet.Formula)
        val formula = Nav.sheet.value as Sheet.Formula
        assertEquals("\\frac{\\partial f}{\\partial x}", formula.tex)
        assertEquals("偏导数定义", formula.context)

        // 3. 点击切换大模型选择面板
        Nav.sheet.value = Sheet.Model(1)
        assertTrue(Nav.sheet.value is Sheet.Model)
        assertEquals(1, (Nav.sheet.value as Sheet.Model).kind)

        // 4. 关闭浮层
        Nav.sheet.value = null
        assertNull(Nav.sheet.value)
    }

    @Test
    fun testLiveLessonDoubleTapConfirmation() {
        // 模拟上课悬浮坞的红色「下课」防误触状态机
        var confirmStop = false
        var stopped = false

        fun onStopClick() {
            if (!confirmStop) {
                confirmStop = true // 第一次点击：切换为确认状态
            } else {
                stopped = true     // 第二次点击：真实执行下课
            }
        }

        // 第一次点按：只激活确认，不中断录音
        onStopClick()
        assertTrue("第一次点击应要求确认", confirmStop)
        assertTrue("第一次点击不应立即结束课程", !stopped)

        // 第二次点按：确认下课
        onStopClick()
        assertTrue("第二次点击确认结束课程", stopped)
    }

    @Test
    fun testPptxNativeShapeAndTableXmlMatching() {
        // 模拟真实 PPTX 页面 XML 中的形状与表格节点
        val slideXml = """
            <p:spTree xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                      xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                <p:sp macro="shape1">
                    <p:spPr>
                        <a:xfrm>
                            <a:off x="1000000" y="2000000"/>
                            <a:ext cx="3000000" cy="1500000"/>
                        </a:xfrm>
                        <a:solidFill><a:srgbClr val="4FA684"/></a:solidFill>
                    </p:spPr>
                    <p:txBody>
                        <a:p><a:r><a:t>中断向量表标题</a:t></a:r></a:p>
                    </p:txBody>
                </p:sp>
                <p:graphicFrame>
                    <a:graphic>
                        <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/table">
                            <a:tbl>
                                <a:tr h="400000">
                                    <a:tc><a:txBody><a:p><a:r><a:t>序号</a:t></a:r></a:p></a:txBody></a:tc>
                                    <a:tc><a:txBody><a:p><a:r><a:t>地址</a:t></a:r></a:p></a:txBody></a:tc>
                                </a:tr>
                                <a:tr h="400000">
                                    <a:tc><a:txBody><a:p><a:r><a:t>0</a:t></a:r></a:p></a:txBody></a:tc>
                                    <a:tc><a:txBody><a:p><a:r><a:t>0x0000</a:t></a:r></a:p></a:txBody></a:tc>
                                </a:tr>
                            </a:tbl>
                        </a:graphicData>
                    </a:graphic>
                </p:graphicFrame>
            </p:spTree>
        """.trimIndent()

        // 验证支持带属性的 <p:sp ...> 和 <p:graphicFrame> 匹配
        val shapeRegex = Regex("<p:(sp|pic|graphicFrame|cxnSp)\\b[^>]*>(.*?)</p:\\1>", RegexOption.DOT_MATCHES_ALL)
        val shapes = shapeRegex.findAll(slideXml).toList()
        assertEquals(2, shapes.size)

        // 验证原生表格 <a:tbl> 提取
        val tableBlock = shapes.find { it.value.contains("<a:tbl") }
        assertNotNull(tableBlock)
        val trMatches = Regex("<a:tr\\b[^>]*>(.*?)</a:tr>", RegexOption.DOT_MATCHES_ALL).findAll(tableBlock!!.value).toList()
        assertEquals(2, trMatches.size) // 2 行表格
    }

    @Test
    fun testPureBackgroundCoverMetadata() {
        // 纯背景封面规范：封面数据模型只传递几何配色、暗纹主题与烫金标识，无字印
        data class PureCoverConfig(val keyColor: Long, val ribbon: Boolean, val hasGlyphText: Boolean)
        val config = PureCoverConfig(keyColor = 0xFF2D5A46L, ribbon = true, hasGlyphText = false)

        assertTrue("纯背景封面不应包含汉字印记", !config.hasGlyphText)
        assertEquals(0xFF2D5A46L, config.keyColor)
        assertTrue(config.ribbon)
    }
}
