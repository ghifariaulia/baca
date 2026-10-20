package com.baca.app.reader

/**
 * Tap zones for page turns: a phone interaction. Desktop uses keyboard.
 */
expect val tapZonesForPageTurnEnabled: Boolean

/**
 * Status and navigation bar icons dark on a light screen, light on a dark one
 * (Android draws the app behind the system bars). No-op on desktop.
 */
@androidx.compose.runtime.Composable
expect fun SystemBarsAppearance(darkBackground: Boolean)

/** Opens http(s) links outside the app (browser). */
expect fun openExternalLink(url: String)
