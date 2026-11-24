package com.baca.shared.parser

import okio.Path
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.ByteArrayOutputStream
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
