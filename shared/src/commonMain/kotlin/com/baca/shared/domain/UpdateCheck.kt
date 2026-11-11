package com.baca.shared.domain

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What asking GitHub for the latest release came to. */
sealed interface UpdateStatus {
    data object UpToDate : UpdateStatus
    data class Available(val version: String, val pageUrl: String) : UpdateStatus
    data class Failed(val reason: String) : UpdateStatus
}

@Serializable
private data class LatestRelease(
    @SerialName("tag_name") val tag: String,
    @SerialName("html_url") val pageUrl: String,
)

private val json = Json { ignoreUnknownKeys = true }

/**
 * Compares [current] with the release [fetchLatest] returns (the body of GitHub's
 * "latest release" API). Network errors and unreadable answers come back as [UpdateStatus.Failed].
 */
suspend fun checkForUpdate(current: String, fetchLatest: suspend () -> String): UpdateStatus {
    val body = try {
        fetchLatest()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        return UpdateStatus.Failed(e.message ?: "Network error")
    }
    val release = try {
        json.decodeFromString<LatestRelease>(body)
    } catch (e: Exception) {
        return UpdateStatus.Failed("Unexpected answer")
    }
    val latest = release.tag.removePrefix("v")
    val latestParts = versionParts(latest) ?: return UpdateStatus.Failed("Unexpected version ${release.tag}")
    val currentParts = versionParts(current) ?: return UpdateStatus.Failed("Unexpected version $current")
    return if (compareVersions(latestParts, currentParts) > 0) UpdateStatus.Available(latest, release.pageUrl) else UpdateStatus.UpToDate
}

private fun versionParts(version: String): List<Int>? =
    version.split('.').map { it.toIntOrNull() ?: return null }

private fun compareVersions(a: List<Int>, b: List<Int>): Int {
    for (i in 0 until maxOf(a.size, b.size)) {
        val diff = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
        if (diff != 0) return diff
    }
    return 0
}
