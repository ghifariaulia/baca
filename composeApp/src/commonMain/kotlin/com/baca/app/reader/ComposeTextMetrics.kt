package com.baca.app.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import com.baca.shared.parser.BookFace
import com.baca.shared.parser.InlineImage
import com.baca.shared.render.Hyphenator
import com.baca.shared.render.TextMetrics
import com.baca.shared.render.greedyLines

/**
 * Real text measurement for the layout engine. [fontSizePx]/[lineHeightPx] are
 * pixels; TextMeasurer works in text units, so the adapter converts with the
 * [Density] passed by the caller (from LocalDensity at the point of use).
 *
 * Measures with the same font family and bold/italic spans the renderer draws
 * with, so measured line breaks equal rendered line breaks (the offsets
 * contract, TECH 5.2/5.3). Lines break as a browser breaks them ([greedyLines]),
 * not as Compose does: Compose's greedy strategy hyphenates only words longer than
 * a line, and its paragraph strategy balances lines instead of filling each.
 */
class ComposeTextMetrics(
    private val measurer: TextMeasurer,
    private val density: Density,
    /** an inline image's width over its height */
    private val imageAspect: (String) -> Float = { 1f },
    /** the book's language, for text without one of its own: hyphenation follows it, as LibreraX's does; null hyphenates nothing, as a browser */
    private val language: String? = null,
    /** the patterns a language is hyphenated with ([rememberHyphenators]) */
    private val hyphenatorFor: (String?) -> Hyphenator? = Hyphenator::forLanguage,
    /** display font name → family, from the app's bundled fonts. */
    private val families: (String?) -> FontFamily?,
) : TextMetrics {

    override fun layoutLines(
        text: String,
        fontSizePx: Float,
        lineHeightPx: Int,
        maxWidthPx: Int,
        fontFamily: String?,
        styles: List<TextMetrics.StyleRange>,
        firstLineIndentPx: Int,
        hyphenate: Boolean,
        language: String?,
        rtl: Boolean,
    ): List<TextMetrics.LineSpan> {
        // nothing to measure before the page is: a table cell's widest line asks unbounded, at size 0
        if (text.isEmpty() || maxWidthPx <= 0 || fontSizePx <= 0f) return emptyList()
        val lang = language ?: this.language
        val style = with(density) {
            TextStyle(
                fontSize = fontSizePx.toSp(),
                lineHeight = lineHeightPx.toFloat().toSp(),
                fontFamily = families(fontFamily),
                localeList = lang?.let { LocaleList(it) },
                // as the renderer draws: letters at their exact advance, not hinted to whole px
                textMotion = TextMotion.Animated,
                textDirection = if (rtl) TextDirection.Rtl else TextDirection.Unspecified,
            )
        }
        val annotated = if (styles.isEmpty()) {
            AnnotatedString(text)
        } else {
            buildAnnotatedString {
                append(text)
                styles.forEach { range ->
                    addStyle(
                        SpanStyle(
                            fontWeight = if (range.bold) FontWeight.Bold else null,
                            fontStyle = if (range.italic) FontStyle.Italic else null,
                            fontFeatureSettings = if (range.smallCaps) SMALL_CAPS else null,
                            fontSize = if (range.scale != 1f) range.scale.em else TextUnit.Unspecified,
                            fontFamily = range.family?.let { families(bookFontName(it)) } ?: range.face?.let { families(faceFontName(it)) },
                            baselineShift = if (range.baseline != 0f) BaselineShift(range.baseline) else null,
                            letterSpacing = if (range.letterSpacing != 0f) range.letterSpacing.em else TextUnit.Unspecified,
                        ),
                        range.start,
                        range.endExclusive,
                    )
                    wordSpaces(text, range.start, range.endExclusive, range.wordSpacing).forEach { (at, style) -> addStyle(style, at, at + 1) }
                }
            }
        }
        // the whole text on one unbroken line (one per hard break), for where each character ends
        val layout = measurer.measure(
            annotated,
            style,
            constraints = Constraints(),
            placeholders = styles.mapNotNull { range ->
                val placeholder = range.image?.let { inlineImagePlaceholder(it, imageAspect(it.src)) }
                    ?: range.spacerPx?.let { spacerPlaceholder(it, density) }
                placeholder?.let { AnnotatedString.Range(it, range.start, range.endExclusive) }
            },
        )
        val boxes = characterBoxes(layout, text.length)
        val images = BooleanArray(text.length).also { at -> styles.filter { it.image != null }.forEach { at[it.start] = true } }
        return greedyLines(
            text,
            edgesOf(text, boxes, rtl),
            maxWidthPx,
            firstLineIndentPx,
            hyphenator = if (hyphenate) hyphenatorFor(lang) else null,
            hyphenPx = measurer.measure("-", style).size.width.toFloat(),
            image = { images[it] },
        )
    }

    // ascent and descent as fractions of the size, measured once per font
    private val verticalCache = HashMap<FontFamily?, TextMetrics.VerticalMetrics>()

    override fun verticalMetrics(fontFamily: String?, face: BookFace?, family: String?): TextMetrics.VerticalMetrics {
        val font = family?.let { families(bookFontName(it)) } ?: families(face?.let(::faceFontName) ?: fontFamily)
        return verticalCache.getOrPut(font) {
            // one line at a size big enough to read to the fraction: its baseline is the ascent
            val probe = measurer.measure("Hxg", TextStyle(fontFamily = font, fontSize = with(density) { METRICS_PX.toSp() }))
            val ascent = probe.firstBaseline / METRICS_PX
            TextMetrics.VerticalMetrics(ascent, probe.size.height / METRICS_PX - ascent)
        }
    }
}

/** Each character's box in [layout], left, top, right and bottom, as `MultiParagraph.fillBoundingBoxes` gives them. */
internal expect fun characterBoxes(layout: TextLayoutResult, length: Int): FloatArray

/**
 * The x of each place between [text]'s characters from the start of its hard line,
 * from the characters' boxes (left, top, right, bottom each) as laid on one line.
 */
private fun edgesOf(text: String, boxes: FloatArray, rtl: Boolean = false): FloatArray {
    val edges = FloatArray(text.length + 1)
    var lineStart = 0
    var origin = 0f
    var bidi = rtl
    for (i in 0..text.length) {
        val atLineEnd = i == text.length || text[i] == '\n'
        if (i == lineStart) {
            origin = if (atLineEnd) 0f else boxes[i * 4]
            // a hard line with right-to-left letters in it has glyphs running backwards, even in a left-to-right block
            if (!rtl) bidi = (i until (text.indexOf('\n', i).let { if (it < 0) text.length else it })).any { isRtlChar(text[it]) }
        } else if (bidi) {
            // right-to-left glyphs run backwards: a character is as wide as its box
            val w = boxes[(i - 1) * 4 + 2] - boxes[(i - 1) * 4]
            edges[i] = edges[i - 1] + maxOf(w, 0f)
        } else {
            // where the next character starts (a picture's box can come out empty); never back,
            // as joined glyphs can share a box
            val x = if (atLineEnd) boxes[(i - 1) * 4 + 2] else boxes[i * 4]
            edges[i] = maxOf(edges[i - 1], x - origin)
        }
        if (i < text.length && text[i] == '\n') lineStart = i + 1
    }
    return edges
}

/** A letter of a script written right to left: Hebrew, Arabic, Syriac, Thaana and their presentation forms. */
internal fun isRtlChar(c: Char): Boolean = c in '\u0590'..'\u08FF' || c in '\uFB1D'..'\uFDFF' || c in '\uFE70'..'\uFEFF'

// the size a font's ascent and descent are measured at
private const val METRICS_PX = 1000f

/** The room an inline image takes in its line; measured and drawn the same way. */
internal fun inlineImagePlaceholder(image: InlineImage, aspect: Float) = Placeholder(
    width = (image.heightEm * aspect).em,
    height = image.heightEm.em,
    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
)

/**
 * `word-spacing` as Compose can draw it: extra letter spacing on each space char
 * in [start, end), [em] of its font. Measured and drawn the same way.
 */
internal fun wordSpaces(text: CharSequence, start: Int, end: Int, em: Float): List<Pair<Int, SpanStyle>> =
    if (em == 0f) emptyList() else (start until end).filter { text[it] == ' ' || text[it] == NO_BREAK_SPACE }.map { it to SpanStyle(letterSpacing = em.em) }

/** An inline element's padding: [px] wide, too short to change the line's height. Measured and drawn the same way. */
internal fun spacerPlaceholder(px: Int, density: Density) = with(density) {
    Placeholder(width = px.toFloat().toSp(), height = 1f.toSp(), placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter)
}

/** The reader font a book's own face is drawn in: one name for measuring and drawing. */
internal fun faceFontName(face: BookFace): String = when (face) {
    BookFace.SANS -> SYSTEM_SANS
    BookFace.MONO -> SYSTEM_MONO
    BookFace.SERIF -> SYSTEM_SERIF
}

/** The name a font the book ships goes by among the reader's fonts ([rememberBookFonts]). */
internal fun bookFontName(family: String): String = "book:$family"

// A book's generic faces, drawn in the system's fonts as a browser draws them (Roboto on Android)
internal const val SYSTEM_SANS = "system-sans"
internal const val SYSTEM_MONO = "system-mono"
internal const val SYSTEM_SERIF = "system-serif"

/** OpenType small caps; fonts without them draw normal lowercase. Measured and drawn the same way. */
internal const val SMALL_CAPS = "smcp"
