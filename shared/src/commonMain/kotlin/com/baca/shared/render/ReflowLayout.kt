package com.baca.shared.render

import com.baca.shared.parser.Block
import com.baca.shared.parser.BlockBox
import com.baca.shared.parser.BlockKind
import com.baca.shared.parser.BookFace
import com.baca.shared.parser.NORMAL_LINE
import com.baca.shared.parser.Room
import com.baca.shared.parser.Spacing
import com.baca.shared.parser.DropCap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Pagination settings in pixels; the UI derives them from density, screen
 * size and the display settings screen (TECH 10.4).
 */
data class ReflowSettings(
    /** the running text's size, to the fraction of a px as a browser keeps it */
    val fontSizePx: Float,
    val lineHeightRatio: Float = 1.7f,
    val imageHeightPx: Int = 0,
    /** device px to a CSS px (the screen density): a book's px spacing doesn't grow with the font */
    val cssPx: Float = 1f,
    /** "serif" | "sans" | "mono" | "libron" — must match the renderer's font. */
    val fontFamily: String? = null,
)

/** Font size relative to body text. Shared by layout and renderer. */
fun Block.Text.fontScale(): Float = scale ?: 1f

/**
 * Line height over font size: the reader's for running text, a browser's `normal`
 * for headings, which LibreraX leaves at the book's own. Shared by layout and renderer.
 */
fun Block.Text.lineRatio(readerRatio: Float, normalRatio: () -> Float = { DEFAULT_NORMAL_RATIO }): Float = when (lineRatio) {
    null -> readerRatio
    NORMAL_LINE -> normalRatio()
    else -> lineRatio
}

// `line-height: normal` where the font isn't known: a browser's usual 1.2
private const val DEFAULT_NORMAL_RATIO = 1.2f

/**
 * Where a block's lines sit, in px from the column's left: lines are measured in a
 * box from [startPx] to [endPx] short of the right edge; the first line starts
 * [firstLinePx] into it and the others [restLinePx]. A positive `text-indent` moves
 * the first line in; a negative one pulls it out to the left of the rest. Shared by
 * layout and renderer.
 */
data class LineBox(val startPx: Int, val endPx: Int, val firstLinePx: Int, val restLinePx: Int) {
    fun linePx(firstLine: Boolean): Int = startPx + if (firstLine) firstLinePx else restLinePx
}

fun Block.Text.lineBox(bodyFontPx: Float, cssPx: Float, columnPx: Int): LineBox {
    val inset = inset.toPx(bodyFontPx, cssPx) + (insetShare * columnPx).roundToInt()
    val end = insetEnd.toPx(bodyFontPx, cssPx) + (insetEndShare * columnPx).roundToInt()
    // a percent indent is of the block's own width
    val indent = indent.toPx(bodyFontPx, cssPx) + (indentShare * (columnPx - inset - end)).roundToInt()
    val hang = maxOf(-indent, 0)
    // like LibreraX's keepIndentsInside: a hanging first line never pulls out past the column
    return LineBox(maxOf(inset - hang, 0), end, maxOf(indent, 0), hang)
}

/**
 * How far a box of text in a font of [sizePx] (device px) and a line [lineHeightPx] tall
 * reaches above its baseline and below, as Chromium works it out (probed in LibreraX's
 * WebView): the font's ascent and descent each rounded to a whole device px, a px moved
 * from ascent to descent where the descent rounded down, and the leading left over split
 * in half in 1/64 px, the odd 64th below.
 */
internal fun lineExtents(sizePx: Float, lineHeightPx: Float, font: TextMetrics.VerticalMetrics): Pair<Float, Float> {
    var ascent = (font.ascent * sizePx).roundToInt()
    var descent = (font.descent * sizePx).roundToInt()
    if (descent < font.descent * sizePx && ascent >= 1) {
        descent++
        ascent--
    }
    val leading = (lineHeightPx * 64).roundToInt() - (ascent + descent) * 64
    val above = leading / 2
    return (ascent + above / 64f) to (descent + (leading - above) / 64f)
}

/**
 * Room above the text of a page [heightPx] tall, as LibreraX lays its page out: a page
 * holds whole lines of body text at their exact height, with 3px to spare (a line that
 * fits to the fraction of a px is still pushed on), and what is left under the last one
 * is split between top and bottom, so the text sits centred instead of a line short at
 * the foot.
 */
fun ReflowSettings.evenLinesPx(heightPx: Float, lineHeightPx: Float = fontSizePx * lineHeightRatio): Float {
    // in css px, as the page's own layout works it out, then back to device px
    val height = heightPx / cssPx
    val lineHeight = lineHeightPx / cssPx
    if (lineHeight <= 0f) return 0f
    val lines = floor((height - LINE_SLACK_PX) / lineHeight).toInt()
    if (lines < 1) return 0f
    return floor((height - lines * lineHeight - LINE_SLACK_PX) / 2).coerceAtLeast(0f) * cssPx
}

private const val LINE_SLACK_PX = 3f

/**
 * The page's room above its text for a chapter of [blocks], LibreraX's lines being its
 * first paragraph's with text (`evenLines` takes the section's first non-empty `<p>`):
 * a chapter set in another size has pages of its own lines. [heightPx] is the room for text.
 */
fun ReflowSettings.chapterEvenPx(blocks: List<Block>, heightPx: Float): Float {
    val first = blocks.firstOrNull { it is Block.Text && it.kind == BlockKind.PARAGRAPH && it.cell == null && it.text.isNotBlank() } as Block.Text?
        ?: return evenLinesPx(heightPx)
    return evenLinesPx(heightPx, fontSizePx * first.fontScale() * first.lineRatio(lineHeightRatio))
}

/** An `<hr>`'s band: the book's height, else a browser's 1px border top and bottom. Shared by layout and renderer. */
fun Block.Rule.heightPx(fontSizePx: Float, cssPx: Float, room: Room = Room.NONE): Int = height?.toPx(fontSizePx, cssPx, room) ?: if (line) 2 * ruleBorderPx(cssPx) else 0

/** A 1 CSS px border as a browser draws it: whole device px, rounded down, at least one. */
fun ruleBorderPx(cssPx: Float): Int = floor(cssPx).toInt().coerceAtLeast(1)


/**
 * A [BlockBox] as drawn on one page, in px from the page's text top-left; its top
 * border only where it begins on this page, its bottom one only where it ends.
 */
data class PageBox(
    val box: BlockBox,
    val leftPx: Int,
    val rightPx: Int,
    val topPx: Int,
    val bottomPx: Int,
    val startsHere: Boolean,
    val endsHere: Boolean,
)

/** One visible line (or image placeholder) placed on a page. */
data class PageLine(
    val blockIndex: Int,
    val start: Int,
    val endExclusive: Int,
    val isImage: Boolean = false,
    /** resolved height for image lines; text lines derive it from style */
    val heightPx: Int? = null,
    /** a right float drawn on the previous line's row, at its right end; takes no height */
    val joinsPrevious: Boolean = false,
    /** a drop cap's width, which this line starts after */
    val floatIndentPx: Int = 0,
    /** in a block of several columns (`column-count`), which one the line is in */
    val column: Int = 0,
    /** px the line grows above and below its block's line height, for a raised mark or a bigger span in it */
    val growAbove: Float = 0f,
    val growBelow: Float = 0f,
    /** drawn this far below its row: a table cell in the middle or at the bottom of a taller row */
    val dropPx: Float = 0f,
)

enum class AnchorResolution { OFFSETS, QUOTE }

data class AnchorRestore(val pageIndex: Int, val resolution: AnchorResolution)

/**
 * Paginates one chapter into pages of [pageHeightPx] lines-sized steps.
 *
 * Pure: no IO, no clocks, no Compose. Same inputs always produce the same
 * pages, so the reader can cache instances keyed by (book, viewport, settings)
 * and rely on stability (TECH 4 page cache).
 */
class ReflowLayout(
    private val blocks: List<Block>,
    private val metrics: TextMetrics,
    private val settings: ReflowSettings,
    private val columnWidthPx: Int,
    private val pageHeightPx: Int,
    private val imageHeight: (Block.Image) -> Int? = { null },
    /** the reader's whole window, which a linked stylesheet's `vh` is of */
    windowHeightPx: Int = 0,
) {
    // what a margin's share of the page, window or column comes to
    private val room = Room(pageHeightPx.toFloat(), windowHeightPx.toFloat(), columnWidthPx.toFloat())

    /**
     * Each table's column edges in px, from 0 to the column's width, laid out as a
     * browser's auto table layout lays them: a column the book sizes in percent
     * gets that share, widened to fit its longest word; the other columns share
     * what's left; and where that's more than the width, the columns with room to
     * spare give it back.
     */
    private val tableColumns: Map<Int, TableColumns> by lazy {
        blocks.indices.filter { (blocks[it] as? Block.Text)?.cell != null }
            .groupBy { (blocks[it] as Block.Text).cell!!.table }
            .mapValues { (_, cellBlocks) -> columnEdges(cellBlocks) }
    }

    // declared before the lines and pages built from it
    /** Which line of [lines] is each box's first and last, by box id. */
    private val boxSpans: Map<Int, IntRange> by lazy {
        val first = HashMap<Int, Int>()
        val last = HashMap<Int, Int>()
        lines.forEachIndexed { i, line ->
            blocks[line.blockIndex].boxes.forEach { box ->
                first.getOrPut(box.id) { i }
                last[box.id] = i
            }
        }
        first.mapValues { (id, f) ->
            // a cell's box runs to the end of its row, past its own last line in a shorter cell
            val cellBox = blocks[lines[f].blockIndex].boxes.first { it.id == id }.cell
            val row = (blocks[lines[f].blockIndex] as? Block.Text)?.cell
            var end = last.getValue(id)
            if (cellBox && row != null) {
                while (end + 1 < lines.size && (blocks[lines[end + 1].blockIndex] as? Block.Text)?.cell?.let { it.table == row.table && it.row == row.row } == true) end++
            }
            f..end
        }
    }

    /**
     * Each page's rows, to the fraction of a px as a browser stacks them: the space above
     * each line (the block's gap, or at a page's top what of it a box keeps) and its row's
     * height (0 for a line joining the row before it).
     */
    private val gaps = mutableListOf<FloatArray>()
    private val rowHeights = mutableListOf<FloatArray>()
    private val spacerCache = HashMap<Block.Text, List<Int?>>()

    val lines: List<PageLine> = buildLines()
    val pages: List<List<PageLine>> = packPages()


    fun pageCount(): Int = pages.size

    /** Chapter-relative position of the first visible item on [pageIndex]. */
    fun positionForPage(pageIndex: Int): BlockPosition {
        val page = pages[pageIndex.coerceIn(0, pages.lastIndex)]
        val first = page.firstOrNull() ?: return BlockPosition(0, 0, "")
        if (first.isImage) {
            return BlockPosition(first.blockIndex, 0, "")
        }
        val block = blocks.getOrNull(first.blockIndex) ?: return BlockPosition(0, 0, "")
        val text = block.text
        val offset = first.start
        val quote = text.substring(offset, (offset + QUOTE_LENGTH).coerceAtMost(text.length))
        return BlockPosition(first.blockIndex, offset, quote)
    }

    /**
     * Restore order per TECH 5.2: offsets first (block + charOffset), then the
     * quote snippet, then null — the caller falls back to book progression.
     */
    fun pageForPosition(blockIndex: Int, charOffset: Int, quote: String): AnchorRestore? {
        if (blockIndex in blocks.indices) {
            val line = lineAt(blockIndex, charOffset)
            if (line != null) {
                return AnchorRestore(pageIndexOf(line), AnchorResolution.OFFSETS)
            }
        }

        if (quote.isNotBlank()) {
            val blockIdx = blocks.indexOfFirst { it.text.contains(quote) }
            if (blockIdx >= 0) {
                val offset = blocks[blockIdx].text.indexOf(quote)
                val line = lineAt(blockIdx, offset)
                if (line != null) {
                    return AnchorRestore(pageIndexOf(line), AnchorResolution.QUOTE)
                }
            }
        }

        return null
    }

    /**
     * The line holding [charOffset] of [blockIndex]: the block's last line starting at
     * or before it. A highlight beginning mid-line belongs to that line's page, not the
     * next line's. A block without lines (empty) gives the first line after it. Lines in
     * columns aren't in text order, so the latest start wins, not the last line.
     */
    private fun lineAt(blockIndex: Int, charOffset: Int): PageLine? =
        lines.filter { it.blockIndex == blockIndex && it.start <= charOffset }.maxByOrNull { it.start }
            ?: lines.firstOrNull { it.blockIndex > blockIndex }

    // match by position: packing may resize image lines, so equality won't do
    private fun pageIndexOf(line: PageLine): Int = pages.indexOfFirst { page ->
        page.any { it.blockIndex == line.blockIndex && it.start == line.start }
    }

    private fun fontPxOf(block: Block.Text): Float = settings.fontSizePx * block.fontScale()

    /** A line's height to the fraction of a px, as pages stack it. */
    private fun exactLineHeightOf(block: Block.Text): Float = fontPxOf(block) * lineRatioOf(block)

    /** The block's line height over its font size; `normal` is its font's ascent and descent, as a browser's. Shared with the renderer. */
    fun lineRatioOf(block: Block.Text): Float = block.linePx?.let { it * settings.cssPx / fontPxOf(block) } ?: block.lineRatio(settings.lineHeightRatio) {
        val (face, family) = strutFontOf(block)
        metrics.verticalMetrics(settings.fontFamily, face, family).let { it.ascent + it.descent }
    }

    /** The block's own font, the one most of its text is in: a paragraph set in sans has a sans strut, one opening with a sans label ("AUTHOR:") keeps its serif one. */
    private fun strutFontOf(block: Block.Text): Pair<BookFace?, String?> =
        block.spans.groupBy { it.face to it.family }.maxByOrNull { (_, spans) -> spans.sumOf { it.text.length } }?.key ?: (null to null)

    // the whole px the metrics style a line with: line height doesn't change where it breaks
    private fun lineHeightPxOf(block: Block.Text): Int = ceil(exactLineHeightOf(block)).toInt()

    /** The text's styles as the renderer draws them, so measured breaks match drawn ones. */
    private fun styleRangesOf(block: Block.Text, spacers: List<Int?> = spacersOf(block)): List<TextMetrics.StyleRange> = buildList {
        var offset = 0
        block.spans.forEachIndexed { i, span ->
            add(
                TextMetrics.StyleRange(
                    offset,
                    offset + span.text.length,
                    span.bold,
                    span.italic,
                    span.smallCaps,
                    span.scale,
                    span.face,
                    span.image,
                    span.baseline + span.baselineLine * lineRatioOf(block),
                    span.letterSpacing,
                    spacers[i],
                    span.wordSpacing,
                    span.family,
                ),
            )
            offset += span.text.length
        }
    }

    /**
     * Each span's spacer width in px (null for text): an inline element's padding at
     * its own font size, or what an inline-block's text leaves of its `min-width`.
     * Shared by layout and renderer.
     */
    fun spacersOf(block: Block.Text): List<Int?> = spacerCache.getOrPut(block) {
        val fontPx = fontPxOf(block)
        block.spans.map { span ->
            val spacer = span.spacer ?: return@map null
            val widthPx = spacer.widthEm * fontPx * span.scale
            val box = spacer.padsBox ?: return@map widthPx.roundToInt()
            val label = block.spans.filter { it.inlineBox == box && it.spacer == null }
            val text = label.joinToString("") { it.text }
            val used = if (text.isEmpty()) 0 else {
                val styles = label.runningFold(0) { at, s -> at + s.text.length }.zip(label) { at, s ->
                    TextMetrics.StyleRange(at, at + s.text.length, s.bold, s.italic, s.smallCaps, s.scale, s.face, s.image, s.baseline, s.letterSpacing)
                }
                metrics.layoutLines(text, fontPx, lineHeightPxOf(block), columnWidthPx * 4, settings.fontFamily, styles).firstOrNull()?.rightPx ?: 0
            }
            (widthPx - used).roundToInt().coerceAtLeast(0)
        }
    }

    /**
     * Where [blockIndex]'s lines sit in the column: its own margins, padding and
     * indent and, in a table, its cell's column; in a block of several columns,
     * [column]'s share of that. Shared by layout and renderer.
     */
    fun lineBoxOf(blockIndex: Int, column: Int = 0): LineBox {
        val block = blocks[blockIndex] as Block.Text
        val box = block.lineBox(settings.fontSizePx, settings.cssPx, columnWidthPx)
        val columns = block.columns
        if (columns != null) {
            val gapPx = columns.gap.px()
            val widthPx = (columnWidthPx - box.startPx - box.endPx - gapPx * (columns.count - 1)) / columns.count
            val startPx = box.startPx + column * (widthPx + gapPx)
            return box.copy(startPx = startPx, endPx = columnWidthPx - startPx - widthPx)
        }
        val cell = block.cell ?: return box
        val table = tableColumns[cell.table] ?: return box
        val edges = table.edges
        // the edges are inside the room the table's boxes leave, which the cell's own insets hold too;
        // the table's spacing goes before each column and after the last
        val first = cell.column.coerceIn(0, edges.lastIndex)
        val last = (cell.column + cell.span).coerceIn(0, edges.lastIndex)
        val start = edges[first] + table.spacingPx * (first + 1)
        val end = columnWidthPx - table.start - table.end - edges[last] - table.spacingPx * last
        return box.copy(startPx = box.startPx + start, endPx = box.endPx + end)
    }

    /** A text block's lines as measured in its box, and the drop cap beside them. */
    private class Measured(val box: LineBox, val drawn: List<TextMetrics.LineSpan>, val cap: Cap?)

    private fun measure(blockIndex: Int): Measured {
        val block = blocks[blockIndex] as Block.Text
        // measure with the renderer's exact style (family + per-span
        // bold/italic/size) so breaks match the drawn lines
        val styleRanges = styleRangesOf(block)
        val box = lineBoxOf(blockIndex)
        val widthPx = columnWidthPx - box.startPx - box.endPx
        val cap = block.dropCap?.let { capOf(block, it, styleRanges) }
        val spans = if (cap != null) {
            dropCapLines(block, styleRanges, widthPx, cap)
        } else if (box.restLinePx > 0) {
            hangingLines(block, styleRanges, widthPx, box.restLinePx)
        } else {
            metrics.layoutLines(
                block.text,
                fontPxOf(block),
                lineHeightPxOf(block),
                widthPx,
                settings.fontFamily,
                styleRanges,
                box.firstLinePx,
                block.hyphenate,
                block.lang,
            )
        }
        return Measured(box, spans.filter { it.endExclusive > it.start }, cap)
    }

    private fun lineOf(blockIndex: Int, measured: Measured, n: Int, joins: Boolean = false): PageLine {
        val span = measured.drawn[n]
        val floatIndent = if (measured.cap != null && n < measured.cap.lines) measured.cap.widthPx else 0
        val (above, below) = growthOf(blocks[blockIndex] as Block.Text, span.start, span.endExclusive)
        return PageLine(blockIndex, span.start, span.endExclusive, joinsPrevious = joins, floatIndentPx = floatIndent, growAbove = above, growBelow = below)
    }

    /**
     * How far the line [start, end) of [block] reaches past its line height, above and below,
     * as a browser's line box covers every inline box in it: each span's box is the line
     * height ratio times its own size, centred on its font's ascent and descent and moved
     * by its raise; the block's own box (the strut) is the line height.
     */
    private fun growthOf(block: Block.Text, start: Int, end: Int): Pair<Float, Float> {
        val fontPx = fontPxOf(block)
        val ratio = lineRatioOf(block)
        val (blockFace, blockFamily) = strutFontOf(block)
        val (strutUp, strutDown) = lineExtents(fontPx, fontPx * ratio, metrics.verticalMetrics(settings.fontFamily, blockFace, blockFamily))
        var up = strutUp
        var down = strutDown
        var at = 0
        for (span in block.spans) {
            val from = at
            at += span.text.length
            if (at <= start || from >= end || span.image != null || span.spacer != null) continue
            val raise = span.baseline + span.baselineLine * ratio
            if (span.scale == 1f && raise == 0f && span.baselinePx == 0f && span.face == blockFace && span.family == null) continue
            val size = fontPx * span.scale
            val shift = raise * size + span.baselinePx
            val (spanUp, spanDown) = lineExtents(size, size * ratio, metrics.verticalMetrics(settings.fontFamily, span.face, span.family))
            up = maxOf(up, shift + spanUp)
            down = maxOf(down, spanDown - shift)
        }
        return (up - strutUp) to (down - strutDown)
    }

    private fun buildLines(): List<PageLine> {
        val result = mutableListOf<PageLine>()
        // where the last text line ends in the column, for a right float to sit beside it
        var lastRightPx: Int? = null
        var blockIndex = 0
        while (blockIndex < blocks.size) {
            val block = blocks[blockIndex]
            val row = (block as? Block.Text)?.cell?.row
            if (row != null) {
                var last = blockIndex
                while ((blocks.getOrNull(last + 1) as? Block.Text)?.cell?.row == row) last++
                result += rowLines(blockIndex..last)
                lastRightPx = null
                blockIndex = last + 1
                continue
            }
            when (block) {
                is Block.Image -> {
                    val heightPx = imageHeight(block)
                    val (above, below) = strutOf(block, heightPx ?: settings.imageHeightPx)
                    result.add(PageLine(blockIndex, 0, 0, isImage = true, heightPx = heightPx, growAbove = above, growBelow = below))
                    lastRightPx = null
                }
                is Block.Rule -> {
                    result.add(PageLine(blockIndex, 0, 0, isImage = true, heightPx = block.heightPx(settings.fontSizePx, settings.cssPx, room)))
                    lastRightPx = null
                }
                is Block.Text -> if (block.columns != null) {
                    result += columnLines(blockIndex, block.columns.count)
                    lastRightPx = null
                } else if (block.text.isNotEmpty()) {
                    val measured = measure(blockIndex)
                    val box = measured.box
                    val drawn = measured.drawn
                    // like a browser: a right float shares the row when it fits after the text, a space apart
                    val joins = block.floatsRight && drawn.size == 1 && lastRightPx?.let {
                        it + settings.fontSizePx.roundToInt() + drawn.single().rightPx <= columnWidthPx - box.startPx - box.endPx
                    } == true
                    drawn.indices.forEach { n -> result.add(lineOf(blockIndex, measured, n, joins)) }
                    lastRightPx = drawn.lastOrNull()?.let { last ->
                        // hanging lines after the first are measured without their indent
                        val hang = if (last.start > 0) box.restLinePx else 0
                        box.startPx + hang + last.rightPx
                    }
                }
            }
            blockIndex++
        }
        return result
    }

    /**
     * A table row: each cell's lines in its own column, side by side. The row's
     * k-th lines share a row on the page: the first cell with a k-th line is drawn
     * as usual, the others join it ([PageLine.joinsPrevious]).
     */
    private fun rowLines(range: IntRange): List<PageLine> {
        val cells = range.filter { blocks[it].text.isNotEmpty() }
            .groupBy { (blocks[it] as Block.Text).cell!!.column }.toSortedMap().values
            .map { column -> column.flatMap { i -> measure(i).let { m -> m.drawn.indices.map { n -> lineOf(i, m, n) } } } }
            .filter { it.isNotEmpty() }
        if (cells.isEmpty()) return emptyList()
        // a cell shorter than its row sits in it by its vertical-align: its share of the spare height above it
        val rowHeight = (0 until cells.maxOf { it.size }).sumOf { k -> cells.maxOf { cell -> cell.getOrNull(k)?.let(::heightOf) ?: 0f }.toDouble() }.toFloat()
        val dropped = cells.map { cell ->
            val spareAbove = (blocks[cell.first().blockIndex] as Block.Text).cell!!.spareAbove
            val drop = (rowHeight - cell.sumOf { heightOf(it).toDouble() }.toFloat()) * spareAbove
            cell.map { it.copy(dropPx = drop) }
        }
        val out = mutableListOf<PageLine>()
        for (k in 0 until dropped.maxOf { it.size }) {
            var first = true
            for (cell in dropped) {
                val line = cell.getOrNull(k) ?: continue
                out += line.copy(joinsPrevious = !first)
                first = false
            }
        }
        return out
    }

    /**
     * A block in [count] columns, as a browser balances them: its lines measured at a
     * column's width, the first column filled first, as many in each. A column's k-th
     * line shares a row with the others' ([PageLine.joinsPrevious]).
     */
    private fun columnLines(blockIndex: Int, count: Int): List<PageLine> {
        val measured = measure(blockIndex)
        val perColumn = (measured.drawn.size + count - 1) / count
        return (0 until perColumn).flatMap { k ->
            (0 until count).mapNotNull { column ->
                val n = column * perColumn + k
                if (n < measured.drawn.size) lineOf(blockIndex, measured, n, joins = column > 0).copy(column = column) else null
            }
        }
    }

    /**
     * A table's column [edges], from 0 to its width: the room inside the insets every cell
     * shares (the padding and borders of the boxes around the table, [start] and [end] px).
     */
    private class TableColumns(val start: Int, val end: Int, val edges: List<Int>, val spacingPx: Int = 0)

    private fun columnEdges(cellBlocks: List<Int>): TableColumns {
        val cells = cellBlocks.map { (blocks[it] as Block.Text).cell!! }
        val widths = cells.first().widths
        val count = maxOf(widths.size, cells.maxOf { it.column + it.span })
        // the table's own room: each cell's box less the cell's own border and padding, which its column holds
        val tableStarts = cellBlocks.map { i -> (blocks[i] as Block.Text).let { it.lineBox(settings.fontSizePx, settings.cssPx, columnWidthPx).startPx - it.cell!!.ownStart.px() } }
        val tableEnds = cellBlocks.map { i -> (blocks[i] as Block.Text).let { it.lineBox(settings.fontSizePx, settings.cssPx, columnWidthPx).endPx - it.cell!!.ownEnd.px() } }
        val shared = TableColumns(tableStarts.min(), tableEnds.min(), emptyList())
        val spacingPx = cells.first().spacing.px()
        val total = (columnWidthPx - shared.start - shared.end - spacingPx * (count + 1)).coerceAtLeast(0).toFloat()
        val mins = FloatArray(count)
        val maxes = FloatArray(count)
        cellBlocks.forEachIndexed { n, i ->
            val cell = (blocks[i] as Block.Text).cell!!
            if (cell.span == 1) {
                val shares = tableStarts[n] + tableEnds[n]
                mins[cell.column] = maxOf(mins[cell.column], (minContentPx(i) - shares).toFloat())
                maxes[cell.column] = maxOf(maxes[cell.column], (maxContentPx(i) - shares).toFloat())
            }
        }
        for (j in 0 until count) maxes[j] = maxOf(maxes[j], mins[j])
        val w = FloatArray(count) { j -> maxOf(widths.getOrNull(j)?.let { it * total } ?: 0f, mins[j]) }
        val auto = (0 until count).filter { widths.getOrNull(it) == null }
        if (auto.isNotEmpty()) {
            // the room the sized columns leave: each auto column its longest word, and what's
            // left by how much wider its text runs on one line (CSS 2.1 auto table layout)
            val left = (total - (0 until count).filter { it !in auto }.sumOf { w[it].toDouble() }.toFloat()).coerceAtLeast(0f)
            val minSum = auto.sumOf { mins[it].toDouble() }.toFloat()
            val maxSum = auto.sumOf { maxes[it].toDouble() }.toFloat()
            auto.forEach { j ->
                w[j] = when {
                    maxSum <= left -> maxes[j]
                    minSum >= left || maxSum <= minSum -> mins[j]
                    else -> mins[j] + (left - minSum) * (maxes[j] - mins[j]) / (maxSum - minSum)
                }
            }
        }
        val sum = w.sum()
        if (sum > total) {
            // over the width: columns above their longest word give back the excess
            val slack = FloatArray(count) { j -> w[j] - mins[j] }
            val slackSum = slack.sum()
            if (slackSum > 0f) for (j in 0 until count) w[j] -= (sum - total).coerceAtMost(slackSum) * slack[j] / slackSum
        } else if (sum < total && sum > 0f) {
            // a table as wide as the column with narrower columns: they grow in proportion
            for (j in 0 until count) w[j] *= total / sum
        }
        var x = 0f
        return TableColumns(shared.start, shared.end, listOf(0) + w.map { x += it; x.roundToInt() }, spacingPx)
    }

    /** A cell's text on one line (each hard line), with the cell's own insets: what its column would take with room to spare. */
    private fun maxContentPx(blockIndex: Int): Int {
        val block = blocks[blockIndex] as Block.Text
        val box = block.lineBox(settings.fontSizePx, settings.cssPx, columnWidthPx)
        val widest = metrics.layoutLines(block.text, fontPxOf(block), lineHeightPxOf(block), MAX_CONTENT_PX, settings.fontFamily, styleRangesOf(block))
            .maxOfOrNull { it.rightPx } ?: 0
        return widest + box.startPx + box.endPx + maxOf(box.firstLinePx, 0)
    }

    /** The widest word of a cell's text, which its column can't be narrower than, with the cell's own insets. */
    private fun minContentPx(blockIndex: Int): Int {
        val block = blocks[blockIndex] as Block.Text
        val styles = styleRangesOf(block)
        val box = block.lineBox(settings.fontSizePx, settings.cssPx, columnWidthPx)
        val widest = Regex("\\S+").findAll(block.text).sortedByDescending { it.value.length }.take(MIN_CONTENT_WORDS).maxOfOrNull { word ->
            val from = word.range.first
            val wordStyles = styles.mapNotNull { r ->
                r.copy(start = maxOf(r.start - from, 0), endExclusive = minOf(r.endExclusive - from, word.value.length)).takeIf { it.endExclusive > it.start }
            }
            metrics.layoutLines(word.value, fontPxOf(block), lineHeightPxOf(block), columnWidthPx * 4, settings.fontFamily, wordStyles)
                .firstOrNull()?.rightPx ?: 0
        } ?: 0
        return widest + box.startPx + box.endPx + maxOf(box.firstLinePx, 0)
    }

    /** A drop cap's width and how many lines it stands beside. */
    private data class Cap(val widthPx: Int, val lines: Int)

    private fun capOf(block: Block.Text, cap: DropCap, styles: List<TextMetrics.StyleRange>): Cap? {
        val capStyles = styles.mapNotNull { r -> r.copy(endExclusive = minOf(r.endExclusive, cap.end)).takeIf { it.endExclusive > it.start } }
        val capText = block.text.substring(0, cap.end)
        val width = metrics.layoutLines(capText, fontPxOf(block), lineHeightPxOf(block), columnWidthPx * 2, settings.fontFamily, capStyles)
            .firstOrNull()?.rightPx ?: return null
        val capFontPx = fontPxOf(block) * (block.spans.firstOrNull()?.scale ?: 1f)
        val lines = ceil(capFontPx * cap.lineRatio / exactLineHeightOf(block)).toInt().coerceAtLeast(1)
        return Cap(width, lines)
    }

    /**
     * The text after a drop cap: its first [Cap.lines] lines narrower by the cap's
     * width, the rest at full width. The first line also holds the cap's own chars,
     * so every offset of the block lies in a line.
     */
    private fun dropCapLines(block: Block.Text, styles: List<TextMetrics.StyleRange>, widthPx: Int, cap: Cap): List<TextMetrics.LineSpan> {
        val text = block.text
        val capEnd = block.dropCap!!.end
        fun measure(from: Int, width: Int) = metrics.layoutLines(
            text.substring(from), fontPxOf(block), lineHeightPxOf(block), width, settings.fontFamily,
            styles.mapNotNull { range ->
                range.copy(start = maxOf(range.start - from, 0), endExclusive = range.endExclusive - from).takeIf { it.endExclusive > it.start }
            },
            hyphenate = block.hyphenate,
            language = block.lang,
        ).map { it.copy(start = it.start + from, endExclusive = it.endExclusive + from) }
        if (capEnd >= text.length) return listOf(TextMetrics.LineSpan(0, text.length))
        val beside = measure(capEnd, widthPx - cap.widthPx).take(cap.lines)
        var besideEnd = beside.lastOrNull()?.endExclusive ?: capEnd
        // the break's own space belongs to neither line
        while (besideEnd < text.length && text[besideEnd] == ' ') besideEnd++
        val below = if (besideEnd < text.length) measure(besideEnd, widthPx) else emptyList()
        return (beside + below).mapIndexed { i, line -> if (i == 0) line.copy(start = 0) else line }
    }

    /**
     * The first line at full width, the rest narrower by [hangPx]. Measured
     * in two parts rather than with a rest-line indent, because text engines
     * differ on whether a line after a hard break counts as a "first line".
     */
    private fun hangingLines(block: Block.Text, styles: List<TextMetrics.StyleRange>, widthPx: Int, hangPx: Int): List<TextMetrics.LineSpan> {
        val text = block.text
        fun measure(from: Int, widthPx: Int) = metrics.layoutLines(
            text.substring(from),
            fontPxOf(block),
            lineHeightPxOf(block),
            widthPx,
            settings.fontFamily,
            styles.mapNotNull { range ->
                range.copy(start = maxOf(range.start - from, 0), endExclusive = range.endExclusive - from)
                    .takeIf { it.endExclusive > it.start }
            },
            hyphenate = block.hyphenate,
            language = block.lang,
        )
        val first = measure(0, widthPx).firstOrNull() ?: return emptyList()
        var restStart = first.endExclusive
        // the break's own space or newline belongs to neither line
        while (restStart < text.length && text[restStart] == ' ') restStart++
        if (restStart < text.length && text[restStart] == '\n') restStart++
        if (restStart >= text.length) return listOf(first)
        val rest = measure(restStart, widthPx - hangPx)
            .map { it.copy(start = it.start + restStart, endExclusive = it.endExclusive + restStart) }
        return listOf(first) + rest
    }

    /** Device px of a [Spacing] at body text size. */
    private fun Spacing.px(): Int = toPx(settings.fontSizePx, settings.cssPx, room)

    /** The space above a line from its boxes starting there: their border and padding, kept at a page's top. */
    private fun boxTopPx(index: Int): Float =
        blocks[lines[index].blockIndex].boxes.filter { boxSpans[it.id]?.first == index }.maxOfOrNull { it.topInset.exact() } ?: 0f

    /** The space below a line from its boxes ending there: their padding and border. */
    private fun boxBottomPx(index: Int): Float =
        blocks[lines[index].blockIndex].boxes.filter { boxSpans[it.id]?.last == index }.maxOfOrNull { it.bottomInset.exact() } ?: 0f

    private fun Spacing.exact(): Float = exactPx(settings.fontSizePx, settings.cssPx, room)

    /** The part of a block's gap a page break cuts: its margins before any padding or border. */
    private fun cutPx(block: Block): Float = (block.marginBefore ?: block.gapBefore)?.exact() ?: 0f

    private fun heightOf(line: PageLine): Float =
        (line.heightPx ?: if (line.isImage) settings.imageHeightPx else null)?.let { it + line.growAbove + line.growBelow } ?: lineHeightOf(line)

    /**
     * How far an inline picture's line reaches above and below it: the picture stands on the
     * baseline of a line in its containing block's font, which reaches below by the font's
     * descent and half its leading, and above by its ascent and the other half, when that's
     * taller than the picture.
     */
    private fun strutOf(block: Block.Image, heightPx: Int): Pair<Float, Float> {
        val strut = block.strut ?: return 0f to 0f
        val fontPx = settings.fontSizePx * strut.scale
        val ratio = block.strutRatio ?: settings.lineHeightRatio
        val (up, down) = lineExtents(fontPx, fontPx * ratio, metrics.verticalMetrics(settings.fontFamily, strut.face, strut.family))
        return maxOf(0f, up - heightPx) to maxOf(0f, down)
    }

    private fun packPages(): List<List<PageLine>> {
        val pages = mutableListOf<List<PageLine>>()
        var current = mutableListOf<PageLine>()
        var currentGaps = mutableListOf<Float>()
        var currentHeights = mutableListOf<Float>()
        var used = 0f
        var lastBlock = -1
        fun newPage() {
            pages.add(current)
            gaps.add(currentGaps.toFloatArray())
            rowHeights.add(currentHeights.toFloatArray())
            current = mutableListOf()
            currentGaps = mutableListOf()
            currentHeights = mutableListOf()
            used = 0f
        }
        // where each row of the page starts, in [lines]
        var rowStarts = mutableListOf<Int>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (line.joinsPrevious && current.isNotEmpty()) {
                current.add(line)
                currentGaps.add(0f)
                currentHeights.add(0f)
                lastBlock = line.blockIndex
                index++
                continue
            }
            // a row is as tall as the tallest of its lines: the ones joining it come next
            var height = heightOf(line)
            var next = index + 1
            var below = boxBottomPx(index)
            while (next < lines.size && lines[next].joinsPrevious) {
                height = maxOf(height, heightOf(lines[next]))
                below = maxOf(below, boxBottomPx(next))
                next++
            }
            val newBlock = line.blockIndex != lastBlock
            val gap = if (newBlock) blocks[line.blockIndex].gapBefore?.exact() ?: 0f else 0f
            lastBlock = line.blockIndex
            // a picture that doesn't fit moves whole to the next page (foliate's break-inside: avoid);
            // a forced break starts one whatever room is left; a box's bottom edge stays on its page
            val forced = newBlock && blocks[line.blockIndex].breakBefore
            val overflows = used + gap + height + below > pageHeightPx + FIT_PX
            if (current.isNotEmpty() && overflows && !forced && avoidsBreakBefore(index)) {
                // a break the book avoids goes back to the page's last one it doesn't, as a browser's does
                val back = rowStarts.drop(1).lastOrNull { !avoidsBreakBefore(it) }
                if (back != null) {
                    val keep = back - rowStarts.first()
                    current = current.subList(0, keep).toMutableList()
                    currentGaps = currentGaps.subList(0, keep).toMutableList()
                    currentHeights = currentHeights.subList(0, keep).toMutableList()
                    newPage()
                    rowStarts = mutableListOf()
                    lastBlock = lines[back - 1].blockIndex
                    index = back
                    continue
                }
            }
            if (current.isNotEmpty() && (forced || overflows)) {
                newPage()
                rowStarts = mutableListOf()
            }
            // at a page's top a browser cuts the margins before any padding or border, and keeps the
            // rest (and a box's border and padding); after a forced break it keeps the margins too
            val above = when {
                current.isNotEmpty() || (forced && pages.isNotEmpty()) -> gap
                else -> maxOf(boxTopPx(index), if (newBlock) gap - cutPx(blocks[line.blockIndex]) else 0f)
            }
            used += above + height
            rowStarts.add(index)
            current.add(line)
            currentGaps.add(above)
            currentHeights.add(height)
            index++
        }
        if (current.isNotEmpty() || pages.isEmpty()) newPage()
        return pages
    }

    /** Whether the book avoids a page break before the row starting at line [index] (`break-*: avoid`). */
    private fun avoidsBreakBefore(index: Int): Boolean {
        if (index == 0) return false
        val block = blocks[lines[index].blockIndex]
        return if (lines[index - 1].blockIndex != lines[index].blockIndex) block.keepWithPrevious else block.keepLines
    }

    /**
     * Each row of [pageIndex] between whole pixels: its top and bottom are the exact ones
     * rounded, so the rows of a page add up to their exact total without drifting. For each
     * line, the px above it and its row's height (both 0 for a line joining the row before).
     */
    private fun wholeRowsOf(pageIndex: Int): Pair<IntArray, IntArray> {
        val exactGaps = gaps[pageIndex]
        val exactHeights = rowHeights[pageIndex]
        val above = IntArray(exactGaps.size)
        val tall = IntArray(exactGaps.size)
        var bottom = 0f
        pages[pageIndex].forEachIndexed { i, line ->
            if (line.joinsPrevious && i > 0) return@forEachIndexed
            val top = bottom + exactGaps[i]
            val end = top + exactHeights[i]
            above[i] = top.roundToInt() - bottom.roundToInt()
            tall[i] = end.roundToInt() - top.roundToInt()
            bottom = end
        }
        return above to tall
    }

    /** The whole px above each line of [pageIndex] (0 for a line joining the row before it). Shared by layout and renderer. */
    fun gapsOf(pageIndex: Int): IntArray = wholeRowsOf(pageIndex).first

    /** The whole px each row of [pageIndex] is drawn in, by its first line (0 for the lines joining it). */
    fun rowHeightsOf(pageIndex: Int): IntArray = wholeRowsOf(pageIndex).second

    /**
     * The boxes drawn on [pageIndex], each with its rectangle in px from the page's
     * text top-left: from its top edge (or the page's top, where it began on an
     * earlier page) to its bottom edge (or the page's bottom, where it goes on).
     */
    fun boxesOf(pageIndex: Int): List<PageBox> {
        val page = pages[pageIndex]
        val (pageGaps, pageRows) = wholeRowsOf(pageIndex)
        val firstIndex = lines.indexOf(page.firstOrNull() ?: return emptyList())
        val tops = HashMap<Int, Int>()
        val bottoms = HashMap<Int, Int>()
        val starts = HashSet<Int>()
        val ends = HashSet<Int>()
        val byId = HashMap<Int, BlockBox>()
        var y = 0
        var rowTop = 0
        var rowHeight = 0
        page.forEachIndexed { i, line ->
            val index = firstIndex + i
            if (!line.joinsPrevious || i == 0) {
                y += rowHeight + pageGaps[i]
                rowTop = y
                rowHeight = pageRows[i]
            }
            blocks[line.blockIndex].boxes.forEach { box ->
                byId[box.id] = box
                val span = boxSpans.getValue(box.id)
                if (box.id !in tops) {
                    val first = span.first == index
                    tops[box.id] = if (first) rowTop - box.topInset.px() else 0
                    if (first) starts += box.id
                }
                val last = span.last == index
                bottoms[box.id] = maxOf(bottoms[box.id] ?: 0, rowTop + rowHeight + if (last) box.bottomInset.px() else 0)
                if (last) ends += box.id
            }
            // a cell's box ends on its row's last line, which may be another cell's
            byId.values.filter { it.cell && it.id !in ends && boxSpans.getValue(it.id).last == index }.forEach { box ->
                bottoms[box.id] = maxOf(bottoms[box.id] ?: 0, rowTop + rowHeight + box.bottomInset.px())
                ends += box.id
            }
        }
        // outermost first: nested boxes draw over their parents
        return byId.values.sortedBy { box -> blocks[page.first().blockIndex].boxes.indexOfFirst { it.id == box.id }.let { if (it < 0) Int.MAX_VALUE else it } }
            .map { box ->
                val (leftPx, rightPx) = cellEdgesOf(box) ?: (
                    (box.left.px() + (box.leftShare * columnWidthPx).roundToInt()) to
                        (columnWidthPx - box.right.px() - (box.rightShare * columnWidthPx).roundToInt())
                    )
                PageBox(
                    box,
                    leftPx = leftPx,
                    rightPx = rightPx,
                    topPx = tops.getValue(box.id),
                    bottomPx = if (box.id in ends) bottoms.getValue(box.id) else pageHeightPx,
                    startsHere = box.id in starts,
                    endsHere = box.id in ends,
                )
            }
    }

    /** A cell's box at its column's edges: its text's box less the cell's own border and padding. */
    private fun cellEdgesOf(box: BlockBox): Pair<Int, Int>? {
        if (!box.cell) return null
        val index = blocks.indexOfFirst { block -> block.boxes.any { it.id == box.id } }
        val cell = (blocks.getOrNull(index) as? Block.Text)?.cell ?: return null
        val text = lineBoxOf(index)
        return (text.startPx - cell.ownStart.px()) to (columnWidthPx - text.endPx + cell.ownEnd.px())
    }

    private fun lineHeightOf(line: PageLine): Float {
        val block = blocks.getOrNull(line.blockIndex) as? Block.Text ?: return settings.fontSizePx
        return exactLineHeightOf(block) + line.growAbove + line.growBelow
    }

    data class BlockPosition(val blockIndex: Int, val charOffset: Int, val quote: String)

    private companion object {
        const val QUOTE_LENGTH = 60
        // what a row may run past the page by and still fit: float error, not a real px
        const val FIT_PX = 0.01f
        // a cell's longest words by chars, of which the widest is its column's minimum
        const val MIN_CONTENT_WORDS = 3
        // room enough that a cell's text never wraps, for its width on one line
        const val MAX_CONTENT_PX = 1_000_000
    }
}
