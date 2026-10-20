package com.baca.shared.domain.reader

import com.baca.shared.domain.FixedLocator
import com.baca.shared.domain.Locator

/**
 * Position + local-settings persistence behind the reader session. DB-backed
 * impl in the db module; fakes in tests.
 */
interface PositionStore {
    val deviceId: String
    fun loadPosition(bookHash: String): Locator?
    fun savePosition(bookHash: String, locator: Locator)

    /**
     * Position saved in another file of the same edition when it is newer
     * than this file's own; restore it by quote, not offsets. Null otherwise.
     */
    fun loadLinkedPosition(bookHash: String): Locator? = null

    /** Book-wide reading fraction (0..1) for library cards; local only. */
    fun saveBookProgress(bookHash: String, fraction: Float) {}

    /** Fixed-layout books (CBZ, TECH 5.2): page-index locator. */
    fun loadFixedPosition(bookHash: String): FixedLocator? = null
    fun saveFixedPosition(bookHash: String, locator: FixedLocator) {}

    fun getLocal(key: String): String?
    fun putLocal(key: String, value: String)
}
