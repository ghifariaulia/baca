package com.baca.app.reader

/**
 * Tap zones for page turns: a phone interaction. Desktop uses keyboard.
 */
expect val tapZonesForPageTurnEnabled: Boolean

/**
 * Whether the reader holds its page to a column on a wide window (desktop). A phone or
 * tablet reads across the whole window, as LibreraX does.
 */
expect val readerColumnCapped: Boolean

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

/**
 * Deletes the cache copy a phone's file picker made of a book once it is imported. No-op on desktop,
 * whose picker hands out the user's own files.
 */
expect fun discardPickedCopy(path: String)

/** Opens http(s) links outside the app (browser). */
expect fun openExternalLink(url: String)

/** The time now, in the device's 12/24-hour style (reader footer). */
expect fun clockText(): String

/** Battery charge in percent; null where there is no battery to report (desktop). */
expect fun batteryPercent(): Int?

/** Hides the phone's status and navigation bars while [enabled] (a swipe from the edge shows them briefly). No-op on desktop. */
@androidx.compose.runtime.Composable
expect fun FullScreen(enabled: Boolean)

/**
 * The reader's safe area, measured as if the system bars were showing, so toggling them doesn't move the page.
 * With [fullScreen] on the bars come up over the page when they are shown, as in LibreraX, so the page keeps
 * clear only of a camera cutout (none from Android 15) and the top and bottom margins set the whole gap; the reader's own bars make
 * room for the system's. With it off the page stays clear of the bars.
 */
@androidx.compose.runtime.Composable
expect fun stableReaderInsets(fullScreen: Boolean): androidx.compose.foundation.layout.WindowInsets
