package com.baca.shared.parser

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
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

actual fun extractPdfPageText(path: Path, pageIndex: Int): List<PdfWordBox> = runCatching {
    if (Build.VERSION.SDK_INT < 35) return emptyList()
    ParcelFileDescriptor.open(path.toFile(), ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            if (pageIndex !in 0 until renderer.pageCount) return emptyList()
            renderer.openPage(pageIndex).use { page ->
                val pw = page.width.toFloat().coerceAtLeast(1f)
                val ph = page.height.toFloat().coerceAtLeast(1f)
                page.getTextContents().flatMap { block ->
                    val bounds = block.bounds
                    val words = block.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                    if (words.isEmpty() || bounds.isEmpty()) emptyList()
                    else if (bounds.size == words.size) {
                        words.zip(bounds).map { (w, r) ->
                            PdfWordBox(
                                text = w,
                                left = (r.left / pw).coerceIn(0f, 1f),
                                top = (r.top / ph).coerceIn(0f, 1f),
                                right = (r.right / pw).coerceIn(0f, 1f),
                                bottom = (r.bottom / ph).coerceIn(0f, 1f),
                            )
                        }
                    } else {
                        val r = bounds.first()
                        listOf(
                            PdfWordBox(
                                text = words.joinToString(" "),
                                left = (r.left / pw).coerceIn(0f, 1f),
                                top = (r.top / ph).coerceIn(0f, 1f),
                                right = (r.right / pw).coerceIn(0f, 1f),
                                bottom = (r.bottom / ph).coerceIn(0f, 1f),
                            ),
                        )
                    }
                }
            }
        }
    }
}.getOrElse { emptyList() }
