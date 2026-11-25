package com.baca.app.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.baca.shared.domain.DisplaySettings
import com.baca.shared.domain.UserFont
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class DisplayControlsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `display content exposes tap zone mode selector`() {
        var latest = DisplaySettings()
        rule.setContent {
            var state by remember { mutableStateOf(DisplaySettings()) }
            DisplayContent(state) {
                state = it
                latest = it
            }
        }

        rule.onNodeWithText("TAP ZONES").assertExists()
        rule.onNodeWithText("Swapped").performScrollTo().performClick()
        assertEquals("swapped", latest.tapZoneMode)
        rule.onNodeWithText("Bars only").performScrollTo().performClick()
        assertEquals("disabled", latest.tapZoneMode)
    }

    @Test
    fun `display content lists user fonts, selects one, and supports removal`() {
        var latest = DisplaySettings()
        var deletedId: String? = null
        val customFonts = listOf(UserFont(id = "f1", name = "MyFont", fileName = "f1.ttf"))
        rule.setContent {
            var state by remember { mutableStateOf(DisplaySettings()) }
            DisplayContent(
                display = state,
                userFonts = customFonts,
                onImportFont = {},
                onDeleteFont = { deletedId = it },
            ) {
                state = it
                latest = it
            }
        }

        rule.onNodeWithText("MyFont").performScrollTo().performClick()
        assertEquals("user:f1", latest.font)
        rule.onNodeWithText("Remove MyFont").performScrollTo().performClick()
        assertEquals("f1", deletedId)
    }
}
