package com.baca.shared.parser

/**
 * The part of a book's CSS that Baca applies (TECH 3.1), cascaded as in a
 * browser, which is how LibreraX shows a book: the book's rules win, by
 * `!important`, specificity and order, over a few reader defaults
 * (`READER_RULES`). The reader's font, size and line spacing are not CSS here;
 * colours, horizontal margins and absolute font sizes are ignored.
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
    val noIndent: Boolean? = null,
    /** negative text-indent: the first line hangs out, the rest are indented */
    val hanging: Boolean? = null,
    val floatRight: Boolean? = null,
    val hidden: Boolean? = null,
    /** relative to the parent element: 1.2 for `1.2em` or `120%` */
    val fontScale: Float? = null,
    /** the element sets its own `color`, which for a link means it isn't drawn as one */
    val ownColor: Boolean? = null,
    val underline: Boolean? = null,
    /** the generic face `font-family` names first */
    val face: BookFace? = null,
    /** vertical margins and padding in em; not inherited */
    val marginTop: Float? = null,
    val marginBottom: Float? = null,
    val paddingTop: Float? = null,
    val paddingBottom: Float? = null,
    /** `border-left`: a vertical rule beside the element (Strange Buildings' recollections) */
    val borderLeft: Boolean? = null,
    /** left margin and padding in em; only an element with a left border uses them */
    val marginLeft: Float? = null,
    val paddingLeft: Float? = null,
    /** `height` in em; an image this short sits in its line of text */
    val heightEm: Float? = null,
) {
    /** [other]'s set values win. */
    fun overriddenBy(other: CssStyle) = CssStyle(
        bold = other.bold ?: bold,
        italic = other.italic ?: italic,
        smallCaps = other.smallCaps ?: smallCaps,
        align = other.align ?: align,
        noIndent = other.noIndent ?: noIndent,
        hanging = other.hanging ?: hanging,
        floatRight = other.floatRight ?: floatRight,
        hidden = other.hidden ?: hidden,
        fontScale = other.fontScale ?: fontScale,
        ownColor = other.ownColor ?: ownColor,
        underline = other.underline ?: underline,
        face = other.face ?: face,
        marginTop = other.marginTop ?: marginTop,
        marginBottom = other.marginBottom ?: marginBottom,
        paddingTop = other.paddingTop ?: paddingTop,
        paddingBottom = other.paddingBottom ?: paddingBottom,
        borderLeft = other.borderLeft ?: borderLeft,
        marginLeft = other.marginLeft ?: marginLeft,
        paddingLeft = other.paddingLeft ?: paddingLeft,
        heightEm = other.heightEm ?: heightEm,
    )
}

/** A declaration block split by `!important`, which outranks every normal declaration. */
internal data class Declarations(val normal: CssStyle = CssStyle(), val important: CssStyle = CssStyle())

/** An element as selectors see it, with the sibling element just before it. */
internal class CssElement(val tag: String, val classes: Set<String>, val previous: CssElement? = null)

internal enum class Combinator { DESCENDANT, CHILD, NEXT_SIBLING }

/** One `tag.class.other` part of a selector. */
internal data class Compound(val tag: String?, val classes: Set<String>) {
    fun matches(element: CssElement) = (tag == null || tag == element.tag) && element.classes.containsAll(classes)
}

/**
 * [parts] run outermost first, the last one is the element the rule styles.
 * [combinators] has one entry per part after the first: how it relates to the part before.
 */
internal class CssRule(val parts: List<Compound>, val combinators: List<Combinator>, val declarations: Declarations, val order: Int) {
    val specificity = parts.sumOf { it.classes.size * 10 + (if (it.tag != null) 1 else 0) }

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
        }
    }
}

private fun tagRules(tags: List<String>, declarations: Declarations, order: Int) =
    tags.map { CssRule(listOf(Compound(it, emptySet())), emptyList(), declarations, order) }

/**
 * What LibreraX adds to a book's own CSS (FoliateCss in LibreraReader):
 * headings centred before the book's rules, so any book rule changes that,
 * and the reader's link colour after them, unless a book rule outranks it.
 * Where the book sets nothing, the reader's indent and paragraph gap apply.
 */
private val READER_RULES =
    tagRules(listOf("h1", "h2", "h3", "h4", "h5", "h6"), Declarations(normal = CssStyle(align = TextAlign.CENTER)), Int.MIN_VALUE) +
        tagRules(listOf("a"), Declarations(important = CssStyle(ownColor = false)), Int.MAX_VALUE)

/** All rules seen so far in a chapter, in document order, with the reader's rules. */
internal class Stylesheet {
    private val rules = READER_RULES.toMutableList()

    fun add(css: String) {
        parseCss(css, firstOrder = rules.size).forEach { rules.add(it) }
    }

    /** The declared style of the last element in [path], cascaded with its inline style. */
    fun styleFor(path: List<CssElement>, inline: String?): CssStyle {
        val matching = rules.filter { it.matches(path) }.sortedWith(compareBy({ it.specificity }, { it.order }))
        val inlineDeclarations = inline?.let(::parseDeclarations) ?: Declarations()
        val normal = matching.fold(CssStyle()) { acc, rule -> acc.overriddenBy(rule.declarations.normal) }
            .overriddenBy(inlineDeclarations.normal)
        return matching.fold(normal) { acc, rule -> acc.overriddenBy(rule.declarations.important) }
            .overriddenBy(inlineDeclarations.important)
    }
}

private val COMPOUND = Regex("^([a-zA-Z][a-zA-Z0-9]*)?((?:\\.[a-zA-Z0-9_-]+)*)$")

/** `div.a > p + .b` into its compounds and combinators; null for selectors Baca doesn't support. */
private fun parseSelector(selector: String): Pair<List<Compound>, List<Combinator>>? {
    val tokens = selector.replace(">", " > ").replace("+", " + ").trim().split(Regex("\\s+"))
    val parts = mutableListOf<Compound>()
    val combinators = mutableListOf<Combinator>()
    var pending: Combinator? = null
    for (token in tokens) {
        val explicit = when (token) {
            ">" -> Combinator.CHILD
            "+" -> Combinator.NEXT_SIBLING
            else -> null
        }
        if (explicit != null) {
            if (parts.isEmpty() || pending != null) return null
            pending = explicit
            continue
        }
        val m = COMPOUND.matchEntire(token)?.takeIf { token.isNotEmpty() } ?: return null
        if (parts.isNotEmpty()) combinators.add(pending ?: Combinator.DESCENDANT)
        pending = null
        parts.add(Compound(m.groupValues[1].lowercase().ifEmpty { null }, m.groupValues[2].split('.').filter { it.isNotEmpty() }.toSet()))
    }
    return if (parts.isEmpty() || pending != null) null else parts to combinators
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
        val raw = declaration.substringAfter(':', "").lowercase()
        val value = raw.replace("!important", "").trim()
        if (name.isEmpty() || value.isEmpty()) return@forEach
        if ("!important" in raw) important = important.declare(name, value) else normal = normal.declare(name, value)
    }
    return Declarations(normal, important)
}

private fun CssStyle.declare(name: String, value: String): CssStyle = when (name) {
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
    "text-indent" -> value.startsWith("-").let { hanging ->
        copy(noIndent = hanging || cssLengthIsZero(value), hanging = hanging)
    }
    "float" -> copy(floatRight = value == "right")
    "display" -> copy(hidden = value == "none")
    "font-size" -> copy(fontScale = relativeSize(value) ?: fontScale)
    "color" -> copy(ownColor = true)
    "font-family" -> copy(face = faceOf(value))
    "margin" -> boxSides(value)?.let { (top, bottom, left) ->
        copy(marginTop = top ?: marginTop, marginBottom = bottom ?: marginBottom, marginLeft = left ?: marginLeft)
    } ?: this
    "padding" -> boxSides(value)?.let { (top, bottom, left) ->
        copy(paddingTop = top ?: paddingTop, paddingBottom = bottom ?: paddingBottom, paddingLeft = left ?: paddingLeft)
    } ?: this
    "margin-left" -> copy(marginLeft = lengthEm(value) ?: marginLeft)
    "padding-left" -> copy(paddingLeft = lengthEm(value) ?: paddingLeft)
    "border-left" -> copy(borderLeft = "none" !in value && !value.startsWith("0 ") && value != "0")
    "margin-top" -> copy(marginTop = lengthEm(value) ?: marginTop)
    "margin-bottom" -> copy(marginBottom = lengthEm(value) ?: marginBottom)
    "padding-top" -> copy(paddingTop = lengthEm(value) ?: paddingTop)
    "padding-bottom" -> copy(paddingBottom = lengthEm(value) ?: paddingBottom)
    "height" -> copy(heightEm = lengthEm(value) ?: heightEm)
    "text-decoration", "text-decoration-line" -> copy(underline = value.contains("underline"))
    else -> this
}

/** Top, bottom and left of a `margin`/`padding` shorthand (1–4 values), in em. */
private fun boxSides(value: String): List<Float?>? {
    val v = value.split(Regex("\\s+")).map(::lengthEm)
    return when (v.size) {
        1 -> listOf(v[0], v[0], v[0])
        2 -> listOf(v[0], v[0], v[1])
        3 -> listOf(v[0], v[2], v[1])
        4 -> listOf(v[0], v[2], v[3])
        else -> null
    }
}

/** A vertical length in em; px and pt against a 16px body. Percentages and `auto` aren't vertical lengths here. */
private fun lengthEm(value: String): Float? = when {
    value.endsWith("rem") -> value.removeSuffix("rem").toFloatOrNull()
    value.endsWith("em") -> value.removeSuffix("em").toFloatOrNull()
    value.endsWith("px") -> value.removeSuffix("px").toFloatOrNull()?.div(16f)
    value.endsWith("pt") -> value.removeSuffix("pt").toFloatOrNull()?.div(12f)
    else -> value.toFloatOrNull()?.takeIf { it == 0f }
}?.coerceAtLeast(0f)

private fun weightIsBold(value: String): Boolean? = when (value) {
    "bold", "bolder" -> true
    "normal", "lighter" -> false
    else -> value.toIntOrNull()?.let { it >= 600 }
}

private fun cssLengthIsZero(value: String): Boolean =
    value.trimEnd('e', 'm', 'p', 'x', 't', '%', 'r').toFloatOrNull() == 0f

// Relative sizes only; absolute px/pt sizes would fight the reader's font size.
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

private fun relativeSize(value: String): Float? = when {
    value.endsWith("em") && !value.endsWith("rem") -> value.removeSuffix("em").toFloatOrNull()
    value.endsWith("%") -> value.removeSuffix("%").toFloatOrNull()?.div(100f)
    else -> KEYWORD_SIZES[value]
}

private val KEYWORD_SIZES = mapOf(
    "xx-small" to 0.6f, "x-small" to 0.75f, "small" to 0.89f, "medium" to 1f,
    "large" to 1.2f, "x-large" to 1.5f, "xx-large" to 2f, "smaller" to 0.83f, "larger" to 1.2f,
)
