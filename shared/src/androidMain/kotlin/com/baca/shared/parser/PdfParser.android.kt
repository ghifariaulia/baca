package com.baca.shared.parser

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import okio.Path
import java.io.ByteArrayOutputStream

private const val MAX_PDF_PAGE_PX = 2400

internal actual fun readPdfMeta(path: Path): Pair<Int, String?>? = runCatching {
    ParcelFileDescriptor.open(path.toFile(), ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { it.pageCount to null }
    }
}.getOrNull()

actual fun renderPdfPageBytes(path: Path, pageIndex: Int): ByteArray? = runCatching {
    ParcelFileDescriptor.open(path.toFile(), ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            if (pageIndex !in 0 until renderer.pageCount) return null
            renderer.openPage(pageIndex).use { page ->
                // Render at 2x points (144 DPI), capped at MAX_PDF_PAGE_PX on the longest edge
                val longest = maxOf(page.width, page.height).coerceAtLeast(1)
                val scale = (2f).coerceAtMost(MAX_PDF_PAGE_PX.toFloat() / longest)
                val width = (page.width * scale).toInt().coerceAtLeast(1)
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val out = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.toByteArray()
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }
}.getOrNull()
