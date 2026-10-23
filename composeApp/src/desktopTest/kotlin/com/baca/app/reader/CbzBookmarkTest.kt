package com.baca.app.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.baca.shared.parser.CbzBook
import com.baca.shared.parser.parseCbz
import com.baca.shared.domain.AnnotationEntry
import com.baca.shared.domain.FixedAnchorDto
import okio.Path.Companion.toPath
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class CbzBookmarkTest {

    @get:Rule
    val rule = createComposeRule()

    private val annotations = mutableStateListOf<AnnotationEntry>()

    private fun comic(): Pair<CbzBook, String> {
        val zip = java.io.File.createTempFile("bookmark-cbz", ".cbz")
        java.util.zip.ZipOutputStream(zip.outputStream()).use { out ->
            listOf("page1.jpg", "page2.jpg").forEach { name ->
                out.putNextEntry(java.util.zip.ZipEntry(name))
                out.write(byteArrayOf(1))
                out.closeEntry()
            }
        }
        zip.deleteOnExit()
        return parseCbz(zip.absolutePath.toPath(), zip.name) to zip.absolutePath
    }

    private fun setReader(book: CbzBook, path: String, startPage: Int? = null) {
        rule.setContent {
            Box(Modifier.size(360.dp, 760.dp)) {
                CbzScreen(
                    book = book,
                    bookPath = path,
                    bookHash = "hash",
                    store = CbzBookmarkTestStore(),
                    onBack = {},
                    annotations = annotations.toList(),
                    onAnnotationUpsert = { entry -> annotations.removeAll { it.id == entry.id }; annotations.add(entry) },
                    onAnnotationDelete = { id -> annotations.removeAll { it.id == id } },
                    startPage = startPage,
                )
            }
        }
    }

    private fun waitForText(text: String) = rule.waitUntil(5_000) {
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForIcon(description: String) = rule.waitUntil(5_000) {
        rule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun `bookmark marks the current page only`() {
        val (book, path) = comic()
        setReader(book, path)
        waitForText("Page 1 of 2")

        rule.onNodeWithContentDescription("Add bookmark").performClick()
        waitForIcon("Remove bookmark")
        val entry = annotations.single()
        assertEquals("bookmark", entry.kind)
        assertEquals(FixedAnchorDto(0, emptyList()), entry.fixedAnchor)
        assertNull(entry.reflowAnchor)

        rule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        waitForText("Page 2 of 2")
        waitForIcon("Add bookmark")
    }

    @Test
    fun `opens on the requested page`() {
        val (book, path) = comic()
        setReader(book, path, startPage = 1)
        waitForText("Page 2 of 2")
    }

    @Test
    fun `fixed bookmark card shows its page`() {
        val entry = AnnotationEntry(
            id = "b1", kind = "bookmark",
            fixedAnchor = FixedAnchorDto(4, emptyList()),
            createdAt = 0, lamport = 1,
        )
        rule.setContent { AnnotationCards(entries = listOf(entry), book = null, ink = androidx.compose.ui.graphics.Color.Black) }
        rule.onNodeWithText("Page 5").assertExists()
    }
}

private class CbzBookmarkTestStore : com.baca.shared.domain.reader.PositionStore {
    override val deviceId = "test"
    override fun loadPosition(bookHash: String): com.baca.shared.domain.Locator? = null
    override fun savePosition(bookHash: String, locator: com.baca.shared.domain.Locator) {}
    override fun getLocal(key: String): String? = null
    override fun putLocal(key: String, value: String) {}
}
