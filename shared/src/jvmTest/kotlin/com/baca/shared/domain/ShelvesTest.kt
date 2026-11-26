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
}
