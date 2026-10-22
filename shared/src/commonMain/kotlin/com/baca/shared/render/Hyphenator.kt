package com.baca.shared.render

/** Where a word may be hyphenated: each place as the index of the letter after it, ascending. */
interface Hyphenator {
    fun points(word: String): List<Int>

    companion object {
        private val enUs by lazy { TexHyphenator(EN_US_PATTERNS, EN_US_EXCEPTIONS) }

        /**
         * The patterns Baca carries for [language]: American English for English, as a
         * WebView takes `en`; none for other languages, nor without a language, where a
         * browser doesn't hyphenate. On Android the system's own come first ([HybHyphenator]).
         */
        fun forLanguage(language: String?): Hyphenator? =
            if (language != null && language.lowercase().let { it == "en" || it.startsWith("en-") }) enUs else null
    }
}

// a WebView on Android: words of at least 5 letters, 2 before the hyphen and 3 after
internal const val MIN_WORD = 5
internal const val MIN_PREFIX = 2
internal const val MIN_SUFFIX = 3

/**
 * Liang's TeX patterns for American English, with the limits a WebView on Android applies:
 * words of at least 5 letters, 2 before the hyphen and 3 after, and no capitalized
 * English words. A word with anything but letters isn't hyphenated.
 */
class TexHyphenator internal constructor(patterns: String, exceptions: String) : Hyphenator {
    // letters → the values between and around them, one more than the letters
    private val values: Map<String, IntArray> = patterns.split(' ', '\n').filter { it.isNotEmpty() }.associate { pattern ->
        val letters = pattern.filter { !it.isDigit() }
        val digits = IntArray(letters.length + 1)
        var at = 0
        for (c in pattern) if (c.isDigit()) digits[at] = c.digitToInt() else at++
        letters to digits
    }
    private val longest = values.keys.maxOf { it.length }
    // whole words hyphenated as written ("as-so-ciate"), over the patterns
    private val listed: Map<String, List<Int>> = exceptions.split(' ', '\n').filter { it.isNotEmpty() }.associate { word ->
        val points = mutableListOf<Int>()
        var letters = 0
        for (c in word) if (c == '-') points += letters else letters++
        word.replace("-", "") to points
    }

    override fun points(word: String): List<Int> {
        if (word.length < MIN_WORD || word.first().isUpperCase() || !word.all { it in 'a'..'z' || it in 'A'..'Z' }) return emptyList()
        val lower = word.lowercase()
        val allowed = MIN_PREFIX..word.length - MIN_SUFFIX
        listed[lower]?.let { points -> return points.filter { it in allowed } }
        val padded = ".$lower."
        val best = IntArray(padded.length + 1)
        for (i in padded.indices) {
            for (j in i + 1..minOf(padded.length, i + longest)) {
                val digits = values[padded.substring(i, j)] ?: continue
                digits.forEachIndexed { k, v -> if (v > best[i + k]) best[i + k] = v }
            }
        }
        // best[m + 1] sits between the word's letters m - 1 and m
        return allowed.filter { best[it + 1] % 2 == 1 }
    }
}
