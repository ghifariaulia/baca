package com.baca.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.baca.app.reader.SheetSection

/** Keep in step with the Android versionName and the desktop packageVersion. */
internal const val APP_VERSION = "0.1.2"

private const val SOURCE_URL = "github.com/ghifariaulia/baca"

/** The About page's body, shown inside the settings frame. */
@Composable
internal fun AboutContent() {
    Column {
        SheetSection("VERSION") { AboutText("Baca $APP_VERSION") }
        SheetSection("WHAT IT IS") {
            AboutText(
                "An e-reader for Android and desktop. There is no account and no server: your books, " +
                    "positions, highlights, notes and bookmarks stay on your device. To move them to another " +
                    "device, create a backup in Settings and restore it there.",
            )
        }
        SheetSection("SOURCE") { AboutText(SOURCE_URL) }
        SheetSection("ACKNOWLEDGEMENTS") {
            AboutText(
                "Pages are laid out to match Librera's reader, which is built on foliate-js by John Factotum (MIT License). " +
                    "English hyphenation uses the TeX hyphenation patterns; on Android other languages use the system's.",
            )
        }
        SheetSection("FONTS") {
            AboutText("Literata, DM Sans and Libron, under the SIL Open Font License 1.1.")
        }
    }
}

@Composable
private fun AboutText(text: String) {
    Text(text, style = Type.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
