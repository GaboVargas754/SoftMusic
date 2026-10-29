package com.softmusic.app.ui

import com.softmusic.app.data.Song

internal enum class MusicLibraryView(val label: String) {
    Songs("Canciones"),
    Artists("Artistas"),
    Albums("Álbumes"),
}

internal data class MusicGroup(
    val key: String,
    val title: String,
    val subtitle: String,
    val songs: List<Song>,
)
