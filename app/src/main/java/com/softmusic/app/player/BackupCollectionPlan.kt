package com.softmusic.app.player

import com.softmusic.app.backup.BackupDocument
import com.softmusic.app.backup.BackupMatchStatus
import com.softmusic.app.backup.BackupPlaylist
import com.softmusic.app.backup.BackupPreferences
import com.softmusic.app.backup.BackupSong
import com.softmusic.app.backup.BackupSongMatch
import com.softmusic.app.backup.MAX_BACKUP_SONGS
import com.softmusic.app.backup.validateBackupDocument
import com.softmusic.app.data.MAX_PLAYLIST_NAME_LENGTH
import com.softmusic.app.data.MusicPlaylist
import com.softmusic.app.data.Song
import com.softmusic.app.data.normalizedPlaylistName
import java.util.UUID

internal data class BackupCollectionState(
    val playlists: List<MusicPlaylist>,
    val favoriteSongIds: Set<Long>,
    val pendingSongs: Map<Long, BackupSong>,
)

internal data class BackupCollectionPlan(
    val state: BackupCollectionState,
    val result: BackupRestoreResult,
)

internal const val BACKUP_CHANGED_MESSAGE =
    "La biblioteca o tus listas cambiaron. Vuelve a revisar la vista previa antes de restaurar."

internal fun requireCurrentBackupPreview(
    expectedCollectionRevision: Long,
    currentCollectionRevision: Long,
    expectedLibraryRevision: Long,
    currentLibraryRevision: Long,
    expectedMatches: List<BackupSongMatch>,
    currentMatches: List<BackupSongMatch>,
) {
    check(expectedCollectionRevision == currentCollectionRevision &&
        expectedLibraryRevision == currentLibraryRevision && expectedMatches == currentMatches) {
        BACKUP_CHANGED_MESSAGE
    }
}

/** Keep only placeholders still used by a favorite, a playlist slot, or its selected cover. */
internal fun BackupCollectionState.pruned(): BackupCollectionState {
    val referencedIds = favoriteSongIds.toMutableSet()
    playlists.forEach { playlist ->
        referencedIds.addAll(playlist.songIds)
        playlist.coverSongId?.let(referencedIds::add)
    }
    return copy(pendingSongs = pendingSongs.filterKeys { it < 0L && it in referencedIds })
}

internal fun planBackupRestore(
    current: BackupCollectionState,
    document: BackupDocument,
    matches: List<BackupSongMatch>,
    newPlaylistId: () -> String = { UUID.randomUUID().toString() },
): BackupCollectionPlan {
    validateBackupDocument(document)
    requireBackupMatches(document.songs, matches)
    val before = current.pruned()
    val pending = before.pendingSongs.toMutableMap()
    val occupiedIds = before.favoriteSongIds.toMutableSet().apply {
        before.playlists.forEach { addAll(it.songIds); it.coverSongId?.let(::add) }
        addAll(pending.keys)
    }
    var nextPendingId = -1L
    fun allocatePendingId(): Long {
        while (nextPendingId in occupiedIds) {
            check(nextPendingId > Long.MIN_VALUE) { "No hay espacio para guardar canciones pendientes." }
            nextPendingId--
        }
        return nextPendingId.also { occupiedIds.add(it) }
    }
    val referencedKeys = document.favoriteKeys.toMutableSet().apply {
        document.playlists.forEach { addAll(it.songKeys); it.coverKey?.let(::add) }
    }
    val idsByKey = buildMap {
        matches.forEach { match ->
            if (match.reference.key !in referencedKeys) return@forEach
            val id = if (match.status == BackupMatchStatus.Matched) requireNotNull(match.songId) else {
                val pendingId = allocatePendingId()
                pending[pendingId] = match.reference.copy(key = pendingReferenceKey(pendingId))
                pendingId
            }
            put(match.reference.key, id)
        }
    }
    require(pending.size <= MAX_BACKUP_SONGS) { "Hay demasiadas canciones pendientes. Resuélvelas antes de importar otro respaldo." }
    val names = before.playlists.mapTo(HashSet()) { normalizedPlaylistName(it.name) }
    val playlistIds = before.playlists.mapTo(HashSet()) { it.id }
    val imported = document.playlists.map { playlist ->
        var id = newPlaylistId()
        var attempts = 0
        while (id.isBlank() || id in playlistIds) {
            check(++attempts < 100) { "No se pudo crear un identificador para la lista restaurada." }
            id = newPlaylistId()
        }
        playlistIds.add(id)
        MusicPlaylist(
            id = id,
            name = uniqueRestoredPlaylistName(playlist.name, names),
            songIds = playlist.songKeys.map { idsByKey.getValue(it) }.distinct(),
            coverSongId = playlist.coverKey?.let(idsByKey::getValue),
        )
    }
    val favorites = before.favoriteSongIds + document.favoriteKeys.map { idsByKey.getValue(it) }
    val after = BackupCollectionState(before.playlists + imported, favorites, pending).pruned()
    return BackupCollectionPlan(after, BackupRestoreResult(
        importedPlaylistCount = imported.size,
        addedFavoriteCount = (favorites - before.favoriteSongIds).size,
        pendingSongCount = after.pendingSongs.size,
        resolvedSongCount = matches.count { it.reference.key in referencedKeys && it.status == BackupMatchStatus.Matched },
    ))
}

/** Replace placeholders in the latest order, preserving edits made since the original import. */
internal fun planPendingBackupResolution(
    current: BackupCollectionState,
    matches: List<BackupSongMatch>,
): BackupCollectionPlan {
    val before = current.pruned()
    requireBackupMatches(before.pendingSongs.values.toList(), matches)
    val replacements = buildMap {
        before.pendingSongs.keys.zip(matches).forEach { (pendingId, match) ->
            if (match.status == BackupMatchStatus.Matched) put(pendingId, requireNotNull(match.songId))
        }
    }
    val playlists = before.playlists.map { playlist ->
        playlist.copy(
            songIds = playlist.songIds.map { replacements[it] ?: it }.distinct(),
            coverSongId = playlist.coverSongId?.let { replacements[it] ?: it },
        )
    }
    val favorites = before.favoriteSongIds.mapTo(LinkedHashSet()) { replacements[it] ?: it }
    val after = BackupCollectionState(playlists, favorites, before.pendingSongs - replacements.keys).pruned()
    return BackupCollectionPlan(after, BackupRestoreResult(
        importedPlaylistCount = 0,
        addedFavoriteCount = (favorites.filter { it > 0L }.toSet() - before.favoriteSongIds).size,
        pendingSongCount = after.pendingSongs.size,
        resolvedSongCount = replacements.size,
    ))
}

/** Export references from the full library, including filtered folders and small audio files. */
internal fun buildBackupDocument(
    current: BackupCollectionState,
    library: List<Song>,
    preferences: BackupPreferences,
    createdAtEpochMs: Long,
    appVersion: String,
): BackupDocument {
    val referencedIds = LinkedHashSet<Long>()
    current.playlists.forEach { referencedIds.addAll(it.songIds) }
    referencedIds.addAll(current.favoriteSongIds)
    require(referencedIds.size <= MAX_BACKUP_SONGS) { "Tus listas y favoritos superan el límite de 10 000 canciones por respaldo." }
    val keysById = referencedIds.withIndex().associate { (index, id) -> id to "song-${index + 1}" }
    val libraryById = library.associateBy { it.id }
    val songs = referencedIds.map { id ->
        val key = keysById.getValue(id)
        libraryById[id]?.let { song ->
            BackupSong(key, song.fileName.takeIf(String::isNotBlank), song.title, song.artist, song.album,
                song.durationMs, song.fileSizeBytes, song.folderPath.takeIf(String::isNotBlank))
        } ?: current.pendingSongs[id]?.copy(key = key)
            // An unavailable local ID has no portable identity; deliberately leave it unmatchable.
            ?: BackupSong(key, null, "", "", "", null, null, null)
    }
    val names = HashSet<String>()
    val playlists = current.playlists.map { playlist ->
        BackupPlaylist(
            name = uniqueRestoredPlaylistName(playlist.name, names),
            songKeys = playlist.songIds.distinct().map { keysById.getValue(it) },
            coverKey = playlist.coverSongId?.takeIf { it in playlist.songIds }?.let(keysById::get),
        )
    }
    return BackupDocument(
        createdAtEpochMs = createdAtEpochMs,
        appVersion = appVersion,
        songs = songs,
        playlists = playlists,
        favoriteKeys = current.favoriteSongIds.map { keysById.getValue(it) },
        preferences = preferences,
    ).also(::validateBackupDocument)
}

private fun requireBackupMatches(references: List<BackupSong>, matches: List<BackupSongMatch>) {
    require(matches.map { it.reference } == references && matches.all {
        if (it.status == BackupMatchStatus.Matched) (it.songId ?: 0L) > 0L else it.songId == null
    }) { BACKUP_CHANGED_MESSAGE }
}

private fun pendingReferenceKey(id: Long): String = "pending-${id.toString().removePrefix("-")}"

private fun uniqueRestoredPlaylistName(original: String, names: MutableSet<String>): String {
    val base = original.trim().take(MAX_PLAYLIST_NAME_LENGTH).ifBlank { "Lista" }
    if (names.add(normalizedPlaylistName(base))) return base
    var counter = 1
    while (true) {
        val suffix = if (counter == 1) " (restaurada)" else " (restaurada $counter)"
        val candidate = base.take(MAX_PLAYLIST_NAME_LENGTH - suffix.length).trimEnd() + suffix
        if (names.add(normalizedPlaylistName(candidate))) return candidate
        counter++
    }
}
