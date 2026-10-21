package com.baca.shared.parser

/**
 * The part of a book's CSS that Baca applies (TECH 3.1), following Librera's
 * policy: the book keeps its alignment, small caps, bold/italic, hidden
 * elements, no-indent classes and relative sizes, while the reader's own font,
 * size, line spacing, indent and justification always win (so `text-align:
 * left/justify`, margins and absolute sizes are ignored).
 *
 * Selectors: `tag`, `.class`, `tag.class.other` only. Rules with descendant,
 * child, sibling, attribute, id or pseudo selectors are skipped rather than
 * guessed at.
 */
internal data class CssStyle(
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val smallCaps: Boolean? = null,
    val align: TextAlign? = null,
    val noIndent: Boolean? = null,
    val floatRight: Boolean? = null,
    val hidden: Boolean? = null,
    /** relative to the parent element: 1.2 for `1.2em` or `120%` */
    val fontScale: Float? = null,
) {
    /** [other]'s set values win. */
    fun overriddenBy(other: CssStyle) = CssStyle(
        bold = other.bold ?: bold,
        italic = other.italic ?: italic,
        smallCaps = other.smallCaps ?: smallCaps,
        align = other.align ?: align,
        noIndent = other.noIndent ?: noIndent,
        floatRight = other.floatRight ?: floatRight,
        hidden = other.hidden ?: hidden,
        fontScale = other.fontScale ?: fontScale,
    )
}

internal class CssRule(val tag: String?, val classes: Set<String>, val style: CssStyle, val order: Int) {
    val specificity = classes.size * 10 + (if (tag != null) 1 else 0)
    fun matches(tagName: String, elementClasses: Set<String>) =
        (tag == null || tag == tagName) && elementClasses.containsAll(classes)
}

/** All rules seen so far in a chapter, in document order. */
internal class Stylesheet {
    private val rules = mutableListOf<CssRule>()

    fun add(css: String) {
        parseCss(css, firstOrder = rules.size).forEach { rules.add(it) }
    }

    /** The element's own declared style: matching rules by specificity, then the inline style. */
    fun styleFor(tag: String, classes: Set<String>, inline: String?): CssStyle {
        val declared = rules.filter { it.matches(tag, classes) }
            .sortedWith(compareBy({ it.specificity }, { it.order }))
            .fold(CssStyle()) { acc, rule -> acc.overriddenBy(rule.style) }
        return inline?.let { declared.overriddenBy(parseDeclarations(it)) } ?: declared
    }
}

private val SIMPLE_SELECTOR = Regex("^([a-zA-Z][a-zA-Z0-9]*)?((?:\\.[a-zA-Z0-9_-]+)*)$")

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
            val style = parseDeclarations(text.substring(open + 1, close))
            prelude.split(',').map { it.trim() }.forEach { selector ->
                SIMPLE_SELECTOR.matchEntire(selector)?.takeIf { selector.isNotEmpty() }?.let { m ->
                    val tag = m.groupValues[1].lowercase().ifEmpty { null }
                    val classes = m.groupValues[2].split('.').filter { it.isNotEmpty() }.toSet()
                    // like Librera: a book-wide rule for body text (`p { text-indent: 0 }`)
                    // doesn't override the reader's indent and justification; classes do
                    val ruleStyle = if (classes.isEmpty() && tag in BODY_TEXT_TAGS) style.copy(noIndent = null, align = null) else style
                    rules.add(CssRule(tag, classes, ruleStyle, firstOrder + rules.size))
                }
            }
        }
        // @media / @font-face / @page blocks are skipped whole
        i = close + 1
    }
    return rules
}

private val BODY_TEXT_TAGS = setOf("p", "div", "body", "span", "li", "html")

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

internal fun parseDeclarations(block: String): CssStyle {
    var style = CssStyle()
    block.split(';').forEach { declaration ->
        val name = declaration.substringBefore(':', "").trim().lowercase()
        val value = declaration.substringAfter(':', "").replace("!important", "").trim().lowercase()
        if (name.isEmpty() || value.isEmpty()) return@forEach
        style = when (name) {
            "font-weight" -> style.copy(bold = weightIsBold(value) ?: style.bold)
            "font-style" -> style.copy(italic = value == "italic" || value == "oblique")
            "font-variant", "font-variant-caps" -> style.copy(smallCaps = value.contains("small-caps"))
            "text-align" -> style.copy(
                align = when (value) {
                    "center" -> TextAlign.CENTER
                    "right", "end" -> TextAlign.END
                    // left/justify: the reader's justification setting decides
                    else -> TextAlign.START
                },
            )
            "text-indent" -> style.copy(noIndent = cssLengthIsZero(value))
            "float" -> style.copy(floatRight = value == "right")
            "display" -> style.copy(hidden = value == "none")
            "font-size" -> style.copy(fontScale = relativeSize(value) ?: style.fontScale)
            else -> style
        }
    }
    return style
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
