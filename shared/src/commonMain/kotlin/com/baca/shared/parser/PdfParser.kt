package com.baca.shared.parser

import okio.Path

/**
 * Fixed-layout PDF metadata and on-demand page image renderer (Android `PdfRenderer` / Desktop Apache PDFBox).
 * Pages are named `"pdf:0"`..`"pdf:${pageCount - 1}"` in [CbzBook.pages].
 */
const val PDF_PAGE_PREFIX = "pdf:"

fun parsePdf(path: Path, fileName: String): CbzBook? {
    val (count, rawTitle) = readPdfMeta(path) ?: return null
    if (count <= 0) return null
    val title = rawTitle?.takeIf { it.isNotBlank() } ?: fileName.substringBeforeLast('.')
    val pages = (0 until count).map { "$PDF_PAGE_PREFIX$it" }
    return CbzBook(title = title, pages = pages)
}

internal expect fun readPdfMeta(path: Path): Pair<Int, String?>?

/** Renders 0-based [pageIndex] of the PDF at [path] into PNG bytes; null if unreadable. */
expect fun renderPdfPageBytes(path: Path, pageIndex: Int): ByteArray?
