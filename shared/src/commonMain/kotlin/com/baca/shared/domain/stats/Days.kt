package com.baca.shared.domain.stats

import kotlinx.serialization.Serializable

/** One day's reading. */
@Serializable
data class DayRecord(
    val xp: Int = 0,
    val seconds: Int = 0,
    val locations: Int = 0,
    val goalMet: Boolean = false,
)

/**
 * Days in a row with the reading goal met, counted back from [today], or from the day before when
 * today's goal isn't met yet (a streak lasts until a whole day passes without reading).
 */
fun currentStreak(days: Map<String, DayRecord>, today: String): Int {
    var day = if (days[today]?.goalMet == true) today else dayBefore(today)
    var streak = 0
    while (days[day]?.goalMet == true) {
        streak++
        day = dayBefore(day)
    }
    return streak
}

/** The longest run of days with the goal met. */
fun longestStreak(days: Map<String, DayRecord>): Int {
    var longest = 0
    var run = 0
    var previous: String? = null
    for (day in days.filterValues { it.goalMet }.keys.sorted()) {
        run = if (previous != null && dayBefore(day) == previous) run + 1 else 1
        longest = maxOf(longest, run)
        previous = day
    }
    return longest
}

/** The [count] days ending with [today], oldest first, as `yyyy-mm-dd`. */
fun lastDays(today: String, count: Int): List<String> =
    generateSequence(today, ::dayBefore).take(count).toList().reversed()

/** The day before [day] (`yyyy-mm-dd`), by the proleptic Gregorian calendar. */
fun dayBefore(day: String): String = civilOf(daysOf(day) - 1)

/** The weekday of [day] (`yyyy-mm-dd`): 0 for Monday to 6 for Sunday. */
fun weekdayOf(day: String): Int = ((daysOf(day) % 7 + 10) % 7).toInt()

// days since 1970-01-01 and back, after Howard Hinnant's civil-date algorithms
private fun daysOf(day: String): Long {
    val (y0, m, d) = day.split('-').map { it.toLong() }
    val y = if (m <= 2) y0 - 1 else y0
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
}

private fun civilOf(days: Long): String {
    val z = days + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val y = yoe + era * 400 + if (m <= 2) 1 else 0
    return "${y.toString().padStart(4, '0')}-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
}
