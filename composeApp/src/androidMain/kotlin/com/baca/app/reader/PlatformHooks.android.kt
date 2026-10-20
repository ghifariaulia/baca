package com.baca.app.reader

import android.content.Context
import android.content.Intent
import android.app.Activity
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

actual val tapZonesForPageTurnEnabled: Boolean = true

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

actual fun openExternalLink(url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        AndroidContexts.current.startActivity(intent)
    }
}
