package com.baca.app.reader

import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

// macOS's `say` speaks with the system voice; Windows and Linux have no voice we can count on being there
@Composable
actual fun rememberSpeech(language: String?): Speech? =
    if (System.getProperty("os.name").startsWith("Mac")) MacSpeech else null

private object MacSpeech : Speech {
    override suspend fun say(text: String) {
        // the text goes in on stdin, so a sentence starting with "-" isn't read as an option
        val process = ProcessBuilder("say", "-f", "-").start()
        try {
            process.outputStream.bufferedWriter().use { it.write(text) }
            runInterruptible(Dispatchers.IO) { process.waitFor() }
        } finally {
            process.destroy()
        }
    }
}
