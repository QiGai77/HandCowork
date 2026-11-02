package app.xuezhang

import app.xuezhang.engine.ReaderPaging
import app.xuezhang.engine.ReaderPaging.Block
import app.xuezhang.ui.ReaderTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.0.7.8 翻书分页算法（纯函数，量好的像素高度）。 */
class V0078PagingTest {
    private fun para(lines: Int, lh: Float = 30f, gap: Float = 0f) = Block(lines * lh, (1..lines).map { it * lh }, gap)
    private fun pageHeights(pages: List<List<ReaderPaging.Slice>>) = pages.map { p -> p.sumOf { it.height.toDouble() }.toFloat() }

    @Test fun shortSectionStaysOnOnePage() {
        val pages = ReaderPaging.paginate(listOf(Block(100f), Block(300f), para(5)), 1000f, 960f)
        assertEquals(1, pages.size); assertEquals(3, pages[0].size)
    }

    @Test fun longParagraphSplitsByLineWithoutLoss() {
        // 截图里的情况：标题 + 课件图占掉大半页，后面长段落要续页
        val blocks = listOf(Block(200f), Block(500f), para(40))
        val pages = ReaderPaging.paginate(blocks, 1000f, 960f)
        assertTrue(pages.size >= 2)
        val slices = pages.flatten().filter { it.block == 2 }
        // 首尾相接，覆盖整段，且每处断点都在行底
        assertEquals(0f, slices.first().from, 0.01f)
        assertEquals(1200f, slices.last().to, 0.01f)
        slices.zipWithNext().forEach { (a, b) -> assertEquals(a.to, b.from, 0.01f); assertEquals(0f, a.to % 30f, 0.01f) }
        pageHeights(pages).forEachIndexed { i, h -> assertTrue("page $i h=$h", h <= (if (i == 0) 1000f else 960f) + 0.5f) }
        // 第一页尽量填满：剩 300px 正好放 10 行
        assertEquals(300f, slices.first().to, 0.01f)
    }

    @Test fun unsplittableBlockMovesToNextPage() {
        val pages = ReaderPaging.paginate(listOf(Block(700f), Block(400f, gapBefore = 10f)), 1000f, 960f)
        assertEquals(2, pages.size)
        assertEquals(listOf(1), pages[1].map { it.block })
        assertEquals(1f, pages[1][0].scale, 0f)
    }

    @Test fun tallBlockScalesDownOrScrolls() {
        val scaled = ReaderPaging.paginate(listOf(Block(1200f)), 1000f, 960f)
        assertEquals(1, scaled.size); assertEquals(1000f / 1200f, scaled[0][0].scale, 0.001f)
        val scroll = ReaderPaging.paginate(listOf(Block(3000f)), 1000f, 960f)
        assertTrue(scroll[0][0].scroll)
    }

    @Test fun gapIgnoredAtTopOfPage() {
        val pages = ReaderPaging.paginate(listOf(Block(990f), Block(500f, gapBefore = 40f)), 1000f, 960f)
        assertEquals(2, pages.size)
        assertEquals(500f, pageHeights(pages)[1], 0.01f)
    }

    @Test fun keepWithNextPullsTagToNextPage() {
        // 「★考点」标签放得下但下一段第一行放不下 → 标签一起挪到下一页
        val pages = ReaderPaging.paginate(listOf(Block(940f), Block(30f, gapBefore = 12f, keepWithNext = true), para(3, gap = 4f)), 1000f, 960f)
        assertEquals(listOf(0), pages[0].map { it.block })
        assertEquals(1, pages[1].first().block)
    }

    @Test fun lineTallerThanPageIsHardCut() {
        val pages = ReaderPaging.paginate(listOf(Block(250f, listOf(250f))), 100f, 100f)
        assertEquals(3, pages.size)
        assertEquals(250f, pages.last().last().to, 0.01f)
    }

    @Test fun emptyInputGivesOnePage() {
        assertEquals(1, ReaderPaging.paginate(emptyList(), 1000f, 960f).size)
        assertEquals(1, ReaderPaging.paginate(listOf(Block(0f)), 1000f, 960f).size)
    }

    @Test fun continuationPagesUseSmallerHeight() {
        val pages = ReaderPaging.paginate(listOf(para(100, 10f)), 500f, 400f)
        val hs = pageHeights(pages)
        assertEquals(500f, hs[0], 0.01f); assertEquals(400f, hs[1], 0.01f); assertEquals(1000f, hs.sum(), 0.01f)
    }

    @Test fun remapKeepsRelativePosition() {
        assertEquals(10, ReaderPaging.remap(3, 0, 1, 10, 4))
        assertEquals(13, ReaderPaging.remap(3, 2, 3, 10, 4))
        assertEquals(12, ReaderPaging.remap(3, 1, 3, 10, 4))
    }

    @Test fun longTitleUsesTwoLines() {
        assertTrue(ReaderTitle.isLong("电机学·变压器（一）·空载运行与励磁"))
        assertTrue(!ReaderTitle.isLong("3.1 中断系统"))
    }
}
