package com.baca.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.baca.app.reader.SheetSection

/** Keep in step with the Android versionName and the desktop packageVersion. */
internal const val APP_VERSION = "0.1.3"

private const val SOURCE_URL = "github.com/ghifariaulia/baca"

/** The About page's body, shown inside the settings frame. */
@Composable
internal fun AboutContent() {
    Column {
        SheetSection("VERSION") { AboutText("Baca $APP_VERSION") }
        SheetSection("SOURCE") { AboutText(SOURCE_URL) }
        SheetSection("ACKNOWLEDGEMENTS") {
            AboutText("Librera and foliate-js (MIT) for page layout, TeX hyphenation patterns.")
        }
        SheetSection("FONTS") {
            AboutText("Literata, DM Sans and Libron, SIL Open Font License 1.1.")
        }
    }
}

@Composable
private fun AboutText(text: String) {
    Text(text, style = Type.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
