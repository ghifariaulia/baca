package com.baca.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ModelsTest {

    private val json = kotlinx.serialization.json.Json

    @Test
    fun locatorRoundTrips() {
        val locator = Locator("OEBPS/ch12.xhtml", 41, 128, 0.57, "quote text")
        val back = json.decodeFromString<Locator>(json.encodeToString(kotlinx.serialization.serializer<Locator>(), locator))
        assertEquals(locator, back)
    }

    @Test
    fun positionRecordRequiresExactlyOneLocator() {
        val ok = PositionRecord("h", "d", reflow = Locator("h", 0, 0, 0.0), lamport = 1, updatedAt = 1)
        assertEquals("h", ok.reflow?.href)

        assertFailsWith<IllegalArgumentException> {
            PositionRecord("h", "d", lamport = 1, updatedAt = 1)
        }
        assertFailsWith<IllegalArgumentException> {
            PositionRecord(
                "h", "d",
                reflow = Locator("h", 0, 0, 0.0),
                fixed = FixedLocator(1, 0.0),
                lamport = 1, updatedAt = 1,
            )
        }
    }

    @Test
    fun annotationRecordRequiresExactlyOneAnchor() {
        assertFailsWith<IllegalArgumentException> {
            AnnotationRecord(id = "a", bookHash = "h", createdAt = 1, lamport = 1, deviceId = "d")
        }
    }
}
