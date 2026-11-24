package com.baca.shared.parser

import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Tiny EPUBs for tests whose package says [epub]'s `meta`. */
internal object FixedLayoutPaths {
    /** A tiny EPUB whose package says [meta] and whose pages are [pages] (body markup). */
    fun epub(meta: String, pages: List<String>, head: String = ""): okio.Path {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, text: String) { zip.putNextEntry(ZipEntry(name)); zip.write(text.encodeToByteArray()); zip.closeEntry() }
            put("mimetype", "application/epub+zip")
            put("META-INF/container.xml", """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/c.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            put(
                "OEBPS/c.opf",
                """<package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>FXL</dc:title>$meta</metadata><manifest>""" +
                    pages.indices.joinToString("") { """<item id="p$it" href="p$it.xhtml" media-type="application/xhtml+xml"/>""" } +
                    """</manifest><spine>""" + pages.indices.joinToString("") { """<itemref idref="p$it"/>""" } + """</spine></package>""",
            )
            pages.forEachIndexed { i, body -> put("OEBPS/p$i.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"><head>$head</head><body>$body</body></html>""") }
        }
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "fxl-${System.nanoTime()}.epub"
        FileSystem.SYSTEM.write(path) { write(out.toByteArray()) }
        return path
    }
}

class FixedLayoutTest {

    private fun epub(meta: String, pages: List<String>, head: String = "") = FixedLayoutPaths.epub(meta, pages, head)

    private val picture = """<div><img src="images/a.jpg"/></div>"""
    private val svgPicture = """<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" viewBox="0 0 600 900"><image width="600" height="900" xlink:href="images/b.jpg"/></svg>"""

    @Test
    fun `a pre-paginated book of pictures is read page by page`() {
        val book = parseEpub(epub("""<meta property="rendition:layout">pre-paginated</meta>""", listOf(picture, svgPicture)))
        assertEquals(listOf("OEBPS/images/a.jpg", "OEBPS/images/b.jpg"), book.fixedPages)
    }

    @Test
    fun `the iBooks and Kobo fixed-layout option counts too`() {
        val book = parseEpub(epub("""<meta name="fixed-layout" content="true"/>""", listOf(picture)))
        assertEquals(listOf("OEBPS/images/a.jpg"), book.fixedPages)
    }

    @Test
    fun `a fixed-layout book with text on a page, or a reflowable one, is read reflowed`() {
        val fixed = """<meta property="rendition:layout">pre-paginated</meta>"""
        assertNull(parseEpub(epub(fixed, listOf(picture, "<p>Some text</p>"))).fixedPages)
        assertNull(parseEpub(epub("""<meta property="rendition:layout">reflowable</meta>""", listOf(picture))).fixedPages)
    }

    @Test
    fun `a book whose pages set a vertical writing mode is vertical, and turns pages right to left`() {
        val style = """<style>html, body { -epub-writing-mode: vertical-rl; writing-mode: vertical-rl }</style>"""
        val vertical = parseEpub(epub("", listOf("<p>縦書き</p>"), head = style))
        assertEquals(true, vertical.vertical)
        assertEquals(true, vertical.pageRtl)
        assertEquals(true, vertical.chapters.single().vertical)
        val horizontal = parseEpub(epub("", listOf("<p>横書き</p>")))
        assertEquals(false, horizontal.vertical)
        assertEquals(false, horizontal.pageRtl)
    }

    @Test
    fun `an absolute-positioned PDFTron page with transform scale is reconstructed into reflowable paragraphs and scaled sizes`() {
        val css = """
            <style>
            .s1 { border:0; margin:0; white-space:pre; position:absolute; display:block; }
            .fs8 { font-size:614.00px; }
            .fs1 { font-size:2000.00px; }
            .t0 { transform:scale(1.152000); }
            #TextContainer1 { position:absolute; transform:scale(0.031257); }
            </style>
        """.trimIndent()
        val body = """
            <div class="PageContainer" id="Page">
              <div id="TextContainer1">
                <div class="s1 t0" style="left:13985.2px;bottom:37552.6px;"><span class="s1 fs1" style="bottom:0px;letter-spacing:78.0px;">C</span></div>
                <div class="s1 t0" style="left:15690.2px;bottom:37552.6px;"><span class="s1 fs1" style="bottom:0px;letter-spacing:78.0px;">OOKING</span></div>
                <div class="s1 t0" style="left:26023.6px;bottom:37552.6px;"><span class="s1 fs1" style="bottom:0px;letter-spacing:78.0px;">WITH</span></div>
                <span class="s1 fs8" style="left:4717.3px;bottom:34950.0px;word-spacing:6.1px;">doors as a signature restaurant that would enable us to cook with free</span>
                <span class="s1 fs8" style="left:21896.6px;bottom:34950.5px;">-</span>
                <span class="s1 fs8" style="left:4717.3px;bottom:34134.0px;letter-spacing:1.8px;word-spacing:42.8px;">dom and respect for our culinary tradition.</span>
              </div>
            </div>
        """.trimIndent()
        val book = parseEpub(epub("""<meta property="rendition:layout">pre-paginated</meta>""", listOf(body), head = css))
        val texts = book.chapters.single().blocks.filterIsInstance<Block.Text>()
        assertEquals(
            listOf(
                "COOKING WITH",
                "doors as a signature restaurant that would enable us to cook with freedom and respect for our culinary tradition.",
            ),
            texts.map { it.text },
        )
        // letter-spacing 1.8px in a 614px font is ~0.0029em, not 1.8 / 16 = 0.1125em
        val bodySpan = texts[1].spans.last()
        kotlin.test.assertTrue(bodySpan.letterSpacing < 0.02f, "letterSpacing must be relative to the element's font size, was ${bodySpan.letterSpacing}")
        kotlin.test.assertTrue(bodySpan.wordSpacing < 0.2f, "wordSpacing must be relative to the element's font size, was ${bodySpan.wordSpacing}")
    }
}
