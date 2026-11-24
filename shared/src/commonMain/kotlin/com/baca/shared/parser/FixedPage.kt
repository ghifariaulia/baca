package com.baca.shared.parser

import nl.adaptivity.xmlutil.EventType
import kotlin.math.abs

/**
 * A fixed-layout (pre-paginated) EPUB page whose pictures and text runs are placed
 * with `position: absolute` inside an explicit pixel viewport (e.g. PDFTron / InDesign exports).
 * Coordinates and font sizes are in the page's own CSS px ([widthPx] × [heightPx]), origin top-left.
 */
data class FixedPage(
    val widthPx: Float,
    val heightPx: Float,
    val backgroundColor: Long? = 0xFFFFFFFFL,
    val images: List<FixedPageImage> = emptyList(),
    val texts: List<FixedPageText> = emptyList(),
    /** Logical lines/paragraphs reconstructed from [texts] for search, read-aloud and text selection. */
    val blocks: List<Block.Text> = emptyList(),
)

data class FixedPageImage(
    val src: String,
    val xPx: Float,
    val yPx: Float,
    val widthPx: Float,
    val heightPx: Float,
)

data class FixedPageText(
    val text: String,
    val xPx: Float,
    /** Bottom edge of the text span box in top-down page CSS px (`heightPx - bottomPx`). */
    val bottomY: Float,
    val fontSizePx: Float,
    val lineHeightRatio: Float = 1f,
    val family: String? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val color: Long? = null,
    val letterSpacingPx: Float = 0f,
    val wordSpacingPx: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationDeg: Float = 0f,
    /** Index into [FixedPage.blocks] and character offset range `[startOffset, endOffset)` within that block's text. */
    val blockIndex: Int = 0,
    val startOffset: Int = 0,
    val endOffset: Int = text.length,
)

/**
 * Parses a pre-paginated XHTML document into a [FixedPage] when it declares a pixel viewport
 * (`<meta name="viewport">` or `body`/`#Page` pixel `width` and `height`) and places its content
 * with `position: absolute`. Returns null when the document has no fixed pixel viewport or
 * contains unpositioned flowing text.
 */
internal fun parseFixedPage(
    source: String,
    resolveSrc: (String) -> String = { it },
    loadCss: (String) -> String? = { null },
): FixedPage? {
    val reader = newXmlReader(source)
    val stylesheet = Stylesheet(darkScheme = false)
    val facts = elementFacts(source)
    var ordinal = -1
    val path = ArrayDeque<CssElement>()
    val lastChild = ArrayDeque<CssElement?>().apply { add(null) }

    fun addSheet(css: String, href: String, linked: Boolean, depth: Int = 0) {
        FIXED_IMPORT.findAll(css).forEach { import ->
            val media = import.groupValues[2].trim()
            if (depth >= 8 || (media.isNotEmpty() && !mediaMatches(media, darkScheme = false))) return@forEach
            val target = href.substringBeforeLast('/', "").let { dir -> if (dir.isEmpty()) import.groupValues[1] else "$dir/${import.groupValues[1]}" }
            loadCss(target)?.let { addSheet(it, target, linked = true, depth + 1) }
        }
        stylesheet.add(if (linked) linkedSheet(css) else css)
    }

    var viewportWidth = 0f
    var viewportHeight = 0f
    var pageBackground: Long? = 0xFFFFFFFFL
    val images = mutableListOf<FixedPageImage>()
    val texts = mutableListOf<FixedPageText>()
    var hasUnpositionedText = false

    data class Frame(
        val xPx: Float = 0f,
        val bottomFromPageBottomPx: Float = 0f,
        val boxWidthPx: Float? = null,
        val boxHeightPx: Float? = null,
        val uniformScale: Float = 1f,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        val rotationDeg: Float = 0f,
        val rawFontPx: Float = 16f,
        val lineHeightRatio: Float = 1f,
        val family: String? = null,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val color: Long? = null,
        val rawLetterSpacingPx: Float = 0f,
        val rawWordSpacingPx: Float = 0f,
        val keepsSpaces: Boolean = false,
        val positioned: Boolean = false,
        val hidden: Boolean = false,
    )

    val stack = ArrayDeque<Frame>().apply { add(Frame()) }
    var inHead = false
    var inBody = false
    var styleText: StringBuilder? = null
    var styleMedia: String? = null
    val pendingText = ArrayDeque<StringBuilder?>()
    val hasChildren = ArrayDeque<Boolean>().apply { add(false) }

    while (reader.hasNext()) {
        val event = reader.next()
        if (event == EventType.START_ELEMENT) ordinal++
        when (event) {
            EventType.START_ELEMENT -> {
                val tag = reader.localName.lowercase()
                if (tag == "head") inHead = true
                if (tag == "body") inBody = true
                if (tag == "meta" && reader.attrOrEmpty("name").equals("viewport", ignoreCase = true)) {
                    val content = reader.attrOrEmpty("content")
                    VIEWPORT_W.find(content)?.groupValues?.get(1)?.toFloatOrNull()?.takeIf { it > 0f }?.let { viewportWidth = it }
                    VIEWPORT_H.find(content)?.groupValues?.get(1)?.toFloatOrNull()?.takeIf { it > 0f }?.let { viewportHeight = it }
                }
                val rel = reader.attrOrEmpty("rel").lowercase().split(' ')
                if (tag == "link" && "stylesheet" in rel && "alternate" !in rel && reader.attr("media").let { it == null || mediaMatches(it, false) }) {
                    reader.attr("href")?.let { href -> loadCss(href.substringBefore('#'))?.let { addSheet(it, href, linked = true) } }
                }
                if (tag == "style") {
                    styleText = StringBuilder()
                    styleMedia = reader.attr("media")
                }

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
                lastChild.addLast(null)

                val declared = stylesheet.styleFor(path, reader.attr("style"))
                val parent = stack.last()
                val cssW = (declared.width as? CssLength.Px)?.px
                val cssH = (declared.height as? CssLength.Px)?.px
                val attrW = reader.attr("width")?.toFloatOrNull()
                val attrH = reader.attr("height")?.toFloatOrNull()
                val declW = cssW ?: if (tag == "img" && parent.positioned && parent.boxWidthPx != null) null else attrW
                val declH = cssH ?: if (tag == "img" && parent.positioned && parent.boxHeightPx != null) null else attrH
                if ((tag == "body" || element.id == "Page" || "PageContainer" in element.classes) && declW != null && declH != null) {
                    if (viewportWidth <= 0f) viewportWidth = declW
                    if (viewportHeight <= 0f) viewportHeight = declH
                }
                if ((element.id == "Page" || "PageContainer" in element.classes) && declared.background != null && declared.background != TRANSPARENT) {
                    pageBackground = declared.background
                }

                val parentCoordScaleX = parent.uniformScale * parent.scaleX
                val parentCoordScaleY = parent.uniformScale * parent.scaleY
                val containerW = parent.boxWidthPx ?: viewportWidth.takeIf { it > 0f } ?: 0f
                val containerH = parent.boxHeightPx ?: viewportHeight.takeIf { it > 0f } ?: 0f

                val xPx = when {
                    declared.leftPx != null -> parent.xPx + declared.leftPx * parentCoordScaleX
                    declared.rightPx != null && declW != null && containerW > 0f -> containerW - (declared.rightPx + declW) * parentCoordScaleX
                    else -> parent.xPx
                }
                val bottomFromBottomPx = when {
                    declared.bottomPx != null -> parent.bottomFromPageBottomPx + declared.bottomPx * parentCoordScaleY
                    declared.topPx != null && declH != null && containerH > 0f -> containerH - (declared.topPx + declH) * parentCoordScaleY
                    else -> parent.bottomFromPageBottomPx
                }

                val sx = declared.transformScale
                val sy = declared.transformScaleY ?: sx
                val isUniform = sx != null && sy != null && abs(sx - sy) < 1e-4f
                val newUniform = if (isUniform) parent.uniformScale * sx else parent.uniformScale
                val newScaleX = if (sx != null && !isUniform) parent.scaleX * sx else parent.scaleX
                val newScaleY = if (sy != null && !isUniform) parent.scaleY * sy else parent.scaleY
                val newRot = parent.rotationDeg + (declared.transformRotateDeg ?: 0f)

                val rawFontPx = when {
                    declared.fontAbsolute != null -> declared.fontAbsolute * 16f
                    declared.fontScale != null -> parent.rawFontPx * declared.fontScale
                    else -> parent.rawFontPx
                }
                val rawLetterPx = when (val ls = declared.letterSpacing) {
                    is CssLength.Px -> ls.px
                    is CssLength.Em -> ls.em * rawFontPx
                    else -> parent.rawLetterSpacingPx
                }
                val rawWordPx = when (val ws = declared.wordSpacing) {
                    is CssLength.Px -> ws.px
                    is CssLength.Em -> ws.em * rawFontPx
                    else -> parent.rawWordSpacingPx
                }
                val isPositioned = parent.positioned || declared.position == "absolute" || declared.position == "fixed"
                val isHidden = parent.hidden || declared.hidden == true || declared.invisible == true || declared.color == INVISIBLE

                val frame = Frame(
                    xPx = xPx,
                    bottomFromPageBottomPx = bottomFromBottomPx,
                    boxWidthPx = declW?.let { it * parentCoordScaleX } ?: parent.boxWidthPx,
                    boxHeightPx = declH?.let { it * parentCoordScaleY } ?: parent.boxHeightPx,
                    uniformScale = newUniform,
                    scaleX = newScaleX,
                    scaleY = newScaleY,
                    rotationDeg = newRot,
                    rawFontPx = rawFontPx,
                    lineHeightRatio = declared.lineRatio?.takeIf { it > 0f } ?: parent.lineHeightRatio,
                    family = declared.family ?: parent.family,
                    bold = declared.bold ?: (parent.bold || tag == "b" || tag == "strong"),
                    italic = declared.italic ?: (parent.italic || tag == "i" || tag == "em"),
                    color = if (tag == "html" || tag == "body") parent.color else declared.color?.takeIf { it != TEXT_COLOR } ?: parent.color,
                    rawLetterSpacingPx = rawLetterPx,
                    rawWordSpacingPx = rawWordPx,
                    keepsSpaces = declared.keepsSpaces ?: parent.keepsSpaces,
                    positioned = isPositioned,
                    hidden = isHidden,
                )
                hasChildren[hasChildren.lastIndex] = true
                hasChildren.addLast(false)
                stack.addLast(frame)
                pendingText.addLast(if (inBody && !inHead && tag != "style" && tag != "script") StringBuilder() else null)

                if (tag == "img" && !isHidden && isPositioned) {
                    val src = reader.attr("src")?.takeIf { it.isNotEmpty() }?.let(resolveSrc)
                    val w = frame.boxWidthPx ?: viewportWidth
                    val h = frame.boxHeightPx ?: viewportHeight
                    if (src != null && w > 0f && h > 0f && viewportHeight > 0f) {
                        val topY = viewportHeight - frame.bottomFromPageBottomPx - h
                        images.add(FixedPageImage(src = src, xPx = frame.xPx, yPx = topY, widthPx = w, heightPx = h))
                    }
                }
            }
            EventType.TEXT, EventType.CDSECT, EventType.ENTITY_REF, EventType.IGNORABLE_WHITESPACE -> {
                val activeStyle = styleText
                if (activeStyle != null) {
                    activeStyle.append(reader.text)
                } else if (inBody && !inHead) {
                    pendingText.lastOrNull()?.append(reader.text)
                }
            }
            EventType.END_ELEMENT -> {
                val tag = reader.localName.lowercase()
                if (tag == "head") inHead = false
                if (tag == "style") {
                    styleText?.takeIf { styleMedia.let { m -> m == null || mediaMatches(m, false) } }?.let { addSheet(it.toString(), "", linked = false) }
                    styleText = null
                }
                val frame = stack.removeLast()
                val textBuf = pendingText.removeLastOrNull()
                val hadChild = hasChildren.removeLastOrNull() == true
                path.removeLastOrNull()
                if (lastChild.size > 1) lastChild.removeLast()

                val rawText = textBuf?.toString().orEmpty()
                if (rawText.isNotEmpty() && !frame.hidden) {
                    if (!frame.positioned) {
                        if (rawText.isNotBlank()) hasUnpositionedText = true
                    } else if (viewportHeight > 0f) {
                        val clean = rawText.replace("\r", "").replace("\n", "")
                        if (clean.isNotBlank() || (!hadChild && frame.keepsSpaces && clean.isNotEmpty())) {
                            texts.add(
                                FixedPageText(
                                    text = clean,
                                    xPx = frame.xPx,
                                    bottomY = viewportHeight - frame.bottomFromPageBottomPx,
                                    fontSizePx = (frame.rawFontPx * frame.uniformScale).coerceAtLeast(1f),
                                    lineHeightRatio = frame.lineHeightRatio,
                                    family = frame.family,
                                    bold = frame.bold,
                                    italic = frame.italic,
                                    color = frame.color,
                                    letterSpacingPx = frame.rawLetterSpacingPx * frame.uniformScale,
                                    wordSpacingPx = frame.rawWordSpacingPx * frame.uniformScale,
                                    scaleX = frame.scaleX,
                                    scaleY = frame.scaleY,
                                    rotationDeg = frame.rotationDeg,
                                ),
                            )
                        }
                    }
                }
            }
            else -> {}
        }
    }

    if (viewportWidth <= 0f || viewportHeight <= 0f || hasUnpositionedText) return null
    val (linkedTexts, blocks) = assignTextBlocks(texts)
    return FixedPage(
        widthPx = viewportWidth,
        heightPx = viewportHeight,
        backgroundColor = pageBackground,
        images = images,
        texts = linkedTexts,
        blocks = blocks,
    )
}

/**
 * Links positioned [FixedPageText] fragments into logical paragraph [Block.Text]s for search,
 * read-aloud and text selection while keeping every fragment's exact visual coordinates unchanged.
 */
private fun assignTextBlocks(rawTexts: List<FixedPageText>): Pair<List<FixedPageText>, List<Block.Text>> {
    if (rawTexts.isEmpty()) return emptyList<FixedPageText>() to emptyList()
    class LineGroup(var bottomY: Float, var fontSizePx: Float, var leftX: Float, val indices: MutableList<Int>)

    val lines = mutableListOf<LineGroup>()
    for (i in rawTexts.indices) {
        val run = rawTexts[i]
        val last = lines.lastOrNull()
        if (last != null && run.rotationDeg == 0f && abs(run.bottomY - last.bottomY) <= maxOf(run.fontSizePx, last.fontSizePx) * 0.45f) {
            last.indices.add(i)
            if (run.text.isNotBlank()) {
                last.fontSizePx = maxOf(last.fontSizePx, run.fontSizePx)
                last.leftX = minOf(last.leftX, run.xPx)
            }
        } else {
            lines.add(LineGroup(run.bottomY, run.fontSizePx, run.xPx, mutableListOf(i)))
        }
    }

    val paragraphs = mutableListOf<MutableList<LineGroup>>()
    for (ln in lines) {
        val prevGroup = paragraphs.lastOrNull()
        if (prevGroup != null) {
            val prevLn = prevGroup.last()
            val dy = ln.bottomY - prevLn.bottomY
            val sameFs = abs(ln.fontSizePx - prevLn.fontSizePx) <= 2.5f
            val sameCol = abs(ln.leftX - prevLn.leftX) <= ln.fontSizePx * 3f
            if (sameFs && sameCol && dy > 0f && dy <= ln.fontSizePx * 1.8f && ln.fontSizePx <= 30f) {
                prevGroup.add(ln)
            } else {
                paragraphs.add(mutableListOf(ln))
            }
        } else {
            paragraphs.add(mutableListOf(ln))
        }
    }

    val updated = rawTexts.toMutableList()
    val blocks = mutableListOf<Block.Text>()
    for ((blockIndex, para) in paragraphs.withIndex()) {
        val sb = StringBuilder()
        for ((lineIdx, ln) in para.withIndex()) {
            if (lineIdx > 0 && sb.isNotEmpty() && !sb.endsWith(' ')) {
                sb.append(' ')
            }
            var prevRight: Float? = null
            for (idx in ln.indices) {
                val run = rawTexts[idx]
                val gap = prevRight?.let { run.xPx - it } ?: 0f
                if (prevRight != null && gap > run.fontSizePx * 0.18f && sb.isNotEmpty() && !sb.endsWith(' ') && !run.text.startsWith(' ')) {
                    sb.append(' ')
                }
                val start = sb.length
                sb.append(run.text)
                val end = sb.length
                updated[idx] = run.copy(blockIndex = blockIndex, startOffset = start, endOffset = end)
                prevRight = run.xPx + estimateRunWidth(run)
            }
        }
        blocks.add(
            Block.Text(
                spans = listOf(FormattedSpan(sb.toString(), false, false)),
                kind = BlockKind.PARAGRAPH,
                headingLevel = 0,
            ),
        )
    }
    return updated to blocks
}

private fun estimateRunWidth(run: FixedPageText): Float {
    var w = 0f
    for (ch in run.text) {
        val ratio = when {
            ch in "MW@%" -> 0.82f
            ch in "OQGCDHNRU" -> 0.68f
            ch in "ABEFKSTVXYZ" -> 0.60f
            ch in "IJL1.,:;!|iIlj" -> 0.28f
            ch.isUpperCase() -> 0.62f
            else -> 0.48f
        }
        w += run.fontSizePx * ratio * run.scaleX + maxOf(run.letterSpacingPx, 0f)
    }
    return w
}

private val VIEWPORT_W = Regex("""width\s*=\s*([0-9.]+)""", RegexOption.IGNORE_CASE)
private val VIEWPORT_H = Regex("""height\s*=\s*([0-9.]+)""", RegexOption.IGNORE_CASE)
private val FIXED_IMPORT = Regex("""@import\s+(?:url\(\s*)?["']?([^"')\s;]+)["']?\s*\)?\s*([^;]*);""", RegexOption.IGNORE_CASE)
