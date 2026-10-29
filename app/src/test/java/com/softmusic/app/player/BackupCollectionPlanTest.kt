package com.softmusic.app.player

import com.softmusic.app.backup.BackupDocument
import com.softmusic.app.backup.BackupMatchStatus
import com.softmusic.app.backup.BackupPlaylist
import com.softmusic.app.backup.BackupPreferences
import com.softmusic.app.backup.BackupSong
import com.softmusic.app.backup.BackupSongMatch
import com.softmusic.app.backup.matchBackupSongs
import com.softmusic.app.data.MusicPlaylist
import com.softmusic.app.data.Song
import com.softmusic.app.data.normalizedPlaylistName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCollectionPlanTest {
    @Test
    fun mergeCopiesListsUnionsFavoritesAndRetainsEveryUnresolvedReference() {
        val name = "x".repeat(50)
        val missing = reference("missing")
        val matched = reference("matched")
        val duplicate = reference("duplicate")
        val ambiguous = reference("ambiguous")
        val oldPlaylist = MusicPlaylist("old", name, listOf(-1L, -2L, 42L), -2L)
        val before = BackupCollectionState(listOf(oldPlaylist), setOf(42L), mapOf(-2L to reference("pending-2")))
        val document = document(listOf(missing, matched, duplicate, ambiguous),
            listOf(BackupPlaylist(name, listOf("matched", "missing", "duplicate", "ambiguous"), "missing")),
            listOf("matched", "missing"))
        val matches = listOf(
            BackupSongMatch(missing, BackupMatchStatus.Missing),
            BackupSongMatch(matched, BackupMatchStatus.Matched, 42L),
            BackupSongMatch(duplicate, BackupMatchStatus.Matched, 42L),
            BackupSongMatch(ambiguous, BackupMatchStatus.Ambiguous),
        )
        val plan = planBackupRestore(before, document, matches) { "new" }
        val restored = plan.state.playlists.last()
        assertEquals(oldPlaylist, plan.state.playlists.first())
        assertEquals(listOf(42L, -3L, -4L), restored.songIds)
        assertEquals(-3L, restored.coverSongId)
        assertEquals(setOf(42L, -3L), plan.state.favoriteSongIds)
        assertEquals(setOf(-2L, -3L, -4L), plan.state.pendingSongs.keys)
        assertEquals(missing.copy(key = "pending-3"), plan.state.pendingSongs[-3L])
        assertEquals(50, restored.name.length)
        assertTrue(restored.name.endsWith(" (restaurada)"))
        assertEquals(BackupRestoreResult(1, 1, 3, 2), plan.result)
    }

    @Test
    fun repeatedImportsUseFreshIdsAndUniqueAccentInsensitiveNames() {
        val document = document(emptyList(), listOf(BackupPlaylist("Musica", emptyList())), emptyList())
        val before = BackupCollectionState(listOf(MusicPlaylist("one", "Música", emptyList())), emptySet(), emptyMap())
        val first = planBackupRestore(before, document, emptyList()) { "two" }
        val second = planBackupRestore(first.state, document, emptyList()) { "three" }
        assertEquals(listOf("one", "two", "three"), second.state.playlists.map { it.id })
        assertEquals(listOf("Música", "Musica (restaurada)", "Musica (restaurada 2)"), second.state.playlists.map { it.name })
        assertEquals(3, second.state.playlists.map { normalizedPlaylistName(it.name) }.distinct().size)
        assertTrue(second.state.playlists.last().songIds.isEmpty())
    }

    @Test
    fun pendingResolutionUsesCurrentEditedOrderAndDeduplicatesAtTheFirstPosition() {
        val pendingA = reference("pending-3")
        val pendingB = reference("pending-4")
        val deleted = reference("pending-5")
        val before = BackupCollectionState(
            playlists = listOf(MusicPlaylist("edited", "Editada", listOf(11L, -4L, 22L, -3L), -3L)),
            favoriteSongIds = linkedSetOf(-4L, 33L),
            pendingSongs = linkedMapOf(-3L to pendingA, -4L to pendingB, -5L to deleted),
        )
        val matches = listOf(BackupSongMatch(pendingA, BackupMatchStatus.Matched, 22L),
            BackupSongMatch(pendingB, BackupMatchStatus.Matched, 33L))
        val plan = planPendingBackupResolution(before, matches)
        assertEquals(listOf(11L, 33L, 22L), plan.state.playlists.single().songIds)
        assertEquals(22L, plan.state.playlists.single().coverSongId)
        assertEquals(setOf(33L), plan.state.favoriteSongIds)
        assertTrue(plan.state.pendingSongs.isEmpty())
        assertEquals(BackupRestoreResult(0, 0, 0, 2), plan.result)
    }

    @Test
    fun unresolvedRetryAndOrdinaryPruningPreserveOnlyReferencesTheUserStillKeeps() {
        val favorite = reference("pending-1")
        val cover = reference("pending-2")
        val discarded = reference("pending-3")
        val current = BackupCollectionState(
            listOf(MusicPlaylist("list", "Lista", emptyList(), -2L)), setOf(-1L),
            linkedMapOf(-1L to favorite, -2L to cover, -3L to discarded),
        )
        val pruned = current.pruned()
        assertEquals(setOf(-1L, -2L), pruned.pendingSongs.keys)
        val plan = planPendingBackupResolution(pruned, listOf(
            BackupSongMatch(favorite, BackupMatchStatus.Missing),
            BackupSongMatch(cover, BackupMatchStatus.Ambiguous),
        ))
        assertEquals(pruned, plan.state)
        assertEquals(BackupRestoreResult(0, 0, 2, 0), plan.result)
    }

    @Test
    fun exportPreservesPendingMetadataAndUsesFullLibraryWithoutMatchingMissingLocalIds() {
        val pending = reference("pending-1").copy(folderPath = "/storage/old")
        val song = song(9L, "hidden.mp3", "/storage/hidden")
        val current = BackupCollectionState(
            listOf(MusicPlaylist("local-list-id", "Lista", listOf(9L, -1L, 777L), -1L)),
            setOf(777L), mapOf(-1L to pending),
        )
        val exported = buildBackupDocument(current, listOf(song), BackupPreferences(), 123L, "3.2")
        assertEquals(song.fileName, exported.songs[0].fileName)
        assertEquals(song.folderPath, exported.songs[0].folderPath)
        assertEquals(pending.copy(key = "song-2"), exported.songs[1])
        assertNull(exported.songs[2].fileName)
        assertNull(exported.songs[2].durationMs)
        assertEquals("song-2", exported.playlists.single().coverKey)
        assertEquals(listOf("song-3"), exported.favoriteKeys)
        val matches = matchBackupSongs(exported.songs, listOf(song, song(777L, "other.mp3", "/storage/new")))
        assertEquals(listOf(BackupMatchStatus.Matched, BackupMatchStatus.Missing, BackupMatchStatus.Missing), matches.map { it.status })
        val roundTrip = planBackupRestore(BackupCollectionState(emptyList(), emptySet(), emptyMap()), exported, matches) { "restored" }
        val pendingAgain = roundTrip.state.pendingSongs.values
        assertTrue(pendingAgain.any { it.fileName == pending.fileName && it.folderPath == pending.folderPath })
        assertEquals(2, roundTrip.result.pendingSongCount)
    }

    @Test
    fun anUnavailablePositiveIdIsNeverExportedAsAPortableFingerprint() {
        val current = BackupCollectionState(emptyList(), setOf(123L), emptyMap())
        val exported = buildBackupDocument(current, emptyList(), BackupPreferences(), 0L, "3.2")
        assertFalse(exported.songs.single().key.contains("123"))
        assertEquals(BackupMatchStatus.Missing, matchBackupSongs(exported.songs, listOf(song(123L))).single().status)
    }

    @Test
    fun exportKeepsSongsWithUnknownFilenamesAsUnresolvedReferences() {
        val current = BackupCollectionState(emptyList(), setOf(123L), emptyMap())
        val exported = buildBackupDocument(current, listOf(song(123L, "")), BackupPreferences(), 0L, "3.2")
        assertNull(exported.songs.single().fileName)
        assertEquals("Título", exported.songs.single().title)
        assertEquals(BackupMatchStatus.Missing, matchBackupSongs(exported.songs, listOf(song(123L, ""))).single().status)
    }

    @Test
    fun exportRejectsAnOversizedReferencedCollectionBeforeCreatingTheDocument() {
        val current = BackupCollectionState(emptyList(), (1L..10_001L).toSet(), emptyMap())
        assertFails { buildBackupDocument(current, emptyList(), BackupPreferences(), 0L, "3.2") }
    }

    @Test
    fun staleCollectionLibraryOrMatchingResultRequiresAnotherPreview() {
        val ref = reference("a")
        val expected = listOf(BackupSongMatch(ref, BackupMatchStatus.Missing))
        requireCurrentBackupPreview(1, 1, 2, 2, expected, expected)
        assertFails { requireCurrentBackupPreview(1, 2, 2, 2, expected, expected) }
        assertFails { requireCurrentBackupPreview(1, 1, 2, 3, expected, expected) }
        assertFails { requireCurrentBackupPreview(1, 1, 2, 2, expected,
            listOf(BackupSongMatch(ref, BackupMatchStatus.Matched, 8L))) }
    }

    @Test
    fun fabricatedMatchIdsAndMissingPreviewReferencesAreRejected() {
        val ref = reference("a")
        val doc = document(listOf(ref), emptyList(), listOf("a"))
        val current = BackupCollectionState(emptyList(), emptySet(), emptyMap())
        assertFails { planBackupRestore(current, doc, listOf(BackupSongMatch(ref, BackupMatchStatus.Matched, -1L))) }
        assertFails { planBackupRestore(current, doc, emptyList()) }
    }

    private fun reference(key: String) = BackupSong(key, "$key.mp3", "Título $key", "Artista", "Álbum", 90_000L, 3_000_000L, "/storage/music")

    private fun song(id: Long, fileName: String = "file.mp3", folder: String = "/storage/music") = Song(
        id = id, title = "Título", artist = "Artista", album = "Álbum", durationMs = 90_000L,
        fileSizeBytes = 3_000_000L, dateAddedSeconds = 1L, folderPath = folder,
        folderName = "music", uri = "content://media/$id", artworkUri = null, fileName = fileName,
    )

    private fun document(songs: List<BackupSong>, playlists: List<BackupPlaylist>, favoriteKeys: List<String>) =
        BackupDocument(createdAtEpochMs = 1L, appVersion = "3.2", songs = songs, playlists = playlists, favoriteKeys = favoriteKeys)

    private fun assertFails(block: () -> Unit) {
        val failure = runCatching(block).exceptionOrNull()
        assertTrue("Expected validation or stale-preview failure", failure is IllegalArgumentException || failure is IllegalStateException)
    }
}
