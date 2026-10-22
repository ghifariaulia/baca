package com.baca.app.reader


import com.baca.shared.render.chapterEvenPx
import com.baca.shared.render.ruleBorderPx
import com.baca.shared.render.evenLinesPx
import com.baca.shared.render.placeIn
import com.baca.shared.render.SOFT_HYPHEN
import com.baca.shared.render.sizePx
import com.baca.shared.render.heightPx

import com.baca.shared.render.fontScale

import com.baca.shared.parser.TextAlign as BookAlign

import androidx.compose.ui.input.pointer.PointerInputChange

import androidx.compose.ui.input.pointer.AwaitPointerEventScope

import androidx.compose.ui.zIndex

import androidx.compose.ui.graphics.luminance

import androidx.compose.ui.input.pointer.PointerEventPass


import androidx.compose.foundation.gestures.awaitFirstDown

import androidx.compose.foundation.gestures.awaitEachGesture

import androidx.compose.animation.animateColorAsState

import androidx.compose.animation.slideOutVertically

import androidx.compose.animation.slideInVertically

import androidx.compose.animation.fadeOut

import androidx.compose.animation.fadeIn

import androidx.compose.animation.AnimatedVisibility

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

import kotlinx.coroutines.sync.withLock

import kotlinx.coroutines.sync.Mutex

import kotlinx.coroutines.coroutineScope

import androidx.compose.ui.layout.onSizeChanged

import androidx.compose.ui.input.pointer.util.VelocityTracker

import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.runtime.rememberUpdatedState

import androidx.compose.animation.core.tween

import androidx.compose.animation.core.spring

import androidx.compose.animation.core.LinearOutSlowInEasing

import androidx.compose.animation.core.FastOutLinearInEasing

import androidx.compose.animation.core.Animatable

import androidx.compose.foundation.layout.windowInsetsPadding



import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baca.app.ui.readerFonts
import com.baca.app.ui.ReaderThemes
import com.baca.app.ui.isDarkScheme
import com.baca.app.ui.Type
import com.baca.shared.domain.DisplaySettings
import com.baca.shared.domain.reader.PositionStore
import com.baca.shared.domain.reader.indexOfChapter
import com.baca.shared.domain.reader.Viewport
import com.baca.shared.domain.decodeDisplay
import com.baca.shared.domain.loadDisplay
import com.baca.shared.domain.saveDisplay
import com.baca.shared.domain.encode
import com.baca.shared.domain.reader.ReaderSession
import com.baca.shared.parser.Block
import com.baca.shared.parser.BookFace
import com.baca.shared.parser.FormattedSpan
import com.baca.shared.parser.TEXT_COLOR
import com.baca.shared.parser.INVISIBLE
import com.baca.shared.domain.reader.SearchHit
import com.baca.shared.domain.reader.SelectionPoint
import com.baca.shared.domain.reader.TextSelection
import com.baca.shared.parser.BlockKind
import com.baca.shared.parser.Chapter
import com.baca.shared.parser.ParseOptions
import com.baca.shared.parser.ParsedEpub
import com.baca.shared.sync.PointDto
import com.baca.shared.sync.ReflowAnchorDto
import com.baca.shared.render.PageBox
import com.baca.shared.render.PageLine
import com.baca.shared.render.ReflowLayout
import com.baca.shared.render.ReflowSettings
import kotlinx.coroutines.launch

/** Provided to page rendering so image blocks can be decoded and drawn. */
val LocalBookImages = compositionLocalOf<BookImages?> { null }

/**
 * Reader screen (docs/TECH.md 10.4): top bar, paged text column, footer
 * progress, tap zones, Aa settings panel. Business logic lives in
 * [ReaderSession]; this composable only wires UI state to it.
 */
@Composable
fun ReaderScreen(
    book: ParsedEpub,
    bookPath: String,
    bookHash: String,
    store: PositionStore,
    onBack: () -> Unit,
    openContents: Boolean = false,
    /** This book's annotations (highlights + notes), synced via M4. */
    annotations: List<com.baca.shared.sync.AnnotationEntry> = emptyList(),
    /** annotation id → originating device label (notes meta line). */
    deviceLabels: Map<String, String> = emptyMap(),
    onAnnotationUpsert: (com.baca.shared.sync.AnnotationEntry) -> Unit = {},
    onAnnotationDelete: (id: String) -> Unit = {},
    /** Open the reader straight on an annotation anchor (Notes list). */
    jumpToAnchor: AnchorJump? = null,
    /** A setting that changes how the book is read (colour scheme, direction, CSS, replacements) changed: read it again under [ParseOptions]. */
    onOptionsChange: (ParseOptions) -> Unit = {},
) {
    var display by remember { mutableStateOf(store.loadDisplay()) }
    var sheet by remember { mutableStateOf(if (openContents) SheetKind.CONTENTS else SheetKind.NONE) }
    var sheetTab by remember { mutableStateOf(ContentsTab.CONTENTS) }
    var viewport by remember { mutableStateOf<Viewport?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    // Text being selected: a long press picks a word, its handles drag the ends
    var selection by remember { mutableStateOf<TextSelection?>(null) }
    // in-book search: the query stays while the book is open; the picked match is tinted (chapter href → range)
    var searchQuery by remember { mutableStateOf("") }
    var searchMatch by remember { mutableStateOf<Pair<String, TextSelection>?>(null) }
    // Note editor sheet (new or existing annotation)
    var editing by remember { mutableStateOf<EditorState?>(null) }
    // a picture open full screen, by its src
    var fullPicture by remember { mutableStateOf<String?>(null) }

    // at the running text's optical size, for measuring and drawing alike (the layout factory, built once, reads
    // the latest); one set per size, as new families each recomposition would resolve every font again
    val fontsNow = readerFonts(display.fontSizeSp)
    val appFonts by rememberUpdatedState(remember(display.fontSizeSp) { fontsNow })
    val images = rememberBookImages(bookPath, book.binaries)
    // the fonts the book ships, by bookFontName; null until read (the layout factory reads the latest)
    val bookFontsState = rememberBookFonts(bookPath, book.fonts)
    val bookFonts by rememberUpdatedState(bookFontsState.value.orEmpty())
    // the patterns for each language the book is written in; null until read
    val languages = remember(book) { (listOfNotNull(book.language) + book.chapters.flatMap { c -> c.blocks.mapNotNull { (it as? Block.Text)?.lang } }).toSet() }
    val hyphenatorsState = rememberHyphenators(languages)
    val hyphenators by rememberUpdatedState(hyphenatorsState.value.orEmpty())
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    /** Chapter [index]'s page height: the room for text less its own even lines. */
    fun pageHeightOf(index: Int, vp: Viewport, settings: ReflowSettings): Int =
        if (book.vertical) vp.pageHeightPx else (vp.textHeightPx - settings.chapterEvenPx(book.chapters[index].blocks, vp.textHeightPx) * 2).toInt()

    val session = remember(bookHash, book) {
        ReaderSession(
            bookHash = bookHash,
            chapters = book.chapters,
            repo = store,
            layoutFactory = { index, vp, settings ->
                ReflowLayout(
                    blocks = book.chapters[index].blocks,
                    metrics = ComposeTextMetrics(
                        textMeasurer, density, imageAspect = { images.aspect(it) }, language = book.language,
                        hyphenatorFor = { language -> language?.let { hyphenators[it] } },
                    ) { name ->
                        name?.let { bookFonts[it] ?: readerFontFamily(it, appFonts) }
                    },
                    settings = settings,
                    columnWidthPx = vp.columnWidthPx,
                    pageHeightPx = pageHeightOf(index, vp, settings),
                    imageHeight = { img: Block.Image ->
                        images.preload(img.src)?.let { bitmap ->
                            // within its containing block, as foliate holds it to 100% of that
                            val (_, widthPx) = img.placeIn(settings.fontSizePx, settings.cssPx, vp.columnWidthPx, vp.roomFor(pageHeightOf(index, vp, settings)))
                            img.box.sizePx(bitmap.width, bitmap.height, density.density, settings.fontSizePx, widthPx, pageHeightOf(index, vp, settings), vp.marginPx, vp.windowWidthPx, vp.windowHeightPx).heightPx
                        }
                    },
                    windowHeightPx = vp.windowHeightPx,
                    windowWidthPx = vp.windowWidthPx,
                )
            },
            onChange = { tick++ },
            toc = book.toc,
            textStart = book.textStart,
        )
    }

    LaunchedEffect(jumpToAnchor) {
        jumpToAnchor?.let { jump ->
            session.jumpToAnchor(jump.href, jump.blockIndex, jump.charOffset)
        }
    }

    LaunchedEffect(display) { store.saveDisplay(display) }
    // debounce so continuous window drags do not re-layout every frame
    LaunchedEffect(session, viewport, display, bookFontsState.value != null, hyphenatorsState.value != null) {
        val vp = viewport ?: return@LaunchedEffect
        // pages are measured in the book's own fonts and hyphenated by its languages' patterns: wait for them
        if (bookFontsState.value == null || hyphenatorsState.value == null) return@LaunchedEffect
        kotlinx.coroutines.delay(150)
        scope.launch { session.ensureView(vp, reflowSettingsOf(display, density)) }
    }

    val colors = ReaderThemes.forTheme(display.theme)
    val schemeDark = isDarkScheme(colors.background)
    val readOptions = display.parseOptions(schemeDark)
    LaunchedEffect(readOptions) { if (book.options.needsReread(book, readOptions)) onOptionsChange(readOptions) }

    val chapterIndex = session.chapterIndex.coerceIn(0, book.chapters.lastIndex)
    val chapter = book.chapters[chapterIndex]
    val layout = remember(chapterIndex, tick) { session.layoutFor(chapterIndex) }
    val accent = MaterialTheme.colorScheme.primary
    // block text with highlight background styles merged in per line (TECH 10.3)
    // keyed on the chapter too: the reader starts on the first chapter and moves to the saved one
    val bookAnnotations = remember(annotations, chapter.href) {
        annotations.filter { !it.deleted && it.reflowAnchor?.href == chapter.href }
    }
    val blockStrings = remember(chapter, display.theme, display.font, accent, bookAnnotations, layout) {
        val dark = colors.background.luminance() < 0.5f
        chapter.blocks.map { block ->
            val spacers = (block as? Block.Text)?.let { layout.spacersOf(it) }.orEmpty()
            annotatedOf(block, colors.ink, dark, spacers, (block as? Block.Text)?.let(layout::lineRatioOf) ?: 1f, reflowSettingsOf(display, density).fontSizePx) { span ->
                span.family?.let { bookFonts[bookFontName(it)] } ?: span.face?.let { readerFontFamily(faceFontName(it), appFonts) }
            }
        }
    }
    val pictures = rememberInlineImages(chapter, images, colors.background)
    val inlineImages = remember(pictures, layout) {
        pictures + spacerContent(chapter.blocks.filterIsInstance<Block.Text>().flatMap { layout.spacersOf(it).filterNotNull() }, density)
    }
    val pageCount = layout.pageCount()
    val pageNo = session.pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    // a vertical book's page is one square; the others may show a spread of columns, or scroll
    val scrolled = display.flow == "scrolled" && !book.vertical
    val columns = if (book.vertical || scrolled) 1 else display.columns
    val page = layout.pages[pageNo]
    // where each drawn line of this page sits, for selection handles to find text under a finger
    val lineGeometry = remember(chapter.href, pageNo, layout) { HashMap<Pair<Int, Int>, LineGeometry>() }
    LaunchedEffect(chapter.href, pageNo) { selection = null }
    val selectionTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    val matchHere = searchMatch?.takeIf { it.first == chapter.href }?.second
    val pickSearchHit: (SearchHit) -> Unit = { hit ->
        sheet = SheetKind.NONE
        val href = book.chapters[hit.chapterIndex].href
        searchMatch = href to TextSelection(
            SelectionPoint(hit.blockIndex, hit.charOffset),
            SelectionPoint(hit.blockIndex, hit.charOffset + hit.length),
        )
        scope.launch { session.jumpToAnchor(href, hit.blockIndex, hit.charOffset) }
    }


    // Page turns slide: the page moves out toward the turn and fades, the next
    // one slides in from the other side. A swipe drags the page first.
    val pageShift = remember { Animatable(0f) }
    val pageAlpha = remember { Animatable(1f) }
    val turnLock = remember { Mutex() }
    var pageWidthPx by remember { mutableIntStateOf(0) }

    val pageTurn: (Int) -> Unit = { delta ->
        scope.launch {
            turnLock.withLock {
                if (scrolled) {
                    // the list follows the page; nothing slides
                    if (session.canTurn(delta)) session.turnPage(delta)
                    return@withLock
                }
                if (!session.canTurn(delta)) {
                    pageShift.animateTo(0f, spring())
                    return@withLock
                }
                // next page: content leaves to the left, to the right in a book read right to left
                val toward = (if (delta > 0) -1f else 1f) * (if (book.pageRtl) -1f else 1f)
                val travel = maxOf(abs(pageShift.value), pageWidthPx * TURN_TRAVEL)
                coroutineScope {
                    launch { pageShift.animateTo(toward * travel, tween(TURN_MS, easing = FastOutLinearInEasing)) }
                    launch { pageAlpha.animateTo(0f, tween(TURN_MS)) }
                }
                session.turnPage(delta * columns)
                pageShift.snapTo(-toward * pageWidthPx * TURN_TRAVEL)
                coroutineScope {
                    launch { pageShift.animateTo(0f, tween(TURN_MS, easing = LinearOutSlowInEasing)) }
                    launch { pageAlpha.animateTo(1f, tween(TURN_MS)) }
                }
            }
        }
    }

    // released swipe: turn when dragged far enough or flicked, else spring back
    val swipeEnded: (velocityX: Float) -> Unit = { velocityX ->
        // a book read right to left is turned by swiping the other way
        val flip = if (book.pageRtl) -1f else 1f
        val dragged = pageShift.value * flip
        val velocity = velocityX * flip
        val delta = when {
            dragged < -pageWidthPx * SWIPE_FRACTION || velocity < -FLING_VELOCITY -> 1
            dragged > pageWidthPx * SWIPE_FRACTION || velocity > FLING_VELOCITY -> -1
            else -> 0
        }
        if (delta == 0) scope.launch { pageShift.animateTo(0f, spring()) } else pageTurn(delta)
    }
    val currentSwipeEnded by rememberUpdatedState(swipeEnded)

    // resolved through the layout so a bookmark stays on its page after reflow
    val pageBookmarks = remember(annotations, layout, pageNo) {
        annotations.filter { entry ->
            val anchor = entry.reflowAnchor
            !entry.deleted && entry.kind == KIND_BOOKMARK && anchor != null && anchor.href == chapter.href &&
                layout.pageForPosition(anchor.start.blockIndex, anchor.start.charOffset, anchor.quote)?.pageIndex == pageNo
        }
    }
    // Contents / Notes tab body, shared by the phone sheet and the desktop panel
    @Composable
    fun ContentsTabBody(tab: ContentsTab, onJumped: () -> Unit) {
        when (tab) {
            ContentsTab.CONTENTS -> ContentsList(book, session, tick, accent, MaterialTheme.colorScheme.onSurface) { href ->
                scope.launch { session.jumpToHref(href) }
                onJumped()
            }
            ContentsTab.NOTES, ContentsTab.BOOKMARKS -> AnnotationCards(
                entries = annotations.filter { !it.deleted && (it.kind == KIND_BOOKMARK) == (tab == ContentsTab.BOOKMARKS) },
                book = book,
                ink = MaterialTheme.colorScheme.onSurface,
                onTap = { entry ->
                    scope.launch {
                        val anchor = entry.reflowAnchor ?: return@launch
                        session.jumpToAnchor(anchor.href, anchor.start.blockIndex, anchor.start.charOffset)
                    }
                    onJumped()
                },
                onEdit = { entry -> editing = EditorState(existing = entry) },
            )
        }
    }

    val toggleBookmark = {
        if (pageBookmarks.isEmpty()) {
            onAnnotationUpsert(newBookmarkEntry(chapter.href, layout.positionForPage(pageNo)))
        } else {
            pageBookmarks.forEach { onAnnotationDelete(it.id) }
        }
    }

    // key events reach the composition only through a focused node (TECH 13.3)
    val focusRequester = remember { FocusRequester() }
    // reader chrome (bars, sheets, panels) follows the app theme like every
    // other screen; only the page area takes the reading theme's colors
    // top bar and footer float over the page and hide on a center tap; the
    // page keeps its size, so hiding them never re-paginates the book
    var barsVisible by remember { mutableStateOf(true) }
    // set when a tap opened a highlight or link, so the page tap skips it;
    // a remembered holder, not state: read and written within one gesture
    val textUsedTap = remember { booleanArrayOf(false) }
    val appDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val darkReading = colors.background.luminance() < 0.5f
    SystemBarsAppearance(darkBackground = if (barsVisible) appDark else darkReading)
    // the status bar strip continues whatever is at the top: the bar or the page
    val edgeColor by animateColorAsState(if (barsVisible) MaterialTheme.colorScheme.surface else colors.background)
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
                    Key.PageDown -> { pageTurn(1); true }
                    Key.PageUp -> { pageTurn(-1); true }
                    // the arrow toward the page's far edge, which is the left one in a book read right to left
                    Key.DirectionRight -> { pageTurn(if (book.pageRtl) -1 else 1); true }
                    Key.DirectionLeft -> { pageTurn(if (book.pageRtl) 1 else -1); true }
                    else -> false
                }
            },
        color = edgeColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        // the edge color fills behind the system bars; content stays clear of them
        // Focus mode: the phone's status and navigation bars hide with the reader's bars.
        // The page keeps the room they take when shown, so hiding them never re-paginates.
        FullScreen(enabled = display.fullScreen && !barsVisible)
        BoxWithConstraints(
            Modifier.fillMaxSize().windowInsetsPadding(stableReaderInsets()),
        ) {
        val isDesktop = maxWidth >= 900.dp
        // LibreraX's side padding: a share of the page's width (on a wide window, the column's)
        // LibreraX's WebView is a whole number of CSS px wide (412 for a 411.4dp screen): its
        // margins and column are shares of that, a hair wider than the screen
        val pageWidth = pageWidthOf(maxWidth, readerColumnCapped)
        val margin = pageWidth * (display.paddingPercent / 100f)
        // and above and below the text, 4 CSS px a percent; below, at least the footer's strip,
        // the system's bar under it counting towards the band as LibreraX's page counts it
        val band = PADDING_BAND * display.paddingPercent
        val barBelow = with(density) { stableReaderInsets().getBottom(density).toDp() }
        var panelOpen by remember { mutableStateOf(false) }
        // Back closes what's open on top of the page first, then leaves the book
        PlatformBackHandler {
            when {
                fullPicture != null -> fullPicture = null
                editing != null -> editing = null
                selection != null -> selection = null
                sheet != SheetKind.NONE -> sheet = SheetKind.NONE
                panelOpen -> panelOpen = false
                else -> onBack()
            }
        }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                // Top bar: back · centered title · contents + Aa (design file 'Reader')
                AnimatedVisibility(
                    visible = barsVisible,
                    modifier = Modifier.align(Alignment.TopCenter).zIndex(1f),
                    enter = fadeIn() + slideInVertically { -it },
                    exit = fadeOut() + slideOutVertically { -it },
                ) {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 4.dp).height(56.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.widthIn(max = 132.dp).weight(1f),
                    ) {
                        IconButton(onClick = onBack) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = toggleBookmark) {
                            val marked = pageBookmarks.isNotEmpty()
                            androidx.compose.material3.Icon(
                                imageVector = if (marked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = if (marked) "Remove bookmark" else "Add bookmark",
                                tint = if (marked) accent else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Text(
                        book.title.ifBlank { com.baca.shared.AppInfo.name },
                        style = Type.body,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = {
                            sheet = if (sheet == SheetKind.SEARCH) SheetKind.NONE else SheetKind.SEARCH
                        }) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = {
                            if (isDesktop) panelOpen = !panelOpen else sheet = SheetKind.CONTENTS
                        }) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Filled.FormatAlignJustify,
                                contentDescription = "Contents",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = {
                            if (sheet == SheetKind.DISPLAY) sheet = SheetKind.NONE else sheet = SheetKind.DISPLAY
                        }) {
                            Text("Aa", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { contentDescription = "Display settings" })
                        }
                    }
                }
                }

                BoxWithConstraints(
                    Modifier.fillMaxSize()
                        .background(colors.background)
                        .onSizeChanged { pageWidthPx = it.width }
                        // Page taps, watched in the final pass so the text below keeps
                        // its own gestures: only a quick, still tap the text didn't
                        // use counts. Edges turn the page (phones, unless switched
                        // off), the middle shows or hides the bars.
                        .pointerInput(display.tapToTurn) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                                textUsedTap[0] = false
                                val up = awaitUp(PointerEventPass.Final) ?: return@awaitEachGesture
                                val moved = (up.position - down.position).getDistance() > viewConfiguration.touchSlop
                                val held = up.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis
                                if (moved || held || textUsedTap[0]) return@awaitEachGesture
                                val edgeTurns = tapZonesForPageTurnEnabled && display.tapToTurn
                                when {
                                    down.position.x < size.width / 3f -> if (edgeTurns) pageTurn(if (book.pageRtl) 1 else -1)
                                    down.position.x > size.width * 2f / 3f -> if (edgeTurns) pageTurn(if (book.pageRtl) -1 else 1)
                                    else -> barsVisible = !barsVisible
                                }
                            }
                        }
                        .then(
                            if (tapZonesForPageTurnEnabled) {
                                Modifier.pointerInput(Unit) {
                                    val velocity = VelocityTracker()
                                    detectHorizontalDragGestures(
                                        onDragStart = { velocity.resetTracking() },
                                        onDragEnd = { currentSwipeEnded(velocity.calculateVelocity().x) },
                                        onDragCancel = { currentSwipeEnded(0f) },
                                    ) { change, dragAmount ->
                                        velocity.addPosition(change.uptimeMillis, change.position)
                                        change.consume()
                                        scope.launch { pageShift.snapTo(pageShift.value + dragAmount) }
                                    }
                                }
                            } else {
                                Modifier
                            },
                        )
                        // the footer's strip, in the page colour; text stops above it
                        .padding(bottom = maxOf(READER_FOOTER_HEIGHT, band - barBelow)),
                ) {
                    val physicalColumnPx = with(density) {
                        (pageWidth.toPx() - margin.toPx() * 2).roundToInt().coerceAtLeast(0)
                    }
                    // a vertical book is laid out turned a quarter: its lines are as long as the page is tall,
                    // and stack across the width (drawn rotated, see rotatedPage)
                    val vertical = book.vertical
                    // LibreraX's vertical page is a square, a page's width on each side, centred in the height
                    // each column of a spread has its own margins
                    val columnWidthPx = if (columns > 1) with(density) {
                        (pageWidth.toPx() / columns - margin.toPx() * 2).roundToInt().coerceAtLeast(0)
                    } else physicalColumnPx
                    // LibreraX's page: a band above the text, then whole lines, with what is
                    // left under the last one split above and below
                    // to the fraction of a px, as the WebView's page is laid out in css px
                    val bandPx = with(density) { band.toPx() }
                    val textHeightPx = (constraints.maxHeight - bandPx).coerceAtLeast(0f)
                    val runningEvenPx = reflowSettingsOf(display, density).evenLinesPx(textHeightPx)
                    // the chapter shown: its own lines, from its first paragraph
                    val evenPx = reflowSettingsOf(display, density).chapterEvenPx(chapter.blocks, textHeightPx)
                    val inner = Viewport(
                        columnWidthPx = columnWidthPx,
                        pageHeightPx = if (vertical) physicalColumnPx else (textHeightPx - runningEvenPx * 2).toInt(),
                        textHeightPx = textHeightPx,
                        marginPx = bandPx,
                        windowWidthPx = constraints.maxWidth,
                        // the footer's strip below the page is the window's too
                        windowHeightPx = constraints.maxHeight + with(density) { maxOf(READER_FOOTER_HEIGHT, band - barBelow).roundToPx() },
                    )
                    LaunchedEffect(inner) { viewport = inner }

                        Column(
                            if (vertical) {
                                // the swipe moves the page across the screen; the rotation is inside it
                                Modifier.fillMaxSize().graphicsLayer {
                                    translationX = pageShift.value
                                    alpha = pageAlpha.value
                                }.wrapContentSize(Alignment.Center).requiredSize(pageWidth).rotatedPage().padding(top = margin)
                            } else {
                                Modifier.fillMaxSize().padding(top = with(density) { (bandPx + evenPx).toDp() }).graphicsLayer {
                                    translationX = pageShift.value
                                    alpha = pageAlpha.value
                                }
                            },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                        // one page's lines, drawn in a column of its own; two columns draw two pages side by side
                        val pageColumn: @Composable (Int) -> Unit = { pageNo ->
                        val page = layout.pages[pageNo]
                        Column(
                            Modifier.width(with(density) { (columnWidthPx + margin.roundToPx() * 2).toDp() })
                                .padding(horizontal = margin)
                                // the book's boxes (borders, backgrounds) behind the lines they hold
                                .drawBoxes(layout.boxesOf(pageNo), colors.ink, dark = colors.background.luminance() < 0.5f),
                        ) {
                            val bodyFontPx = with(density) { display.fontSizeSp.sp.toPx() }
                            // one drawn text line: in its block's box (its cell's column in a table, its own in columns), justified as body text
                            val textLine: @Composable (PageLine) -> Unit = { line ->
                                val block = chapter.blocks[line.blockIndex] as Block.Text
                                // a drop cap is drawn on its own: the line's text starts after it
                                val cap = block.dropCap?.takeIf { line.start == 0 }
                                val textStart = cap?.end ?: line.start
                                val ranges = highlightRangesOf(
                                    bookAnnotations,
                                    chapter.href,
                                    line.blockIndex,
                                    textStart,
                                    line.endExclusive,
                                )
                                val style = lineStyle(block, layout.lineRatioOf(block), display, colors.ink, appFonts)
                                // book CSS centred/right lines and headings stay unjustified
                                val body = block.kind != BlockKind.HEADING && block.align == BookAlign.START && !block.keepsSpaces && !block.ragged
                                // where ReflowLayout measured the line: the block's margins, padding and text-indent
                                val box = layout.lineBoxOf(line.blockIndex, line.column)
                                // beside a drop cap, lines start after it, as ReflowLayout measured them
                                val indentPx = if (block.dropCap != null) box.startPx + line.floatIndentPx else box.linePx(firstLine = line.start == 0)
                                // right-to-left lines start at the right edge: the indent is there, and the left is the block's own inset
                                val leftPx = if (block.rtl) box.startPx else indentPx
                                val rightPx = if (block.rtl) box.rightPx(line.start == 0) else box.endPx
                                val lineText = highlighted(
                                    blockStrings[line.blockIndex],
                                    ranges,
                                    textStart,
                                    line.endExclusive,
                                    (selection ?: matchHere)?.rangeIn(line.blockIndex, block.text.length),
                                    selectionTint,
                                ).let { text ->
                                    // a hyphenated break: the text has no hyphen (or an invisible soft one), the measured line drew one
                                    val soft = line.endExclusive > 0 && block.text[line.endExclusive - 1] == SOFT_HYPHEN
                                    if (soft || (block.hyphenate && breaksWord(block.text, line.endExclusive))) text + AnnotatedString("-") else text
                                }.let { text ->
                                    if (body && !vertical && endsMidParagraph(block.text, line.endExclusive)) {
                                        justified(
                                            text, style, columnWidthPx - leftPx - rightPx, textMeasurer, density,
                                            inlinePlaceholders(block, textStart, line.endExclusive, images, layout.spacersOf(block), density),
                                        )
                                    } else {
                                        text
                                    }
                                }
                                Box(
                                    Modifier.fillMaxWidth()
                                        // a line grown round a raised mark keeps its own height lower down
                                        .padding(top = with(density) { line.growAbove.toDp() })
                                        .insetBy(leftPx, rightPx),
                                    // START is where the text begins: the right edge for right-to-left text
                                    contentAlignment = when (block.align) {
                                        BookAlign.CENTER -> Alignment.TopCenter
                                        BookAlign.END -> if (block.rtl) Alignment.TopStart else Alignment.TopEnd
                                        BookAlign.START -> if (block.rtl) Alignment.TopEnd else Alignment.TopStart
                                    },
                                ) {
                                    if (line.start == 0 && block.marker != null) {
                                        ListMarker(block.marker!!, style, shiftPx = box.firstLinePx + line.floatIndentPx)
                                    }
                                    if (cap != null) {
                                        DropCapText(
                                            highlighted(blockStrings[line.blockIndex], emptyList(), 0, cap.end),
                                            style, block.spans.first().scale * cap.lineRatio, line.floatIndentPx,
                                        )
                                    }
                                    if (vertical) {
                                        VerticalLineText(lineText, style, Modifier.fillMaxWidth().height(with(density) { style.lineHeight.toDp() }))
                                    } else if (block.spans.any { it.href != null }) {
                                        LinkText(
                                            lineText,
                                            style,
                                            inlineImages,
                                            onPlaced = { textLayout, topLeft ->
                                                lineGeometry[line.blockIndex to textStart] =
                                                    LineGeometry(line.blockIndex, textStart, line.endExclusive, textLayout, topLeft)
                                            },
                                            onLink = { href ->
                                                textUsedTap[0] = true
                                                if (href.startsWith("http")) openExternalLink(href)
                                                else scope.launch { session.jumpToHref(href) }
                                            },
                                            onLongPress = { offset ->
                                                val at = (textStart + offset).coerceAtMost(line.endExclusive - 1)
                                                selection = TextSelection.wordAt(chapter.blocks, line.blockIndex, at)
                                            },
                                        )
                                    } else {
                                        TappableText(
                                            lineText,
                                            style,
                                            inlineImages,
                                            ranges,
                                            textStart,
                                            onTapAnnotation = { entry ->
                                                textUsedTap[0] = true
                                                editing = EditorState(existing = entry)
                                            },
                                            onPlaced = { textLayout, topLeft ->
                                                lineGeometry[line.blockIndex to textStart] =
                                                    LineGeometry(line.blockIndex, textStart, line.endExclusive, textLayout, topLeft)
                                            },
                                            onLongPress = { offset ->
                                                val at = (textStart + offset).coerceAtMost(line.endExclusive - 1)
                                                selection = TextSelection.wordAt(chapter.blocks, line.blockIndex, at)
                                            },
                                        )
                                    }
                                }
                            }
                            val pageGaps = layout.gapsOf(pageNo)
                            // each row between whole pixels, its exact height rounded where it runs on
                            val rowHeights = layout.rowHeightsOf(pageNo)
                            var firstOnPage = true
                            page.forEachIndexed { lineIndex, line ->
                                if (line.joinsPrevious && !firstOnPage) {
                                    // drawn in the row before it
                                    return@forEachIndexed
                                }
                                // the gap packPages left above this line
                                val gapPx = pageGaps[lineIndex]
                                if (gapPx > 0) Spacer(Modifier.fillMaxWidth().height(with(density) { gapPx.toDp() }))
                                firstOnPage = false
                                when {
                                    line.isImage && chapter.blocks[line.blockIndex] is Block.Rule ->
                                        RuleLine(chapter.blocks[line.blockIndex] as Block.Rule, line.heightPx ?: 0, if (isDarkScheme(colors.background)) colors.ink else RULE_GRAY)
                                    // a picture stands on its line's baseline: the line's reach above it, the picture, the reach below
                                    line.isImage -> Box(
                                        Modifier.fillMaxWidth().height(with(density) { rowHeights[lineIndex].toDp() })
                                            .padding(top = with(density) { line.growAbove.toDp() }),
                                    ) {
                                        ImageLine(chapter, line.blockIndex, line.heightPx, inner, bodyFontPx, colors.background) { fullPicture = it }
                                    }
                                    else -> {
                                        // a table row's other cells, or a right float, join this line's row
                                        val joined = page.drop(lineIndex + 1).takeWhile { it.joinsPrevious }
                                        Box(Modifier.fillMaxWidth().height(with(density) { rowHeights[lineIndex].toDp() })) {
                                            (listOf(line) + joined).forEach { each ->
                                                // a table cell in the middle or at the bottom of its row is drawn lower in it
                                                Box(Modifier.offset { IntOffset(0, each.dropPx.roundToInt()) }) { textLine(each) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        }
                        if (scrolled) {
                            // the chapter's pages one under another; the page on top is the reading position
                            key(layout) {
                                val listState = rememberLazyListState(pageNo)
                                var jumping by remember { mutableStateOf(false) }
                                LaunchedEffect(pageNo) {
                                    if (listState.firstVisibleItemIndex == pageNo) return@LaunchedEffect
                                    jumping = true
                                    listState.animateScrollToItem(pageNo)
                                    jumping = false
                                }
                                LaunchedEffect(listState) {
                                    snapshotFlow { listState.firstVisibleItemIndex }.collect { if (!jumping) session.showPage(it) }
                                }
                                LazyColumn(Modifier.fillMaxSize(), listState) {
                                    items(pageCount) { index ->
                                        Box(Modifier.padding(bottom = with(density) { (evenPx * 2).toDp() })) { pageColumn(index) }
                                    }
                                }
                            }
                        } else Row {
                            repeat(columns) { c ->
                                if (pageNo + c < pageCount) pageColumn(pageNo + c)
                                else Spacer(Modifier.width(with(density) { (columnWidthPx + margin.roundToPx() * 2).toDp() }))
                            }
                        }
                    }

                }

                ReaderFooter(
                    fraction = session.readingFraction(),
                    chapterStarts = session.chapterStartFractions(),
                    chapterPagesLeft = session.chapterPagesLeft(),
                    page = session.locationNumber(),
                    totalPages = session.locationTotal().coerceAtLeast(1),
                    ink = colors.ink,
                    horizontalMargin = margin,
                    // as wide as the page
                    modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = pageWidth),
                )

                AnimatedVisibility(
                    visible = barsVisible,
                    modifier = Modifier.align(Alignment.BottomCenter).zIndex(1f),
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                ) {
                Column(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    SeekBar(
                        fraction = session.readingFraction(),
                        onSeek = { fraction ->
                            scope.launch {
                                session.jumpToFraction(fraction.toDouble())
                            }
                        },
                        onSeekEnd = { scope.launch { session.commitPosition() } },
                    )
                    Spacer(Modifier.height(4.dp))
                    val total = session.locationTotal().coerceAtLeast(1)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            // numbered as the footer numbers them
                            "Page ${session.locationNumber()} of $total",
                            style = Type.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val percent = (session.locationNumber() - 1) * 100 / (total - 1).coerceAtLeast(1)
                        Text(
                            "${percent}%",
                            style = Type.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                }
            }

            if (isDesktop && panelOpen) {
                // Dismiss layer: click anywhere outside the panel closes it
                Box(
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTapGestures { panelOpen = false }
                    },
                )
                // Right panel (TECH 10.4): 340 wide, floating rounded card
                // (same treatment as the display popover); as tall as its
                // content, scrolling once it reaches the window bottom
                Column(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 12.dp, top = 64.dp, bottom = 12.dp)
                        .width(340.dp)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(vertical = 12.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Contents", style = Type.cardTitle, fontSize = 18.sp)
                        IconButton(onClick = { panelOpen = false }) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Box(Modifier.padding(horizontal = 20.dp)) {
                        ContentsTabs(sheetTab, annotations) { sheetTab = it }
                    }
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                    ) {
                        // the panel stays open after a jump, like before
                        ContentsTabBody(sheetTab) {}
                    }
                }
            }
            if (isDesktop && sheet == SheetKind.DISPLAY) {
                // Dismiss layer for the popover too
                Box(
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTapGestures { sheet = SheetKind.NONE }
                    },
                )
                // Popover from Aa (design 'Desktop Display settings')
                Column(
                    Modifier.width(320.dp).align(Alignment.TopEnd)
                        .padding(top = 64.dp, end = 16.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(20.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Display", style = Type.cardTitle, fontSize = 18.sp)
                        Text(
                            "This device only",
                            style = Type.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DisplayContent(display) { display = it }
                }
            }
            if (isDesktop && sheet == SheetKind.SEARCH) {
                Box(
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTapGestures { sheet = SheetKind.NONE }
                    },
                )
                Column(
                    Modifier.width(400.dp).align(Alignment.TopEnd)
                        .padding(top = 64.dp, end = 16.dp)
                        .keepsTaps()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(20.dp),
                ) {
                    Text("Search", style = Type.cardTitle, fontSize = 18.sp)
                    SearchContent(book.chapters, searchQuery, { searchQuery = it }, pickSearchHit)
                }
            }
            if (!isDesktop && sheet != SheetKind.NONE) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                        .clickable { sheet = SheetKind.NONE },
                )
                Column(
                    Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                        // the search field's keyboard pushes the sheet up
                        .imePadding()
                        .keepsTaps()
                        .background(MaterialTheme.colorScheme.surface, SheetShape)
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            when (sheet) {
                                SheetKind.CONTENTS -> "Contents"
                                SheetKind.SEARCH -> "Search"
                                else -> "Display"
                            },
                            style = Type.cardTitle,
                            fontSize = 18.sp,
                        )
                        TextButton(onClick = { sheet = SheetKind.NONE }) {
                            Text("Done", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    when (sheet) {
                        SheetKind.CONTENTS -> {
                            ContentsTabs(sheetTab, annotations) { sheetTab = it }
                            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                                ContentsTabBody(sheetTab) { sheet = SheetKind.NONE }
                            }
                        }
                        SheetKind.SEARCH -> SearchContent(book.chapters, searchQuery, { searchQuery = it }, pickSearchHit)
                        else -> DisplayContent(display) { display = it }
                    }
                }
            }

            // "Reader selection" (design page): the selected text, its two handles and the toolbar
            selection?.let { current ->
                val copyText = rememberCopyToClipboard()
                val blocks = book.chapters[chapterIndex].blocks
                SelectionOverlay(
                    selection = current,
                    geometry = lineGeometry,
                    onChange = { selection = it },
                    onDismiss = { selection = null },
                    toolbar = { modifier ->
                        SelectionToolbar(
                            onColor = { colorName ->
                                onAnnotationUpsert(
                                    newHighlightEntry(current.anchor(chapter.href, blocks), current.text(blocks), colorName),
                                )
                                selection = null
                            },
                            onNote = {
                                editing = EditorState(anchor = current.anchor(chapter.href, blocks), quote = current.text(blocks))
                                selection = null
                            },
                            onCopy = {
                                copyText(current.text(blocks))
                                selection = null
                            },
                            modifier = modifier,
                        )
                    },
                )
            }

            fullPicture?.let { src -> PictureViewer(src) { fullPicture = null } }

            // Note editor sheet (design 'Note editor')
            editing?.let { editor ->
                val existing = editor.existing
                NoteEditorSheet(
                    quote = editor.quote,
                    color = editor.color,
                    note = editor.note,
                    onColor = { editing = editor.copy(color = it) },
                    onNoteChange = { editing = editor.copy(note = it) },
                    onCancel = { editing = null },
                    onSave = {
                        val entry = existing ?: editor.anchor?.let { newHighlightEntry(it, editor.quote, editor.color) }
                        if (entry != null) onAnnotationUpsert(entry.copy(color = editor.color, note = editor.note))
                        editing = null
                    },
                    onDelete = if (existing != null) {
                        {
                            onAnnotationDelete(existing.id)
                            editing = null
                        }
                    } else {
                        null
                    },
                )
            }
        }
        }
    }
}

/** A sheet keeps taps on its blank areas; otherwise they reach the dimmed backdrop under it, which closes it. */
private fun Modifier.keepsTaps(): Modifier = pointerInput(Unit) { detectTapGestures { } }

/** Annotation colors (design toolbar: yellow/green/blue/pink). */
object HighlightColors {
    fun of(name: String): Color = when (name) {
        "green" -> Color(0xFF8CC98F)
        "blue" -> Color(0xFFA8C8F0)
        "pink" -> Color(0xFFF2AFC7)
        else -> Color(0xFFF6D860) // yellow, TECH token `highlight`
    }
}

private enum class ContentsTab { CONTENTS, NOTES, BOOKMARKS }

/** Open-the-reader-on-an-anchor parameter (Notes list entry points). */
data class AnchorJump(val href: String, val blockIndex: Int, val charOffset: Int)

/** State for the note editor sheet; [existing] null = creating one for [anchor]. */
private data class EditorState(
    val existing: com.baca.shared.sync.AnnotationEntry? = null,
    val anchor: ReflowAnchorDto? = existing?.reflowAnchor,
    val quote: String = existing?.text.orEmpty(),
    val color: String = existing?.color ?: "yellow",
    val note: String = existing?.note.orEmpty(),
)

/** Text copy for the selection toolbar's Copy action. */
@Composable
expect fun rememberCopyToClipboard(): (String) -> Unit

private fun newHighlightEntry(anchor: ReflowAnchorDto, text: String, colorName: String) =
    com.baca.shared.sync.AnnotationEntry(
        id = kotlin.uuid.Uuid.random().toString(),
        kind = "highlight",
        reflowAnchor = anchor,
        text = text,
        color = colorName,
        createdAt = 0L, // repository stamps wall time via now()
        lamport = 0L,
    )

private data class HighlightRange(val start: Int, val end: Int, val colorName: String, val entry: com.baca.shared.sync.AnnotationEntry)

internal const val KIND_BOOKMARK = "bookmark"

/** Bookmark at a page's first position; start == end so it never paints a highlight. */
private fun newBookmarkEntry(chapterHref: String, at: ReflowLayout.BlockPosition) =
    com.baca.shared.sync.AnnotationEntry(
        id = kotlin.uuid.Uuid.random().toString(),
        kind = KIND_BOOKMARK,
        reflowAnchor = ReflowAnchorDto(
            href = chapterHref,
            start = PointDto(blockIndex = at.blockIndex, charOffset = at.charOffset),
            end = PointDto(blockIndex = at.blockIndex, charOffset = at.charOffset),
            quote = at.quote,
        ),
        text = at.quote,
        createdAt = 0L,
        lamport = 0L,
    )

private fun highlightRangesOf(
    annotations: List<com.baca.shared.sync.AnnotationEntry>,
    chapterHref: String,
    blockIndex: Int,
    lineStart: Int,
    lineEnd: Int,
): List<HighlightRange> {
    val out = mutableListOf<HighlightRange>()
    annotations.forEach { ann ->
        val anchor = ann.reflowAnchor ?: return@forEach
        if (anchor.href != chapterHref) return@forEach
        if (anchor.start.blockIndex > blockIndex || anchor.end.blockIndex < blockIndex) return@forEach
        val start = if (anchor.start.blockIndex == blockIndex) anchor.start.charOffset else 0
        val end = if (anchor.end.blockIndex == blockIndex) anchor.end.charOffset else Int.MAX_VALUE
        val s = maxOf(start, lineStart)
        val e = minOf(end, lineEnd)
        if (e > s) out.add(HighlightRange(s - lineStart, e - lineStart, ann.color, ann))
    }
    return out
}

private fun highlighted(
    base: AnnotatedString,
    ranges: List<HighlightRange>,
    lineStart: Int,
    lineEnd: Int,
    /** the text being selected, in block offsets */
    selected: IntRange? = null,
    selectionTint: Color = Color.Unspecified,
): AnnotatedString {
    val selectedHere = selected?.let { maxOf(it.first, lineStart) until minOf(it.last + 1, lineEnd) }?.takeIf { !it.isEmpty() }
    if (ranges.isEmpty() && selectedHere == null) return base.subSequence(lineStart, lineEnd)
    return buildAnnotatedString {
        append(base.subSequence(lineStart, lineEnd))
        ranges.forEach { r ->
            addStyle(SpanStyle(background = HighlightColors.of(r.colorName)), r.start, r.end)
        }
        selectedHere?.let { addStyle(SpanStyle(background = selectionTint), it.first - lineStart, it.last + 1 - lineStart) }
    }
}

/** Text line with annotation gestures: tap on a highlight opens the editor. */
@Composable
private fun TappableText(
    text: AnnotatedString,
    style: TextStyle,
    inlineContent: Map<String, InlineTextContent>,
    ranges: List<HighlightRange>,
    lineStart: Int,
    onTapAnnotation: (com.baca.shared.sync.AnnotationEntry) -> Unit,
    /** this drawn line's layout and its top-left in the root (selection hit-testing) */
    onPlaced: (TextLayoutResult, Offset) -> Unit,
    /** a long press at this char offset into the line */
    onLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // foundation BasicText: M3's Text merges LocalTextStyle (bodyLarge's
    // lineHeight wins over ours — the compressed-line-height bug)
    androidx.compose.foundation.text.BasicText(
        text = text,
        style = style,
        softWrap = false,
        maxLines = 1,
        inlineContent = inlineContent,
        onTextLayout = { layout = it },
        modifier = modifier
            .onGloballyPositioned { coordinates -> layout?.let { onPlaced(it, coordinates.positionInRoot()) } }
            .pointerInput(text, ranges, lineStart) {
                detectTapGestures(
                    onTap = { pos ->
                        val l = layout ?: return@detectTapGestures
                        val offset = l.getOffsetForPosition(pos)
                        ranges.firstOrNull { offset >= it.start && offset < it.end }?.let { r ->
                            onTapAnnotation(r.entry)
                        }
                    },
                    onLongPress = { pos -> layout?.let { onLongPress(it.getOffsetForPosition(pos)) } },
                )
            },
    )
}

/** Floating pill over the long-pressed paragraph (design 'Reader selection'). */
@Composable
private fun SelectionToolbar(
    onColor: (String) -> Unit,
    onNote: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pill = Color(0xFF15181E)
    Row(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(pill)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listOf("yellow", "green", "blue", "pink").forEach { name ->
            val selected = false
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(HighlightColors.of(name))
                    .then(
                        if (selected) Modifier.border(2.dp, Color.White, CircleShape) else Modifier.border(1.dp, pill, CircleShape)
                    )
                    .clickable { onColor(name) },
            )
        }
        Text("|", color = Color.White.copy(alpha = 0.3f))
        Text(
            "Note",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onNote).padding(horizontal = 4.dp),
        )
        Text(
            "Copy",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onCopy).padding(horizontal = 4.dp),
        )
    }
}

/** Note editor sheet (design 'Note editor'): quote card, color, note, delete. */
@Composable
private fun NoteEditorSheet(
    quote: String,
    color: String,
    note: String,
    onColor: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = onCancel))
        Column(
        Modifier.fillMaxWidth().align(Alignment.BottomCenter)
            // above the keyboard while typing the note
            .imePadding()
            .keepsTaps()
            .background(MaterialTheme.colorScheme.surface, SheetShape)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp, bottom = 12.dp)) {
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outline))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onCancel) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("Note", style = Type.cardTitle, fontSize = 18.sp)
            TextButton(onClick = onSave) { Text("Save", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(HighlightColors.of(color)).padding(top = 2.dp))
                Text(quote, style = TextStyle(fontSize = 15.sp, fontFamily = FontFamily.Serif, lineHeight = 21.sp))
            }
        }
        SheetSection("COLOR") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf("yellow", "green", "blue", "pink").forEach { name ->
                    val selected = name == color
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(HighlightColors.of(name))
                            .clickable { onColor(name) }
                            .then(
                                if (selected) {
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape).padding(4.dp)
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
        }
        SheetSection("NOTE") {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 96.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .padding(12.dp),
            ) {
                // the whole box takes the tap, not just the placeholder's width
                androidx.compose.foundation.text.BasicTextField(
                    value = note,
                    onValueChange = onNoteChange,
                    textStyle = Type.body.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                ) { inner ->
                    Box {
                        if (note.isEmpty()) {
                            Text("Add a note…", style = Type.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // always composed, so the cursor shows in an empty note
                        inner()
                    }
                }
            }
        }
        Text(
            "Notes sync to your other devices.",
            style = Type.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        if (onDelete != null) {
            Text(
                "Delete highlight",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFB3261E),
                modifier = Modifier.align(Alignment.CenterHorizontally).clickable(onClick = onDelete).padding(8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
    }
}

/** Annotation cards for the contents-panel Notes tab and the Notes screen. */
@Composable
internal fun AnnotationCards(
    entries: List<com.baca.shared.sync.AnnotationEntry>,
    book: ParsedEpub?,
    ink: Color,
    deviceLabels: Map<String, String> = emptyMap(),
    onTap: (com.baca.shared.sync.AnnotationEntry) -> Unit = {},
    onEdit: (com.baca.shared.sync.AnnotationEntry) -> Unit = {},
) {
    Column(Modifier.fillMaxWidth()) {
        entries.forEach { entry ->
            val anchor = entry.reflowAnchor
            val chapterIdx = anchor?.let { indexOfChapter(it.href, book?.chapters ?: emptyList()) } ?: -1
            // without the parsed book (Notes screen) we can't check — assume locatable
            val fixedPage = entry.fixedAnchor?.pageIndex
            val locatable = book == null || fixedPage != null ||
                (anchor != null && chapterIdx >= 0 &&
                    anchor.start.blockIndex < (book.chapters[chapterIdx].blocks.size))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .then(if (locatable) Modifier.clickable { onTap(entry) } else Modifier)
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (entry.kind == KIND_BOOKMARK) {
                        androidx.compose.material3.Icon(
                            Icons.Filled.Bookmark,
                            contentDescription = "Bookmark",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Box(Modifier.size(12.dp).padding(top = 2.dp).clip(RoundedCornerShape(3.dp)).background(HighlightColors.of(entry.color)))
                    }
                    Text(
                        // a bookmark on an image-only page has no quote
                        entry.text.ifBlank { anchor?.quote.orEmpty() }
                            .ifBlank { if (entry.kind == KIND_BOOKMARK) "Bookmark" else "" },
                        style = TextStyle(fontSize = 15.sp, fontFamily = FontFamily.Serif, lineHeight = 21.sp),
                        color = ink,
                    )
                }
                if (entry.note.isNotBlank()) {
                    Text(entry.note, style = Type.body, modifier = Modifier.padding(top = 4.dp))
                }
                if (!locatable) {
                    Text(
                        "⚠ Can't locate in text",
                        style = Type.caption,
                        color = Color(0xFFB25000),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    buildString {
                        if (fixedPage != null) append("Page ${fixedPage + 1}")
                        else if (chapterIdx >= 0) append("Chapter ${chapterIdx + 1}")
                        val device = deviceLabels[entry.id]
                        if (device != null) append(if (isNotEmpty()) " · $device" else device)
                    },
                    style = Type.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (entry.note.isNotBlank()) {
                    Text(
                        "Edit note",
                        style = Type.caption,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.End).clickable { onEdit(entry) }.padding(top = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** Contents | Notes (n) | Bookmarks (n) tab toggle at the top of the contents sheet. */
@Composable
private fun ContentsTabs(
    current: ContentsTab,
    annotations: List<com.baca.shared.sync.AnnotationEntry>,
    onSelect: (ContentsTab) -> Unit,
) {
    val live = annotations.filter { !it.deleted }
    val bookmarkCount = live.count { it.kind == KIND_BOOKMARK }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            ContentsTab.CONTENTS to "Contents",
            ContentsTab.NOTES to "Notes (${live.size - bookmarkCount})",
            ContentsTab.BOOKMARKS to "Bookmarks ($bookmarkCount)",
        ).forEach { (tab, label) ->
            val selected = tab == current
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun Modifier.drawBoxes(boxes: List<PageBox>, ink: Color, dark: Boolean): Modifier =
    if (boxes.isEmpty()) this else drawBehind {
        boxes.forEach { drawBox(it, ink, dark) }
    }

/**
 * One box: its background, then each border side, as a browser draws them. A box split
 * over pages is drawn whole and cut at the page edge, so the first page keeps its rounded
 * top corners and the last its rounded bottom ones. On a dark page the background goes
 * and borders without a colour of their own take the ink, as LibreraX's css() sets it.
 */
internal fun DrawScope.drawBox(page: PageBox, ink: Color, dark: Boolean) {
    val box = page.box
    val css = density
    val (top, right, bottom, left) = box.border.map { it * css }
    val color = { side: Int -> box.borderColor[side]?.let { Color(it) } ?: ink }
    val l = page.leftPx.toFloat()
    val r = page.rightPx.toFloat()
    val t = page.topPx.toFloat()
    val b = page.bottomPx.toFloat()
    if (r <= l || b <= t) return
    val radius = box.radiusPx * css
    // past the cut, far enough that a corner there falls outside the page
    val beyond = radius + top + bottom
    val wholeT = if (page.startsHere) t else t - beyond
    val wholeB = if (page.endsHere) b else b + beyond
    val sameSides = setOf(top, right, bottom, left).size == 1 && (0..3).map(color).toSet().size == 1
    clipRect(l, t, r, b) {
        box.background?.takeIf { !dark }?.let { bg ->
            drawRoundRect(Color(bg), Offset(l, wholeT), Size(r - l, wholeB - wholeT), CornerRadius(radius))
        }
        if (radius > 0f && sameSides && top > 0f) {
            val half = top / 2
            drawRoundRect(color(0), Offset(l + half, wholeT + half), Size(r - l - top, wholeB - wholeT - top), CornerRadius(radius - half), style = Stroke(top))
            return@clipRect
        }
        if (top > 0f && page.startsHere) drawRect(color(0), Offset(l, t), Size(r - l, top))
        if (bottom > 0f && page.endsHere) drawRect(color(2), Offset(l, b - bottom), Size(r - l, bottom))
        if (left > 0f) drawRect(color(3), Offset(l, t), Size(left, b - t))
        if (right > 0f) drawRect(color(1), Offset(r - right, t), Size(right, b - t))
    }
}

/**
 * How LibreraX lays a picture on the page: dimmed to 80% on a dark page; on a tinted
 * light one, a drawing on white multiplied into it so the white takes the page's
 * colour; on a white page, as it is.
 */
internal fun pictureFilter(page: Color, onPlainGround: () -> Boolean): ColorFilter? = when {
    isDarkScheme(page) -> DIMMED
    page != Color.White && onPlainGround() -> ColorFilter.tint(page, BlendMode.Multiply)
    else -> null
}

private val DIMMED = ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(0.8f, 0.8f, 0.8f, 1f) })

/** An `<hr>`: a browser's grey hairline, or the book's ornament at its own size, centred in the band. */
@Composable
private fun RuleLine(rule: Block.Rule, heightPx: Int, color: Color) {
    val density = LocalDensity.current
    val ornament = rule.ornament?.let { LocalBookImages.current?.image(it) }
    Box(
        Modifier.fillMaxWidth().height(with(density) { heightPx.toDp() }).clipToBounds()
            .drawBehind {
                // the book's width, centred as a browser centres a rule
                val inset = size.width * (1f - rule.widthShare.coerceIn(0f, 1f)) / 2
                if (rule.line && ornament == null) {
                    // a browser's inset border: the top one darker, the bottom one in the rule's colour
                    val border = ruleBorderPx(density.density).toFloat()
                    val top = size.height / 2 - border
                    drawRect(insetDark(color), Offset(inset, top), Size(size.width - 2 * inset, border))
                    drawRect(color, Offset(inset, top + border), Size(size.width - 2 * inset, border))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (ornament != null) {
            androidx.compose.foundation.Image(
                painter = BitmapPainter(ornament),
                contentDescription = null,
                modifier = Modifier.size(with(density) { (ornament.width * density.density).toDp() }, with(density) { (ornament.height * density.density).toDp() }),
            )
        }
    }
}

/** What a margin's shares are of, for a page [pageHeightPx] tall in this viewport. */
private fun Viewport.roomFor(pageHeightPx: Int) =
    com.baca.shared.parser.Room(pageHeightPx.toFloat(), windowHeightPx.toFloat(), columnWidthPx.toFloat(), windowWidthPx.toFloat())

internal const val NO_BREAK_SPACE = '\u00A0'

// a browser's `hr { color: gray }`; on a dark page LibreraX gives it the theme's text
private val RULE_GRAY = Color(0xFF808080)

/** Chromium's `Color::Dark()`, which an inset border's top is drawn in. */
private fun insetDark(color: Color): Color {
    val v = maxOf(color.red, color.green, color.blue)
    val m = if (v == 0f) 0f else maxOf(0f, (v - 0.33f) / v)
    return Color(color.red * m, color.green * m, color.blue * m, color.alpha)
}

@Composable
private fun ImageLine(chapter: Chapter, blockIndex: Int, reservedHeightPx: Int?, vp: Viewport, fontPx: Float, page: Color, onOpen: (String) -> Unit) {
    val images = LocalBookImages.current
    val block = chapter.blocks[blockIndex] as Block.Image
    val bitmap = images?.image(block.src)
    if (bitmap != null) {
        val density = LocalDensity.current
        // the size ReflowLayout reserved, and the width that goes with it, in its containing block
        val (startPx, widthPx) = block.placeIn(fontPx, density.density, vp.columnWidthPx, vp.roomFor(vp.pageHeightPx))
        val size = block.box.sizePx(bitmap.width, bitmap.height, density.density, fontPx, widthPx, vp.pageHeightPx, vp.marginPx, vp.windowWidthPx, vp.windowHeightPx)
        val height = reservedHeightPx ?: size.heightPx
        Box(
            Modifier.offset { androidx.compose.ui.unit.IntOffset(startPx, 0) }
                .width(with(density) { widthPx.toDp() })
                .height(with(density) { height.toDp() }),
            contentAlignment = when (block.align) {
                BookAlign.CENTER -> Alignment.TopCenter
                BookAlign.END -> Alignment.TopEnd
                BookAlign.START -> Alignment.TopStart
            },
        ) {
            androidx.compose.foundation.Image(
                painter = BitmapPainter(bitmap),
                contentDescription = block.alt.ifBlank { "image" },
                contentScale = ContentScale.Fit,
                colorFilter = pictureFilter(page) { images?.onPlainGround(block.src) == true },
                modifier = Modifier.size(with(density) { size.widthPx.toDp() }, with(density) { height.toDp() })
                    // a long press opens it full screen, as LibreraX does
                    .pointerInput(block.src) { detectTapGestures(onLongPress = { onOpen(block.src) }) },
            )
        }
    } else {
        Box(Modifier.fillMaxWidth().height(96.dp)) {}
    }
}

/**
 * Contents rows shared by the phone sheet and the desktop panel: toc title
 * left, chapter percent right; current chapter accent-bold with a dot and
 * "You are here". [onJump] receives the entry href (matching uses the shared
 * tolerant resolver).
 */
@Composable
private fun ContentsList(
    book: ParsedEpub,
    session: ReaderSession,
    tick: Int,
    accent: Color,
    ink: Color,
    onJump: (String) -> Unit,
) {
    book.toc.forEachIndexed { index, entry ->
        val chapterAt = chapterIndexOf(book.chapters, entry.href)
        // tick is read here on purpose: it bumps on every position change, so
        // the accent dot moves as soon as the chapter/page changes
        val isCurrent = tick >= 0 && chapterAt == session.chapterIndex && chapterAt >= 0
        Row(
            Modifier.fillMaxWidth().clickable(onClick = { onJump(entry.anchor?.let { "${entry.href}#$it" } ?: entry.href) }).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isCurrent) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                }
                Text(
                    entry.title,
                    style = Type.body,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) accent else ink,
                )
            }
        }
        // dividers separate rows; none under the last one
        if (index != book.toc.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}


/**
 * Tolerant toc-href → chapter-index resolution: exact zip path first, then
 * fragment/query-stripped, then case-insensitive. Toc hrefs and chapter hrefs
 * are normally identical, but some EPUBs differ in fragments or casing.
 */
// reuse the shared tolerant matcher; the local alias keeps the call sites short
private fun chapterIndexOf(chapters: List<Chapter>, tocHref: String): Int =
    indexOfChapter(tocHref, chapters)

/** Sheet building blocks per design file 'Display settings' page. */
private val SheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)



/** Single chip; filled state uses accent fill with white text (design file). */

private enum class SheetKind { NONE, DISPLAY, CONTENTS, SEARCH }

// --- helpers ---

@Composable
private fun lineStyle(block: Block.Text, lineRatio: Float, display: DisplaySettings, ink: Color, fonts: com.baca.app.ui.UiFonts): TextStyle {
    val density = LocalDensity.current
    // exactly the size (to the fraction of a px, as a browser) and line height ReflowLayout
    // measured with, so a drawn line is never wider or taller than the one that was paginated
    val fontPx = with(density) { display.fontSizeSp.sp.toPx() * block.fontScale() }
    // to the fraction of a px, as pages stack it; the row around the line holds it at whole pixels
    val lineHeightPx = fontPx * lineRatio
    return with(density) {
        TextStyle(
            fontSize = fontPx.toSp(),
            lineHeight = lineHeightPx.toSp(),
            fontFamily = readerFontFamily(display.font, fonts),
            color = ink,
            lineHeightStyle = PageLineHeightStyle,
            // letters at their exact advance, as a browser places them; hinted ones set lines narrower
            textMotion = TextMotion.Animated,
            textDirection = if (block.rtl) TextDirection.Rtl else TextDirection.Unspecified,
        )
    }
}

// Each page line is its own Text; Compose's default trims the line spacing off
// single-line text, so lines would draw shorter than ReflowLayout reserved and
// leave the page bottom blank. Keep the full line height, glyphs centered.
private val PageLineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/** One mapping for measuring and drawing, so page breaks match the drawn text. */
internal fun readerFontFamily(key: String, fonts: com.baca.app.ui.UiFonts): FontFamily = when (key) {
    "sans" -> fonts.sans
    "mono", SYSTEM_MONO -> FontFamily.Monospace
    "libron" -> fonts.libron
    SYSTEM_SANS -> FontFamily.SansSerif
    SYSTEM_SERIF -> FontFamily.Serif
    else -> fonts.serif
}

/**
 * A floated first letter, [widthPx] wide, at the left of the lines beside it; its
 * own line box ([lineRatio] of the block's font) starts at the first line's top.
 */
@Composable
private fun DropCapText(text: AnnotatedString, style: TextStyle, lineRatio: Float, widthPx: Int) {
    androidx.compose.foundation.text.BasicText(
        text,
        style = style.copy(lineHeight = style.fontSize * lineRatio),
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
            layout(0, 0) { placeable.place(-widthPx, 0) }
        },
    )
}

/**
 * A list item's marker, outside its text as a browser draws it: right-aligned
 * against where the item's text starts, [shiftPx] left of the first line.
 */
@Composable
private fun ListMarker(marker: String, style: TextStyle, shiftPx: Int) {
    androidx.compose.foundation.text.BasicText(
        "$marker ",
        style = style,
        maxLines = 1,
        modifier = Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
            layout(0, placeable.height) { placeable.place(-placeable.width - shiftPx, 0) }
        },
    )
}

/** A paragraph's last line and lines before a <br> stay ragged. */
// a line that ends between letters shows a hyphen, except in scripts written without spaces (CJK, kana, Hangul),
// which break anywhere
internal fun breaksWord(text: String, lineEnd: Int): Boolean =
    lineEnd in 1 until text.length && text[lineEnd - 1].isLetter() && text[lineEnd].isLetter() &&
        !isUpright(text[lineEnd - 1]) && !isUpright(text[lineEnd])

private fun endsMidParagraph(text: String, lineEnd: Int): Boolean =
    lineEnd < text.length && text[lineEnd - 1] != '\n' && text[lineEnd] != '\n'

/**
 * Full justification for one drawn line: the gap to the column edge is spread
 * over its spaces as letter spacing. Each line is its own Text, where
 * TextAlign.Justify does nothing (a single line is always a "last line").
 */
internal fun justified(
    text: AnnotatedString,
    style: TextStyle,
    widthPx: Int,
    measurer: TextMeasurer,
    density: Density,
    /** the line's inline images, as it is drawn */
    placeholders: List<AnnotatedString.Range<Placeholder>>,
): AnnotatedString {
    // a no-break space stretches as a space does (Chromium's TreatAsSpace)
    val gaps = text.indices.filter { text[it] == ' ' || text[it] == NO_BREAK_SPACE }
    if (gaps.isEmpty()) return text
    val natural = measurer.measure(text, style, softWrap = false, maxLines = 1, placeholders = placeholders).size.width
    // a hair of slack: rounding must never push the line into a second row
    val extra = widthPx - natural - JUSTIFY_SLACK_PX
    // however far, as a browser justifies: only a paragraph's last line keeps its spaces
    if (extra <= 0) return text
    val spacing = with(density) { (extra.toFloat() / gaps.size).toSp() }
    return buildAnnotatedString {
        append(text)
        gaps.forEach { addStyle(SpanStyle(letterSpacing = spacing), it, it + 1) }
    }
}

/** Next pointer event in [pass] that lifts the finger, consumed or not; null if the gesture ends otherwise. */
private suspend fun AwaitPointerEventScope.awaitUp(pass: PointerEventPass): PointerInputChange? {
    while (true) {
        val change = awaitPointerEvent(pass).changes.firstOrNull() ?: return null
        if (!change.pressed) return change
    }
}

private const val TURN_MS = 160
// how far (fraction of page width) a turning page slides while it fades
private const val TURN_TRAVEL = 0.3f
private const val SWIPE_FRACTION = 0.2f
private const val FLING_VELOCITY = 800f // px/s

private const val JUSTIFY_SLACK_PX = 2

private fun reflowSettingsOf(display: DisplaySettings, density: Density): ReflowSettings = with(density) {
    val fontPx = display.fontSizeSp.sp.toPx()
    ReflowSettings(
        fontSizePx = fontPx,
        lineHeightRatio = display.lineRatio(),
        imageHeightPx = (display.fontSizeSp * 6f).sp.roundToPx(),
        cssPx = density.density,
        fontFamily = display.font,
    )
}

/** Side margin as a share of the page width; medium is LibreraX's default 6%. */
// LibreraX's top and bottom padding: 4 CSS px for each percent of side padding
private val PADDING_BAND = 4.dp

// the text column on a wide window
private val DESKTOP_COLUMN = 680.dp

/**
 * The page's width in a window [windowWidth] wide: all of it, as LibreraX's WebView is, a
 * whole number of CSS px (412 for a 411.4dp screen); held to the desktop column when [capped].
 */
internal fun pageWidthOf(windowWidth: Dp, capped: Boolean): Dp =
    ceil((if (capped) minOf(windowWidth, DESKTOP_COLUMN) else windowWidth).value).dp

private const val TAG_LINK = "baca.href"

// a browser's link colour (WebKit's -webkit-link), and LibreraX's on a dark page
private val LINK_BLUE = Color(0xFF0000EE)
private val DARK_LINK = Color(0xFF8AB4F8)

/**
 * A block's text styled as LibreraX shows it: the book's own colours on a light page,
 * links in a browser's blue and underlined unless the book says otherwise; on a dark
 * page everything in the theme's ink and links in a light blue (LibreraX's `css()`).
 */
private fun annotatedOf(
    block: Block,
    ink: Color,
    dark: Boolean,
    spacers: List<Int?>,
    /** the block's line height over its font size, as the layout measured it */
    lineRatio: Float,
    /** the running text's size in px, which a span's px shift is taken against */
    bodyFontPx: Float,
    /** a span's font, when it isn't the reader's */
    faces: (FormattedSpan) -> FontFamily?,
): AnnotatedString = when (block) {
    is Block.Image, is Block.Rule -> AnnotatedString("")
    is Block.Text -> buildAnnotatedString {
        block.spans.forEachIndexed { index, span ->
            val link = span.href != null
            if (link) pushStringAnnotation(TAG_LINK, span.href!!)
            val bookColor = span.color
            pushStyle(
                SpanStyle(
                    // an inline background (`<mark>`), which LibreraX clears on a dark page
                    background = span.background?.takeIf { !dark }?.let { Color(it) } ?: Color.Unspecified,
                    color = when {
                        bookColor == INVISIBLE -> Color.Transparent
                        dark -> if (link || span.inAnchor) DARK_LINK else ink
                        bookColor == TEXT_COLOR -> ink
                        bookColor != null -> Color(bookColor)
                        link -> LINK_BLUE
                        else -> ink
                    },
                    textDecoration = TextDecoration.combine(
                        listOfNotNull(
                            TextDecoration.Underline.takeIf { if (link) !span.plainLink else span.underline },
                            TextDecoration.LineThrough.takeIf { span.strike },
                        ),
                    ),
                    fontWeight = if (span.bold) FontWeight.Bold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                    fontFeatureSettings = if (span.smallCaps) SMALL_CAPS else null,
                    // as ComposeTextMetrics measures them
                    fontSize = if (span.scale != 1f) span.scale.em else TextUnit.Unspecified,
                    fontFamily = faces(span),
                    // as ReflowLayout measures it: a percentage is of the line height
                    baselineShift = (
                        span.baseline + span.baselineLine * lineRatio +
                            span.baselinePx / (bodyFontPx * block.fontScale() * span.scale)
                        ).takeIf { it != 0f }?.let { BaselineShift(it) },
                    letterSpacing = if (span.letterSpacing != 0f) span.letterSpacing.em else TextUnit.Unspecified,
                ),
            )
            val spacer = spacers.getOrNull(index)
            val at = length
            when {
                span.image != null -> appendInlineContent(span.image!!.id, span.text)
                spacer != null -> appendInlineContent(spacerId(spacer), span.text)
                else -> append(span.text)
            }
            wordSpaces(span.text, 0, span.text.length, span.wordSpacing).forEach { (i, style) -> addStyle(style, at + i, at + i + 1) }
            pop()
            if (link) pop()
        }
    }
}

/** Text with tappable internal/external links; part of text selection downstream. */
@Composable
private fun LinkText(
    text: AnnotatedString,
    style: TextStyle,
    inlineContent: Map<String, InlineTextContent>,
    onPlaced: (TextLayoutResult, Offset) -> Unit,
    onLink: (String) -> Unit,
    /** a long press at this char offset into the line */
    onLongPress: (Int) -> Unit,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    androidx.compose.foundation.text.BasicText(
        text = text,
        style = style,
        softWrap = false,
        maxLines = 1,
        inlineContent = inlineContent,
        onTextLayout = { layout = it },
        modifier = Modifier.onGloballyPositioned { coordinates -> layout?.let { onPlaced(it, coordinates.positionInRoot()) } }.pointerInput(text) {
            detectTapGestures(
                onTap = { pos ->
                    layout?.let { l ->
                        val offset = l.getOffsetForPosition(pos)
                        text.getStringAnnotations(TAG_LINK, offset, offset).firstOrNull()?.let { ann ->
                            onLink(ann.item)
                        }
                    }
                },
                onLongPress = { pos -> layout?.let { onLongPress(it.getOffsetForPosition(pos)) } },
            )
        },
    )
}

private val CURSOR_WIDTH = 3.dp
private val CURSOR_HEIGHT = 20.dp

/**
 * Invisible 24dp touch strip; the visible track is 6dp, 12dp when hovered (desktop)
 * or grabbed, with a cursor line at the reading position. Tap or drag anywhere in
 * the strip to jump.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun SeekBar(fraction: Float, onSeek: (Float) -> Unit, onSeekEnd: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val barHeight = if (expanded || dragging) 12.dp else 6.dp
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outline

    Box(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .seekHover({ expanded = true }, { expanded = false })
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    onSeek(((pos.x / size.width).coerceIn(0f, 1f)))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { onSeekEnd(); dragging = false },
                    onDragCancel = { onSeekEnd(); dragging = false },
                ) { change, _ ->
                    dragging = true
                    onSeek(((change.position.x / size.width).coerceIn(0f, 1f)))
                    change.consume()
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val barPx = barHeight.toPx()
            val top = (size.height - barPx) / 2
            val radius = CornerRadius(barPx / 2)
            val reached = size.width * fraction.coerceIn(0f, 1f)
            drawRoundRect(track, Offset(0f, top), Size(size.width, barPx), radius)
            drawRoundRect(accent, Offset(0f, top), Size(reached, barPx), radius)
            // the reading position: a line taller than the bar, kept inside the strip's ends
            val cursorWidth = CURSOR_WIDTH.toPx()
            val cursorHeight = CURSOR_HEIGHT.toPx()
            drawRoundRect(
                accent,
                Offset((reached - cursorWidth / 2).coerceIn(0f, size.width - cursorWidth), (size.height - cursorHeight) / 2),
                Size(cursorWidth, cursorHeight),
                CornerRadius(cursorWidth / 2),
            )
        }
    }
}
