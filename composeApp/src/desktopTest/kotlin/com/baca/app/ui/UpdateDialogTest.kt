package com.baca.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.baca.shared.domain.UpdateStatus
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class UpdateDialogTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `update dialog shows version and handles download, later, and skip`() {
        var downloads = 0
        var laters = 0
        var skips = 0
        val available = UpdateStatus.Available("0.3.0", "https://example.test/v0.3.0")

        rule.setContent {
            AppTheme {
                UpdateAvailableDialog(
                    available = available,
                    currentVersion = "0.2.1",
                    onDownload = { downloads++ },
                    onLater = { laters++ },
                    onSkip = { skips++ },
                )
            }
        }

        rule.onNodeWithText("Update available").assertExists()
        rule.onNodeWithText("Baca 0.3.0 is available (you have 0.2.1).").assertExists()
        rule.onNodeWithText("Download").performClick()
        rule.onNodeWithText("Later").performClick()
        rule.onNodeWithText("Skip this version").performClick()

        assertEquals(1, downloads)
        assertEquals(1, laters)
        assertEquals(1, skips)
    }
}
