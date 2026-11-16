package com.baca.app.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.baca.shared.domain.stats.PageOutcome
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Times how long the screen is in view (the app at least started: not in the background, nor minimised) from now
 * until the calling coroutine is cancelled, then hands the milliseconds to [done], which runs even so.
 */
suspend fun timeInView(lifecycle: Lifecycle, done: suspend (Long) -> Unit) {
    var inViewMs = 0L
    var since: TimeMark? = null
    try {
        lifecycle.currentStateFlow.map { it.isAtLeast(Lifecycle.State.STARTED) }.distinctUntilChanged().collect { inView ->
            if (inView) {
                since = since ?: TimeSource.Monotonic.markNow()
            } else {
                since?.let { inViewMs += it.elapsedNow().inWholeMilliseconds }
                since = null
            }
        }
    } finally {
        since?.let { inViewMs += it.elapsedNow().inWholeMilliseconds }
        withContext(NonCancellable) { done(inViewMs) }
    }
}

/** The note a page's credit earns, the biggest milestone first; null when it reached none. */
fun milestoneOf(outcome: PageOutcome): String? = when {
    outcome.levelUp != null -> "Level ${outcome.levelUp} reached"
    outcome.bookDone -> "Book finished · +${outcome.xp} XP"
    outcome.chapterDone -> "Chapter done · +${outcome.xp} XP"
    outcome.goalMet -> "Daily goal met · +${outcome.xp} XP"
    else -> null
}

/** A milestone note over the page for a few seconds; [note] clears itself. Chrome colours, as the reader's bars. */
@Composable
fun MilestoneNote(note: MutableState<String?>, modifier: Modifier) {
    val text = note.value ?: return
    LaunchedEffect(text) {
        delay(MILESTONE_MS)
        note.value = null
    }
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

private const val MILESTONE_MS = 3_000L
