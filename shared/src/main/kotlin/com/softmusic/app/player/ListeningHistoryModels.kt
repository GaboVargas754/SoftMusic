package com.softmusic.app.player

import com.softmusic.app.data.Song

data class ListeningHistoryEntry(val songId: Long, val playCount: Long, val lastPlayedAtMs: Long)

data class ListeningHistoryState(
    val enabled: Boolean = true,
    val entries: List<ListeningHistoryEntry> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val trackingRevision: Long = 0L,
)

/** Resolves only currently available songs; saved entries never create library items. */
fun ListeningHistoryState.orderedSongs(songs: List<Song>, key: String): List<Song> {
    val songsById = songs.associateBy { it.id }
    val ordering = if (key == "most_played") {
        compareByDescending<ListeningHistoryEntry> { it.playCount }
            .thenByDescending { it.lastPlayedAtMs }
            .thenBy { it.songId }
    } else {
        compareByDescending<ListeningHistoryEntry> { it.lastPlayedAtMs }
            .thenByDescending { it.playCount }
            .thenBy { it.songId }
    }
    return entries.asSequence().filter { it.playCount > 0L }.sortedWith(ordering)
        .distinctBy { it.songId }.mapNotNull { songsById[it.songId] }.toList()
}
