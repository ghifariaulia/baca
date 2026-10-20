package com.baca.app.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baca.app.ui.Type
import com.baca.shared.domain.reader.CbzSession
import com.baca.shared.domain.reader.PositionStore
import com.baca.shared.parser.CbzBook
import com.baca.shared.sync.AnnotationEntry
import com.baca.shared.sync.FixedAnchorDto
import kotlinx.coroutines.launch

/**
 * Fixed-layout reader (TECH 9.2): image pages with pinch-zoom + pan, same
 * footer pattern as the reflow reader. Zoom/pan never touches the locator —
 * only page changes are saved.
 */
@Composable
fun CbzScreen(
    book: CbzBook,
    bookPath: String,
    bookHash: String,
    store: PositionStore,
    onBack: () -> Unit,
    /** This book's annotations; only bookmarks apply to image pages. */
    annotations: List<AnnotationEntry> = emptyList(),
    onAnnotationUpsert: (AnnotationEntry) -> Unit = {},
    onAnnotationDelete: (id: String) -> Unit = {},
    /** Open straight on this page (Notes list) instead of the saved one. */
    startPage: Int? = null,
) {
    val scope = rememberCoroutineScope()
    val images = rememberBookImages(bookPath)
    var tick by remember { mutableIntStateOf(0) }
    val session = remember(bookHash) { CbzSession(bookHash, book.pages.size, store, onChange = { tick++ }) }

    var viewportReady by remember { mutableStateOf(false) }
    LaunchedEffect(bookHash) {
        session.ensureView()
        startPage?.let { session.goToPage(it) }
        viewportReady = true
    }

    // tick bumps on every page change, so reading it keeps the mark current
    val pageBookmarks = remember(annotations, tick) {
        annotations.filter { !it.deleted && it.kind == KIND_BOOKMARK && it.fixedAnchor?.pageIndex == session.pageIndex }
    }
    val toggleBookmark = {
        if (pageBookmarks.isEmpty()) {
            onAnnotationUpsert(newFixedBookmarkEntry(session.pageIndex))
        } else {
            pageBookmarks.forEach { onAnnotationDelete(it.id) }
        }
    }

    val zoom = remember { mutableFloatStateOf(1f) }
    val pan = remember { mutableStateOf(Offset.Zero) }

    val focusRequester = remember { FocusRequester() }
    Surface(
        Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            // a focused control that leaves composition (closed sheet, toolbar)
            // drops focus entirely and arrow keys stop; take it back. Launched
            // so it never runs once the screen itself is disposed.
            .onFocusChanged { if (!it.hasFocus) scope.launch { focusRequester.requestFocus() } }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight, Key.PageDown -> { scope.launch { session.turnPage(1) }; true }
                    Key.DirectionLeft, Key.PageUp -> { scope.launch { session.turnPage(-1) }; true }
                    else -> false
                }
            },
        color = MaterialTheme.colorScheme.background,
    ) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(56.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.IconButton(onClick = onBack) {
                    Text("‹", fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = toggleBookmark) {
                    val marked = pageBookmarks.isNotEmpty()
                    Icon(
                        imageVector = if (marked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = if (marked) "Remove bookmark" else "Add bookmark",
                        tint = if (marked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    book.title,
                    style = Type.body,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${book.pages.size}p",
                    style = Type.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }

            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (viewportReady && book.pages.isNotEmpty()) {
                    val href = book.pages[session.pageIndex.coerceIn(0, book.pages.lastIndex)]
                    val bitmap = images?.image(href)
                    if (bitmap != null) {
                        Image(
                            painter = BitmapPainter(bitmap),
                            contentDescription = "${session.pageIndex + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = zoom.value
                                    scaleY = zoom.value
                                    translationX = pan.value.x
                                    translationY = pan.value.y
                                }
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, gesturePan, gestureZoom, _ ->
                                        zoom.value = (zoom.value * gestureZoom).coerceIn(1f, 6f)
                                        pan.value = if (zoom.value > 1f) {
                                            pan.value + gesturePan
                                        } else {
                                            Offset.Zero
                                        }
                                    }
                                },
                        )
                    } else {
                        Box(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Page ${session.pageIndex + 1} could not be decoded", style = Type.body)
                        }
                    }
                    // tap zones (zoomed-out only; zoom/pan never moves the page)
                    if (zoom.value <= 1f) {
                        Box(
                            Modifier.fillMaxHeight().fillMaxWidth(0.33f)
                                .align(Alignment.CenterStart)
                                .pointerInput(Unit) {
                                    detectTapGestures {
                                        scope.launch { session.turnPage(-1) }; tick++
                                    }
                                },
                        )
                        Box(
                            Modifier.fillMaxHeight().fillMaxWidth(0.33f)
                                .align(Alignment.CenterEnd)
                                .pointerInput(Unit) {
                                    detectTapGestures {
                                        scope.launch { session.turnPage(1) }; tick++
                                    }
                                },
                        )
                    }
                }
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                SeekBar(
                    fraction = session.progress().toFloat(),
                    onSeek = { fraction ->
                        scope.launch {
                            session.goToPage((fraction * (book.pages.size - 1).coerceAtLeast(0)).toInt())
                            tick++
                        }
                    },
                    onSeekEnd = { /* fixed positions commit on every change */ },
                )
                Spacer(Modifier.height(4.dp))
                val total = book.pages.size
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "Page ${session.pageIndex + 1} of $total",
                        style = Type.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${(session.progress() * 100).toInt()}%",
                        style = Type.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun newFixedBookmarkEntry(pageIndex: Int) =
    AnnotationEntry(
        id = kotlin.uuid.Uuid.random().toString(),
        kind = KIND_BOOKMARK,
        fixedAnchor = FixedAnchorDto(pageIndex, emptyList()),
        createdAt = 0L, // repository stamps wall time via now()
        lamport = 0L,
    )
