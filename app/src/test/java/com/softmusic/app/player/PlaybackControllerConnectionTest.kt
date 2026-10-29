package com.softmusic.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PlaybackControllerConnectionTest {
    @Test
    fun closingBeforeCompletionReleasesTheLateControllerWithoutAttachingIt() {
        val pending = FakePending()
        val attached = mutableListOf<Any>()
        val detached = mutableListOf<Any>()
        var connected = 0
        val connection = PlaybackControllerConnection(pending, attached::add, detached::add)
        connection.connect({ connected++ }, { throw AssertionError(it) })
        connection.close()
        pending.complete()
        connection.close()

        assertNull(connection.controller)
        assertEquals(0, connected)
        assertEquals(0, pending.getCalls)
        assertEquals(emptyList<Any>(), attached)
        assertEquals(emptyList<Any>(), detached)
        assertEquals(1, pending.releaseCalls)
        assertEquals(1, pending.controllerReleases)
    }

    @Test
    fun aConnectedControllerIsDetachedAndReleasedExactlyOnce() {
        val pending = FakePending()
        val attached = mutableListOf<Any>()
        val detached = mutableListOf<Any>()
        val connection = PlaybackControllerConnection(pending, attached::add, detached::add)
        connection.connect({}, { throw AssertionError(it) })
        pending.complete()
        assertSame(pending.value, connection.controller)
        assertEquals(listOf(pending.value), attached)
        connection.close()
        connection.close()
        assertNull(connection.controller)
        assertEquals(listOf(pending.value), detached)
        assertEquals(1, pending.releaseCalls)
        assertEquals(1, pending.controllerReleases)
    }

    @Test
    fun failureReportsOnceAndStillAllowsThePendingHandleToBeReleased() {
        val pending = FakePending(IllegalStateException("unavailable"))
        val failures = mutableListOf<Throwable>()
        val connection = PlaybackControllerConnection(pending, {}, {})
        connection.connect({ throw AssertionError("Unexpected connection") }, failures::add)
        connection.connect({ throw AssertionError("Unexpected second connection") }, failures::add)
        pending.complete()
        assertEquals(listOf(pending.failure), failures)
        assertNull(connection.controller)
        connection.close()
        assertEquals(1, pending.releaseCalls)
    }

    @Test
    fun closingAConnectionBeforeStartingDoesNotRegisterACallback() {
        val pending = FakePending()
        val connection = PlaybackControllerConnection(pending, {}, {})
        connection.close()
        connection.connect({ throw AssertionError("Unexpected connection") }, { throw AssertionError(it) })
        assertEquals(0, pending.registrations)
        assertEquals(1, pending.releaseCalls)
    }

    private class FakePending(val failure: Throwable? = null) : PendingPlaybackController<Any> {
        val value = Any()
        var registrations = 0
        var getCalls = 0
        var releaseCalls = 0
        var controllerReleases = 0
        private var callback: (() -> Unit)? = null
        private var ready = false
        override fun get(): Any { getCalls++; failure?.let { throw it }; return value }
        override fun whenReady(callback: () -> Unit) { registrations++; this.callback = callback }
        override fun release() { releaseCalls++; if (ready && failure == null) controllerReleases++ }
        fun complete() {
            ready = true
            if (releaseCalls > 0 && failure == null) controllerReleases++
            callback?.invoke()
        }
    }
}
