package com.baca.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals

private data class FakeBook(val title: String, val series: String)

class LibrarySeriesTest {

    @Test
    fun `groups books by series case-insensitively and separates standalones`() {
        val books = listOf(
            FakeBook("Dune Messiah", "Dune"),
            FakeBook("Dune", "dune"),
            FakeBook("Frankenstein", ""),
            FakeBook("Foundation", "Foundation"),
        )

        val (series, standalones) = groupBySeries(books, seriesOf = { it.series }, titleOf = { it.title })
        assertEquals(listOf("Dune", "Foundation"), series.map { it.name })
        assertEquals(listOf("Dune", "Dune Messiah"), series[0].books.map { it.title })
        assertEquals(listOf("Frankenstein"), standalones.map { it.title })
    }
}
