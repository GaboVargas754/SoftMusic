package com.softmusic.app.player

/** Counts from a successfully committed collection restore. */
data class BackupRestoreResult(
    val importedPlaylistCount: Int,
    val addedFavoriteCount: Int,
    val pendingSongCount: Int,
    val resolvedSongCount: Int,
)
