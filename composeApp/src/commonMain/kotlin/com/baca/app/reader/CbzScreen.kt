package com.baca.app.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.ui.zIndex
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.baca.shared.domain.stats.PageOutcome
import com.baca.shared.domain.stats.PageReading
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
import com.baca.shared.domain.AnnotationEntry
import com.baca.shared.domain.FixedAnchorDto
import com.baca.shared.domain.loadDisplay
import com.baca.shared.domain.saveDisplay
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
    /** credits a page once it is turned away from, for reading stats (a comic's locations are its pages) */
    onPageRead: (suspend (PageReading) -> PageOutcome)? = null,
) {
    val scope = rememberCoroutineScope()
    val images = rememberBookImages(bookPath)
    val bookFonts by rememberBookFonts(bookPath, book.fonts)
    var tick by remember { mutableIntStateOf(0) }
    val session = remember(bookHash) {
        CbzSession(
            bookHash = bookHash,
            pageCount = book.pages.size,
            repo = store,
            onChange = { tick++ },
            pageHrefs = if (book.fixedPages.isNotEmpty()) book.pages else emptyList(),
        )
    }

    var viewportReady by remember { mutableStateOf(false) }
    LaunchedEffect(bookHash) {
        session.ensureView()
        startPage?.let { session.goToPage(it) }
        viewportReady = true
    }

    // Reading stats: each page's time in view, credited when it goes; a picture has no words to wait for
    val milestone = remember { mutableStateOf<String?>(null) }
    val reportPage by rememberUpdatedState(onPageRead)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val shownPage = remember(tick) { session.pageIndex }
    // read here, not in the effect: the effect would see the newest value before it is restarted with it
    val ready = viewportReady
    LaunchedEffect(ready, shownPage) {
        val report = reportPage ?: return@LaunchedEffect
        if (!ready || book.pages.isEmpty()) return@LaunchedEffect
        timeInView(lifecycle) { ms ->
            milestoneOf(report(PageReading(shownPage..shownPage, book.pages.size, emptyList(), ms, chars = 0)))
                ?.let { milestone.value = it }
        }
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
    var display by remember { mutableStateOf(store.loadDisplay()) }
    LaunchedEffect(display) { store.saveDisplay(display) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var barsVisible by remember { mutableStateOf(true) }
    var barsShowTick by remember { mutableIntStateOf(0) }
    var seeking by remember { mutableStateOf(false) }
    LaunchedEffect(barsVisible, barsShowTick, seeking, sheet) {
        if (barsVisible && !seeking && sheet == null) {
            kotlinx.coroutines.delay(BARS_AUTO_HIDE_MS)
            barsVisible = false
        }
    }
    fun turn(delta: Int) {
        barsVisible = false
        scope.launch { session.turnPage(delta) }
        tick++
    }

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
                    Key.DirectionRight, Key.PageDown -> { turn(1); true }
                    Key.DirectionLeft, Key.PageUp -> { turn(-1); true }
                    else -> false
                }
            },
        color = MaterialTheme.colorScheme.background,
    ) {
        FullScreen(enabled = display.fullScreen && !barsVisible)
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        PlatformBackHandler {
            if (sheet != null) sheet = null else onBack()
        }
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = barsVisible,
                modifier = Modifier.align(Alignment.TopCenter).zIndex(1f),
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
            ) {
                Box(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                        .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                        .padding(horizontal = 4.dp).height(56.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.CenterStart),
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { barsShowTick++; toggleBookmark() }) {
                            val marked = pageBookmarks.isNotEmpty()
                            Icon(
                                imageVector = if (marked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = if (marked) "Remove bookmark" else "Add bookmark",
                                tint = if (marked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Text(
                        book.title,
                        style = Type.body,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 104.dp),
                    )
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        IconButton(onClick = {
                            barsShowTick++
                            sheet = if (sheet == "bookmarks") null else "bookmarks"
                        }) {
                            Icon(
                                imageVector = Icons.Filled.FormatAlignJustify,
                                contentDescription = "Contents",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = {
                            barsShowTick++
                            sheet = if (sheet == "display") null else "display"
                        }) {
                            Text(
                                "Aa",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.semantics { contentDescription = "Display settings" },
                            )
                        }
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onDragEnd = {
                                if (zoom.floatValue <= 1f) {
                                    val threshold = size.width * 0.15f
                                    if (totalDrag < -threshold) turn(1)
                                    else if (totalDrag > threshold) turn(-1)
                                }
                            },
                        ) { change, dragAmount ->
                            if (zoom.floatValue <= 1f) {
                                change.consume()
                                totalDrag += dragAmount
                            }
                        }
                    },
            ) {
                if (viewportReady && book.pages.isNotEmpty()) {
                    val pageIdx = session.pageIndex.coerceIn(0, book.pages.lastIndex)
                    val fixedPage = book.fixedPages.getOrNull(pageIdx)
                    val href = book.pages[pageIdx]
                    val zoomModifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoom.value
                            scaleY = zoom.value
                            translationX = pan.value.x
                            translationY = pan.value.y
                        }
                        .pinchZoomAndPan(zoom, pan)
                    if (fixedPage != null) {
                        val loadedFonts = bookFonts
                        if (loadedFonts != null) {
                            FixedPageView(
                                page = fixedPage,
                                images = images,
                                bookFonts = loadedFonts,
                                modifier = zoomModifier,
                            )
                        }
                    } else {
                        val bitmap = images.image(href)
                        if (bitmap != null) {
                            Image(
                                painter = BitmapPainter(bitmap),
                                contentDescription = "${session.pageIndex + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = zoomModifier,
                            )
                        } else {
                            Box(
                                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Page ${session.pageIndex + 1} could not be decoded", style = Type.body)
                            }
                        }
                    }
                    // tap zones (zoomed-out only; zoom/pan never moves the page)
                    if (zoom.value <= 1f) {
                        Box(
                            Modifier.fillMaxHeight().fillMaxWidth(0.33f)
                                .align(Alignment.CenterStart)
                                .pointerInput(Unit) {
                                    detectTapGestures { turn(-1) }
                                },
                        )
                        Box(
                            Modifier.fillMaxHeight().fillMaxWidth(0.34f)
                                .align(Alignment.Center)
                                .pointerInput(Unit) {
                                    detectTapGestures {
                                        barsVisible = !barsVisible
                                        if (barsVisible) barsShowTick++
                                    }
                                },
                        )
                        Box(
                            Modifier.fillMaxHeight().fillMaxWidth(0.33f)
                                .align(Alignment.CenterEnd)
                                .pointerInput(Unit) {
                                    detectTapGestures { turn(1) }
                                },
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = barsVisible,
                modifier = Modifier.align(Alignment.BottomCenter).zIndex(1f),
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
            ) {
                Column(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    SeekBar(
                        fraction = session.progress().toFloat(),
                        onSeek = { fraction ->
                            seeking = true
                            barsShowTick++
                            scope.launch {
                                session.goToPage((fraction * (book.pages.size - 1).coerceAtLeast(0)).toInt())
                                tick++
                            }
                        },
                        onSeekEnd = {
                            seeking = false
                            barsShowTick++
                        },
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
            if (sheet != null) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                        .clickable { sheet = null },
                )
                Column(
                    Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .pointerInput(Unit) { detectTapGestures { } }
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .heightIn(max = 560.dp),
                ) {
                    Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp, bottom = 12.dp)) {
                        Box(
                            Modifier.width(36.dp).height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.outline),
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (sheet == "bookmarks") "Bookmarks" else "Display",
                            style = Type.cardTitle,
                            fontSize = 18.sp,
                        )
                        TextButton(onClick = { sheet = null }) {
                            Text("Done", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (sheet == "bookmarks") {
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                            AnnotationCards(
                                entries = annotations.filter { !it.deleted && it.kind == KIND_BOOKMARK },
                                book = null,
                                ink = MaterialTheme.colorScheme.onSurface,
                                onTap = { entry ->
                                    val target = entry.fixedAnchor?.pageIndex ?: return@AnnotationCards
                                    scope.launch { session.goToPage(target) }
                                    tick++
                                    sheet = null
                                },
                            )
                        }
                    } else {
                        DisplayContent(display) { display = it }
                    }
                }
            }
            MilestoneNote(milestone, Modifier.align(Alignment.TopCenter).padding(top = 64.dp))
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
