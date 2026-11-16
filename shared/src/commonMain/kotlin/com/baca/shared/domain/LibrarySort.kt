package com.baca.shared.domain

/** The orders the library's book grid can take. [key] is what's saved in `settings_local`. */
enum class LibrarySort(val key: String, val label: String) {
    ADDED("added", "Date added"),
    RECENT("recent", "Recently read"),
    TITLE("title", "Title"),
    AUTHOR("author", "Author"),
    PROGRESS("progress", "Progress");

    companion object {
        const val STORE_KEY = "library_sort"

        /** The saved order, or [ADDED] (the library's order before sorting existed) when none is saved. */
        fun of(key: String?): LibrarySort = entries.firstOrNull { it.key == key } ?: ADDED
    }
}

/** What a book is sorted by. [lastReadAt] and [progress] are null for a book never opened. */
data class SortFacts(
    val title: String,
    val author: String,
    val addedAt: Long,
    val lastReadAt: Long?,
    val progress: Float?,
)

/**
 * [books] in [sort]'s order. Newest first for dates and most read first for progress, books never
 * opened after the rest; titles and authors A to Z ignoring case, books with no author last.
 */
fun <T> sortLibrary(books: List<T>, sort: LibrarySort, facts: (T) -> SortFacts): List<T> {
    val byAdded = compareByDescending<SortFacts> { it.addedAt }
    val byTitle = compareBy<SortFacts, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
    val comparator: Comparator<SortFacts> = when (sort) {
        LibrarySort.ADDED -> byAdded
        LibrarySort.RECENT -> compareBy<SortFacts> { it.lastReadAt == null }
            .thenByDescending { it.lastReadAt }
            .then(byAdded)
        LibrarySort.TITLE -> byTitle.thenBy(String.CASE_INSENSITIVE_ORDER) { it.author }
        LibrarySort.AUTHOR -> compareBy<SortFacts> { it.author.isBlank() }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.author }
            .then(byTitle)
        LibrarySort.PROGRESS -> compareBy<SortFacts> { it.progress == null }
            .thenByDescending { it.progress }
            .thenByDescending { it.lastReadAt }
    }
    return books.sortedWith { a, b -> comparator.compare(facts(a), facts(b)) }
}
