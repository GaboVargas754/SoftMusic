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
