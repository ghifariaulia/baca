package com.baca.app.reader

import androidx.compose.runtime.Composable

/** Short label for a chosen folder (the last part of its path). */
expect fun folderLabel(path: String): String

/** Folder picker; persists access on Android. */
@Composable
expect fun rememberFolderChooser(title: String = "Choose folder", onPicked: (String?) -> Unit): () -> Unit
