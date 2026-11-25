package com.baca.shared.domain.reader

import com.baca.shared.parser.PdfWordBox
import kotlin.math.abs

/** Finds the index of the word at normalized page coordinates ([nx], [ny]), or within a small hit margin. */
fun findPdfWordAt(words: List<PdfWordBox>, nx: Float, ny: Float, margin: Float = 0.02f): Int? {
    words.forEachIndexed { idx, w ->
        if (nx in w.left..w.right && ny in w.top..w.bottom) return idx
    }
    var bestIdx: Int? = null
    var bestDist = Float.MAX_VALUE
    words.forEachIndexed { idx, w ->
        if (nx in (w.left - margin)..(w.right + margin) && ny in (w.top - margin)..(w.bottom + margin)) {
            val cx = (w.left + w.right) * 0.5f
            val cy = (w.top + w.bottom) * 0.5f
            val dist = abs(nx - cx) + abs(ny - cy)
            if (dist < bestDist) {
                bestDist = dist
                bestIdx = idx
            }
        }
    }
    return bestIdx
}

/** Returns joined text and merged `[x, y, w, h]` rects for words in `[startIndex..endIndex]`. */
fun pdfSelectionRange(words: List<PdfWordBox>, startIndex: Int, endIndex: Int): Pair<String, List<List<Float>>> {
    if (words.isEmpty()) return "" to emptyList()
    val lo = minOf(startIndex, endIndex).coerceIn(0, words.lastIndex)
    val hi = maxOf(startIndex, endIndex).coerceIn(0, words.lastIndex)
    val slice = words.subList(lo, hi + 1)
    val text = slice.joinToString(" ") { it.text }
    val rects = mergeLineBoxes(slice)
    return text to rects
}

private fun mergeLineBoxes(slice: List<PdfWordBox>): List<List<Float>> {
    if (slice.isEmpty()) return emptyList()
    val merged = mutableListOf<List<Float>>()
    var curL = slice[0].left
    var curT = slice[0].top
    var curR = slice[0].right
    var curB = slice[0].bottom
    for (i in 1 until slice.size) {
        val w = slice[i]
        val lineH = (curB - curT).coerceAtLeast(0.01f)
        val sameLine = abs(w.top - curT) <= lineH * 0.6f && w.left >= curL - 0.02f
        if (sameLine) {
            curL = minOf(curL, w.left)
            curT = minOf(curT, w.top)
            curR = maxOf(curR, w.right)
            curB = maxOf(curB, w.bottom)
        } else {
            merged += listOf(curL, curT, (curR - curL).coerceAtLeast(0f), (curB - curT).coerceAtLeast(0f))
            curL = w.left
            curT = w.top
            curR = w.right
            curB = w.bottom
        }
    }
    merged += listOf(curL, curT, (curR - curL).coerceAtLeast(0f), (curB - curT).coerceAtLeast(0f))
    return merged
}

data class PdfSearchHit(
    val pageIndex: Int,
    val snippet: String,
    val rects: List<List<Float>>,
)

/** Searches [pageCount] PDF pages case-insensitively for [query] using [wordsForPage]. */
fun searchPdfPages(
    pageCount: Int,
    query: String,
    wordsForPage: (Int) -> List<PdfWordBox>,
    limit: Int = 100,
): List<PdfSearchHit> {
    val q = query.trim()
    if (q.isEmpty() || pageCount <= 0) return emptyList()
    val hits = mutableListOf<PdfSearchHit>()
    for (page in 0 until pageCount) {
        if (hits.size >= limit) break
        val words = wordsForPage(page)
        words.forEachIndexed { idx, w ->
            if (hits.size >= limit) return@forEachIndexed
            if (w.text.contains(q, ignoreCase = true)) {
                val from = (idx - 3).coerceAtLeast(0)
                val to = (idx + 3).coerceAtMost(words.lastIndex)
                val snippet = words.subList(from, to + 1).joinToString(" ") { it.text }
                hits += PdfSearchHit(page, snippet, mergeLineBoxes(listOf(w)))
            }
        }
    }
    return hits
}
