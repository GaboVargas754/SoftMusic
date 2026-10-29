package com.softmusic.app.player

import com.softmusic.app.data.Song
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
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SmartQueueCoordinatorTest {
    @Test
    fun manualPendingOrderSurvivesModeConfigurationUntilADeliberateFreshQueue() = runBlocking {
        val fixture = Fixture(listOf(1, 4, 3, 2), 1L)
        try {
            fixture.coordinator.configure(true, true)
            fixture.coordinator.markManualEdit()
            fixture.coordinator.configure(false, false)
            fixture.coordinator.configure(true, true)
            fixture.coordinator.awaitPrepared()
            assertEquals(listOf(1L, 4L, 3L, 2L), fixture.ids)
            assertEquals(0, fixture.writes)
            assertTrue(fixture.analyzed.isEmpty())
            fixture.coordinator.startNewQueue()
            fixture.coordinator.awaitPrepared()
            assertEquals(1, fixture.writes)
            assertEquals(setOf(1L, 2L, 3L, 4L), fixture.ids.toSet())
        } finally { fixture.close() }
    }

    @Test
    fun disablingSmartRejectsAnAnalysisThatFinishesAfterItsModeChanged() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val fixture = Fixture(listOf(1, 2, 3), 1L) {
            withContext(NonCancellable) { release.await() }
            SmartSongProfile(120f, 1f, 0.1f)
        }
        try {
            fixture.coordinator.configure(true, true)
            fixture.coordinator.prepareAutomatic()
            val oldJobs = fixture.scope.coroutineContext[Job]!!.children.toList()
            fixture.coordinator.configure(false, false)
            release.complete(Unit)
            oldJobs.forEach { it.join() }
            assertTrue(fixture.coordinator.prepareAutomatic())
            assertEquals(listOf(1L, 2L, 3L), fixture.ids)
            assertEquals(0, fixture.writes)
            assertFalse(fixture.states.last().enabled)
            assertFalse(fixture.states.last().busy)
        } finally { release.complete(Unit); fixture.close() }
    }

    @Test
    fun automaticCommitsOneBoundedBatchWithoutTouchingPlayedPrefixOrTail() = runBlocking {
        val fixture = Fixture((1L..40L).toList(), 3L)
        try {
            fixture.coordinator.configure(true, true)
            fixture.coordinator.awaitPrepared()
            assertEquals((1L..3L).toList(), fixture.ids.take(3))
            assertEquals((16L..40L).toList(), fixture.ids.drop(15))
            assertEquals(13, fixture.analyzed.size) // Anchor plus twelve pending candidates.
            assertEquals((1L..40L).toSet(), fixture.ids.toSet())
            assertEquals(40, fixture.ids.size)
            val committed = fixture.ids.toList()
            fixture.currentId = fixture.ids[3]
            fixture.coordinator.currentChanged()
            repeat(5) { assertTrue(fixture.coordinator.prepareAutomatic()) }
            assertEquals(committed, fixture.ids)
            assertEquals(13, fixture.analyzed.size)
            assertEquals(1, fixture.writes)
        } finally { fixture.close() }
    }

    @Test
    fun explicitSortAnalyzesEveryPendingSongAndPreservesItsCommittedOrder() = runBlocking {
        val fixture = Fixture((1L..25L).toList(), 4L)
        try {
            fixture.coordinator.configure(false, false)
            fixture.coordinator.orderRemaining()
            fixture.coordinator.awaitPrepared()
            assertEquals((4L..25L).toList(), fixture.analyzed)
            assertEquals((1L..4L).toList(), fixture.ids.take(4))
            assertEquals((1L..25L).toSet(), fixture.ids.toSet())
            assertEquals(25, fixture.ids.size)
            assertTrue(fixture.states.last().message!!.contains("21 canciones"))
            val ordered = fixture.ids.toList()
            fixture.currentId = fixture.ids[4]
            fixture.coordinator.currentChanged()
            fixture.coordinator.configure(true, true)
            fixture.coordinator.awaitPrepared()
            assertEquals(ordered, fixture.ids)
            assertEquals(1, fixture.writes)
        } finally { fixture.close() }
    }

    @Test
    fun manualEditWinsEvenIfAnOldAnalyzerReturnsAfterCancellation() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val fixture = Fixture(listOf(1, 2, 3, 4), 1L) {
            entered.complete(Unit)
            withContext(NonCancellable) { release.await() }
            SmartSongProfile(120f, 1f, 0.1f)
        }
        try {
            fixture.coordinator.configure(true, true)
            assertFalse(fixture.coordinator.prepareAutomatic())
            entered.await()
            val oldJobs = fixture.scope.coroutineContext[Job]!!.children.toList()
            fixture.coordinator.markManualEdit()
            fixture.ids = listOf(1, 4, 3, 2)
            fixture.coordinator.queueChanged()
            release.complete(Unit)
            oldJobs.forEach { it.join() }
            fixture.coordinator.awaitPrepared()
            assertEquals(listOf(1L, 4L, 3L, 2L), fixture.ids)
            assertEquals(0, fixture.writes)
            assertFalse(fixture.states.last().busy)
        } finally { release.complete(Unit); fixture.close() }
    }

    @Test
    fun seekAndOwnerChangesRejectCapturedResultsAndDoNotRestartTheSameChoice() = runBlocking {
        for (replaceDeck in listOf(false, true)) {
            val release = CompletableDeferred<Unit>()
            val fixture = Fixture(listOf(1, 2, 3), 1L) {
                withContext(NonCancellable) { release.await() }
                SmartSongProfile(120f, 1f, 0.1f)
            }
            try {
                fixture.coordinator.configure(true, true)
                fixture.coordinator.prepareAutomatic()
                val oldJobs = fixture.scope.coroutineContext[Job]!!.children.toList()
                if (replaceDeck) fixture.owner = Any() else fixture.coordinator.cancelForTransport()
                release.complete(Unit)
                oldJobs.forEach { it.join() }
                assertEquals(0, fixture.writes)
                if (!replaceDeck) {
                    assertTrue(fixture.coordinator.prepareAutomatic())
                    assertEquals(1, fixture.analyzed.size)
                }
            } finally { release.complete(Unit); fixture.close() }
        }
    }

    @Test
    fun togglingAnActiveSortCancelsItAndMissingMeasurementsReportFallback() = runBlocking {
        val fixture = Fixture(listOf(1, 2, 3), 1L) { awaitCancellation() }
        try {
            fixture.coordinator.orderRemaining()
            assertTrue(fixture.states.last().busy)
            fixture.coordinator.orderRemaining()
            assertFalse(fixture.states.last().busy)
            assertTrue(fixture.states.last().message!!.contains("cancelado"))
            assertEquals(0, fixture.writes)
        } finally { fixture.close() }
        val unavailable = Fixture(listOf(1, 2, 3), 1L) { SmartSongProfile() }
        try {
            unavailable.coordinator.orderRemaining()
            unavailable.coordinator.awaitPrepared()
            assertEquals(0, unavailable.writes)
            assertEquals(listOf(1L, 2L, 3L), unavailable.ids)
            assertTrue(unavailable.states.last().message!!.contains("mediciones suficientes"))
        } finally { unavailable.close() }
    }

    @Test
    fun automaticDeadlineAndPerSongDeadlineBothBoundWork() = runBlocking {
        val fixture = Fixture((1L..30L).toList(), 1L, automaticTimeoutMs = 20L, songTimeoutMs = 100L) { awaitCancellation() }
        try {
            fixture.coordinator.configure(true, true)
            fixture.coordinator.awaitPrepared()
            assertEquals(1, fixture.analyzed.size)
            assertFalse(fixture.states.last().busy)
            assertEquals(0, fixture.writes)
        } finally { fixture.close() }
        val explicit = Fixture(listOf(1, 2, 3), 1L, songTimeoutMs = 5L) { awaitCancellation() }
        try {
            explicit.coordinator.orderRemaining()
            explicit.coordinator.awaitPrepared()
            assertEquals(3, explicit.analyzed.size)
            assertFalse(explicit.states.last().busy)
        } finally { explicit.close() }
    }

    @Test
    fun settledMonitorUsesOnlyLightweightIdentityAndLateTracksSkipAnalysis() = runBlocking {
        val fixture = Fixture(listOf(1, 2, 3), 1L, canAnalyzeAutomatic = { false })
        try {
            fixture.coordinator.configure(true, true)
            repeat(100) { assertTrue(fixture.coordinator.prepareAutomatic()) }
            assertEquals(0, fixture.snapshotReads)
            assertTrue(fixture.analyzed.isEmpty())
        } finally { fixture.close() }
    }

    private class Fixture(
        ids: List<Long>,
        var currentId: Long,
        automaticTimeoutMs: Long = 15_000L,
        songTimeoutMs: Long = 2_000L,
        canAnalyzeAutomatic: () -> Boolean = { true },
        analysis: suspend (Song) -> SmartSongProfile = { song ->
            SmartSongProfile(if (song.id % 2L == 0L) 155f else 120f, 1f, 0.1f)
        },
    ) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var owner = Any()
        var ids = ids.toList()
        val analyzed = mutableListOf<Long>()
        val states = mutableListOf<SmartQueueRuntimeState>()
        var writes = 0
        var snapshotReads = 0
        val coordinator = SmartQueueCoordinator(scope, snapshot = {
            snapshotReads++
            SmartQueueSnapshot(owner, this.ids.map(::song), currentId)
        }, analyze = {
            analyzed += it.id
            analysis(it)
        }, applyOrder = { captured, pending ->
            check(captured.owner === owner && captured.ids == this.ids && captured.currentSongId == currentId)
            this.ids = captured.ids.take(captured.currentIndex + 1) + pending
            writes++
            true
        }, publish = { states += it }, random = Random(7), automaticTimeoutMs = automaticTimeoutMs,
            songTimeoutMs = songTimeoutMs, canAnalyzeAutomatic = canAnalyzeAutomatic, currentIdentity = { owner to currentId })

        fun close() { coordinator.release(); scope.cancel() }

        private fun song(id: Long) = Song(id, "$id", "Artist", "Album", 180_000L, 2_000_000L, 0L, "/", "Music", "file:///$id.wav", null)
    }
}
