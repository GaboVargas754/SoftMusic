package com.softmusic.app.ui

import java.util.Locale

internal fun formatDuration(durationMs: Long): String {
    val safeMs = durationMs.coerceAtLeast(0)
    val totalSeconds = safeMs / 1_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

internal fun Int.favoriteCountLabel(): String = when (this) {
    0 -> "Sin favoritos"
    1 -> "1 canción favorita"
    else -> "$this canciones favoritas"
}

internal fun Int.songCountLabel(): String = when (this) {
    1 -> "1 canción"
    else -> "$this canciones"
}

internal fun Int.artistCountLabel(): String = when (this) {
    1 -> "1 artista"
    else -> "$this artistas"
}

internal fun Int.albumCountLabel(): String = when (this) {
    1 -> "1 álbum"
    else -> "$this álbumes"
}

internal fun Int.playlistCountLabel(): String = when (this) {
    0 -> "Sin playlists"
    1 -> "1 playlist"
    else -> "$this playlists"
}
