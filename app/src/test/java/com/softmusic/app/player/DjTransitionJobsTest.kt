package com.softmusic.app.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DjTransitionJobsTest {
    @Test
    fun targetRemainsReplannableUntilPlaybackIsCommitted() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val jobs = DjTransitionJobs(scope)
            lateinit var owner: DjTransitionJobs.Transition
            jobs.startTransition {
                owner = it
                awaitCancellation()
            }
            assertTrue(jobs.isTransitionActive)
            assertFalse(jobs.isTransitionCommitted)
            owner.rememberVolume(0.8f)
            assertTrue(jobs.isTransitionCommitted)
            jobs.cancelTransition({}, {})
            assertFalse(jobs.isTransitionCommitted)
        } finally { scope.cancel() }
    }

    @Test
    fun cancellingFadeCancelsItsWorkThenRestoresVolumeBeforeStoppingStandby() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val jobs = DjTransitionJobs(scope)
            val cancelled = CompletableDeferred<Unit>()
            val events = mutableListOf<String>()
            lateinit var owner: DjTransitionJobs.Transition
            jobs.startTransition { transition ->
                owner = transition
                owner.rememberVolume(0.65f)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
            assertTrue(jobs.isTransitionActive)

            jobs.cancelTransition(
                restoreVolume = { events += "restore:$it" },
                stopStandby = { events += "stop" },
            )
            cancelled.await()

            assertFalse(jobs.isTransitionActive)
            assertNull(owner.originalVolume)
            assertEquals(listOf("restore:0.65", "stop"), events)
            jobs.cancelTransition(restoreVolume = { events += "restore" }, stopStandby = { events += "stop" })
            assertEquals(listOf("restore:0.65", "stop", "stop"), events)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun stoppingMonitorPreventsAnotherTick() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val jobs = DjTransitionJobs(scope)
            var ticks = 0
            jobs.startMonitor(intervalMs = { 10_000L }, tick = { ticks++ })
            assertEquals(1, ticks)
            assertTrue(scope.coroutineContext[Job]!!.children.any { it.isActive })
            jobs.stopMonitor()
            jobs.stopMonitor()
            assertFalse(scope.coroutineContext[Job]!!.children.any { it.isActive })
            assertEquals(1, ticks)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun cancelledJobsLateFinallyCannotClearReplacementOwnershipOrVolume() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val jobs = DjTransitionJobs(scope)
            val releaseOld = CompletableDeferred<Unit>()
            val oldFinished = CompletableDeferred<Unit>()
            lateinit var old: DjTransitionJobs.Transition
            lateinit var replacement: DjTransitionJobs.Transition
            jobs.startTransition { owner ->
                old = owner
                owner.rememberVolume(0.4f)
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) { releaseOld.await() }
                    assertFalse(owner.rememberVolume(0.1f))
                    owner.clearVolume()
                    owner.restoreAfterAbort({ true }, { error("Stale volume restore") }, { error("Stale standby reset") })
                    oldFinished.complete(Unit)
                }
            }
            jobs.cancelTransition({}, {})
            jobs.startTransition { owner ->
                replacement = owner
                owner.rememberVolume(0.8f)
                awaitCancellation()
            }
            assertTrue(jobs.isTransitionActive)
            releaseOld.complete(Unit)
            oldFinished.await()

            assertFalse(old.isCurrent)
            assertTrue(replacement.isCurrent)
            assertTrue(jobs.isTransitionActive)
            val restored = mutableListOf<Float>()
            jobs.cancelTransition(restoreVolume = { restored += it }, stopStandby = {})
            assertEquals(listOf(0.8f), restored)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun synchronouslyCompletedWorkDoesNotLeaveACompletedJobAsTheOwner() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val jobs = DjTransitionJobs(scope)
            lateinit var completed: DjTransitionJobs.Transition
            jobs.startTransition { owner ->
                completed = owner
                owner.rememberVolume(0.3f)
            }
            assertFalse(jobs.isTransitionActive)
            assertFalse(completed.isCurrent)
            jobs.cancelTransition(restoreVolume = { error("Completed job retained its volume") }, stopStandby = {})

            jobs.startTransition { owner ->
                owner.rememberVolume(0.7f)
                awaitCancellation()
            }
            val restored = mutableListOf<Float>()
            jobs.cancelTransition(restoreVolume = { restored += it }, stopStandby = {})
            assertEquals(listOf(0.7f), restored)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun abortStillStopsStandbyIfRestoringVolumeThrows() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val jobs = DjTransitionJobs(scope)
            val events = mutableListOf<String>()
            jobs.startTransition { owner ->
                owner.rememberVolume(0.6f)
                val failure = runCatching {
                    owner.restoreAfterAbort(
                        isOutgoingActive = { true },
                        restoreVolume = { events += "restore"; error("Player failed") },
                        stopStandby = { events += "stop" },
                    )
                }.exceptionOrNull()
                assertTrue(failure is IllegalStateException)
                assertNull(owner.originalVolume)
            }
            assertEquals(listOf("restore", "stop"), events)
            assertFalse(jobs.isTransitionActive)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun cancellationStillStopsStandbyIfRestoringVolumeThrows() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val jobs = DjTransitionJobs(scope)
            jobs.startTransition { owner ->
                owner.rememberVolume(0.6f)
                awaitCancellation()
            }
            val events = mutableListOf<String>()
            val failure = runCatching {
                jobs.cancelTransition(
                    restoreVolume = { events += "restore"; error("Player failed") },
                    stopStandby = { events += "stop" },
                )
            }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
            assertEquals(listOf("restore", "stop"), events)
            assertFalse(jobs.isTransitionActive)
        } finally {
            scope.cancel()
        }
    }
}
