package com.baca.app.reader

import androidx.compose.ui.Modifier

/** Hover events for the seek bar; no-op on touch-only platforms. */
expect fun Modifier.seekHover(onEnter: () -> Unit, onExit: () -> Unit): Modifier
