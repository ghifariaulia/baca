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
    fun `a pre-paginated book with absolute-positioned text and images is parsed into FixedPages`() {
        val fontCss = """
            @font-face {
                font-style:normal;
                font-weight:normal;
                font-family:f5generic;
                src:url(fonts/f5.otf) format("opentype");
            }
        """.trimIndent()
        val head = """
            <meta content="width=1078, height=1446" name="viewport"/>
            <style>
            $fontCss
            .PageContainer { background-color:#FFFFFF; }
            .s1 { border:0; margin:0; white-space:pre; position:absolute; display:block; }
            .l5 { line-height:1.14; }
            .fs8 { font-size:614.00px; }
            .fs10 { font-size:783.00px; }
            .f5 { font-family:f5generic; }
            .t1 { transform:scale(0.784314, 1.215686); }
            .t4 { transform:matrix(0.000000, -1.000000, 1.000000, 0.000000, 0.000000, 0.000000); }
            #ImageContainer010_00 { bottom:433.80px; right:0.00px; width:695.27px; height:1012.20px; position:absolute; }
            #TextContainer10 { height:100%; width:100%; position:absolute; transform-origin:left bottom; transform:scale(0.031257); }
            </style>
        """.trimIndent()
        val body = """
            <div class="PageContainer" id="Page" style="position:absolute;top:0px;left:0px;width:1078px;height:1446px;">
              <div id="ImageContainer010_00"><img alt="bg" height="1012" src="images/bg010_00.jpg" style="position:absolute" width="695"/></div>
              <div id="TextContainer10">
                <span class="s1 f5 fs8 l5" style="left:3084.1px;bottom:43114.4px;color:rgb(35,31,31);">10 </span>
                <span class="s1 f5 fs10 l5 t1" style="left:3827.52px;bottom:43030px;color:rgb(35,31,31);">|</span>
                <span class="s1 f5 fs8 l5 t4" style="left:13568.2px;bottom:10135.2px;color:rgb(35,31,31);">50ºC</span>
                <span class="s1 f5 fs8 l5" style="left:1000px;bottom:1000px;color:transparent;">hidden</span>
              </div>
            </div>
        """.trimIndent()
        val book = parseEpub(epub("""<meta property="rendition:layout">pre-paginated</meta>""", listOf(body), head = head))
        val fixedBook = kotlin.test.assertNotNull(book.fixedLayoutBook)
        assertEquals(listOf("OEBPS/p0.xhtml"), fixedBook.pages)
        val page = kotlin.test.assertNotNull(fixedBook.fixedPages.single())
        assertEquals(1078f, page.widthPx)
        assertEquals(1446f, page.heightPx)

        val img = page.images.single()
        assertEquals("OEBPS/images/bg010_00.jpg", img.src)
        assertEquals(1078f - 695.27f, img.xPx, 0.1f)
        assertEquals(1446f - 433.80f - 1012.20f, img.yPx, 0.1f)
        assertEquals(695.27f, img.widthPx, 0.1f)
        assertEquals(1012.20f, img.heightPx, 0.1f)

        // Transparent text is skipped, leaving the 3 visible spans
        assertEquals(3, page.texts.size)
        val first = page.texts[0]
        assertEquals("10 ", first.text)
        assertEquals(96.4f, first.xPx, 0.1f)
        assertEquals(1446f - 1347.6f, first.bottomY, 0.1f)
        assertEquals(19.2f, first.fontSizePx, 0.1f)
        assertEquals("f5generic", first.family)
        assertEquals("OEBPS/fonts/f5.otf", fixedBook.fonts.single { it.family == first.family }.path)

        val condensed = page.texts[1]
        assertEquals("|", condensed.text)
        assertEquals(0.784314f, condensed.scaleX, 0.001f)
        assertEquals(1.215686f, condensed.scaleY, 0.001f)

        val rotated = page.texts[2]
        assertEquals("50ºC", rotated.text)
        assertEquals(-90f, rotated.rotationDeg, 0.1f)

        // A pre-paginated book with ordinary flowing text still reflows
        val flowing = parseEpub(epub("""<meta property="rendition:layout">pre-paginated</meta>""", listOf("<p>Plain flowing text</p>"), head = head))
        assertNull(flowing.fixedLayoutBook)
    }

    @Test
    fun `a reflow locator saved before fixed-layout support maps its href to the spine page index`() = kotlinx.coroutines.test.runTest {
        val hrefs = (1..12).map { if (it == 1) "OEBPS/cover.xhtml" else "OEBPS/page${it.toString().padStart(3, '0')}.xhtml" }
        val store = object : com.baca.shared.domain.reader.PositionStore {
            override val deviceId = "test"
            override fun loadPosition(bookHash: String) = com.baca.shared.domain.Locator(
                href = "OEBPS/page010.xhtml",
                blockIndex = 0,
                charOffset = 0,
                progression = 0.5,
                quote = "",
            )
            override fun savePosition(bookHash: String, locator: com.baca.shared.domain.Locator) {}
            override fun getLocal(key: String): String? = null
            override fun putLocal(key: String, value: String) {}
        }
        val session = com.baca.shared.domain.reader.CbzSession("hash", hrefs.size, store, pageHrefs = hrefs)
        session.ensureView()
        assertEquals(9, session.pageIndex)
    }
}
