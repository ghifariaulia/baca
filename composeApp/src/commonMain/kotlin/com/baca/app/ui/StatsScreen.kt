package com.baca.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baca.shared.domain.stats.DayRecord
import com.baca.shared.domain.stats.ReadingStats
import com.baca.shared.domain.stats.StatsSummary
import com.baca.shared.domain.stats.XpRules
import com.baca.shared.domain.stats.grouped
import com.baca.shared.domain.stats.weekdayOf

/** Reading stats (TECH 10.9): level, streak, today against the goal, the week, totals and achievements. */
@Composable
fun StatsScreen(
    summary: StatsSummary?,
    onGoalMinutesChange: ((Int) -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Stats", style = Type.screenTitle)
            if (summary == null) return@Column
            val goalSeconds = summary.goalMinutes * 60
            LevelCard(summary)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 360.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Tile("Streak", days(summary.streak), "Best ${days(summary.longestStreak)}", Modifier.weight(1f).fillMaxHeight())
                            Tile(
                                "Today",
                                minutes(summary.today.seconds.toLong()),
                                if (summary.today.goalMet) "Goal met" else "Goal ${minutes(goalSeconds.toLong())}",
                                Modifier.weight(1f).fillMaxHeight(),
                                progress = summary.today.seconds / goalSeconds.toFloat(),
                            )
                        }
                        Tile("Finished", "${summary.booksFinished}", if (summary.booksFinished == 1) "book" else "books", Modifier.fillMaxWidth())
                    }
                } else {
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Tile("Streak", days(summary.streak), "Best ${days(summary.longestStreak)}", Modifier.weight(1f).fillMaxHeight())
                        Tile(
                            "Today",
                            minutes(summary.today.seconds.toLong()),
                            if (summary.today.goalMet) "Goal met" else "Goal ${minutes(goalSeconds.toLong())}",
                            Modifier.weight(1f).fillMaxHeight(),
                            progress = summary.today.seconds / goalSeconds.toFloat(),
                        )
                        Tile("Finished", "${summary.booksFinished}", if (summary.booksFinished == 1) "book" else "books", Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
            if (onGoalMinutesChange != null) {
                DailyGoalCard(summary.goalMinutes, onGoalMinutesChange)
            }
            Week(summary)
            if (summary.history.isNotEmpty()) {
                HistoryCalendar(summary.history)
            }
            Totals(summary)
            Achievements(summary)
        }
    }
}

@Composable
private fun DailyGoalCard(goalMinutes: Int, onChange: (Int) -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("DAILY GOAL", style = Type.sectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text("$goalMinutes min / day", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { onChange((goalMinutes - 5).coerceAtLeast(ReadingStats.MIN_GOAL_MINUTES)) },
                    enabled = goalMinutes > ReadingStats.MIN_GOAL_MINUTES,
                    modifier = Modifier.semantics { contentDescription = "Decrease daily goal" },
                ) {
                    Text("−", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = { onChange((goalMinutes + 5).coerceAtMost(ReadingStats.MAX_GOAL_MINUTES)) },
                    enabled = goalMinutes < ReadingStats.MAX_GOAL_MINUTES,
                    modifier = Modifier.semantics { contentDescription = "Increase daily goal" },
                ) {
                    Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HistoryCalendar(history: List<Pair<String, DayRecord>>) {
    var selected by remember(history) { mutableStateOf(history.lastOrNull()) }
    Card {
        Text("HISTORY", style = Type.sectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            history.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    week.forEach { entry ->
                        val (day, record) = entry
                        val isSelected = selected?.first == day
                        val fill = when {
                            record.goalMet -> MaterialTheme.colorScheme.primary
                            record.seconds > 0 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            else -> MaterialTheme.colorScheme.background
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .height(28.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(fill)
                                .then(
                                    if (isSelected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(4.dp))
                                    else Modifier,
                                )
                                .semantics { contentDescription = "Day $day" }
                                .clickable { selected = entry },
                        )
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        selected?.let { (day, record) ->
            Spacer(Modifier.height(8.dp))
            Text(
                "$day · ${minutes(record.seconds.toLong())} · ${record.locations} pages · ${record.xp} XP",
                style = Type.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LevelCard(summary: StatsSummary) {
    val level = summary.level
    Card {
        Text("LEVEL", style = Type.sectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("${level.number}", fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text("${summary.xp} XP", style = Type.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Bar(level.xpIntoLevel / level.xpForNext.toFloat(), 8.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            "${level.xpForNext - level.xpIntoLevel} XP to level ${level.number + 1}",
            style = Type.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Tile(label: String, value: String, caption: String, modifier: Modifier, progress: Float? = null) {
    Card(modifier) {
        Text(label.uppercase(), style = Type.sectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        progress?.let {
            Bar(it, 4.dp)
            Spacer(Modifier.height(4.dp))
        }
        Text(caption, style = Type.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Week(summary: StatsSummary) {
    val most = summary.week.maxOf { it.second.seconds }.coerceAtLeast(summary.goalMinutes * 60)
    Card {
        Text("THIS WEEK", style = Type.sectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            summary.week.forEach { (day, record) ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        val share = record.seconds / most.toFloat()
                        if (share > 0f) {
                            Box(
                                Modifier.fillMaxWidth().fillMaxHeight(share.coerceIn(0.04f, 1f))
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(if (record.goalMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(WEEKDAYS[weekdayOf(day)], style = Type.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Totals(summary: StatsSummary) {
    Card {
        Text("TOTALS", style = Type.sectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Fact("Pages read", "${summary.locations}")
        Fact("Time read", minutes(summary.seconds))
    }
}

@Composable
private fun Achievements(summary: StatsSummary) {
    Card {
        Text("ACHIEVEMENTS", style = Type.sectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        summary.achievements.forEach { achievement ->
            val reached = achievement.tier > 0
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
                Icon(
                    if (reached) Icons.Filled.CheckCircle else Icons.Outlined.Lock,
                    contentDescription = if (reached) "Reached" else "Not yet reached",
                    tint = if (reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(achievement.title, style = Type.cardTitle)
                    Text(achievement.description, style = Type.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val next = achievement.nextGoal
                    if (next == null) {
                        Text("Every tier reached", style = Type.caption, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) { Bar(achievement.value / next.toFloat(), 4.dp) }
                            Text("${grouped(achievement.value)} / ${grouped(next)}", style = Type.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = Type.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = Type.body, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier.fillMaxWidth(), content: @Composable () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}

@Composable
private fun Bar(share: Float, height: androidx.compose.ui.unit.Dp) {
    Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(MaterialTheme.colorScheme.outline)) {
        Box(
            Modifier.fillMaxWidth(share.coerceIn(0f, 1f)).height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

private fun days(count: Int) = if (count == 1) "1 day" else "$count days"

private fun minutes(seconds: Long): String {
    val total = seconds / 60
    return if (total < 60) "$total min" else "${total / 60} h ${total % 60} min"
}

private val WEEKDAYS = listOf("M", "T", "W", "T", "F", "S", "S")
