package com.baca.app.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.baca.shared.render.HybHyphenator
import com.baca.shared.render.Hyphenator
import com.baca.shared.render.hyphenLocaleOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A hyphenator for each of the book's [languages], read off the main thread: the system's
 * own patterns where it has them for the language (Android's, which a WebView hyphenates
 * with), else the ones Baca carries; null until they're read, so pages aren't measured
 * without them.
 */
@Composable
internal fun rememberHyphenators(languages: Set<String>): State<Map<String, Hyphenator?>?> =
    produceState<Map<String, Hyphenator?>?>(null, languages) {
        value = withContext(Dispatchers.IO) {
            languages.associateWith { language ->
                val locale = hyphenLocaleOf(language)
                // Chromium leaves capitalized words whole in English only
                systemHyphenData(locale)?.let { HybHyphenator(it, capitalized = !locale.startsWith("en")) }
                    ?: Hyphenator.forLanguage(language)
            }
        }
    }

/** The system's compiled patterns for [locale] (`hyph-<locale>.hyb`); null where it has none. Blocking. */
internal expect fun systemHyphenData(locale: String): ByteArray?
