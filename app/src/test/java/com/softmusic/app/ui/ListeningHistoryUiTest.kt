package com.softmusic.app.ui

import com.softmusic.app.data.Song
import com.softmusic.app.player.ListeningHistoryEntry
import com.softmusic.app.player.ListeningHistoryState
import com.softmusic.app.player.PlayerUiState
import com.softmusic.app.player.orderedSongs
import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningHistoryUiTest {
    @Test
    fun historyRankingsRespectHiddenFoldersAndTheSmallAudioFilter() {
        val visible = song(1L)
        val hidden = song(2L).copy(folderPath = "/private")
        val small = song(3L).copy(durationMs = 5_000L)
        val neverPlayed = song(5L)
        val state = PlayerUiState(
            songs = listOf(visible, hidden, small, neverPlayed),
            hiddenFolderPaths = setOf("/private"),
            excludeSmallAudios = true,
            listeningHistory = ListeningHistoryState(entries = listOf(
                ListeningHistoryEntry(1L, 1L, 100L), ListeningHistoryEntry(2L, 20L, 400L),
                ListeningHistoryEntry(3L, 10L, 300L), ListeningHistoryEntry(4L, 5L, 200L),
            )),
        )

        ListeningHistoryView.entries.forEach { view ->
            assertEquals(listOf(visible), state.listeningHistory.orderedSongs(state.visibleLibrarySongs(), view.key))
        }
        val unfiltered = state.copy(hiddenFolderPaths = emptySet(), excludeSmallAudios = false)
        assertEquals(listOf(2L, 3L, 1L),
            unfiltered.listeningHistory.orderedSongs(unfiltered.visibleLibrarySongs(), "recent").map { it.id })
    }

    private fun song(id: Long) = Song(id, "Song $id", "Artist", "Album", 180_000L, 2_000_000L,
        0L, "/music", "Music", "content://song/$id", null)
}
