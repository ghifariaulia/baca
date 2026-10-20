package com.baca.app.reader

/**
 * Tap zones for page turns: a phone interaction. Desktop uses keyboard.
 */
expect val tapZonesForPageTurnEnabled: Boolean

/** Opens http(s) links outside the app (browser). */
expect fun openExternalLink(url: String)
