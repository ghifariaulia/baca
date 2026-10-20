package com.baca.shared.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Example tests: one per merge rule from docs/TECH.md 6.3, plus the
 * delete/edit interplay rules.
 */
class MergeRulesTest {

    private fun state(dev: String, lamport: Long, href: String = "ch1.xhtml") =
        Tagged(dev, StateFile(ReflowLocator(href, 0, 0, 0.5, "q"), lamport, 0))

    @Test
    fun `position - highest lamport wins regardless of order`() {
        val merged = mergePositions(
            mapOf("book" to listOf(state("a", 3), state("b", 9), state("c", 1))),
        )
        assertEquals("b", merged["book"]!!.deviceId)
    }

    @Test
    fun `annotations - edit after delete wins and delete after edit wins`() {
        fun entry(lamport: Long, deleted: Boolean, note: String = "") = AnnotationEntry(
            id = "a1", kind = "highlight",
            reflowAnchor = ReflowAnchorDto("h", PointDto(0, 0), PointDto(0, 5), "q"),
            note = note, createdAt = 0, lamport = lamport, deleted = deleted,
        )
        // delete-then-edit: the edit (higher clock) wins
        val editWins = mergeAnnotations(
            listOf(
                Tagged("a", AnnotsFile(listOf(entry(4, deleted = true)))),
                Tagged("b", AnnotsFile(listOf(entry(7, deleted = false, note = "revived")))),
            ),
        )
        assertEquals("revived", editWins["a1"]!!.value.note)
        assertFalse(editWins["a1"]!!.value.deleted)

        // edit-then-delete: the delete (higher clock) wins
        val deleteWins = mergeAnnotations(
            listOf(
                Tagged("a", AnnotsFile(listOf(entry(4, deleted = false)))),
                Tagged("b", AnnotsFile(listOf(entry(7, deleted = true)))),
            ),
        )
        assertTrue(deleteWins["a1"]!!.value.deleted)
    }

    @Test
    fun `annotations - concurrent edits resolve to the later clock`() {
        fun edit(lamport: Long, note: String) = AnnotationEntry(
            id = "a1", kind = "highlight",
            reflowAnchor = ReflowAnchorDto("h", PointDto(0, 0), PointDto(0, 5), "q"),
            note = note, createdAt = 0, lamport = lamport,
        )
        val merged = mergeAnnotations(
            listOf(
                Tagged("a", AnnotsFile(listOf(edit(10, "first")))),
                Tagged("b", AnnotsFile(listOf(edit(11, "second")))),
            ),
        )
        assertEquals("second", merged["a1"]!!.value.note)
    }

    @Test
    fun `library - re-add after delete works because the add has a higher clock`() {
        val tombstone = Tagged("a", LibraryFile(listOf(LibraryEntry("h", "T", 0, 10, deleted = true))))
        val reAdd = Tagged("b", LibraryFile(listOf(LibraryEntry("h", "T", 0, 20))))
        val merged = mergeLibrary(listOf(tombstone, reAdd))
        assertFalse(merged["h"]!!.value.deleted)
    }

    @Test
    fun `library - delete after add wins when its clock is higher`() {
        val add = Tagged("a", LibraryFile(listOf(LibraryEntry("h", "T", 0, 10))))
        val delete = Tagged("b", LibraryFile(listOf(LibraryEntry("h", "T", 0, 30, deleted = true))))
        val merged = mergeLibrary(listOf(add, delete))
        assertTrue(merged["h"]!!.value.deleted)
    }

    @Test
    fun `settings - per key last writer wins`() {
        fun file(dev: String, key: String, value: String, lamport: Long) =
            Tagged(dev, SettingsFile(mapOf(key to SyncedSetting(JsonString(value), lamport))))
        val merged = mergeSettings(
            listOf(
                file("a", "k1", "\"a\"", 5),
                file("b", "k1", "\"b\"", 6),
                file("b", "k2", "\"other\"", 2),
            ),
        )
        assertEquals("\"b\"", merged["k1"]!!.value.value.raw)
        assertEquals("\"other\"", merged["k2"]!!.value.value.raw)
    }
}
