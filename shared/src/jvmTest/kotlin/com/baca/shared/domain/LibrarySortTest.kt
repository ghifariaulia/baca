package com.baca.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class LibrarySortTest {

    private val emma = SortFacts("Emma", "Jane Austen", addedAt = 3, lastReadAt = 10, progress = 0.2f)
    private val dracula = SortFacts("dracula", "Bram Stoker", addedAt = 1, lastReadAt = 30, progress = 0.9f)
    private val anon = SortFacts("Beowulf", "", addedAt = 4, lastReadAt = null, progress = null)
    private val moby = SortFacts("Moby-Dick", "Herman Melville", addedAt = 2, lastReadAt = 20, progress = 0.95f)
    private val books = listOf(emma, dracula, anon, moby)

    private fun titles(sort: LibrarySort) = sortLibrary(books, sort) { it }.map { it.title }

    @Test
    fun `date added puts the newest first`() {
        assertEquals(listOf("Beowulf", "Emma", "Moby-Dick", "dracula"), titles(LibrarySort.ADDED))
    }

    @Test
    fun `recently read puts the last opened first and unopened books last`() {
        assertEquals(listOf("dracula", "Moby-Dick", "Emma", "Beowulf"), titles(LibrarySort.RECENT))
    }

    @Test
    fun `title sorts A to Z ignoring case`() {
        assertEquals(listOf("Beowulf", "dracula", "Emma", "Moby-Dick"), titles(LibrarySort.TITLE))
    }

    @Test
    fun `author sorts A to Z with books that have no author last`() {
        assertEquals(listOf("dracula", "Moby-Dick", "Emma", "Beowulf"), titles(LibrarySort.AUTHOR))
    }

    @Test
    fun `progress puts the most read first and unopened books last`() {
        assertEquals(listOf("Moby-Dick", "dracula", "Emma", "Beowulf"), titles(LibrarySort.PROGRESS))
    }

    @Test
    fun `an unknown or missing saved order falls back to date added`() {
        assertEquals(LibrarySort.ADDED, LibrarySort.of(null))
        assertEquals(LibrarySort.ADDED, LibrarySort.of("shelf"))
        assertEquals(LibrarySort.TITLE, LibrarySort.of("title"))
    }
}
