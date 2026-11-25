package com.baca.app.reader

import androidx.compose.runtime.Composable

/** The system's voice, reading the book aloud. */
interface Speech {
    /** Called when reading starts; [onExternalStop] fires if stopped from a system notification. */
    fun start(title: String, onExternalStop: () -> Unit) {}

    /** Says [text] and returns once it has been said; cancelling the call stops the voice. */
    suspend fun say(text: String)

    /** Called when reading stops (naturally, by user, or on dispose). */
    fun stop() {}
}

/**
 * The system's voice for the book's [language] (a BCP 47 tag; the voice's own when null or not installed),
 * or null where there is none: until Android's engine is ready, without one, and on Windows and Linux.
 */
@Composable
expect fun rememberSpeech(language: String?): Speech?
