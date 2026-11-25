package com.baca.shared.parser

import okio.Path
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import javax.imageio.ImageIO

internal actual fun readPdfMeta(path: Path): Pair<Int, String?>? = runCatching {
    Loader.loadPDF(path.toFile()).use { doc ->
        val title = doc.documentInformation?.title?.trim()?.takeIf { it.isNotEmpty() }
        doc.numberOfPages to title
    }
}.getOrNull()

actual fun renderPdfPageBytes(path: Path, pageIndex: Int): ByteArray? = runCatching {
    Loader.loadPDF(path.toFile()).use { doc ->
        if (pageIndex !in 0 until doc.numberOfPages) return null
        val renderer = PDFRenderer(doc)
        val image = renderer.renderImageWithDPI(pageIndex, 144f, ImageType.RGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        out.toByteArray()
    }
}.getOrNull()

actual fun extractPdfPageText(path: Path, pageIndex: Int): List<PdfWordBox> = runCatching {
    Loader.loadPDF(path.toFile()).use { doc ->
        if (pageIndex !in 0 until doc.numberOfPages) return emptyList()
        val words = mutableListOf<PdfWordBox>()
        val stripper = object : PDFTextStripper() {
            init {
                sortByPosition = true
                startPage = pageIndex + 1
                endPage = pageIndex + 1
            }

            override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                val buf = StringBuilder()
                var minX = Float.MAX_VALUE
                var minY = Float.MAX_VALUE
                var maxX = 0f
                var maxY = 0f
                var pageW = 1f
                var pageH = 1f

                fun flush() {
                    val word = buf.toString().trim()
                    if (word.isNotEmpty() && maxX > minX && maxY > minY) {
                        words += PdfWordBox(
                            text = word,
                            left = (minX / pageW).coerceIn(0f, 1f),
                            top = (minY / pageH).coerceIn(0f, 1f),
                            right = (maxX / pageW).coerceIn(0f, 1f),
                            bottom = (maxY / pageH).coerceIn(0f, 1f),
                        )
                    }
                    buf.clear()
                    minX = Float.MAX_VALUE
                    minY = Float.MAX_VALUE
                    maxX = 0f
                    maxY = 0f
                }

                for (pos in textPositions) {
                    val ch = pos.unicode ?: ""
                    if (ch.isBlank()) {
                        flush()
                    } else {
                        pageW = pos.pageWidth.coerceAtLeast(1f)
                        pageH = pos.pageHeight.coerceAtLeast(1f)
                        val x0 = pos.xDirAdj
                        val x1 = x0 + pos.widthDirAdj
                        val y1 = pos.yDirAdj
                        val y0 = (y1 - pos.heightDir).coerceAtLeast(0f)
                        minX = minOf(minX, x0)
                        minY = minOf(minY, y0)
                        maxX = maxOf(maxX, x1)
                        maxY = maxOf(maxY, y1)
                        buf.append(ch)
                    }
                }
                flush()
            }
        }
        stripper.writeText(doc, StringWriter())
        words
    }
}.getOrElse { emptyList() }
