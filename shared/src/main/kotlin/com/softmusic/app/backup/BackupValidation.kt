package com.softmusic.app.backup

import com.softmusic.app.data.MAX_PLAYLIST_NAME_LENGTH
import com.softmusic.app.data.normalizedPlaylistName

/** Validates the complete document before any persistent setting or collection is changed. */
fun validateBackupDocument(document: BackupDocument) {
    require(document.formatVersion == BACKUP_FORMAT_VERSION) {
        "La versión del respaldo no es compatible con esta versión de SoftMusic."
    }
    require(document.createdAtEpochMs >= 0L) { "La fecha del respaldo no es válida." }
    validateText(document.appVersion, "La versión de la aplicación", 128, allowBlank = false)
    require(document.songs.size <= MAX_BACKUP_SONGS) { "El respaldo contiene demasiadas canciones." }
    require(document.playlists.size <= MAX_BACKUP_PLAYLISTS) { "El respaldo contiene demasiadas listas." }
    require(document.favoriteKeys.size <= MAX_BACKUP_SONGS) { "El respaldo contiene demasiados favoritos." }

    val keys = HashSet<String>(document.songs.size)
    document.songs.forEach { song ->
        validateText(song.key, "La clave de una canción", allowBlank = false)
        require(keys.add(song.key)) { "El respaldo contiene claves de canción duplicadas." }
        song.fileName?.let { name ->
            validateText(name, "El nombre de archivo", MAX_BACKUP_PATH_LENGTH, allowBlank = false)
            require('/' !in name && '\\' !in name) { "El nombre de archivo debe contener solo el nombre, sin carpetas." }
        }
        validateText(song.title, "El título de una canción")
        validateText(song.artist, "El artista de una canción")
        validateText(song.album, "El álbum de una canción")
        require(song.durationMs == null || song.durationMs >= 0L) { "La duración de una canción no es válida." }
        require(song.fileSizeBytes == null || song.fileSizeBytes >= 0L) { "El tamaño de una canción no es válido." }
        song.folderPath?.let { validatePath(it) }
    }

    var entryCount = document.songs.size.toLong() + document.favoriteKeys.size
    val playlistNames = HashSet<String>(document.playlists.size)
    document.playlists.forEach { playlist ->
        validateText(playlist.name, "El nombre de una lista", MAX_PLAYLIST_NAME_LENGTH, allowBlank = false)
        require(playlistNames.add(normalizedPlaylistName(playlist.name))) { "El respaldo contiene nombres de lista duplicados." }
        entryCount += playlist.songKeys.size
        require(entryCount <= MAX_BACKUP_ENTRIES) { "El respaldo contiene demasiadas entradas." }
        val playlistKeys = HashSet<String>(playlist.songKeys.size)
        playlist.songKeys.forEach { key ->
            require(key in keys) { "Una lista hace referencia a una canción que no está en el respaldo." }
            require(playlistKeys.add(key)) { "Una lista contiene canciones duplicadas." }
        }
        require(playlist.coverKey == null || playlist.coverKey in playlistKeys) {
            "La portada de una lista debe ser una canción de esa lista."
        }
    }
    val favoriteKeys = HashSet<String>(document.favoriteKeys.size)
    document.favoriteKeys.forEach { key ->
        require(key in keys) { "Un favorito hace referencia a una canción que no está en el respaldo." }
        require(favoriteKeys.add(key)) { "El respaldo contiene favoritos duplicados." }
    }
    document.preferences?.let { preferences ->
        require(preferences.fontScale.isFinite() && preferences.fontScale in 0.80f..1.20f) {
            "El tamaño del texto del respaldo no es válido."
        }
        require(preferences.djMixDurationSeconds in 5..8) { "La duración de mezcla del respaldo no es válida." }
        preferences.defaultFolderPath?.let { validatePath(it) }
        require(preferences.hiddenFolderPaths.size <= MAX_BACKUP_SONGS) { "El respaldo contiene demasiadas carpetas ocultas." }
        preferences.hiddenFolderPaths.forEach { validatePath(it) }
        val autoDj = preferences.autoDjSettings
        require(autoDj.beats in setOf(8, 16, 32)) { "El número de tiempos de Auto DJ no es válido." }
        require(autoDj.intensity.isFinite() && autoDj.intensity in 0f..1f) { "La intensidad de Auto DJ no es válida." }
        entryCount += preferences.hiddenFolderPaths.size
    }
    require(entryCount <= MAX_BACKUP_ENTRIES) { "El respaldo contiene demasiadas entradas." }
}

private fun validatePath(path: String) = validateText(path, "La ruta de una carpeta", MAX_BACKUP_PATH_LENGTH, allowBlank = false)

private fun validateText(value: String, label: String, maxLength: Int = MAX_BACKUP_TEXT_LENGTH, allowBlank: Boolean = true) {
    require(value.length <= maxLength) { "$label es demasiado largo." }
    require(allowBlank || value.isNotBlank()) { "$label no puede estar vacío." }
    require(value.none { Character.isISOControl(it) }) { "$label contiene caracteres no válidos." }
}
