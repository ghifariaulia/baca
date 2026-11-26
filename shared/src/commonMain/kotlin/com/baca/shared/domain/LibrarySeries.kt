package com.baca.shared.domain

/** Books sharing a non-blank series name, sorted by title within the series. */
data class SeriesGroup<T>(
    val name: String,
    val books: List<T>,
)

/** Splits [items] into sorted [SeriesGroup]s (case-insensitive key) and standalone items with no series. */
fun <T> groupBySeries(
    items: List<T>,
    seriesOf: (T) -> String,
    titleOf: (T) -> String,
): Pair<List<SeriesGroup<T>>, List<T>> {
    val grouped = linkedMapOf<String, MutableList<T>>()
    val displayNames = linkedMapOf<String, String>()
    val standalones = mutableListOf<T>()

    for (item in items) {
        val rawSeries = seriesOf(item).trim()
        if (rawSeries.isEmpty()) {
            standalones += item
            continue
        }
        val key = rawSeries.lowercase()
        displayNames.putIfAbsent(key, rawSeries)
        grouped.getOrPut(key) { mutableListOf() } += item
    }

    val seriesGroups = grouped.entries
        .sortedBy { displayNames.getValue(it.key).lowercase() }
        .map { (key, books) ->
            SeriesGroup(
                name = displayNames.getValue(key),
                books = books.sortedBy { titleOf(it).lowercase() },
            )
        }

    return seriesGroups to standalones
}
