package com.baca.shared.parser

internal actual fun decodeText(bytes: ByteArray, charset: String): String = String(bytes, java.nio.charset.Charset.forName(charset))
