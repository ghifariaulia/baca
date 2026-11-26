package com.baca.shared.domain

import com.baca.shared.domain.reader.PositionStore
import kotlin.test.Test
import kotlin.test.assertEquals

private class ShelfFakeStore : PositionStore {
    val local = mutableMapOf<String, String>()
    override val deviceId = "test"
    override fun loadPosition(bookHash: String): Locator? = null
    override fun savePosition(bookHash: String, locator: Locator) {}
    override fun getLocal(key: String): String? = local[key]
    override fun putLocal(key: String, value: String) { local[key] = value }
}

class ShelvesTest {

    @Test
    fun `creates, dedupes, and persists shelves and book memberships`() {
        val store = ShelfFakeStore()
        assertEquals(emptyList(), store.loadShelves())

        var shelves = addShelf(store.loadShelves(), "  Sci-Fi  ")
        shelves = addShelf(shelves, "sci-fi") // case-insensitive dedupe
        shelves = addShelf(shelves, "") // blank ignored
        shelves = addShelf(shelves, "Favorites")
        store.saveShelves(shelves)
        assertEquals(listOf("Sci-Fi", "Favorites"), store.loadShelves())

        var book1 = toggleBookShelf(store.bookShelves("h1"), "Sci-Fi")
        book1 = toggleBookShelf(book1, "Favorites")
        store.setBookShelves("h1", book1)
        assertEquals(listOf("Sci-Fi", "Favorites"), store.bookShelves("h1"))

        book1 = toggleBookShelf(store.bookShelves("h1"), "Sci-Fi")
        store.setBookShelves("h1", book1)
        assertEquals(listOf("Favorites"), store.bookShelves("h1"))
    }

    @Test
    fun `renames and deletes shelves across library and books`() {
        val store = ShelfFakeStore()
        store.saveShelves(listOf("Sci-Fi", "Favorites"))
        store.setBookShelves("h1", listOf("Sci-Fi", "Favorites"))
        store.setBookShelves("h2", listOf("Favorites"))

        store.renameShelfEverywhere("Sci-Fi", "Speculative", listOf("h1", "h2"))
        assertEquals(listOf("Speculative", "Favorites"), store.loadShelves())
        assertEquals(listOf("Speculative", "Favorites"), store.bookShelves("h1"))
        assertEquals(listOf("Favorites"), store.bookShelves("h2"))

        // Duplicate rename is ignored
        store.renameShelfEverywhere("Speculative", "favorites", listOf("h1", "h2"))
        assertEquals(listOf("Speculative", "Favorites"), store.loadShelves())

        store.deleteShelfEverywhere("Favorites", listOf("h1", "h2"))
        assertEquals(listOf("Speculative"), store.loadShelves())
        assertEquals(listOf("Speculative"), store.bookShelves("h1"))
        assertEquals(emptyList(), store.bookShelves("h2"))
    }
}
