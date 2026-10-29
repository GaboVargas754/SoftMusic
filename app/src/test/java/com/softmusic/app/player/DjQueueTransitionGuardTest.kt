package com.softmusic.app.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DjQueueTransitionGuardTest {
    @Test
    fun reorderRemoveAddAndClearCannotResurrectSnapshotsFromAnyTransitionStage() = runBlocking {
        val changedQueues = listOf(
            listOf("a", "c", "b"),
            listOf("a", "c"),
            listOf("a", "b", "c", "d"),
            emptyList(),
        )
        Stage.entries.forEach { stage ->
            changedQueues.forEach { changedQueue ->
                val fixture = Fixture()
                try {
                    val plan = fixture.start(stage)
                    fixture.editQueue(changedQueue)
                    val writesAfterEdit = fixture.writes.toList()
                    plan.finish()

                    assertEquals("stage=$stage queue=$changedQueue", writesAfterEdit, fixture.writes)
                    assertEquals(changedQueue, fixture.outgoing.queue)
                    assertSame(fixture.outgoing, fixture.active)
                    assertEquals(42_000L, fixture.outgoing.positionMs)
                    assertEquals(0.8f, fixture.outgoing.volume, 0f)
                    assertTrue(fixture.incoming.queue.isEmpty())
                    assertEquals(3, plan.staleActionsAttempted)
                    assertFalse(fixture.jobs.isTransitionActive)
                } finally {
                    fixture.close()
                }
            }
        }
    }

    @Test
    fun multipleQuickEditsLeaveOnlyTheLatestQueueEligibleForHandoff() = runBlocking {
        val fixture = Fixture()
        try {
            val oldest = fixture.start(Stage.Analysis)
            fixture.editQueue(listOf("a", "c", "b"))
            val middle = fixture.start(Stage.Fade)
            fixture.editQueue(listOf("a", "d", "c", "b"))
            val latest = fixture.start(Stage.Precache)

            oldest.finish()
            middle.finish()
            assertTrue(fixture.jobs.isTransitionActive)
            assertEquals(listOf("a", "d", "c", "b"), fixture.incoming.queue)
            latest.finish()

            assertSame(fixture.incoming, fixture.active)
            assertEquals(listOf("a", "d", "c", "b"), fixture.active.queue)
            assertEquals(1, fixture.writes.count { it == "handoff" })
        } finally {
            fixture.close()
        }
    }

    @Test
    fun sourceRefreshAndStandbyPlaylistChangesDoNotCancelAValidPlan() = runBlocking {
        val fixture = Fixture()
        try {
            val plan = fixture.start(Stage.Readiness)
            assertFalse(fixture.guard.onTimelineChanged(fixture.outgoing, playlistChanged = false))
            assertFalse(fixture.guard.onTimelineChanged(fixture.incoming, playlistChanged = true))
            plan.finish()

            assertSame(fixture.incoming, fixture.active)
            assertEquals(1, fixture.writes.count { it == "handoff" })
        } finally {
            fixture.close()
        }
    }

    @Test
    fun shuffleCanHandoffItsReorderedTargetAndIgnoresItsOwnTimelineEvents() = runBlocking {
        val fixture = Fixture(listOf("b", "c", "a"), currentMediaId = "a")
        try {
            val shuffledQueue = listOf("a", "c", "b")
            val plan = fixture.start(Stage.BeforeHandoff, targetQueue = shuffledQueue)
            plan.finish()

            assertSame(fixture.incoming, fixture.active)
            assertEquals(shuffledQueue, fixture.active.queue)
            assertTrue(fixture.ownTimelineEventsIgnored)
            assertEquals(1, fixture.writes.count { it == "handoff" })
        } finally {
            fixture.close()
        }
    }

    @Test
    fun cancellationWithoutQueueChangesStillBlocksAnAnalyzerThatSwallowedCancellation() = runBlocking {
        val fixture = Fixture()
        try {
            val plan = fixture.start(Stage.Analysis)
            fixture.cancel()
            plan.finish()

            assertEquals(listOf("a", "b", "c"), fixture.outgoing.queue)
            assertSame(fixture.outgoing, fixture.active)
            assertTrue(fixture.writes.none { it == "prepare" || it == "gain" || it == "handoff" })
            assertEquals(3, plan.staleActionsAttempted)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun currentItemChangesAbortOwnedWorkAndRestoreItsOriginalVolume() = runBlocking {
        val fixture = Fixture()
        try {
            val plan = fixture.start(Stage.Fade)
            fixture.outgoing.currentMediaId = "b"
            plan.finish()

            assertSame(fixture.outgoing, fixture.active)
            assertEquals("b", fixture.outgoing.currentMediaId)
            assertEquals(42_000L, fixture.outgoing.positionMs)
            assertEquals(0.8f, fixture.outgoing.volume, 0f)
            assertTrue(fixture.incoming.queue.isEmpty())
            assertTrue(fixture.writes.none { it == "handoff" })
        } finally {
            fixture.close()
        }
    }

    private enum class Stage { Analysis, Precache, Readiness, Fade, BeforeHandoff }

    private class FakeDeck(
        var queue: List<String>,
        var currentMediaId: String?,
        var volume: Float = 0.8f,
        val positionMs: Long = 42_000L,
    )

    /** Uses the same guard, handoff and job-owned cleanup operations as the Android coordinator. */
    private class Fixture(queue: List<String> = listOf("a", "b", "c"), currentMediaId: String = "a") {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val jobs = DjTransitionJobs(scope)
        val outgoing = FakeDeck(queue, currentMediaId)
        val incoming = FakeDeck(emptyList(), null)
        var active = outgoing
        val guard = DjQueueTransitionGuard(activeDeck = { active }, currentMediaId = { it.currentMediaId })
        val writes = mutableListOf<String>()
        var ownTimelineEventsIgnored = false

        fun editQueue(queue: List<String>) {
            active.queue = queue
            if (active.currentMediaId !in queue) active.currentMediaId = queue.firstOrNull()
            if (guard.onTimelineChanged(active, playlistChanged = true)) reset()
        }

        fun cancel() {
            guard.invalidate()
            reset()
        }

        private fun reset() {
            jobs.cancelTransition(restoreVolume = { active.volume = it }, stopStandby = ::clearIncoming)
        }

        private fun clearIncoming() {
            incoming.queue = emptyList()
            incoming.volume = 0f
            writes += "clear"
            check(!guard.onTimelineChanged(incoming, playlistChanged = true))
        }

        fun start(stage: Stage, targetQueue: List<String> = active.queue.toList()): Pending {
            val captured = guard.capture(active, active.currentMediaId!!)
            val pending = Pending()
            jobs.startTransition { owner ->
                fun restore() = owner.restoreAfterAbort(
                    isOutgoingActive = { active === captured.outgoing },
                    restoreVolume = { captured.outgoing.volume = it },
                    stopStandby = ::clearIncoming,
                )
                fun prepare() = guard.performIfCurrent(captured, { owner.isCurrent }) {
                    incoming.queue = targetQueue
                    incoming.currentMediaId = targetQueue.getOrNull(1)
                    writes += "prepare"
                }.also { if (!it) restore() }
                fun gain() = guard.performIfCurrent(captured, { owner.isCurrent }) {
                    if (owner.originalVolume == null) owner.rememberVolume(captured.outgoing.volume)
                    captured.outgoing.volume = 0.2f
                    incoming.volume = 0.6f
                    writes += "gain"
                }.also { if (!it) restore() }
                if (stage >= Stage.Precache) prepare()
                if (stage >= Stage.Fade) gain()
                // Simulates decoder/analysis code returning late after swallowing cancellation.
                try {
                    withContext(NonCancellable) { pending.resume.await() }
                } catch (_: CancellationException) {
                    // The real guard must also reject work in this state.
                }
                pending.staleActionsAttempted++
                prepare()
                pending.staleActionsAttempted++
                gain()
                pending.staleActionsAttempted++
                if (!guard.handoffIfCurrent(captured, { owner.isCurrent }) {
                        val outgoingEvent = guard.onTimelineChanged(outgoing, playlistChanged = true)
                        active = incoming
                        val incomingEvent = guard.onTimelineChanged(incoming, playlistChanged = true)
                        ownTimelineEventsIgnored = !outgoingEvent && !incomingEvent
                        writes += "handoff"
                    }) restore()
                owner.clearVolume()
                pending.done.complete(Unit)
            }
            return pending
        }

        fun close() = scope.cancel()
    }

    private class Pending {
        val resume = CompletableDeferred<Unit>()
        val done = CompletableDeferred<Unit>()
        var staleActionsAttempted = 0

        suspend fun finish() {
            resume.complete(Unit)
            done.await()
        }
    }
}
