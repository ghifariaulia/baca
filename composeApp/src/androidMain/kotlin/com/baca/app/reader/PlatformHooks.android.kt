package com.baca.app.reader

import android.content.Context
import android.content.Intent
import android.app.Activity
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union

actual val tapZonesForPageTurnEnabled: Boolean = true

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

actual fun openExternalLink(url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        AndroidContexts.current.startActivity(intent)
    }
}

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
actual fun stableReaderInsets(): androidx.compose.foundation.layout.WindowInsets {
    val insets = androidx.compose.foundation.layout.WindowInsets
    // not the keyboard: the note sheet moves above it, the page behind stays as it is
    return insets.systemBarsIgnoringVisibility.union(insets.displayCutout)
}
