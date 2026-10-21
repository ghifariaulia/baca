package com.baca.app.reader

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.baca.app.ui.Type
import com.baca.shared.domain.reader.SEARCH_LIMIT
import com.baca.shared.domain.reader.SearchHit
import com.baca.shared.domain.reader.searchBook
import com.baca.shared.parser.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * In-book search: a query field and the matches, each with its chapter and the
 * text around it. Results follow the query as it's typed.
 */
@Composable
internal fun SearchContent(
    chapters: List<Chapter>,
    query: String,
    onQuery: (String) -> Unit,
    onPick: (SearchHit) -> Unit,
) {
    val hits by produceState<List<SearchHit>?>(null, query, chapters) {
        // the last query's results don't stand in for this one's
        value = null
        // wait for a pause in typing; a whole book takes a moment to scan
        delay(SEARCH_DEBOUNCE_MS)
        value = withContext(Dispatchers.Default) { searchBook(chapters, query) }
    }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { if (query.isEmpty()) focus.requestFocus() }
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().padding(vertical = 8.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .padding(12.dp),
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = Type.body.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            ) { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text("Search this book", style = Type.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    inner()
                }
            }
        }
        val found = hits
        val status = when {
            query.trim().length < 2 -> null
            found == null -> "Searching…"
            found.isEmpty() -> "No matches"
            found.size >= SEARCH_LIMIT -> "First $SEARCH_LIMIT matches"
            found.size == 1 -> "1 match"
            else -> "${found.size} matches"
        }
        status?.let {
            Text(it, style = Type.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
        }
        if (!found.isNullOrEmpty() && query.trim().length >= 2) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                itemsIndexed(found) { index, hit ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    SearchRow(chapters, hit) { onPick(hit) }
                }
            }
        }
    }
}

@Composable
private fun SearchRow(chapters: List<Chapter>, hit: SearchHit, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(
            // a run-on part has no contents entry of its own; name it after the one it continues
            chapters.take(hit.chapterIndex + 1).lastOrNull { it.title != null }?.title ?: "Chapter ${hit.chapterIndex + 1}",
            style = Type.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            buildAnnotatedString {
                append(hit.snippet.substring(0, hit.matchInSnippet.first))
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) {
                    append(hit.snippet.substring(hit.matchInSnippet))
                }
                append(hit.snippet.substring(hit.matchInSnippet.last + 1))
            },
            style = Type.body,
            maxLines = 3,
        )
    }
}

private const val SEARCH_DEBOUNCE_MS = 250L
