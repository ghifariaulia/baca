package com.baca.shared.domain

import com.baca.shared.db.DbPositionStore
import com.baca.shared.db.inMemoryDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoUpdatePolicyTest {

    @Test
    fun `auto update defaults to enabled and persists toggle`() {
        val (db, _) = inMemoryDb()
        val store = DbPositionStore(db, now = { 100L })

        assertTrue(store.loadAutoUpdateEnabled())
        store.saveAutoUpdateEnabled(false)
        assertFalse(store.loadAutoUpdateEnabled())
        store.saveAutoUpdateEnabled(true)
        assertTrue(store.loadAutoUpdateEnabled())
    }

    @Test
    fun `shouldCheckForUpdate respects enabled flag and cooldown`() {
        assertFalse(shouldCheckForUpdate(enabled = false, lastCheckedAt = null, nowMs = 10_000L))
        assertTrue(shouldCheckForUpdate(enabled = true, lastCheckedAt = null, nowMs = 10_000L))

        val last = 1_000_000L
        assertFalse(shouldCheckForUpdate(enabled = true, lastCheckedAt = last, nowMs = last + AUTO_UPDATE_COOLDOWN_MS - 1))
        assertTrue(shouldCheckForUpdate(enabled = true, lastCheckedAt = last, nowMs = last + AUTO_UPDATE_COOLDOWN_MS))
    }

    @Test
    fun `shouldShowUpdatePopup ignores skipped version and non-available statuses`() {
        val available = UpdateStatus.Available("0.2.2", "https://example.test/0.2.2")
        assertEquals(available, shouldShowUpdatePopup(available, skippedVersion = null))
        assertNull(shouldShowUpdatePopup(available, skippedVersion = "0.2.2"))
        assertEquals(available, shouldShowUpdatePopup(available, skippedVersion = "0.2.1"))
        assertNull(shouldShowUpdatePopup(UpdateStatus.UpToDate, skippedVersion = null))
        assertNull(shouldShowUpdatePopup(UpdateStatus.Failed("offline"), skippedVersion = null))
    }

    @Test
    fun `store persists lastCheckedAt and skippedVersion`() {
        val (db, _) = inMemoryDb()
        val store = DbPositionStore(db, now = { 100L })

        assertNull(store.loadLastUpdateCheckAt())
        store.saveLastUpdateCheckAt(123456L)
        assertEquals(123456L, store.loadLastUpdateCheckAt())

        assertNull(store.loadSkippedUpdateVersion())
        store.saveSkippedUpdateVersion("0.2.2")
        assertEquals("0.2.2", store.loadSkippedUpdateVersion())
    }
}
