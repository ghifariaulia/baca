package com.baca.app.reader

import android.content.Context
import android.content.Intent
import android.app.Activity
import android.net.Uri
import com.baca.shared.domain.reader.webDictionaryUrl
import java.io.File
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union

actual val tapZonesForPageTurnEnabled: Boolean = true

actual val hardwareReaderControlsSupported: Boolean = true

@Composable
actual fun ApplyScreenBrightness(brightness: Float) {
    val view = LocalView.current
    androidx.compose.runtime.DisposableEffect(brightness) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val lp = window.attributes
            lp.screenBrightness = if (brightness < 0f) -1f else brightness.coerceIn(0.01f, 1f)
            window.attributes = lp
        }
        onDispose {
            if (window != null) {
                val lp = window.attributes
                lp.screenBrightness = -1f
                window.attributes = lp
            }
        }
    }
}

private var activeVolumeTurnHandler: ((Int) -> Unit)? = null

fun dispatchVolumeKeyTurn(delta: Int): Boolean {
    val handler = activeVolumeTurnHandler ?: return false
    handler(delta)
    return true
}

@Composable
actual fun PlatformVolumeKeyHandler(enabled: Boolean, onTurn: (delta: Int) -> Unit) {
    val currentTurn by androidx.compose.runtime.rememberUpdatedState(onTurn)
    androidx.compose.runtime.DisposableEffect(enabled) {
        if (enabled) {
            val callback: (Int) -> Unit = { delta -> currentTurn(delta) }
            activeVolumeTurnHandler = callback
            onDispose {
                if (activeVolumeTurnHandler === callback) activeVolumeTurnHandler = null
            }
        } else {
            onDispose {}
        }
    }
}

actual val readerColumnCapped: Boolean = false

/** Application context is captured once at startup by MainActivity. */
object AndroidContexts {
    lateinit var current: Context
}

@Composable
actual fun SystemBarsAppearance(darkBackground: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkBackground
            isAppearanceLightNavigationBars = !darkBackground
        }
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}

actual fun discardPickedCopy(path: String) {
    // the picker gives each copy a folder of its own in the cache (copyToCache)
    File(path).parentFile?.takeIf { it.parentFile == AndroidContexts.current.cacheDir && it.name.startsWith(STAGED_PREFIX) }?.deleteRecursively()
}

/**
 * Books copied into the cache to be imported or read, which nothing deleted when an app was closed
 * mid-way or by an older version: gone at start-up, when none is in use.
 */
fun clearStagedBooks(context: Context) {
    context.cacheDir.listFiles { file -> file.name.startsWith(STAGED_PREFIX) }?.forEach { it.deleteRecursively() }
}

actual fun openExternalLink(url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        AndroidContexts.current.startActivity(intent)
    }
}

// dictionary and translation apps take selected text through ACTION_PROCESS_TEXT (the manifest's <queries> lets us see them)
actual fun lookUpWord(term: String) {
    val context = AndroidContexts.current
    val intent = Intent(Intent.ACTION_PROCESS_TEXT)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_PROCESS_TEXT, term)
        .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
    if (context.packageManager.queryIntentActivities(intent, 0).isEmpty()) {
        openExternalLink(webDictionaryUrl(term))
        return
    }
    val chooser = Intent.createChooser(intent, "Look up").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

actual fun todayKey(): String = java.time.LocalDate.now().toString()

actual fun clockText(): String =
    android.text.format.DateFormat.getTimeFormat(AndroidContexts.current).format(java.util.Date())

actual fun batteryPercent(): Int? {
    val manager = AndroidContexts.current.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
    return manager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
}

@Composable
actual fun FullScreen(enabled: Boolean) {
    val view = LocalView.current
    androidx.compose.runtime.DisposableEffect(enabled) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val bars = androidx.core.view.WindowInsetsCompat.Type.systemBars()
        controller?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (enabled) controller?.hide(bars) else controller?.show(bars)
        // leaving the reader brings the bars back
        onDispose { controller?.show(bars) }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
actual fun stableReaderInsets(fullScreen: Boolean): androidx.compose.foundation.layout.WindowInsets {
    val insets = androidx.compose.foundation.layout.WindowInsets
    // not the keyboard: the note sheet moves above it, the page behind stays as it is
    // As LibreraX does: in full screen the bars come up over the page instead of making room, so the top and
    // bottom margins are the whole gap. Up to Android 14 the page keeps clear of the camera's cut-out;
    // from 15 it runs the whole screen, camera and all, as the system itself has it there.
    if (fullScreen) {
        return if (android.os.Build.VERSION.SDK_INT >= 35) androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0) else insets.displayCutout
    }
    return insets.systemBarsIgnoringVisibility.union(insets.displayCutout)
}
