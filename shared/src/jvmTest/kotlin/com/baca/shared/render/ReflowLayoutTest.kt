package com.baca.shared.render

import com.baca.shared.parser.Block
import com.baca.shared.parser.BlockKind
import com.baca.shared.parser.Spacing
import com.baca.shared.parser.DropCap
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
    colWidth: Int = 100,
    pageHeight: Int = 100,
    metrics: TextMetrics = GridMetrics(),
): ReflowLayout = ReflowLayout(
    blocks.toList(), metrics,
    ReflowSettings(
        fontSizePx = fontSizePx,
        lineHeightRatio = lineHeightRatio,
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
        val p2 = text("iiii jjjj kkkk llll mmmm nnnn oooo pppp").copy(gapBefore = Spacing(1f)) // 39 chars, 4 lines
        val l = layout(p1, p2, pageHeight = 100)
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
    fun `a picture that doesn't fit moves whole to the next page`() {
        val p1 = text("0123456789".repeat(2)) // 2 lines = 40px, 60px left
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
    fun `first line of a paragraph is shortened by its text-indent`() {
        val l = ReflowLayout(
            listOf(text("0123456789abcdefghij"), text("0123456789abcdefghij").copy(indent = Spacing(1f))),
            GridMetrics(),
            ReflowSettings(fontSizePx = 20, lineHeightRatio = 1.0f),
            100, 1000,
        )
        // no indent: 10 + 10; 1em indent: 8 (two columns of indent) + 10 + 2
        assertEquals(listOf(0 to 10, 10 to 20), l.lines.filter { it.blockIndex == 0 }.map { it.start to it.endExclusive })
        assertEquals(listOf(0 to 8, 8 to 18, 18 to 20), l.lines.filter { it.blockIndex == 1 }.map { it.start to it.endExclusive })
    }

    @Test
    fun `hanging paragraph keeps the first line full and indents the rest, after a line break too`() {
        val hanging = text("0123456789abcdefghij\nklmnopqrst").copy(indent = Spacing(-1f), inset = Spacing(1f))
        val l = ReflowLayout(
            listOf(hanging), GridMetrics(),
            ReflowSettings(fontSizePx = 20, lineHeightRatio = 1.0f),
            100, 1000,
        )
        // 10 at full width, then 8 columns per line (two columns of indent)
        assertEquals(listOf(0 to 10, 10 to 18, 18 to 21, 21 to 29, 29 to 31), l.lines.map { it.start to it.endExclusive })
    }

    @Test
    fun `a block set in by a left border wraps to the narrower width`() {
        val inset = text("0123456789abcdefghij").copy(inset = Spacing(1f))
        val l = ReflowLayout(listOf(inset), GridMetrics(), ReflowSettings(fontSizePx = 20, lineHeightRatio = 1.0f), 100, 1000)
        // 100 wide less a 20px inset: 8 columns a line
        assertEquals(listOf(0 to 8, 8 to 16, 16 to 20), l.lines.map { it.start to it.endExclusive })
    }

    @Test
    fun `a right float shares the row with the line before when it fits`() {
        val date = text("28th Feb")
        val place = text("tokyo").copy(floatsRight = true)
        fun layout(width: Int) = ReflowLayout(
            listOf(date, place), GridMetrics(),
            ReflowSettings(fontSizePx = 20, lineHeightRatio = 1.0f), width, 1000,
        )
        // 80 + 20 (1em) apart + 50 fits in 200: one row, no extra height
        val wide = layout(200)
        assertEquals(listOf(false, true), wide.lines.map { it.joinsPrevious })
        assertEquals(1, wide.pageCount())
        // 150 doesn't fit in 120: its own line
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
        val l = layout(heading, para, pageHeight = 100)
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

    @Test
    fun `the room under a page's last whole line is split above and below`() {
        // 20px type at 1.5 is a 30px line: 7 lines fill 210 of 230px, 10px above and below
        val settings = ReflowSettings(fontSizePx = 20, lineHeightRatio = 1.5f)
        assertEquals(10, settings.evenLinesPx(230))
        assertEquals(0, settings.evenLinesPx(240))
        assertEquals(0, settings.evenLinesPx(20))
    }

    @Test
    fun `a negative indent never pulls the first line out past the column`() {
        val box = text("x").copy(indent = Spacing(-2f), inset = Spacing(1f)).lineBox(20, 1f, 100)
        assertEquals(LineBox(startPx = 0, endPx = 0, firstLinePx = 0, restLinePx = 40), box)
    }

    @Test
    fun `headings are set at a browser's normal line height, not the reader's`() {
        val l = ReflowLayout(
            listOf(text("Title", BlockKind.HEADING), text("Body")),
            GridMetrics(), ReflowSettings(fontSizePx = 20, lineHeightRatio = 2f), 100, 70,
        )
        // a 24px heading line and a 40px body line fit 70px; at the reader's 40px the heading wouldn't
        assertEquals(1, l.pageCount())
    }

    @Test
    fun `lines beside a drop cap are shortened by its width`() {
        // a 3em "D" (one 10px grid column at any size) one line-height tall stands beside 3 lines
        val capped = Block.Text(
            listOf(FormattedSpan("D", false, false, scale = 3f), FormattedSpan("abcdefghijklmnopqrstuvwxyz0123456789", false, false)),
            BlockKind.PARAGRAPH, 0, dropCap = DropCap(1, 1f),
        )
        val l = ReflowLayout(listOf(capped), GridMetrics(), ReflowSettings(fontSizePx = 20, lineHeightRatio = 1f), 100, 1000)
        // 9 columns beside the cap for 3 lines, then 10
        assertEquals(listOf(0 to 10, 10 to 19, 19 to 28, 28 to 37), l.lines.map { it.start to it.endExclusive })
        assertEquals(listOf(10, 10, 10, 0), l.lines.map { it.floatIndentPx })
    }

    @Test
    fun `the line after a drop cap's lines doesn't start with the break's space`() {
        val capped = Block.Text(
            listOf(FormattedSpan("D", false, false, scale = 3f), FormattedSpan("abcdefgh abcdefgh abcdefgh abcd", false, false)),
            BlockKind.PARAGRAPH, 0, dropCap = DropCap(1, 0.5f),
        )
        val l = ReflowLayout(listOf(capped), GridMetrics(), ReflowSettings(fontSizePx = 20, lineHeightRatio = 1f), 100, 1000)
        assertTrue(l.lines.drop(1).none { capped.text[it.start] == ' ' }, l.lines.map { capped.text.substring(it.start, it.endExclusive) }.toString())
    }

    @Test
    fun `css px spacing follows the screen's density, not the font size`() {
        val block = text("x").copy(inset = Spacing(em = 1f, px = 10f))
        assertEquals(20 + 30, block.lineBox(bodyFontPx = 20, cssPx = 3f, columnPx = 400).startPx)
        assertEquals(40 + 30, block.lineBox(bodyFontPx = 40, cssPx = 3f, columnPx = 400).startPx)
    }
}
