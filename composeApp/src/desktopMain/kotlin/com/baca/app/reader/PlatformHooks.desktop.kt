package com.baca.app.reader

import androidx.compose.foundation.layout.safeDrawing
import java.awt.Desktop
import java.net.URI
import com.baca.shared.domain.reader.webDictionaryUrl

actual val tapZonesForPageTurnEnabled: Boolean = false

actual val hardwareReaderControlsSupported: Boolean = false

@androidx.compose.runtime.Composable
actual fun ApplyScreenBrightness(brightness: Float) {
    // desktop windows do not override display brightness
}

@androidx.compose.runtime.Composable
actual fun PlatformVolumeKeyHandler(enabled: Boolean, onTurn: (delta: Int) -> Unit) {
    // desktop does not intercept hardware volume keys
}

actual val readerColumnCapped: Boolean = true

@androidx.compose.runtime.Composable
actual fun SystemBarsAppearance(darkBackground: Boolean) {
    // the desktop title bar follows the app theme from Main.kt at launch
}

@androidx.compose.runtime.Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // no system Back on desktop
}

actual fun discardPickedCopy(path: String) {}

actual fun openExternalLink(url: String) {
    runCatching {
        Desktop.getDesktop().browse(URI(url))
    }
}

// macOS has Dictionary.app behind the dict: scheme; elsewhere there is no system dictionary to ask
actual fun lookUpWord(term: String) {
    if (System.getProperty("os.name").startsWith("Mac")) {
        runCatching { Desktop.getDesktop().browse(URI("dict", "//$term", null)) }
    } else {
        openExternalLink(webDictionaryUrl(term))
    }
}

actual fun todayKey(): String = java.time.LocalDate.now().toString()

actual fun clockText(): String =
    java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

// a laptop's battery is already on screen in the menu bar
actual fun batteryPercent(): Int? = null

@androidx.compose.runtime.Composable
actual fun FullScreen(enabled: Boolean) {
    // a desktop window has no system bars to hide
}

@androidx.compose.runtime.Composable
actual fun stableReaderInsets(fullScreen: Boolean): androidx.compose.foundation.layout.WindowInsets =
    androidx.compose.foundation.layout.WindowInsets.safeDrawing
