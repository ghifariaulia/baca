package com.baca.app.reader

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertTrue

class TimeInViewTest {

    @Test
    fun `time with the app in the background or minimised isn't counted`() = runBlocking {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle get() = registry
        }
        owner.registry.currentState = Lifecycle.State.RESUMED
        val counted = CompletableDeferred<Long>()
        val timing = launch { timeInView(owner.lifecycle) { counted.complete(it) } }
        delay(200)
        owner.registry.currentState = Lifecycle.State.CREATED // stopped: in the background
        delay(600)
        owner.registry.currentState = Lifecycle.State.RESUMED
        delay(200)
        timing.cancel()
        val ms = counted.await()
        assertTrue(ms in 350..700, "about 400 ms in view, not 1000: $ms")
    }
}
