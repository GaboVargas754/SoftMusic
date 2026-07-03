package com.softmusic.app.player

import com.softmusic.app.data.MusicFolder
import com.softmusic.app.data.Song
import com.softmusic.app.data.isIncludedBySmallAudioFilter

fun SortMode.songComparator(): Comparator<Song> = when (this) {
    SortMode.Recent -> compareByDescending<Song> { it.dateAddedSeconds }.thenBy { it.title.lowercase() }
    SortMode.Title -> compareBy<Song> { it.title.lowercase() }.thenBy { it.artist.lowercase() }
}

fun List<Song>.filteredAndSorted(
    selectedFolderPath: String?,
    sortMode: SortMode,
    hiddenFolderPaths: Set<String>,
    excludeSmallAudios: Boolean,
): List<Song> = asSequence()
    .filter { it.folderPath !in hiddenFolderPaths }
    .filter { it.isIncludedBySmallAudioFilter(excludeSmallAudios) }
    .filter { selectedFolderPath == null || it.folderPath == selectedFolderPath }
    .sortedWith(sortMode.songComparator())
    .toList()

fun List<Song>.visibleSongs(
    hiddenFolderPaths: Set<String>,
    excludeSmallAudios: Boolean,
): List<Song> = filter {
    it.folderPath !in hiddenFolderPaths && it.isIncludedBySmallAudioFilter(excludeSmallAudios)
}

fun List<MusicFolder>.visibleFolders(hiddenFolderPaths: Set<String>): List<MusicFolder> = filterNot { it.path in hiddenFolderPaths }

fun PlayerUiState.playableSongs(): List<Song> = visibleSongs.ifEmpty { songs.visibleSongs(hiddenFolderPaths, excludeSmallAudios) }

fun List<Song>.toFolders(): List<MusicFolder> = groupBy { it.folderPath }
    .map { (path, songs) ->
        MusicFolder(
            path = path,
            name = songs.firstOrNull()?.folderName ?: path.substringAfterLast('/'),
            songCount = songs.size,
        )
    }
    .sortedWith(compareBy<MusicFolder> { it.name.lowercase() }.thenBy { it.path.lowercase() })
