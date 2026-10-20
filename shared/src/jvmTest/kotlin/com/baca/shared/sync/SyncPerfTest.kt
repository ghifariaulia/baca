package com.baca.shared.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.baca.shared.db.Baca
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M5 performance checks: sync round trips with a large
 * annotations file. Budgets are generous — the point is catching order-of-
 * magnitude regressions, not micro-benchmarks.
 */
class SyncPerfTest {

    private fun freshDb(): Baca {
        val driver = JdbcSqliteDriver("jdbc:sqlite:")
        Baca.Schema.create(driver)
        return Baca(driver)
    }

    private fun entry(i: Int, lamport: Long = i.toLong()) = AnnotationEntry(
        id = "ann-$i",
        kind = "highlight",
        reflowAnchor = ReflowAnchorDto("ch.xhtml", PointDto(i % 50, i % 300), PointDto(i % 50, (i % 300) + 20), "quote $i"),
        text = "highlighted text number $i with some more words to make it realistic",
        note = if (i % 5 == 0) "note for $i" else "",
        createdAt = i.toLong(),
        lamport = lamport,
    )

    @Test
    fun fiveThousandAnnotationsRoundTripUnder5s() {
        val count = 5000
        val dbA = freshDb()
        val a = SyncBoundary(dbA, "dev-A", now = { 1L })
        for (i in 0 until count) {
            val e = entry(i)
            dbA.annotationsQueries.upsertAnnotation(
                id = e.id, book_hash = "hash-1", kind = e.kind, anchor = SyncJson.encode(e),
                text = e.text, note = e.note, created_at = e.createdAt,
                device_id = "dev-A", lamport = e.lamport, deleted = 0,
            )
        }
        dbA.booksQueries.insertBook("hash-1", "Big Book", "A", "/b/1.epub", "epub", null, 1, 1, 0)

        val transport = MemoryTransport()
        val exportMs = measureTimeMillis {
            kotlinx.coroutines.runBlocking { a.exportAll(transport) }
        }
        println("PERF export($count annots) = ${exportMs}ms")
        assertTrue(exportMs < 5000, "export should be fast, was ${exportMs}ms")

        val dbB = freshDb()
        val b = SyncBoundary(dbB, "dev-B", now = { 1L })
        dbB.booksQueries.insertBook("hash-1", "Big Book", "A", "", "remote", null, 1, 2, 0)
        val pullMs = measureTimeMillis {
            kotlinx.coroutines.runBlocking {
                b.pullAndMerge(transport)
            }
        }
        println("PERF pull+merge($count annots) = ${pullMs}ms")
        assertTrue(pullMs < 5000, "merge should be fast, was ${pullMs}ms")
        assertEquals(count, dbB.annotationsQueries.selectAnnotationsAll().executeAsList().size)
    }

}
