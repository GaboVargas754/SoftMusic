package com.softmusic.app.data

data class MusicPlaylist(
    val id: String,
    val name: String,
    val songIds: List<Long>,
)
