package com.baca.app.reader

import androidx.compose.foundation.layout.safeDrawing
import java.awt.Desktop
import java.net.URI

actual val tapZonesForPageTurnEnabled: Boolean = false

@androidx.compose.runtime.Composable
actual fun SystemBarsAppearance(darkBackground: Boolean) {
    // the desktop title bar follows the app theme from Main.kt at launch
}

@androidx.compose.runtime.Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // no system Back on desktop
}

actual fun openExternalLink(url: String) {
    runCatching {
        Desktop.getDesktop().browse(URI(url))
    }
}

actual fun clockText(): String =
    java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

// a laptop's battery is already on screen in the menu bar
actual fun batteryPercent(): Int? = null

@androidx.compose.runtime.Composable
actual fun FullScreen(enabled: Boolean) {
    // a desktop window has no system bars to hide
}

@androidx.compose.runtime.Composable
actual fun stableReaderInsets(): androidx.compose.foundation.layout.WindowInsets =
    androidx.compose.foundation.layout.WindowInsets.safeDrawing
