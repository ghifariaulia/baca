package com.baca.shared.domain.reader

import com.baca.shared.domain.Locator
import com.baca.shared.parser.Chapter
import com.baca.shared.parser.TocEntry
import com.baca.shared.render.ReflowLayout
import com.baca.shared.render.ReflowSettings
import kotlin.math.ceil

/** Reading viewport in pixels: the text column size inside margins. */
data class Viewport(
    val columnWidthPx: Int,
    val pageHeightPx: Int,
    /** the band above (and below) the text, which pictures are held short of twice */
    val marginPx: Float = 0f,
    /** the reader's whole window, LibreraX's WebView: what a linked stylesheet's vw and vh are of */
    val windowWidthPx: Int = 0,
    val windowHeightPx: Int = 0,
    /** the room for text before whole lines are fitted to it; [pageHeightPx] is it less the running text's even lines */
    val textHeightPx: Float = pageHeightPx.toFloat(),
)

/**
 * Reader state machine: current chapter + page, position persistence and
 * reflow restoration. Pure — Compose-free; the UI observes [onChange].
 *
 * Reflowable books only; fixed-layout books (CBZ) get a separate session in
 * their own step.
 */

/** Tolerant href match shared by session jumps and the contents sheet. */
public fun indexOfChapter(href: String, chapters: List<Chapter>): Int {
    val key = hrefKey(href)
    chapters.forEachIndexed { i, c ->
        if (c.href == href || hrefKey(c.href) == key) return i
    }
    return -1
}

private fun hrefKey(href: String): String =
    href.substringBefore('#').substringBefore('?').trim().lowercase()

class ReaderSession(
    val bookHash: String,
    private val chapters: List<Chapter>,
    private val repo: PositionStore,
    private val layoutFactory: (chapterIndex: Int, viewport: Viewport, settings: ReflowSettings) -> ReflowLayout,
    private val onChange: () -> Unit,
    toc: List<TocEntry> = emptyList(),
    /** the chapter a new book opens at ([com.baca.shared.parser.ParsedEpub.textStart]) */
    private val textStart: String? = null,
) {
    // the book's places as LibreraX numbers them, where its documents' sizes are known (EPUB)
    private val locations = BookLocations(chapters, toc)

    var chapterIndex: Int = 0
        private set
    var pageIndex: Int = 0
        private set

    private var viewport = Viewport(0, 0)
    private var settings = ReflowSettings(0f)
    private var initialized = false
    // a jump asked for before the first layout (Notes/Bookmarks screens open the reader with one)
    private var pendingJump: Triple<String, Int, Int>? = null
    private val layouts = mutableMapOf<Int, ReflowLayout>()

    fun layoutFor(index: Int): ReflowLayout =
        layouts.getOrPut(index) { layoutFactory(index, viewport, settings) }

    /**
     * First call opens the book (loads the saved position); later calls with a
     * changed viewport/settings reflow and restore the position.
     */
    suspend fun ensureView(viewport: Viewport, settings: ReflowSettings) {
        if (initialized && viewport == this.viewport && settings == this.settings) return
        // the place being read: kept through every reflow until the reader moves on, so
        // changing the font back and forth never creeps back to an earlier page start
        val reflowAnchor = if (initialized) readingAnchor ?: currentLocator() else null

        this.viewport = viewport
        this.settings = settings
        layouts.clear()
        cachedTotalPages = null

        if (!initialized) {
            initialized = true
            val linked = repo.loadLinkedPosition(bookHash)
            val saved = repo.loadPosition(bookHash)
            val jump = pendingJump
            pendingJump = null
            when {
                jump != null && moveToAnchor(jump.first, jump.second, jump.third) -> {}
                linked != null -> restoreFromOtherFile(linked)
                saved != null -> restore(saved)
                else -> {
                    chapterIndex = startChapter()
                    pageIndex = 0
                }
            }
        } else {
            reflowAnchor?.let { restore(it) }
        }
        readingAnchor = reflowAnchor
        save(reflowAnchor ?: currentLocator())
        onChange()
    }

    suspend fun turnPage(delta: Int) {
        val count = layoutFor(chapterIndex).pageCount()
        var target = pageIndex + delta
        if (target < 0) {
            val previous = adjacentChapter(chapterIndex, -1)
            if (previous != null) {
                chapterIndex = previous
                target = layoutFor(chapterIndex).pageCount() - 1
            } else {
                target = 0
            }
        } else if (target >= count) {
            val next = adjacentChapter(chapterIndex, 1)
            if (next != null) {
                chapterIndex = next
                target = 0
            } else {
                target = count - 1
            }
        }
        pageIndex = target.coerceIn(0, (layoutFor(chapterIndex).pageCount() - 1).coerceAtLeast(0))
        save()
        onChange()
    }

    /** False at the book's first page going back and its last page going forward. */
    fun canTurn(delta: Int): Boolean = if (delta < 0) {
        adjacentChapter(chapterIndex, -1) != null || pageIndex > 0
    } else {
        adjacentChapter(chapterIndex, 1) != null || pageIndex < layoutFor(chapterIndex).pageCount() - 1
    }

    /** The chapter a page turn in [dir] reaches from [from]: the next one in the reading order, past any out of it (`linear="no"`). */
    private fun adjacentChapter(from: Int, dir: Int): Int? {
        var index = from + dir
        while (index in chapters.indices) {
            if (chapters[index].linear) return index
            index += dir
        }
        return null
    }

    /**
     * Book-wide counting: chapter layouts all get built (they are cheap once
     * measured), so "Page 30 of 300" is exact, not estimated.
     */
    private fun buildAllPageCounts() {
        chapters.indices.forEach { layoutFor(it) }
    }

    private var cachedTotalPages: Int? = null

    /**
     * Book-wide page total; builds all chapter layouts once per viewport/settings
     * (the expensive pass) and caches — the footer calls this every recomposition.
     */
    fun bookTotalPages(): Int {
        cachedTotalPages?.let { return it }
        buildAllPageCounts()
        val total = chapters.indices.sumOf { layoutFor(it).pageCount() }
        cachedTotalPages = total
        return total
    }

    /** 1-based page position over the whole book. */
    fun bookPageNumber(): Int {
        buildAllPageCounts()
        val before = (0 until chapterIndex).sumOf { layoutFor(it).pageCount() }
        return before + (pageIndex + 1)
    }

    /** The footer's page number: the page's location ([BookLocations]), else its page in the book. */
    fun locationNumber(): Int =
        if (locations.total > 0) locations.number(chapterIndex, pageIndex, layoutFor(chapterIndex).pageCount()) else bookPageNumber()

    /** The footer's page total: the book's locations, else its pages. */
    fun locationTotal(): Int = if (locations.total > 0) locations.total else bookTotalPages()

    /** Where the page starts, as a fraction of the book, counted as [locationNumber] is (the footer's bar). */
    fun readingFraction(): Float {
        if (locations.total > 0) return place().toFloat()
        val total = bookTotalPages()
        return if (total <= 1) 0f else ((bookPageNumber() - 1) / (total - 1).toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Locations to the next chapter (the footer's "Pages left"), null when the contents
     * mark none; without locations, the pages after this one in the chapter.
     */
    fun chapterPagesLeft(): Int? =
        if (locations.total > 0) locations.pagesLeft(place())
        else (layoutFor(chapterIndex).pageCount() - 1 - pageIndex).coerceAtLeast(0)

    private fun place(): Double = locations.place(chapterIndex, pageIndex, layoutFor(chapterIndex).pageCount())

    /** Where each chapter after the first starts, as a fraction of the book (footer ticks). */
    fun chapterStartFractions(): List<Float> {
        if (locations.total > 0) return locations.chapterStarts
        val total = bookTotalPages()
        if (total <= 1) return emptyList()
        var cumulative = 0
        return chapters.indices.drop(1).map { i ->
            cumulative += layoutFor(i - 1).pageCount()
            cumulative / total.toFloat()
        }
    }

    /** Jumps to the start of a chapter (contents panel). */
    suspend fun jumpToChapter(target: Int) {
        if (target in chapters.indices) {
            chapterIndex = target
            pageIndex = 0
            save()
            onChange()
        }
    }

    /**
     * Seeks to [fraction] of the book (progress bar): with locations, the first page of the
     * chapter there that starts at or past it, as LibreraX seeks; else the page that far
     * through the book's pages. Does not commit the position; call [commitPosition] when
     * the gesture ends.
     */
    suspend fun jumpToFraction(fraction: Double) {
        if (chapters.isEmpty()) return
        if (locations.total <= 0) {
            jumpToBookPage(1 + (fraction * (bookTotalPages() - 1)).toInt())
            return
        }
        val (chapter, within) = locations.chapterAt(fraction)
        val pages = layoutFor(chapter).pageCount().coerceAtLeast(1)
        val page = ceil(within * pages - 1e-6).toInt()
        val next = adjacentChapter(chapter, 1)
        if (page >= pages && next != null) {
            chapterIndex = next
            pageIndex = 0
        } else {
            chapterIndex = chapter
            pageIndex = page.coerceAtMost(pages - 1)
        }
        onChange()
    }

    /** Seeks to a 1-based book-wide page, counting every chapter's pages. */
    internal suspend fun jumpToBookPage(targetPage: Int) {
        if (chapters.isEmpty()) return
        buildAllPageCounts()
        val target = targetPage.coerceIn(1, bookTotalPages())
        var cumulative = 0
        for (i in chapters.indices) {
            val count = layoutFor(i).pageCount()
            if (target <= cumulative + count || i == chapters.lastIndex) {
                chapterIndex = i
                pageIndex = (target - cumulative - 1).coerceIn(0, (count - 1).coerceAtLeast(0))
                onChange()
                return
            }
            cumulative += count
        }
    }

    suspend fun commitPosition() {
        if (chapters.isNotEmpty()) save()
    }

    /**
     * Internal links point at a chapter document's zip path. Matching is
     * tolerant (fragment/query stripped, case-insensitive fallback) because
     * toc hrefs and manifest paths disagree in some EPUBs.
     */
    suspend fun jumpToHref(href: String) {
        val target = indexOfChapter(href, chapters)
        if (target >= 0) jumpToChapter(target)
    }

    /** Jump to an annotation anchor (Notes list / contents panel). */
    suspend fun jumpToAnchor(href: String, blockIndex: Int, charOffset: Int) {
        if (!initialized) {
            // no layout yet: ensureView goes here instead of the saved position
            pendingJump = Triple(href, blockIndex, charOffset)
            return
        }
        if (!moveToAnchor(href, blockIndex, charOffset)) return
        save()
        onChange()
    }

    private fun moveToAnchor(href: String, blockIndex: Int, charOffset: Int): Boolean {
        val target = indexOfChapter(href, chapters)
        if (target < 0) return false
        chapterIndex = target
        pageIndex = layoutFor(target).pageForPosition(blockIndex, charOffset, "")?.pageIndex ?: 0
        return true
    }

    /** How far through the book the page is: its place by locations, else by pages. */
    fun progress(): Double {
        if (locations.total > 0) return place()
        val total = bookTotalPages()
        return if (total <= 1) 0.0 else (bookPageNumber() - 1).toDouble() / (total - 1)
    }

    fun currentLocator(): Locator {
        val layout = layoutFor(chapterIndex)
        val pos = layout.positionForPage(pageIndex)
        return Locator(
            href = chapters[chapterIndex].href,
            blockIndex = pos.blockIndex,
            charOffset = pos.charOffset,
            progression = progress(),
            quote = pos.quote,
        )
    }

    private suspend fun restore(locator: Locator) {
        val ci = chapters.indexOfFirst { it.href == locator.href }
        if (ci >= 0) {
            chapterIndex = ci
            val restore = layoutFor(ci).pageForPosition(locator.blockIndex, locator.charOffset, locator.quote)
            if (restore != null) {
                pageIndex = restore.pageIndex
                return
            }
        }
        chapterIndex = (locator.progression * chapters.size).toInt().coerceIn(0, chapters.lastIndex)
        pageIndex = 0
    }

    /** Quote first; otherwise the book-wide fraction ([currentLocator] saves progress()). */
    private suspend fun restoreFromOtherFile(locator: Locator) {
        val hit = findQuote(chapters, locator.quote, locator.href)
        if (hit != null) {
            chapterIndex = hit.chapterIndex
            pageIndex = layoutFor(hit.chapterIndex).pageForPosition(hit.blockIndex, hit.charOffset, locator.quote)
                ?.pageIndex ?: 0
            return
        }
        jumpToFraction(locator.progression)
    }

    /** Where a new book opens, as foliate's `goToTextStart`: the start of the text, else the first chapter in the reading order. */
    private fun startChapter(): Int =
        textStart?.let { indexOfChapter(it, chapters) }?.takeIf { it >= 0 } ?: chapters.indexOfFirst { it.linear }.coerceAtLeast(0)

    /** Where the reader is: set by a reflow, cleared by any move ([save] without a locator). */
    private var readingAnchor: Locator? = null

    private suspend fun save(locator: Locator? = null) {
        if (chapters.isEmpty()) return
        if (locator == null) readingAnchor = null
        repo.savePosition(bookHash, locator ?: currentLocator())
        // the reader's own fraction: by locations, or pagesBefore / (total - 1)
        if (locations.total > 0) {
            repo.saveBookProgress(bookHash, place().toFloat())
            return
        }
        val total = bookTotalPages()
        val before = (0 until chapterIndex).sumOf { layoutFor(it).pageCount() }
        if (total > 1) repo.saveBookProgress(bookHash, (before + pageIndex).toFloat() / (total - 1))
    }
}
