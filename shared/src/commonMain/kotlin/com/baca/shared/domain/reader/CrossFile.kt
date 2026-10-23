package com.baca.shared.domain.reader

import com.baca.shared.parser.Chapter
import com.baca.shared.domain.AnnotationEntry
import com.baca.shared.domain.PointDto
import com.baca.shared.domain.ReflowAnchorDto

/**
 * Positions and annotations made in another file of the same edition (linked
 * by work key) are re-found by their quoted text. Chapter paths and block
 * numbering can differ between files, so their offsets may point at the wrong
 * text while still looking valid; they are never trusted here.
 */
data class TextPoint(val chapterIndex: Int, val blockIndex: Int, val charOffset: Int)

/** Where [quote] occurs, preferring the chapter at [preferHref]; null when absent or too short to be distinctive. */
fun findQuote(chapters: List<Chapter>, quote: String, preferHref: String?): TextPoint? {
    if (quote.length < MIN_QUOTE_LENGTH) return null
    val preferred = preferHref?.let { indexOfChapter(it, chapters) } ?: -1
    val order = listOfNotNull(preferred.takeIf { it >= 0 }) + chapters.indices.filter { it != preferred }
    for (ci in order) {
        chapters[ci].blocks.forEachIndexed { bi, block ->
            val at = block.text.indexOf(quote)
            if (at >= 0) return TextPoint(ci, bi, at)
        }
    }
    return null
}

/**
 * [entry] with its reflow anchor moved onto [chapters] by quote. When the
 * quote isn't found the anchor's href is cleared: nothing gets highlighted and
 * the card reads "can't locate", instead of marking unrelated text.
 */
fun relinkEntry(entry: AnnotationEntry, chapters: List<Chapter>): AnnotationEntry {
    val anchor = entry.reflowAnchor ?: return entry
    val hit = findQuote(chapters, anchor.quote, anchor.href)
        ?: return entry.copy(reflowAnchor = anchor.copy(href = ""))
    val shift = hit.charOffset - anchor.start.charOffset
    val end = if (anchor.end.blockIndex == anchor.start.blockIndex) {
        PointDto(hit.blockIndex, anchor.end.charOffset + shift)
    } else {
        PointDto(anchor.end.blockIndex + (hit.blockIndex - anchor.start.blockIndex), anchor.end.charOffset)
    }
    return entry.copy(
        reflowAnchor = ReflowAnchorDto(
            href = chapters[hit.chapterIndex].href,
            start = PointDto(hit.blockIndex, hit.charOffset),
            end = end,
            quote = anchor.quote,
            prefix = anchor.prefix,
            suffix = anchor.suffix,
        ),
    )
}

// short quotes ("I", "Chapter 1") match in the wrong place
private const val MIN_QUOTE_LENGTH = 12
