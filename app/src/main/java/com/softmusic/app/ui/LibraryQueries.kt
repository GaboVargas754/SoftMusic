package com.softmusic.app.ui

import com.softmusic.app.data.MusicPlaylist
import com.softmusic.app.data.Song
import com.softmusic.app.data.isIncludedBySmallAudioFilter
import com.softmusic.app.data.orderedSongsFrom
import com.softmusic.app.player.PlaybackQueueSource
import com.softmusic.app.player.PlayerUiState
import com.softmusic.app.player.SortMode
import java.text.Normalizer

private fun MusicLibraryView.groupListLabel(): String = when (this) {
    MusicLibraryView.Songs -> "canciones"
    MusicLibraryView.Artists -> "artistas"
    MusicLibraryView.Albums -> "álbumes"
}

internal fun MusicLibraryView.playbackQueueSource(): PlaybackQueueSource = when (this) {
    MusicLibraryView.Songs -> PlaybackQueueSource.Songs
    MusicLibraryView.Artists -> PlaybackQueueSource.Artist
    MusicLibraryView.Albums -> PlaybackQueueSource.Album
}

internal fun PlayerUiState.favoriteSongs(): List<Song> = visibleLibrarySongs()
    .filter { it.id in favoriteSongIds }
    .sortedForDisplay(sortMode)

internal fun PlayerUiState.visibleLibrarySongs(): List<Song> = songs.filter {
    it.folderPath !in hiddenFolderPaths && it.isIncludedBySmallAudioFilter(excludeSmallAudios)
}

internal fun MusicPlaylist.songsFrom(
    songs: List<Song>,
    hiddenFolderPaths: Set<String> = emptySet(),
    excludeSmallAudios: Boolean = true,
): List<Song> {
    return orderedSongsFrom(songs)
        .filter { it.folderPath !in hiddenFolderPaths && it.isIncludedBySmallAudioFilter(excludeSmallAudios) }
}

internal fun MusicPlaylist.coverSongFrom(songs: List<Song>): Song? {
    return coverSongId?.let { coverId -> songs.firstOrNull { it.id == coverId } }
        ?: songs.firstOrNull { it.artworkUri != null }
        ?: songs.firstOrNull()
}

internal fun List<Song>.filterBySearch(query: String): List<Song> {
    val cleanQuery = query.normalizedSearchText()
    if (cleanQuery.isBlank()) return this

    return filter { song ->
        song.title.normalizedSearchText().contains(cleanQuery) ||
            song.artist.normalizedSearchText().contains(cleanQuery) ||
            song.album.normalizedSearchText().contains(cleanQuery) ||
            song.folderName.normalizedSearchText().contains(cleanQuery)
    }
}

internal fun List<MusicPlaylist>.filterByPlaylistSearch(query: String): List<MusicPlaylist> {
    val cleanQuery = query.normalizedSearchText()
    if (cleanQuery.isBlank()) return this

    return filter { playlist -> playlist.name.normalizedSearchText().contains(cleanQuery) }
}

internal fun List<MusicGroup>.filterByGroupSearch(query: String): List<MusicGroup> {
    val cleanQuery = query.normalizedSearchText()
    if (cleanQuery.isBlank()) return this

    return filter { group -> group.title.normalizedSearchText().contains(cleanQuery) }
}

internal fun String.normalizedSearchText(): String {
    return Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
}

internal fun List<Song>.toMusicGroups(
    view: MusicLibraryView,
    sortMode: SortMode,
): List<MusicGroup> {
    val grouped = when (view) {
        MusicLibraryView.Songs -> return emptyList()
        MusicLibraryView.Artists -> groupBy { it.artist }
        MusicLibraryView.Albums -> groupBy { it.album }
    }

    return grouped.map { (title, songs) ->
        val sortedSongs = songs.sortedForDisplay(sortMode)
        val subtitle = when (view) {
            MusicLibraryView.Songs -> ""
            MusicLibraryView.Artists -> {
                val albumCount = songs.map { it.album }.distinct().size
                "${songs.size.songCountLabel()} - ${albumCount.albumCountLabel()}"
            }
            MusicLibraryView.Albums -> songs
                .map { it.artist }
                .distinct()
                .sortedBy { it.lowercase() }
                .joinToString(", ")
        }
        MusicGroup(
            key = "${view.name}:${title.lowercase()}",
            title = title,
            subtitle = subtitle.ifBlank { songs.size.songCountLabel() },
            songs = sortedSongs,
        )
    }.sortedBy { it.title.lowercase() }
}

internal fun List<Song>.sortedForDisplay(sortMode: SortMode): List<Song> = when (sortMode) {
    SortMode.Recent -> sortedWith(compareByDescending<Song> { it.dateAddedSeconds }.thenBy { it.title.lowercase() })
    SortMode.Title -> sortedWith(compareBy<Song> { it.title.lowercase() }.thenBy { it.artist.lowercase() })
}
