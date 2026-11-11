package com.baca.shared.domain

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UpdateCheckTest {

    private fun release(tag: String) =
        """{"tag_name":"$tag","html_url":"https://github.com/o/r/releases/tag/$tag","draft":false}"""

    @Test
    fun `a higher release is available and links to its page`() = runTest {
        val status = checkForUpdate("0.1.3") { release("v0.1.4") }
        assertEquals(UpdateStatus.Available("0.1.4", "https://github.com/o/r/releases/tag/v0.1.4"), status)
    }

    @Test
    fun `the same or an older release is up to date`() = runTest {
        assertEquals(UpdateStatus.UpToDate, checkForUpdate("0.1.3") { release("v0.1.3") })
        assertEquals(UpdateStatus.UpToDate, checkForUpdate("0.2.0") { release("v0.1.9") })
    }

    @Test
    fun `versions compare by number, not by text`() = runTest {
        assertIs<UpdateStatus.Available>(checkForUpdate("0.9.0") { release("v0.10.0") })
        assertIs<UpdateStatus.Available>(checkForUpdate("0.1") { release("0.1.1") })
    }

    @Test
    fun `a failed or unreadable fetch is reported, not hidden`() = runTest {
        val offline = checkForUpdate("0.1.3") { error("no network") }
        assertEquals(UpdateStatus.Failed("no network"), offline)
        assertIs<UpdateStatus.Failed>(checkForUpdate("0.1.3") { "<html>rate limited</html>" })
        assertIs<UpdateStatus.Failed>(checkForUpdate("0.1.3") { """{"tag_name":"nightly","html_url":"x"}""" })
    }

    @Test
    fun `cancelling the check is not a failure`() = runTest {
        var thrown: Throwable? = null
        try {
            checkForUpdate("0.1.3") { throw kotlinx.coroutines.CancellationException("stop") }
        } catch (e: kotlinx.coroutines.CancellationException) {
            thrown = e
        }
        assertTrue(thrown != null)
    }
}
