package com.baca.shared.render

import com.baca.shared.parser.Block
import com.baca.shared.parser.BlockKind
import com.baca.shared.parser.DropCap
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Pagination settings in pixels; the UI derives them from density, screen
 * size and the display settings screen (TECH 10.4).
 */
data class ReflowSettings(
    val fontSizePx: Int,
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
fun Block.Text.lineRatio(readerRatio: Float): Float = if (kind == BlockKind.HEADING) HEADING_LINE_HEIGHT else readerRatio

private const val HEADING_LINE_HEIGHT = 1.2f

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

fun Block.Text.lineBox(bodyFontPx: Int, cssPx: Float, columnPx: Int): LineBox {
    val indent = indent.toPx(bodyFontPx, cssPx)
    val inset = inset.toPx(bodyFontPx, cssPx) + (insetShare * columnPx).roundToInt()
    val hang = maxOf(-indent, 0)
    val end = insetEnd.toPx(bodyFontPx, cssPx) + (insetEndShare * columnPx).roundToInt()
    // like LibreraX's keepIndentsInside: a hanging first line never pulls out past the column
    return LineBox(maxOf(inset - hang, 0), end, maxOf(indent, 0), hang)
}

/**
 * Room above the text of a page [heightPx] tall, as LibreraX lays its page out: a page
 * holds whole lines of body text, and what is left under the last one is split between
 * top and bottom, so the text sits centred instead of a line short at the foot.
 */
fun ReflowSettings.evenLinesPx(heightPx: Int): Int {
    val lineHeightPx = ceil(fontSizePx * lineHeightRatio).toInt()
    if (heightPx < lineHeightPx || lineHeightPx <= 0) return 0
    return heightPx % lineHeightPx / 2
}

/** An `<hr>`'s band: the book's height, else a browser's 1px border top and bottom. Shared by layout and renderer. */
fun Block.Rule.heightPx(fontSizePx: Int, cssPx: Float): Int = height?.toPx(fontSizePx, cssPx) ?: if (line) RULE_PX else 0

private const val RULE_PX = 2

/** Space above the block: its CSS margins and padding (TECH 3.1). Shared by layout and renderer. */
fun Block.gapBeforePx(fontSizePx: Int, cssPx: Float): Int = gapBefore?.toPx(fontSizePx, cssPx) ?: 0

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
) {
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
     * next line's. A block without lines (empty) gives the first line after it.
     */
    private fun lineAt(blockIndex: Int, charOffset: Int): PageLine? =
        lines.lastOrNull { it.blockIndex == blockIndex && it.start <= charOffset }
            ?: lines.firstOrNull { it.blockIndex > blockIndex }

    // match by position: packing may resize image lines, so equality won't do
    private fun pageIndexOf(line: PageLine): Int = pages.indexOfFirst { page ->
        page.any { it.blockIndex == line.blockIndex && it.start == line.start }
    }

    private fun fontPxOf(block: Block.Text): Int = (settings.fontSizePx * block.fontScale()).toInt()

    // round up: the renderer draws the exact fractional height, so rounding
    // down would let a full page overflow and clip its last line
    private fun lineHeightPxOf(block: Block.Text): Int = ceil(fontPxOf(block) * block.lineRatio(settings.lineHeightRatio)).toInt()

    private fun buildLines(): List<PageLine> {
        val result = mutableListOf<PageLine>()
        // where the last text line ends in the column, for a right float to sit beside it
        var lastRightPx: Int? = null
        blocks.forEachIndexed { blockIndex, block ->
            when (block) {
                is Block.Image -> {
                    result.add(PageLine(blockIndex, 0, 0, isImage = true, heightPx = imageHeight(block)))
                    lastRightPx = null
                }
                is Block.Rule -> {
                    result.add(PageLine(blockIndex, 0, 0, isImage = true, heightPx = block.heightPx(settings.fontSizePx, settings.cssPx)))
                    lastRightPx = null
                }
                is Block.Text -> {
                    if (block.text.isEmpty()) return@forEachIndexed
                    // measure with the renderer's exact style (family + per-span
                    // bold/italic/size) so breaks match the drawn lines
                    val styleRanges = buildList {
                        var offset = 0
                        block.spans.forEach { span ->
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
                                    span.baseline,
                                    span.letterSpacing,
                                ),
                            )
                            offset += span.text.length
                        }
                    }
                    val box = block.lineBox(settings.fontSizePx, settings.cssPx, columnWidthPx)
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
                        )
                    }
                    val drawn = spans.filter { it.endExclusive > it.start }
                    // like a browser: a right float shares the row when it fits after the text, a space apart
                    val joins = block.floatsRight && drawn.size == 1 && lastRightPx?.let {
                        it + settings.fontSizePx + drawn.single().rightPx <= columnWidthPx - box.startPx - box.endPx
                    } == true
                    drawn.forEachIndexed { i, span ->
                        val floatIndent = if (cap != null && i < cap.lines) cap.widthPx else 0
                        result.add(PageLine(blockIndex, span.start, span.endExclusive, joinsPrevious = joins, floatIndentPx = floatIndent))
                    }
                    lastRightPx = drawn.lastOrNull()?.let { last ->
                        // hanging lines after the first are measured without their indent
                        val hang = if (last.start > 0) box.restLinePx else 0
                        box.startPx + hang + last.rightPx
                    }
                }
            }
        }
        return result
    }

    /** A drop cap's width and how many lines it stands beside. */
    private data class Cap(val widthPx: Int, val lines: Int)

    private fun capOf(block: Block.Text, cap: DropCap, styles: List<TextMetrics.StyleRange>): Cap? {
        val capStyles = styles.mapNotNull { r -> r.copy(endExclusive = minOf(r.endExclusive, cap.end)).takeIf { it.endExclusive > it.start } }
        val capText = block.text.substring(0, cap.end)
        val width = metrics.layoutLines(capText, fontPxOf(block), lineHeightPxOf(block), columnWidthPx * 2, settings.fontFamily, capStyles)
            .firstOrNull()?.rightPx ?: return null
        val capFontPx = fontPxOf(block) * (block.spans.firstOrNull()?.scale ?: 1f)
        val lines = ceil(capFontPx * cap.lineRatio / lineHeightPxOf(block)).toInt().coerceAtLeast(1)
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

    private fun packPages(): List<List<PageLine>> {
        val pages = mutableListOf<List<PageLine>>()
        var current = mutableListOf<PageLine>()
        var used = 0
        var lastBlock = -1
        for (line in lines) {
            val height = line.heightPx
                ?: if (line.isImage) settings.imageHeightPx else lineHeightOf(line)
            if (line.joinsPrevious && current.isNotEmpty()) {
                current.add(line)
                lastBlock = line.blockIndex
                continue
            }
            val gap = if (line.blockIndex != lastBlock && used > 0) blocks[line.blockIndex].gapBeforePx(settings.fontSizePx, settings.cssPx) else 0
            lastBlock = line.blockIndex
            // a picture that doesn't fit moves whole to the next page (foliate's break-inside: avoid)
            if (used > 0 && used + gap + height > pageHeightPx) {
                pages.add(current)
                current = mutableListOf()
                used = 0
            }
            used += gap + height
            current.add(line)
        }
        if (current.isNotEmpty()) pages.add(current)
        if (pages.isEmpty()) pages.add(emptyList())
        return pages
    }

    private fun lineHeightOf(line: PageLine): Int {
        val block = blocks.getOrNull(line.blockIndex) as? Block.Text ?: return settings.fontSizePx
        return lineHeightPxOf(block)
    }

    data class BlockPosition(val blockIndex: Int, val charOffset: Int, val quote: String)

    private companion object {
        const val QUOTE_LENGTH = 60
    }
}
