package com.baca.shared.parser

import com.baca.shared.domain.reader.BookLocations
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What a book says about itself, beyond its title and author: the OPF's or FB2's description block. */
@Serializable
data class BookMeta(
    val publisher: String = "",
    /** as the book writes it: a year or a date */
    val published: String = "",
    val description: String = "",
    val subjects: List<String> = emptyList(),
    val rights: String = "",
    val series: String = "",
)

/** Everything the book's details page lists beyond the library row: [BookMeta] plus what reading the book shows. */
@Serializable
data class BookInfo(
    val meta: BookMeta = BookMeta(),
    val identifier: String = "",
    val language: String = "",
    /** documents in the reading order */
    val chapters: Int = 0,
    val words: Int = 0,
    /** pages as the reader numbers them (locations); 0 when the documents' sizes aren't known */
    val pages: Int = 0,
)

/** The book's [BookInfo], counted from what was read. */
fun ParsedEpub.info(): BookInfo {
    val reading = chapters.filter { it.linear }
    val words = reading.sumOf { chapter ->
        chapter.blocks.sumOf { block -> if (block is Block.Text) block.text.split(WHITESPACE).count { it.isNotEmpty() } else 0 }
    }
    return BookInfo(
        meta = meta,
        identifier = identifier.orEmpty(),
        language = language.orEmpty(),
        chapters = reading.size,
        words = words,
        pages = BookLocations(chapters, toc).total,
    )
}

private val WHITESPACE = Regex("\\s+")

private val infoJson = Json { ignoreUnknownKeys = true }

fun BookInfo.encode(): String = infoJson.encodeToString(BookInfo.serializer(), this)

fun decodeBookInfo(text: String): BookInfo? = runCatching { infoJson.decodeFromString(BookInfo.serializer(), text) }.getOrNull()
