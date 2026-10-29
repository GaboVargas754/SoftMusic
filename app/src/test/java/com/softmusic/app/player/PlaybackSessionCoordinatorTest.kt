package com.softmusic.app.player

import com.softmusic.app.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionCoordinatorTest {
    @Test
    fun restoreWaitsForSongsAndAnEmptyPlayerThenRunsOnlyOnce() {
        val store = FakeStore(session())
        val coordinator = PlaybackSessionCoordinator(store)
        assertNull(coordinator.restore(false, emptyList(), PlaybackMode.Ordered))
        assertNull(coordinator.restore(true, songs, PlaybackMode.Ordered))
        assertFalse(coordinator.hasRestoredSession)
        assertEquals(0, store.loads)

        val result = coordinator.restore(false, songs, PlaybackMode.Ordered)!!
        assertEquals(listOf(1L, 2L), result.queue.map { it.id })
        assertTrue(coordinator.hasRestoredSession)
        assertNull(coordinator.restore(false, songs, PlaybackMode.Ordered))
        assertEquals(1, store.loads)
    }

    @Test
    fun reopeningWithADifferentDefaultPreservesTheSavedModeQueueAndPosition() {
        for ((savedMode, initialMode) in listOf(
            PlaybackMode.Ordered to PlaybackMode.Shuffle,
            PlaybackMode.Shuffle to PlaybackMode.Ordered,
        )) {
            val saved = session().copy(playbackMode = savedMode)
            val restored = PlaybackSessionCoordinator(FakeStore(saved)).restore(false, songs, initialMode)!!
            assertEquals(savedMode, restored.playbackMode)
            assertEquals(saved.queueIds, restored.queue.map { it.id })
            assertEquals(saved.currentSongId, restored.queue[restored.currentIndex].id)
            assertEquals(saved.positionMs, restored.positionMs)
            assertEquals(saved.source, restored.source)
            assertEquals(saved.sourceKey, restored.sourceKey)
        }
    }

    @Test
    fun restoreDropsMissingAndRepeatedSongsAndBoundsTheSavedPosition() {
        val store = FakeStore(session().copy(queueIds = listOf(99L, 2L, 2L, 1L), currentSongId = 99L, positionMs = 99_999L))
        val result = PlaybackSessionCoordinator(store).restore(false, songs, PlaybackMode.RepeatList)!!
        assertEquals(listOf(2L, 1L), result.queue.map { it.id })
        assertEquals(0, result.currentIndex)
        assertEquals(2_000L, result.positionMs)
        assertEquals(PlaybackQueueSource.Playlist, result.source)
        assertEquals("list", result.sourceKey)
    }

    @Test
    fun restoreBoundsNegativePositionsAndUsesFallbackModeForOlderSessions() {
        val result = PlaybackSessionCoordinator(FakeStore(session().copy(positionMs = -42L, playbackMode = null)))
            .restore(false, songs, PlaybackMode.RepeatCurrent)!!
        assertEquals(0L, result.positionMs)
        assertEquals(PlaybackMode.RepeatCurrent, result.playbackMode)
        assertEquals(1, result.currentIndex)

        val unknownDurationSongs = listOf(song(2L, 0L))
        val unknownDuration = PlaybackSessionCoordinator(FakeStore(session().copy(positionMs = 9_000L)))
            .restore(false, unknownDurationSongs, PlaybackMode.Ordered)!!
        assertEquals(9_000L, unknownDuration.positionMs)
    }

    @Test
    fun aMissingOrFullyRemovedSavedQueueCompletesTheRestoreAttempt() {
        for (stored in listOf(null, session().copy(queueIds = listOf(99L)))) {
            val coordinator = PlaybackSessionCoordinator(FakeStore(stored))
            assertNull(coordinator.restore(false, songs, PlaybackMode.Ordered))
            assertTrue(coordinator.hasRestoredSession)
        }
    }

    @Test
    fun scheduledSavesThrottlePositionsWhileForcedSavesStillDeduplicate() {
        val clock = FakeClock(10_000L)
        val store = FakeStore()
        val coordinator = PlaybackSessionCoordinator(store, clock)
        val first = session()
        coordinator.scheduleSave(first, queueHasItems = true)
        clock.now += 500L
        coordinator.scheduleSave(first.copy(positionMs = 500L), queueHasItems = true)
        assertEquals(listOf(first), store.saved)

        val changed = first.copy(positionMs = 700L)
        coordinator.scheduleSave(changed, queueHasItems = true, force = true)
        coordinator.scheduleSave(changed, queueHasItems = true, force = true)
        clock.now += 1_999L
        coordinator.scheduleSave(changed.copy(positionMs = 800L), queueHasItems = true)
        assertEquals(listOf(first, changed), store.saved)
        clock.now += 1L
        coordinator.scheduleSave(changed.copy(positionMs = 900L), queueHasItems = true)
        assertEquals(listOf(first, changed, changed.copy(positionMs = 900L)), store.saved)
    }

    @Test
    fun aStaleCurrentSongCannotOverwriteTheSessionAfterAQueueChange() {
        val queue = ActivePlaybackQueue()
        queue.replace(songs, songs, mode = PlaybackMode.Ordered)
        val store = FakeStore()
        val coordinator = PlaybackSessionCoordinator(store, FakeClock(10_000L))
        val valid = queue.snapshot.playbackSessionSnapshot(2L, 400L, PlaybackMode.Ordered)
        coordinator.save(valid)
        queue.replace(listOf(songs[0]), songs, mode = PlaybackMode.Ordered, hasManualEdits = true)
        val stale = queue.snapshot.playbackSessionSnapshot(2L, 500L, PlaybackMode.Ordered)
        assertNull(stale)
        coordinator.scheduleSave(stale, queueHasItems = true, force = true)
        coordinator.save(null)
        assertEquals(listOf(valid), store.saved)
    }

    @Test
    fun clearPreventsRestorationAndAllowsAnIdenticalNewSessionToBeSaved() {
        val store = FakeStore(session())
        val clock = FakeClock(10_000L)
        val coordinator = PlaybackSessionCoordinator(store, clock)
        coordinator.scheduleSave(session(), queueHasItems = true)
        coordinator.clear()
        assertEquals(1, store.clears)
        assertTrue(coordinator.hasRestoredSession)
        assertNull(coordinator.restore(false, songs, PlaybackMode.Ordered))
        clock.now += 500L
        coordinator.scheduleSave(session(), queueHasItems = true)
        assertEquals(1, store.saved.size)
        coordinator.save(session())
        assertEquals(listOf(session(), session()), store.saved)
    }

    @Test
    fun existingTimelineKeepsCollectionOriginOnlyWhenTheSavedQueueMatches() {
        val coordinator = PlaybackSessionCoordinator(FakeStore(session()))
        assertEquals(session(), coordinator.matchingTimelineSession(listOf("1", "2"), "2"))
        assertNull(coordinator.matchingTimelineSession(listOf("2", "1"), "2"))
        assertNull(coordinator.matchingTimelineSession(listOf("1", "2"), "1"))
    }

    private class FakeStore(var stored: PlaybackSessionSnapshot? = null) : PlaybackSessionStore {
        var loads = 0
        var clears = 0
        val saved = mutableListOf<PlaybackSessionSnapshot>()
        override fun loadPlaybackSession(): PlaybackSessionSnapshot? { loads++; return stored }
        override fun savePlaybackSession(session: PlaybackSessionSnapshot) { saved += session; stored = session }
        override fun clearPlaybackSession() { clears++; stored = null }
    }

    private class FakeClock(var now: Long) : PlaybackSessionClock {
        override fun currentTimeMillis(): Long = now
    }

    private fun session() = PlaybackSessionSnapshot(listOf(1L, 2L), 2L, 100L, PlaybackMode.Shuffle, PlaybackQueueSource.Playlist, "list")
    private val songs = listOf(song(1L), song(2L))
    private fun song(id: Long, duration: Long = 2_000L) = Song(id, "Song $id", "Artist", "Album", duration, 100L, 0L, "/music", "music", "content://music/$id", null)
}
