package com.baca.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnnotationExportTest {

    @Test
    fun `formats highlights and notes grouped by book and chapter in Markdown`() {
        val entries = listOf(
            AnnotationEntry(
                id = "1",
                kind = "highlight",
                reflowAnchor = ReflowAnchorDto("OEBPS/ch1.xhtml", PointDto(1, 0), PointDto(1, 10), "Line one\nLine two", page = 12),
                text = "Line one\nLine two",
                note = "Important point",
                color = "yellow",
                createdAt = 1L,
                lamport = 1L,
            ),
            AnnotationEntry(
                id = "2",
                kind = "bookmark",
                reflowAnchor = ReflowAnchorDto("OEBPS/ch1.xhtml", PointDto(2, 0), PointDto(2, 0), "Bookmark", page = 14),
                text = "",
                createdAt = 2L,
                lamport = 2L,
            ),
            AnnotationEntry(
                id = "3",
                kind = "highlight",
                reflowAnchor = ReflowAnchorDto("OEBPS/ch2.xhtml", PointDto(0, 0), PointDto(0, 5), "Deleted", page = 20),
                text = "Deleted",
                createdAt = 3L,
                lamport = 3L,
                deleted = true,
            ),
        )

        val md = formatAnnotationsMarkdown(listOf(ExportBook("Frankenstein", "Mary Shelley", entries)))
        assertTrue(md.contains("# Frankenstein — Mary Shelley"))
        assertTrue(md.contains("## OEBPS/ch1.xhtml"))
        assertTrue(md.contains("> Line one\n> Line two"))
        assertTrue(md.contains("Important point"))
        assertFalse(md.contains("Deleted"))
        assertFalse(md.contains("Bookmark"))
    }

    @Test
    fun `returns empty string when no exportable annotations exist`() {
        assertEquals("", formatAnnotationsMarkdown(listOf(ExportBook("Empty", "", emptyList()))))
    }
}
