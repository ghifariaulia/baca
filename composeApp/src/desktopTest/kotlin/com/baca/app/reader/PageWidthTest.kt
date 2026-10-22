package com.baca.app.reader

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class PageWidthTest {
    @Test
    fun `a tablet reads across its whole window, the desktop in its column`() {
        // an 800dp tablet: all of it, as LibreraX's page is; a phone's 411.4dp is 412 CSS px
        assertEquals(800.dp, pageWidthOf(800.dp, capped = false))
        assertEquals(412.dp, pageWidthOf(411.4.dp, capped = false))
        // a wide desktop window holds the page to its 680dp column
        assertEquals(680.dp, pageWidthOf(1200.dp, capped = true))
    }
}
