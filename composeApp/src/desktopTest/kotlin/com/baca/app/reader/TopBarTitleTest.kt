package com.baca.app.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.baca.app.ui.AppTheme
import com.baca.shared.domain.Locator
import com.baca.shared.domain.reader.PositionStore
import com.baca.shared.parser.parseEpub
import okio.FileSystem
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertTrue

/** The title in the reader's top bar sits in the middle of the bar, not of what the buttons leave. */
@OptIn(ExperimentalTestApi::class)
class TopBarTitleTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the title is centred on the bar at a desktop width and a phone's`() {
        val bytes = javaClass.classLoader.getResourceAsStream("books/frankenstein.epub")!!.readBytes()
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "title-test.epub"
        FileSystem.SYSTEM.write(path) { write(bytes) }
        val book = parseEpub(path)
        val width = androidx.compose.runtime.mutableStateOf(1000)
        rule.setContent {
            AppTheme {
                Box(Modifier.size(width.value.dp, 700.dp)) { ReaderScreen(book, path.toString(), "hash", TitleStore(), onBack = {}) }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText(book.title.take(10), substring = true).fetchSemanticsNodes().isNotEmpty() }
        listOf(1000, 420).forEach { w ->
            width.value = w
            rule.waitForIdle()
            val title = rule.onNodeWithText(book.title, substring = true).fetchSemanticsNode()
            val center = title.positionInRoot.x + title.size.width / 2f
            assertTrue(abs(center - w / 2f) <= 2f, "title centre $center should be the bar's ${w / 2f} at width $w")
        }
    }
}

private class TitleStore : PositionStore {
    override val deviceId = "t"
    override fun loadPosition(bookHash: String): Locator? = null
    override fun savePosition(bookHash: String, locator: Locator) {}
    override fun getLocal(key: String): String? = null
    override fun putLocal(key: String, value: String) {}
}
