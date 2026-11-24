package com.baca.shared.domain.reader

import com.baca.shared.domain.FixedLocator

/**
 * Fixed-layout session (CBZ, TECH 9.1): no pagination engine — the page index
 * is the position (TECH 5.2). Pure, Compose-free; the UI observes [onChange].
 */
class CbzSession(
    val bookHash: String,
    private val pageCount: Int,
    private val repo: PositionStore,
    private val onChange: () -> Unit = {},
    /** Spine document hrefs for a fixed-layout EPUB, so a position saved when it was reflowed maps to its page index. */
    private val pageHrefs: List<String> = emptyList(),
) {
    var pageIndex: Int = 0
        private set
    private var initialized = false

    /** First call restores the saved page; later calls are no-ops. */
    suspend fun ensureView() {
        if (initialized) return
        initialized = true
        val saved = repo.loadFixedPosition(bookHash)
        val migratedPage = if (saved == null && pageHrefs.isNotEmpty()) {
            repo.loadPosition(bookHash)?.href?.let { href -> pageHrefs.indexOf(href).takeIf { it >= 0 } }
        } else {
            null
        }
        pageIndex = (saved?.pageIndex ?: migratedPage ?: 0).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        onChange()
    }

    suspend fun goToPage(target: Int) {
        val clamped = target.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        if (clamped == pageIndex) return
        pageIndex = clamped
        repo.saveFixedPosition(bookHash, currentLocator())
        onChange()
    }

    suspend fun turnPage(delta: Int) = goToPage(pageIndex + delta)

    /** Fraction 0..1 across the book; 0 for single-page books. */
    fun progress(): Double =
        if (pageCount <= 1) 0.0 else pageIndex.toDouble() / (pageCount - 1)

    fun currentLocator(): FixedLocator =
        FixedLocator(pageIndex = pageIndex, progression = progress())
}
