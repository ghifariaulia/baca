package com.baca.app.reader

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
