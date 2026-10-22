package com.baca.shared.parser

import kotlinx.serialization.Serializable

/** A find-and-replace rule the reader applies to a book's text as it is read: [find] is plain text, every occurrence becomes [replace]. */
@Serializable
data class TextReplacement(val find: String, val replace: String)

/**
 * What the reader's settings change in how a book is read, as LibreraX applies them to each page it
 * loads: the colour scheme the book's CSS is read for, the direction of the page's root, the reader's
 * own CSS (which comes after the book's and so wins at equal weight), and replacements in its text.
 */
data class ParseOptions(
    val darkScheme: Boolean = false,
    /** the root's direction as the reader sets it (`true` right to left); null leaves it to the book and its language */
    val rtl: Boolean? = null,
    val userCss: String = "",
    val replacements: List<TextReplacement> = emptyList(),
) {
    /** [text] with the replacement rules applied, each over the whole of it, in order. */
    fun replaced(text: String): String =
        replacements.fold(text) { acc, rule -> if (rule.find.isEmpty()) acc else acc.replace(rule.find, rule.replace) }

    /**
     * Whether [book], read under its own options, must be read again under [now]: any of the reader's
     * rules changed, or the colour scheme did in a book whose CSS asks for it.
     */
    fun needsReread(book: ParsedEpub, now: ParseOptions): Boolean =
        now.rtl != rtl || now.userCss != userCss || now.replacements != replacements ||
            (book.schemeAware && now.darkScheme != darkScheme)
}
