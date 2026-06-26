package com.softmusic.app.data

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val dateAddedSeconds: Long,
    val folderPath: String,
    val folderName: String,
    val uri: String,
    val artworkUri: String?,
)

const val SMALL_AUDIO_MIN_DURATION_MS = 30_000L
const val SMALL_AUDIO_MIN_FILE_SIZE_BYTES = 1_000_000L

fun Song.isIncludedBySmallAudioFilter(excludeSmallAudios: Boolean): Boolean {
    if (!excludeSmallAudios) return true
    return durationMs >= SMALL_AUDIO_MIN_DURATION_MS && fileSizeBytes >= SMALL_AUDIO_MIN_FILE_SIZE_BYTES
}
