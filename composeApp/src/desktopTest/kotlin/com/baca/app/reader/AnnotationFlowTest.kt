package com.baca.app.reader

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.click
import androidx.compose.ui.semantics.getOrNull
import com.baca.shared.db.DbAnnotationRepository
import com.baca.shared.db.Baca
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.baca.shared.parser.parseEpub
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val AStore = object : com.baca.shared.domain.reader.PositionStore {
    override val deviceId = "test"
    override fun loadPosition(bookHash: String) = null
    override fun savePosition(bookHash: String, locator: com.baca.shared.domain.Locator) {}
    override fun getLocal(key: String): String? = null
    override fun putLocal(key: String, value: String) {}
}

/** Long-press → toolbar → Note → Save round trip through the repository. */
class AnnotationFlowTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun longPressCreatesHighlightWithNote() {
        val bytes = javaClass.classLoader.getResourceAsStream("books/frankenstein.epub")!!.readBytes()
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "annot-test.epub"
        FileSystem.SYSTEM.write(path) { write(bytes) }
        val book = parseEpub(path).openingAtText()
        val driver = JdbcSqliteDriver("jdbc:sqlite:")
        Baca.Schema.create(driver)
        val repo = DbAnnotationRepository(Baca(driver), now = { 1L })

        rule.setContent {
            ReaderScreen(
                book = book,
                bookPath = path.toString(),
                bookHash = "hash",
                store = AStore,
                onBack = {},
                annotations = repo.forBookDetailed("hash").map { it.entry },
                onAnnotationUpsert = { entry -> repo.upsert(entry, "hash") },
                onAnnotationDelete = {},
            )
        }
        // wait for the reader + first real page layout (reflow lands after the
        // 150ms viewport debounce)
        rule.waitUntil(20_000) {
            rule.onAllNodesWithText("Title:", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        // long press somewhere over body text
        rule.onNodeWithText("Title: Frankenstein", substring = true)
            .performTouchInput { longClick(center) }
        // toolbar appears
        rule.waitForIdle()
        rule.onNodeWithText("Note").assertExists()
        rule.onNodeWithText("Note").performTouchInput { click(center) }
        // editor opens with the quoted text; type a note and save
        rule.waitForIdle()
        // a tap on the sheet's blank space keeps it open (it used to fall through to the backdrop)
        rule.onNodeWithText("COLOR").performTouchInput { click(center) }
        rule.waitForIdle()
        rule.onNodeWithText("Save").assertExists()
        // the whole note box takes the tap, not just the placeholder's width
        rule.onNode(hasSetTextAction()).performTouchInput { click(centerRight - androidx.compose.ui.geometry.Offset(4f, 0f)) }
        rule.waitForIdle()
        rule.onNode(hasSetTextAction()).assertIsFocused()
        rule.onNode(hasSetTextAction()).performTextInput("the hallway")
        rule.onNodeWithText("Save").performClick()
        val stored = repo.forBookDetailed("hash")
        assertEquals(1, stored.size)
        assertEquals("the hallway", stored.first().entry.note)
        assertTrue(stored.first().entry.text.isNotBlank(), "highlight anchors to the block text")
        assertEquals("highlight", stored.first().entry.kind)
    }

    @Test
    fun `long press selects a word, a handle drags the selection, and the highlight keeps that range`() {
        val bytes = javaClass.classLoader.getResourceAsStream("books/frankenstein.epub")!!.readBytes()
        val path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "select-test.epub"
        FileSystem.SYSTEM.write(path) { write(bytes) }
        val book = parseEpub(path).openingAtText()
        val driver = JdbcSqliteDriver("jdbc:sqlite:")
        Baca.Schema.create(driver)
        val repo = DbAnnotationRepository(Baca(driver), now = { 1L })
        val annotTick = androidx.compose.runtime.mutableIntStateOf(0)
        rule.setContent {
            annotTick.value
            ReaderScreen(
                book = book, bookPath = path.toString(), bookHash = "hash", store = AStore, onBack = {},
                annotations = repo.forBookDetailed("hash").map { it.entry },
                onAnnotationUpsert = { entry -> repo.upsert(entry, "hash") },
                onAnnotationDelete = {},
            )
        }
        rule.waitUntil(20_000) { rule.onAllNodesWithText("Title:", substring = true).fetchSemanticsNodes().isNotEmpty() }
        val line = rule.onNodeWithText("Title: Frankenstein", substring = true)
        line.performTouchInput { longClick(centerLeft + androidx.compose.ui.geometry.Offset(10f, 0f)) }
        rule.waitForIdle()
        val handles = rule.onAllNodesWithTag(SELECTION_HANDLE_TAG)
        assertEquals(2, handles.fetchSemanticsNodes().size)

        // "Title" is selected; drag the end handle along the line
        handles[1].performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(120f, 0f), 400) }
        rule.waitForIdle()
        rule.onNodeWithText("Note").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Save").performClick()
        annotTick.value++

        val anchor = repo.forBookDetailed("hash").single().entry.reflowAnchor!!
        assertEquals(anchor.start.blockIndex, anchor.end.blockIndex)
        assertEquals(0, anchor.start.charOffset)
        val selected = anchor.end.charOffset - anchor.start.charOffset
        assertTrue(selected > "Title".length, "the drag extended the selection past the word, got $selected chars")
        assertTrue(anchor.quote.startsWith("Title: Fr"), "quote follows the selection: ${anchor.quote}")
        // and the page draws it
        rule.waitForIdle()
        val drawn = rule.onAllNodesWithText("Title: Frankenstein", substring = true).fetchSemanticsNodes().flatMap { node ->
            val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            node.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            results.flatMap { r -> r.layoutInput.text.spanStyles.map { it.item.background } }
        }
        assertTrue(HighlightColors.of("yellow") in drawn, "the highlight is drawn, got backgrounds $drawn")
    }
}
