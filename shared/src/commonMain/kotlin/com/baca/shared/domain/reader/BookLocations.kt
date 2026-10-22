package com.baca.shared.domain.reader

import com.baca.shared.parser.Chapter
import com.baca.shared.parser.TocEntry
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Places in a book as LibreraX numbers its pages (foliate's locations): every 1500 bytes
 * of the book's documents is one, whatever the font or margins. A document counts for
 * at least one; one out of the reading order for none. Chapters start where the
 * contents' entries do, at its top two levels.
 */
class BookLocations(chapters: List<Chapter>, toc: List<TocEntry>) {
    private val sizes = chapters.map { if (it.size > 0) maxOf(it.size, BYTES_PER_LOCATION) else 0 }
    private val sizeTotal = sizes.sum().toDouble()
    /** each chapter's start as a fraction of the book, and the book's end */
    private val starts = sizes.runningFold(0.0) { before, size -> before + size }.map { if (sizeTotal > 0) it / sizeTotal else 0.0 }

    /** How many locations the book has; 0 when its documents' sizes aren't known. */
    val total: Int = ceil(sizeTotal / BYTES_PER_LOCATION).toInt()

    /** Where each chapter starts, as a fraction of the book (the footer's ticks). */
    val chapterStarts: List<Float> = chapterStartsOf(chapters, toc)

    /** Where page [page] of the [pages] in [chapter] starts, as a fraction of the book. */
    fun place(chapter: Int, page: Int, pages: Int): Double =
        starts[chapter] + (starts[chapter + 1] - starts[chapter]) * page / pages.coerceAtLeast(1)

    /**
     * The chapter [fraction] of the way through the book falls in, and how far into it
     * (LibreraX's seeking): a chapter out of the reading order is passed over.
     */
    fun chapterAt(fraction: Double): Pair<Int, Double> {
        val last = sizes.indexOfLast { it > 0 }
        for (i in sizes.indices) {
            if (sizes[i] > 0 && (fraction < starts[i + 1] || i == last)) {
                return i to ((fraction - starts[i]) / (starts[i + 1] - starts[i])).coerceIn(0.0, 1.0)
            }
        }
        return 0 to 0.0
    }

    /** The page's 1-based location; the book's last page, ending at the end, shows the last one. */
    fun number(chapter: Int, page: Int, pages: Int): Int {
        if (place(chapter, page + 1, pages) >= maxOf(0.9, 1.0 - 1.5 / total)) return total
        return minOf(total - 1, floor(place(chapter, page, pages) * total).toInt()) + 1
    }

    /**
     * Locations from [place] to the next chapter's start (or the book's end), at least
     * one; null when the contents mark no chapters. In floats, as LibreraX reckons it.
     */
    fun pagesLeft(place: Double): Int? {
        if (chapterStarts.isEmpty() || total <= 0) return null
        val at = place.toFloat()
        val next = chapterStarts.firstOrNull { it > at + 0.5f / total } ?: 1f
        return ceil((next - at) * total - 0.01f).toInt().coerceAtLeast(1)
    }

    private fun chapterStartsOf(chapters: List<Chapter>, toc: List<TocEntry>): List<Float> {
        // each place once, at the shallowest level an entry there has
        val levels = mutableMapOf<Double, Int>()
        val indices = toc.map { indexOfChapter(it.href, chapters) }
        for ((entry, index) in toc.zip(indices)) {
            if (index < 0) continue
            // a document holding several entries places each by the share of its text before the entry's anchor
            val shared = indices.count { it == index } > 1
            val within = if (shared) entry.anchor?.let { chapters[index].anchors[it] } ?: 0f else 0f
            val f = starts[index] + (starts[index + 1] - starts[index]) * within
            if (!(f > 0 && f < 1)) continue
            val at = (f * 1e5).roundToLong() / 1e5
            levels[at] = minOf(entry.level, levels[at] ?: entry.level)
        }
        // the shallowest level with two chapters or more, and the one under it
        val top = levels.values.distinct().sorted().firstOrNull { level -> levels.values.count { it <= level } >= 2 }
        return levels.filterValues { top == null || it <= top + CHAPTER_LEVELS - 1 }.keys.sorted().map { it.toFloat() }
    }

    private companion object {
        const val BYTES_PER_LOCATION = 1500
        const val CHAPTER_LEVELS = 2
    }
}
