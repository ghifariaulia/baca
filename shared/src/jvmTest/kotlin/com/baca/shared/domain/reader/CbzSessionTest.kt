package com.baca.shared.domain.reader

import com.baca.shared.domain.FixedLocator
import kotlin.test.Test
import kotlin.test.assertEquals

private class FixedStore : PositionStore {
    override val deviceId = "test"
    private val fixed = mutableMapOf<String, FixedLocator>()
    private val reflow = mutableMapOf<String, com.baca.shared.domain.Locator>()
    override fun loadPosition(bookHash: String) = reflow[bookHash]
    override fun savePosition(bookHash: String, locator: com.baca.shared.domain.Locator) {
        reflow[bookHash] = locator
    }
    override fun loadFixedPosition(bookHash: String): FixedLocator? = fixed[bookHash]
    override fun saveFixedPosition(bookHash: String, locator: FixedLocator) {
        fixed[bookHash] = locator
    }
    override fun getLocal(key: String): String? = null
    override fun putLocal(key: String, value: String) {}
}

class CbzSessionTest {

    @Test
    fun startsAtZeroAndClampsToLastPage() {
        val store = FixedStore()
        kotlinx.coroutines.runBlocking {
            val session = CbzSession("h", 10, store)
            session.ensureView()
            assertEquals(0, session.pageIndex)
            session.goToPage(5)
            assertEquals(5, session.pageIndex)
            assertEquals(5.0 / 9.0, session.progress(), 1e-9)
            session.goToPage(99)
            assertEquals(9, session.pageIndex)
            assertEquals(1.0, session.progress())
        }
    }

    @Test
    fun positionSavesAndRestoresAcrossSessions() {
        val store = FixedStore()
        kotlinx.coroutines.runBlocking {
            CbzSession("h", 24, store).apply {
                ensureView()
                goToPage(7)
            }
        }
        kotlinx.coroutines.runBlocking {
            val reopened = CbzSession("h", 24, store)
            reopened.ensureView()
            assertEquals(7, reopened.pageIndex)
        }
    }
}
