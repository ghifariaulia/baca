package com.baca.app.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.baca.shared.domain.FixedLocator
import com.baca.shared.domain.Locator
import com.baca.shared.domain.reader.PositionStore
import com.baca.shared.parser.parseEpub
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FixedPageRenderTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `positioned fixed-layout EPUB page renders photo on the right and header text along the top in CbzScreen`() {
        val redJpg = ByteArrayOutputStream().also { out ->
            val img = BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.color = Color(200, 30, 30)
            g.fillRect(0, 0, 40, 40)
            g.dispose()
            ImageIO.write(img, "jpg", out)
        }.toByteArray()

        val head = """
            <meta content="width=1078, height=1446" name="viewport"/>
            <style>
            .PageContainer { background-color:#FFFFFF; }
            .s1 { border:0; margin:0; white-space:pre; position:absolute; display:block; }
            .l0 { line-height:1.00; }
            .fs8 { font-size:614.00px; }
            #ImageContainer010_00 { bottom:433.80px; right:0.00px; width:695.27px; height:1012.20px; position:absolute; }
            #TextContainer10 { height:100%; width:100%; position:absolute; transform-origin:left bottom; transform:scale(0.031257); }
            </style>
        """.trimIndent()
        val body = """
            <div class="PageContainer" id="Page" style="position:absolute;top:0px;left:0px;width:1078px;height:1446px;">
              <div id="ImageContainer010_00"><img alt="bg" height="1012" src="images/bg010_00.jpg" style="position:absolute" width="695"/></div>
              <div id="TextContainer10">
                <span class="s1 fs8 l0" style="left:3084.1px;bottom:43114.4px;color:rgb(20,20,20);">10 | COOKING WITH JOAN ROCA</span>
              </div>
            </div>
        """.trimIndent()

        val epubBytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                fun put(name: String, bytes: ByteArray) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
                put("mimetype", "application/epub+zip".encodeToByteArray())
                put("META-INF/container.xml", """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/c.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""".encodeToByteArray())
                put(
                    "OEBPS/c.opf",
                    """<package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>FXL</dc:title><meta property="rendition:layout">pre-paginated</meta></metadata><manifest><item id="p0" href="p0.xhtml" media-type="application/xhtml+xml"/><item id="img0" href="images/bg010_00.jpg" media-type="image/jpeg"/></manifest><spine><itemref idref="p0"/></spine></package>""".encodeToByteArray(),
                )
                put("OEBPS/p0.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"><head>$head</head><body>$body</body></html>""".encodeToByteArray())
                put("OEBPS/images/bg010_00.jpg", redJpg)
            }
        }.toByteArray()

        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "fxl-render-${System.nanoTime()}.epub"
        FileSystem.SYSTEM.write(path) { write(epubBytes) }
        val parsed = parseEpub(path)
        val fixedBook = assertNotNull(parsed.fixedLayoutBook)

        rule.setContent {
            Box(Modifier.size(539.dp, 723.dp)) {
                CbzScreen(
                    book = fixedBook,
                    bookPath = path.toString(),
                    bookHash = "fxl-hash",
                    store = FixedRenderTestStore(),
                    onBack = {},
                )
            }
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Page 1 of 1").fetchSemanticsNodes().isNotEmpty() }
        // Hide the floating chrome bars so we inspect the pure page canvas
        rule.mainClock.advanceTimeBy(3_500)
        rule.waitForIdle()

        val pixels = rule.onRoot().captureToImage().toPixelMap()
        // Right half (x in 300..500, y in 150..400) has the red photo (r > 0.6, g < 0.3)
        var photoPixels = 0
        for (y in 150..400 step 5) {
            for (x in 300..500 step 5) {
                val c = pixels[x, y]
                if (c.red > 0.6f && c.green < 0.3f) photoPixels++
            }
        }
        assertTrue(photoPixels > 100, "Expected photo drawn on the right of the page, found $photoPixels red samples")

        // Top-left header area (x in 40..180, y in 30..60) has dark ink on a white page
        var inkPixels = 0
        for (y in 30..60) {
            for (x in 40..180) {
                val c = pixels[x, y]
                if (c.red < 0.4f && c.green < 0.4f && c.blue < 0.4f) inkPixels++
            }
        }
        assertTrue(inkPixels > 20, "Expected header text drawn near the top-left of the page, found $inkPixels ink pixels")
    }
}

private class FixedRenderTestStore(private val initialPage: Int = 0) : PositionStore {
    override val deviceId = "test"
    override fun loadPosition(bookHash: String): Locator? = null
    override fun savePosition(bookHash: String, locator: Locator) {}
    override fun loadFixedPosition(bookHash: String): FixedLocator = FixedLocator(initialPage, 0.0)
    override fun saveFixedPosition(bookHash: String, locator: FixedLocator) {}
    override fun getLocal(key: String): String? = null
    override fun putLocal(key: String, value: String) {}
}
