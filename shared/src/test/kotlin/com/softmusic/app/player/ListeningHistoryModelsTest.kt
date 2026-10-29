package com.softmusic.app.player

import com.softmusic.app.data.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListeningHistoryModelsTest {
    @Test
    fun `recent and most-played use real entries with deterministic ties`() {
        val state = ListeningHistoryState(entries = listOf(
            ListeningHistoryEntry(1L, 3L, 100L), ListeningHistoryEntry(2L, 1L, 300L),
            ListeningHistoryEntry(3L, 3L, 200L), ListeningHistoryEntry(4L, 3L, 200L),
        ))
        val songs = (1L..4L).map(::song)
        assertEquals(listOf(2L, 3L, 4L, 1L), state.orderedSongs(songs, "recent").map { it.id })
        assertEquals(listOf(3L, 4L, 1L, 2L), state.orderedSongs(songs, "most_played").map { it.id })
        assertEquals(state.orderedSongs(songs, "recent"), state.orderedSongs(songs, "unknown"))
    }

    @Test
    fun `hidden unavailable and unqualified entries never create history songs`() {
        val state = ListeningHistoryState(entries = listOf(
            ListeningHistoryEntry(1L, 2L, 200L), ListeningHistoryEntry(2L, 1L, 300L),
            ListeningHistoryEntry(3L, 0L, 400L), ListeningHistoryEntry(4L, 5L, 500L),
        ))
        assertEquals(listOf(1L), state.orderedSongs(listOf(song(1L), song(3L)), "recent").map { it.id })
        assertTrue(ListeningHistoryState().orderedSongs(listOf(song(1L)), "recent").isEmpty())
    }

    private fun song(id: Long) = Song(id, "Song $id", "Artist", "Album", 180_000L, 2_000_000L,
        0L, "/music", "Music", "content://song/$id", null)
}
