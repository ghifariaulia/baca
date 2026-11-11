package com.baca.app.reader

import java.net.HttpURLConnection
import java.net.URL

private const val LATEST_RELEASE_URL = "https://api.github.com/repos/ghifariaulia/baca/releases/latest"
private const val TIMEOUT_MS = 10_000

actual fun fetchLatestReleaseJson(): String {
    val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        check(connection.responseCode == 200) { "GitHub answered ${connection.responseCode}" }
        return connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}
