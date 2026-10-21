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

/**
 * Handles the system Back gesture/button while [enabled] (Android). The most
 * recently composed enabled handler wins, so inner screens and sheets go
 * first. No-op on desktop, which has no system Back.
 */
@androidx.compose.runtime.Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/** Opens http(s) links outside the app (browser). */
expect fun openExternalLink(url: String)

/** The time now, in the device's 12/24-hour style (reader footer). */
expect fun clockText(): String

/** Battery charge in percent; null where there is no battery to report (desktop). */
expect fun batteryPercent(): Int?

/** Hides the phone's status and navigation bars while [enabled] (a swipe from the edge shows them briefly). No-op on desktop. */
@androidx.compose.runtime.Composable
expect fun FullScreen(enabled: Boolean)

/** The reader's safe area, measured as if the system bars were showing, so [FullScreen] doesn't move the page. */
@androidx.compose.runtime.Composable
expect fun stableReaderInsets(): androidx.compose.foundation.layout.WindowInsets
