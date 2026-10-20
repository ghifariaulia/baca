package com.baca.shared.render

import com.baca.shared.parser.Block
import com.baca.shared.parser.BlockKind
import kotlin.math.ceil

/**
 * Pagination settings in pixels; the UI derives them from density, screen
 * size and the display settings screen (TECH 10.4).
 */
data class ReflowSettings(
    val fontSizePx: Int,
    val lineHeightRatio: Float = 1.7f,
    val paragraphGapPx: Int = 0,
    val headingScale: Float = 1.35f,
    val imageHeightPx: Int = 0,
    /** first-line indent of body paragraphs (not headings) */
    val paragraphIndentPx: Int = 0,
    /** "serif" | "sans" | "mono" | "libron" — must match the renderer's font. */
    val fontFamily: String? = null,
)

/** One visible line (or image placeholder) placed on a page. */
data class PageLine(
    val blockIndex: Int,
    val start: Int,
    val endExclusive: Int,
    val isImage: Boolean = false,
    /** resolved height for image lines; text lines derive it from style */
    val heightPx: Int? = null,
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
            val line = lines.firstOrNull {
                it.blockIndex > blockIndex ||
                    (it.blockIndex == blockIndex && it.start >= charOffset)
            }
            if (line != null) {
                return AnchorRestore(pageIndexOf(line), AnchorResolution.OFFSETS)
            }
        }

        if (quote.isNotBlank()) {
            val blockIdx = blocks.indexOfFirst { it.text.contains(quote) }
            if (blockIdx >= 0) {
                val offset = blocks[blockIdx].text.indexOf(quote)
                val line = lines.firstOrNull {
                    it.blockIndex > blockIdx || (it.blockIndex == blockIdx && it.start >= offset)
                }
                if (line != null) {
                    return AnchorRestore(pageIndexOf(line), AnchorResolution.QUOTE)
                }
            }
        }

        return null
    }

    // match by position: packing may resize image lines, so equality won't do
    private fun pageIndexOf(line: PageLine): Int = pages.indexOfFirst { page ->
        page.any { it.blockIndex == line.blockIndex && it.start == line.start }
    }

    private fun fontPxOf(block: Block.Text): Int = when (block.kind) {
        BlockKind.HEADING -> (settings.fontSizePx * settings.headingScale).toInt()
        else -> settings.fontSizePx
    }

    // round up: the renderer draws the exact fractional height, so rounding
    // down would let a full page overflow and clip its last line
    private fun lineHeightPxOf(block: Block.Text): Int = when (block.kind) {
        BlockKind.HEADING -> ceil(fontPxOf(block) * settings.lineHeightRatio).toInt()
        else -> ceil(settings.fontSizePx * settings.lineHeightRatio).toInt()
    }

    private fun buildLines(): List<PageLine> {
        val result = mutableListOf<PageLine>()
        blocks.forEachIndexed { blockIndex, block ->
            when (block) {
                is Block.Image -> result.add(
                    PageLine(blockIndex, 0, 0, isImage = true, heightPx = imageHeight(block)),
                )
                is Block.Text -> {
                    if (block.text.isEmpty()) return@forEachIndexed
                    // measure with the renderer's exact style (family + per-span
                    // bold/italic) so breaks match the drawn lines; headings
                    // render bold, so they measure bold
                    val headingBold = block.kind == BlockKind.HEADING
                    val styleRanges = buildList {
                        var offset = 0
                        block.spans.forEach { span ->
                            add(
                                TextMetrics.StyleRange(
                                    offset,
                                    offset + span.text.length,
                                    span.bold || headingBold,
                                    span.italic,
                                ),
                            )
                            offset += span.text.length
                        }
                    }
                    metrics
                        .layoutLines(
                            block.text,
                            fontPxOf(block),
                            lineHeightPxOf(block),
                            columnWidthPx,
                            settings.fontFamily,
                            styleRanges,
                            if (block.kind == BlockKind.HEADING) 0 else settings.paragraphIndentPx,
                        )
                        .filter { it.endExclusive > it.start }
                        .forEach { span ->
                            result.add(PageLine(blockIndex, span.start, span.endExclusive))
                        }
                }
            }
        }
        return result
    }

    private fun packPages(): List<List<PageLine>> {
        val pages = mutableListOf<List<PageLine>>()
        var current = mutableListOf<PageLine>()
        var used = 0
        var lastBlock = -1
        for (original in lines) {
            var line = original
            var height = line.heightPx
                ?: if (line.isImage) settings.imageHeightPx else lineHeightOf(line)
            val gap = if (line.blockIndex != lastBlock && used > 0) settings.paragraphGapPx else 0
            lastBlock = line.blockIndex
            val left = pageHeightPx - used - gap
            // an image pushed whole to the next page leaves the rest of this
            // one blank; scale it into the leftover space when that's not tiny
            if (line.isImage && used > 0 && height > left && left >= height * MIN_IMAGE_SCALE) {
                height = left
                line = line.copy(heightPx = left)
            }
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
        const val MIN_IMAGE_SCALE = 0.5f
    }
}
