package com.softmusic.app.data

fun MusicPlaylist.orderedSongsFrom(songs: List<Song>): List<Song> {
    val songsById = songs.associateBy { it.id }
    return songIds.mapNotNull(songsById::get)
}
