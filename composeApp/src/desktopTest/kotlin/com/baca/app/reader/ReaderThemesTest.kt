package com.baca.app.reader

import androidx.compose.ui.graphics.Color
import com.baca.app.ui.ReaderThemes
import com.baca.shared.domain.DisplaySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReaderThemesTest {
    @Test
    fun `the reading themes are LibreraX's, page and text, and Ink's black on Paper's page`() {
        val themes = listOf("white", "paper", "ink", "grey", "dim", "dusk", "black").map { ReaderThemes.forTheme(it) }
        assertEquals(
            listOf(
                0xFFFFFFFF to 0xFF111111, 0xFFF4ECD8 to 0xFF5B4636, 0xFFF4ECD8 to 0xFF111111, 0xFFE9E9E9 to 0xFF262626,
                0xFF303030 to 0xFFDDDDDD, 0xFF1C1A17 to 0xFFCFC5B3, 0xFF000000 to 0xFFDDDDDD,
            ).map { (page, text) -> Color(page) to Color(text) },
            themes.map { it.background to it.ink },
        )
        // the two LibreraX has that Baca hadn't are settings like the rest
        DisplaySettings(theme = "grey")
        DisplaySettings(theme = "dusk")
    }

    @Test
    fun `the dark themes are those LibreraX reads a book's dark scheme for`() {
        assertEquals(
            listOf(false, false, false, false, true, true, true),
            listOf("white", "paper", "ink", "grey", "dim", "dusk", "black").map { com.baca.app.ui.isDarkScheme(ReaderThemes.forTheme(it).background) },
        )
    }

    @Test
    fun `pictures, letter-sized ones too, are dimmed on a dark page and multiplied into a tinted one`() {
        val paper = ReaderThemes.forTheme("paper").background
        assertEquals(androidx.compose.ui.graphics.ColorFilter.tint(paper, androidx.compose.ui.graphics.BlendMode.Multiply), pictureFilter(paper) { true })
        assertEquals(null, pictureFilter(paper) { false })
        assertEquals(null, pictureFilter(ReaderThemes.forTheme("white").background) { true })
        assertTrue(pictureFilter(ReaderThemes.forTheme("dusk").background) { false } != null)
    }
}
