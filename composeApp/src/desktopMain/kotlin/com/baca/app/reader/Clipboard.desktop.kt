package com.baca.app.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
actual fun rememberCopyToClipboard(): (String) -> Unit = remember {
    { text -> Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
}
