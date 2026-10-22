package com.baca.app.reader

import java.io.File

internal actual fun systemHyphenData(locale: String): ByteArray? =
    File("/system/usr/hyphen-data/hyph-$locale.hyb").takeIf { it.canRead() }?.readBytes()
