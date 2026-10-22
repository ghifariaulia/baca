package com.baca.shared.render

import com.baca.shared.parser.Block
import com.baca.shared.parser.BlockBox
import com.baca.shared.parser.BlockKind
import com.baca.shared.parser.Columns
import com.baca.shared.parser.Spacing
import com.baca.shared.parser.DropCap
import com.baca.shared.parser.INLINE_IMAGE
import com.baca.shared.parser.InlineSpacer
import com.baca.shared.parser.TableCell
import com.baca.shared.parser.FormattedSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun text(block: String, kind: BlockKind = BlockKind.PARAGRAPH): Block.Text =
    Block.Text(listOf(FormattedSpan(block, false, false)), kind, if (kind == BlockKind.HEADING) 1 else 0)

private fun layout(
    vararg blocks: Block,
    fontSizePx: Float = 20f,
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
            ReflowSettings(fontSizePx = 20f, lineHeightRatio = 1.0f, imageHeightPx = 60),
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
            ReflowSettings(fontSizePx = 20f, lineHeightRatio = 1.0f, imageHeightPx = 60),
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
            ReflowSettings(fontSizePx = 20f, lineHeightRatio = 1.0f),
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
            ReflowSettings(fontSizePx = 20f, lineHeightRatio = 1.0f),
            100, 1000,
        )
        // 10 at full width, then 8 columns per line (two columns of indent)
        assertEquals(listOf(0 to 10, 10 to 18, 18 to 21, 21 to 29, 29 to 31), l.lines.map { it.start to it.endExclusive })
    }

    @Test
    fun `a block set in by a left border wraps to the narrower width`() {
        val inset = text("0123456789abcdefghij").copy(inset = Spacing(1f))
        val l = ReflowLayout(listOf(inset), GridMetrics(), ReflowSettings(fontSizePx = 20f, lineHeightRatio = 1.0f), 100, 1000)
        // 100 wide less a 20px inset: 8 columns a line
        assertEquals(listOf(0 to 8, 8 to 16, 16 to 20), l.lines.map { it.start to it.endExclusive })
    }

    @Test
    fun `a right float shares the row with the line before when it fits`() {
        val date = text("28th Feb")
        val place = text("tokyo").copy(floatsRight = true)
        fun layout(width: Int) = ReflowLayout(
            listOf(date, place), GridMetrics(),
            ReflowSettings(fontSizePx = 20f, lineHeightRatio = 1.0f), width, 1000,
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
    fun `each chapter's lines are its first paragraph's`() {
        val settings = ReflowSettings(fontSizePx = 20f, lineHeightRatio = 1f)
        // a chapter whose text is set at 0.75: 15px lines in 230px leave 5, 1 above (3px to spare)
        val small = listOf(text("Title", BlockKind.HEADING).copy(scale = 2f), text("").copy(scale = 0.5f), text("text").copy(scale = 0.75f))
        assertEquals(1f, settings.chapterEvenPx(small, 230f))
        // a chapter without any paragraph takes the running text's
        assertEquals(settings.evenLinesPx(230f), settings.chapterEvenPx(listOf(text("Title", BlockKind.HEADING)), 230f))
    }

    @Test
    fun `the room under a page's last whole line is split above and below`() {
        // 20px type at 1.5 is a 30px line: 7 lines and 3px to spare fill 213 of 230px, 8px above and below
        val settings = ReflowSettings(fontSizePx = 20f, lineHeightRatio = 1.5f)
        assertEquals(8f, settings.evenLinesPx(230f))
        // 8 lines need 240px and the 3px: at 240 only 7 fit
        assertEquals(13f, settings.evenLinesPx(240f))
        assertEquals(0f, settings.evenLinesPx(243f))
        assertEquals(0f, settings.evenLinesPx(20f))
    }

    @Test
    fun `the room around the lines is reckoned in css px, as the page lays it out`() {
        // Strange Buildings at 125% / 1.5 on a 2.625 screen: 780 css px for 30px lines leave 13.5 above and
        // below, which the page floors to 13 css px; in device px that's 34.125, not floor(35.4) = 35
        val settings = ReflowSettings(fontSizePx = 52.5f, lineHeightRatio = 1.5f, cssPx = 2.625f)
        assertEquals(34.125f, settings.evenLinesPx(780 * 2.625f), 0.001f)
    }

    @Test
    fun `lines stack at their exact height and are drawn at whole pixels without drift`() {
        // 21px type at 1.5: 31.5px lines, two of which fill a 63px page exactly
        val l = layout(text("a".repeat(30)), fontSizePx = 21f, lineHeightRatio = 1.5f, pageHeight = 63)
        assertEquals(listOf(2, 1), l.pages.map { it.size })
        // rows from 0 to 31.5 and 31.5 to 63, at whole pixels 0–32 and 32–63
        assertEquals(listOf(32, 31), l.rowHeightsOf(0).toList())
        assertEquals(listOf(0, 0), l.gapsOf(0).toList())
    }

    @Test
    fun `a negative indent never pulls the first line out past the column`() {
        val box = text("x").copy(indent = Spacing(-2f), inset = Spacing(1f)).lineBox(20f, 1f, 100)
        assertEquals(LineBox(startPx = 0, endPx = 0, firstLinePx = 0, restLinePx = 40), box)
    }

    @Test
    fun `a heading takes the reader's line height unless the book sets its own`() {
        fun pages(heading: Block.Text) = ReflowLayout(
            listOf(heading, text("Body")), GridMetrics(), ReflowSettings(fontSizePx = 20f, lineHeightRatio = 2f), 100, 70,
        ).pageCount()
        // 40px + 40px don't fit 70px; at the book's 1.2 the heading is 24px and they do
        assertEquals(2, pages(text("Title", BlockKind.HEADING)))
        assertEquals(1, pages(text("Title", BlockKind.HEADING).copy(lineRatio = 1.2f)))
    }

    @Test
    fun `lines beside a drop cap are shortened by its width`() {
        // a 3em "D" (one 10px grid column at any size) one line-height tall stands beside 3 lines
        val capped = Block.Text(
            listOf(FormattedSpan("D", false, false, scale = 3f), FormattedSpan("abcdefghijklmnopqrstuvwxyz0123456789", false, false)),
            BlockKind.PARAGRAPH, 0, dropCap = DropCap(1, 1f),
        )
        val l = ReflowLayout(listOf(capped), GridMetrics(), ReflowSettings(fontSizePx = 20f, lineHeightRatio = 1f), 100, 1000)
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
        val l = ReflowLayout(listOf(capped), GridMetrics(), ReflowSettings(fontSizePx = 20f, lineHeightRatio = 1f), 100, 1000)
        assertTrue(l.lines.drop(1).none { capped.text[it.start] == ' ' }, l.lines.map { capped.text.substring(it.start, it.endExclusive) }.toString())
    }

    @Test
    fun `css px spacing follows the screen's density, not the font size`() {
        val block = text("x").copy(inset = Spacing(em = 1f, px = 10f))
        assertEquals(20 + 30, block.lineBox(bodyFontPx = 20f, cssPx = 3f, columnPx = 400).startPx)
        assertEquals(40 + 30, block.lineBox(bodyFontPx = 40f, cssPx = 3f, columnPx = 400).startPx)
    }

    private fun cell(text: String, row: Int, column: Int, widths: List<Float?>) =
        text(text).copy(cell = TableCell(table = 1, row = row, column = column, span = 1, widths = widths))

    @Test
    fun `a table row's cells sit side by side, each in its column`() {
        // 15% + 85% of 200px: 30px and 170px, but "KURIHARA:" (90px) widens the first to fit it
        val widths = listOf(0.15f, 0.85f)
        val blocks = listOf(
            cell("KURIHARA:", 1, 0, widths), cell("abcdefghijklmnop qrstuvw", 1, 1, widths),
            cell("AUTHOR:", 2, 0, widths), cell("x", 2, 1, widths),
        )
        val l = ReflowLayout(blocks, GridMetrics(), ReflowSettings(fontSizePx = 20f, lineHeightRatio = 1f), 200, 1000)
        assertEquals(LineBox(startPx = 0, endPx = 110, firstLinePx = 0, restLinePx = 0), l.lineBoxOf(0))
        assertEquals(90, l.lineBoxOf(1).startPx)
        // the second cell's first line joins the label's row; its second line is a row of its own
        assertEquals(listOf(0 to false, 1 to true, 1 to false, 2 to false, 3 to true), l.lines.map { it.blockIndex to it.joinsPrevious })
        assertEquals(3, l.pages.single().count { !it.joinsPrevious })
    }

    @Test
    fun `a table's border spacing goes before each column and after the last`() {
        // 10px of spacing in a 200px column: two 50% columns share 170px, 85 each
        val spaced = TableCell(table = 1, row = 1, column = 0, span = 1, widths = listOf(0.5f, 0.5f), spacing = Spacing(px = 10f))
        val blocks = listOf(text("a").copy(cell = spaced), text("b").copy(cell = spaced.copy(column = 1)))
        val l = ReflowLayout(blocks, GridMetrics(), ReflowSettings(fontSizePx = 20f, lineHeightRatio = 1f), 200, 1000)
        assertEquals(10 to 105, l.lineBoxOf(0).let { it.startPx to it.endPx })
        assertEquals(105 to 10, l.lineBoxOf(1).let { it.startPx to it.endPx })
    }

    @Test
    fun `a picture set inline stands on its line's baseline, the line's descent below it`() {
        // grid fonts at 20px and 1.5: the line reaches 21px above its baseline and 9 below
        fun picture(tall: Int, inline: Boolean) = ReflowLayout(
            listOf(Block.Image("", "p.png", strut = if (inline) FormattedSpan("", false, false) else null)), GridMetrics(),
            ReflowSettings(fontSizePx = 20f, lineHeightRatio = 1.5f), 100, 1000, imageHeight = { tall },
        ).rowHeightsOf(0).single()
        assertEquals(109, picture(100, inline = true))
        // a picture shorter than the line's reach above is in a line as tall as any
        assertEquals(30, picture(10, inline = true))
        // one set as a block is its own height
        assertEquals(100, picture(100, inline = false))
    }

    @Test
    fun `a cell's box is drawn at its column's edges, as tall as its row`() {
        // two 50px columns; each cell's box has 2px of border, its text 2px in from it
        val cell = TableCell(table = 1, row = 1, column = 0, span = 1, widths = listOf(0.5f, 0.5f))
        fun boxed(text: String, id: Int, column: Int) = text(text).copy(
            cell = cell.copy(column = column, ownStart = Spacing(px = 2f), ownEnd = Spacing(px = 2f)),
            inset = Spacing(px = 2f), insetEnd = Spacing(px = 2f),
            boxes = listOf(BlockBox(id = id, border = listOf(2f, 2f, 2f, 2f), cell = true)),
        )
        // "a" one line, "bbbb cccc" two: the short cell's box runs the whole row
        val l = ReflowLayout(listOf(boxed("a", 1, 0), boxed("bbbb cccc", 2, 1)), GridMetrics(), ReflowSettings(fontSizePx = 20f, lineHeightRatio = 1f), 100, 1000)
        val boxes = l.boxesOf(0).associate { it.box.id to listOf(it.leftPx, it.rightPx, it.topPx, it.bottomPx) }
        assertEquals(boxes.getValue(2)[3], boxes.getValue(1)[3])
        assertEquals(listOf(0, 50), boxes.getValue(1).take(2))
        assertEquals(listOf(50, 100), boxes.getValue(2).take(2))
    }

    @Test
    fun `a cell shorter than its row is drawn in the middle of it`() {
        // a one-line cell beside a three-line one, 20px lines: 40px spare, 20 above the short cell
        val middle = TableCell(table = 1, row = 1, column = 0, span = 1, widths = listOf(0.5f, 0.5f), spareAbove = 0.5f)
        val blocks = listOf(text("a").copy(cell = middle), text("bbbb cccc dddd").copy(cell = middle.copy(column = 1)))
        val l = ReflowLayout(blocks, GridMetrics(), ReflowSettings(fontSizePx = 20f, lineHeightRatio = 1f), 100, 1000)
        assertEquals(listOf(20f, 0f, 0f, 0f), l.lines.map { it.dropPx })
    }

    @Test
    fun `a row whose cells have no lines yet lays out as nothing`() {
        // before the page is measured the metrics give no lines
        val none = object : TextMetrics by GridMetrics() {
            override fun layoutLines(text: String, fontSizePx: Float, lineHeightPx: Int, maxWidthPx: Int, fontFamily: String?, styles: List<TextMetrics.StyleRange>, firstLineIndentPx: Int, hyphenate: Boolean, language: String?, rtl: Boolean) =
                emptyList<TextMetrics.LineSpan>()
        }
        val cell = TableCell(table = 1, row = 1, column = 0, span = 1, widths = emptyList())
        val l = ReflowLayout(listOf(text("a").copy(cell = cell), text("b").copy(cell = cell.copy(column = 1))), none, ReflowSettings(fontSizePx = 20f, lineHeightRatio = 1f), 100, 1000)
        assertEquals(emptyList(), l.lines)
    }

    @Test
    fun `columns without widths share the room as a browser's auto table layout does`() {
        // a label column whose text is one word, and one of running text (Strange Buildings' notebox tables):
        // each gets its widest word, and the room left goes by how much wider than that its text runs
        val blocks = listOf(
            cell("PROPERTY:", 1, 0, emptyList()), cell("aaaa bbbb cccc dddd eeee ffff", 1, 1, emptyList()),
        )
        val l = ReflowLayout(blocks, GridMetrics(), ReflowSettings(fontSizePx = 20f, lineHeightRatio = 1f), 200, 1000)
        // "PROPERTY:" is 90px and can't grow; the text takes the other 110
        assertEquals(90, l.lineBoxOf(1).startPx)
        assertEquals(110, l.lineBoxOf(0).endPx)
    }

    @Test
    fun `a table inside a padded box shares out the room inside the padding`() {
        // the box's padding sets every cell in by 20px from each side: the table has 160px
        val inset = Spacing(px = 20f)
        val blocks = listOf(
            cell("PROPERTY:", 1, 0, emptyList()).copy(inset = inset, insetEnd = inset),
            cell("aaaa bbbb cccc dddd eeee ffff", 1, 1, emptyList()).copy(inset = inset, insetEnd = inset),
        )
        val l = ReflowLayout(blocks, GridMetrics(), ReflowSettings(fontSizePx = 20f, lineHeightRatio = 1f, cssPx = 1f), 200, 1000)
        assertEquals(20 to 90, l.lineBoxOf(0).let { it.startPx to 200 - it.endPx - it.startPx })
        assertEquals(110 to 20, l.lineBoxOf(1).let { it.startPx to it.endPx })
    }

    @Test
    fun `a block in two columns fills the first, then the second, as many lines in each`() {
        // 200px less a 20px gap: two columns of 90px, 9 letters a line, so 50 letters make 6 lines, 3 a column
        val blocks = listOf(text("a".repeat(50)).copy(columns = Columns(2, Spacing(px = 20f))))
        val l = ReflowLayout(blocks, GridMetrics(), ReflowSettings(fontSizePx = 20f, lineHeightRatio = 1f, cssPx = 1f), 200, 1000)
        assertEquals(listOf(0 to 0, 27 to 1, 9 to 0, 36 to 1, 18 to 0, 45 to 1), l.lines.map { it.start to it.column })
        assertEquals(listOf(false, true, false, true, false, true), l.lines.map { it.joinsPrevious })
        assertEquals(0 to 110, l.lineBoxOf(0, column = 0).let { it.startPx to it.endPx })
        assertEquals(110 to 0, l.lineBoxOf(0, column = 1).let { it.startPx to it.endPx })
    }

    @Test
    fun `a spacer is its em at the span's size, a min-width pad what the label leaves`() {
        val block = Block.Text(
            listOf(
                FormattedSpan("(1)", false, false, inlineBox = 1),
                FormattedSpan(INLINE_IMAGE.toString(), false, false, spacer = InlineSpacer(2f, padsBox = 1)),
                FormattedSpan(INLINE_IMAGE.toString(), false, false, spacer = InlineSpacer(0.5f)),
                FormattedSpan("text", false, false),
            ),
            BlockKind.PARAGRAPH, 0,
        )
        val l = ReflowLayout(listOf(block), GridMetrics(), ReflowSettings(fontSizePx = 20f, lineHeightRatio = 1f), 200, 1000)
        // 2em = 40px less "(1)" at 30px; 0.5em = 10px
        assertEquals(listOf(null, 10, 10, null), l.spacersOf(block))
    }

    @Test
    fun `a margin's share of the page is measured on the page`() {
        // 30% of a 1000px page between the lines (at a page's top a margin is cut)
        val l = ReflowLayout(listOf(text("x"), text("a").copy(gapBefore = Spacing(pageH = 0.3f))), GridMetrics(), ReflowSettings(fontSizePx = 20f, lineHeightRatio = 1f), 100, 1000)
        assertEquals(300, l.gapsOf(0)[1])
    }

    @Test
    fun `line-height normal is the font's own height`() {
        // grid fonts: ascent 0.8 and descent 0.2, so normal is 1 of the size; the reader's 1.5 elsewhere
        val normal = text("a").copy(lineRatio = com.baca.shared.parser.NORMAL_LINE)
        val l = ReflowLayout(listOf(normal, text("b")), GridMetrics(), ReflowSettings(fontSizePx = 20f, lineHeightRatio = 1.5f), 100, 1000)
        assertEquals(listOf(1f, 1.5f), listOf(normal, text("b")).map { l.lineRatioOf(it) })
        assertEquals(listOf(20, 30), l.rowHeightsOf(0).toList())
    }

    @Test
    fun `a rule is a browser's two 1px borders, each whole device px`() {
        val rule = Block.Rule()
        // 2.625 device px to a CSS px: each border 2 device px, as Chromium snaps it
        assertEquals(4, rule.heightPx(fontSizePx = 52.5f, cssPx = 2.625f))
        assertEquals(2, rule.heightPx(fontSizePx = 16f, cssPx = 1f))
        assertEquals(0, rule.copy(line = false).heightPx(fontSizePx = 16f, cssPx = 1f))
    }

    @Test
    fun `a right-to-left block's first line is indented on the right`() {
        val para = text("abc").copy(indent = Spacing(px = 20f), inset = Spacing(px = 5f), insetEnd = Spacing(px = 7f), rtl = true)
        val box = para.lineBox(bodyFontPx = 16f, cssPx = 1f, columnPx = 400)
        // the left is the block's own inset; the right is its inset and, on the first line, the indent
        assertEquals(5, box.startPx)
        assertEquals(27, box.rightPx(firstLine = true))
        assertEquals(7, box.rightPx(firstLine = false))
        // left to right, the same block puts it on the left
        val ltr = para.copy(rtl = false).lineBox(bodyFontPx = 16f, cssPx = 1f, columnPx = 400)
        assertEquals(25, ltr.linePx(firstLine = true))
    }

    @Test
    fun `a forced break starts a new page whatever room is left`() {
        val l = layout(text("One"), text("Two").copy(breakBefore = true), text("Three"), pageHeight = 1000)
        assertEquals(listOf(listOf(0), listOf(1, 2)), l.pages.map { page -> page.map { it.blockIndex }.distinct() })
    }

    @Test
    fun `a break the book avoids goes back to the page's last one it doesn't`() {
        fun pages(l: ReflowLayout) = l.pages.map { page -> page.map { it.blockIndex }.distinct() }
        // 20px lines, 60px pages: three one-line blocks fill a page
        // a heading kept with what follows it moves to the next page with it (probed: Chromium's columns do this)
        val heading = layout(text("a"), text("b"), text("H"), text("c").copy(keepWithPrevious = true), lineHeightRatio = 1f, pageHeight = 60)
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), pages(heading))
        // two headings in a row, each kept with the next, move together
        val two = layout(text("a"), text("H1"), text("H2").copy(keepWithPrevious = true), text("c").copy(keepWithPrevious = true), lineHeightRatio = 1f, pageHeight = 60)
        assertEquals(listOf(listOf(0), listOf(1, 2, 3)), pages(two))
        // a block kept whole moves to the next page whole
        val whole = layout(text("a"), text("b"), text("lines that wrap").copy(keepLines = true), lineHeightRatio = 1f, pageHeight = 60, colWidth = 50)
        assertEquals(listOf(listOf(0, 1), listOf(2)), pages(whole))
        // one taller than a page still starts a fresh page, then breaks where it must
        val tall = layout(text("a"), text("one two three four five").copy(keepLines = true), lineHeightRatio = 1f, pageHeight = 60, colWidth = 50)
        assertEquals(listOf(listOf(0), listOf(1), listOf(1)), pages(tall))
        // with no earlier break on the page, the avoided one is taken
        val only = layout(text("H"), text("c").copy(keepWithPrevious = true), lineHeightRatio = 1f, pageHeight = 20)
        assertEquals(listOf(listOf(0), listOf(1)), pages(only))
    }

    @Test
    fun `at a page top margins go and padding stays, after a forced break both stay`() {
        // each block: 30px of gap, the first 20 of it margin before its 10px of padding
        fun gapped(t: String, forced: Boolean = false) =
            text(t).copy(gapBefore = Spacing(px = 30f), marginBefore = Spacing(px = 20f), breakBefore = forced)
        // 20px lines, 60px pages: "aaa" fills page 1, so "bbb" starts page 2 at an unforced break
        val l = layout(text("aaa"), gapped("bbb"), gapped("ccc", forced = true), lineHeightRatio = 1f, pageHeight = 60)
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), l.pages.map { page -> page.map { it.blockIndex }.distinct() })
        assertEquals(10, l.gapsOf(1).first())
        assertEquals(30, l.gapsOf(2).first())
    }

    @Test
    fun `the parser tells a block's margins before any padding from the rest of its gap`() {
        val blocks = com.baca.shared.parser.parseXhtml(
            """<html><head><style>p { margin: 1em 0 } h2 { margin-top: 2em; padding-top: 1em }</style></head>""" +
                """<body><p>a</p><h2>Title</h2><p>b</p></body></html>""",
        )
        val title = blocks[1]
        // above the 1.5em heading: its 2em margin (3em of body text, collapsing the p's 1em), then its 1em padding
        assertEquals(Spacing(4.5f), title.gapBefore)
        assertEquals(Spacing(3f), title.marginBefore)
        // below it, its own 0.83em bottom margin outweighs the p's 1em top margin
        assertEquals(Spacing(1.245f), blocks[2].marginBefore)
    }

    @Test
    fun `a line grows round a raised mark or a bigger span, as a browser's line box does`() {
        // grid fonts: ascent 0.8, descent 0.2 of the size. 20px text at 1.5: each line box reaches
        // 20 × (1.5 + 0.6) / 2 = 21 above the baseline and 9 below
        val plain = FormattedSpan("abc ", false, false)
        // a mark at 0.5 the size raised 0.5 of its own size: 5 + 10 × 1.05 = 15.5 above, inside the strut
        val small = FormattedSpan("1", false, false, scale = 0.5f, baseline = 0.5f)
        // the same mark at full size: 10 + 21 = 31 above, 10 over the strut
        val raised = FormattedSpan("1", false, false, baseline = 0.5f)
        // a span 1.5 times the size: 31.5 above and 13.5 below
        val big = FormattedSpan("B", false, false, scale = 1.5f)
        fun para(vararg spans: FormattedSpan) = Block.Text(spans.toList(), BlockKind.PARAGRAPH, 0)
        val l = layout(para(plain, small), para(plain, raised), para(plain, big), fontSizePx = 20f, lineHeightRatio = 1.5f, colWidth = 1000, pageHeight = 1000)
        val grown = l.lines.map { listOf(it.growAbove, it.growBelow) }
        listOf(listOf(0f, 0f), listOf(10f, 0f), listOf(10.5f, 4.5f)).zip(grown).forEach { (want, got) ->
            want.zip(got).forEach { (w, g) -> assertEquals(w, g, 0.001f) }
        }
        // and the page stacks them at their grown heights: 30, 40, 45
        assertEquals(listOf(30, 40, 45), l.rowHeightsOf(0).toList())
    }

    @Test
    fun `a paragraph opening with a label in another face keeps its own font's strut`() {
        // Literata's and Roboto's ascent and descent: a sans strut would sit 1.85px lower than the serif text
        val metrics = object : TextMetrics by GridMetrics() {
            override fun verticalMetrics(fontFamily: String?, face: com.baca.shared.parser.BookFace?, family: String?) =
                if (face == com.baca.shared.parser.BookFace.SANS) TextMetrics.VerticalMetrics(0.928f, 0.244f)
                else TextMetrics.VerticalMetrics(1.177f, 0.308f)
        }
        // a dialogue line as Strange Buildings sets it: the speaker in sans at 0.85em, then the words in the serif
        val label = FormattedSpan("AUTHOR: ", true, false, scale = 0.85f, face = com.baca.shared.parser.BookFace.SANS)
        val words = FormattedSpan("I agree with what you said about it", false, false)
        val l = layout(Block.Text(listOf(label, words), BlockKind.PARAGRAPH, 0), fontSizePx = 20f, lineHeightRatio = 1.5f, colWidth = 200, metrics = metrics)
        assertTrue(l.lines.size > 1)
        // the label's own box reaches lower on its line; the lines after it keep the serif strut
        assertTrue(l.lines.drop(1).all { it.growAbove == 0f && it.growBelow == 0f })
    }

    @Test
    fun `a line's boxes reach as far as Chromium rounds their fonts`() {
        // Literata's and Roboto's ascent and descent, at LibreraX's 20 CSS px on a 2.625 screen (device px)
        val serif = TextMetrics.VerticalMetrics(1.177f, 0.308f)
        val sans = TextMetrics.VerticalMetrics(0.928f, 0.244f)
        val dpr = 2.625f
        fun growth(sansCss: Int): Float {
            val (strutUp, strutDown) = lineExtents(20 * dpr, 30 * dpr, serif)
            val (up, down) = lineExtents(sansCss * dpr, sansCss * 1.5f * dpr, sans)
            return (maxOf(up, strutUp) + maxOf(down, strutDown) - strutUp - strutDown) / dpr
        }
        // as probed: a sans label 20px, 21px, 22px and 24px tall in a 20px serif line
        listOf(20 to 1.523f, 21 to 2.263f, 22 to 3f, 24 to 6f).forEach { (size, grown) -> assertEquals(grown, growth(size), 0.02f) }
        assertEquals(0f, growth(15))
    }

    @Test
    fun `a break at the end of a paragraph adds no line, and a paragraph of only a break is one empty line`() {
        val blocks = com.baca.shared.parser.parseXhtml("<html><body><p>a<br/></p><p><br/></p><p>b<br/><br/></p></body></html>")
        val l = layout(*blocks.toTypedArray(), lineHeightRatio = 1f, colWidth = 1000, pageHeight = 1000)
        // "a"; an empty line; "b" then the one empty line its second break makes
        assertEquals(listOf(0, 1, 2, 2), l.lines.map { it.blockIndex })
    }

    @Test
    fun `a box is drawn from its top edge to its bottom edge, its insets kept at a page's top`() {
        val box = BlockBox(id = 1, border = listOf(2f, 2f, 2f, 2f), topInset = Spacing(px = 10f), bottomInset = Spacing(px = 10f))
        val boxed = text("abc").copy(boxes = listOf(box), gapBefore = Spacing(px = 30f))
        val l = ReflowLayout(listOf(text("before"), boxed, text("after").copy(gapBefore = Spacing(px = 20f))), GridMetrics(), ReflowSettings(fontSizePx = 20f, lineHeightRatio = 1f), 100, 1000)
        // before: 0-20; gap 30 (20 margin + 10 inset); text 50-70; bottom inset to 80
        assertEquals(listOf(PageBox(box, 0, 100, 40, 80, startsHere = true, endsHere = true)), l.boxesOf(0))
        // at a page's top the margin goes, the box's 10px stay
        val top = ReflowLayout(listOf(boxed), GridMetrics(), ReflowSettings(fontSizePx = 20f, lineHeightRatio = 1f), 100, 1000)
        assertEquals(10, top.gapsOf(0)[0])
        assertEquals(0, top.boxesOf(0).single().topPx)
    }
}
