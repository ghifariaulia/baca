package com.baca.shared.domain.reader

import com.baca.shared.domain.Locator
import com.baca.shared.parser.Block
import com.baca.shared.parser.Chapter
import com.baca.shared.render.ReflowLayout
import com.baca.shared.render.ReflowSettings

/** Reading viewport in pixels: the text column size inside margins. */
data class Viewport(val columnWidthPx: Int, val pageHeightPx: Int)

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
) {
    var chapterIndex: Int = 0
        private set
    var pageIndex: Int = 0
        private set

    private var viewport = Viewport(0, 0)
    private var settings = ReflowSettings(0)
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
        val reflowAnchor = if (initialized) currentLocator() else null

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
                    chapterIndex = firstTextChapter()
                    pageIndex = 0
                }
            }
        } else {
            reflowAnchor?.let { restore(it) }
        }
        save()
        onChange()
    }

    suspend fun turnPage(delta: Int) {
        val count = layoutFor(chapterIndex).pageCount()
        var target = pageIndex + delta
        if (target < 0) {
            if (chapterIndex > 0) {
                chapterIndex--
                target = layoutFor(chapterIndex).pageCount() - 1
            } else {
                target = 0
            }
        } else if (target >= count) {
            if (chapterIndex < chapters.lastIndex) {
                chapterIndex++
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
        chapterIndex > 0 || pageIndex > 0
    } else {
        chapterIndex < chapters.lastIndex || pageIndex < layoutFor(chapterIndex).pageCount() - 1
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

    /** Pages after this one in the current chapter. */
    fun chapterPagesLeft(): Int =
        (layoutFor(chapterIndex).pageCount() - 1 - pageIndex).coerceAtLeast(0)

    /** Where each chapter after the first starts, as a fraction of the book's pages (footer ticks). */
    fun chapterStartFractions(): List<Float> {
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
     * Seeks to a 1-based book-wide page (progress bar). Does not commit the
     * position; call [commitPosition] when the gesture ends.
     */
    suspend fun jumpToBookPage(targetPage: Int) {
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

    fun progress(): Double {
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
        jumpToBookPage(1 + (locator.progression * (bookTotalPages() - 1)).toInt())
    }

    private fun firstTextChapter(): Int =
        chapters.indexOfFirst { ch -> ch.blocks.any { it is Block.Text } }.coerceAtLeast(0)

    private suspend fun save() {
        if (chapters.isEmpty()) return
        repo.savePosition(bookHash, currentLocator())
        // same fraction the reader footer shows (pagesBefore / (total - 1))
        val total = bookTotalPages()
        val before = (0 until chapterIndex).sumOf { layoutFor(it).pageCount() }
        if (total > 1) repo.saveBookProgress(bookHash, (before + pageIndex).toFloat() / (total - 1))
    }
}
