package com.baca.shared.render

import com.baca.shared.parser.parseEpub
import okio.Path.Companion.toPath
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * M5 performance check: reader open time for a real-world EPUB —
 * parse + full-book pagination (what the first open pays).
 */
class ReaderOpenPerfTest {

    private lateinit var book: com.baca.shared.parser.ParsedEpub

    @Test
    fun frankensteinOpensFast() {
        val path = "src/jvmTest/resources/books/frankenstein.epub".toPath()
        val parseMs = measureTimeMillis { book = parseEpub(path) }
        println("PERF parse = ${parseMs}ms (${book.chapters.size} chapters)")

        // full-book pagination with grid metrics (deterministic, no Compose):
        // same total-work shape as the real TextMeasurer pass
        val metrics = GridMetrics()
        val settings = ReflowSettings(fontSizePx = 38f, lineHeightRatio = 1.7f)
        val total = book.chapters.sumOf { chapter ->
            val layoutMs = measureTimeMillis {
                ReflowLayout(chapter.blocks, metrics, settings, columnWidthPx = 648, pageHeightPx = 500)
            }
            layoutMs
        }
        println("PERF full-book pagination = ${total}ms")
        assertTrue(total < 10_000, "pagination should stay interactive, was ${total}ms")
    }
}
