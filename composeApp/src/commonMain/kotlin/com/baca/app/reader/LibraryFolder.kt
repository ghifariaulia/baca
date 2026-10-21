package com.baca.app.reader

import androidx.compose.runtime.Composable

/** A supported book file in the library folder; [location] is the library's path for it. */
data class FolderBook(val location: String, val name: String)

/**
 * The user's library folder, read in place (TECH 5.4): books are not copied
 * into app storage. Desktop locations are file paths; Android locations are
 * document URIs inside a persisted folder grant.
 */
interface LibraryFolder {
    /** Supported book files in the folder and all its subfolders. */
    fun books(): List<FolderBook>

    /** A local file with the content of [location]; on Android a temporary copy. */
    fun stage(location: String): String

    /** Releases what [stage] created; no-op where nothing was copied. */
    fun unstage(path: String)

    /** Copies the file at [path] into the folder's top level; returns its location. */
    fun add(path: String): String

    fun contains(location: String): Boolean
}

/** The library folder for a stored setting value, or null when none is set. */
@Composable
expect fun rememberLibraryFolder(location: String?): LibraryFolder?
