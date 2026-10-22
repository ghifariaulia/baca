package com.baca.app.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import com.baca.shared.domain.Locator
import com.baca.shared.domain.reader.PositionStore
import com.baca.shared.parser.parseMarkdownFile
import okio.FileSystem
import org.junit.Rule
import org.junit.Test

/** A Markdown file opens as a book and reads: its heading and a paragraph are on the page. */
class MarkdownBookTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `a markdown file opens as a book`() {
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "notes.md"
        FileSystem.SYSTEM.write(path) { writeUtf8("# The Heading\n\nA paragraph of *plain* words.\n\n## Second Part\n\nMore words here.\n") }
        val book = parseMarkdownFile(path)
        rule.setContent {
            Box(Modifier.size(420.dp, 800.dp)) {
                ReaderScreen(book, path.toString(), "md-hash", NoStore(), onBack = {})
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("The Heading", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        check(rule.onAllNodesWithText("A paragraph of plain words.", substring = true).fetchSemanticsNodes().isNotEmpty()) { "the paragraph is on the page" }
    }
}

private class NoStore : PositionStore {
    override val deviceId = "test"
    override fun loadPosition(bookHash: String): Locator? = null
    override fun savePosition(bookHash: String, locator: Locator) {}
    override fun getLocal(key: String): String? = null
    override fun putLocal(key: String, value: String) {}
}
