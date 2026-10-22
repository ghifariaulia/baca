package com.baca.shared.render

/**
 * Android's hyphenation patterns in their compiled form (`/system/usr/hyphen-data/hyph-*.hyb`),
 * read as AOSP's Hyphenator reads them, which is what a WebView on Android hyphenates with:
 * words of at least 5 letters, 2 before the hyphen and 3 after, letters of the patterns'
 * alphabet only (a capital is its small letter), and capitalized words left whole in
 * English only, as Chromium leaves them.
 */
class HybHyphenator(private val data: ByteArray, private val capitalized: Boolean) : Hyphenator {
    private fun u32(at: Int) = (data[at].toInt() and 0xff) or ((data[at + 1].toInt() and 0xff) shl 8) or
        ((data[at + 2].toInt() and 0xff) shl 16) or ((data[at + 3].toInt() and 0xff) shl 24)

    private val alphabetAt = u32(8)
    private val trieAt = u32(12)
    private val patternsAt = u32(16)
    private val charMask = u32(trieAt + 4)
    private val linkShift = u32(trieAt + 8)
    private val linkMask = u32(trieAt + 12)
    private val patternShift = u32(trieAt + 16)
    private val patternDataAt = patternsAt + u32(patternsAt + 8)

    private fun trie(node: Int) = u32(trieAt + 24 + 4 * node)
    private fun pattern(index: Int) = u32(patternsAt + 16 + 4 * index)

    /** The letter's code in the patterns' alphabet; 0 for one outside it. */
    private fun code(c: Char): Int {
        val ch = c.code
        if (u32(alphabetAt) == 0) {
            val low = u32(alphabetAt + 4)
            val high = u32(alphabetAt + 8)
            return if (ch in low until high) data[alphabetAt + 12 + ch - low].toInt() and 0xff else 0
        }
        // entries are the code point above 11 bits of code, sorted
        var lo = 0
        var hi = u32(alphabetAt + 4) - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val entry = u32(alphabetAt + 8 + 4 * mid)
            val point = entry ushr 11
            when {
                point < ch -> lo = mid + 1
                point > ch -> hi = mid - 1
                else -> return entry and 0x7ff
            }
        }
        return 0
    }

    override fun points(word: String): List<Int> {
        if (word.length < MIN_WORD || (!capitalized && word.first().isUpperCase())) return emptyList()
        val codes = IntArray(word.length + 2)
        for (i in word.indices) codes[i + 1] = code(word[i]).takeIf { it != 0 } ?: return emptyList()
        val result = IntArray(word.length)
        val maxOffset = codes.size - MIN_SUFFIX - 1
        for (i in 0 until codes.size - 1) {
            var node = 0
            for (j in i until codes.size) {
                val c = codes[j]
                val entry = trie(node + c)
                if ((entry and charMask) != c) break
                node = (entry and linkMask) ushr linkShift
                val index = trie(node) ushr patternShift
                if (index == 0) continue
                val p = pattern(index)
                val length = p ushr 26
                val shift = (p ushr 20) and 0x3f
                val at = patternDataAt + (p and 0xfffff)
                val offset = j + 1 - (length + shift)
                for (k in maxOf(MIN_PREFIX - offset, 0) until minOf(length, maxOffset - offset)) {
                    val value = data[at + k].toInt() and 0xff
                    if (value > result[offset + k]) result[offset + k] = value
                }
            }
        }
        return (MIN_PREFIX until maxOffset).filter { result[it] and 1 == 1 }
    }
}

/**
 * The patterns file a language tag (`lang`, `dc:language`) is hyphenated with, as Chromium's
 * `MapLocale` falls back: `en` is American, other English British, `de` the 1996 spelling,
 * `no` Bokmål; a region or variant it doesn't know is dropped until a tag it has is left.
 */
fun hyphenLocaleOf(tag: String): String {
    var mapped = tag.lowercase().replace('_', '-')
    val original = mapped
    while (true) {
        LOCALE_FALLBACK[mapped]?.let { (locale, exact) -> return if (exact != null && mapped == original) exact else locale }
        val cut = mapped.lastIndexOf('-')
        if (cut <= 0) return mapped
        mapped = mapped.substring(0, cut)
    }
}

// Chromium's locale_fallback_data (from CLDR, compiled by AOSP): a locale and, where it differs, the one for an exact match
private val LOCALE_FALLBACK: Map<String, Pair<String, String?>> =
    listOf("en-as", "en-gu", "en-mh", "en-mp", "en-pr", "en-um", "en-vi").associateWith { "en-us" to null } +
        mapOf(
            "en" to ("en-gb" to "en-us"),
            "de" to ("de-1996" to null),
            "de-li-1901" to ("de-ch-1901" to null),
            "no" to ("nb" to null),
            "mn" to ("mn-cyrl" to null),
            "am" to ("und-ethi" to null), "byn" to ("und-ethi" to null), "gez" to ("und-ethi" to null),
            "ti" to ("und-ethi" to null), "wal" to ("und-ethi" to null),
            "und-beng" to ("bn" to null), "und-deva" to ("hi" to null), "und-gujr" to ("gu" to null),
            "und-guru" to ("pa" to null), "und-knda" to ("kn" to null), "und-mlym" to ("ml" to null),
            "und-orya" to ("or" to null), "und-taml" to ("ta" to null), "und-telu" to ("te" to null),
        ) +
        listOf("de-1901", "de-1996", "de-ch-1901", "en-gb", "en-us", "mn-cyrl", "und-ethi").associateWith { it to null }
