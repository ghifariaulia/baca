package com.baca.shared.domain.stats

/** Formats a [StatsSummary] into a concise, shareable plain-text summary. */
fun formatStatsShareText(summary: StatsSummary): String = buildString {
    appendLine("Baca Reading Stats")
    appendLine("Level ${summary.level.number} · ${grouped(summary.xp.toLong())} XP")
    appendLine("Streak: ${summary.streak} ${if (summary.streak == 1) "day" else "days"} (best ${summary.longestStreak})")
    appendLine("Books finished: ${summary.booksFinished}")
    appendLine("Pages read: ${grouped(summary.locations.toLong())}")
    append("Time read: ${formatDuration(summary.seconds)}")
}

private fun formatDuration(seconds: Long): String {
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
