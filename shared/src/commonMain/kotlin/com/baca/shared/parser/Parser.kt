package com.baca.shared.parser

import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.core.KtXmlReader
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.openZip
import java.io.StringReader
import kotlin.math.roundToInt

data class FormattedSpan(
    val text: String,
    val bold: Boolean,
    val italic: Boolean,
    /** Internal links carry the target document's zip path; external carry the URL. */
    val href: String? = null,
    /** book CSS `font-variant: small-caps` */
    val smallCaps: Boolean = false,
    /** a link the book draws without its underline (`text-decoration: none`); still followed on tap */
    val plainLink: Boolean = false,
    /** book CSS font size relative to its block's */
    val scale: Float = 1f,
    /** a face other than the book's running text's (TECH 3.1); null is the reader's font */
    val face: BookFace? = null,
    /** book CSS `text-decoration: underline` on text that isn't a link */
    val underline: Boolean = false,
    /** `vertical-align`: raised (positive) or lowered, in em of its own font */
    val baseline: Float = 0f,
    /** `vertical-align` in a percentage: raised by this many of its line heights */
    val baselineLine: Float = 0f,
    /** `letter-spacing` in em of its own font */
    val letterSpacing: Float = 0f,
    /** the book's `color` (0xAARRGGBB), [TEXT_COLOR] for the text's own; null: ink, or a link's blue */
    val color: Long? = null,
    /** `word-spacing` in em of its own font */
    val wordSpacing: Float = 0f,
    /** an image drawn in the line, in place of this span's one [INLINE_IMAGE] char */
    val image: InlineImage? = null,
    /** space in the line instead of this span's one [INLINE_IMAGE] char: an inline element's padding or margin */
    val spacer: InlineSpacer? = null,
    /** the `display: inline-block` element this text is in, for its `min-width` ([InlineSpacer.padsBox]) */
    val inlineBox: Int? = null,
    /** the family the book names for it (lowercased); after [withRunningText], a font the book ships, else null */
    val family: String? = null,
    /** struck through: `<s>`, `<del>`, `text-decoration: line-through` */
    val strike: Boolean = false,
    /** device px raised by, besides [baseline]: Chromium moves `super` and `sub` a px more */
    val baselinePx: Float = 0f,
    /** inside an `<a>`, a link or not: LibreraX colours all of them on a dark page */
    val inAnchor: Boolean = false,
    /** an inline element's `background-color` behind the text (`<mark>`), 0xAARRGGBB */
    val background: Long? = null,
)

/**
 * Horizontal space inside a line, [widthEm] of the span's own font: an inline
 * element's padding or margin. When [padsBox] is set it pads that inline-block's
 * text out to [widthEm] instead (its `min-width`), so the space is what's left
 * after the text, worked out when the line is measured.
 */
data class InlineSpacer(val widthEm: Float, val padsBox: Int? = null)

/** The generic faces a book's CSS names; drawn with the reader's own sans and monospace fonts. */
enum class BookFace { SERIF, SANS, MONO }

/** A CSS length as a book writes it; [Px] is CSS pixels, [Percent] of the column unless said otherwise. */
/** The book's `column-count` on a block: its text laid in [count] columns, [gap] apart. */
data class Columns(val count: Int, val gap: Spacing)

sealed class CssLength {
    data class Px(val px: Float) : CssLength()
    data class Em(val em: Float) : CssLength()
    data class Percent(val percent: Float) : CssLength()
    /** of the page's height, as foliate's iframe is a page tall */
    data class Vh(val percent: Float) : CssLength()
    /** of the iframe's width, which reads as 0 (probed) */
    data class Vw(val percent: Float) : CssLength()
    /** a linked stylesheet's `vh`/`vw`: foliate makes them px of the whole WebView, the reader's window */
    data class WindowVh(val percent: Float) : CssLength()
    data class WindowVw(val percent: Float) : CssLength()
    /** `auto`: a margin that takes up what's left, which centres a narrower block */
    data object Auto : CssLength()
}

/**
 * How the book sizes a picture (book CSS and `width`/`height` attributes);
 * null sides are `auto`. Percent widths are already of the column.
 */
data class ImageBox(val width: CssLength? = null, val height: CssLength? = null, val maxWidth: CssLength? = null)

/** A text-sized image inside a paragraph (Kinki Region's ○○ redactions), [heightEm] tall, as wide as its aspect makes it. */
data class InlineImage(val src: String, val heightEm: Float)

/** The char an inline image stands on in its block's text, so offsets count it once. */
const val INLINE_IMAGE = '\uFFFC'

/**
 * An element's box, drawn behind the blocks inside it: its borders, background and
 * rounded corners (book CSS `border`, `background-color`, `border-radius`).
 * Horizontally its outer edges sit [left]/[leftShare] in from the column's left and
 * [right]/[rightShare] in from its right; vertically it runs from [topInset] above
 * its first line to [bottomInset] below its last (its own border and padding, and
 * what's inside them before the text).
 */
data class BlockBox(
    val id: Int,
    val left: Spacing = Spacing.ZERO,
    val leftShare: Float = 0f,
    val right: Spacing = Spacing.ZERO,
    val rightShare: Float = 0f,
    /** border widths in CSS px, top, right, bottom, left */
    val border: List<Float> = listOf(0f, 0f, 0f, 0f),
    /** each side's colour (0xAARRGGBB); null is the text's */
    val borderColor: List<Long?> = listOf(null, null, null, null),
    val background: Long? = null,
    val radiusPx: Float = 0f,
    val topInset: Spacing = Spacing.ZERO,
    val bottomInset: Spacing = Spacing.ZERO,
    /** a table cell's box: drawn at its column's edges, as tall as its row */
    val cell: Boolean = false,
)

/**
 * A length as a book sets it: [em] of the running text, which grows with the
 * reader's font, plus [px] in CSS px, which a browser keeps at their size
 * whatever the font, plus shares of the page's height ([pageH], an in-document
 * `vh`), the window's ([windowH], a linked stylesheet's `vh`) and the column's
 * width ([width], a percent margin), which only the layout knows ([Room]).
 */
class Spacing(
    val em: Float = 0f,
    val px: Float = 0f,
    /**
     * The elements' own spacing it adds up, each as the book set it: LibreraX halves each
     * wide one on its own, not their sum ([withRunningText]). Null: one element's.
     */
    val parts: List<Spacing>? = null,
    val pageH: Float = 0f,
    val windowH: Float = 0f,
    val width: Float = 0f,
    /** a linked stylesheet's `vw`, of the window's width */
    val windowW: Float = 0f,
) {
    operator fun plus(other: Spacing) =
        Spacing(em + other.em, px + other.px, joined(other) { a, b -> a + b }, pageH + other.pageH, windowH + other.windowH, width + other.width, windowW + other.windowW)
    operator fun minus(other: Spacing) =
        Spacing(em - other.em, px - other.px, joined(other) { a, b -> a.without(b) }, pageH - other.pageH, windowH - other.windowH, width - other.width, windowW - other.windowW)

    /** Its share of the page, window and column, without its em and px. */
    fun relative() = Spacing(pageH = pageH, windowH = windowH, width = width, windowW = windowW)

    /** Its parts, itself as one where it has none listed (a zero adds none). */
    fun partsOrSelf(): List<Spacing> = parts ?: if (em == 0f && px == 0f) emptyList() else listOf(Spacing(em, px))

    private fun joined(other: Spacing, op: (List<Spacing>, List<Spacing>) -> List<Spacing>): List<Spacing>? =
        if (parts == null && other.parts == null) null else op(partsOrSelf(), other.partsOrSelf())

    private fun List<Spacing>.without(taken: List<Spacing>): List<Spacing> {
        val left = toMutableList()
        taken.forEach { part -> left.indexOfFirst { it == part }.takeIf { it >= 0 }?.let(left::removeAt) }
        return left
    }

    override fun equals(other: Any?) =
        other is Spacing && em == other.em && px == other.px && pageH == other.pageH && windowH == other.windowH && width == other.width && windowW == other.windowW
    override fun hashCode() = listOf(em, px, pageH, windowH, width, windowW).hashCode()
    override fun toString() = "Spacing(em=$em, px=$px" + (if (pageH != 0f || windowH != 0f || width != 0f || windowW != 0f) ", pageH=$pageH, windowH=$windowH, width=$width, windowW=$windowW" else "") + ")"

    /** In device px, [cssPx] device px to a CSS px. */
    fun toPx(fontPx: Float, cssPx: Float, room: Room = Room.NONE): Int = exactPx(fontPx, cssPx, room).roundToInt()

    /** In device px to the fraction, as a browser stacks blocks. */
    fun exactPx(fontPx: Float, cssPx: Float, room: Room = Room.NONE): Float =
        em * fontPx + px * cssPx + pageH * room.pageHeightPx + windowH * room.windowHeightPx + width * room.widthPx + windowW * room.windowWidthPx

    companion object {
        val ZERO = Spacing()
    }
}

/** What a [Spacing]'s shares are of, in device px: the page's height, the window's height and width, the column's width. */
data class Room(val pageHeightPx: Float, val windowHeightPx: Float, val widthPx: Float, val windowWidthPx: Float = 0f) {
    companion object {
        val NONE = Room(0f, 0f, 0f)
    }
}

/**
 * A drop cap: the block's first [end] chars, floated left (`float: left`) and set
 * [lines] lines tall, the lines beside it shortened by its width, as a browser lays
 * out a floated first letter.
 */
data class DropCap(val end: Int, val lineRatio: Float)

/**
 * Where a block sits in a table: row [row] (unique in the chapter) and column
 * [column], spanning [span] columns, of table [table]. [widths] are the table's
 * column widths as shares of it, null where the book sets none.
 */
data class TableCell(
    val table: Int,
    val row: Int,
    val column: Int,
    val span: Int,
    val widths: List<Float?>,
    /** the table's `border-spacing` across: between its columns and at its sides */
    val spacing: Spacing = Spacing.ZERO,
    /** where the cell sits in a taller row: the share of the spare height above it (0 top, 0.5 middle, 1 bottom) */
    val spareAbove: Float = 0f,
    /** the cell's own border and padding, start and end side: inside its column's width */
    val ownStart: Spacing = Spacing.ZERO,
    val ownEnd: Spacing = Spacing.ZERO,
)

/** Horizontal alignment a book's CSS gives a block; START leaves it to the reader's justification. */
enum class TextAlign { START, CENTER, END }

enum class BlockKind { PARAGRAPH, HEADING, QUOTE }

sealed class Block {
    abstract val text: String

    data class Text(
        val spans: List<FormattedSpan>,
        val kind: BlockKind,
        val headingLevel: Int,
        /** from book CSS (TECH 3.1); the defaults are plain body text */
        val align: TextAlign = TextAlign.START,
        /** `text-indent`; negative hangs the first line out */
        val indent: Spacing = Spacing.ZERO,
        /** book CSS font size relative to body text; null when the book sets none */
        val scale: Float? = null,
        override val gapBefore: Spacing? = null,
        /** left margins, padding and border: the text sits this far in from the column's left edge */
        val inset: Spacing = Spacing.ZERO,
        /** the boxes it sits in, outermost first */
        override val boxes: List<BlockBox> = emptyList(),
        /** book CSS `float: right` at a paragraph's end: drawn at the right, beside the line before if it fits */
        val floatsRight: Boolean = false,
        /** right margins and padding */
        val insetEnd: Spacing = Spacing.ZERO,
        /** a list item's marker ("•", "1."), drawn left of its first line */
        val marker: String? = null,
        /** `<pre>`: spaces and line breaks as written, lines never stretched */
        val keepsSpaces: Boolean = false,
        /** percent margins and a narrower `width`, as shares of the column, left and right */
        val insetShare: Float = 0f,
        val insetEndShare: Float = 0f,
        val dropCap: DropCap? = null,
        /** the book's line height over the font size, where the reader's isn't set (headings); null is the reader's */
        val lineRatio: Float? = null,
        /** false where the book turns hyphenation off (`hyphens: none`) */
        val hyphenate: Boolean = true,
        /** inside a table cell, laid out in its column beside the row's other cells */
        val cell: TableCell? = null,
        override val breakBefore: Boolean = false,
        /** ranged to the start and never justified: a contents entry that is a link */
        val ragged: Boolean = false,
        val columns: Columns? = null,
        override val marginBefore: Spacing? = null,
        override val keepWithPrevious: Boolean = false,
        override val keepLines: Boolean = false,
        /** its element's `lang` or `xml:lang`, the nearest one; null is the book's */
        val lang: String? = null,
        /** a percent `text-indent`: the share of the block's width the first line is set in by, on top of [indent] */
        val indentShare: Float = 0f,
        /** right-to-left text: lines begin at the right edge, and the first line's indent is there */
        val rtl: Boolean = false,
        /** the book's `line-height` in CSS px, over [lineRatio]: it doesn't grow with the font */
        val linePx: Float? = null,
    ) : Block() {
        override val text: String get() = spans.joinToString("") { it.text }
    }

    /**
     * `<hr>`: a hairline across the column ([line]) and/or the book's ornament
     * ([ornament], its `background-image`) centred in a band [height] tall
     * (null: just the line).
     */
    data class Rule(
        override val gapBefore: Spacing? = null,
        val ornament: String? = null,
        val height: Spacing? = null,
        val line: Boolean = true,
        /** the rule's length as a share of the column, centred; a browser's default is all of it */
        val widthShare: Float = 1f,
        override val breakBefore: Boolean = false,
        override val boxes: List<BlockBox> = emptyList(),
        override val marginBefore: Spacing? = null,
        override val keepWithPrevious: Boolean = false,
        override val keepLines: Boolean = false,
    ) : Block() {
        override val text: String get() = ""
    }

    data class Image(
        val alt: String,
        val src: String,
        override val gapBefore: Spacing? = null,
        val box: ImageBox = ImageBox(),
        /** the containing block's alignment: a picture sits in its line as text does */
        val align: TextAlign = TextAlign.START,
        override val breakBefore: Boolean = false,
        override val boxes: List<BlockBox> = emptyList(),
        override val marginBefore: Spacing? = null,
        /** its containing block's content box: in from the column's left and right, as [Text.inset] */
        val inset: Spacing = Spacing.ZERO,
        val insetEnd: Spacing = Spacing.ZERO,
        val insetShare: Float = 0f,
        val insetEndShare: Float = 0f,
        override val keepWithPrevious: Boolean = false,
        override val keepLines: Boolean = false,
        /**
         * The line a picture set inline sits in, its bottom on the baseline: its containing
         * block's font (an empty span) and line height ratio (null: the reader's). Null for a
         * picture set as a block (`display: block`), which is just its own height.
         */
        val strut: FormattedSpan? = null,
        val strutRatio: Float? = null,
    ) : Block() {
        override val text: String get() = ""
    }

    /** Space above the block from the book's CSS margins and padding, in em; null leaves the reader's gap. */
    abstract val gapBefore: Spacing?

    /**
     * The part of [gapBefore] that is margins before any padding or border: what a browser
     * cuts at a page top it didn't force. Null: all of it.
     */
    abstract val marginBefore: Spacing?

    /** A forced page break before the block (`page-break-before: always`). */
    abstract val breakBefore: Boolean

    /** No page break before the block if one can go earlier (`break-after: avoid` above it, `break-before: avoid`, inside a `break-inside: avoid` box). */
    abstract val keepWithPrevious: Boolean

    /** No page break between the block's own lines if one can go earlier (`break-inside: avoid`). */
    abstract val keepLines: Boolean

    /** The boxes it sits in, outermost first ([BlockBox]). */
    abstract val boxes: List<BlockBox>
}

/**
 * Offsets contract (docs/TECH.md 5.2/5.3): a block's text is its spans joined in
 * order, and every locator charOffset and annotation anchor charOffset is an
 * index into that exact string. Spans never overlap and never reorder.
 *
 * [title] comes from the book's TOC (EPUB3 nav or NCX), null when the TOC
 * has no entry for the spine document. [size] is the document's bytes in the book's
 * file, 0 when it's out of the reading order (`linear="no"`) or not an EPUB: what a
 * location is counted in ([com.baca.shared.domain.reader.BookLocations]).
 */
data class Chapter(
    val href: String,
    val title: String?,
    val blocks: List<Block>,
    val size: Int = 0,
    /** in the reading order; false (`linear="no"`, FB2's notes) is reached only by links and the contents */
    val linear: Boolean = true,
    /** the share of the document's text (its body's `textContent`) before each element id: where a contents entry pointing inside it starts */
    val anchors: Map<String, Float> = emptyMap(),
    /** the index of the block each element id is in, or comes before: where a link or contents entry to the id lands */
    val anchorBlocks: Map<String, Int> = emptyMap(),
    /** its body's font size (over a browser's 16px) and family: the running text, in a book with too little text to tell */
    val bodyScale: Float = 1f,
    val bodyFamily: String? = null,
    /** set in a vertical writing mode: lines run down the page, from the right edge */
    val vertical: Boolean = false,
)

/** A contents entry; [level] 1 for the top of the contents, 2 for an entry under one of those… */
data class TocEntry(
    val href: String,
    val title: String,
    val level: Int = 1,
    /** the id in [href]'s document it points to (its fragment); null: the document's start */
    val anchor: String? = null,
)

data class ParsedEpub(
    val title: String,
    val author: String,
    val coverHref: String?,
    val chapters: List<Chapter>,
    /** Ordered TOC entries (docs' zip paths for internal links, absolute URLs external). */
    val toc: List<TocEntry> = emptyList(),
    /** The OPF's unique dc:identifier (ISBN, UUID, URL…), raw; null when absent. */
    val identifier: String? = null,
    /** The OPF's first dc:language (BCP 47, "en", "ja-JP"); hyphenation follows it, as in LibreraX. */
    val language: String? = null,
    /** The fonts the book ships (`@font-face` in its stylesheets), for text the book sets in them. */
    val fonts: List<BookFont> = emptyList(),
    /** Images the book carries inside itself rather than as files (FB2's `<binary>`), by src. */
    val binaries: Map<String, ByteArray> = emptyMap(),
    /** The chapter where the text starts (the `bodymatter` landmark or the guide's `text`), which a new book opens at; null: none given. */
    val textStart: String? = null,
    /**
     * A fixed-layout (pre-paginated) book whose pages are each a picture: the pictures' zip paths in reading order,
     * to be read page by page like a comic. Null otherwise: a fixed-layout book with text is read reflowed.
     */
    val fixedPages: List<String>? = null,
    /** set in a vertical writing mode (its first chapter of text is): its pages are drawn turned a quarter, lines down the page */
    val vertical: Boolean = false,
    /** pages turn right to left: the spine says so, or the book's language or first page reads that way */
    val pageRtl: Boolean = false,
    /** what the reader's settings changed in how it was read: see [ParseOptions] */
    val options: ParseOptions = ParseOptions(),
    /** the book's CSS asks for the colour scheme, so a theme of the other kind must read it again */
    val schemeAware: Boolean = false,
)

/**
 * The zip path of the parsed OPF document is the base for resolving chapter
 * hrefs; [Chapter.href] values are full zip paths usable as locator hrefs.
 */
fun parseEpub(path: Path, options: ParseOptions = ParseOptions()): ParsedEpub {
    val zip = FileSystem.SYSTEM.openZip(path)
    val containerXml = zip.read(CONTAINER) { readUtf8() }
    val opfPath = findRootfilePath(containerXml)?.toPath()
        ?: error("EPUB has no rootfile in $CONTAINER")
    val opfXml = zip.read(opfPath) { readUtf8() }
    val base = opfPath.parent ?: ROOT
    val opf = parseOpf(opfXml)
    // The TOC document's relative hrefs resolve against the TOC file's own
    // directory — not the OPF's! nav.xhtml/text/*.ncx commonly sit in a subdir,
    // and resolving against the OPF dir drops that prefix, which made every
    // toc href non-matching against chapter paths (contents never linked).
    val toc = opf.tocHref?.let { tocHref ->
        runCatching {
            val tocPath = (base / tocHref.toPath()).normalized()
            val tocDir = tocPath.parent ?: ROOT
            val tocXml = zip.read(tocPath) { readUtf8() }
            parseToc(tocXml, tocHref) { src -> (tocDir / src.toPath()).normalized().toString() }
        }.getOrDefault(emptyList())
    } ?: emptyList()
    val titles = toc.associate { it.href to it.title }
    // where the text starts: the nav's landmarks, else the EPUB 2 guide
    val textStart = opf.navHref?.let { navHref ->
        runCatching {
            val navPath = (base / navHref.toPath()).normalized()
            parseNavTextStart(zip.read(navPath) { readUtf8() }) { src -> ((navPath.parent ?: ROOT) / src.toPath()).normalized().toString() }
        }.getOrNull()
    } ?: opf.guideText?.substringBefore('#')?.takeIf { it.isNotEmpty() }?.let { (base / it.toPath()).normalized().toString() }
    // one book usually shares a stylesheet across chapters: read each file once
    val cssFiles = mutableMapOf<Path, String?>()
    fun css(path: Path): String? = cssFiles.getOrPut(path) { runCatching { zip.read(path) { readUtf8() } }.getOrNull() }
    // whether the book styles itself by the colour scheme: then a theme of the other kind reads it again
    var schemeAware = false
    val chapters = opf.spine.mapNotNull { idref ->
        val href = opf.manifest[idref] ?: return@mapNotNull null
        val chapterPath = (base / href.toPath()).normalized()
        val xhtml = zip.read(chapterPath) { readUtf8() }
        if (xhtml.contains(COLOR_SCHEME, ignoreCase = true)) schemeAware = true
        // XHTML-relative hrefs resolve against the chapter document's own
        // directory, not the OPF directory (they coincide when spine docs sit
        // next to the OPF, which is why Frankenstein parsed 'correctly').
        val chapterDir = chapterPath.parent ?: ROOT
        val anchors = HashMap<String, Float>()
        val anchorBlocks = HashMap<String, Int>()
        var body = 1f to (null as String?)
        var vertical = false
        Chapter(
            href = chapterPath.toString(),
            title = titles[chapterPath.toString()],
            blocks = parseXhtml(
                xhtml,
                resolveSrc = { src -> (chapterDir / src.toPath()).normalized().toString() },
                loadCss = { href -> css((chapterDir / href.substringBefore('#').toPath()).normalized()) },
                anchors = anchors,
                anchorBlocks = anchorBlocks,
                darkScheme = options.darkScheme,
                options = options,
                defaultRtl = rtlLanguage(opf.language),
                onBody = { scale, family -> body = scale to family },
                onVertical = { vertical = true },
            ),
            anchors = anchors,
            anchorBlocks = anchorBlocks,
            size = if (idref in opf.nonLinear) 0 else zip.metadata(chapterPath).size?.toInt() ?: 0,
            linear = idref !in opf.nonLinear,
            bodyScale = body.first,
            bodyFamily = body.second,
            vertical = vertical,
        )
    }
    // Normalize the cover href like spine hrefs: it is relative to the OPF dir
    // in the manifest, and zip reads need the full path.
    val cover = opf.coverHref?.let {
        if (it.contains("://")) it else (base / it.toPath()).normalized().toString()
    }
    // the fonts its stylesheets ship, each url() relative to its stylesheet
    val fonts = cssFiles.flatMap { (path, css) ->
        css?.let { fontFacesOf(it) { url -> ((path.parent ?: ROOT) / url.toPath()).normalized().toString() } }.orEmpty()
    }
    return ParsedEpub(
        opf.title, opf.author, cover, withRunningText(chapters, fonts.map { it.family }.toSet()), toc, opf.identifier, opf.language, fonts,
        textStart = textStart,
        fixedPages = if (opf.fixedLayout) imagePagesOf(chapters) else null,
        vertical = chapters.firstOrNull { c -> c.blocks.any { it is Block.Text && it.text.isNotBlank() } }?.vertical == true,
        pageRtl = opf.pageRtl || rtlLanguage(opf.language) || chapters.any { it.vertical } || chapters.firstNotNullOfOrNull { c -> c.blocks.firstOrNull { it is Block.Text && it.text.isNotBlank() } as? Block.Text }?.rtl == true,
        options = options,
        schemeAware = schemeAware || cssFiles.values.any { it?.contains(COLOR_SCHEME, ignoreCase = true) == true },
    )
}

internal fun parseToc(
    tocXml: String,
    tocHref: String,
    resolve: (String) -> String,
): List<TocEntry> =
    if (tocHref.endsWith(".ncx")) parseNcxToc(tocXml, resolve) else parseNavToc(tocXml, resolve)

/** EPUB2 toc.ncx: navLabel/text + content/@src pairs. */
internal fun parseNcxToc(xml: String, resolve: (String) -> String): List<TocEntry> {
    val reader = newXmlReader(xml)
    val entries = mutableListOf<TocEntry>()
    val title = StringBuilder()
    var inNavLabel = false
    var depth = 0
    while (reader.hasNext()) {
        val event = reader.next()
        when (event) {
            EventType.START_ELEMENT -> when (reader.localName) {
                "navPoint" -> depth++
                "navLabel" -> {
                    inNavLabel = true
                    title.setLength(0)
                }
                "content" -> {
                    val raw = reader.attr("src")
                    if (raw != null && title.isNotBlank()) {
                        entries.add(TocEntry(resolve(raw.substringBefore('#')), title.toString().trim(), depth, fragmentOf(raw)))
                    }
                }
            }
            EventType.TEXT, EventType.CDSECT, EventType.ENTITY_REF -> if (inNavLabel) title.append(reader.text)
            EventType.END_ELEMENT -> when (reader.localName) {
                "navLabel" -> inNavLabel = false
                "navPoint" -> depth--
            }
            else -> {}
        }
    }
    return entries
}

/** EPUB3 nav.xhtml: <a href> + text pairs inside the toc nav. */
internal fun parseNavToc(xml: String, resolve: (String) -> String): List<TocEntry> {
    val reader = newXmlReader(xml)
    // nav.xhtml can hold several navs: the toc, a page-list (printed page
    // numbers) and landmarks. Only the toc nav is a table of contents.
    val navs = mutableListOf<Pair<Boolean, MutableList<TocEntry>>>()
    var current: MutableList<TocEntry>? = null
    var currentIsToc = false
    var anchorHref: String? = null
    val title = StringBuilder()
    // how deep in the nav's lists the entry is
    var depth = 0
    while (reader.hasNext()) {
        val event = reader.next()
        when (event) {
            EventType.START_ELEMENT -> when (reader.localName) {
                "ol", "ul" -> depth++
                "nav" -> {
                    // epub:type's localName is plain "type"; id="toc" also common
                    val type = reader.attrOrEmpty("type")
                    val id = reader.attrOrEmpty("id")
                    current = mutableListOf()
                    currentIsToc = type.equals("toc", true) || id.equals("toc", true)
                }
                "a" -> {
                    anchorHref = reader.attr("href")
                    title.setLength(0)
                }
            }
            EventType.TEXT, EventType.CDSECT, EventType.ENTITY_REF ->
                if (anchorHref != null) title.append(reader.text)
            EventType.END_ELEMENT -> when (reader.localName) {
                "ol", "ul" -> depth--
                "a" -> {
                    if (anchorHref != null && current != null) {
                        val src = anchorHref.substringBefore('#')
                        if (title.isNotBlank()) current.add(TocEntry(resolve(src), title.toString().trim(), depth.coerceAtLeast(1), fragmentOf(anchorHref)))
                    }
                    anchorHref = null
                }
                "nav" -> {
                    current?.let { navs.add(currentIsToc to it) }
                    current = null
                }
            }
            else -> {}
        }
    }
    // the toc nav when present, else the first nav (some EPUBs omit the type)
    return navs.firstOrNull { it.first }?.second ?: navs.firstOrNull()?.second ?: emptyList()
}

/** The picture on each page of a fixed-layout book, if every page in the reading order is only one. */
private fun imagePagesOf(chapters: List<Chapter>): List<String>? {
    val pages = chapters.filter { it.linear }.map { chapter ->
        chapter.blocks.filter { it !is Block.Text || it.text.isNotBlank() }.singleOrNull()?.let { (it as? Block.Image)?.src }?.takeIf { it.isNotEmpty() }
    }
    return pages.takeIf { it.isNotEmpty() && it.all { page -> page != null } }?.filterNotNull()
}

/** The nav document's landmark for the start of the text (`bodymatter`), resolved; null when it has none. */
internal fun parseNavTextStart(xml: String, resolve: (String) -> String): String? {
    val reader = newXmlReader(xml)
    var inLandmarks = false
    while (reader.hasNext()) {
        when (reader.next()) {
            EventType.START_ELEMENT -> when (reader.localName) {
                "nav" -> inLandmarks = reader.attrOrEmpty("type").split(' ').contains("landmarks")
                "a" -> if (inLandmarks && isTextStart(reader.attrOrEmpty("type"))) {
                    reader.attr("href")?.substringBefore('#')?.takeIf { it.isNotEmpty() }?.let { return resolve(it) }
                }
            }
            EventType.END_ELEMENT -> if (reader.localName == "nav") inLandmarks = false
            else -> {}
        }
    }
    return null
}

private fun fragmentOf(href: String): String? = href.substringAfter('#', "").ifEmpty { null }

private const val COLOR_SCHEME = "prefers-color-scheme"

// languages written right to left (a script's direction, as `Intl.Locale.textInfo` gives it)
private val RTL_LANGUAGES = setOf("ar", "he", "iw", "fa", "ur", "yi", "ps", "sd", "ug", "dv", "ckb", "prs", "arc", "ks", "az-arab")

/** Whether a language tag is written right to left. */
fun rtlLanguage(tag: String?): Boolean {
    val lower = tag?.lowercase()?.replace('_', '-') ?: return false
    return lower.substringBefore('-') in RTL_LANGUAGES && !lower.contains("-latn")
}

// a landmark's or guide reference's type, as foliate's goToTextStart takes it
private fun isTextStart(type: String) = type.split(' ').any { it == "bodymatter" || it == "text" }

// `@import url("a.css") screen;`, `@import 'a.css';`: the url and its media
private val IMPORT = Regex("""@import\s+(?:url\(\s*)?["']?([^"')\s;]+)["']?\s*\)?\s*([^;]*);""", RegexOption.IGNORE_CASE)
// a stylesheet importing itself, or round a ring of them, stops here
private const val MAX_IMPORT_DEPTH = 8

private val CONTAINER = "META-INF/container.xml".toPath()
private val ROOT = "/".toPath()

private fun findRootfilePath(containerXml: String): String? {
    val reader = newXmlReader(containerXml)
    while (reader.hasNext()) {
        val event = reader.next()
        if (event === EventType.START_ELEMENT && reader.localName == "rootfile") {
            return reader.attr("full-path")
        }
    }
    return null
}

private data class Opf(
    val title: String,
    val author: String,
    val coverHref: String?,
    val tocHref: String?,
    val manifest: Map<String, String>,
    val spine: List<String>,
    val identifier: String?,
    val language: String?,
    /** spine items out of the reading order (`linear="no"`) */
    val nonLinear: Set<String>,
    val navHref: String?,
    /** the EPUB 2 guide's start of the text (`<reference type="text">`), relative to the OPF */
    val guideText: String?,
    /** `rendition:layout` pre-paginated, or the iBooks/Kobo `fixed-layout` option */
    val fixedLayout: Boolean = false,
    /** the spine's `page-progression-direction` is right to left */
    val pageRtl: Boolean = false,
)

private fun parseOpf(opfXml: String): Opf {
    val reader = newXmlReader(opfXml)
    val manifest = mutableMapOf<String, String>()
    val spine = mutableListOf<String>()
    val nonLinear = mutableSetOf<String>()
    var title = ""
    var author = ""
    var language: String? = null
    var coverHref: String? = null
    var coverId: String? = null
    var navHref: String? = null
    var ncxHref: String? = null
    var guideText: String? = null
    var fixedLayout = false
    var pageRtl = false
    var inMetadata = false
    var capture: ((String) -> Unit)? = null
    // package@unique-identifier names the dc:identifier that identifies the book
    var uniqueId: String? = null
    val identifiers = mutableListOf<Pair<String?, String>>()

    while (reader.hasNext()) {
        val event = reader.next()
        when (event) {
            EventType.START_ELEMENT -> when (reader.localName) {
                "package" -> uniqueId = reader.attr("unique-identifier")
                "metadata" -> inMetadata = true
                "identifier" -> if (inMetadata) {
                    val id = reader.attr("id")
                    capture = { identifiers.add(id to it) }
                }
                "item" -> {
                    val id = reader.attr("id")
                    val href = reader.attr("href")
                    if (id != null && href != null) {
                        manifest[id] = href
                        if (reader.attrOrEmpty("properties").contains("cover-image")) {
                            coverHref = href
                        }
                        if (reader.attrOrEmpty("properties").contains("nav")) {
                            navHref = href
                        }
                        if (reader.attrOrEmpty("media-type") == "application/x-dtbncx+xml") {
                            ncxHref = href
                        }
                    }
                }
                "spine" -> if (reader.attr("page-progression-direction")?.lowercase() == "rtl") pageRtl = true
                "itemref" -> reader.attr("idref")?.let { idref ->
                    spine.add(idref)
                    if (reader.attr("linear") == "no") nonLinear.add(idref)
                }
                "title" -> if (inMetadata) capture = { title = it }
                "creator" -> if (inMetadata) capture = { author = it }
                "language" -> if (inMetadata && language == null) capture = { language = it.trim().ifEmpty { null } }
                "meta" -> {
                    if (reader.attrOrEmpty("name") == "cover") coverId = reader.attr("content")
                    if (reader.attrOrEmpty("name") == "fixed-layout" && reader.attrOrEmpty("content").equals("true", true)) fixedLayout = true
                    if (reader.attrOrEmpty("property") == "rendition:layout") capture = { if (it.trim() == "pre-paginated") fixedLayout = true }
                }
                "reference" -> if (guideText == null && isTextStart(reader.attrOrEmpty("type"))) guideText = reader.attr("href")
            }
            EventType.TEXT, EventType.CDSECT -> capture?.invoke(reader.text)
            EventType.END_ELEMENT -> when (reader.localName) {
                "title" -> capture = null
                "meta" -> capture = null
                "creator" -> capture = null
                "language" -> capture = null
                "identifier" -> capture = null
                "metadata" -> inMetadata = false
            }
            else -> {}
        }
    }
    val coverByMeta = coverId?.let { manifest[it] }
    val identifier = (identifiers.firstOrNull { it.first != null && it.first == uniqueId } ?: identifiers.firstOrNull())
        ?.second?.trim()?.takeIf { it.isNotEmpty() }
    return Opf(title.trim(), author.trim(), coverHref ?: coverByMeta, navHref ?: ncxHref, manifest, spine, identifier, language, nonLinear, navHref, guideText, fixedLayout, pageRtl)
}

// <br> placeholder while collecting text: source newlines are plain whitespace
// in HTML, only <br> is a real line break
private const val BR_MARK = '\u2028'

/**
 * HTML whitespace rules: runs of spaces/tabs/newlines become one space,
 * nothing at the start or end of a block or around a <br>. Without this the
 * source file's own line wrapping showed up as hard breaks and blank lines.
 */
internal fun collapseWhitespace(spans: List<FormattedSpan>): List<FormattedSpan> {
    val builders = spans.map { StringBuilder() }
    var pendingSpace: StringBuilder? = null
    var atLineStart = true
    spans.forEachIndexed { i, span ->
        val out = builders[i]
        for (c in span.text) {
            when {
                c == BR_MARK -> {
                    pendingSpace = null
                    out.append('\n')
                    atLineStart = true
                }
                c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u000C' ->
                    if (!atLineStart) pendingSpace = out
                else -> {
                    // the space stays in the span where the whitespace was
                    pendingSpace?.append(' ')
                    pendingSpace = null
                    out.append(c)
                    atLineStart = false
                }
            }
        }
    }
    return spans.mapIndexedNotNull { i, span ->
        builders[i].toString().takeIf { it.isNotEmpty() }?.let { span.copy(text = it) }
    }
}

/**
 * The printed book's page numbers ("12", "[Pg 12]", "xv"), marked by EPUB 3
 * `epub:type="pagebreak"`, ARIA `doc-pagebreak` or the usual class names.
 * They belong to the paper layout, not the text.
 */
private fun isPageMarker(reader: XmlReader): Boolean {
    if (reader.attrOrEmpty("type").split(' ').contains("pagebreak")) return true
    if (reader.attrOrEmpty("role") == "doc-pagebreak") return true
    return reader.attrOrEmpty("class").split(' ').any { it.lowercase() in PAGE_NUMBER_CLASSES }
}

private val PAGE_NUMBER_CLASSES = setOf("pagenum", "pageno", "page-number", "pagenumber", "page_number", "pagebreak")

internal fun parseXhtml(
    source: String,
    resolveSrc: (String) -> String = { it },
    /** stylesheet text for a `<link>` href, relative to this document */
    loadCss: (String) -> String? = { null },
    /** filled with [Chapter.anchors] */
    anchors: MutableMap<String, Float>? = null,
    /** filled with [Chapter.anchorBlocks] */
    anchorBlocks: MutableMap<String, Int>? = null,
    /** the reading theme is dark: the book's `prefers-color-scheme: dark` rules hold */
    darkScheme: Boolean = false,
    /** told the body's size (over 16px) and family, which stand for the running text where there's too little of it */
    onBody: (scale: Float, family: String?) -> Unit = { _, _ -> },
    /** the book's language reads right to left: where the page doesn't say otherwise, so does its text */
    defaultRtl: Boolean = false,
    /** told when the page's root sets a vertical `writing-mode` */
    onVertical: () -> Unit = {},
    /** what the reader's settings change in the page */
    options: ParseOptions = ParseOptions(),
): List<Block> {
    // MathML written out along the line, which the blocks below then read as text
    val xhtml = mathmlToHtml(source)
    val reader = newXmlReader(xhtml)
    val blocks = mutableListOf<Block>()
    val stylesheet = Stylesheet(darkScheme || options.darkScheme)
    // the reader's own CSS comes after the book's, which it is added after in order
    if (options.userCss.isNotBlank()) stylesheet.addUser(options.userCss)
    // each element's place among its siblings, for :last-child and the like; the ordinal counts elements
    val facts = elementFacts(xhtml)
    var ordinal = -1
    var spans: MutableList<FormattedSpan>? = null
    var kind = BlockKind.PARAGRAPH
    var headingLevel = 0
    var blockAlign = TextAlign.START
    var blockIndent = Spacing.ZERO
    var blockIndentShare = 0f
    var blockScale: Float? = null
    var blockFloatsRight = false
    var blockKeepsSpaces = false
    var blockDropCap: DropCap? = null
    var blockLineRatio: Float? = null
    var blockLinePx: Float? = null
    var blockHyphenate = true
    var blockRagged = false
    // a printed page number was skipped in the open block: alone there, it still takes a line
    var blockHadMarker = false
    // inside a nav, and the depth of the open list item, for contents entries
    var navDepth = 0
    var openLi = -1
    // a forced page break waiting for the next block
    var pendingBreak = false
    // inside a floated first letter: its element's depth and line height, 0 when not inside one
    var capDepth = 0
    var capLineRatio = 1f
    // the next list item's marker, for the first block of text inside it
    var pendingMarker: String? = null
    // the open lists' item counters, innermost last
    val listCounters = ArrayDeque<Int>()
    // the block's own computed size, so spans carry theirs relative to it
    var blockFontScale = 1f
    // book CSS vertical margins (collapsing: the largest wins) and padding (adding up) since the last block
    var pendingMargin: Margins? = null
    var pendingPadding = Spacing.ZERO
    // margins and padding above an open box's top edge, which don't collapse with what's inside it
    var pendingOuter = Spacing.ZERO
    // how much of the pending gap came before the first top padding or border opened since the last
    // block: the margins a page break cuts; null while there is none. And each block's, in block order
    var pendingCut: Spacing? = null
    var takenCut: Spacing? = null
    val blockCuts = mutableListOf<Spacing?>()
    fun pendingTotal() = pendingOuter + pendingMargin.net() + pendingPadding
    /** A top padding or border is about to open: what's pending so far is what a page break would cut. */
    fun startKeeping(added: Spacing) {
        if (pendingCut == null && added != Spacing.ZERO) pendingCut = pendingTotal()
    }
    // the open boxes, outermost last, and each block's box ids, in block order
    class OpenedBox(val box: BlockBox) {
        var depth = -1
        var outerAt = Spacing.ZERO
        var topInset: Spacing? = null
        var bottomInset = Spacing.ZERO
    }
    val openedBoxes = ArrayDeque<OpenedBox>()
    val finishedBoxes = mutableMapOf<Int, OpenedBox>()
    val blockBoxes = mutableListOf<List<Int>>()
    fun openBox(box: BlockBox) {
        val opened = OpenedBox(box)
        openedBoxes.addLast(opened)
        finishedBoxes[box.id] = opened
    }
    var borderedElements = 0
    var quoteDepth = 0
    var linkDepth = 0
    var linkHref: String? = null
    // inside a printed-page marker or a display:none element: its depth, 0 when not skipping
    var skipDepth = 0
    // inside <head>, whose text (the <title>) a browser doesn't show
    var inHead = false
    // inside an <svg>, whose only content Baca shows is a picture
    var svgDepth = 0
    var svgBox = ImageBox()
    // the open tables, innermost last: each one's column widths and where its rows and cells have got to
    class OpenTable(val id: Int, val cellPadding: Float?, val spacing: Pair<Spacing, Spacing>, val border: Float) {
        val widths = mutableListOf<Float?>()
        var row = 0
        var column = 0
    }
    val tables = ArrayDeque<OpenTable>()
    var tableCount = 0
    var rowCount = 0

    /** A `<font>`'s `size` (1–7, or ±n from 3, as a size keyword), `face` and `color`. */
    fun fontHints(): CssStyle {
        val size = reader.attr("size")?.trim()?.let { value ->
            val n = value.trimStart('+', '-').toIntOrNull() ?: return@let null
            when {
                value.startsWith("+") -> 3 + n
                value.startsWith("-") -> 3 - n
                else -> n
            }.coerceIn(1, 7)
        }
        val css = listOfNotNull(
            size?.let { "font-size: ${FONT_SIZES[it - 1]}" },
            reader.attr("face")?.let { "font-family: $it" },
            reader.attr("color")?.let { "color: $it" },
        ).joinToString(";")
        return parseDeclarations(css).normal
    }

    /** What an element's attributes say as CSS: a table's `cellspacing`, its cells' `cellpadding`, an `align`. */
    fun hintsOf(tag: String): CssStyle {
        val table = tables.lastOrNull()
        // a table's border="N": an N px outset border round it, and 1px inset ones round its cells, in gray
        fun bordered(px: Float, style: String) = BorderSide(px, style, BORDER_GRAY).let { CssStyle(borderTop = it, borderRight = it, borderBottom = it, borderLeft = it) }
        return listOfNotNull(
            when (reader.attr("dir")?.lowercase()) { "rtl" -> CssStyle(rtl = true); "ltr" -> CssStyle(rtl = false); else -> null },
            if (tag == "td" || tag == "th") table?.cellPadding?.let { CssLength.Px(it) }?.let { CssStyle(paddingTop = it, paddingRight = it, paddingBottom = it, paddingLeft = it) } else null,
            if ((tag == "td" || tag == "th") && (table?.border ?: 0f) > 0f) bordered(1f, "inset") else null,
            if (tag == "table") reader.attr("cellspacing")?.toFloatOrNull()?.let { CssStyle(borderSpacing = CssLength.Px(it) to CssLength.Px(it)) } else null,
            if (tag == "table") (reader.attr("border")?.let(::attrLength) as? CssLength.Px)?.px?.takeIf { it > 0f }?.let { bordered(it, "outset") } else null,
            if (tag == "font") fontHints() else null,
            if (tag == "ol" || tag == "ul" || tag == "li") reader.attr("type")?.let { LIST_TYPES[it] ?: LIST_TYPES[it.lowercase()] }?.let { CssStyle(listStyle = it) } else null,
            if (tag in ALIGNED_TAGS) when (reader.attr("align")?.lowercase()) {
                "center", "middle" -> CssStyle(align = TextAlign.CENTER, pushesBlocks = TextAlign.CENTER)
                "right" -> CssStyle(align = TextAlign.END, pushesBlocks = TextAlign.END)
                "left", "justify" -> CssStyle(align = TextAlign.START)
                else -> null
            } else null,
        ).fold(CssStyle()) { all, hint -> all.overriddenBy(hint) }
    }

    /** A `<td>`/`<th>`'s style: its place in the table, its own width taken as its column's when the columns set none. */
    fun inCell(tag: String, style: Computed, declared: CssStyle): Computed {
        if (tag != "td" && tag != "th") return style
        val table = tables.lastOrNull() ?: return style
        val span = reader.attr("colspan")?.toIntOrNull()?.coerceIn(1, 100) ?: 1
        val width = (declared.width ?: reader.attr("width")?.let(::attrLength)) as? CssLength.Percent
        if (width != null && span == 1) {
            while (table.widths.size <= table.column) table.widths.add(null)
            if (table.widths[table.column] == null) table.widths[table.column] = width.percent / 100f
        }
        val ownStart = Spacing(px = declared.borderLeft.drawnPx) + declared.paddingLeft.sideSpacing(style.scale)
        val ownEnd = Spacing(px = declared.borderRight.drawnPx) + declared.paddingRight.sideSpacing(style.scale)
        val cell = TableCell(table.id, table.row, table.column, span, table.widths.toList(), table.spacing.first, style.rowAlign, ownStart, ownEnd)
        table.column += span
        return style.copy(cell = cell)
    }

    // the open `display: inline-block` elements with a `min-width`, innermost last, and how many there have been
    val inlineBoxes = ArrayDeque<Int>()
    var inlineBoxCount = 0
    class OpenBox(val id: Int, val minEm: Float, val depth: Int)
    val openBoxes = ArrayDeque<OpenBox>()

    // the open blocks of a fixed `height`, innermost last: each one's depth, its first block, its height
    class HeldHeight(val depth: Int, val from: Int, val height: Spacing)
    val heldHeights = ArrayDeque<HeldHeight>()

    // per open element, whether it lays out as a block (its tag, or the book's `display`)
    val openIsBlock = ArrayDeque<Boolean>()
    // inside a <style> element: its text, collected for the stylesheet
    var styleText: StringBuilder? = null
    var styleMedia: String? = null
    // the open <q> elements
    var quoteMarks = 0

    /**
     * A stylesheet and the ones it `@import`s, which come first; [href] is where it is,
     * relative to this document ("" for a `<style>`), which its imports are relative to.
     * A linked or imported sheet is a CSS file, which foliate rewrites ([linkedSheet]).
     */
    fun addSheet(css: String, href: String, linked: Boolean, depth: Int = 0) {
        IMPORT.findAll(css).forEach { import ->
            val media = import.groupValues[2].trim()
            if (depth >= MAX_IMPORT_DEPTH || (media.isNotEmpty() && !mediaMatches(media, darkScheme))) return@forEach
            val target = href.substringBeforeLast('/', "").let { dir -> if (dir.isEmpty()) import.groupValues[1] else "$dir/${import.groupValues[1]}" }
            loadCss(target)?.let { addSheet(it, target, linked = true, depth + 1) }
        }
        stylesheet.add(if (linked) linkedSheet(css) else css)
    }
    // float:right content inside a paragraph gets its own right-aligned line;
    // the depth of the floated element, 0 when not inside one
    var floatDepth = 0
    // computed (inherited) style of each open element, innermost last
    val styles = ArrayDeque<Computed>().apply { add(Computed(rtl = options.rtl ?: defaultRtl)) }
    // the open elements as CSS selectors see them, outermost first, and each one's declared style
    val path = ArrayDeque<CssElement>()
    val declaredStyles = ArrayDeque<CssStyle>()
    // per open element, its last child element so far: the next child's previous sibling
    val lastChild = ArrayDeque<CssElement?>().apply { add(null) }

    fun takeBreak(): Boolean = pendingBreak.also { pendingBreak = false }
    // `break-*: avoid`: the next block keeps with the one before; open `break-inside: avoid` elements, by depth and first block
    var pendingAvoid = false
    val avoidInside = ArrayDeque<Pair<Int, Int>>()
    val blockKeeps = mutableListOf<Pair<Boolean, Boolean>>()

    fun takeGap(): Spacing? {
        val gap = if (pendingMargin == null && pendingPadding == Spacing.ZERO && pendingOuter == Spacing.ZERO) null
        else pendingOuter + pendingMargin.net() + pendingPadding
        takenCut = if (gap == null) null else pendingCut ?: gap
        pendingCut = null
        pendingMargin = null
        pendingPadding = Spacing.ZERO
        pendingOuter = Spacing.ZERO
        // a box's first block: how far its top edge is above the text
        openedBoxes.forEach { box -> if (box.topInset == null) box.topInset = (gap ?: Spacing.ZERO) - box.outerAt }
        return gap
    }

    /** A block's boxes, as they'll be once closed ([finishBoxes]). */
    fun boxIds(): List<Int> = openedBoxes.map { it.box.id }

    fun addBlock(block: Block) {
        blocks.add(block)
        blockBoxes.add(boxIds())
        blockCuts.add(takenCut)
        blockKeeps.add((pendingAvoid || avoidInside.any { it.second < blocks.size - 1 }) to avoidInside.isNotEmpty())
        pendingAvoid = false
        takenCut = null
    }

    fun openBlock(k: BlockKind, level: Int) {
        val style = styles.last()
        kind = if (quoteDepth > 0 && k == BlockKind.PARAGRAPH) BlockKind.QUOTE else k
        headingLevel = level
        blockAlign = style.align
        blockIndent = style.indent
        blockIndentShare = style.indentShare
        blockScale = style.scale.takeIf { it != 1f }
        blockKeepsSpaces = style.keepsSpaces
        blockDropCap = null
        blockLineRatio = style.lineRatio
        blockLinePx = style.linePx
        blockHyphenate = style.hyphenate
        blockRagged = style.ragged
        blockFloatsRight = false
        blockFontScale = style.scale
        spans = mutableListOf()
    }

    fun closeBlock() {
        // a browser gives a line to text however small and hidden: the block keeps one, empty
        if (blockHadMarker && spans?.all { it.text.isBlank() } == true) spans?.add(FormattedSpan("\u00A0", false, false))
        blockHadMarker = false
        val list = spans ?: return
        spans = null
        val merged = mutableListOf<FormattedSpan>()
        for (s in list) {
            val last = merged.lastOrNull()
            if (last != null && last.image == null && s.image == null && last.spacer == null && s.spacer == null && last.copy(text = "") == s.copy(text = "")) {
                merged[merged.lastIndex] = last.copy(text = last.text + s.text)
            } else {
                merged.add(s)
            }
        }
        val collapsed = if (blockKeepsSpaces) keptWhitespace(merged) else collapseWhitespace(merged)
        // not isNotBlank: a lone &nbsp; survives collapsing and draws an empty
        // line, as in HTML (books use <p>&nbsp;</p> for scene breaks)
        if (collapsed.any { it.text.isNotEmpty() }) {
            val style = styles.last()
            addBlock(
                Block.Text(
                    collapsed, kind, headingLevel, blockAlign, blockIndent, blockScale, takeGap(),
                    style.inset, emptyList(), blockFloatsRight, style.insetEnd, pendingMarker, blockKeepsSpaces,
                    style.insetShare, style.insetEndShare, blockDropCap, blockLineRatio, blockHyphenate, style.cell,
                    takeBreak(), blockRagged, style.columns, lang = style.lang, indentShare = blockIndentShare, linePx = blockLinePx, rtl = style.rtl,
                ).let { if (it.ragged) it.copy(align = TextAlign.START) else it },
            )
            pendingMarker = null
        }
    }

    fun appendText(t: String, image: InlineImage? = null, spacer: InlineSpacer? = null) {
        val style = styles.last()
        val plainLink = linkHref != null && !style.underline
        val scale = style.scale / blockFontScale
        val face = style.face.takeIf { it != BookFace.SERIF }
        val underline = linkHref == null && style.underlined
        val list = spans ?: return
        val text = transformed(t, style.transform, list.lastOrNull()?.text?.lastOrNull())
        list.add(
            FormattedSpan(
                text, style.bold, style.italic, linkHref, style.smallCaps, plainLink, scale, face, underline,
                style.baseline, style.baselineLine, style.letterSpacing, if (style.invisible) INVISIBLE else style.color, style.wordSpacing, image, spacer, inlineBoxes.lastOrNull(),
                style.family, style.struck, style.baselinePx, style.inAnchor, style.inlineBackground,
            ),
        )
    }

    /** An inline element's own space: its padding or margin on one side, as one placeholder char. */
    fun appendSpace(length: CssLength?, scale: Float) {
        val em = length.spacing(scale).let { (it.em + it.px / 16f) / scale }
        if (em > 0f) appendText(INLINE_IMAGE.toString(), spacer = InlineSpacer(em))
    }

    // the body's text so far, as its textContent counts it (hidden text and all), and where each id came in it
    var inBody = false
    var bodyText = 0
    val idsAt = HashMap<String, Int>()
    val idBlocks = HashMap<String, Int>()
    while (reader.hasNext()) {
        val event = reader.next()
        if (event == EventType.START_ELEMENT) ordinal++
        when (event) {
            EventType.START_ELEMENT -> {
                if (reader.localName == "body") inBody = true
                if (inBody) reader.attr("id")?.let {
                    idsAt.getOrPut(it) { bodyText }
                    // the block the id is in, or the next one to be made
                    idBlocks.getOrPut(it) { blocks.size }
                }
            }
            EventType.TEXT, EventType.CDSECT, EventType.ENTITY_REF, EventType.IGNORABLE_WHITESPACE -> if (inBody) bodyText += reader.text.length
            else -> {}
        }
        if (skipDepth > 0) {
            when (event) {
                EventType.START_ELEMENT -> skipDepth++
                EventType.END_ELEMENT -> skipDepth--
                else -> {}
            }
            continue
        }
        when (event) {
            EventType.START_ELEMENT -> {
                val tag = reader.localName.lowercase()
                val rel = reader.attrOrEmpty("rel").lowercase().split(' ')
                // an alternate stylesheet is one the reader picks, which no one does here
                if (tag == "link" && "stylesheet" in rel && "alternate" !in rel && reader.attr("media").let { it == null || mediaMatches(it, darkScheme) }) {
                    reader.attr("href")?.let { href -> loadCss(href)?.let { addSheet(it, href, linked = true) } }
                }
                if (tag == "style") {
                    styleText = StringBuilder()
                    styleMedia = reader.attr("media")
                }
                if (tag == "head") inHead = true
                // the SVG's own size is the picture's box: a cover page's "100%" fills the page
                if (tag == "svg" && svgDepth == 0) svgBox = ImageBox(
                    width = reader.attr("width")?.let(::attrLength) ?: CssLength.Percent(100f),
                    height = reader.attr("height")?.let(::attrLength)?.let { if (it is CssLength.Percent) CssLength.Vh(it.percent) else it },
                )
                val element = CssElement(
                    tag,
                    reader.attrOrEmpty("class").split(' ').filter { it.isNotEmpty() }.toSet(),
                    lastChild.last(),
                    reader.attr("id"),
                    (0 until reader.attributeCount).associate { reader.getAttributeLocalName(it).lowercase() to reader.getAttributeValue(it) },
                    facts.getOrNull(ordinal),
                )
                lastChild[lastChild.lastIndex] = element
                path.addLast(element)
                val declared = stylesheet.styleFor(path, reader.attr("style"), hintsOf(tag))
                    // foliate sets the root's padding over the book's, and the reader its direction
                    .let {
                        if (tag == "html") it.copy(paddingTop = null, paddingRight = null, paddingBottom = null, paddingLeft = null, rtl = options.rtl ?: it.rtl) else it
                    }
                if (isPageMarker(reader) || declared.hidden == true) {
                    if (declared.hidden != true && spans != null) blockHadMarker = true
                    path.removeLast()
                    skipDepth = 1
                    continue
                }
                lastChild.addLast(null)
                declaredStyles.addLast(declared)
                val isBlock = isBlock(tag, declared.display)
                openIsBlock.addLast(isBlock)
                // a block element ends the text before it, as in a browser
                if (isBlock) closeBlock()
                val style = styles.last().inherit(tag, declared).let { inherited ->
                    if (!isBlock) return@let inherited
                    // foliate zeroes the body's margins; every other block keeps the book's
                    val hasMargins = tag != "body" && tag != "html"
                    val marginLeft = if (hasMargins) declared.marginLeft.sideSpacing(inherited.scale) else Spacing.ZERO
                    val ruleAt = inherited.inset + marginLeft
                    // percent margins are of the containing block; a narrower width leaves room its auto margins take
                    val parentShare = styles.last().widthShare
                    val free = (declared.width as? CssLength.Percent)?.let { (1f - it.percent / 100f).coerceAtLeast(0f) * parentShare } ?: 0f
                    val leftAuto = hasMargins && declared.marginLeft == CssLength.Auto
                    val rightAuto = hasMargins && declared.marginRight == CssLength.Auto
                    // a parent's `-webkit-center` or `-webkit-right` (an `align` attribute, `<center>`) places it as auto margins would
                    val pushed = styles.last().pushesBlocks.takeIf { !leftAuto && !rightAuto }
                    val (freeLeft, freeRight) = when {
                        (leftAuto && rightAuto) || pushed == TextAlign.CENTER -> free / 2 to free / 2
                        leftAuto || pushed == TextAlign.END -> free to 0f
                        else -> 0f to free
                    }
                    fun share(length: CssLength?) = (length as? CssLength.Percent)?.let { it.percent / 100f * parentShare } ?: 0f
                    val marginRight = if (hasMargins) declared.marginRight.sideSpacing(inherited.scale) else Spacing.ZERO
                    val outerShare = inherited.insetShare + freeLeft + (if (hasMargins) share(declared.marginLeft) else 0f)
                    val outerEndShare = inherited.insetEndShare + freeRight + (if (hasMargins) share(declared.marginRight) else 0f)
                    val borders = listOf(declared.borderTop, declared.borderRight, declared.borderBottom, declared.borderLeft).map { it.drawnPx }
                    // an element with a border or a background is a box, drawn behind what it holds
                    val boxed = tag != "hr" && tag != "body" && tag != "html" &&
                        (borders.any { it > 0f } || (declared.background != null && declared.background != TRANSPARENT))
                    if (boxed) openBox(
                        BlockBox(
                            id = ++borderedElements,
                            left = ruleAt, leftShare = outerShare,
                            right = inherited.insetEnd + marginRight, rightShare = outerEndShare,
                            border = borders,
                            borderColor = listOf(declared.borderTop, declared.borderRight, declared.borderBottom, declared.borderLeft)
                                .map { it.color?.takeIf { c -> c != TEXT_COLOR } },
                            background = declared.background?.takeIf { it != TRANSPARENT },
                            radiusPx = declared.radius.spacing(inherited.scale).let { it.em * BROWSER_EM_PX + it.px },
                            cell = tag == "td" || tag == "th",
                        ),
                    )
                    inherited.copy(
                        inset = ruleAt + Spacing(px = borders[3]) + declared.paddingLeft.sideSpacing(inherited.scale),
                        insetEnd = inherited.insetEnd + Spacing(px = borders[1]) + declared.paddingRight.sideSpacing(inherited.scale) + marginRight,
                        insetShare = outerShare + share(declared.paddingLeft),
                        insetEndShare = outerEndShare + share(declared.paddingRight),
                    )
                }.let { inCell(tag, it, declared) }
                    // `lang` and `xml:lang` alike
                    .let { s -> reader.attr("lang")?.trim()?.takeIf { it.isNotEmpty() }?.let { s.copy(lang = it) } ?: s }
                // vertical margins of blocks, in the block's own em; an inline element's don't move lines
                if (isBlock) {
                    if (declared.breakBefore == true) pendingBreak = true
                    if (declared.avoidBefore == true) pendingAvoid = true
                    declared.marginTop?.let { pendingMargin = collapsed(pendingMargin, it.verticalSpacing(style.scale)) }
                    val box = openedBoxes.lastOrNull()?.takeIf { it.depth == -1 }
                    if (box != null) {
                        // the box's top edge: margins above it end here, its border and padding come inside
                        startKeeping(Spacing(px = box.box.border[0]))
                        pendingOuter += pendingMargin.net() + pendingPadding
                        pendingMargin = null
                        pendingPadding = Spacing(px = box.box.border[0])
                        box.outerAt = pendingOuter
                        box.depth = styles.size + 1
                    }
                    declared.paddingTop.verticalSpacing(style.scale).let { padding ->
                        startKeeping(padding)
                        // a padding parts the margins above it from those inside: they no longer collapse
                        if (padding != Spacing.ZERO) {
                            pendingOuter += pendingMargin.net()
                            pendingMargin = null
                        }
                        pendingPadding += padding
                    }
                }
                styles.addLast(style)
                if (tag == "body") onBody(style.scale, style.family)
                if ((tag == "body" || tag == "html") && declared.vertical == true) onVertical()
                if (isBlock && declared.avoidInside == true) avoidInside.addLast(styles.size to blocks.size)
                val fixedHeight = declared.height?.takeIf { it is CssLength.Em || it is CssLength.Px || it is CssLength.Vh || it is CssLength.WindowVh }
                if (isBlock && fixedHeight != null && tag !in heightOwnTags) {
                    heldHeights.addLast(HeldHeight(styles.size, blocks.size, fixedHeight.verticalSpacing(style.scale)))
                }
                // an inline element's own space and, for an inline-block, its minimum width
                if (!isBlock && tag != "img" && tag != "br" && !inHead && svgDepth == 0) {
                    val spaced = declared.marginLeft.isLength() || declared.paddingLeft.isLength()
                    if (spaced && spans == null) openBlock(BlockKind.PARAGRAPH, 0)
                    appendSpace(declared.marginLeft, style.scale)
                    appendSpace(declared.paddingLeft, style.scale)
                    val minEm = declared.minWidth.takeIf { declared.display == "inline-block" }.spacing(style.scale)
                        .let { (it.em + it.px / 16f) / style.scale }
                    if (minEm > 0f) {
                        inlineBoxes.addLast(++inlineBoxCount)
                        openBoxes.addLast(OpenBox(inlineBoxCount, minEm, styles.size))
                    }
                }
                // a floated element before any text of its paragraph: a drop cap
                if (declared.floatLeft == true && capDepth == 0 && spans?.all { it.text.isBlank() } != false && !isBlock) {
                    if (spans == null && !inHead) openBlock(BlockKind.PARAGRAPH, 0)
                    capDepth = styles.size
                    capLineRatio = declared.lineRatio ?: DROP_CAP_LINE_RATIO
                }
                if (declared.floatRight == true && spans != null && floatDepth == 0) {
                    val blocksBefore = blocks.size
                    closeBlock()
                    // it may share a row only with text of its own paragraph
                    val afterText = blocks.size > blocksBefore
                    floatDepth = styles.size
                    val (k, level) = kind to headingLevel
                    openBlock(k, level)
                    blockAlign = TextAlign.END
                    blockIndent = Spacing.ZERO
                    blockIndentShare = 0f
                    blockFloatsRight = afterText
                    // the parts of a split paragraph sit together
                    pendingMargin = Margins.ZERO
                }
                when (tag) {
                    "a" -> {
                        if (linkDepth == 0) linkHref = reader.attr("href")?.let { asLink(it, resolveSrc) }
                        linkDepth++
                        // a list item that starts with a link is a contents entry: LibreraX ranges it to the start
                        if (openLi == styles.size - 1 && reader.attr("href") != null && spans?.all { it.text.isBlank() } != false) blockRagged = true
                    }
                    "nav" -> navDepth++
                    "blockquote" -> quoteDepth++
                    "img" -> {
                        val src = reader.attr("src")?.let(resolveSrc) ?: ""
                        val height = (declared.height ?: reader.attr("height")?.let(::attrLength)).letterEm()
                        // as LibreraX draws it: an image the book sizes like a letter stays in its line
                        if (spans != null && height != null && height > 0f && height <= INLINE_IMAGE_MAX_EM) {
                            appendText(INLINE_IMAGE.toString(), InlineImage(src, height))
                        } else {
                            // a picture breaks the paragraph around it; the text after it carries on unindented
                            val inText = spans != null
                            val (k, level) = kind to headingLevel
                            closeBlock()
                            val within = styles[styles.size - 2]
                            addBlock(
                                Block.Image(
                                    reader.attr("alt") ?: "", src, takeGap(), imageBox(declared, reader, within), style.align, takeBreak(),
                                    inset = within.inset, insetEnd = within.insetEnd, insetShare = within.insetShare, insetEndShare = within.insetEndShare,
                                    strut = if (isBlock) null else FormattedSpan(
                                        "", false, false, scale = within.scale, face = within.face.takeIf { it != BookFace.SERIF }, family = within.family,
                                    ),
                                    strutRatio = within.lineRatio,
                                ),
                            )
                            if (inText) {
                                openBlock(k, level)
                                blockIndent = Spacing.ZERO
                                blockIndentShare = 0f
                                pendingMargin = Margins.ZERO
                            }
                        }
                    }
                    "br" -> {
                        // a <br> straight inside a <div> or <section> is a line of its own, as text there is
                        if (spans == null && !inHead && svgDepth == 0) openBlock(BlockKind.PARAGRAPH, 0)
                        appendText(BR_MARK.toString())
                    }
                    // a line may break here, as at a space that takes no room
                    "wbr" -> appendText(WORD_BREAK.toString())
                    // a browser's quotes round a <q>: double, single inside another
                    "q" -> appendText(if (quoteMarks++ % 2 == 0) "\u201C" else "\u2018")
                    "ul", "ol" -> listCounters.addLast(reader.attr("start")?.toIntOrNull() ?: 1)
                    "table" -> tables.addLast(
                        OpenTable(
                            ++tableCount,
                            reader.attr("cellpadding")?.toFloatOrNull(),
                            if (style.collapse) Spacing.ZERO to Spacing.ZERO else style.borderSpacing,
                            reader.attr("border")?.let(::attrLength).let { (it as? CssLength.Px)?.px } ?: 0f,
                        ),
                    )
                    "col" -> tables.lastOrNull()?.let { table ->
                        val width = (declared.width ?: reader.attr("width")?.let(::attrLength)) as? CssLength.Percent
                        repeat(reader.attr("span")?.toIntOrNull()?.coerceIn(1, 100) ?: 1) { table.widths.add(width?.let { it.percent / 100f }) }
                    }
                    "tr" -> tables.lastOrNull()?.let {
                        it.row = ++rowCount
                        it.column = 0
                        // the table's spacing down: above each row, and below the last (where the table ends)
                        pendingPadding += it.spacing.second
                    }
                    "li" -> {
                        val n = reader.attr("value")?.toIntOrNull() ?: listCounters.lastOrNull() ?: 1
                        if (listCounters.isNotEmpty()) listCounters[listCounters.lastIndex] = n + 1
                        pendingMarker = listMarker(style.listStyle, n)
                        openBlock(BlockKind.PARAGRAPH, 0)
                        // LibreraX ranges contents entries to the start: every list item in a nav
                        if (navDepth > 0) blockRagged = true
                        openLi = styles.size
                    }
                    "p", "pre" -> if (isBlock) openBlock(BlockKind.PARAGRAPH, 0)
                    "svg" -> svgDepth++
                    // a picture wrapped in SVG, as cover pages have it: the SVG's box, the picture in it
                    "image" -> if (svgDepth > 0) {
                        reader.attr("href")?.let { href ->
                            closeBlock()
                            addBlock(
                                Block.Image(
                                    "", resolveSrc(href), takeGap(), svgBox, style.align, takeBreak(),
                                    inset = style.inset, insetEnd = style.insetEnd, insetShare = style.insetShare, insetEndShare = style.insetEndShare,
                                ),
                            )
                        }
                    }
                    "hr" -> addBlock(
                        Block.Rule(
                            takeGap(),
                            declared.backgroundImage?.let(resolveSrc),
                            declared.height?.takeIf { it is CssLength.Em || it is CssLength.Px || it is CssLength.Vh || it is CssLength.WindowVh }
                                ?.verticalSpacing(style.scale),
                            // a browser draws a rule as its border: none when the book takes it away
                            line = !(declared.borderTop.style == "none" && declared.borderBottom.style == "none"),
                            widthShare = (declared.width as? CssLength.Percent)?.let { it.percent / 100f } ?: 1f,
                            breakBefore = takeBreak(),
                        ),
                    )
                    // cells sit side by side in their row, a space apart
                    in headingTags -> if (isBlock) openBlock(BlockKind.HEADING, tag[1].digitToInt())
                }
            }
            // whitespace-only text (e.g. <i> </i> between words) arrives as
            // IGNORABLE_WHITESPACE; it is real text in XHTML
            EventType.TEXT, EventType.CDSECT, EventType.ENTITY_REF, EventType.IGNORABLE_WHITESPACE ->
                styleText?.append(reader.text) ?: run {
                    // text straight inside a <div>, <td>, <dd>… is a block of its own, as in a browser
                    if (svgDepth > 0) return@run
                    if (spans == null && !inHead && options.replaced(reader.text).any { it !in HTML_SPACE }) openBlock(BlockKind.PARAGRAPH, 0)
                    appendText(options.replaced(reader.text))
                }
            EventType.END_ELEMENT -> {
                val tag = reader.localName.lowercase()
                if (capDepth == styles.size) {
                    capDepth = 0
                    // the cap is the text so far, whitespace before it aside
                    val capped = spans?.joinToString("") { it.text }?.trim()?.length ?: 0
                    if (capped > 0) blockDropCap = DropCap(capped, capLineRatio)
                }
                if (floatDepth == styles.size) {
                    // the floated part ends: what follows continues the paragraph, unindented
                    floatDepth = 0
                    val (k, level) = kind to headingLevel
                    val (align, scale) = styles[styles.size - 2].align to blockScale
                    closeBlock()
                    openBlock(k, level)
                    blockAlign = align
                    blockScale = scale
                    blockIndent = Spacing.ZERO
                    blockIndentShare = 0f
                    pendingMargin = Margins.ZERO
                }
                when (tag) {
                    "q" -> appendText(if (--quoteMarks % 2 == 0) "\u201D" else "\u2019")
                    "style" -> {
                        styleText?.takeIf { styleMedia.let { it == null || mediaMatches(it, darkScheme) } }?.let { addSheet(it.toString(), "", linked = false) }
                        styleText = null
                    }
                    "a" -> {
                        linkDepth--
                        if (linkDepth == 0) linkHref = null
                    }
                    "blockquote" -> quoteDepth--
                    "nav" -> navDepth--
                    "head" -> inHead = false
                    "ul", "ol" -> listCounters.removeLastOrNull()
                    "table" -> tables.removeLastOrNull()?.let { pendingPadding += it.spacing.second }
                    "svg" -> svgDepth--
                }
                if (openIsBlock.lastOrNull() == false && tag != "img" && tag != "br" && !inHead) {
                    openBoxes.lastOrNull()?.takeIf { it.depth == styles.size }?.let { box ->
                        openBoxes.removeLast()
                        inlineBoxes.removeLastOrNull()
                        appendText(INLINE_IMAGE.toString(), spacer = InlineSpacer(box.minEm, padsBox = box.id))
                    }
                    declaredStyles.lastOrNull()?.let { declared ->
                        appendSpace(declared.paddingRight, styles.last().scale)
                        appendSpace(declared.marginRight, styles.last().scale)
                    }
                }
                val wasBlock = openIsBlock.removeLastOrNull() == true
                if (wasBlock) closeBlock()
                if (avoidInside.lastOrNull()?.first == styles.size) avoidInside.removeLast()
                heldHeights.lastOrNull()?.takeIf { it.depth == styles.size }?.let { held ->
                    heldHeights.removeLast()
                    val inside = blocks.subList(held.from, blocks.size)
                    // a blank line the book holds at its height (`<div style="height: 1em"><p>&nbsp;</p></div>`):
                    // a band that tall, the margins of what's inside collapsing out of its top only
                    if (inside.all { it is Block.Text && it.text.isBlank() }) {
                        val first = inside.firstOrNull()
                        val gap = if (first != null) first.gapBefore else takeGap()
                        if (first != null) takenCut = blockCuts[held.from]
                        blockCuts.subList(held.from, blockCuts.size).clear()
                        val breaks = if (first != null) first.breakBefore else takeBreak()
                        inside.clear()
                        blockBoxes.subList(held.from, blockBoxes.size).clear()
                        blockKeeps.subList(held.from, blockKeeps.size).clear()
                        pendingMargin = null
                        pendingPadding = Spacing.ZERO
                        addBlock(Block.Rule(gap, height = held.height, line = false, breakBefore = breaks))
                    }
                }
                val closing = styles.last()
                if (styles.size > 1) styles.removeLast()
                path.removeLastOrNull()
                if (lastChild.size > 1) lastChild.removeLast()
                declaredStyles.removeLastOrNull()?.let { declared ->
                    if (wasBlock) {
                        if (declared.breakAfter == true) pendingBreak = true
                        if (declared.avoidAfter == true) pendingAvoid = true
                        declared.paddingBottom.verticalSpacing(closing.scale).takeIf { it != Spacing.ZERO }?.let { padding ->
                            pendingOuter += pendingMargin.net()
                            pendingMargin = null
                            pendingPadding += padding
                        }
                        openedBoxes.lastOrNull()?.takeIf { it.depth == styles.size + 1 }?.let { box ->
                            // the box's bottom edge: all that's pending inside it since its last block (boxes
                            // closed in it, margins a padding parted), and its bottom border
                            openedBoxes.removeLast()
                            pendingPadding += Spacing(px = box.box.border[2])
                            box.bottomInset = pendingOuter + pendingMargin.net() + pendingPadding
                            pendingOuter = box.bottomInset
                            pendingMargin = null
                            pendingPadding = Spacing.ZERO
                        }
                        declared.marginBottom?.let { pendingMargin = collapsed(pendingMargin, it.verticalSpacing(closing.scale)) }
                    }
                }
            }
            else -> {}
        }
    }
    anchorBlocks?.putAll(idBlocks)
    anchors?.putAll(idsAt.mapValues { (_, at) -> if (bodyText > 0) at.toFloat() / bodyText else 0f })
    // each block with its boxes as they ended up, their insets known now they're closed
    return blocks.mapIndexed { i, block ->
        val boxes = blockBoxes[i].mapNotNull { id ->
            finishedBoxes[id]?.let { it.box.copy(topInset = it.topInset ?: Spacing.ZERO, bottomInset = it.bottomInset) }
        }
        val cut = blockCuts[i]
        val (withPrevious, lines) = blockKeeps[i]
        when (block) {
            is Block.Text -> block.copy(boxes = boxes, marginBefore = cut, keepWithPrevious = withPrevious, keepLines = lines)
            is Block.Image -> block.copy(boxes = boxes, marginBefore = cut, keepWithPrevious = withPrevious, keepLines = lines)
            is Block.Rule -> block.copy(boxes = boxes, marginBefore = cut, keepWithPrevious = withPrevious, keepLines = lines)
        }
    }
}

/** An element's inherited style: tag defaults, then the book's CSS for it. */
private data class Computed(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val smallCaps: Boolean = false,
    /** right-to-left text: `dir`, `direction`, else the book's language; inherited */
    val rtl: Boolean = false,
    val align: TextAlign = TextAlign.START,
    /** `text-indent`; inherited, as in CSS */
    val indent: Spacing = Spacing.ZERO,
    /** [Block.Text.indentShare]; inherited */
    val indentShare: Float = 0f,
    /** font size relative to body text */
    val scale: Float = 1f,
    /** the book's colour; null: the theme's ink */
    val color: Long? = null,
    val underline: Boolean = true,
    /** `text-decoration: underline` on running text; [underline] is about links */
    val underlined: Boolean = false,
    /** `text-decoration: line-through`, which reaches the text inside */
    val struck: Boolean = false,
    val face: BookFace = BookFace.SERIF,
    /** [CssStyle.genericMono]; inherited */
    val genericMono: Boolean = false,
    /** its size comes from a length (px, pt, rem) here or above, not a keyword: plain monospace keeps it as it is */
    val sizeAbsolute: Boolean = false,
    /** the first family the book names, lowercased; inherited */
    val family: String? = null,
    val inset: Spacing = Spacing.ZERO,
    val insetEnd: Spacing = Spacing.ZERO,
    val insetShare: Float = 0f,
    val insetEndShare: Float = 0f,
    val listStyle: String? = null,
    val cell: TableCell? = null,
    /** the book's line height ratio; null where the reader's holds */
    val lineRatio: Float? = null,
    /** a `line-height` in CSS px, which stays px whatever the font; inherited */
    val linePx: Float? = null,
    /** [FormattedSpan.background]: an inline element's, behind the text in it */
    val inlineBackground: Long? = null,
    /** [FormattedSpan.inAnchor]; inherited */
    val inAnchor: Boolean = false,
    /** inside a heading, where the reader's leading isn't set */
    val inHeading: Boolean = false,
    val hyphenate: Boolean = true,
    val invisible: Boolean = false,
    val wordSpacing: Float = 0f,
    val keepsSpaces: Boolean = false,
    val baseline: Float = 0f,
    val baselineLine: Float = 0f,
    /** [FormattedSpan.baselinePx]; rides on as [baseline] does */
    val baselinePx: Float = 0f,
    val transform: String? = null,
    val letterSpacing: Float = 0f,
    /** the content's share of the column's width: a block's `width: 89%` narrows what's inside */
    val widthShare: Float = 1f,
    /** the columns of the `column-count` element the text is in */
    val columns: Columns? = null,
    val borderSpacing: Pair<Spacing, Spacing> = Spacing.ZERO to Spacing.ZERO,
    val collapse: Boolean = false,
    /** the nearest `lang`; inherited */
    val lang: String? = null,
    /** [CssStyle.pushesBlocks]: where narrower blocks inside go */
    /** set left where the reader's justification doesn't reach ([JUSTIFIED_TAGS]): drawn ragged */
    val ragged: Boolean = false,
    val pushesBlocks: TextAlign? = null,
    /** a table cell's place in its row ([TableCell.spareAbove]): row groups centre theirs, rows and cells take it on */
    val rowAlign: Float = 0f,
) {
    fun inherit(tag: String, declared: CssStyle): Computed {
        val ownFace = declared.face ?: face
        val ownMono = declared.genericMono ?: genericMono
        // a keyword's size, or a length's; else the parent's, scaled, and by the quirk where it came from a keyword
        val ownScale = (
            declared.fontKeyword?.let { (if (ownMono) MONOSPACE_KEYWORD_PX else KEYWORD_PX)[it] / BROWSER_EM_PX }
                ?: declared.fontAbsolute
                ?: (scale * (declared.fontScale ?: 1f) * if (sizeAbsolute) 1f else monospaceQuirk(genericMono, ownMono))
            )
            .coerceIn(MIN_SCALE, MAX_SCALE)
        return Computed(
            bold = declared.bold ?: (bold || tag in boldTags || tag in headingTags),
            italic = declared.italic ?: (italic || tag in italicTags),
            smallCaps = declared.smallCaps ?: smallCaps,
            rtl = declared.rtl ?: rtl,
            // `left` and `right` are physical: in right-to-left text `right` is where it begins
            align = declared.alignSide?.let { side ->
                val ownRtl = declared.rtl ?: rtl
                when (side) {
                    "center" -> TextAlign.CENTER
                    "left" -> if (ownRtl) TextAlign.END else TextAlign.START
                    "right" -> if (ownRtl) TextAlign.START else TextAlign.END
                    "end" -> TextAlign.END
                    else -> TextAlign.START
                }
            } ?: declared.align ?: align,
            // the reader's justification is set on running text only: elsewhere a book's `left` stays ragged
            ragged = when {
                tag in JUSTIFIED_TAGS -> false
                declared.align != null -> if (declared.rtl ?: rtl) declared.alignSide == "left" || declared.alignSide == "center" || declared.alignSide == "end" else declared.alignLeft == true
                else -> ragged
            },
            indent = declared.indent?.spacing(ownScale) ?: indent,
            // a percentage is inherited as one, and comes to px in each block's own width
            indentShare = declared.indent?.let { (it as? CssLength.Percent)?.percent?.div(100f) ?: 0f } ?: indentShare,
            scale = ownScale,
            // inherit takes the parent's colour, and where the parent has none, the text's own; LibreraX
            // sets the theme's text on html and body over the book's
            color = if (tag == "html" || tag == "body") null else declared.color?.let { if (it == TEXT_COLOR) color ?: TEXT_COLOR else it } ?: color,
            underline = declared.underline ?: underline,
            underlined = declared.underline ?: underlined,
            struck = declared.lineThrough ?: struck,
            face = ownFace,
            genericMono = ownMono,
            sizeAbsolute = declared.fontAbsolute != null || (declared.fontKeyword == null && sizeAbsolute),
            family = declared.family ?: family,
            inset = inset,
            insetEnd = insetEnd,
            insetShare = insetShare,
            insetEndShare = insetEndShare,
            listStyle = declared.listStyle ?: listStyle,
            cell = cell,
            // the reader's leading is set over the book's on running-text blocks, as LibreraX's css() sets it
            // (not inside a heading, which keeps the book's leading); a px line height stays px
            lineRatio = when {
                tag in leadTags && !inHeading -> null
                declared.lineRatio != null -> declared.lineRatio
                declared.linePx != null -> null
                else -> lineRatio
            },
            linePx = when {
                tag in leadTags && !inHeading -> null
                declared.lineRatio != null -> null
                else -> declared.linePx ?: linePx
            },
            inHeading = inHeading || tag in headingTags,
            inAnchor = inAnchor || tag == "a",
            // an inline element's background is behind its text; a block's is its box's
            inlineBackground = if (isBlock(tag, declared.display)) null else declared.background?.takeIf { it != TRANSPARENT } ?: inlineBackground,
            // and its hyphenation on body, p, li and div
            hyphenate = tag in hyphenTags || (declared.hyphenate ?: hyphenate),
            invisible = declared.invisible ?: invisible,
            wordSpacing = declared.wordSpacing ?: wordSpacing,
            keepsSpaces = declared.keepsSpaces ?: keepsSpaces,
            // vertical-align isn't inherited, but the text inside a raised element rides on it; `super` and
            // `sub` move by a third and a fifth of the parent's size and a device px (probed)
            baseline = when {
                declared.shiftKeyword == 1 -> scale / 3f / ownScale
                declared.shiftKeyword == -1 -> -scale / 5f / ownScale
                declared.baselineLine != null -> 0f
                else -> declared.baseline ?: baseline
            },
            baselinePx = when {
                declared.shiftKeyword != null -> declared.shiftKeyword.toFloat()
                declared.baseline != null || declared.baselineLine != null -> 0f
                else -> baselinePx
            },
            baselineLine = if (declared.baseline != null) 0f else declared.baselineLine ?: baselineLine,
            transform = declared.transform ?: transform,
            letterSpacing = declared.letterSpacing ?: letterSpacing,
            widthShare = (declared.width as? CssLength.Percent)?.let { widthShare * it.percent / 100f } ?: widthShare,
            // a browser's `column-gap: normal` is 1em
            columns = when {
                declared.columnCount == null -> columns
                declared.columnCount > 1 -> Columns(declared.columnCount, (declared.columnGap ?: CssLength.Em(1f)).spacing(ownScale))
                else -> null
            },
            borderSpacing = declared.borderSpacing?.let { (across, down) -> across.spacing(ownScale) to down.spacing(ownScale) } ?: borderSpacing,
            collapse = declared.borderCollapse ?: collapse,
            pushesBlocks = if (declared.align != null) declared.pushesBlocks else pushesBlocks,
            lang = lang,
            // a browser's `tbody, thead, tfoot { vertical-align: middle }` and `tr, td, th { vertical-align: inherit }`;
            // XHTML has no row group the book doesn't write, and a table's own is the baseline (the top)
            rowAlign = declared.rowAlign?.let(::spareAboveOf) ?: when (tag) {
                "tbody", "thead", "tfoot" -> 0.5f
                "tr", "td", "th" -> rowAlign
                else -> 0f
            },
        )
    }
}

/**
 * A browser sets plain `monospace` (the generic family alone) at 13px where other text
 * is 16px: text whose size isn't set going into or out of it is scaled by that. A named
 * monospace font, or a size the element sets, isn't. LibreraX inherits this, so a book's
 * transcripts come out smaller and tighter.
 */
private fun monospaceQuirk(parent: Boolean, own: Boolean): Float = when {
    own && !parent -> MONOSPACE_RATIO
    !own && parent -> 1f / MONOSPACE_RATIO
    else -> 1f
}

private const val MONOSPACE_RATIO = 13f / 16f

/** A length as spacing: em of body text (an element's em is its own font size, [scale]) or CSS px. Percentages aren't lengths here. */
private fun CssLength?.spacing(scale: Float): Spacing = when (this) {
    is CssLength.Em -> ownSpacing(Spacing(em = em * scale))
    is CssLength.Px -> ownSpacing(Spacing(px = px))
    else -> Spacing.ZERO
}

/** A cell's `vertical-align` keyword as [TableCell.spareAbove]. */
private fun spareAboveOf(keyword: String): Float = when (keyword) {
    "middle" -> 0.5f
    "bottom", "text-bottom" -> 1f
    else -> 0f
}

/**
 * A top or bottom margin or padding: a length as [spacing], and the ones only a page's layout
 * can measure: `vh` of the page (a linked stylesheet's of the window), a percentage of the column's width.
 */
private fun CssLength?.verticalSpacing(scale: Float): Spacing = when (this) {
    is CssLength.Vh -> Spacing(pageH = percent / 100f)
    is CssLength.WindowVh -> Spacing(windowH = percent / 100f)
    is CssLength.Percent -> Spacing(width = percent / 100f)
    else -> spacing(scale)
}

/** A left or right margin or padding: `vh` of the page, a linked sheet's `vh` and `vw` of the window; an in-document `vw` is nothing (probed). */
private fun CssLength?.sideSpacing(scale: Float): Spacing = when (this) {
    is CssLength.Vh -> Spacing(pageH = percent / 100f)
    is CssLength.WindowVh -> Spacing(windowH = percent / 100f)
    is CssLength.WindowVw -> Spacing(windowW = percent / 100f)
    else -> spacing(scale)
}

/** One element's own spacing, a part of whatever it's added to. */
private fun ownSpacing(value: Spacing) = Spacing(value.em, value.px, value.partsOrSelf())

/** Whether a length is one Baca can space by: em or px, above zero. */
private fun CssLength?.isLength(): Boolean = (this is CssLength.Em && em > 0f) || (this is CssLength.Px && px > 0f)

/** Adjoining vertical margins collapsed: the largest positive one and the most negative, which add up. */
private class Margins(val positive: Spacing, val negative: Spacing) {
    val net get() = positive + negative

    companion object {
        val ZERO = Margins(Spacing.ZERO, Spacing.ZERO)
    }
}

private fun Margins?.net(): Spacing = this?.net ?: Spacing.ZERO

// a phone's page height and column width in CSS px, LibreraX's 754 and 351
private const val NOMINAL_PAGE_PX = 754f
private const val NOMINAL_COLUMN_PX = 351f

/** [margin] collapsed into [current], compared at a browser's 16px em. */
private fun collapsed(current: Margins?, margin: Spacing): Margins {
    // a share of the page or column taken at a phone's sizes, to tell the larger
    fun px(s: Spacing) = s.em * 16f + s.px + (s.pageH + s.windowH) * NOMINAL_PAGE_PX + s.width * NOMINAL_COLUMN_PX
    val now = current ?: Margins.ZERO
    return if (px(margin) >= 0f) Margins(if (px(margin) > px(now.positive)) margin else now.positive, now.negative)
    else Margins(now.positive, if (px(margin) < px(now.negative)) margin else now.negative)
}

/**
 * A `<pre>` block's text as written: the newline straight after `<pre>` and the
 * last one before `</pre>` aside, as a browser drops them, and tabs to the next
 * 8-column stop.
 */
private fun keptWhitespace(spans: List<FormattedSpan>): List<FormattedSpan> {
    var column = 0
    val texts = spans.map { span ->
        buildString {
            for (c in span.text.replace("\r\n", "\n")) when (c) {
                BR_MARK, '\n' -> {
                    append('\n')
                    column = 0
                }
                '\t' -> do {
                    append(' ')
                    column++
                } while (column % 8 != 0)
                else -> {
                    append(c)
                    column++
                }
            }
        }
    }.toMutableList()
    texts.indexOfFirst { it.isNotEmpty() }.takeIf { it >= 0 && texts[it].startsWith('\n') }?.let { texts[it] = texts[it].substring(1) }
    texts.indexOfLast { it.isNotEmpty() }.takeIf { it >= 0 && texts[it].endsWith('\n') }?.let { texts[it] = texts[it].dropLast(1) }
    return spans.mapIndexedNotNull { i, span -> texts[i].takeIf { it.isNotEmpty() }?.let { span.copy(text = it) } }
}

/**
 * [text] as `text-transform` shows it, a letter for a letter, so offsets into the
 * block's text still hold (TECH 5.2). [before] is the text's previous char, for
 * `capitalize`'s word starts.
 */
private fun transformed(text: String, transform: String?, before: Char?): String = when (transform) {
    "uppercase" -> text.map { it.uppercaseChar() }.joinToString("")
    "lowercase" -> text.map { it.lowercaseChar() }.joinToString("")
    "capitalize" -> text.mapIndexed { i, c ->
        val previous = if (i == 0) before else text[i - 1]
        if (previous == null || !previous.isLetterOrDigit() && previous != '\'' && previous != '’') c.uppercaseChar() else c
    }.joinToString("")
    else -> text
}

/** A list item's marker for its `list-style-type`; null for `none`. */
private fun listMarker(type: String?, n: Int): String? = when (type ?: "disc") {
    "none" -> null
    "circle" -> "◦"
    "square" -> "▪"
    "decimal" -> "$n."
    "lower-alpha", "lower-latin" -> "${alphabetic(n)}."
    "upper-alpha", "upper-latin" -> "${alphabetic(n).uppercase()}."
    "lower-roman" -> "${roman(n).lowercase()}."
    "upper-roman" -> "${roman(n)}."
    else -> "•"
}

private fun alphabetic(n: Int): String {
    var k = n.coerceAtLeast(1)
    val out = StringBuilder()
    while (k > 0) {
        k--
        out.insert(0, 'a' + k % 26)
        k /= 26
    }
    return out.toString()
}

private fun roman(n: Int): String {
    var k = n.coerceIn(1, 3999)
    val out = StringBuilder()
    for ((value, numeral) in ROMAN) while (k >= value) {
        out.append(numeral)
        k -= value
    }
    return out.toString()
}

private val ROMAN = listOf(
    1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC",
    50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I",
)

/** Its height in em when the book sizes it like text (em or px); else null. */
private fun CssLength?.letterEm(): Float? = when (this) {
    is CssLength.Em -> em
    is CssLength.Px -> px / 16f
    else -> null
}

/**
 * The picture's box: book CSS first, then its `width`/`height` attributes, as a
 * browser takes them. A percent width is of the containing block, so it's
 * turned into a share of the column here.
 */
private fun imageBox(declared: CssStyle, reader: XmlReader, parent: Computed): ImageBox {
    fun ofColumn(length: CssLength?) =
        if (length is CssLength.Percent) CssLength.Percent(length.percent * parent.widthShare) else length
    return ImageBox(
        width = ofColumn(declared.width ?: reader.attr("width")?.let(::attrLength)),
        height = declared.height ?: reader.attr("height")?.let(::attrLength),
        maxWidth = ofColumn(declared.maxWidth),
    )
}

// a browser's `line-height: normal`, for a floated letter that sets none
private const val DROP_CAP_LINE_RATIO = 1.2f

// taller than this, an inline image would push its line past the line height every page is measured with
private const val INLINE_IMAGE_MAX_EM = 1f

// LibreraX keeps every size the book gives; these only stop a broken stylesheet (font-size: 0, 100em)
private const val MIN_SCALE = 0.25f
private const val MAX_SCALE = 5f

private fun asLink(raw: String, resolveSrc: (String) -> String): String? {
    val withoutFragment = raw.substringBefore('#')
    if (withoutFragment.isEmpty()) return null
    return if (raw.startsWith("http://") || raw.startsWith("https://") || raw.startsWith("mailto:")) {
        raw
    } else {
        resolveSrc(withoutFragment)
    }
}

private val headingTags = setOf("h1", "h2", "h3", "h4", "h5", "h6")

// a <font size>'s keywords, 1 to 7
private val FONT_SIZES = listOf("x-small", "small", "medium", "large", "x-large", "xx-large", "xxx-large")

// a list's `type` attribute as list-style-type: the letters and numerals as written, case and all
private val LIST_TYPES = mapOf(
    "1" to "decimal", "a" to "lower-alpha", "A" to "upper-alpha", "i" to "lower-roman", "I" to "upper-roman",
    "disc" to "disc", "circle" to "circle", "square" to "square", "none" to "none",
)

// a table's and its cells' borders from its `border` attribute
private const val BORDER_GRAY = 0xFF808080

// LibreraX's ALIGN_BLOCKS: what the reader's justification is set on
private val JUSTIFIED_TAGS = setOf("body", "p", "li", "div", "blockquote")

// whose `align` attribute aligns their text, and the blocks in them
private val ALIGNED_TAGS = headingTags + setOf("p", "div", "caption", "thead", "tbody", "tfoot", "tr", "td", "th")

/** Elements whose `height` sizes their own content (a picture, a rule, a table's parts), not a block of text. */
private val heightOwnTags = setOf("img", "hr", "table", "tr", "td", "th")

/** Elements LibreraX sets the reader's line height on, over the book's (its LEAD_BLOCKS). */
private val leadTags = setOf("body", "p", "li", "div", "blockquote", "dd", "dt", "td", "th", "figcaption")

/** Elements LibreraX hyphenates whatever the book says. */
private val hyphenTags = setOf("body", "p", "li", "div")

private const val BROWSER_EM_PX = 16f

/** `<wbr>`: a zero-width space, where a line may break. */
const val WORD_BREAK = '\u200B'

/** `visibility: hidden` text: measured, not seen. */
const val INVISIBLE = 0L

/** Whether an element lays out as a block: the book's `display`, else what a browser does with its tag. */
private fun isBlock(tag: String, display: String?): Boolean = when (display) {
    "inline", "inline-block", "inline-flex", "inline-table", "contents" -> false
    "block", "list-item", "flex", "grid", "table", "table-row", "flow-root", "table-caption" -> true
    else -> tag in blockTags
}

/** Elements a browser lays out as blocks: text before, inside and after each is a block of its own. */
private val blockTags = headingTags + setOf(
    "p", "li", "div", "section", "article", "aside", "header", "footer", "nav", "main", "body",
    "blockquote", "figure", "figcaption", "address", "pre", "hr",
    "ul", "ol", "dl", "dt", "dd", "table", "caption", "thead", "tbody", "tfoot", "tr", "td", "th", "center",
)

// HTML's whitespace; a non-breaking space is text
private const val HTML_SPACE = " \t\n\r\u000C"
private val boldTags = setOf("b", "strong")
private val italicTags = setOf("i", "em")

// Leading whitespace before the XML declaration is ill-formed XML but shows up
// in real files; KtXmlReader refuses it, so strip it once, here.
internal fun newXmlReader(xml: String): XmlReader = KtXmlReader(StringReader(xml.trimStart()), true)

internal fun XmlReader.attrOrEmpty(name: String): String = attr(name) ?: ""

internal fun XmlReader.attr(name: String): String? {
    for (i in 0 until attributeCount) {
        if (getAttributeLocalName(i) == name) return getAttributeValue(i)
    }
    return null
}
