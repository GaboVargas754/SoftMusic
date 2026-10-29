package com.softmusic.app.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRefreshMonitorTest {
    @Test
    fun permissionOwnsRegistrationAndResumeRequestsAnImmediateRescan() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val registration = FakeRegistration()
        var requests = 0
        val monitor = LibraryRefreshMonitor(scope, registration::register, { requests++ })
        try {
            monitor.onResume()
            assertEquals(0, requests)
            assertEquals(0, registration.registered)
            monitor.setPermissionGranted(true)
            monitor.setPermissionGranted(true)
            assertEquals(1, registration.registered)
            assertEquals(1, requests)
            monitor.onResume()
            assertEquals(2, requests)
            monitor.setPermissionGranted(false)
            monitor.onResume()
            assertEquals(1, registration.unregistered)
            assertEquals(2, requests)
            monitor.setPermissionGranted(true)
            assertEquals(2, registration.registered)
            assertEquals(3, requests)
            monitor.close()
            monitor.close()
            monitor.setPermissionGranted(true)
            monitor.onResume()
            assertEquals(2, registration.unregistered)
            assertEquals(3, requests)
        } finally {
            monitor.close()
            scope.cancel()
        }
    }

    @Test
    fun burstsDebounceForFiveHundredMillisecondsAndOnlyTheLatestNotificationRequestsAScan() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val registration = FakeRegistration()
        val paused = Channel<CompletableDeferred<Unit>>(Channel.UNLIMITED)
        var requests = 0
        val intervals = mutableListOf<Long>()
        val monitor = LibraryRefreshMonitor(scope, registration::register, { requests++ }, pause = { interval ->
            intervals += interval
            val gate = CompletableDeferred<Unit>()
            paused.trySend(gate)
            withContext(NonCancellable) { gate.await() }
        })
        val gates = mutableListOf<CompletableDeferred<Unit>>()
        try {
            monitor.setPermissionGranted(true)
            registration.notifyChange()
            gates += withTimeout(3_000L) { paused.receive() }
            registration.notifyChange()
            gates += withTimeout(3_000L) { paused.receive() }
            registration.notifyChange()
            gates += withTimeout(3_000L) { paused.receive() }
            assertEquals(listOf(500L, 500L, 500L), intervals)
            gates[0].complete(Unit)
            gates[1].complete(Unit)
            assertEquals(1, requests)
            gates[2].complete(Unit)
            assertEquals(2, requests)
        } finally {
            gates.forEach { it.complete(Unit) }
            monitor.close()
            scope.cancel()
        }
    }

    @Test
    fun permissionRevocationAndCloseDiscardPendingOrLateObserverNotifications() = runBlocking {
        for (close in listOf(false, true)) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val registration = FakeRegistration()
            val gate = CompletableDeferred<Unit>()
            val paused = CompletableDeferred<Unit>()
            var requests = 0
            val monitor = LibraryRefreshMonitor(scope, registration::register, { requests++ }, pause = {
                paused.complete(Unit)
                withContext(NonCancellable) { gate.await() }
            })
            try {
                monitor.setPermissionGranted(true)
                registration.notifyChange()
                withTimeout(3_000L) { paused.await() }
                if (close) monitor.close() else monitor.setPermissionGranted(false)
                registration.notifyChange()
                gate.complete(Unit)
                assertEquals(1, requests)
                assertEquals(1, registration.unregistered)
            } finally {
                gate.complete(Unit)
                monitor.close()
                scope.cancel()
            }
        }
    }

    @Test
    fun resumeCancelsADebouncedNotificationAndPermissionRegistrationRacesAreIgnored() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val registration = FakeRegistration()
        val gate = CompletableDeferred<Unit>()
        var requests = 0
        val monitor = LibraryRefreshMonitor(scope, registration::register, { requests++ }, pause = {
            withContext(NonCancellable) { gate.await() }
        })
        try {
            monitor.setPermissionGranted(true)
            registration.notifyChange()
            monitor.onResume()
            gate.complete(Unit)
            assertEquals(2, requests)
            val denied = LibraryRefreshMonitor(scope, { throw SecurityException("permission revoked") }, { requests++ })
            denied.setPermissionGranted(true)
            denied.onResume()
            denied.close()
            assertEquals(2, requests)
            assertTrue(registration.registered > 0)
        } finally {
            gate.complete(Unit)
            monitor.close()
            scope.cancel()
        }
    }

    private class FakeRegistration {
        var registered = 0
        var unregistered = 0
        private var callback: (() -> Unit)? = null
        fun register(onChanged: () -> Unit): AutoCloseable {
            registered++
            callback = onChanged
            return AutoCloseable { unregistered++ }
        }
        // Retain the callback to simulate an observer event already queued during unregister.
        fun notifyChange() { callback?.invoke() }
    }
}
