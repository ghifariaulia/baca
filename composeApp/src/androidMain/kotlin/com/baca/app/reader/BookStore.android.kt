package com.baca.app.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.io.File

@Composable
actual fun rememberBookStoreDir(): String =
    File(LocalContext.current.filesDir, "books").absolutePath
