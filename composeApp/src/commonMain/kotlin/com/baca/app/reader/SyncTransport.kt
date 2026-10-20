package com.baca.app.reader

import androidx.compose.runtime.Composable
import com.baca.shared.sync.SyncTransport

/**
 * Transport for the stored sync folder path (desktop path / Android SAF tree
 * URI), or null when unset/invalid.
 */
@Composable
expect fun rememberSyncTransport(folderPath: String?): SyncTransport?

/** Short label shown in the transport row ("iCloud Drive / Baca" style). */
expect fun folderLabel(path: String): String

/** Folder picker for the sync folder; persists access on Android (TECH 11.1). */
@Composable
expect fun rememberSyncFolderPicker(onPicked: (String?) -> Unit): () -> Unit
