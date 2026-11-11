package com.baca.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.baca.app.reader.ChoiceChip
import com.baca.app.reader.SheetSection
import com.baca.app.reader.openExternalLink
import com.baca.shared.domain.UpdateStatus
import kotlinx.coroutines.launch

/** Keep in step with the Android versionName and the desktop packageVersion. */
internal const val APP_VERSION = "0.1.3"

private const val SOURCE_URL = "github.com/ghifariaulia/baca"

/** The About page's body, shown inside the settings frame. */
@Composable
internal fun AboutContent(checkForUpdate: suspend () -> UpdateStatus) {
    Column {
        SheetSection("VERSION") {
            AboutText("Baca $APP_VERSION")
            UpdateCheck(checkForUpdate)
        }
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

/** A button that asks for the latest release, then says what it found; nothing is sent until it is pressed. */
@Composable
private fun UpdateCheck(checkForUpdate: suspend () -> UpdateStatus) {
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<UpdateStatus?>(null) }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceChip(if (checking) "Checking…" else "Check for updates", !checking) {
            if (checking) return@ChoiceChip
            checking = true
            scope.launch {
                status = checkForUpdate()
                checking = false
            }
        }
        (status as? UpdateStatus.Available)?.let { ChoiceChip("Download ${it.version}", false) { openExternalLink(it.pageUrl) } }
    }
    status?.let { result ->
        Spacer(Modifier.height(8.dp))
        AboutText(
            when (result) {
                UpdateStatus.UpToDate -> "You have the latest version."
                is UpdateStatus.Available -> "Version ${result.version} is available."
                is UpdateStatus.Failed -> "Couldn't check: ${result.reason}"
            },
        )
    }
}
