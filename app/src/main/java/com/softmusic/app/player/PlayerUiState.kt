package com.softmusic.app.player

import com.softmusic.app.data.Song
import com.softmusic.app.data.MusicFolder
import com.softmusic.app.data.MusicPlaylist

data class PlayerUiState(
    val songs: List<Song> = emptyList(),
    val visibleSongs: List<Song> = emptyList(),
    val playbackQueue: List<Song> = emptyList(),
    val favoriteSongIds: Set<Long> = emptySet(),
    val playlists: List<MusicPlaylist> = emptyList(),
    val allFolders: List<MusicFolder> = emptyList(),
    val folders: List<MusicFolder> = emptyList(),
    val selectedFolderPath: String? = null,
    val hiddenFolderPaths: Set<String> = emptySet(),
    val currentSong: Song? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    val shuffleEnabled: Boolean = false,
    val repeatSetting: RepeatSetting = RepeatSetting.Off,
    val playbackMode: PlaybackMode = PlaybackMode.Ordered,
    val sortMode: SortMode = SortMode.Recent,
    val djModeEnabled: Boolean = false,
    val djMixDurationSeconds: Int = 8,
    val hasController: Boolean = false,
)
