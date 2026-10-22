package com.baca.shared.parser

import nl.adaptivity.xmlutil.EventType

/**
 * The part of a book's CSS that Baca applies (TECH 3.1), cascaded as in a
 * browser, which is how LibreraX shows a book: the book's rules win, by
 * `!important`, specificity and order, over a browser's default stylesheet
 * (`DEFAULT_CSS`). The reader's font, size and line spacing are not CSS here.
 *
 * Selectors: compounds like `tag`, `.class`, `tag.class.other`, joined by
 * descendant (space), child (`>`) or next-sibling (`+`) combinators. Rules
 * with `~`, attribute, id or pseudo selectors are skipped rather than guessed at.
 */
internal data class CssStyle(
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val smallCaps: Boolean? = null,
    val align: TextAlign? = null,
    /** `writing-mode` is vertical (`vertical-rl`, `tb-rl`…): its lines run down the page, from the right */
    val vertical: Boolean? = null,
    /** `direction: rtl` (true) or `ltr`; inherited */
    val rtl: Boolean? = null,
    /** `text-align` as written, `left` and `right` physical: they mean the other side of a right-to-left block */
    val alignSide: String? = null,
    /** `text-align: left` (true) or `justify` (false), both [TextAlign.START]: kept where the reader's justification isn't set */
    val alignLeft: Boolean? = null,
    /** `text-align: -webkit-center` or `-webkit-right`, which also push narrower blocks inside; inherited */
    val pushesBlocks: TextAlign? = null,
    /** `text-indent`; negative hangs the first line out */
    val indent: CssLength? = null,
    val floatRight: Boolean? = null,
    val floatLeft: Boolean? = null,
    /** `line-height` over font size, for a floated drop cap's height */
    val lineRatio: Float? = null,
    val hidden: Boolean? = null,
    /** `display` as written: block, inline, list-item… */
    val display: String? = null,
    /** relative to the parent element: 1.2 for `1.2em` or `120%` */
    val fontScale: Float? = null,
    /** relative to the browser's 16px, whatever the parent's: `20px`, `1.2rem` */
    val fontAbsolute: Float? = null,
    /** a size keyword, `xx-small` 0 to `xxx-large` 7, which plain monospace takes from a table of its own */
    val fontKeyword: Int? = null,
    /** `color` as 0xAARRGGBB, or [TEXT_COLOR] for `inherit`/`currentColor` */
    val color: Long? = null,
    val underline: Boolean? = null,
    val lineThrough: Boolean? = null,
    /** the generic face `font-family` names first */
    val face: BookFace? = null,
    /** `font-family: monospace` and nothing else, which a browser sets at 13px rather than 16 */
    val genericMono: Boolean? = null,
    /** the first family `font-family` names, lowercased: a book's own font, or a generic name */
    val family: String? = null,
    /** margins and padding; not inherited */
    val marginTop: CssLength? = null,
    val marginBottom: CssLength? = null,
    val paddingTop: CssLength? = null,
    val paddingBottom: CssLength? = null,
    val marginLeft: CssLength? = null,
    val paddingLeft: CssLength? = null,
    val marginRight: CssLength? = null,
    val paddingRight: CssLength? = null,
    /** each side's border, as the book sets it: `border`, `border-top`, `border-width`… */
    val borderTop: BorderSide = BorderSide(),
    val borderRight: BorderSide = BorderSide(),
    val borderBottom: BorderSide = BorderSide(),
    val borderLeft: BorderSide = BorderSide(),
    /** `border-radius` in CSS px */
    val radius: CssLength? = null,
    /** `background-color` (0xAARRGGBB), [TRANSPARENT] for none */
    val background: Long? = null,
    /** `list-style-type`, inherited by list items: disc, decimal, none… */
    val listStyle: String? = null,
    /** `vertical-align` raising (positive) or lowering text, in em of its font */
    val baseline: Float? = null,
    /** `vertical-align` in percent of the line height, which isn't known until laid out */
    val baselineLine: Float? = null,
    /** `page-break-before/after: always` (or `break-before/after: page`) */
    val breakBefore: Boolean? = null,
    val breakAfter: Boolean? = null,
    /** `break-before/after/inside: avoid` (or `page-break-*: avoid`): no page break there if one can go elsewhere */
    val avoidBefore: Boolean? = null,
    val avoidAfter: Boolean? = null,
    val avoidInside: Boolean? = null,
    /** `vertical-align: super` (1) or `sub` (-1), which move text by its parent's size */
    val shiftKeyword: Int? = null,
    /** `vertical-align` as a keyword (`top`, `middle`…), which places a table cell in its row */
    val rowAlign: String? = null,
    /** `border-spacing`, across and down; inherited */
    val borderSpacing: Pair<CssLength, CssLength>? = null,
    /** `border-collapse: collapse`, which leaves no spacing between cells; inherited */
    val borderCollapse: Boolean? = null,
    /** `text-transform`: uppercase, lowercase, capitalize or none */
    val transform: String? = null,
    val minWidth: CssLength? = null,
    /** `column-count`; 1 for `auto` */
    val columnCount: Int? = null,
    /** `column-gap`; null for `normal` */
    val columnGap: CssLength? = null,
    /** `line-height` in px, which [lineRatio] can't hold: a ratio of a font size not yet known */
    val linePx: Float? = null,
    val hyphenate: Boolean? = null,
    val invisible: Boolean? = null,
    /** `word-spacing` in em */
    val wordSpacing: Float? = null,
    /** `letter-spacing` in em; 0 for `normal` */
    val letterSpacing: Float? = null,
    /** `white-space` keeps the text's spaces and line breaks (`pre`, `pre-wrap`, `pre-line`) */
    val keepsSpaces: Boolean? = null,
    /** `background-image: url(…)`, as the book writes it */
    val backgroundImage: String? = null,
    /** box sizes as the book writes them; images and blocks use them */
    val width: CssLength? = null,
    val height: CssLength? = null,
    val maxWidth: CssLength? = null,
) {
    /** [other]'s set values win. */
    private val setsSize get() = fontScale != null || fontAbsolute != null || fontKeyword != null

    fun overriddenBy(other: CssStyle) = CssStyle(
        bold = other.bold ?: bold,
        italic = other.italic ?: italic,
        smallCaps = other.smallCaps ?: smallCaps,
        align = other.align ?: align,
        pushesBlocks = if (other.align != null) other.pushesBlocks else pushesBlocks,
        alignLeft = if (other.align != null) other.alignLeft else alignLeft,
        alignSide = if (other.align != null) other.alignSide else alignSide,
        rtl = other.rtl ?: rtl,
        vertical = other.vertical ?: vertical,
        indent = other.indent ?: indent,
        floatRight = other.floatRight ?: floatRight,
        floatLeft = other.floatLeft ?: floatLeft,
        hidden = other.hidden ?: hidden,
        display = other.display ?: display,
        // the later of a relative and an absolute size wins, as one font-size declaration
        fontScale = if (other.setsSize) other.fontScale else fontScale,
        fontAbsolute = if (other.setsSize) other.fontAbsolute else fontAbsolute,
        fontKeyword = if (other.setsSize) other.fontKeyword else fontKeyword,
        color = other.color ?: color,
        underline = other.underline ?: underline,
        lineThrough = other.lineThrough ?: lineThrough,
        face = other.face ?: face,
        genericMono = other.genericMono ?: genericMono,
        family = other.family ?: family,
        marginTop = other.marginTop ?: marginTop,
        marginBottom = other.marginBottom ?: marginBottom,
        paddingTop = other.paddingTop ?: paddingTop,
        paddingBottom = other.paddingBottom ?: paddingBottom,
        borderTop = borderTop.overriddenBy(other.borderTop),
        borderRight = borderRight.overriddenBy(other.borderRight),
        borderBottom = borderBottom.overriddenBy(other.borderBottom),
        borderLeft = borderLeft.overriddenBy(other.borderLeft),
        radius = other.radius ?: radius,
        background = other.background ?: background,
        marginLeft = other.marginLeft ?: marginLeft,
        paddingLeft = other.paddingLeft ?: paddingLeft,
        marginRight = other.marginRight ?: marginRight,
        paddingRight = other.paddingRight ?: paddingRight,
        listStyle = other.listStyle ?: listStyle,
        // one vertical-align declaration: in em or in line heights
        baseline = if (other.baseline != null || other.baselineLine != null) other.baseline else baseline,
        baselineLine = if (other.baseline != null || other.baselineLine != null) other.baselineLine else baselineLine,
        breakBefore = other.breakBefore ?: breakBefore,
        breakAfter = other.breakAfter ?: breakAfter,
        avoidBefore = other.avoidBefore ?: avoidBefore,
        avoidAfter = other.avoidAfter ?: avoidAfter,
        avoidInside = other.avoidInside ?: avoidInside,
        rowAlign = if (other.baseline != null || other.baselineLine != null) other.rowAlign else rowAlign,
        shiftKeyword = if (other.baseline != null || other.baselineLine != null) other.shiftKeyword else shiftKeyword,
        borderSpacing = other.borderSpacing ?: borderSpacing,
        borderCollapse = other.borderCollapse ?: borderCollapse,
        transform = other.transform ?: transform,
        letterSpacing = other.letterSpacing ?: letterSpacing,
        minWidth = other.minWidth ?: minWidth,
        columnCount = other.columnCount ?: columnCount,
        columnGap = other.columnGap ?: columnGap,
        // one line-height declaration: a ratio or px
        lineRatio = if (other.lineRatio != null || other.linePx != null) other.lineRatio else lineRatio,
        linePx = if (other.lineRatio != null || other.linePx != null) other.linePx else linePx,
        hyphenate = other.hyphenate ?: hyphenate,
        invisible = other.invisible ?: invisible,
        wordSpacing = other.wordSpacing ?: wordSpacing,
        keepsSpaces = other.keepsSpaces ?: keepsSpaces,
        backgroundImage = other.backgroundImage ?: backgroundImage,
        width = other.width ?: width,
        height = other.height ?: height,
        maxWidth = other.maxWidth ?: maxWidth,
    )
}

/** A declaration block split by `!important`, which outranks every normal declaration. */
internal data class Declarations(val normal: CssStyle = CssStyle(), val important: CssStyle = CssStyle())

/** An element as selectors see it, with the sibling element just before it. */
internal class CssElement(
    val tag: String,
    val classes: Set<String>,
    val previous: CssElement? = null,
    val id: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    /** where it sits among its siblings, read ahead of the parse ([elementFacts]); null when unknown */
    val facts: ElementFacts? = null,
)

/**
 * An element's place among its siblings, which a parse reading forwards can't
 * know yet: its 1-based [index] of [count] children, its [typeIndex] of
 * [typeCount] siblings with its tag, and whether it holds nothing ([empty]).
 */
internal class ElementFacts(val index: Int, val count: Int, val typeIndex: Int, val typeCount: Int, val empty: Boolean)

/** Each element's [ElementFacts], in document order. */
internal fun elementFacts(xhtml: String): List<ElementFacts> {
    class Frame(val tag: String) {
        val children = mutableListOf<Pair<Int, String>>()
        var content = false
    }
    val reader = newXmlReader(xhtml)
    val counts = mutableListOf<IntArray>() // index, count, typeIndex, typeCount
    val empty = mutableListOf<Boolean>()
    val stack = ArrayDeque<Pair<Int, Frame>>()
    val root = Frame("")
    fun settle(frame: Frame) {
        frame.children.forEachIndexed { i, (ordinal, tag) ->
            val sameTag = frame.children.filter { it.second == tag }
            counts[ordinal] = intArrayOf(i + 1, frame.children.size, sameTag.indexOfFirst { it.first == ordinal } + 1, sameTag.size)
        }
    }
    while (reader.hasNext()) {
        when (reader.next()) {
            EventType.START_ELEMENT -> {
                val ordinal = counts.size
                counts.add(IntArray(4))
                empty.add(true)
                val tag = reader.localName.lowercase()
                (stack.lastOrNull()?.second ?: root).let { it.children.add(ordinal to tag); it.content = true }
                stack.addLast(ordinal to Frame(tag))
            }
            EventType.TEXT, EventType.CDSECT, EventType.ENTITY_REF ->
                if (reader.text.isNotBlank()) stack.lastOrNull()?.second?.content = true
            EventType.END_ELEMENT -> stack.removeLastOrNull()?.let { (ordinal, frame) ->
                empty[ordinal] = !frame.content
                settle(frame)
            }
            else -> {}
        }
    }
    settle(root)
    return counts.mapIndexed { i, c -> ElementFacts(c[0], c[1], c[2], c[3], empty[i]) }
}

internal enum class Combinator { DESCENDANT, CHILD, NEXT_SIBLING, LATER_SIBLING }

/** `[name]`, `[name=value]`, `[name~=value]`… */
internal data class AttributeTest(val name: String, val op: String?, val value: String) {
    fun matches(element: CssElement): Boolean {
        val actual = element.attributes[name] ?: return false
        return when (op) {
            null -> true
            "=" -> actual == value
            "~=" -> actual.split(' ').contains(value)
            "|=" -> actual == value || actual.startsWith("$value-")
            "^=" -> value.isNotEmpty() && actual.startsWith(value)
            "$=" -> value.isNotEmpty() && actual.endsWith(value)
            "*=" -> value.isNotEmpty() && actual.contains(value)
            else -> false
        }
    }
}

/** One `tag#id.class[attr]:pseudo` part of a selector; a null [tag] is `*` or none. */
internal data class Compound(
    val tag: String?,
    val classes: Set<String>,
    val id: String? = null,
    val attributes: List<AttributeTest> = emptyList(),
    /** `:first-child`, `:first-of-type`, `:not(…)` */
    val pseudos: List<(CssElement) -> Boolean> = emptyList(),
) {
    fun matches(element: CssElement) = (tag == null || tag == element.tag) && element.classes.containsAll(classes) &&
        (id == null || id == element.id) && attributes.all { it.matches(element) } && pseudos.all { it(element) }

    val specificity get() = (if (id != null) 100 else 0) + (classes.size + attributes.size + pseudos.size) * 10 + (if (tag != null) 1 else 0)
}

/**
 * [parts] run outermost first, the last one is the element the rule styles.
 * [combinators] has one entry per part after the first: how it relates to the part before.
 */
internal class CssRule(
    val parts: List<Compound>,
    val combinators: List<Combinator>,
    val declarations: Declarations,
    val order: Int,
    /** the browser's own stylesheet, which every book rule outranks */
    val isDefault: Boolean = false,
) {
    val specificity = parts.sumOf { it.specificity }

    /** [path] runs from the outermost open element to the element being styled. */
    fun matches(path: List<CssElement>) = path.isNotEmpty() && matchFrom(parts.lastIndex, path.last(), path.subList(0, path.lastIndex))

    /** Whether [element], inside [ancestors] (outermost first), matches parts up to [part]. */
    private fun matchFrom(part: Int, element: CssElement, ancestors: List<CssElement>): Boolean {
        if (!parts[part].matches(element)) return false
        if (part == 0) return true
        return when (combinators[part - 1]) {
            Combinator.CHILD -> ancestors.isNotEmpty() &&
                matchFrom(part - 1, ancestors.last(), ancestors.subList(0, ancestors.lastIndex))
            Combinator.DESCENDANT -> ancestors.indices.reversed().any { i ->
                matchFrom(part - 1, ancestors[i], ancestors.subList(0, i))
            }
            Combinator.NEXT_SIBLING -> element.previous?.let { matchFrom(part - 1, it, ancestors) } ?: false
            Combinator.LATER_SIBLING -> generateSequence(element.previous) { it.previous }.any { matchFrom(part - 1, it, ancestors) }
        }
    }
}

private fun tagRules(tags: List<String>, declarations: Declarations, order: Int) =
    tags.map { CssRule(listOf(Compound(it, emptySet())), emptyList(), declarations, order) }

/**
 * The parts of a browser's default stylesheet a page shows (WebKit's html.css),
 * which LibreraX leaves in place under a book's own CSS: paragraph and heading
 * margins, heading sizes, lists and blockquotes set in by 40px, monospace
 * `<pre>`. No paragraph indent: a book that wants one says so.
 */
private const val DEFAULT_CSS = """
p, dl, pre { margin-top: 1em; margin-bottom: 1em }
blockquote, figure { margin: 1em 40px }
ul, ol { margin-top: 1em; margin-bottom: 1em; padding-left: 40px; list-style-type: disc }
ol { list-style-type: decimal }
ul ul, ul ol, ol ul, ol ol { margin-top: 0; margin-bottom: 0 }
ul ul, ol ul { list-style-type: circle }
ul ul ul, ul ol ul, ol ul ul, ol ol ul { list-style-type: square }
dd { margin-left: 40px }
h1 { font-size: 2em; margin-top: 0.67em; margin-bottom: 0.67em }
h2 { font-size: 1.5em; margin-top: 0.83em; margin-bottom: 0.83em }
h3 { font-size: 1.17em; margin-top: 1em; margin-bottom: 1em }
h4 { margin-top: 1.33em; margin-bottom: 1.33em }
h5 { font-size: 0.83em; margin-top: 1.67em; margin-bottom: 1.67em }
h6 { font-size: 0.67em; margin-top: 2.33em; margin-bottom: 2.33em }
pre, code, kbd, samp, tt { font-family: monospace }
pre { white-space: pre }
hr { margin-top: 0.5em; margin-bottom: 0.5em }
th { font-weight: bold; text-align: center }
td, th { padding: 1px }
table { border-spacing: 2px; text-indent: 0 }
small, sub, sup { font-size: smaller }
u, ins, abbr[title], acronym[title] { text-decoration: underline }
s, strike, del { text-decoration: line-through }
[hidden] { display: none }
sub { vertical-align: sub }
sup { vertical-align: super }
cite, var, dfn, address { font-style: italic }
center { text-align: -webkit-center }
caption { text-align: -webkit-center }
script, template, noscript, rp { display: none }
big { font-size: larger }
mark { background-color: yellow; color: black }
rt { font-size: 50% }
"""

/** What LibreraX's `css()` sets over a book's own rules: a contents page's links look like links. */
private const val LIBRERAX_CSS = """
nav a[href], ol a[href], ul a[href] { text-decoration: underline !important }
"""

// lazy: it's parsed with values declared further down this file
private val READER_RULES by lazy {
    parseCss(DEFAULT_CSS).map { CssRule(it.parts, it.combinators, it.declarations, it.order, isDefault = true) } +
        // after every book rule of the same weight
        parseCss(LIBRERAX_CSS, firstOrder = Int.MAX_VALUE / 2)
}

// after any rule a book can have
private const val USER_RULE_ORDER = 1_000_000

/** All rules seen so far in a chapter, in document order, with the reader's rules. */
internal class Stylesheet(private val darkScheme: Boolean = false) {
    private val rules = READER_RULES.toMutableList()

    fun add(css: String) {
        parseCss(css, firstOrder = rules.size, darkScheme).forEach { rules.add(it) }
    }

    /** The reader's own CSS: after every sheet of the book in order, so it wins where the weight is equal. */
    fun addUser(css: String) {
        parseCss(css, firstOrder = USER_RULE_ORDER, darkScheme).forEach { rules.add(it) }
    }

    /**
     * The declared style of the last element in [path], cascaded with its inline style. [hints]
     * are what its attributes say (`cellpadding`…), which a browser puts over its own rules and
     * under the book's.
     */
    fun styleFor(path: List<CssElement>, inline: String?, hints: CssStyle = CssStyle()): CssStyle {
        val matching = rules.filter { it.matches(path) }.sortedWith(compareBy({ !it.isDefault }, { it.specificity }, { it.order }))
        val inlineDeclarations = inline?.let(::parseDeclarations) ?: Declarations()
        val (defaults, book) = matching.partition { it.isDefault }
        val normal = defaults.fold(CssStyle()) { acc, rule -> acc.overriddenBy(rule.declarations.normal) }
            .overriddenBy(hints)
            .let { book.fold(it) { acc, rule -> acc.overriddenBy(rule.declarations.normal) } }
            .overriddenBy(inlineDeclarations.normal)
        return matching.fold(normal) { acc, rule -> acc.overriddenBy(rule.declarations.important) }
            .overriddenBy(inlineDeclarations.important)
    }
}

/**
 * `div.a > p + .b ~ [lang]` into its compounds and combinators; null for selectors
 * Baca can't match (pseudo-elements, `:hover`, `:last-child`…), which skips the rule.
 */
private fun parseSelector(selector: String): Pair<List<Compound>, List<Combinator>>? {
    val parts = mutableListOf<Compound>()
    val combinators = mutableListOf<Combinator>()
    var pending: Combinator? = null
    var i = 0
    val text = selector.trim()
    while (i < text.length) {
        val c = text[i]
        val explicit = when (c) {
            '>' -> Combinator.CHILD
            '+' -> Combinator.NEXT_SIBLING
            '~' -> Combinator.LATER_SIBLING
            else -> null
        }
        when {
            c.isWhitespace() -> {
                if (parts.isNotEmpty() && pending == null) pending = Combinator.DESCENDANT
                i++
            }
            explicit != null -> {
                if (parts.isEmpty()) return null
                pending = explicit
                i++
            }
            else -> {
                val end = compoundEnd(text, i)
                val compound = parseCompound(text.substring(i, end)) ?: return null
                if (parts.isNotEmpty()) combinators.add(pending ?: return null)
                pending = null
                parts.add(compound)
                i = end
            }
        }
    }
    return if (parts.isEmpty() || (pending != null && pending != Combinator.DESCENDANT)) null else parts to combinators
}

/** Where the compound starting at [from] ends: the next combinator or space outside brackets and parentheses. */
private fun compoundEnd(text: String, from: Int): Int {
    var depth = 0
    var i = from
    while (i < text.length) {
        val c = text[i]
        when {
            c == '[' || c == '(' -> depth++
            c == ']' || c == ')' -> depth--
            depth == 0 && (c.isWhitespace() || c == '>' || c == '+' || c == '~') -> return i
        }
        i++
    }
    return i
}

private val IDENT = Regex("^-?[a-zA-Z_][a-zA-Z0-9_-]*")
private val ATTRIBUTE = Regex("""^\[\s*(?:[a-zA-Z_*]*\|)?([a-zA-Z_:][-a-zA-Z0-9_:.]*)\s*(?:([~|^$*]?=)\s*(?:"([^"]*)"|'([^']*)'|([^\s\]]+))\s*(?:i\s*)?)?]""")

/** One compound; null when it uses something Baca doesn't match. */
private fun parseCompound(text: String): Compound? {
    var rest = text
    var tag: String? = null
    var id: String? = null
    val classes = mutableSetOf<String>()
    val attributes = mutableListOf<AttributeTest>()
    val pseudos = mutableListOf<(CssElement) -> Boolean>()
    if (rest.startsWith("*")) {
        rest = rest.substring(1)
    } else {
        IDENT.find(rest)?.let {
            tag = it.value.lowercase()
            rest = rest.substring(it.value.length)
        }
    }
    while (rest.isNotEmpty()) {
        when (rest[0]) {
            '.', '#' -> {
                val name = IDENT.find(rest.substring(1))?.value ?: return null
                if (rest[0] == '.') classes.add(name) else id = name
                rest = rest.substring(1 + name.length)
            }
            '[' -> {
                val m = ATTRIBUTE.find(rest) ?: return null
                val value = m.groupValues[3].ifEmpty { m.groupValues[4] }.ifEmpty { m.groupValues[5] }
                attributes.add(AttributeTest(m.groupValues[1].substringAfter(':').lowercase(), m.groupValues[2].ifEmpty { null }, value))
                rest = rest.substring(m.value.length)
            }
            ':' -> {
                val name = IDENT.find(rest.removePrefix(":"))?.value?.lowercase() ?: return null
                val after = rest.removePrefix(":").substring(name.length)
                val argument = if (after.startsWith("(")) after.substring(1, after.indexOf(')').takeIf { it > 0 } ?: return null) else null
                pseudos.add(pseudo(name, argument) ?: return null)
                rest = after.substring(argument?.let { it.length + 2 } ?: 0)
            }
            else -> return null
        }
    }
    return Compound(tag, classes, id, attributes, pseudos)
}

/** The pseudo-classes a page's structure decides; anything else (`:hover`, `::before`) is null. */
private fun pseudo(name: String, argument: String?): ((CssElement) -> Boolean)? = when (name) {
    "first-child" -> { e -> e.previous == null }
    "first-of-type" -> { e -> generateSequence(e.previous) { it.previous }.none { it.tag == e.tag } }
    "last-child" -> { e -> e.facts?.let { it.index == it.count } == true }
    "only-child" -> { e -> e.facts?.count == 1 }
    "last-of-type" -> { e -> e.facts?.let { it.typeIndex == it.typeCount } == true }
    "only-of-type" -> { e -> e.facts?.typeCount == 1 }
    "empty" -> { e -> e.facts?.empty == true }
    "nth-child" -> argument?.let(::nth)?.let { n -> { e -> e.facts?.let { n(it.index) } == true } }
    "nth-last-child" -> argument?.let(::nth)?.let { n -> { e -> e.facts?.let { n(it.count - it.index + 1) } == true } }
    "nth-of-type" -> argument?.let(::nth)?.let { n -> { e -> e.facts?.let { n(it.typeIndex) } == true } }
    "nth-last-of-type" -> argument?.let(::nth)?.let { n -> { e -> e.facts?.let { n(it.typeCount - it.typeIndex + 1) } == true } }
    "not" -> argument?.let { parseCompound(it.trim()) }?.let { inner -> { e -> !inner.matches(e) } }
    "root" -> { e -> e.tag == "html" }
    "link" -> { e -> e.tag == "a" && e.attributes.containsKey("href") }
    else -> null
}

/** `an+b`, `odd`, `even` or a number, as a test of a 1-based position; null when it can't be read. */
private fun nth(argument: String): ((Int) -> Boolean)? {
    val text = argument.replace(" ", "").lowercase()
    val (a, b) = when (text) {
        "odd" -> 2 to 1
        "even" -> 2 to 0
        else -> if ('n' in text) {
            val a = text.substringBefore('n').let { if (it == "" || it == "+") 1 else if (it == "-") -1 else it.toIntOrNull() ?: return null }
            val b = text.substringAfter('n').ifEmpty { "0" }.removePrefix("+").toIntOrNull() ?: return null
            a to b
        } else {
            0 to (text.toIntOrNull() ?: return null)
        }
    }
    return { pos -> if (a == 0) pos == b else (pos - b) % a == 0 && (pos - b) / a >= 0 }
}

internal fun parseCss(css: String, firstOrder: Int = 0, darkScheme: Boolean = false): List<CssRule> {
    val text = css.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
    val rules = mutableListOf<CssRule>()
    var i = 0
    while (i < text.length) {
        val open = text.indexOf('{', i)
        if (open < 0) break
        val prelude = text.substring(i, open).trim()
        val semicolon = text.indexOf(';', i)
        if (prelude.startsWith('@') && semicolon in i until open) {
            // @import / @charset statement: no block
            i = semicolon + 1
            continue
        }
        val close = matchingBrace(text, open)
        if (!prelude.startsWith('@')) {
            val declarations = parseDeclarations(text.substring(open + 1, close))
            prelude.split(',').forEach { selector ->
                parseSelector(selector)?.let { (parts, combinators) ->
                    rules.add(CssRule(parts, combinators, declarations, firstOrder + rules.size))
                }
            }
        } else if (prelude.startsWith("@media", ignoreCase = true) && mediaMatches(prelude.substring(6), darkScheme)) {
            rules += parseCss(text.substring(open + 1, close), firstOrder + rules.size, darkScheme)
        }
        // @font-face / @page blocks, and @media blocks for other media, are skipped whole
        i = close + 1
    }
    return rules
}

/**
 * Whether an `@media` query list holds for a phone's page as LibreraX's WebView
 * sees it: screen media, [MEDIA_WIDTH] by [MEDIA_HEIGHT] CSS px, portrait, and the
 * colour scheme of the reading theme ([darkScheme]), as LibreraX points a book's
 * `prefers-color-scheme` at its paper. A query with a feature Baca can't answer doesn't hold.
 */
internal fun mediaMatches(queryList: String, darkScheme: Boolean = false): Boolean = queryList.split(',').any { query ->
    val q = query.trim().lowercase()
    val negated = q.startsWith("not ")
    val body = q.removePrefix("not ").removePrefix("only ").trim()
    val type = body.substringBefore('(').substringBefore(" and").trim()
    val typeHolds = type.isEmpty() || type == "all" || type == "screen"
    val features = Regex("\\(([^)]*)\\)").findAll(body).map { it.groupValues[1].trim() }.toList()
    val holds = typeHolds && features.all { featureHolds(it, darkScheme) }
    holds != negated
}

private fun featureHolds(feature: String, darkScheme: Boolean): Boolean {
    val name = feature.substringBefore(':').trim()
    val value = feature.substringAfter(':', "").trim()
    fun px() = (cssLength(value) as? CssLength.Px)?.px ?: (cssLength(value) as? CssLength.Em)?.let { it.em * 16f }
    return when (name) {
        "min-width" -> px()?.let { MEDIA_WIDTH >= it } ?: false
        "max-width" -> px()?.let { MEDIA_WIDTH <= it } ?: false
        "min-height" -> px()?.let { MEDIA_HEIGHT >= it } ?: false
        "max-height" -> px()?.let { MEDIA_HEIGHT <= it } ?: false
        "orientation" -> value == "portrait"
        "color" -> true
        "prefers-color-scheme" -> value == if (darkScheme) "dark" else "light"
        else -> false
    }
}

private const val MEDIA_WIDTH = 400f
private const val MEDIA_HEIGHT = 700f

private fun matchingBrace(text: String, open: Int): Int {
    var depth = 0
    for (j in open until text.length) {
        when (text[j]) {
            '{' -> depth++
            '}' -> if (--depth == 0) return j
        }
    }
    return text.length - 1
}

internal fun parseDeclarations(block: String): Declarations {
    var normal = CssStyle()
    var important = CssStyle()
    block.split(';').forEach { declaration ->
        val name = declaration.substringBefore(':', "").trim().lowercase()
        val raw = declaration.substringAfter(':', "")
        val isImportant = raw.contains("!important", ignoreCase = true)
        // as written: a url() is a file name, and those are case-sensitive
        val written = raw.replace(Regex("!\\s*important", RegexOption.IGNORE_CASE), "").trim()
        val value = written.lowercase()
        if (name.isEmpty() || value.isEmpty()) return@forEach
        if (isImportant) important = important.declare(name, value, written) else normal = normal.declare(name, value, written)
    }
    return Declarations(normal, important)
}

private fun CssStyle.declare(name: String, value: String, written: String): CssStyle = when (name) {
    "font-weight" -> copy(bold = weightIsBold(value) ?: bold)
    "font-style" -> copy(italic = value == "italic" || value == "oblique")
    "font-variant", "font-variant-caps" -> copy(smallCaps = value.contains("small-caps"))
    "direction" -> copy(rtl = when (value) { "rtl" -> true; "ltr" -> false; else -> rtl })
    "writing-mode", "-webkit-writing-mode", "-epub-writing-mode" ->
        copy(vertical = if (value.startsWith("vertical") || value == "tb-rl" || value == "tb") true else if (value.startsWith("horizontal") || value == "lr-tb" || value == "lr") false else vertical)
    "text-align" -> copy(
        alignSide = when (value) {
            "left", "-webkit-left" -> "left"
            "right", "-webkit-right" -> "right"
            "center", "-webkit-center" -> "center"
            "end" -> "end"
            else -> "start"
        },
        align = when (value) {
            "center", "-webkit-center" -> TextAlign.CENTER
            "right", "end", "-webkit-right" -> TextAlign.END
            // left/justify: the reader's justification setting decides
            else -> TextAlign.START
        },
        alignLeft = when (value) {
            "left", "start", "-webkit-left" -> true
            "justify" -> false
            else -> null
        },
        pushesBlocks = when (value) {
            "-webkit-center" -> TextAlign.CENTER
            "-webkit-right" -> TextAlign.END
            else -> null
        },
    )
    "text-indent" -> copy(indent = cssLength(value, signed = true) ?: indent)
    "float" -> copy(floatRight = value == "right", floatLeft = value == "left")
    "line-height" -> copy(lineRatio = lineRatioOf(value) ?: lineRatio, linePx = (cssLength(value) as? CssLength.Px)?.px)
    "hyphens", "-webkit-hyphens" -> copy(hyphenate = value != "none" && value != "manual")
    "visibility" -> copy(invisible = value == "hidden" || value == "collapse")
    "word-spacing" -> copy(wordSpacing = if (value == "normal") 0f else (cssLength(value, signed = true) ?: return this).letterEm())
    "display" -> copy(hidden = value == "none", display = value)
    "font-size" -> relativeSize(value)?.let { copy(fontScale = it, fontAbsolute = null, fontKeyword = null) }
        ?: SIZE_KEYWORDS.indexOf(value).takeIf { it >= 0 }?.let { copy(fontKeyword = it, fontScale = null, fontAbsolute = null) }
        ?: absoluteSize(value)?.let { copy(fontAbsolute = it, fontScale = null, fontKeyword = null) }
        ?: this
    "color" -> copy(color = parseColor(value) ?: color)
    "font-family" -> copy(
        face = faceOf(value),
        family = value.substringBefore(',').trim().trim('"', '\'').ifEmpty { null },
        genericMono = value == "monospace",
    )
    "margin" -> boxSides(value, signed = true)?.let { (top, right, bottom, left) ->
        copy(
            marginTop = top ?: marginTop, marginRight = right ?: marginRight,
            marginBottom = bottom ?: marginBottom, marginLeft = left ?: marginLeft,
        )
    } ?: this
    "padding" -> boxSides(value)?.let { (top, right, bottom, left) ->
        copy(
            paddingTop = top ?: paddingTop, paddingRight = right ?: paddingRight,
            paddingBottom = bottom ?: paddingBottom, paddingLeft = left ?: paddingLeft,
        )
    } ?: this
    "margin-left" -> copy(marginLeft = cssLength(value, signed = true) ?: marginLeft)
    "padding-left" -> copy(paddingLeft = cssLength(value) ?: paddingLeft)
    "margin-right" -> copy(marginRight = cssLength(value, signed = true) ?: marginRight)
    "padding-right" -> copy(paddingRight = cssLength(value) ?: paddingRight)
    "border" -> borderOf(value).let { copy(borderTop = it, borderRight = it, borderBottom = it, borderLeft = it) }
    "border-top" -> copy(borderTop = borderOf(value))
    "border-right" -> copy(borderRight = borderOf(value))
    "border-bottom" -> copy(borderBottom = borderOf(value))
    "border-left" -> copy(borderLeft = borderOf(value))
    "border-width" -> boxSidesOf(value) { BorderSide(width = borderWidth(it)) }
    "border-style" -> boxSidesOf(value) { BorderSide(style = it) }
    "border-color" -> boxSidesOf(value) { BorderSide(color = parseColor(it)) }
    "border-top-width", "border-right-width", "border-bottom-width", "border-left-width" ->
        side(name.removeSuffix("-width"), BorderSide(width = borderWidth(value)))
    "border-top-style", "border-right-style", "border-bottom-style", "border-left-style" ->
        side(name.removeSuffix("-style"), BorderSide(style = value))
    "border-top-color", "border-right-color", "border-bottom-color", "border-left-color" ->
        side(name.removeSuffix("-color"), BorderSide(color = parseColor(value)))
    "border-radius" -> copy(radius = cssLength(value.substringBefore(' ').substringBefore('/')) ?: radius)
    "background-color" -> copy(background = backgroundOf(value) ?: background)
    // vertical margins may be negative, and collapse with positive ones as a browser's do
    "margin-top" -> copy(marginTop = cssLength(value, signed = true) ?: marginTop)
    "margin-bottom" -> copy(marginBottom = cssLength(value, signed = true) ?: marginBottom)
    "padding-top" -> copy(paddingTop = cssLength(value) ?: paddingTop)
    "padding-bottom" -> copy(paddingBottom = cssLength(value) ?: paddingBottom)
    "list-style-type" -> copy(listStyle = value)
    "vertical-align" -> if (value.endsWith("%")) {
        value.removeSuffix("%").toFloatOrNull()?.let { copy(baselineLine = it / 100f, baseline = null) } ?: this
    } else {
        copy(
            baseline = baselineOf(value) ?: baseline, baselineLine = null, rowAlign = value.takeIf { it in ROW_ALIGNMENTS },
            shiftKeyword = when (value) {
                "super" -> 1
                "sub" -> -1
                else -> null
            },
        )
    }
    // as a browser's columns read them (probed): only a column break forces one, and a page's
    // avoid doesn't hold in columns; a linked sheet's page breaks are columns' by now (linkedSheet)
    "break-before" -> copy(breakBefore = value == "column", avoidBefore = value in AVOIDED_BREAKS)
    "break-after" -> copy(breakAfter = value == "column", avoidAfter = value in AVOIDED_BREAKS)
    "break-inside" -> copy(avoidInside = value in AVOIDED_BREAKS)
    "page-break-before" -> copy(breakBefore = false, avoidBefore = value == "avoid")
    "page-break-after" -> copy(breakAfter = false, avoidAfter = value == "avoid")
    "page-break-inside" -> copy(avoidInside = value == "avoid")
    "-webkit-column-break-before" -> copy(breakBefore = value == "always", avoidBefore = value == "avoid")
    "-webkit-column-break-after" -> copy(breakAfter = value == "always", avoidAfter = value == "avoid")
    "-webkit-column-break-inside" -> copy(avoidInside = value == "avoid")
    "border-spacing" -> value.split(Regex("\\s+")).mapNotNull { cssLength(it) }.takeIf { it.isNotEmpty() }
        ?.let { copy(borderSpacing = it.first() to it.getOrElse(1) { _ -> it.first() }) } ?: this
    "border-collapse" -> copy(borderCollapse = value == "collapse")
    "text-transform" -> copy(transform = value)
    "white-space" -> copy(keepsSpaces = value.startsWith("pre"))
    "background-image" -> URL.find(written)?.let { copy(backgroundImage = it.groupValues[1].trim('\'', '"', ' ')) } ?: this
    // the shorthand: an image, a colour, or both
    "background" -> copy(
        backgroundImage = URL.find(written)?.groupValues?.get(1)?.trim('\'', '"', ' ') ?: backgroundImage,
        background = value.split(' ').firstNotNullOfOrNull { backgroundOf(it) } ?: background,
    )
    "letter-spacing" -> copy(letterSpacing = if (value == "normal") 0f else (cssLength(value, signed = true) ?: return this).letterEm())
    "list-style" -> value.split(Regex("\\s+")).firstOrNull { it in LIST_STYLES }?.let { copy(listStyle = it) } ?: this
    "width" -> copy(width = cssLength(value))
    "height" -> copy(height = cssLength(value))
    "max-width" -> copy(maxWidth = cssLength(value))
    "min-width" -> copy(minWidth = cssLength(value))
    "column-count", "-webkit-column-count", "-moz-column-count" -> copy(columnCount = value.toIntOrNull() ?: 1)
    "column-gap", "-webkit-column-gap", "-moz-column-gap" -> copy(columnGap = cssLength(value))
    "text-decoration", "text-decoration-line" -> copy(underline = value.contains("underline"), lineThrough = value.contains("line-through"))
    else -> this
}

/** Top, right, bottom and left of a `margin`/`padding` shorthand (1–4 values). */
private fun boxSides(value: String, signed: Boolean = false): List<CssLength?>? {
    val v = value.split(Regex("\\s+")).map { cssLength(it, signed) }
    return when (v.size) {
        1 -> listOf(v[0], v[0], v[0], v[0])
        2 -> listOf(v[0], v[1], v[0], v[1])
        3 -> listOf(v[0], v[1], v[2], v[1])
        4 -> v
        else -> null
    }
}

/** A `color` value as 0xAARRGGBB; [TEXT_COLOR] for the text's own colour; null for what Baca can't read. */
internal fun parseColor(value: String): Long? {
    if (value == "inherit" || value == "currentcolor") return TEXT_COLOR
    if (value.startsWith("#")) {
        val hex = value.substring(1)
        val full = when (hex.length) {
            3, 4 -> hex.map { "$it$it" }.joinToString("")
            6, 8 -> hex
            else -> return null
        }
        val rgb = full.substring(0, 6).toLongOrNull(16) ?: return null
        val alpha = if (full.length == 8) full.substring(6).toLongOrNull(16) ?: return null else 0xFF
        return (alpha shl 24) or rgb
    }
    if (value.startsWith("rgb")) {
        val parts = value.substringAfter('(').substringBefore(')').split(',', ' ', '/').filter { it.isNotBlank() }
        if (parts.size < 3) return null
        val channels = parts.take(3).map { p ->
            val n = if (p.endsWith("%")) p.removeSuffix("%").toFloatOrNull()?.times(2.55f) else p.toFloatOrNull()
            n?.toInt()?.coerceIn(0, 255)?.toLong() ?: return null
        }
        return (0xFFL shl 24) or (channels[0] shl 16) or (channels[1] shl 8) or channels[2]
    }
    return NAMED_COLORS[value]?.let { (0xFFL shl 24) or it }
}

/** `color: inherit` or `currentColor`: the text's own colour, which is the theme's ink unless a parent sets one. */
const val TEXT_COLOR = -1L

private val NAMED_COLORS = mapOf(
    "black" to 0x000000L, "white" to 0xFFFFFFL, "red" to 0xFF0000L, "green" to 0x008000L, "blue" to 0x0000FFL,
    "gray" to 0x808080L, "grey" to 0x808080L, "silver" to 0xC0C0C0L, "maroon" to 0x800000L, "navy" to 0x000080L,
    "purple" to 0x800080L, "teal" to 0x008080L, "olive" to 0x808000L, "yellow" to 0xFFFF00L, "orange" to 0xFFA500L,
    "brown" to 0xA52A2AL, "darkred" to 0x8B0000L, "darkblue" to 0x00008BL, "darkgreen" to 0x006400L,
    "darkgray" to 0xA9A9A9L, "darkgrey" to 0xA9A9A9L, "dimgray" to 0x696969L, "dimgrey" to 0x696969L,
)

/** `line-height` as a ratio of the font size: a number, em, %, or `normal` ([NORMAL_LINE], the font's own). */
private fun lineRatioOf(value: String): Float? = when (value) {
    "normal" -> NORMAL_LINE
    else -> value.toFloatOrNull() ?: when (val length = cssLength(value)) {
        is CssLength.Em -> length.em
        is CssLength.Percent -> length.percent / 100f
        else -> null
    }
}

/** `line-height: normal`, which only the layout can measure: the font's ascent and descent ([com.baca.shared.render.ReflowLayout.lineRatioOf]). */
const val NORMAL_LINE = -1f

/** One side's border as declared; null parts are unset, and a style of `none` draws nothing. */
data class BorderSide(val width: Float? = null, val style: String? = null, val color: Long? = null) {
    fun overriddenBy(other: BorderSide) = BorderSide(other.width ?: width, other.style ?: style, other.color ?: color)

    /** Drawn width in CSS px: nothing without a style, as in CSS (`border-width` alone draws nothing). */
    val drawnPx: Float get() = if (style == null || style == "none" || style == "hidden") 0f else width ?: MEDIUM_BORDER
}

private const val MEDIUM_BORDER = 3f

/** A `border` shorthand: width, style and colour in any order. */
private fun borderOf(value: String): BorderSide {
    if (value == "none" || value == "0") return BorderSide(width = 0f, style = "none")
    var side = BorderSide(width = MEDIUM_BORDER, style = "none")
    value.replace(Regex(",\\s*"), ",").split(' ').filter { it.isNotEmpty() }.forEach { part ->
        when {
            part in BORDER_STYLES -> side = side.copy(style = part)
            borderWidth(part) != null -> side = side.copy(width = borderWidth(part))
            else -> parseColor(part)?.let { side = side.copy(color = it) }
        }
    }
    return side
}

private val BORDER_STYLES = setOf("none", "hidden", "solid", "dotted", "dashed", "double", "groove", "ridge", "inset", "outset")

private fun borderWidth(value: String): Float? = when (value) {
    "thin" -> 1f
    "medium" -> MEDIUM_BORDER
    "thick" -> 5f
    else -> when (val length = cssLength(value)) {
        is CssLength.Px -> length.px
        is CssLength.Em -> length.em * 16f
        else -> null
    }
}

/** A `background-color`: a colour, or [TRANSPARENT] for `transparent`/`none`; null for what isn't one (`inherit`, an image). */
private fun backgroundOf(value: String): Long? = when (value) {
    "transparent", "none", "initial" -> TRANSPARENT
    "inherit", "currentcolor" -> null
    else -> parseColor(value)?.takeIf { it != TEXT_COLOR }
}

/** No background. */
const val TRANSPARENT = 0L

/** The four sides of a `border-width`/`-style`/`-color` shorthand, each read by [read]. */
private fun CssStyle.boxSidesOf(value: String, read: (String) -> BorderSide): CssStyle {
    val v = value.replace(Regex(",\\s*"), ",").split(' ').filter { it.isNotEmpty() }
    val (top, right, bottom, left) = when (v.size) {
        1 -> listOf(v[0], v[0], v[0], v[0])
        2 -> listOf(v[0], v[1], v[0], v[1])
        3 -> listOf(v[0], v[1], v[2], v[1])
        4 -> v
        else -> return this
    }
    return copy(
        borderTop = borderTop.overriddenBy(read(top)), borderRight = borderRight.overriddenBy(read(right)),
        borderBottom = borderBottom.overriddenBy(read(bottom)), borderLeft = borderLeft.overriddenBy(read(left)),
    )
}

private fun CssStyle.side(name: String, part: BorderSide): CssStyle = when (name) {
    "border-top" -> copy(borderTop = borderTop.overriddenBy(part))
    "border-right" -> copy(borderRight = borderRight.overriddenBy(part))
    "border-bottom" -> copy(borderBottom = borderBottom.overriddenBy(part))
    else -> copy(borderLeft = borderLeft.overriddenBy(part))
}

private val URL = Regex("url\\(([^)]*)\\)")

/**
 * How far `vertical-align` moves text, in em of its font; WebKit raises `super` by a
 * third of an em. `top`, `middle` and `bottom` align a table cell or a picture in
 * its row, and leave the text on its baseline.
 */
private fun baselineOf(value: String): Float? = when {
    value == "super" -> SUPER_SHIFT
    value == "sub" -> SUB_SHIFT
    value in ROW_ALIGNMENTS -> 0f
    // a length: in em of the element's own font
    else -> when (val length = cssLength(value, signed = true)) {
        is CssLength.Em -> length.em
        is CssLength.Px -> length.px / 16f
        else -> null
    }
}

private val AVOIDED_BREAKS = setOf("avoid", "avoid-column")

// Baca's own units for a linked sheet's vh and vw (linkedSheet), which foliate turns into the window's px
private const val WINDOW_VH = "-window-vh"
private const val WINDOW_VW = "-window-vw"

/**
 * A linked stylesheet as foliate hands it to the page: `-epub-` prefixes dropped (`-epub-hyphens`
 * is `hyphens`), `vh` and `vw` made lengths of the whole
 * window rather than the page, and its page breaks turned into column breaks (`page-break-*:`
 * into `-webkit-column-break-*:`, `break-*: page` into `column`). A `<style>` element and a
 * `style` attribute keep what they wrote.
 */
internal fun linkedSheet(css: String): String = css
    .replace(Regex("""(?<=[{\s;])-epub-""", RegexOption.IGNORE_CASE), "")
    .replace(Regex("""(\d*\.?\d+)vw""", RegexOption.IGNORE_CASE)) { it.groupValues[1] + WINDOW_VW }
    .replace(Regex("""(\d*\.?\d+)vh""", RegexOption.IGNORE_CASE)) { it.groupValues[1] + WINDOW_VH }
    .replace(Regex("""page-break-(after|before|inside)\s*:""", RegexOption.IGNORE_CASE)) { "-webkit-column-break-${it.groupValues[1]}:" }
    .replace(Regex("""break-(after|before|inside)\s*:\s*(avoid-)?page""", RegexOption.IGNORE_CASE)) { "break-${it.groupValues[1]}: ${it.groupValues[2]}column" }

private val ROW_ALIGNMENTS = setOf("baseline", "top", "text-top", "middle", "bottom", "text-bottom")

private const val SUPER_SHIFT = 0.33f
private const val SUB_SHIFT = -0.2f

private fun CssLength.letterEm(): Float = when (this) {
    is CssLength.Em -> em
    is CssLength.Px -> px / 16f
    else -> 0f
}

/** The `list-style-type` keywords Baca draws a marker for, or `none`. */
private val LIST_STYLES = setOf(
    "none", "disc", "circle", "square", "decimal", "lower-alpha", "upper-alpha", "lower-latin", "upper-latin", "lower-roman", "upper-roman",
)

/**
 * A `width` or `height` attribute as HTML reads it: a number is CSS px and one followed
 * by `%` a percentage, whatever comes after (`300`, `300px`, `50%`); anything else is null.
 */
internal fun attrLength(value: String): CssLength? {
    val match = Regex("""^\s*(\d+(?:\.\d+)?)\s*(%?)""").find(value) ?: return null
    val number = match.groupValues[1].toFloat()
    return if (match.groupValues[2] == "%") CssLength.Percent(number) else CssLength.Px(number)
}

/** A length; `auto`, `none`, anything unknown and (unless [signed]) negatives are null, the browser's default. */
internal fun cssLength(value: String, signed: Boolean = false): CssLength? {
    fun number(suffix: String) = value.removeSuffix(suffix).trim().toFloatOrNull()?.takeIf { signed || it >= 0f }
    return when {
        value == "auto" -> CssLength.Auto
        value.endsWith("rem") -> number("rem")?.let { CssLength.Em(it) }
        value.endsWith("em") -> number("em")?.let { CssLength.Em(it) }
        value.endsWith("px") -> number("px")?.let { CssLength.Px(it) }
        value.endsWith("pt") -> number("pt")?.let { CssLength.Px(it * 4f / 3f) }
        value.endsWith("%") -> number("%")?.let { CssLength.Percent(it) }
        value.endsWith(WINDOW_VH) -> number(WINDOW_VH)?.let { CssLength.WindowVh(it) }
        value.endsWith(WINDOW_VW) -> number(WINDOW_VW)?.let { CssLength.WindowVw(it) }
        value.endsWith("vh") -> number("vh")?.let { CssLength.Vh(it) }
        value.endsWith("vw") -> number("vw")?.let { CssLength.Vw(it) }
        else -> value.toFloatOrNull()?.takeIf { it == 0f }?.let { CssLength.Px(0f) }
    }
}


private fun weightIsBold(value: String): Boolean? = when (value) {
    "bold", "bolder" -> true
    "normal", "lighter" -> false
    else -> value.toIntOrNull()?.let { it >= 600 }
}

/** A font the book ships, from an `@font-face` rule: its [family] (lowercased), the file's zip [path], weight and style. */
data class BookFont(val family: String, val path: String, val weight: Int, val italic: Boolean)

/** The `@font-face` rules in [css], each font's `url()` resolved by [resolve] to a path in the book. */
fun fontFacesOf(css: String, resolve: (String) -> String): List<BookFont> =
    Regex("@font-face\\s*\\{([^}]*)\\}", RegexOption.IGNORE_CASE).findAll(css).mapNotNull { rule ->
        val declarations = rule.groupValues[1].split(';').associate { d ->
            d.substringBefore(':', "").trim().lowercase() to d.substringAfter(':', "").trim()
        }
        val family = declarations["font-family"]?.trim('"', '\'')?.lowercase()?.ifEmpty { null } ?: return@mapNotNull null
        val url = Regex("url\\(\\s*['\"]?([^'\")]+)['\"]?\\s*\\)").find(declarations["src"] ?: "")?.groupValues?.get(1)
            ?: return@mapNotNull null
        val weight = when (val w = declarations["font-weight"]?.lowercase()) {
            null, "normal" -> 400
            "bold" -> 700
            else -> w.toIntOrNull() ?: 400
        }
        val italic = declarations["font-style"]?.lowercase().let { it == "italic" || it == "oblique" }
        BookFont(family, resolve(url), weight, italic)
    }.toList()

/** The kind of face a `font-family` list starts with; a font the reader doesn't have is read as serif. */
private fun faceOf(value: String): BookFace {
    val first = value.substringBefore(',').trim().trim('"', '\'')
    return when {
        "mono" in first || first == "courier" || first == "courier new" -> BookFace.MONO
        "sans" in first || first in SANS_FONTS -> BookFace.SANS
        else -> BookFace.SERIF
    }
}

private val SANS_FONTS = setOf("helvetica", "helvetica neue", "arial", "verdana", "roboto")

/** A size relative to the parent's: `em`, `%`, `smaller`, `larger`. */
private fun relativeSize(value: String): Float? = when {
    value.endsWith("em") && !value.endsWith("rem") -> value.removeSuffix("em").toFloatOrNull()
    value.endsWith("%") -> value.removeSuffix("%").toFloatOrNull()?.div(100f)
    value == "smaller" -> 1f / 1.2f
    value == "larger" -> 1.2f
    else -> null
}

/** A size that doesn't depend on the parent's, relative to the browser's 16px: `px`, `pt`, `rem` */
private fun absoluteSize(value: String): Float? = when {
    value.endsWith("rem") -> value.removeSuffix("rem").toFloatOrNull()
    value.endsWith("px") -> value.removeSuffix("px").toFloatOrNull()?.div(16f)
    value.endsWith("pt") -> value.removeSuffix("pt").toFloatOrNull()?.div(12f)
    else -> null
}?.takeIf { it > 0f }

internal val SIZE_KEYWORDS = listOf("xx-small", "x-small", "small", "medium", "large", "x-large", "xx-large", "xxx-large")

// Chromium's sizes for them in CSS px (probed), and for plain monospace, whose medium is 13px
internal val KEYWORD_PX = listOf(9f, 10f, 13f, 16f, 18f, 24f, 32f, 48f)
internal val MONOSPACE_KEYWORD_PX = listOf(9f, 10f, 12f, 13f, 16f, 20f, 26f, 39f)
