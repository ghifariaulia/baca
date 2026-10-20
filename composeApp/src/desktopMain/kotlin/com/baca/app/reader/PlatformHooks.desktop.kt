package com.baca.app.reader

import java.awt.Desktop
import java.net.URI

actual val tapZonesForPageTurnEnabled: Boolean = false

actual fun openExternalLink(url: String) {
    runCatching {
        Desktop.getDesktop().browse(URI(url))
    }
}
