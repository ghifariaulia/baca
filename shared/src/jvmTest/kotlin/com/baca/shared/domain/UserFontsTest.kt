package com.baca.shared.domain

import com.baca.shared.domain.reader.PositionStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class FontFakeStore : PositionStore {
    val local = mutableMapOf<String, String>()
    override val deviceId = "t"
    override fun loadPosition(bookHash: String): Locator? = null
    override fun savePosition(bookHash: String, locator: Locator) {}
    override fun getLocal(key: String): String? = local[key]
    override fun putLocal(key: String, value: String) { local[key] = value }
}

class UserFontsTest {

    @Test
    fun `DisplaySettings accepts user font keys and rejects invalid ones`() {
        val custom = decodeDisplay(DisplaySettings(font = "user:my-font").encode())
        assertEquals("user:my-font", custom.font)
        assertTrue(isUserFontKey(custom.font))

        assertFailsWith<IllegalArgumentException> { DisplaySettings(font = "user:") }
        assertFailsWith<IllegalArgumentException> { DisplaySettings(font = "unknown") }
    }

    @Test
    fun `user fonts persist in PositionStore and deleted active font falls back to serif`() {
        val store = FontFakeStore()
        val initial = store.loadUserFonts()
        assertEquals(emptyList(), initial)

        val added = addUserFontEntry(initial, id = "f1", name = "EB Garamond", fileName = "f1.ttf")
        store.saveUserFonts(added)
        assertEquals(listOf(UserFont("f1", "EB Garamond", "f1.ttf")), store.loadUserFonts())

        val active = DisplaySettings(font = "user:f1")
        assertEquals("user:f1", resolveDisplayFont(active, store.loadUserFonts()).font)

        val afterRemove = removeUserFontEntry(store.loadUserFonts(), "f1")
        store.saveUserFonts(afterRemove)
        assertEquals("serif", resolveDisplayFont(active, store.loadUserFonts()).font)
    }
}
