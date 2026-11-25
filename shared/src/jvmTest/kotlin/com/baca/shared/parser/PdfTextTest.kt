package com.baca.shared.parser

import okio.FileSystem
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfTextTest {

    @Test
    fun `extractPdfPageText returns normalized word boxes in reading order`() {
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "pdf-text-test.pdf"
        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 14f)
                cs.newLineAtOffset(72f, 700f)
                cs.showText("Hello Baca PDF")
                cs.endText()
            }
            doc.save(path.toFile())
        }

        val words = extractPdfPageText(path, 0)
        assertEquals(listOf("Hello", "Baca", "PDF"), words.map { it.text })
        words.forEach { w ->
            assertTrue(w.left in 0f..1f && w.right in 0f..1f && w.left < w.right)
            assertTrue(w.top in 0f..1f && w.bottom in 0f..1f && w.top < w.bottom)
        }
        assertTrue(words[0].right <= words[1].left + 0.02f)
        assertEquals(emptyList(), extractPdfPageText(path, 5))
    }
}
