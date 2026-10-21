package com.baca.shared.parser

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
    /** relative to the browser's 16px, whatever the parent's: `20px`, `1.2rem`, `large` */
    val fontAbsolute: Float? = null,
    /** `color` as 0xAARRGGBB, or [TEXT_COLOR] for `inherit`/`currentColor` */
    val color: Long? = null,
    val underline: Boolean? = null,
    /** the generic face `font-family` names first */
    val face: BookFace? = null,
    /** margins and padding; not inherited */
    val marginTop: CssLength? = null,
    val marginBottom: CssLength? = null,
    val paddingTop: CssLength? = null,
    val paddingBottom: CssLength? = null,
    val marginLeft: CssLength? = null,
    val paddingLeft: CssLength? = null,
    val marginRight: CssLength? = null,
    val paddingRight: CssLength? = null,
    /** `border-left`: a vertical rule beside the element (Strange Buildings' recollections) */
    val borderLeft: Boolean? = null,
    /** `list-style-type`, inherited by list items: disc, decimal, none… */
    val listStyle: String? = null,
    /** `vertical-align` raising (positive) or lowering text, in em of its font */
    val baseline: Float? = null,
    /** `text-transform`: uppercase, lowercase, capitalize or none */
    val transform: String? = null,
    /** `letter-spacing` in em; 0 for `normal` */
    val letterSpacing: Float? = null,
    /** `white-space` keeps the text's spaces and line breaks (`pre`, `pre-wrap`, `pre-line`) */
    val keepsSpaces: Boolean? = null,
    /** `border: none` (or 0) on an element a browser would draw a border on, like `<hr>` */
    val noBorder: Boolean? = null,
    /** `background-image: url(…)`, as the book writes it */
    val backgroundImage: String? = null,
    /** box sizes as the book writes them; images and blocks use them */
    val width: CssLength? = null,
    val height: CssLength? = null,
    val maxWidth: CssLength? = null,
) {
    /** [other]'s set values win. */
    fun overriddenBy(other: CssStyle) = CssStyle(
        bold = other.bold ?: bold,
        italic = other.italic ?: italic,
        smallCaps = other.smallCaps ?: smallCaps,
        align = other.align ?: align,
        indent = other.indent ?: indent,
        floatRight = other.floatRight ?: floatRight,
        floatLeft = other.floatLeft ?: floatLeft,
        lineRatio = other.lineRatio ?: lineRatio,
        hidden = other.hidden ?: hidden,
        display = other.display ?: display,
        // the later of a relative and an absolute size wins, as one font-size declaration
        fontScale = if (other.fontScale != null || other.fontAbsolute != null) other.fontScale else fontScale,
        fontAbsolute = if (other.fontScale != null || other.fontAbsolute != null) other.fontAbsolute else fontAbsolute,
        color = other.color ?: color,
        underline = other.underline ?: underline,
        face = other.face ?: face,
        marginTop = other.marginTop ?: marginTop,
        marginBottom = other.marginBottom ?: marginBottom,
        paddingTop = other.paddingTop ?: paddingTop,
        paddingBottom = other.paddingBottom ?: paddingBottom,
        borderLeft = other.borderLeft ?: borderLeft,
        marginLeft = other.marginLeft ?: marginLeft,
        paddingLeft = other.paddingLeft ?: paddingLeft,
        marginRight = other.marginRight ?: marginRight,
        paddingRight = other.paddingRight ?: paddingRight,
        listStyle = other.listStyle ?: listStyle,
        baseline = other.baseline ?: baseline,
        transform = other.transform ?: transform,
        letterSpacing = other.letterSpacing ?: letterSpacing,
        keepsSpaces = other.keepsSpaces ?: keepsSpaces,
        noBorder = other.noBorder ?: noBorder,
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
)

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
th { font-weight: bold }
small, sub, sup { font-size: smaller }
sub { vertical-align: sub }
sup { vertical-align: super }
cite, var, dfn, address { font-style: italic }
center { text-align: center }
"""

/** What LibreraX's `css()` sets over a book's own rules: a contents page's links look like links. */
private const val LIBRERAX_CSS = """
nav a, ol a, ul a { text-decoration: underline !important }
"""

// lazy: it's parsed with values declared further down this file
private val READER_RULES by lazy {
    parseCss(DEFAULT_CSS).map { CssRule(it.parts, it.combinators, it.declarations, it.order, isDefault = true) } +
        // after every book rule of the same weight
        parseCss(LIBRERAX_CSS, firstOrder = Int.MAX_VALUE / 2)
}

/** All rules seen so far in a chapter, in document order, with the reader's rules. */
internal class Stylesheet {
    private val rules = READER_RULES.toMutableList()

    fun add(css: String) {
        parseCss(css, firstOrder = rules.size).forEach { rules.add(it) }
    }

    /** The declared style of the last element in [path], cascaded with its inline style. */
    fun styleFor(path: List<CssElement>, inline: String?): CssStyle {
        val matching = rules.filter { it.matches(path) }.sortedWith(compareBy({ !it.isDefault }, { it.specificity }, { it.order }))
        val inlineDeclarations = inline?.let(::parseDeclarations) ?: Declarations()
        val normal = matching.fold(CssStyle()) { acc, rule -> acc.overriddenBy(rule.declarations.normal) }
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

/** The pseudo-classes a page's structure decides; anything else (`:hover`, `::before`, `:last-child`) is null. */
private fun pseudo(name: String, argument: String?): ((CssElement) -> Boolean)? = when (name) {
    "first-child" -> { e -> e.previous == null }
    "nth-child" -> if (argument?.trim() == "1") { e -> e.previous == null } else null
    "first-of-type" -> { e -> generateSequence(e.previous) { it.previous }.none { it.tag == e.tag } }
    "not" -> argument?.let { parseCompound(it.trim()) }?.let { inner -> { e -> !inner.matches(e) } }
    "root" -> { e -> e.tag == "html" }
    "link" -> { e -> e.tag == "a" && e.attributes.containsKey("href") }
    else -> null
}

internal fun parseCss(css: String, firstOrder: Int = 0): List<CssRule> {
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
        }
        // @media / @font-face / @page blocks are skipped whole
        i = close + 1
    }
    return rules
}

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
    "text-align" -> copy(
        align = when (value) {
            "center" -> TextAlign.CENTER
            "right", "end" -> TextAlign.END
            // left/justify: the reader's justification setting decides
            else -> TextAlign.START
        },
    )
    "text-indent" -> copy(indent = cssLength(value, signed = true) ?: indent)
    "float" -> copy(floatRight = value == "right", floatLeft = value == "left")
    "line-height" -> copy(
        lineRatio = value.toFloatOrNull() ?: (cssLength(value) as? CssLength.Em)?.em
            ?: (cssLength(value) as? CssLength.Percent)?.let { it.percent / 100f } ?: lineRatio,
    )
    "display" -> copy(hidden = value == "none", display = value)
    "font-size" -> relativeSize(value)?.let { copy(fontScale = it, fontAbsolute = null) }
        ?: absoluteSize(value)?.let { copy(fontAbsolute = it, fontScale = null) }
        ?: this
    "color" -> copy(color = parseColor(value) ?: color)
    "font-family" -> copy(face = faceOf(value))
    "margin" -> boxSides(value)?.let { (top, right, bottom, left) ->
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
    "margin-left" -> copy(marginLeft = cssLength(value) ?: marginLeft)
    "padding-left" -> copy(paddingLeft = cssLength(value) ?: paddingLeft)
    "margin-right" -> copy(marginRight = cssLength(value) ?: marginRight)
    "padding-right" -> copy(paddingRight = cssLength(value) ?: paddingRight)
    "border-left" -> copy(borderLeft = "none" !in value && !value.startsWith("0 ") && value != "0")
    "margin-top" -> copy(marginTop = cssLength(value) ?: marginTop)
    "margin-bottom" -> copy(marginBottom = cssLength(value) ?: marginBottom)
    "padding-top" -> copy(paddingTop = cssLength(value) ?: paddingTop)
    "padding-bottom" -> copy(paddingBottom = cssLength(value) ?: paddingBottom)
    "list-style-type" -> copy(listStyle = value)
    "vertical-align" -> copy(baseline = baselineOf(value) ?: baseline)
    "text-transform" -> copy(transform = value)
    "white-space" -> copy(keepsSpaces = value.startsWith("pre"))
    "border", "border-style", "border-top" -> copy(noBorder = value == "none" || value == "0" || value.startsWith("0 ") || value.startsWith("none"))
    "background", "background-image" -> URL.find(written)?.let { copy(backgroundImage = it.groupValues[1].trim('\'', '"', ' ')) } ?: this
    "letter-spacing" -> copy(letterSpacing = if (value == "normal") 0f else (cssLength(value, signed = true) ?: return this).letterEm())
    "list-style" -> value.split(Regex("\\s+")).firstOrNull { it in LIST_STYLES }?.let { copy(listStyle = it) } ?: this
    "width" -> copy(width = cssLength(value))
    "height" -> copy(height = cssLength(value))
    "max-width" -> copy(maxWidth = cssLength(value))
    "text-decoration", "text-decoration-line" -> copy(underline = value.contains("underline"))
    else -> this
}

/** Top, right, bottom and left of a `margin`/`padding` shorthand (1–4 values). */
private fun boxSides(value: String): List<CssLength?>? {
    val v = value.split(Regex("\\s+")).map { cssLength(it) }
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

private val URL = Regex("url\\(([^)]*)\\)")

/** How far `vertical-align` moves text, in em of its font; WebKit raises `super` by a third of an em. */
private fun baselineOf(value: String): Float? = when {
    value == "super" || value == "top" || value == "text-top" -> SUPER_SHIFT
    value == "sub" || value == "bottom" || value == "text-bottom" -> SUB_SHIFT
    value == "baseline" || value == "middle" -> 0f
    // a percentage or length: its direction is what a reader sees
    value.startsWith("-") -> SUB_SHIFT
    value.first().isDigit() -> if (value.trimStart('0', '.').isEmpty()) 0f else SUPER_SHIFT
    else -> null
}

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
    value == "smaller" -> 0.83f
    value == "larger" -> 1.2f
    else -> null
}

/** A size that doesn't depend on the parent's, relative to the browser's 16px: `px`, `pt`, `rem`, `small`… */
private fun absoluteSize(value: String): Float? = when {
    value.endsWith("rem") -> value.removeSuffix("rem").toFloatOrNull()
    value.endsWith("px") -> value.removeSuffix("px").toFloatOrNull()?.div(16f)
    value.endsWith("pt") -> value.removeSuffix("pt").toFloatOrNull()?.div(12f)
    else -> ABSOLUTE_KEYWORDS[value]
}?.takeIf { it > 0f }

private val ABSOLUTE_KEYWORDS = mapOf(
    "xx-small" to 0.6f, "x-small" to 0.75f, "small" to 0.89f, "medium" to 1f,
    "large" to 1.2f, "x-large" to 1.5f, "xx-large" to 2f,
)
