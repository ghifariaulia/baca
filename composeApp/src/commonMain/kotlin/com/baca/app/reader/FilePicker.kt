package com.baca.app.reader

import androidx.compose.runtime.Composable

/**
 * Platform file picker for book files (epub/fb2/txt/cbz). [onPicked] receives
 * staged local paths (already copied out of the SAF/cloud) or an empty list
 * on cancel. Desktop picks one file; Android picks several.
 */
@Composable
expect fun rememberBookPicker(onPicked: (List<String>) -> Unit): () -> Unit

/**
 * Folder picker for "import every supported file in a folder" (TECH 8.3).
 * [onPicked] receives local paths of the folder's supported book files (top
 * level only; staged copies on Android), or an empty list on cancel.
 */
@Composable
expect fun rememberFolderPicker(onPicked: (List<String>) -> Unit): () -> Unit
