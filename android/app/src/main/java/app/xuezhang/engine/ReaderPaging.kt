package app.xuezhang.engine

/**
 * v0.0.7.8 翻书阅读分页：把一节笔记量好高度的内容块（标题、课件图、段落、公式、互动块、考点…）排进固定高度的书页。
 * 纯函数（只吃量好的像素高度），便于单元测试。
 *
 * - 段落（breaks 非空）按行切：break 是每一行底部相对块顶的 y，跨页时在能放下的最后一行处断开，余下的放到续页。
 * - 不可拆的块（图片、函数图像、小测…）放不下就整块挪到下一页。
 * - 单块比整页还高：缩放到 ≥[minScale] 能放下就缩放，否则这一页内部可滚动（最后手段）。
 * - keepWithNext（如「★考点」标签、小标题）：和下一块的第一行一起放，避免孤零零留在页底。
 * - gapBefore 只在块不是页面第一块时计入。
 */
object ReaderPaging {
    data class Block(
        val height: Float,
        val breaks: List<Float> = emptyList(),
        val gapBefore: Float = 0f,
        val keepWithNext: Boolean = false,
    ) {
        val splittable get() = breaks.isNotEmpty()
    }

    /** 一页上放的一块（或一块的一段 [from,to)）。scale<1 表示缩小放下；scroll 表示这一块在页内滚动。 */
    data class Slice(val block: Int, val from: Float, val to: Float, val scale: Float = 1f, val scroll: Boolean = false) {
        val height get() = (to - from) * scale
    }

    private const val EPS = 0.5f

    /**
     * @param firstH 第一页可用高度（含标题等，由调用方放进 blocks）
     * @param contH 续页可用高度（已扣掉「（续）」页眉）
     * @return 至少一页；每页是若干 Slice
     */
    fun paginate(blocks: List<Block>, firstH: Float, contH: Float, minScale: Float = 0.6f): List<List<Slice>> {
        val pages = mutableListOf<List<Slice>>()
        var cur = mutableListOf<Slice>()
        var used = 0f
        var cap = firstH.coerceAtLeast(1f)
        fun newPage() { pages.add(cur); cur = mutableListOf(); used = 0f; cap = contH.coerceAtLeast(1f) }
        fun gapOf(b: Block) = if (cur.isEmpty()) 0f else b.gapBefore
        fun firstChunk(b: Block) = if (b.splittable) b.breaks.first().coerceAtMost(b.height) else b.height

        for ((i, b) in blocks.withIndex()) {
            if (b.height <= 0f) { continue }
            // 和下一块一起：放得下自己 + 下一块第一行，否则另起一页
            if (b.keepWithNext && cur.isNotEmpty()) {
                val nx = blocks.getOrNull(i + 1)
                val need = gapOf(b) + b.height + (nx?.let { it.gapBefore + firstChunk(it) } ?: 0f)
                if (used + need > cap + EPS && b.height + (nx?.let { it.gapBefore + firstChunk(it) } ?: 0f) <= contH + EPS) newPage()
            }
            val g = gapOf(b)
            if (used + g + b.height <= cap + EPS) { cur.add(Slice(i, 0f, b.height)); used += g + b.height; continue }
            if (b.splittable) {
                val ends = (b.breaks.filter { it > EPS && it < b.height - EPS } + b.height).sorted()
                var from = 0f
                var guard = 0
                while (from < b.height - EPS && guard++ < 10_000) {
                    val gg = if (cur.isEmpty()) 0f else if (from == 0f) b.gapBefore else 0f
                    val avail = cap - used - gg
                    val fit = ends.lastOrNull { it > from + EPS && it - from <= avail + EPS }
                    if (fit == null) {
                        if (cur.isEmpty()) {
                            // 一行比整页还高（极端字号）：硬切
                            val to = (from + avail).coerceAtMost(b.height)
                            cur.add(Slice(i, from, to)); used = cap; from = to; newPage()
                        } else newPage()
                        continue
                    }
                    cur.add(Slice(i, from, fit)); used += gg + (fit - from); from = fit
                    if (from < b.height - EPS) newPage()
                }
                continue
            }
            // 不可拆：挪到下一页
            if (cur.isNotEmpty()) newPage()
            if (b.height <= cap + EPS) { cur.add(Slice(i, 0f, b.height)); used = b.height; continue }
            val s = cap / b.height
            if (s >= minScale) cur.add(Slice(i, 0f, b.height, scale = s)) else cur.add(Slice(i, 0f, b.height, scroll = true))
            used = cap
        }
        if (cur.isNotEmpty() || pages.isEmpty()) pages.add(cur)
        return pages
    }

    /** 重新分页后保持阅读位置：旧页对应的（章，章内第几页）→ 新列表里同一章里最接近的那页。 */
    fun remap(oldChapter: Int, oldPart: Int, oldParts: Int, newFirstPageOfChapter: Int, newParts: Int): Int {
        if (oldParts <= 1 || newParts <= 1) return newFirstPageOfChapter
        val f = oldPart.toFloat() / (oldParts - 1)
        return newFirstPageOfChapter + (f * (newParts - 1) + 0.5f).toInt().coerceIn(0, newParts - 1)
    }
}
