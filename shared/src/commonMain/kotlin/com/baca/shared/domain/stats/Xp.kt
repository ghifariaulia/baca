package com.baca.shared.domain.stats

import com.baca.shared.domain.READ_THROUGH
import kotlinx.serialization.Serializable

/** What reading earns (TECH 10.9). */
object XpRules {
    const val LOCATION = 1
    const val CHAPTER = 20
    const val BOOK = 100
    const val DAILY_GOAL = 10
    /** reading time a day needs for its goal and the streak */
    const val GOAL_SECONDS = 15 * 60
    /** a chapter shorter than this earns no bonus (title pages, copyright notes) */
    const val MIN_CHAPTER_LOCATIONS = 5
    /** share of a chapter, or of the book, read in the read-through for its bonus */
    const val READ_SHARE = 0.8
    /** a read-through after the book is finished starts on a page within this share of the start */
    const val RESTART_SHARE = 0.02
    /** most of one page's time counted as reading; longer is the reader away */
    const val MAX_PAGE_SECONDS = 5 * 60
}

/**
 * How long a page must stay up before its locations count: 30% of the time its words take at
 * 250 words a minute (about 6 characters a word), between 1.5 and 15 seconds.
 */
fun minDwellMs(chars: Int): Long = (chars / 6.0 / 250 * 60_000 * 0.3).toLong().coerceIn(1_500L, 15_000L)

/** A level, the XP earned into it and the XP it takes to the next. */
data class Level(val number: Int, val xpIntoLevel: Int, val xpForNext: Int)

/** Total XP that reaches [level]: 0 for level 1, then 100, 300, 600, 1000… */
fun xpToReach(level: Int): Int = 50 * (level - 1) * level

fun levelOf(xp: Int): Level {
    var level = 1
    while (xp >= xpToReach(level + 1)) level++
    return Level(level, xp - xpToReach(level), xpToReach(level + 1) - xpToReach(level))
}

/** One edition's reading: the locations read in this read-through and the bonuses already earned. */
@Serializable
data class ReadThrough(
    val pass: Int = 1,
    /** locations read in this read-through, as [LocationSet.encode] writes them */
    val read: String = "",
    /** chapters (by their place in the contents) whose bonus this read-through paid */
    val chapters: List<Int> = emptyList(),
    val finished: Boolean = false,
    /** times the book was finished, over all read-throughs */
    val finishes: Int = 0,
    val bookBonusPaid: Boolean = false,
)

/** A page that was on screen: its locations (0-based) in the book of [total], and the book's chapters as locations. */
data class PageReading(
    val locations: IntRange,
    val total: Int,
    val chapters: List<IntRange>,
    val dwellMs: Long,
    val chars: Int,
)

/** What a page earned. */
data class Credited(val state: ReadThrough, val xp: Int, val locations: Int, val chapterDone: Boolean, val bookDone: Boolean)

/**
 * Credits [page] to [state]. A page up too briefly for its words earns nothing; each location pays once a
 * read-through; a chapter pays once most of it is read; the book pays once, the first time it is finished.
 * After a finished book, a page at its start begins the next read-through, whose locations and chapters pay again.
 */
fun credit(state: ReadThrough, page: PageReading): Credited {
    if (page.total <= 0 || page.dwellMs < minDwellMs(page.chars)) return Credited(state, 0, 0, false, false)
    val restart = state.finished && page.locations.first < page.total * XpRules.RESTART_SHARE
    val current = if (restart) ReadThrough(pass = state.pass + 1, finishes = state.finishes, bookBonusPaid = state.bookBonusPaid) else state
    val read = LocationSet.decode(current.read)
    val range = page.locations.first.coerceAtLeast(0)..page.locations.last.coerceAtMost(page.total - 1)
    val fresh = range.count() - read.countIn(range)
    read.add(range)

    val touched = page.chapters.withIndex().filter { (_, chapter) -> chapter.first <= range.last && range.first <= chapter.last }
    val chaptersDone = chaptersDone(page.chapters, read, current.chapters).filter { index -> touched.any { it.index == index } }

    // finished where the app counts a book finished (READ_THROUGH), and only when most of it was read on the way
    val finishedNow = !current.finished && range.last + 1 >= page.total * READ_THROUGH && read.count() >= page.total * XpRules.READ_SHARE
    val bookBonus = finishedNow && !current.bookBonusPaid
    val xp = fresh * XpRules.LOCATION + chaptersDone.size * XpRules.CHAPTER + (if (bookBonus) XpRules.BOOK else 0)
    val next = current.copy(
        read = read.encode(),
        chapters = current.chapters + chaptersDone,
        finished = current.finished || finishedNow,
        finishes = current.finishes + if (finishedNow) 1 else 0,
        bookBonusPaid = current.bookBonusPaid || bookBonus,
    )
    return Credited(next, xp, fresh, chaptersDone.isNotEmpty(), finishedNow)
}

/** Chapters long enough for a bonus, mostly [read] and not yet in [paid], by their index in [chapters]. */
internal fun chaptersDone(chapters: List<IntRange>, read: LocationSet, paid: List<Int>): List<Int> =
    chapters.withIndex().filter { (index, chapter) ->
        index !in paid &&
            chapter.count() >= XpRules.MIN_CHAPTER_LOCATIONS &&
            read.countIn(chapter) >= chapter.count() * XpRules.READ_SHARE
    }.map { it.index }
