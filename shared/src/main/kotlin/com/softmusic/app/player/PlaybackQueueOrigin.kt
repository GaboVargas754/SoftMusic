package com.softmusic.app.player

/** Guards metadata recovery for a running queue without changing its playback. */
fun playbackQueueOriginMatchesTimeline(
    savedQueueIds: List<Long>,
    savedCurrentSongId: Long,
    timelineMediaIds: List<String>,
    currentMediaId: String?,
): Boolean = savedQueueIds.isNotEmpty() &&
    savedCurrentSongId in savedQueueIds &&
    currentMediaId == savedCurrentSongId.toString() &&
    timelineMediaIds == savedQueueIds.map(Long::toString)

fun PlayerUiState.playbackQueueOriginLabel(): String {
    val sourceKey = playbackQueueSourceKey?.takeIf { it.isNotBlank() }
    return when (playbackQueueSource) {
        PlaybackQueueSource.Favorites -> "Favoritos"
        PlaybackQueueSource.Playlist -> playlists.firstOrNull { it.id == sourceKey }
            ?.name?.takeIf { it.isNotBlank() } ?: "Playlist"
        PlaybackQueueSource.Artist -> sourceKey ?: "Artista"
        PlaybackQueueSource.Album -> sourceKey ?: "Álbum"
        PlaybackQueueSource.History -> when (sourceKey) {
            "most_played" -> "Más reproducidas"
            "recent" -> "Escuchadas recientemente"
            else -> "Historial"
        }
        PlaybackQueueSource.Songs -> {
            if (sourceKey == null) return "Biblioteca local"
            allFolders.firstOrNull { it.path == sourceKey }?.name?.takeIf { it.isNotBlank() }
                ?: folders.firstOrNull { it.path == sourceKey }?.name?.takeIf { it.isNotBlank() }
                ?: sourceKey.trimEnd('/').substringAfterLast('/').ifBlank { "Carpeta" }
        }
    }
}
