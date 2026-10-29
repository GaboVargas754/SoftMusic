package com.softmusic.app.data

const val MAX_PLAYLIST_NAME_LENGTH = 50

data class PlaylistMutation(
    val playlists: List<MusicPlaylist>,
    val changed: Boolean,
)

data class PlaylistQueueAddResult(
    val playlists: List<MusicPlaylist>,
    val addedCount: Int,
) {
    val changed: Boolean = addedCount > 0
}

fun MusicPlaylist.orderedSongsFrom(songs: List<Song>): List<Song> {
    val songsById = songs.associateBy { it.id }
    return songIds.mapNotNull(songsById::get)
}

fun cleanPlaylistName(name: String, maxLength: Int = MAX_PLAYLIST_NAME_LENGTH): String? = name
    .trim()
    .take(maxLength)
    .takeIf { it.isNotBlank() }

fun List<MusicPlaylist>.withNewPlaylist(
    name: String,
    id: String,
    songIds: List<Long> = emptyList(),
): PlaylistMutation {
    val cleanName = cleanPlaylistName(name) ?: return PlaylistMutation(this, changed = false)
    if (any { it.name.playlistNameKey() == cleanName.playlistNameKey() }) {
        return PlaylistMutation(this, changed = false)
    }
    return PlaylistMutation(
        playlists = this + MusicPlaylist(
            id = id,
            name = cleanName,
            songIds = songIds,
            coverSongId = null,
        ),
        changed = true,
    )
}

fun List<MusicPlaylist>.withoutPlaylist(playlistId: String): PlaylistMutation {
    val updated = filterNot { it.id == playlistId }
    return PlaylistMutation(updated, changed = updated.size != size)
}

fun List<MusicPlaylist>.withPlaylistCover(playlistId: String, songId: Long?): PlaylistMutation = mutatePlaylist(playlistId) { playlist ->
    val safeCoverSongId = songId?.takeIf { it in playlist.songIds }
    playlist.copy(coverSongId = safeCoverSongId)
}

fun List<MusicPlaylist>.withSongAddedToPlaylist(playlistId: String, songId: Long): PlaylistMutation = mutatePlaylist(playlistId) { playlist ->
    if (songId in playlist.songIds) playlist else playlist.copy(songIds = playlist.songIds + songId)
}

fun List<MusicPlaylist>.withSongRemovedFromPlaylist(playlistId: String, songId: Long): PlaylistMutation = mutatePlaylist(playlistId) { playlist ->
    playlist.copy(
        songIds = playlist.songIds.filterNot { it == songId },
        coverSongId = playlist.coverSongId?.takeIf { it != songId },
    )
}

fun List<MusicPlaylist>.withQueueAddedToPlaylist(playlistId: String, queueSongIds: List<Long>): PlaylistQueueAddResult {
    val uniqueQueueIds = queueSongIds.distinct()
    if (uniqueQueueIds.isEmpty()) return PlaylistQueueAddResult(this, addedCount = 0)

    var addedCount = 0
    val mutation = mutatePlaylist(playlistId) { playlist ->
        val existingIds = playlist.songIds.toSet()
        val missingIds = uniqueQueueIds.filterNot { it in existingIds }
        addedCount = missingIds.size
        if (missingIds.isEmpty()) playlist else playlist.copy(songIds = playlist.songIds + missingIds)
    }
    return PlaylistQueueAddResult(mutation.playlists, addedCount)
}

private fun List<MusicPlaylist>.mutatePlaylist(
    playlistId: String,
    transform: (MusicPlaylist) -> MusicPlaylist,
): PlaylistMutation {
    var changed = false
    val updated = map { playlist ->
        if (playlist.id != playlistId) {
            playlist
        } else {
            transform(playlist).also { changed = changed || it != playlist }
        }
    }
    return PlaylistMutation(updated, changed)
}

private fun String.playlistNameKey(): String = trim().lowercase()

data class PlaylistSongsRemoveResult(
    val playlists: List<MusicPlaylist>,
    val removedCount: Int,
) {
    val changed: Boolean = removedCount > 0
}

/** Matches the accent-insensitive names shown by the playlist creation form. */
fun normalizedPlaylistName(name: String): String = java.text.Normalizer
    .normalize(name.trim().lowercase(), java.text.Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "")

fun List<MusicPlaylist>.withPlaylistRenamed(playlistId: String, name: String): PlaylistMutation {
    val cleanName = name.trim()
    if (cleanName.isBlank() || cleanName.length > MAX_PLAYLIST_NAME_LENGTH ||
        any { it.id != playlistId && normalizedPlaylistName(it.name) == normalizedPlaylistName(cleanName) }
    ) {
        return PlaylistMutation(this, changed = false)
    }
    return mutatePlaylist(playlistId) { it.copy(name = cleanName) }
}

/** Replaces visible slots only; a refresh or another edit invalidates the captured full order. */
fun List<MusicPlaylist>.withPlaylistSongsReordered(
    playlistId: String,
    expectedFullIds: List<Long>,
    latestVisibleIds: List<Long>,
    orderedVisibleIds: List<Long>,
): PlaylistMutation {
    val playlist = firstOrNull { it.id == playlistId } ?: return PlaylistMutation(this, changed = false)
    if (playlist.songIds != expectedFullIds) return PlaylistMutation(this, changed = false)
    val visibleIds = latestVisibleIds.toSet()
    if (latestVisibleIds.size != visibleIds.size ||
        orderedVisibleIds.size != latestVisibleIds.size ||
        orderedVisibleIds.toSet() != visibleIds ||
        playlist.songIds.filter { it in visibleIds } != latestVisibleIds
    ) {
        return PlaylistMutation(this, changed = false)
    }
    val replacements = orderedVisibleIds.iterator()
    val reordered = playlist.songIds.map { if (it in visibleIds) replacements.next() else it }
    return mutatePlaylist(playlistId) { it.copy(songIds = reordered) }
}

fun List<MusicPlaylist>.withSongsAddedToPlaylist(
    playlistId: String,
    songIds: List<Long>,
): PlaylistQueueAddResult = withQueueAddedToPlaylist(playlistId, songIds)

fun List<MusicPlaylist>.withoutSongsFromPlaylist(
    playlistId: String,
    songIds: Set<Long>,
): PlaylistSongsRemoveResult {
    var removedCount = 0
    val mutation = mutatePlaylist(playlistId) { playlist ->
        val removedIds = playlist.songIds.filter { it in songIds }.toSet()
        removedCount = removedIds.size
        if (removedIds.isEmpty()) playlist else playlist.copy(
            songIds = playlist.songIds.filterNot { it in removedIds },
            coverSongId = playlist.coverSongId?.takeUnless { it in removedIds },
        )
    }
    return PlaylistSongsRemoveResult(mutation.playlists, removedCount)
}
