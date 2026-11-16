package com.baca.shared.domain.reader

/**
 * What the selection toolbar's Define looks up for [selected]: its words with the punctuation
 * round them dropped and line breaks as spaces, or null when no letter or digit is left.
 */
fun lookupTerm(selected: String): String? {
    val term = selected
        .trim { !it.isLetterOrDigit() }
        .split(Regex("\\s+"))
        .joinToString(" ")
    return term.ifEmpty { null }
}

/** Wiktionary's search for [term], used where no dictionary app takes it. It opens the entry when one matches. */
fun webDictionaryUrl(term: String): String =
    "https://en.wiktionary.org/wiki/Special:Search?search=" + percentEncode(term)

private fun percentEncode(text: String): String = buildString {
    for (byte in text.encodeToByteArray()) {
        val c = byte.toInt().toChar()
        if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in "-._~") {
            append(c)
        } else {
            append('%').append(HEX[(byte.toInt() shr 4) and 0xF]).append(HEX[byte.toInt() and 0xF])
        }
    }
}

private const val HEX = "0123456789ABCDEF"
