package com.baca.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class WorkKeyTest {

    @Test
    fun `same id in different spellings gives one key`() {
        assertEquals(
            workKeyOf("urn:uuid:1B2C3D4E-0000-1111", "Frankenstein"),
            workKeyOf("1b2c3d4e00001111", "  FRANKENSTEIN "),
        )
        assertEquals(workKeyOf("urn:isbn:978-0-14-143947-1", "Dracula"), workKeyOf("9780141439471", "Dracula"))
    }

    @Test
    fun `placeholder or missing ids link nothing`() {
        assertNull(workKeyOf(null, "Frankenstein"))
        assertNull(workKeyOf("  ", "Frankenstein"))
        assertNull(workKeyOf("id1", "Frankenstein"))
    }

    @Test
    fun `same id on different titles does not link`() {
        assertNotEquals(workKeyOf("http://example.com/book", "Poems"), workKeyOf("http://example.com/book", "Essays"))
    }
}
