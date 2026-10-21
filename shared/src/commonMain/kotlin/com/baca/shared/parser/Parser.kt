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
    /** `letter-spacing` in em of its own font */
    val letterSpacing: Float = 0f,
    /** the book's `color` (0xAARRGGBB), [TEXT_COLOR] for the text's own; null: ink, or a link's blue */
    val color: Long? = null,
    /** an image drawn in the line, in place of this span's one [INLINE_IMAGE] char */
    val image: InlineImage? = null,
)

/** The generic faces a book's CSS names; drawn with the reader's own sans and monospace fonts. */
enum class BookFace { SERIF, SANS, MONO }

/** A CSS length as a book writes it; [Px] is CSS pixels, [Percent] of the column unless said otherwise. */
sealed class CssLength {
    data class Px(val px: Float) : CssLength()
    data class Em(val em: Float) : CssLength()
    data class Percent(val percent: Float) : CssLength()
    /** of the page's height, as foliate's iframe is a page tall */
    data class Vh(val percent: Float) : CssLength()
    data class Vw(val percent: Float) : CssLength()
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
 * A vertical rule left of a block, from a book CSS `border-left`, [at] in from
 * the column edge. Blocks of one bordered element share a [group], so the rule
 * runs on through the gaps between them.
 */
data class LeftRule(val group: Int, val at: Spacing)

/**
 * A length as a book sets it: [em] of the running text, which grows with the
 * reader's font, plus [px] in CSS px, which a browser keeps at their size
 * whatever the font.
 */
data class Spacing(val em: Float = 0f, val px: Float = 0f) {
    operator fun plus(other: Spacing) = Spacing(em + other.em, px + other.px)

    /** In device px, [cssPx] device px to a CSS px. */
    fun toPx(fontPx: Int, cssPx: Float): Int = (em * fontPx + px * cssPx).roundToInt()

    companion object {
        val ZERO = Spacing()
    }
}

/**
 * A drop cap: the block's first [end] chars, floated left (`float: left`) and set
 * [lines] lines tall, the lines beside it shortened by its width, as a browser lays
 * out a floated first letter.
 */
data class DropCap(val end: Int, val lineRatio: Float)

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
        val leftRule: LeftRule? = null,
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
    ) : Block() {
        override val text: String get() = ""
    }

    /** Space above the block from the book's CSS margins and padding, in em; null leaves the reader's gap. */
    abstract val gapBefore: Spacing?
}

/**
 * Offsets contract (docs/TECH.md 5.2/5.3): a block's text is its spans joined in
 * order, and every locator charOffset and annotation anchor charOffset is an
 * index into that exact string. Spans never overlap and never reorder.
 *
 * [title] comes from the book's TOC (EPUB3 nav or NCX), null when the TOC
 * has no entry for the spine document.
 */
data class Chapter(val href: String, val title: String?, val blocks: List<Block>)

data class TocEntry(val href: String, val title: String)

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
)

/**
 * The zip path of the parsed OPF document is the base for resolving chapter
 * hrefs; [Chapter.href] values are full zip paths usable as locator hrefs.
 */
fun parseEpub(path: Path): ParsedEpub {
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
    // one book usually shares a stylesheet across chapters: read each file once
    val cssFiles = mutableMapOf<Path, String?>()
    fun css(path: Path): String? = cssFiles.getOrPut(path) { runCatching { zip.read(path) { readUtf8() } }.getOrNull() }
    val chapters = opf.spine.mapNotNull { idref ->
        val href = opf.manifest[idref] ?: return@mapNotNull null
        val chapterPath = (base / href.toPath()).normalized()
        val xhtml = zip.read(chapterPath) { readUtf8() }
        // XHTML-relative hrefs resolve against the chapter document's own
        // directory, not the OPF directory (they coincide when spine docs sit
        // next to the OPF, which is why Frankenstein parsed 'correctly').
        val chapterDir = chapterPath.parent ?: ROOT
        Chapter(
            href = chapterPath.toString(),
            title = titles[chapterPath.toString()],
            blocks = parseXhtml(
                xhtml,
                resolveSrc = { src -> (chapterDir / src.toPath()).normalized().toString() },
                loadCss = { href -> css((chapterDir / href.substringBefore('#').toPath()).normalized()) },
            ),
        )
    }
    // Normalize the cover href like spine hrefs: it is relative to the OPF dir
    // in the manifest, and zip reads need the full path.
    val cover = opf.coverHref?.let {
        if (it.contains("://")) it else (base / it.toPath()).normalized().toString()
    }
    return ParsedEpub(opf.title, opf.author, cover, withRunningText(chapters), toc, opf.identifier, opf.language)
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
    while (reader.hasNext()) {
        val event = reader.next()
        when (event) {
            EventType.START_ELEMENT -> when (reader.localName) {
                "navLabel" -> {
                    inNavLabel = true
                    title.setLength(0)
                }
                "content" -> {
                    val src = reader.attr("src")?.substringBefore('#')
                    if (src != null && title.isNotBlank()) {
                        entries.add(TocEntry(resolve(src), title.toString().trim()))
                    }
                }
            }
            EventType.TEXT, EventType.CDSECT, EventType.ENTITY_REF -> if (inNavLabel) title.append(reader.text)
            EventType.END_ELEMENT -> if (reader.localName == "navLabel") inNavLabel = false
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
    while (reader.hasNext()) {
        val event = reader.next()
        when (event) {
            EventType.START_ELEMENT -> when (reader.localName) {
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
                "a" -> {
                    if (anchorHref != null && current != null) {
                        val src = anchorHref.substringBefore('#')
                        if (title.isNotBlank()) current.add(TocEntry(resolve(src), title.toString().trim()))
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
)

private fun parseOpf(opfXml: String): Opf {
    val reader = newXmlReader(opfXml)
    val manifest = mutableMapOf<String, String>()
    val spine = mutableListOf<String>()
    var title = ""
    var author = ""
    var language: String? = null
    var coverHref: String? = null
    var coverId: String? = null
    var navHref: String? = null
    var ncxHref: String? = null
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
                "itemref" -> reader.attr("idref")?.let { spine.add(it) }
                "title" -> if (inMetadata) capture = { title = it }
                "creator" -> if (inMetadata) capture = { author = it }
                "language" -> if (inMetadata && language == null) capture = { language = it.trim().ifEmpty { null } }
                "meta" -> if (reader.attrOrEmpty("name") == "cover") coverId = reader.attr("content")
            }
            EventType.TEXT, EventType.CDSECT -> capture?.invoke(reader.text)
            EventType.END_ELEMENT -> when (reader.localName) {
                "title" -> capture = null
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
    return Opf(title.trim(), author.trim(), coverHref ?: coverByMeta, navHref ?: ncxHref, manifest, spine, identifier, language)
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
    xhtml: String,
    resolveSrc: (String) -> String = { it },
    /** stylesheet text for a `<link>` href, relative to this document */
    loadCss: (String) -> String? = { null },
): List<Block> {
    val reader = newXmlReader(xhtml)
    val blocks = mutableListOf<Block>()
    val stylesheet = Stylesheet()
    var spans: MutableList<FormattedSpan>? = null
    var kind = BlockKind.PARAGRAPH
    var headingLevel = 0
    var blockAlign = TextAlign.START
    var blockIndent = Spacing.ZERO
    var blockScale: Float? = null
    var blockFloatsRight = false
    var blockKeepsSpaces = false
    var blockDropCap: DropCap? = null
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
    var pendingMargin: Spacing? = null
    var pendingPadding = Spacing.ZERO
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
    // per open element, whether it lays out as a block (its tag, or the book's `display`)
    val openIsBlock = ArrayDeque<Boolean>()
    // inside a <style> element: its text, collected for the stylesheet
    var styleText: StringBuilder? = null
    // float:right content inside a paragraph gets its own right-aligned line;
    // the depth of the floated element, 0 when not inside one
    var floatDepth = 0
    // computed (inherited) style of each open element, innermost last
    val styles = ArrayDeque<Computed>().apply { add(Computed()) }
    // the open elements as CSS selectors see them, outermost first, and each one's declared style
    val path = ArrayDeque<CssElement>()
    val declaredStyles = ArrayDeque<CssStyle>()
    // per open element, its last child element so far: the next child's previous sibling
    val lastChild = ArrayDeque<CssElement?>().apply { add(null) }

    fun takeGap(): Spacing? {
        val gap = if (pendingMargin == null && pendingPadding == Spacing.ZERO) null else (pendingMargin ?: Spacing.ZERO) + pendingPadding
        pendingMargin = null
        pendingPadding = Spacing.ZERO
        return gap
    }

    fun openBlock(k: BlockKind, level: Int) {
        val style = styles.last()
        kind = if (quoteDepth > 0 && k == BlockKind.PARAGRAPH) BlockKind.QUOTE else k
        headingLevel = level
        blockAlign = style.align
        blockIndent = style.indent
        blockScale = style.scale.takeIf { it != 1f }
        blockKeepsSpaces = style.keepsSpaces
        blockDropCap = null
        blockFloatsRight = false
        blockFontScale = style.scale
        spans = mutableListOf()
    }

    fun closeBlock() {
        val list = spans ?: return
        spans = null
        val merged = mutableListOf<FormattedSpan>()
        for (s in list) {
            val last = merged.lastOrNull()
            if (last != null && last.image == null && s.image == null && last.copy(text = "") == s.copy(text = "")) {
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
            blocks.add(
                Block.Text(
                    collapsed, kind, headingLevel, blockAlign, blockIndent, blockScale, takeGap(),
                    style.inset, style.leftRule, blockFloatsRight, style.insetEnd, pendingMarker, blockKeepsSpaces,
                    style.insetShare, style.insetEndShare, blockDropCap,
                ),
            )
            pendingMarker = null
        }
    }

    fun appendText(t: String, image: InlineImage? = null) {
        val style = styles.last()
        val plainLink = linkHref != null && !style.underline
        val scale = style.scale / blockFontScale
        val face = style.face.takeIf { it != BookFace.SERIF }
        val underline = linkHref == null && style.underlined
        val list = spans ?: return
        val text = transformed(t, style.transform, list.lastOrNull()?.text?.lastOrNull())
        list.add(
            FormattedSpan(text, style.bold, style.italic, linkHref, style.smallCaps, plainLink, scale, face, underline, style.baseline, style.letterSpacing, style.color, image),
        )
    }

    while (reader.hasNext()) {
        val event = reader.next()
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
                if (tag == "link" && reader.attrOrEmpty("rel").contains("stylesheet")) {
                    reader.attr("href")?.let(loadCss)?.let { stylesheet.add(it) }
                }
                if (tag == "style") styleText = StringBuilder()
                if (tag == "head") inHead = true
                // the SVG's own size is the picture's box: a cover page's "100%" fills the page
                if (tag == "svg" && svgDepth == 0) svgBox = ImageBox(
                    width = reader.attr("width")?.let(::cssLength) ?: CssLength.Percent(100f),
                    height = reader.attr("height")?.let(::cssLength)?.let { if (it is CssLength.Percent) CssLength.Vh(it.percent) else it },
                )
                val element = CssElement(
                    tag,
                    reader.attrOrEmpty("class").split(' ').filter { it.isNotEmpty() }.toSet(),
                    lastChild.last(),
                    reader.attr("id"),
                    (0 until reader.attributeCount).associate { reader.getAttributeLocalName(it).lowercase() to reader.getAttributeValue(it) },
                )
                lastChild[lastChild.lastIndex] = element
                path.addLast(element)
                val declared = stylesheet.styleFor(path, reader.attr("style"))
                if (isPageMarker(reader) || declared.hidden == true) {
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
                    val marginLeft = if (hasMargins) declared.marginLeft.spacing(inherited.scale) else Spacing.ZERO
                    val ruleAt = inherited.inset + marginLeft
                    // percent margins are of the containing block; a narrower width leaves room its auto margins take
                    val parentShare = styles.last().widthShare
                    val free = (declared.width as? CssLength.Percent)?.let { (1f - it.percent / 100f).coerceAtLeast(0f) * parentShare } ?: 0f
                    val leftAuto = hasMargins && declared.marginLeft == CssLength.Auto
                    val rightAuto = hasMargins && declared.marginRight == CssLength.Auto
                    val (freeLeft, freeRight) = when {
                        leftAuto && rightAuto -> free / 2 to free / 2
                        leftAuto -> free to 0f
                        else -> 0f to free
                    }
                    fun share(length: CssLength?) = (length as? CssLength.Percent)?.let { it.percent / 100f * parentShare } ?: 0f
                    inherited.copy(
                        inset = ruleAt + declared.paddingLeft.spacing(inherited.scale),
                        insetEnd = inherited.insetEnd + declared.paddingRight.spacing(inherited.scale) +
                            (if (hasMargins) declared.marginRight.spacing(inherited.scale) else Spacing.ZERO),
                        insetShare = inherited.insetShare + freeLeft + share(declared.paddingLeft) + (if (hasMargins) share(declared.marginLeft) else 0f),
                        insetEndShare = inherited.insetEndShare + freeRight + share(declared.paddingRight) + (if (hasMargins) share(declared.marginRight) else 0f),
                        leftRule = if (declared.borderLeft == true) LeftRule(group = ++borderedElements, at = ruleAt) else inherited.leftRule,
                    )
                }
                // vertical margins of blocks, in the block's own em; an inline element's don't move lines
                if (isBlock) {
                    declared.marginTop?.let { pendingMargin = collapsed(pendingMargin, it.spacing(style.scale)) }
                    pendingPadding += declared.paddingTop.spacing(style.scale)
                }
                styles.addLast(style)
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
                    blockFloatsRight = afterText
                    // the parts of a split paragraph sit together
                    pendingMargin = Spacing.ZERO
                }
                when (tag) {
                    "a" -> {
                        if (linkDepth == 0) linkHref = reader.attr("href")?.let { asLink(it, resolveSrc) }
                        linkDepth++
                    }
                    "blockquote" -> quoteDepth++
                    "img" -> {
                        val src = reader.attr("src")?.let(resolveSrc) ?: ""
                        val height = (declared.height ?: reader.attr("height")?.let(::cssLength)).letterEm()
                        // as LibreraX draws it: an image the book sizes like a letter stays in its line
                        if (spans != null && height != null && height > 0f && height <= INLINE_IMAGE_MAX_EM) {
                            appendText(INLINE_IMAGE.toString(), InlineImage(src, height))
                        } else {
                            // a picture breaks the paragraph around it; the text after it carries on unindented
                            val inText = spans != null
                            val (k, level) = kind to headingLevel
                            closeBlock()
                            blocks.add(Block.Image(reader.attr("alt") ?: "", src, takeGap(), imageBox(declared, reader, styles[styles.size - 2]), style.align))
                            if (inText) {
                                openBlock(k, level)
                                blockIndent = Spacing.ZERO
                                pendingMargin = Spacing.ZERO
                            }
                        }
                    }
                    "br" -> appendText(BR_MARK.toString())
                    "ul", "ol" -> listCounters.addLast(reader.attr("start")?.toIntOrNull() ?: 1)
                    "li" -> {
                        val n = reader.attr("value")?.toIntOrNull() ?: listCounters.lastOrNull() ?: 1
                        if (listCounters.isNotEmpty()) listCounters[listCounters.lastIndex] = n + 1
                        pendingMarker = listMarker(style.listStyle, n)
                        openBlock(BlockKind.PARAGRAPH, 0)
                    }
                    "p", "pre" -> if (isBlock) openBlock(BlockKind.PARAGRAPH, 0)
                    "svg" -> svgDepth++
                    // a picture wrapped in SVG, as cover pages have it: the SVG's box, the picture in it
                    "image" -> if (svgDepth > 0) {
                        reader.attr("href")?.let { href ->
                            closeBlock()
                            blocks.add(Block.Image("", resolveSrc(href), takeGap(), svgBox, style.align))
                        }
                    }
                    "hr" -> blocks.add(
                        Block.Rule(
                            takeGap(),
                            declared.backgroundImage?.let(resolveSrc),
                            declared.height?.takeIf { it is CssLength.Em || it is CssLength.Px }?.spacing(style.scale),
                            line = declared.noBorder != true,
                        ),
                    )
                    // cells sit side by side in their row, a space apart
                    "td", "th" -> if (spans != null) appendText(" ") else openBlock(BlockKind.PARAGRAPH, 0)
                    in headingTags -> if (isBlock) openBlock(BlockKind.HEADING, tag[1].digitToInt())
                }
            }
            // whitespace-only text (e.g. <i> </i> between words) arrives as
            // IGNORABLE_WHITESPACE; it is real text in XHTML
            EventType.TEXT, EventType.CDSECT, EventType.ENTITY_REF, EventType.IGNORABLE_WHITESPACE ->
                styleText?.append(reader.text) ?: run {
                    // text straight inside a <div>, <td>, <dd>… is a block of its own, as in a browser
                    if (svgDepth > 0) return@run
                    if (spans == null && !inHead && reader.text.any { it !in HTML_SPACE }) openBlock(BlockKind.PARAGRAPH, 0)
                    appendText(reader.text)
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
                    pendingMargin = Spacing.ZERO
                }
                when (tag) {
                    "style" -> {
                        styleText?.let { stylesheet.add(it.toString()) }
                        styleText = null
                    }
                    "a" -> {
                        linkDepth--
                        if (linkDepth == 0) linkHref = null
                    }
                    "blockquote" -> quoteDepth--
                    "head" -> inHead = false
                    "ul", "ol" -> listCounters.removeLastOrNull()
                    "svg" -> svgDepth--
                }
                val wasBlock = openIsBlock.removeLastOrNull() == true
                if (wasBlock) closeBlock()
                val closing = styles.last()
                if (styles.size > 1) styles.removeLast()
                path.removeLastOrNull()
                if (lastChild.size > 1) lastChild.removeLast()
                declaredStyles.removeLastOrNull()?.let { declared ->
                    if (wasBlock) {
                        declared.marginBottom?.let { pendingMargin = collapsed(pendingMargin, it.spacing(closing.scale)) }
                        pendingPadding += declared.paddingBottom.spacing(closing.scale)
                    }
                }
            }
            else -> {}
        }
    }
    return blocks
}

/** An element's inherited style: tag defaults, then the book's CSS for it. */
private data class Computed(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val smallCaps: Boolean = false,
    val align: TextAlign = TextAlign.START,
    /** `text-indent`; inherited, as in CSS */
    val indent: Spacing = Spacing.ZERO,
    /** font size relative to body text */
    val scale: Float = 1f,
    /** the book's colour; null: the theme's ink */
    val color: Long? = null,
    val underline: Boolean = true,
    /** `text-decoration: underline` on running text; [underline] is about links */
    val underlined: Boolean = false,
    val face: BookFace = BookFace.SERIF,
    val inset: Spacing = Spacing.ZERO,
    val insetEnd: Spacing = Spacing.ZERO,
    val insetShare: Float = 0f,
    val insetEndShare: Float = 0f,
    val leftRule: LeftRule? = null,
    val listStyle: String? = null,
    val keepsSpaces: Boolean = false,
    val baseline: Float = 0f,
    val transform: String? = null,
    val letterSpacing: Float = 0f,
    /** the content's share of the column's width: a block's `width: 89%` narrows what's inside */
    val widthShare: Float = 1f,
) {
    fun inherit(tag: String, declared: CssStyle): Computed {
        val ownFace = declared.face ?: face
        val ownScale = (declared.fontAbsolute ?: (scale * (declared.fontScale ?: 1f) * monospaceQuirk(face, ownFace)))
            .coerceIn(MIN_SCALE, MAX_SCALE)
        return Computed(
            bold = declared.bold ?: (bold || tag in boldTags || tag in headingTags),
            italic = declared.italic ?: (italic || tag in italicTags),
            smallCaps = declared.smallCaps ?: smallCaps,
            align = declared.align ?: align,
            indent = declared.indent?.spacing(ownScale) ?: indent,
            scale = ownScale,
            // inherit takes the parent's colour, and where the parent has none, the text's own
            color = declared.color?.let { if (it == TEXT_COLOR) color ?: TEXT_COLOR else it } ?: color,
            underline = declared.underline ?: underline,
            underlined = declared.underline ?: underlined,
            face = ownFace,
            inset = inset,
            insetEnd = insetEnd,
            insetShare = insetShare,
            insetEndShare = insetEndShare,
            leftRule = leftRule,
            listStyle = declared.listStyle ?: listStyle,
            keepsSpaces = declared.keepsSpaces ?: keepsSpaces,
            // vertical-align isn't inherited, but the text inside a raised element rides on it
            baseline = declared.baseline ?: baseline,
            transform = declared.transform ?: transform,
            letterSpacing = declared.letterSpacing ?: letterSpacing,
            widthShare = (declared.width as? CssLength.Percent)?.let { widthShare * it.percent / 100f } ?: widthShare,
        )
    }
}

/**
 * A browser sets monospace at 13px where other text is 16px, and keeps that ratio
 * for relative sizes: switching into or out of monospace scales the text by it.
 * LibreraX inherits this, so a book's transcripts come out smaller and tighter.
 */
private fun monospaceQuirk(parent: BookFace, own: BookFace): Float = when {
    own == BookFace.MONO && parent != BookFace.MONO -> MONOSPACE_RATIO
    own != BookFace.MONO && parent == BookFace.MONO -> 1f / MONOSPACE_RATIO
    else -> 1f
}

private const val MONOSPACE_RATIO = 13f / 16f

/** A length as spacing: em of body text (an element's em is its own font size, [scale]) or CSS px. Percentages aren't lengths here. */
private fun CssLength?.spacing(scale: Float): Spacing = when (this) {
    is CssLength.Em -> Spacing(em = em * scale)
    is CssLength.Px -> Spacing(px = px)
    else -> Spacing.ZERO
}

/** Adjoining vertical margins collapse to the largest, compared at a browser's 16px em. */
private fun collapsed(current: Spacing?, margin: Spacing): Spacing =
    if (current == null || margin.em * 16f + margin.px > current.em * 16f + current.px) margin else current

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
        width = ofColumn(declared.width ?: reader.attr("width")?.let(::cssLength)),
        height = declared.height ?: reader.attr("height")?.let(::cssLength),
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
    "ul", "ol", "dl", "dt", "dd", "table", "caption", "thead", "tbody", "tfoot", "tr",
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
