package com.baca.app.reader

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

@Composable
actual fun rememberSpeech(language: String?): Speech? {
    var ready by remember { mutableStateOf<AndroidSpeech?>(null) }
    DisposableEffect(language) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(AndroidContexts.current) { status ->
            val tts = engine ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) {
                Log.w("Baca", "text-to-speech engine failed to start: $status")
                return@TextToSpeech
            }
            language?.let { Locale.forLanguageTag(it) }
                ?.takeIf { tts.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
                ?.let { tts.language = it }
            ready = AndroidSpeech(tts)
        }
        onDispose {
            ready = null
            engine?.stop()
            engine?.shutdown()
        }
    }
    return ready
}

private class AndroidSpeech(private val tts: TextToSpeech) : Speech {
    private val waiting = ConcurrentHashMap<String, CancellableContinuation<Unit>>()

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {}
            override fun onDone(utteranceId: String) = finish(utteranceId)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) = finish(utteranceId)

            override fun onError(utteranceId: String, errorCode: Int) {
                // a sentence the engine can't say is passed over rather than ending the reading
                Log.w("Baca", "text-to-speech could not say an utterance: $errorCode")
                finish(utteranceId)
            }

            override fun onStop(utteranceId: String, interrupted: Boolean) = finish(utteranceId)
        })
    }

    private fun finish(id: String) {
        waiting.remove(id)?.takeIf { it.isActive }?.resume(Unit)
    }

    override suspend fun say(text: String) = suspendCancellableCoroutine { continuation ->
        val id = UUID.randomUUID().toString()
        waiting[id] = continuation
        continuation.invokeOnCancellation {
            waiting.remove(id)
            tts.stop()
        }
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR) finish(id)
    }
}
