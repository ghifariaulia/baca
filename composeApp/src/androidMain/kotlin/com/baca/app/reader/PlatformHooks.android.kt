package com.baca.app.reader

import android.content.Context
import android.content.Intent
import android.net.Uri

actual val tapZonesForPageTurnEnabled: Boolean = true

/** Application context is captured once at startup by MainActivity. */
object AndroidContexts {
    lateinit var current: Context
}

actual fun openExternalLink(url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        AndroidContexts.current.startActivity(intent)
    }
}
