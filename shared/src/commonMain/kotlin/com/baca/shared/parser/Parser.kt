package com.baca.shared.parser

import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.core.KtXmlReader
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.openZip
import java.io.StringReader

data class FormattedSpan(
    val text: String,
    val bold: Boolean,
    val italic: Boolean,
    /** Internal links carry the target document's zip path; external carry the URL. */
    val href: String? = null,
    /** book CSS `font-variant: small-caps` */
    val smallCaps: Boolean = false,
    /** a link the book's CSS draws as body text (own colour, no underline); still followed on tap */
    val plainLink: Boolean = false,
    /** book CSS font size relative to its block's */
    val scale: Float = 1f,
    /** a face other than the book's running text's (TECH 3.1); null is the reader's font */
    val face: BookFace? = null,
    /** book CSS `text-decoration: underline` on text that isn't a link */
    val underline: Boolean = false,
    /** an image drawn in the line, in place of this span's one [INLINE_IMAGE] char */
    val image: InlineImage? = null,
)

/** The generic faces a book's CSS names; drawn with the reader's own sans and monospace fonts. */
enum class BookFace { SERIF, SANS, MONO }

/** A text-sized image inside a paragraph (Kinki Region's ○○ redactions), [heightEm] tall, as wide as its aspect makes it. */
data class InlineImage(val src: String, val heightEm: Float)

/** The char an inline image stands on in its block's text, so offsets count it once. */
const val INLINE_IMAGE = '\uFFFC'

/**
 * A vertical rule left of a block, from a book CSS `border-left`, [atEm] in from
 * the column edge. Blocks of one bordered element share a [group], so the rule
 * runs on through the gaps between them.
 */
data class LeftRule(val group: Int, val atEm: Float)

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
        val indent: Boolean = true,
        /** book CSS font size relative to body text; null when the book sets none */
        val scale: Float? = null,
        /** book CSS negative text-indent: first line flush, the rest take the reader's indent */
        val hanging: Boolean = false,
        override val gapBeforeEm: Float? = null,
        /** book CSS left border and padding: the text sits this far in from the column edge */
        val insetEm: Float = 0f,
        val leftRule: LeftRule? = null,
        /** book CSS `float: right` at a paragraph's end: drawn at the right, beside the line before if it fits */
        val floatsRight: Boolean = false,
    ) : Block() {
        override val text: String get() = spans.joinToString("") { it.text }
    }

    data class Image(val alt: String, val src: String, override val gapBeforeEm: Float? = null) : Block() {
        override val text: String get() = ""
    }

    /** Space above the block from the book's CSS margins and padding, in em; null leaves the reader's gap. */
    abstract val gapBeforeEm: Float?
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
    return ParsedEpub(opf.title, opf.author, cover, withRunningText(chapters), toc, opf.identifier)
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
)

private fun parseOpf(opfXml: String): Opf {
    val reader = newXmlReader(opfXml)
    val manifest = mutableMapOf<String, String>()
    val spine = mutableListOf<String>()
    var title = ""
    var author = ""
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
                "meta" -> if (reader.attrOrEmpty("name") == "cover") coverId = reader.attr("content")
            }
            EventType.TEXT, EventType.CDSECT -> capture?.invoke(reader.text)
            EventType.END_ELEMENT -> when (reader.localName) {
                "title" -> capture = null
                "creator" -> capture = null
                "identifier" -> capture = null
                "metadata" -> inMetadata = false
            }
            else -> {}
        }
    }
    val coverByMeta = coverId?.let { manifest[it] }
    val identifier = (identifiers.firstOrNull { it.first != null && it.first == uniqueId } ?: identifiers.firstOrNull())
        ?.second?.trim()?.takeIf { it.isNotEmpty() }
    return Opf(title.trim(), author.trim(), coverHref ?: coverByMeta, navHref ?: ncxHref, manifest, spine, identifier)
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
    var blockIndent = true
    var blockScale: Float? = null
    var blockHanging = false
    var blockFloatsRight = false
    // the block's own computed size, so spans carry theirs relative to it
    var blockFontScale = 1f
    // book CSS vertical margins (collapsing: the largest wins) and padding (adding up) since the last block
    var pendingMargin: Float? = null
    var pendingPadding = 0f
    var borderedElements = 0
    var quoteDepth = 0
    var linkDepth = 0
    var linkHref: String? = null
    // inside a printed-page marker or a display:none element: its depth, 0 when not skipping
    var skipDepth = 0
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

    fun takeGap(): Float? {
        val gap = if (pendingMargin == null && pendingPadding == 0f) null else (pendingMargin ?: 0f) + pendingPadding
        pendingMargin = null
        pendingPadding = 0f
        return gap
    }

    fun openBlock(k: BlockKind, level: Int) {
        val style = styles.last()
        kind = if (quoteDepth > 0 && k == BlockKind.PARAGRAPH) BlockKind.QUOTE else k
        headingLevel = level
        blockAlign = style.align
        blockIndent = !style.noIndent
        blockScale = style.scale.takeIf { it != 1f }
        blockHanging = style.hanging
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
            if (last != null && last.bold == s.bold && last.italic == s.italic && last.href == s.href && last.smallCaps == s.smallCaps && last.plainLink == s.plainLink &&
                last.scale == s.scale && last.face == s.face && last.underline == s.underline && last.image == null && s.image == null
            ) {
                merged[merged.lastIndex] = last.copy(text = last.text + s.text)
            } else {
                merged.add(s)
            }
        }
        val collapsed = collapseWhitespace(merged)
        // not isNotBlank: a lone &nbsp; survives collapsing and draws an empty
        // line, as in HTML (books use <p>&nbsp;</p> for scene breaks)
        if (collapsed.any { it.text.isNotEmpty() }) {
            val style = styles.last()
            blocks.add(
                Block.Text(
                    collapsed, kind, headingLevel, blockAlign, blockIndent, blockScale, blockHanging, takeGap(),
                    style.insetEm, style.leftRule, blockFloatsRight,
                ),
            )
        }
    }

    fun appendText(t: String, image: InlineImage? = null) {
        val style = styles.last()
        val plainLink = linkHref != null && style.ownColor && !style.underline
        val scale = style.scale / blockFontScale
        val face = style.face.takeIf { it != BookFace.SERIF }
        val underline = linkHref == null && style.underlined
        spans?.add(FormattedSpan(t, style.bold, style.italic, linkHref, style.smallCaps, plainLink, scale, face, underline, image))
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
                val element = CssElement(tag, reader.attrOrEmpty("class").split(' ').filter { it.isNotEmpty() }.toSet(), lastChild.last())
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
                // margins of block-level elements only, not of inline text inside a paragraph
                if (spans == null) {
                    declared.marginTop?.let { pendingMargin = maxOf(pendingMargin ?: 0f, it) }
                    pendingPadding += declared.paddingTop ?: 0f
                }
                val style = styles.last().inherit(tag, declared).let { inherited ->
                    if (declared.borderLeft != true) return@let inherited
                    val ruleAt = inherited.insetEm + (declared.marginLeft ?: 0f)
                    inherited.copy(
                        insetEm = ruleAt + (declared.paddingLeft ?: 0f),
                        leftRule = LeftRule(group = ++borderedElements, atEm = ruleAt),
                    )
                }
                styles.addLast(style)
                if (declared.floatRight == true && spans != null && floatDepth == 0) {
                    val blocksBefore = blocks.size
                    closeBlock()
                    // it may share a row only with text of its own paragraph
                    val afterText = blocks.size > blocksBefore
                    floatDepth = styles.size
                    val (k, level) = kind to headingLevel
                    openBlock(k, level)
                    blockAlign = TextAlign.END
                    blockIndent = false
                    blockHanging = false
                    blockFloatsRight = afterText
                    // the parts of a split paragraph sit together
                    pendingMargin = 0f
                }
                when (tag) {
                    "a" -> {
                        if (linkDepth == 0) linkHref = reader.attr("href")?.let { asLink(it, resolveSrc) }
                        linkDepth++
                    }
                    "blockquote" -> quoteDepth++
                    "img" -> {
                        val src = reader.attr("src")?.let(resolveSrc) ?: ""
                        val height = declared.heightEm
                        // as LibreraX draws it: an image the book sizes like a letter stays in its line
                        if (spans != null && height != null && height > 0f && height <= INLINE_IMAGE_MAX_EM) {
                            appendText(INLINE_IMAGE.toString(), InlineImage(src, height))
                        } else {
                            blocks.add(Block.Image(reader.attr("alt") ?: "", src, takeGap()))
                        }
                    }
                    "br" -> appendText(BR_MARK.toString())
                    "p", "li" -> openBlock(BlockKind.PARAGRAPH, 0)
                    in headingTags -> openBlock(BlockKind.HEADING, tag[1].digitToInt())
                }
            }
            // whitespace-only text (e.g. <i> </i> between words) arrives as
            // IGNORABLE_WHITESPACE; it is real text in XHTML
            EventType.TEXT, EventType.CDSECT, EventType.ENTITY_REF, EventType.IGNORABLE_WHITESPACE ->
                styleText?.append(reader.text) ?: appendText(reader.text)
            EventType.END_ELEMENT -> {
                val tag = reader.localName.lowercase()
                if (floatDepth == styles.size) {
                    // the floated part ends: what follows continues the paragraph, unindented
                    floatDepth = 0
                    val (k, level) = kind to headingLevel
                    val (align, scale) = styles[styles.size - 2].align to blockScale
                    closeBlock()
                    openBlock(k, level)
                    blockAlign = align
                    blockScale = scale
                    blockIndent = false
                    pendingMargin = 0f
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
                    "p", "li" -> closeBlock()
                    in headingTags -> closeBlock()
                }
                if (styles.size > 1) styles.removeLast()
                path.removeLastOrNull()
                if (lastChild.size > 1) lastChild.removeLast()
                declaredStyles.removeLastOrNull()?.let { declared ->
                    if (spans == null) {
                        declared.marginBottom?.let { pendingMargin = maxOf(pendingMargin ?: 0f, it) }
                        pendingPadding += declared.paddingBottom ?: 0f
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
    val noIndent: Boolean = false,
    val hanging: Boolean = false,
    /** font size relative to body text */
    val scale: Float = 1f,
    val ownColor: Boolean = false,
    val underline: Boolean = true,
    /** `text-decoration: underline` on running text; [underline] is about links */
    val underlined: Boolean = false,
    val face: BookFace = BookFace.SERIF,
    val insetEm: Float = 0f,
    val leftRule: LeftRule? = null,
) {
    fun inherit(tag: String, declared: CssStyle): Computed {
        val ownFace = declared.face ?: face
        return Computed(
            bold = declared.bold ?: (bold || tag in boldTags || tag in headingTags),
            italic = declared.italic ?: (italic || tag in italicTags),
            smallCaps = declared.smallCaps ?: smallCaps,
            align = declared.align ?: align,
            noIndent = declared.noIndent ?: noIndent,
            hanging = declared.hanging ?: hanging,
            scale = (scale * (declared.fontScale ?: 1f) * monospaceQuirk(face, ownFace)).coerceIn(MIN_SCALE, MAX_SCALE),
            ownColor = declared.ownColor ?: ownColor,
            underline = declared.underline ?: underline,
            underlined = declared.underline ?: underlined,
            face = ownFace,
            insetEm = insetEm,
            leftRule = leftRule,
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

// taller than this, an inline image would push its line past the line height every page is measured with
private const val INLINE_IMAGE_MAX_EM = 1f

// a book's tiny print or poster-size headings shouldn't overrule the reader's size
private const val MIN_SCALE = 0.75f
private const val MAX_SCALE = 2f

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
