package com.softmusic.app.backup

import com.softmusic.app.player.AutoDjSettings
import com.softmusic.app.player.DjMixMode
import com.softmusic.app.player.PlaybackMode
import com.softmusic.app.player.SortMode

const val BACKUP_FORMAT = "SoftMusicBackup"
const val BACKUP_FORMAT_VERSION = 1
const val MAX_BACKUP_BYTES = 10 * 1024 * 1024
const val MAX_BACKUP_SONGS = 10_000
const val MAX_BACKUP_PLAYLISTS = 500
const val MAX_BACKUP_ENTRIES = 100_000
const val MAX_BACKUP_PATH_LENGTH = 4_096
const val MAX_BACKUP_TEXT_LENGTH = 512

/** Keys belong to this document; neither MediaStore IDs nor URIs identify a file on another device. */
data class BackupSong(
    val key: String,
    val fileName: String?,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long?,
    val fileSizeBytes: Long?,
    val folderPath: String?,
)

data class BackupPlaylist(
    val name: String,
    val songKeys: List<String>,
    val coverKey: String? = null,
)

data class BackupPreferences(
    val highPerformanceMode: Boolean = false,
    val fontScale: Float = 1f,
    val playbackMode: PlaybackMode = PlaybackMode.Ordered,
    val sortMode: SortMode = SortMode.Recent,
    val excludeSmallAudios: Boolean = true,
    val defaultFolderPath: String? = null,
    val hiddenFolderPaths: Set<String> = emptySet(),
    val djModeEnabled: Boolean = false,
    val djMixMode: DjMixMode = DjMixMode.Classic,
    val djMixDurationSeconds: Int = 8,
    val autoDjSettings: AutoDjSettings = AutoDjSettings(),
)

data class BackupDocument(
    val formatVersion: Int = BACKUP_FORMAT_VERSION,
    val createdAtEpochMs: Long,
    val appVersion: String,
    val songs: List<BackupSong>,
    val playlists: List<BackupPlaylist>,
    val favoriteKeys: List<String>,
    val preferences: BackupPreferences? = null,
)

enum class BackupMatchStatus { Matched, Missing, Ambiguous }

data class BackupSongMatch(
    val reference: BackupSong,
    val status: BackupMatchStatus,
    val songId: Long? = null,
)
