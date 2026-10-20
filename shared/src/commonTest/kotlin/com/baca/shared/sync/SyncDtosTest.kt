package com.baca.shared.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SyncDtosTest {

    @Test
    fun deviceMetaRoundTrips() {
        val meta = DeviceMeta("phone-a", counter = 41, lamport = 903, updatedAt = 1712345678)
        val back = SyncJson.decode<DeviceMeta>(SyncJson.encode(meta))
        assertEquals(meta, back)
    }

    @Test
    fun reflowStateFileRoundTripsAndKeepsDiscriminator() {
        val file = StateFile(
            locator = ReflowLocator(
                href = "OEBPS/ch12.xhtml",
                blockIndex = 41,
                charOffset = 128,
                progression = 0.57,
                quote = "the yellow gaslight",
            ),
            lamport = 903,
            updatedAt = 1712345678,
        )
        val text = SyncJson.encode(file)
        val back = SyncJson.decode<StateFile>(text)
        assertEquals(file, back)
        assertEquals("reflow", Regex("\"type\"\\s*:\\s*\"(\\w+)\"").find(text)!!.groupValues[1])
    }

    @Test
    fun fixedStateFileRoundTrips() {
        val file = StateFile(
            locator = FixedPageLocator(pageIndex = 211, progression = 0.57),
            lamport = 12,
            updatedAt = 1712345678,
        )
        val back = SyncJson.decode<StateFile>(SyncJson.encode(file))
        assertEquals(file, back)
    }

    @Test
    fun invalidStateFileIsRejectedOnDecode() {
        val text = """
            {"locator": {"href": " ", "blockIndex": -1, "charOffset": 0,
                         "progression": 0.0, "quote": ""}, "lamport": 1, "updatedAt": 2}
        """.trimIndent()
        assertFailsWith<IllegalArgumentException> { SyncJson.decode<StateFile>(text) }
    }

    @Test
    fun annotsFileRoundTrips() {
        val file = AnnotsFile(
            listOf(
                AnnotationEntry(
                    id = "a1",
                    kind = "highlight",
                    reflowAnchor = ReflowAnchorDto(
                        href = "OEBPS/ch12.xhtml",
                        start = PointDto(41, 128),
                        end = PointDto(41, 190),
                        quote = "highlighted text",
                        prefix = "before the ",
                        suffix = " after it",
                    ),
                    text = "highlighted text",
                    note = "",
                    createdAt = 1712345678,
                    lamport = 880,
                ),
                AnnotationEntry(
                    id = "a2",
                    kind = "bookmark",
                    fixedAnchor = FixedAnchorDto(pageIndex = 21, rects = listOf(listOf(10f, 20f, 30f, 40f))),
                    createdAt = 1712345700,
                    lamport = 881,
                    deleted = true,
                ),
            ),
        )
        val back = SyncJson.decode<AnnotsFile>(SyncJson.encode(file))
        assertEquals(file, back)
    }

    @Test
    fun annotationEntryRejectsUnknownKindAndEmptyAnchor() {
        val validAnchor = ReflowAnchorDto("h", PointDto(0, 0), PointDto(0, 1), "q")
        assertFailsWith<IllegalArgumentException> {
            AnnotationEntry("a", "banana", reflowAnchor = validAnchor, createdAt = 1, lamport = 1)
        }
        assertFailsWith<IllegalArgumentException> {
            AnnotationEntry("a", "note", createdAt = 1, lamport = 1)
        }
    }

    @Test
    fun libraryAndSettingsRoundTrip() {
        val library = LibraryFile(
            listOf(
                LibraryEntry("hash-1", "Frankenstein", 1712345678, lamport = 700),
                LibraryEntry("hash-2", "Dracula", 1712345679, lamport = 701, deleted = true),
            ),
        )
        assertEquals(library, SyncJson.decode<LibraryFile>(SyncJson.encode(library)))

        val settings = SettingsFile(
            mapOf("dictionary" to SyncedSetting(JsonString("""{"lang":"en"}"""), lamport = 650)),
        )
        assertEquals(settings, SyncJson.decode<SettingsFile>(SyncJson.encode(settings)))
    }

    @Test
    fun unknownFieldsAreIgnoredSoOlderFilesStayReadable() {
        val text = """
            {"books": [], "someFutureField": 1}
        """.trimIndent()
        assertEquals(0, SyncJson.decode<LibraryFile>(text).books.size)
    }
}
