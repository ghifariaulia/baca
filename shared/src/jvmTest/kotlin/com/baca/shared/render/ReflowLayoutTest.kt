package com.baca.shared.render

import com.baca.shared.parser.Block
import com.baca.shared.parser.BlockKind
import com.baca.shared.parser.FormattedSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun text(block: String, kind: BlockKind = BlockKind.PARAGRAPH): Block.Text =
    Block.Text(listOf(FormattedSpan(block, false, false)), kind, if (kind == BlockKind.HEADING) 1 else 0)

private fun layout(
    vararg blocks: Block,
    fontSizePx: Int = 20,
    lineHeightRatio: Float = 1.0f,
    paragraphGapPx: Int = 0,
    colWidth: Int = 100,
    pageHeight: Int = 100,
    metrics: TextMetrics = GridMetrics(),
): ReflowLayout = ReflowLayout(
    blocks.toList(), metrics,
    ReflowSettings(
        fontSizePx = fontSizePx,
        lineHeightRatio = lineHeightRatio,
        paragraphGapPx = paragraphGapPx,
        imageHeightPx = 60,
    ),
    colWidth, pageHeight,
)

// Grid: 10 chars per line, line height = fontSize * ratio (20px default).
// pageHeight 100 -> 5 lines per page.

class ReflowLayoutTest {

    @Test
    fun `paginates by height with paragraph gaps`() {
        val p1 = text("aaaa bbbb cccc dddd eeee ffff gggg hhhh") // 39 chars, 4 lines
        val p2 = text("iiii jjjj kkkk llll mmmm nnnn oooo pppp") // 39 chars, 4 lines
        val l = layout(p1, p2, paragraphGapPx = 20, pageHeight = 100)
        // p1: 4 lines x 20 = 80px. p2 needs gap 20 + 80 = 100 more -> new page
        assertEquals(2, l.pageCount())
        assertTrue(l.pages[0].all { it.blockIndex == 0 })
        assertTrue(l.pages[1].all { it.blockIndex == 1 })
    }

    @Test
    fun `pages preserve all text in order`() {
        val para = text("0123456789".repeat(40)) // 400 chars, 40 lines
        val l = layout(para, pageHeight = 100) // 5 lines per page
        assertEquals(8, l.pageCount())
        val joined = l.pages.joinToString("") { p ->
            p.joinToString("") { para.text.substring(it.start, it.endExclusive) }
        }
        assertEquals(para.text, joined)
    }

    @Test
    fun `locator round trip - positionForPage then pageForPosition`() {
        val para = text("word ".repeat(100)) // 500 chars, 50 lines
        val l = layout(para, pageHeight = 100) // 10 pages
        val mid = l.pageCount() / 2
        val pos = l.positionForPage(mid)
        val restore = l.pageForPosition(pos.blockIndex, pos.charOffset, pos.quote)
        assertEquals(AnchorResolution.OFFSETS, restore?.resolution)
        assertEquals(mid, restore?.pageIndex)
    }

    @Test
    fun `locator survives reflow via offsets`() {
        val para = text("word ".repeat(100)) // 500 chars, 50 lines
        val small = layout(para, lineHeightRatio = 1.0f, pageHeight = 100) // 10 pages
        val big = layout(para, lineHeightRatio = 2.0f, pageHeight = 100) // 2 lines/page, 25 pages
        assertTrue(big.pageCount() > small.pageCount())

        val pos = small.positionForPage(3) // line 30.. -> start 300
        val restore = big.pageForPosition(pos.blockIndex, pos.charOffset, pos.quote)
        assertEquals(AnchorResolution.OFFSETS, restore?.resolution)
        val firstLine = big.pages[restore!!.pageIndex].first()
        assertEquals(pos.quote.take(10), para.text.substring(firstLine.start, firstLine.endExclusive).take(10))
    }

    @Test
    fun `damaged offsets fall back to quote`() {
        // non-periodic text: quote must be found at its original offset only
        val para = text(
            (1..60).joinToString(" ") { n -> "sentence number $n walks on." },
        )
        val l = layout(para, pageHeight = 100)
        val pos = l.positionForPage(2)
        val damaged = l.pageForPosition(pos.blockIndex + 500, pos.charOffset + 99999, pos.quote)
        assertEquals(AnchorResolution.QUOTE, damaged?.resolution)
        assertEquals(2, damaged?.pageIndex)
    }

    @Test
    fun `unknown block and unknown quote give no anchor - caller uses progression`() {
        val para = text("hello world this is a longer text for pages")
        val l = layout(para, pageHeight = 100)
        assertNull(l.pageForPosition(500, 0, "not present anywhere"))
    }

    @Test
    fun `image block takes placeholder height and does not lose text`() {
        val p1 = text("0123456789".repeat(30)) // 30 lines
        val img = Block.Image("cover", "")
        val p2 = text("abcdefghij".repeat(30)) // 30 lines
        val l = layout(p1, img, p2, pageHeight = 100)
        val joined = l.pages.joinToString("") { p ->
            p.joinToString("") { line ->
                when {
                    line.isImage -> ""
                    line.blockIndex == 0 -> p1.text.substring(line.start, line.endExclusive)
                    else -> p2.text.substring(line.start, line.endExclusive)
                }
            }
        }
        assertEquals(p1.text + p2.text, joined)
        assertTrue(l.pages.any { page -> page.any { it.isImage } })
    }

    @Test
    fun `image height from decoded aspect reserves real space`() {
        val p1 = text("0123456789".repeat(30)) // 30 lines -> 6 pages
        val img = Block.Image("fig", "img.jpg")
        val p2 = text("abcdefghij".repeat(5)) // 5 lines
        // 40 lines at 20px = 800px each; image 90px fits into a fresh 100px page
        val l = ReflowLayout(
            listOf(p1, img, p2), GridMetrics(),
            ReflowSettings(fontSizePx = 20, lineHeightRatio = 1.0f, imageHeightPx = 60),
            100, 100,
            imageHeight = { 90 },
        )
        val imgPage = l.pages.first { page -> page.any { it.isImage } }
        assertEquals(1, imgPage.size)
    }

    @Test
    fun `image shrinks into leftover space instead of leaving it blank`() {
        val p1 = text("0123456789".repeat(2)) // 2 lines = 40px, 60px left
        val img = Block.Image("fig", "img.jpg")
        val l = ReflowLayout(
            listOf(p1, img), GridMetrics(),
            ReflowSettings(fontSizePx = 20, lineHeightRatio = 1.0f, imageHeightPx = 60),
            100, 100,
            imageHeight = { 80 }, // 60px left >= half of 80
        )
        assertEquals(1, l.pageCount())
        assertEquals(60, l.pages[0].single { it.isImage }.heightPx)
        // anchors on the shrunk image still resolve to its page
        assertEquals(0, l.pageForPosition(1, 0, "")?.pageIndex)
    }

    @Test
    fun `image moves to next page when leftover space is under half its height`() {
        val p1 = text("0123456789".repeat(4)) // 4 lines = 80px, 20px left
        val img = Block.Image("fig", "img.jpg")
        val l = ReflowLayout(
            listOf(p1, img), GridMetrics(),
            ReflowSettings(fontSizePx = 20, lineHeightRatio = 1.0f, imageHeightPx = 60),
            100, 100,
            imageHeight = { 80 },
        )
        assertEquals(2, l.pageCount())
        assertEquals(80, l.pages[1].single { it.isImage }.heightPx)
    }

    @Test
    fun `first line of a paragraph is shortened by the indent, headings are not`() {
        val l = ReflowLayout(
            listOf(text("0123456789abcdefghij", BlockKind.HEADING), text("0123456789abcdefghij")),
            GridMetrics(),
            ReflowSettings(fontSizePx = 20, lineHeightRatio = 1.0f, paragraphIndentPx = 20),
            100, 1000,
        )
        // heading: 10 + 10; paragraph: 8 (two columns of indent) + 10 + 2
        assertEquals(listOf(0 to 10, 10 to 20), l.lines.filter { it.blockIndex == 0 }.map { it.start to it.endExclusive })
        assertEquals(listOf(0 to 8, 8 to 18, 18 to 20), l.lines.filter { it.blockIndex == 1 }.map { it.start to it.endExclusive })
    }

    @Test
    fun `hanging paragraph keeps the first line full and indents the rest, after a line break too`() {
        val hanging = text("0123456789abcdefghij\nklmnopqrst").copy(indent = false, hanging = true)
        val l = ReflowLayout(
            listOf(hanging), GridMetrics(),
            ReflowSettings(fontSizePx = 20, lineHeightRatio = 1.0f, paragraphIndentPx = 20),
            100, 1000,
        )
        // 10 at full width, then 8 columns per line (two columns of indent)
        assertEquals(listOf(0 to 10, 10 to 18, 18 to 21, 21 to 29, 29 to 31), l.lines.map { it.start to it.endExclusive })
    }

    @Test
    fun `a block set in by a left border wraps to the narrower width`() {
        val inset = text("0123456789abcdefghij").copy(indent = false, insetEm = 1f)
        val l = ReflowLayout(listOf(inset), GridMetrics(), ReflowSettings(fontSizePx = 20, lineHeightRatio = 1.0f), 100, 1000)
        // 100 wide less a 20px inset: 8 columns a line
        assertEquals(listOf(0 to 8, 8 to 16, 16 to 20), l.lines.map { it.start to it.endExclusive })
    }

    @Test
    fun `a right float shares the row with the line before when it fits`() {
        val date = text("28th Feb").copy(indent = false)
        val place = text("tokyo").copy(indent = false, floatsRight = true)
        fun layout(width: Int) = ReflowLayout(
            listOf(date, place), GridMetrics(),
            ReflowSettings(fontSizePx = 20, lineHeightRatio = 1.0f, paragraphIndentPx = 10), width, 1000,
        )
        // 80 + 10 apart + 50 fits in 200: one row, no extra height
        val wide = layout(200)
        assertEquals(listOf(false, true), wide.lines.map { it.joinsPrevious })
        assertEquals(1, wide.pageCount())
        // 140 doesn't fit in 120: its own line
        assertEquals(listOf(false, false), layout(120).lines.map { it.joinsPrevious })
    }

    @Test
    fun `empty chapter produces one empty page`() {
        val l = layout()
        assertEquals(1, l.pageCount())
        assertTrue(l.pages[0].isEmpty())
        assertEquals(ReflowLayout.BlockPosition(0, 0, ""), l.positionForPage(0))
        assertNull(l.pageForPosition(0, 0, "anything"))
    }

    @Test
    fun `heading occupies first line of first page`() {
        val heading = text("Introduction", BlockKind.HEADING)
        val para = text("word ".repeat(50))
        val l = layout(heading, para, pageHeight = 100, paragraphGapPx = 20)
        assertEquals(0, l.pages[0][0].blockIndex)
        assertEquals(0, l.pages[0][0].start)
    }

    @Test
    fun `page count scales with page height`() {
        val para = text("word ".repeat(100))
        val tall = layout(para, pageHeight = 200)
        val short = layout(para, pageHeight = 100)
        assertTrue(tall.pageCount() < short.pageCount())
    }
}
