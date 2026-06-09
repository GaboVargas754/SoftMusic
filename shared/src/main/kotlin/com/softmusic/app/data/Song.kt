package com.softmusic.app.data

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val dateAddedSeconds: Long,
    val folderPath: String,
    val folderName: String,
    val uri: String,
    val artworkUri: String?,
)
