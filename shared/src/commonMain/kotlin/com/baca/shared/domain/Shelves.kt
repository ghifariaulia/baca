package com.baca.shared.domain

import com.baca.shared.domain.reader.PositionStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val KEY_SHELVES = "library_shelves"
const val SHELF_BOOK_PREFIX = "shelves:"

private val shelvesJson = Json { ignoreUnknownKeys = true }

fun PositionStore.loadShelves(): List<String> =
    getLocal(KEY_SHELVES)?.let { runCatching { shelvesJson.decodeFromString<List<String>>(it) }.getOrNull() }.orEmpty()

fun PositionStore.saveShelves(shelves: List<String>) {
    putLocal(KEY_SHELVES, shelvesJson.encodeToString(shelves))
}

fun PositionStore.bookShelves(bookHash: String): List<String> =
    getLocal(SHELF_BOOK_PREFIX + bookHash)
        ?.let { runCatching { shelvesJson.decodeFromString<List<String>>(it) }.getOrNull() }
        .orEmpty()

fun PositionStore.setBookShelves(bookHash: String, shelves: List<String>) {
    putLocal(SHELF_BOOK_PREFIX + bookHash, shelvesJson.encodeToString(shelves))
}

fun addShelf(existing: List<String>, name: String): List<String> {
    val clean = name.trim()
    if (clean.isEmpty()) return existing
    if (existing.any { it.equals(clean, ignoreCase = true) }) return existing
    return existing + clean
}

fun toggleBookShelf(current: List<String>, shelf: String): List<String> =
    if (current.any { it.equals(shelf, ignoreCase = true) }) {
        current.filterNot { it.equals(shelf, ignoreCase = true) }
    } else {
        current + shelf
    }
