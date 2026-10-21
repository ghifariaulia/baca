package com.baca.app.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.baca.shared.parser.parseEpub
import okio.FileSystem
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** A tap in the middle of the page hides the top bar and footer; another brings them back. */
class ReaderBarsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `center tap toggles the bars without changing the page`() {
        val bytes = javaClass.classLoader.getResourceAsStream("books/frankenstein.epub")!!.readBytes()
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "bars-test.epub"
        FileSystem.SYSTEM.write(path) { write(bytes) }
        val book = parseEpub(path)
        rule.setContent {
            Box(Modifier.size(420.dp, 800.dp)) {
                ReaderScreen(book, path.toString(), "hash", BarsTestStore(), onBack = {})
            }
        }
        fun barsShown() = rule.onAllNodesWithText("Page ", substring = true).fetchSemanticsNodes().isNotEmpty()
        rule.waitUntil(10_000) { barsShown() }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        fun pageLabel() = rule.onAllNodesWithText("Page ", substring = true).fetchSemanticsNodes().single()
            .config[SemanticsProperties.Text].joinToString()
        val before = pageLabel()

        rule.onRoot().performTouchInput { click(center) }
        rule.waitUntil(5_000) { !barsShown() }

        rule.onRoot().performTouchInput { click(center) }
        rule.waitUntil(5_000) { barsShown() }
        // same page: hiding the bars doesn't re-paginate
        assertEquals(before, pageLabel())
    }
}

private class BarsTestStore : com.baca.shared.domain.reader.PositionStore {
    override val deviceId = "t"
    override fun loadPosition(bookHash: String): com.baca.shared.domain.Locator? = null
    override fun savePosition(bookHash: String, locator: com.baca.shared.domain.Locator) {}
    override fun getLocal(key: String): String? = null
    override fun putLocal(key: String, value: String) {}
}
