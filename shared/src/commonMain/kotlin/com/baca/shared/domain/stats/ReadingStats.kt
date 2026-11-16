package com.baca.shared.domain.stats

import kotlinx.serialization.json.Json

/** Where stats are kept: `settings_local` keys, so a backup carries them and no table is added. */
interface StatsStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    /** every key starting with [prefix], with its value */
    fun withPrefix(prefix: String): Map<String, String>
}

/** The edition a book's reading is credited to: its work key, so copies of one edition share it, else its hash. */
fun statsBookId(bookHash: String, workKey: String?): String = workKey?.takeIf { it.isNotEmpty() } ?: bookHash

/** What a page earned, for the reader's milestone note. */
data class PageOutcome(
    val xp: Int,
    val chapterDone: Boolean,
    val bookDone: Boolean,
    val goalMet: Boolean,
    /** the level reached when this page took the reader past one, else null */
    val levelUp: Int?,
)

data class Achievement(val title: String, val description: String, val unlocked: Boolean)

/** Everything the stats screen shows. */
data class StatsSummary(
    val xp: Int,
    val level: Level,
    val streak: Int,
    val longestStreak: Int,
    val today: DayRecord,
    /** the last seven days, oldest first */
    val week: List<Pair<String, DayRecord>>,
    val booksFinished: Int,
    val locations: Int,
    val seconds: Long,
    val achievements: List<Achievement>,
)

/**
 * Reading stats (TECH 10.9): credits pages as they are read, catches up on earlier reading once, and sums it all
 * up. Dates come in as `yyyy-mm-dd` from the caller, which reads the clock.
 */
class ReadingStats(private val store: StatsStore) {

    /** Credits a page of edition [bookId] read on [today]. */
    fun pageRead(bookId: String, page: PageReading, today: String): PageOutcome {
        val before = totalXp()
        val state = store.get(READ_PREFIX + bookId)?.let { json.decodeFromString(ReadThrough.serializer(), it) } ?: ReadThrough()
        val credited = credit(state, page)
        if (credited.state != state) store.put(READ_PREFIX + bookId, json.encodeToString(ReadThrough.serializer(), credited.state))

        val day = dayOf(today)
        val seconds = day.seconds + (page.dwellMs / 1000).toInt().coerceAtMost(XpRules.MAX_PAGE_SECONDS)
        val goalNow = !day.goalMet && seconds >= XpRules.GOAL_SECONDS
        val earned = credited.xp + if (goalNow) XpRules.DAILY_GOAL else 0
        store.put(
            DAY_PREFIX + today,
            json.encodeToString(
                DayRecord.serializer(),
                DayRecord(day.xp + earned, seconds, day.locations + credited.locations, day.goalMet || goalNow),
            ),
        )
        val levelBefore = levelOf(before).number
        val levelAfter = levelOf(before + earned).number
        return PageOutcome(earned, credited.chapterDone, credited.bookDone, goalNow, levelAfter.takeIf { it > levelBefore })
    }

    /**
     * Credits reading done before stats were kept, the first time only: [books] is asked for only then. The mark it
     * leaves goes into backups, so restoring one made before stats catches up on that library instead.
     */
    fun catchUpOnce(books: () -> List<EarlierBook>) {
        if (store.get(CAUGHT_UP_KEY) != null) return
        val existing = store.withPrefix(READ_PREFIX)
            .mapKeys { it.key.removePrefix(READ_PREFIX) }
            .mapValues { json.decodeFromString(ReadThrough.serializer(), it.value) }
        val (states, earlier) = catchUp(books(), existing)
        states.forEach { (id, state) -> store.put(READ_PREFIX + id, json.encodeToString(ReadThrough.serializer(), state)) }
        store.put(EARLIER_KEY, json.encodeToString(EarlierReading.serializer(), earlier))
        store.put(CAUGHT_UP_KEY, "1")
    }

    fun summary(today: String): StatsSummary {
        val days = days()
        val earlier = earlier()
        val xp = earlier.xp + days.values.sumOf { it.xp }
        val reads = store.withPrefix(READ_PREFIX).values.map { json.decodeFromString(ReadThrough.serializer(), it) }
        val finishes = reads.sumOf { it.finishes }
        val booksFinished = reads.count { it.finishes > 0 }
        val locations = earlier.locations + days.values.sumOf { it.locations }
        val longest = longestStreak(days)
        val level = levelOf(xp)
        return StatsSummary(
            xp = xp,
            level = level,
            streak = currentStreak(days, today),
            longestStreak = longest,
            today = days[today] ?: DayRecord(),
            week = lastDays(today, 7).map { it to (days[it] ?: DayRecord()) },
            booksFinished = booksFinished,
            locations = locations,
            seconds = days.values.sumOf { it.seconds.toLong() },
            achievements = listOf(
                Achievement("First book", "Finish a book", booksFinished >= 1),
                Achievement("Bookworm", "Finish 10 books", booksFinished >= 10),
                Achievement("Library", "Finish 50 books", booksFinished >= 50),
                Achievement("Re-reader", "Finish a book a second time", finishes > booksFinished),
                Achievement("On a roll", "Meet the daily goal 7 days in a row", longest >= 7),
                Achievement("Habit", "Meet the daily goal 30 days in a row", longest >= 30),
                Achievement("Seasoned", "Reach level 10", level.number >= 10),
            ),
        )
    }

    private fun totalXp(): Int = earlier().xp + days().values.sumOf { it.xp }

    private fun days(): Map<String, DayRecord> = store.withPrefix(DAY_PREFIX)
        .mapKeys { it.key.removePrefix(DAY_PREFIX) }
        .mapValues { json.decodeFromString(DayRecord.serializer(), it.value) }

    private fun dayOf(day: String): DayRecord =
        store.get(DAY_PREFIX + day)?.let { json.decodeFromString(DayRecord.serializer(), it) } ?: DayRecord()

    private fun earlier(): EarlierReading =
        store.get(EARLIER_KEY)?.let { json.decodeFromString(EarlierReading.serializer(), it) } ?: EarlierReading()

    companion object {
        const val READ_PREFIX = "xp:read:"
        const val DAY_PREFIX = "xp:day:"
        const val EARLIER_KEY = "xp:earlier"
        const val CAUGHT_UP_KEY = "xp:caught-up"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
