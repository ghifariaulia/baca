package com.baca.shared.domain.stats

/** An achievement in tiers: [value] counts toward [goals], and each goal reached is a tier; [asks] says what each goal asks. */
data class Achievement(val name: String, val value: Long, val goals: List<Long>, val asks: List<String>) {
    /** tiers reached, 0 before the first */
    val tier: Int get() = goals.count { value >= it }

    /** the goal still ahead, null once every tier is reached */
    val nextGoal: Long? get() = goals.getOrNull(tier)

    /** "Bookworm III"; the bare name before the first tier */
    val title: String get() = if (tier == 0) name else "$name ${ROMAN[tier - 1]}"

    /** what the next tier asks, or the last one once all are reached */
    val description: String get() = asks[tier.coerceAtMost(asks.lastIndex)]
}

/** The achievements, each in tiers, from what was read. [seconds] is reading time since stats were kept. */
fun achievementsOf(booksFinished: Int, longestStreak: Int, locations: Int, seconds: Long, level: Int): List<Achievement> = listOf(
    tiers("Bookworm", booksFinished.toLong(), 1, 5, 10, 25, 50, 100) { if (it == 1L) "Finish a book" else "Finish $it books" },
    tiers("On a roll", longestStreak.toLong(), 3, 7, 14, 30, 100) { "Meet the daily goal $it days in a row" },
    tiers("Page turner", locations.toLong(), 100, 1_000, 5_000, 10_000, 50_000) { "Read ${grouped(it)} pages" },
    tiers("Devoted", seconds / 3600, 1, 10, 50, 100, 500) { if (it == 1L) "Read for an hour" else "Read for $it hours" },
    tiers("Seasoned", level.toLong(), 5, 10, 20, 30, 50) { "Reach level $it" },
)

private fun tiers(name: String, value: Long, vararg goals: Long, ask: (Long) -> String) =
    Achievement(name, value, goals.toList(), goals.map(ask))

/** [n] with its thousands set apart: 50,000. */
fun grouped(n: Long): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

private val ROMAN = listOf("I", "II", "III", "IV", "V", "VI")
