package com.baca.shared.parser

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds a tiny EPUB fixture with known, small chapters so tests assert exact
 * block lists and offsets. chapterHtml entries become chapters in spine order.
 */
fun tinyEpub(
    title: String = "Fixture Book",
    author: String = "Fixture Author",
    images: Map<String, ByteArray> = emptyMap(),
    chapterDir: String = "OEBPS",
    /** Where nav.xhtml/toc.ncx live; links are relative to this dir. */
    navDir: String = "OEBPS",
    vararg chapterHtml: String,
): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        zip.putNextEntry(ZipEntry("mimetype"))
        zip.write("application/epub+zip".toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("META-INF/container.xml"))
        zip.write(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
            """.trimIndent().toByteArray(StandardCharsets.UTF_8),
        )
        zip.closeEntry()

        // Interpolated values can be multi-line, so build these documents
        // with explicit joins instead of trimIndent (interpolation would
        // zero the common indent and trimIndent would trim nothing).
        val hrefPrefix = chapterDir.removePrefix("OEBPS").trimStart('/')
        val chapterHref = { i: Int ->
            if (hrefPrefix.isEmpty()) "ch$i.xhtml" else "$hrefPrefix/ch$i.xhtml"
        }
        val navPrefix = navDir.removePrefix("OEBPS").trimStart('/')
        val navHref = { name: String -> if (navPrefix.isEmpty()) name else "$navPrefix/$name" }
        val navRelativeHref = { i: Int ->
            val navPrefix = navDir.removePrefix("OEBPS").trimStart('/')
            if (navPrefix.isEmpty()) chapterHref(i)
            else "../" + chapterHref(i)  // nav one level below OEBPS
        }
        val manifestItems = chapterHtml.indices.joinToString("\n") { i ->
            """<item id="c$i" href="${chapterHref(i)}" media-type="application/xhtml+xml"/>"""
        } +
            "\n" +
            """<item id="nav" href="${navHref("nav.xhtml")}" media-type="application/xhtml+xml" properties="nav"/>""" +
            "\n" +
            """<item id="ncx" href="${navHref("toc.ncx")}" media-type="application/x-dtbncx+xml"/>"""
        val spineRefs = chapterHtml.indices.joinToString("\n") { i -> """<itemref idref="c$i"/>""" }
        val opfXml = listOf(
            """<?xml version="1.0" encoding="UTF-8"?>""",
            """<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">""",
            """  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">""",
            """    <dc:title>$title</dc:title>""",
            """    <dc:creator>$author</dc:creator>""",
            """  </metadata>""",
            """  <manifest>""",
            manifestItems,
            """  </manifest>""",
            """  <spine>""",
            spineRefs,
            """  </spine>""",
            """</package>""",
        ).joinToString("\n")

        val tocNav = (
            listOf(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\">",
                "  <body><nav epub:type=\"toc\"><ol>",
            ) + chapterHtml.indices.map { i ->
                "    <li><a href=\"${navRelativeHref(i)}\">Title ${i + 1}</a></li>"
            } + listOf(
                "  </ol></nav>",
                // EPUB3 books also carry a page-list nav; it must not leak
                // into the contents list (the "Strange Pictures" bug)
                "  <nav epub:type=\"page-list\"><ol><li><a href=\"${navRelativeHref(0)}\">1</a></li><li><a href=\"${navRelativeHref(1)}\">2</a></li></ol></nav>",
                "  <nav epub:type=\"landmarks\"><ol><li><a href=\"${navRelativeHref(0)}\">Start</a></li></ol></nav>",
                "</body>",
                "</html>",
            )
            ).joinToString("\n")
        val tocNcx = (
            listOf(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\">",
                "  <head/><docTitle><text>t</text></docTitle><navMap>",
            ) + chapterHtml.indices.map { i ->
                "    <navPoint id=\"n${i}\"><navLabel><text>Title ${i + 1}</text></navLabel><content src=\"${navRelativeHref(i)}\"/></navPoint>"
            } + listOf(
                "  </navMap></ncx>",
            )
            ).joinToString("\n")

        zip.putNextEntry(ZipEntry("$navDir/nav.xhtml"))
        zip.write(tocNav.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
        zip.putNextEntry(ZipEntry("$navDir/toc.ncx"))
        zip.write(tocNcx.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()

        zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
        zip.write(opfXml.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()

        images.forEach { (path, bytes) ->
            zip.putNextEntry(ZipEntry(path))
            zip.write(bytes)
            zip.closeEntry()
        }

        chapterHtml.forEachIndexed { i, html ->
            val xhtml = listOf(
                """<?xml version="1.0" encoding="UTF-8"?>""",
                """<html xmlns="http://www.w3.org/1999/xhtml">""",
                """  <body>""",
                html,
                """  </body>""",
                """</html>""",
            ).joinToString("\n")
            zip.putNextEntry(ZipEntry("$chapterDir/ch$i.xhtml"))
            zip.write(xhtml.toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

fun xhtmlOf(vararg bodyLines: String): String = bodyLines.joinToString("\n")

/** Chapters-only fixture with default title/author and no extra images. */
fun tinyEpubOf(vararg chapterHtml: String): ByteArray =
    tinyEpub("Fixture Book", "Fixture Author", chapterHtml = chapterHtml)

fun tinyEpub(title: String, author: String, vararg chapterHtml: String): ByteArray =
    tinyEpub(title, author, images = emptyMap(), chapterHtml = chapterHtml)
