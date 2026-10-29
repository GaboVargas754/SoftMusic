package com.softmusic.app.backup

import com.softmusic.app.player.AutoDjSettings
import kotlin.test.Test
import kotlin.test.assertFailsWith

class BackupValidationTest {
    @Test
    fun `valid document and empty collections are accepted`() {
        validateBackupDocument(document())
        validateBackupDocument(document().copy(songs = emptyList(), playlists = emptyList(), favoriteKeys = emptyList()))
    }

    @Test
    fun `duplicate or dangling keys and cover outside playlist are rejected`() {
        val document = document()
        listOf(
            document.copy(songs = document.songs + document.songs),
            document.copy(favoriteKeys = listOf("song-1", "song-1")),
            document.copy(favoriteKeys = listOf("gone")),
            document.copy(playlists = listOf(BackupPlaylist("Lista", listOf("gone")))),
            document.copy(playlists = listOf(BackupPlaylist("Lista", listOf("song-1", "song-1")))),
            document.copy(playlists = listOf(BackupPlaylist("Lista", emptyList(), "song-1"))),
            document.copy(playlists = listOf(BackupPlaylist("Música", emptyList()), BackupPlaylist("musica", emptyList()))),
        ).forEach { invalid -> assertFailsWith<IllegalArgumentException> { validateBackupDocument(invalid) } }
    }

    @Test
    fun `version timestamp bounds names paths and preferences are validated`() {
        val document = document()
        listOf(
            document.copy(formatVersion = 2),
            document.copy(createdAtEpochMs = -1),
            document.copy(appVersion = ""),
            document.copy(songs = listOf(document.songs.single().copy(durationMs = -1))),
            document.copy(songs = listOf(document.songs.single().copy(fileSizeBytes = -1))),
            document.copy(songs = listOf(document.songs.single().copy(fileName = "folder/track.mp3"))),
            document.copy(songs = listOf(document.songs.single().copy(title = "x".repeat(MAX_BACKUP_TEXT_LENGTH + 1)))),
            document.copy(songs = listOf(document.songs.single().copy(folderPath = "x".repeat(MAX_BACKUP_PATH_LENGTH + 1)))),
            document.copy(playlists = listOf(BackupPlaylist(" ", emptyList()))),
            document.copy(preferences = BackupPreferences(fontScale = Float.NaN)),
            document.copy(preferences = BackupPreferences(fontScale = 2f)),
            document.copy(preferences = BackupPreferences(djMixDurationSeconds = 9)),
            document.copy(preferences = BackupPreferences(autoDjSettings = AutoDjSettings(intensity = Float.POSITIVE_INFINITY))),
            document.copy(preferences = BackupPreferences(autoDjSettings = AutoDjSettings(beats = 9))),
        ).forEach { invalid -> assertFailsWith<IllegalArgumentException> { validateBackupDocument(invalid) } }
    }

    @Test
    fun `collection limits and total playlist entries are bounded`() {
        val reference = document().songs.single()
        assertFailsWith<IllegalArgumentException> {
            validateBackupDocument(document().copy(songs = List(MAX_BACKUP_SONGS + 1) { reference.copy(key = "song-$it") }))
        }
        assertFailsWith<IllegalArgumentException> {
            validateBackupDocument(document().copy(playlists = List(MAX_BACKUP_PLAYLISTS + 1) { BackupPlaylist("Lista $it", emptyList()) }))
        }
        val references = List(MAX_BACKUP_SONGS) { reference.copy(key = "song-$it") }
        assertFailsWith<IllegalArgumentException> {
            validateBackupDocument(document().copy(songs = references,
                playlists = List(11) { BackupPlaylist("Lista $it", references.map { it.key }) }))
        }
    }

    private fun document() = BackupDocument(createdAtEpochMs = 1_790_000_000_000, appVersion = "3.2.0",
        songs = listOf(BackupSong("song-1", "track.mp3", "Título", "Artista", "Álbum", 180_000, 3_000_000, "/music")),
        playlists = listOf(BackupPlaylist("Mi lista", listOf("song-1"), "song-1")), favoriteKeys = listOf("song-1"))
}
