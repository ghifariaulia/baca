package com.baca.app.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import okio.FileSystem
import okio.Path.Companion.toPath

/** Stable per-user folder: ~/.baca/books */
@Composable
actual fun rememberBookStoreDir(): String = remember {
    val dir = (System.getProperty("user.home").toPath() / ".baca" / "books")
    FileSystem.SYSTEM.createDirectories(dir)
    dir.toString()
}
