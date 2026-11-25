package com.baca.app.reader

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.getOrNull
import com.baca.shared.parser.parseEpub
import kotlinx.coroutines.CompletableDeferred
import okio.FileSystem
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadAloudTest {
    @get:Rule val rule = createComposeRule()

    private val store = object : com.baca.shared.domain.reader.PositionStore {
        override val deviceId = "test"
        override fun loadPosition(bookHash: String) = null
        override fun savePosition(bookHash: String, locator: com.baca.shared.domain.Locator) {}
        override fun getLocal(key: String): String? = null
        override fun putLocal(key: String, value: String) {}
    }

    /** Says nothing: notes each sentence and holds it until the test lets it finish. */
    private class FakeSpeech : Speech {
        val said = mutableListOf<String>()
        var finish = CompletableDeferred<Unit>()
        var stopped = false
        var externalStop: (() -> Unit)? = null
        var serviceStopped = false
        override fun start(title: String, onExternalStop: () -> Unit) {
            externalStop = onExternalStop
        }
        override fun stop() {
            serviceStopped = true
        }
        override suspend fun say(text: String) {
            said += text
            try {
                finish.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                stopped = true
                throw e
            }
            finish = CompletableDeferred()
        }
    }

    @Test
    fun `read aloud says the page's sentences in turn and stops when asked`() {
        val bytes = javaClass.classLoader.getResourceAsStream("books/frankenstein.epub")!!.readBytes()
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "read-aloud.epub"
        FileSystem.SYSTEM.write(path) { write(bytes) }
        val book = parseEpub(path).openingAtText()
        val voice = FakeSpeech()
        rule.setContent {
            ReaderScreen(
                book = book, bookPath = path.toString(), bookHash = "hash", store = store, onBack = {},
                speech = voice,
            )
        }
        rule.waitUntil(20_000) { rule.onAllNodesWithText("Title:", substring = true).fetchSemanticsNodes().isNotEmpty() }

        rule.onNodeWithContentDescription("Read aloud").performClick()
        rule.waitUntil(5_000) { voice.said.size == 1 }
        assertEquals("The Project Gutenberg eBook of Frankenstein; or, the modern prometheus", voice.said[0], "starts at the top of the page")
        rule.onNodeWithText("Stop reading").assertExists()

        voice.finish.complete(Unit)
        rule.waitUntil(5_000) { voice.said.size == 2 }
        assertTrue(voice.said[1] != voice.said[0], "goes on to the next sentence")

        rule.onNodeWithText("Stop reading").performClick()
        rule.waitForIdle()
        assertTrue(voice.stopped, "the voice is cut off")
        assertEquals(0, rule.onAllNodesWithText("Stop reading").fetchSemanticsNodes().size)
        assertEquals(2, voice.said.size)
    }

    @Test
    fun `the page turns when the voice reaches text on the next page`() {
        val bytes = javaClass.classLoader.getResourceAsStream("books/frankenstein.epub")!!.readBytes()
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "read-aloud-turn.epub"
        FileSystem.SYSTEM.write(path) { write(bytes) }
        val book = parseEpub(path).openingAtText()
        val voice = FakeSpeech()
        rule.setContent {
            ReaderScreen(
                book = book, bookPath = path.toString(), bookHash = "hash", store = store, onBack = {},
                speech = voice,
            )
        }
        rule.waitUntil(20_000) { rule.onAllNodesWithText("Title:", substring = true).fetchSemanticsNodes().isNotEmpty() }
        val firstPage = footerPage()

        rule.onNodeWithContentDescription("Read aloud").performClick()
        var spokenCount = 0
        while (footerPage() == firstPage && spokenCount < 100) {
            rule.waitUntil(5_000) { voice.said.size == spokenCount + 1 }
            spokenCount++
            voice.finish.complete(Unit)
            rule.waitForIdle()
        }
        assertEquals(firstPage + 1, footerPage(), "after ${voice.said.size} sentences")
    }

    @Test
    fun `external stop dismisses reading pill and stops speech`() {
        val bytes = javaClass.classLoader.getResourceAsStream("books/frankenstein.epub")!!.readBytes()
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "read-aloud-ext-stop.epub"
        FileSystem.SYSTEM.write(path) { write(bytes) }
        val book = parseEpub(path).openingAtText()
        val voice = FakeSpeech()
        rule.setContent {
            ReaderScreen(
                book = book, bookPath = path.toString(), bookHash = "hash", store = store, onBack = {},
                speech = voice,
            )
        }
        rule.waitUntil(20_000) { rule.onAllNodesWithText("Title:", substring = true).fetchSemanticsNodes().isNotEmpty() }

        rule.onNodeWithContentDescription("Read aloud").performClick()
        rule.waitUntil(5_000) { voice.said.isNotEmpty() && voice.externalStop != null }
        rule.onNodeWithText("Stop reading").assertExists()

        rule.runOnUiThread { voice.externalStop?.invoke() }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Stop reading").fetchSemanticsNodes().isEmpty() }
        assertTrue(voice.stopped)
        assertTrue(voice.serviceStopped)
    }

    // the footer's "page / total"
    private fun footerPage(): Int {
        val pattern = Regex("""^(\d+) / \d+$""")
        val node = rule.onAllNodesWithText(" / ", substring = true).fetchSemanticsNodes()
            .mapNotNull { n -> n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString() }
            .firstNotNullOf { pattern.find(it) }
        return node.groupValues[1].toInt()
    }
}
