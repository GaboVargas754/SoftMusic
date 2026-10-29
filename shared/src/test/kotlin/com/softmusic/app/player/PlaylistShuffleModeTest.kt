package com.softmusic.app.player

import com.softmusic.app.data.Song
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PlaylistShuffleModeTest {
    @Test
    fun cycleHasExactlyThreeStatesAndMapsBothShufflesToShufflePlayback() {
        val off = PlaylistShuffleMode.Off
        assertEquals(PlaylistShuffleMode.Shuffle, off.next())
        assertEquals(PlaylistShuffleMode.SmartShuffle, off.next().next())
        assertEquals(off, off.next().next().next())
        assertEquals(PlaybackMode.Ordered, off.playbackMode)
        assertEquals(PlaybackMode.Shuffle, off.next().playbackMode)
        assertEquals(PlaybackMode.Shuffle, off.next().next().playbackMode)
    }

    @Test
    fun turningOffRestoresOnlyPendingPlaylistOrderAndKeepsManualExtrasStable() {
        val queue = listOf(7L, 3L, 6L, 99L, 2L, 98L, 4L).map(::song)
        val result = queue.withPlaylistPendingOrder((1L..7L).map(::song), 3L, PlaylistShuffleMode.Off)
        assertEquals(listOf(7L, 3L, 2L, 4L, 6L, 99L, 98L), result.map { it.id })
        assertEquals(queue.map { it.id }.toSet(), result.map { it.id }.toSet())
        assertEquals(queue.size, result.size)
    }

    @Test
    fun regularAndSmartSelectionsBothRegeneratePendingEvenWithTheSamePlaybackMode() {
        val queue = (1L..15L).map(::song)
        for (mode in listOf(PlaylistShuffleMode.Shuffle, PlaylistShuffleMode.SmartShuffle)) {
            val result = queue.withPlaylistPendingOrder(queue, 4L, mode, Random(7))
            assertEquals(queue.take(4), result.take(4))
            assertNotEquals(queue.drop(4), result.drop(4))
            assertEquals(queue.map { it.id }.toSet(), result.map { it.id }.toSet())
            assertEquals(queue.size, result.size)
        }
    }

    @Test
    fun missingCurrentItemCannotReplaceAnyPartOfTheQueue() {
        val queue = listOf(1L, 2L, 3L).map(::song)
        assertEquals(queue, queue.withPlaylistPendingOrder(queue.reversed(), 99L, PlaylistShuffleMode.Off))
    }

    private fun song(id: Long) = Song(id, "$id", "Artist", "Album", 180_000L, 2_000_000L, 0L, "/", "Music", "file:///$id.wav", null)
}
