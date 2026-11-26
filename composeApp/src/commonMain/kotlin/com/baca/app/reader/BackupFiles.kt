package com.baca.app.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
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

/** Save dialog for exported Markdown notes. [onPicked] receives a sink factory, or null on cancel. */
@Composable
expect fun rememberMarkdownSaver(suggestedName: String, onPicked: ((() -> Sink)?) -> Unit): () -> Unit

/** Save dialog for exported PNG images. [onPicked] receives a sink factory, or null on cancel. */
@Composable
expect fun rememberPngSaver(suggestedName: String, onPicked: ((() -> Sink)?) -> Unit): () -> Unit

/** Encodes an [ImageBitmap] as PNG bytes. */
expect fun encodePng(bitmap: ImageBitmap): ByteArray

internal const val BACKUP_FILE_NAME = "baca-backup.zip"
