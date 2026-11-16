package com.baca.shared.domain.stats

import kotlinx.serialization.Serializable

/** A book read before stats were kept: what its saved position and its contents tell. */
data class EarlierBook(
    /** the edition (`workKeyOf`), else the file's hash */
    val id: String,
    val locations: Int,
    /** the book's chapters as locations, as the reader credits them (`BookLocations.chapterRanges`) */
    val chapters: List<IntRange>,
    val progress: Float,
    val finished: Boolean,
)

/** XP and locations from the catch-up, kept apart from the days since no day is known for them; they count in the totals. */
@Serializable
data class EarlierReading(val xp: Int = 0, val locations: Int = 0)

/**
 * Credits reading done before stats were kept: each edition's locations up to its saved position (all of them
 * when finished), the chapters that makes mostly read, and the book bonus when finished. Copies of one edition
 * count once, the furthest read. Reading already credited in [existing] (pages read before the catch-up ran)
 * is merged with, never paid twice. Returns the editions' new read-throughs and what they earned.
 */
fun catchUp(books: List<EarlierBook>, existing: Map<String, ReadThrough>): Pair<Map<String, ReadThrough>, EarlierReading> {
    val states = mutableMapOf<String, ReadThrough>()
    var earlier = EarlierReading()
    books.filter { it.locations > 0 }
        .groupBy { it.id }
        .forEach { (id, copies) ->
            val book = copies.maxBy { if (it.finished) 2f else it.progress }
            val before = existing[id] ?: ReadThrough()
            val share = if (book.finished) 1.0 else book.progress.toDouble().coerceIn(0.0, 1.0)
            val readTo = (book.locations * share).toInt()
            val read = LocationSet.decode(before.read)
            val fresh = if (readTo > 0) readTo - read.countIn(0 until readTo) else 0
            read.add(0 until readTo)
            val chapters = chaptersDone(book.chapters, read, before.chapters)
            val bookBonus = book.finished && !before.bookBonusPaid
            val finishedNow = book.finished && !before.finished
            if (fresh == 0 && chapters.isEmpty() && !finishedNow) return@forEach
            states[id] = before.copy(
                read = read.encode(),
                chapters = before.chapters + chapters,
                finished = before.finished || book.finished,
                finishes = before.finishes + if (finishedNow) 1 else 0,
                bookBonusPaid = before.bookBonusPaid || bookBonus,
            )
            earlier = EarlierReading(
                xp = earlier.xp + fresh * XpRules.LOCATION + chapters.size * XpRules.CHAPTER + if (bookBonus) XpRules.BOOK else 0,
                locations = earlier.locations + fresh,
            )
        }
    return states to earlier
}
