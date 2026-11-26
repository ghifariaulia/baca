package com.baca.shared.domain

import com.baca.shared.domain.reader.PositionStore

const val KEY_AUTO_UPDATE_CHECK = "auto_update_check"
const val KEY_UPDATE_LAST_CHECKED_AT = "update_last_checked_at"
const val KEY_UPDATE_SKIPPED_VERSION = "update_skipped_version"
const val AUTO_UPDATE_COOLDOWN_MS = 24L * 60 * 60 * 1000

/** Returns whether automatic startup update checks are enabled (defaults to true). */
fun PositionStore.loadAutoUpdateEnabled(): Boolean =
    getLocal(KEY_AUTO_UPDATE_CHECK) != "0"

fun PositionStore.saveAutoUpdateEnabled(enabled: Boolean) {
    putLocal(KEY_AUTO_UPDATE_CHECK, if (enabled) "1" else "0")
}

fun PositionStore.loadLastUpdateCheckAt(): Long? =
    getLocal(KEY_UPDATE_LAST_CHECKED_AT)?.toLongOrNull()

fun PositionStore.saveLastUpdateCheckAt(nowMs: Long) {
    putLocal(KEY_UPDATE_LAST_CHECKED_AT, nowMs.toString())
}

fun PositionStore.loadSkippedUpdateVersion(): String? =
    getLocal(KEY_UPDATE_SKIPPED_VERSION)?.takeIf { it.isNotBlank() }

fun PositionStore.saveSkippedUpdateVersion(version: String) {
    putLocal(KEY_UPDATE_SKIPPED_VERSION, version)
}

/** Returns true if automatic update checking is enabled and the cooldown since [lastCheckedAt] has elapsed. */
fun shouldCheckForUpdate(
    enabled: Boolean,
    lastCheckedAt: Long?,
    nowMs: Long,
    cooldownMs: Long = AUTO_UPDATE_COOLDOWN_MS,
): Boolean {
    if (!enabled) return false
    if (lastCheckedAt == null) return true
    return nowMs - lastCheckedAt >= cooldownMs
}

/** Returns [status] if it is an available update that has not been skipped by the user. */
fun shouldShowUpdatePopup(status: UpdateStatus, skippedVersion: String?): UpdateStatus.Available? {
    val available = status as? UpdateStatus.Available ?: return null
    return if (available.version == skippedVersion) null else available
}
