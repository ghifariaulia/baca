package com.baca.shared.domain

/**
 * Key that links different files of the same EPUB edition (re-zipped,
 * re-downloaded): the OPF's unique identifier plus the title, both normalized.
 * The title guards against generators that stamp one placeholder id on
 * unrelated books. Null when the book has no usable identifier.
 */
fun workKeyOf(identifier: String?, title: String): String? {
    val id = identifier?.trim()?.lowercase()
        ?.replace(ID_SCHEME, "")
        ?.filter { it.isLetterOrDigit() }
        ?: return null
    if (id.length < MIN_ID_LENGTH) return null
    val normalizedTitle = title.lowercase().filter { it.isLetterOrDigit() }
    return "$id|$normalizedTitle"
}

// "urn:uuid:…", "urn:isbn:…", "isbn:…" name the same id as the bare value
private val ID_SCHEME = Regex("^(urn:)?(uuid|isbn):")

// shorter ids ("1", "id") are placeholders, not identifiers
private const val MIN_ID_LENGTH = 6
