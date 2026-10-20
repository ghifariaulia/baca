package com.baca.app.reader

import androidx.compose.runtime.Composable
import okio.Sink

/**
 * Save dialog for a library backup. [onPicked] receives a way to open the
 * chosen destination, or null on cancel.
 */
@Composable
expect fun rememberBackupSaver(onPicked: ((() -> Sink)?) -> Unit): () -> Unit

/**
 * Open dialog for a library backup. [onPicked] receives a local file path
 * (on Android, a copy in the app cache) or null on cancel.
 */
@Composable
expect fun rememberBackupOpener(onPicked: (String?) -> Unit): () -> Unit

internal const val BACKUP_FILE_NAME = "baca-backup.zip"
