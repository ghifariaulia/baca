package com.baca.app.reader

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
actual fun Modifier.seekHover(onEnter: () -> Unit, onExit: () -> Unit): Modifier =
    this
        .onPointerEvent(PointerEventType.Enter) { onEnter() }
        .onPointerEvent(PointerEventType.Exit) { onExit() }
