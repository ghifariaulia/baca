package com.baca.shared.parser

/**
 * The part of a book's CSS that Baca applies (TECH 3.1), cascaded the way
 * LibreraX does it: the book's rules compete with the reader's own rules
 * (`READER_RULES`) by `!important`, specificity and order, as in a browser.
 * The reader's font, size and line spacing always win and are not CSS here;
 * colours, margins and absolute sizes are ignored.
 *
 * Selectors: compounds like `tag`, `.class`, `tag.class.other`, joined by
 * descendant (space) or child (`>`) combinators. Rules with sibling,
 * attribute, id or pseudo selectors are skipped rather than guessed at.
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
    )
}

/** A declaration block split by `!important`, which outranks every normal declaration. */
internal data class Declarations(val normal: CssStyle = CssStyle(), val important: CssStyle = CssStyle())

/** An element as selectors see it. */
internal data class CssElement(val tag: String, val classes: Set<String>)

/** One `tag.class.other` part of a selector. */
internal data class Compound(val tag: String?, val classes: Set<String>) {
    fun matches(element: CssElement) = (tag == null || tag == element.tag) && element.classes.containsAll(classes)
}

/**
 * [parts] run outermost first, the last one is the element the rule styles.
 * [childOf] has one entry per part after the first: true for `>`, false for a space.
 */
internal class CssRule(val parts: List<Compound>, val childOf: List<Boolean>, val declarations: Declarations, val order: Int) {
    val specificity = parts.sumOf { it.classes.size * 10 + (if (it.tag != null) 1 else 0) }

    /** [path] runs from the outermost open element to the element being styled. */
    fun matches(path: List<CssElement>) = path.isNotEmpty() && matchFrom(parts.lastIndex, path.lastIndex, path)

    private fun matchFrom(part: Int, at: Int, path: List<CssElement>): Boolean {
        if (!parts[part].matches(path[at])) return false
        if (part == 0) return true
        if (childOf[part - 1]) return at > 0 && matchFrom(part - 1, at - 1, path)
        return (at - 1 downTo 0).any { matchFrom(part - 1, it, path) }
    }
}

private fun tagRules(tags: List<String>, declarations: Declarations, order: Int) =
    tags.map { CssRule(listOf(Compound(it, emptySet())), emptyList(), declarations, order) }

/**
 * LibreraX's own stylesheet, as far as Baca applies CSS (FoliateCss and
 * BookCSS.generatedCss in LibreraReader). Headings are centred before the
 * book's CSS, so any book rule changes that. Body text gets the reader's
 * justification and an `!important` indent after the book's CSS, so a book
 * rule needs more specificity to set alignment, and `!important` too to set
 * the indent. Links take the reader's link colour unless a book rule
 * outranks that.
 */
private val READER_RULES =
    tagRules(listOf("h1", "h2", "h3", "h4", "h5", "h6"), Declarations(normal = CssStyle(align = TextAlign.CENTER)), Int.MIN_VALUE) +
        tagRules(
            listOf("body", "div", "p", "span"),
            Declarations(
                normal = CssStyle(align = TextAlign.START),
                important = CssStyle(noIndent = false, hanging = false),
            ),
            Int.MAX_VALUE,
        ) +
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

/** `div.a > p .b` into its compounds and combinators; null for selectors Baca doesn't support. */
private fun parseSelector(selector: String): Pair<List<Compound>, List<Boolean>>? {
    val tokens = selector.replace(">", " > ").trim().split(Regex("\\s+"))
    val parts = mutableListOf<Compound>()
    val childOf = mutableListOf<Boolean>()
    var child = false
    for (token in tokens) {
        if (token == ">") {
            if (parts.isEmpty() || child) return null
            child = true
            continue
        }
        val m = COMPOUND.matchEntire(token)?.takeIf { token.isNotEmpty() } ?: return null
        if (parts.isNotEmpty()) childOf.add(child)
        child = false
        parts.add(Compound(m.groupValues[1].lowercase().ifEmpty { null }, m.groupValues[2].split('.').filter { it.isNotEmpty() }.toSet()))
    }
    return if (parts.isEmpty() || child) null else parts to childOf
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
                parseSelector(selector)?.let { (parts, childOf) ->
                    rules.add(CssRule(parts, childOf, declarations, firstOrder + rules.size))
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
    "text-decoration", "text-decoration-line" -> copy(underline = value.contains("underline"))
    else -> this
}

private fun weightIsBold(value: String): Boolean? = when (value) {
    "bold", "bolder" -> true
    "normal", "lighter" -> false
    else -> value.toIntOrNull()?.let { it >= 600 }
}

private fun cssLengthIsZero(value: String): Boolean =
    value.trimEnd('e', 'm', 'p', 'x', 't', '%', 'r').toFloatOrNull() == 0f

// Relative sizes only; absolute px/pt sizes would fight the reader's font size.
private fun relativeSize(value: String): Float? = when {
    value.endsWith("em") && !value.endsWith("rem") -> value.removeSuffix("em").toFloatOrNull()
    value.endsWith("%") -> value.removeSuffix("%").toFloatOrNull()?.div(100f)
    else -> KEYWORD_SIZES[value]
}

private val KEYWORD_SIZES = mapOf(
    "xx-small" to 0.6f, "x-small" to 0.75f, "small" to 0.89f, "medium" to 1f,
    "large" to 1.2f, "x-large" to 1.5f, "xx-large" to 2f, "smaller" to 0.83f, "larger" to 1.2f,
)
