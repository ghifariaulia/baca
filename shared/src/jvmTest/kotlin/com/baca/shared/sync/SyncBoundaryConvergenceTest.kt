package com.baca.shared.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.baca.shared.db.Baca
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Two-device convergence through the transport (the scripted core of plan
 * 11.4): A adds a book and moves position; B has its own book + highlight;
 * merging on both sides converges to identical DB dumps.
 */
class SyncBoundaryConvergenceTest {

    private fun freshDb(): Baca {
        val driver = JdbcSqliteDriver("jdbc:sqlite:")
        Baca.Schema.create(driver)
        return Baca(driver)
    }

    @Test
    fun twoDevicesConvergeThroughTransport() {
        val transport = MemoryTransport()

        // --- device A: a book + moved position
        val dbA = freshDb()
        val a = SyncBoundary(dbA, "dev-A", now = { 100L })
        dbA.booksQueries.insertBook("hash-1", "Moby-Dick", "H. Melville", "/books/1.epub", "epub", null, 100, 1, 0)
        dbA.positionsQueries.setPosition("hash-1", "dev-A", """{"href":"ch3.xhtml","blockIndex":4,"charOffset":99,"progression":0.31,"quote":"q"}""", 3, 100)
        kotlinx.coroutines.runBlocking { a.exportAll(transport) }

        // --- device B: its own book + highlight + synced setting
        val dbB = freshDb()
        val b = SyncBoundary(dbB, "dev-B", now = { 200L })
        dbB.booksQueries.insertBook("hash-2", "Dracula", "B. Stoker", "/books/2.epub", "epub", null, 200, 4, 0)
        dbB.positionsQueries.setPosition("hash-2", "dev-B", """{"href":"ch1.xhtml","blockIndex":0,"charOffset":5,"progression":0.02,"quote":"q"}""", 5, 200)
        dbB.annotationsQueries.upsertAnnotation(
            id = "ann-1", book_hash = "hash-2", kind = "highlight",
            anchor = SyncJson.encode(
                AnnotationEntry(
                    id = "ann-1", kind = "highlight",
                    reflowAnchor = ReflowAnchorDto("ch1.xhtml", PointDto(0, 0), PointDto(0, 9), "quote"),
                    text = "opening", createdAt = 200, lamport = 6,
                ),
            ),
            text = "opening", note = "", created_at = 200, device_id = "local", lamport = 6, deleted = 0,
        )
        dbB.settings_syncedQueries.putSynced("dictionary", "\"dracula\"", 7)
        kotlinx.coroutines.runBlocking { b.exportAll(transport) }

        // --- both sides pull and merge
        val reportA = kotlinx.coroutines.runBlocking { a.pullAndMerge(transport) }
        val reportB = kotlinx.coroutines.runBlocking { b.pullAndMerge(transport) }
        assertTrue(reportA.positions + reportA.library > 0, "A should merge B's data")
        assertTrue(reportB.positions > 0, "B should merge A's positions")

        // --- converged: dumps must match after sync
        assertEquals(dumpBooks(dbA).sorted(), dumpBooks(dbB).sorted())
        assertEquals(dumpPositions(dbA).sorted(), dumpPositions(dbB).sorted())
        assertEquals(dumpAnnots(dbA).sorted(), dumpAnnots(dbB).sorted().map { it.replace("|local|", "|dev-B|") })
        // the merged highlight stays attached to its book on both devices
        assertEquals(listOf("hash-2"), dbA.annotationsQueries.selectAnnotationsAll().executeAsList().map { it.book_hash })
        assertEquals(listOf("hash-2"), dbB.annotationsQueries.selectAnnotationsAll().executeAsList().map { it.book_hash })

        // A learned B's synced setting
        assertEquals("\"dracula\"", dbA.settings_syncedQueries.selectSynced("dictionary").executeAsOneOrNull()?.value_)

        // A overwrote nothing of its own position (A's hash-1 won its own merge)
        assertTrue(reportA.positionOverwrites.isEmpty())

        // each device only ever wrote into its own folder (TECH 6.1)
        val writers = transport.files.keys.filter { it.startsWith("devices/") }.map { it.split('/')[1] }.distinct()
        assertTrue(writers.all { it == "dev-A" || it == "dev-B" }, "writes stay in own folders")
    }

    @Test
    fun equalLamportAnnotationEditsConvergeAcrossDevices() {
        val transport = MemoryTransport()
        fun device(id: String, text: String): Pair<Baca, SyncBoundary> {
            val db = freshDb()
            val entry = AnnotationEntry(id = "ann-1", kind = "note", fixedAnchor = FixedAnchorDto(0, emptyList()), text = text, createdAt = 1, lamport = 5)
            // rows written on-device carry the "local" device id
            db.annotationsQueries.upsertAnnotation(
                id = "ann-1", book_hash = "hash-1", kind = "note", anchor = SyncJson.encode(entry),
                text = text, note = "", created_at = 1, device_id = "local", lamport = 5, deleted = 0,
            )
            return db to SyncBoundary(db, id, now = { 1L })
        }
        val (dbA, a) = device("dev-A", "from A")
        val (dbB, b) = device("dev-B", "from B")

        kotlinx.coroutines.runBlocking {
            a.exportAll(transport)
            b.exportAll(transport)
            a.pullAndMerge(transport)
            b.pullAndMerge(transport)
        }

        // (lamport, device_id) tie: dev-B > dev-A, so both keep B's edit
        assertEquals("from B", dbA.annotationsQueries.selectAnnotationsAll().executeAsOne().text)
        assertEquals("from B", dbB.annotationsQueries.selectAnnotationsAll().executeAsOne().text)
    }

    private fun dumpBooks(db: Baca): List<String> =
        db.booksQueries.selectBooksAll().executeAsList().map { "${it.book_hash}|${it.title}|${it.lamport}|${it.deleted}" }

    private fun dumpPositions(db: Baca): List<String> =
        db.positionsQueries.selectPositions().executeAsList().map { row ->
            val locator = if (row.locator.contains("\"href\"")) {
                SyncJson.decode<ReflowLocator>(row.locator)
            } else {
                SyncJson.decode<FixedPageLocator>(row.locator)
            }
            "${row.book_hash}|$locator|${row.lamport}"
        }

    private fun dumpAnnots(db: Baca): List<String> =
        db.annotationsQueries.selectAnnotationsAll().executeAsList().map { "${it.id}|${it.book_hash}|${it.device_id}|${it.text}|${it.lamport}|${it.deleted}" }
}
