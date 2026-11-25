package com.baca.shared.domain.reader

import com.baca.shared.parser.PdfWordBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PdfTextLayerTest {

    private val words = listOf(
        PdfWordBox("Hello", 0.10f, 0.10f, 0.20f, 0.14f),
        PdfWordBox("world", 0.22f, 0.10f, 0.34f, 0.14f),
        PdfWordBox("second", 0.10f, 0.20f, 0.25f, 0.24f),
        PdfWordBox("line", 0.27f, 0.20f, 0.36f, 0.24f),
    )

    @Test
    fun `finds word at normalized point and builds merged line rects`() {
        val idx = findPdfWordAt(words, 0.15f, 0.12f)
        assertEquals(0, idx)

        val (text, rects) = pdfSelectionRange(words, 0, 2)
        assertEquals("Hello world second", text)
        // Two lines -> 2 merged rects [x, y, w, h]
        assertEquals(2, rects.size)
        assertEquals(0.10f, rects[0][0], 0.001f)
        assertEquals(0.24f, rects[0][2], 0.001f) // 0.34 - 0.10
    }

    @Test
    fun `searches across pdf pages case-insensitively`() {
        val hits = searchPdfPages(pageCount = 2, query = "WORLD", wordsForPage = { p ->
            if (p == 1) words else emptyList()
        })
        assertEquals(1, hits.size)
        assertEquals(1, hits[0].pageIndex)
        assertTrue(hits[0].snippet.contains("world"))
        assertNotNull(hits[0].rects.firstOrNull())
    }
}
