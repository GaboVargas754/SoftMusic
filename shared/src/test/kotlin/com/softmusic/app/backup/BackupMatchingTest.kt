package com.softmusic.app.backup

import com.softmusic.app.data.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackupMatchingTest {
    @Test
    fun `changed IDs and URI still match exact file identity`() {
        val result = matchBackupSongs(listOf(reference()), listOf(song().copy(id = 909L, uri = "content://another/909"))).single()

        assertEquals(BackupMatchStatus.Matched, result.status)
        assertEquals(909L, result.songId)
    }

    @Test
    fun `unique complete identity can move folders and normalize metadata`() {
        val local = song().copy(folderPath = "/new/music", title = "  CANCIÓN  bonita ", artist = "ARTISTA")
        val result = matchBackupSongs(listOf(reference()), listOf(local)).single()

        assertEquals(BackupMatchStatus.Matched, result.status)
        assertEquals(local.id, result.songId)
    }

    @Test
    fun `duplicates are ambiguous and never pick the first file`() {
        val copies = listOf(song().copy(id = 1, folderPath = "/copy-a"), song().copy(id = 2, folderPath = "/copy-b"))
        val result = matchBackupSongs(listOf(reference()), copies).single()

        assertEquals(BackupMatchStatus.Ambiguous, result.status)
        assertNull(result.songId)
        assertEquals(result, matchBackupSongs(listOf(reference()), copies.reversed()).single())
    }

    @Test
    fun `exact unique folder can resolve copies but duplicate same locations cannot`() {
        val original = song()
        val elsewhere = song().copy(id = 2, folderPath = "/elsewhere")
        val selected = matchBackupSongs(listOf(reference()), listOf(elsewhere, original)).single()
        assertEquals(BackupMatchStatus.Matched, selected.status)
        assertEquals(original.id, selected.songId)

        val duplicatedLocation = matchBackupSongs(listOf(reference()), listOf(original, original.copy(id = 3))).single()
        assertEquals(BackupMatchStatus.Ambiguous, duplicatedLocation.status)
        assertNull(duplicatedLocation.songId)
    }

    @Test
    fun `two physical references cannot collapse into one remaining local copy`() {
        val original = reference().copy(key = "original", folderPath = "/music")
        val missingCopy = reference().copy(key = "copy", folderPath = "/copy")
        val results = matchBackupSongs(listOf(missingCopy, original), listOf(song()))

        assertEquals(listOf(BackupMatchStatus.Ambiguous, BackupMatchStatus.Matched), results.map { it.status })
        assertNull(results[0].songId)
        assertEquals(1L, results[1].songId)
        assertEquals(results.reversed(), matchBackupSongs(listOf(original, missingCopy), listOf(song())))
    }

    @Test
    fun `colliding moved references and repeated identical references remain ambiguous`() {
        val first = reference().copy(key = "first", folderPath = "/first-old-folder")
        val second = reference().copy(key = "second", folderPath = "/second-old-folder")
        val moved = matchBackupSongs(listOf(first, second), listOf(song()))
        assertEquals(listOf(BackupMatchStatus.Ambiguous, BackupMatchStatus.Ambiguous), moved.map { it.status })
        assertEquals(listOf(null, null), moved.map { it.songId })

        val repeated = matchBackupSongs(listOf(reference(), reference().copy(key = "another-key")), listOf(song()))
        assertEquals(listOf(BackupMatchStatus.Ambiguous, BackupMatchStatus.Ambiguous), repeated.map { it.status })
    }

    @Test
    fun `distinct exact local copies keep independent matches`() {
        val first = reference().copy(key = "first", folderPath = "/first")
        val second = reference().copy(key = "second", folderPath = "/second")
        val results = matchBackupSongs(listOf(first, second), listOf(
            song().copy(id = 10, folderPath = "/first"), song().copy(id = 20, folderPath = "/second")))

        assertEquals(listOf(BackupMatchStatus.Matched, BackupMatchStatus.Matched), results.map { it.status })
        assertEquals(listOf(10L, 20L), results.map { it.songId })
    }

    @Test
    fun `same metadata alone or reused local IDs never match a different file`() {
        listOf(
            song().copy(fileName = "other.mp3"),
            song().copy(fileSizeBytes = 3_000_001),
            song().copy(durationMs = 181_000),
            song().copy(album = "Otro álbum"),
        ).forEach { different ->
            val result = matchBackupSongs(listOf(reference()), listOf(different)).single()
            assertEquals(BackupMatchStatus.Missing, result.status)
            assertNull(result.songId)
        }
    }

    @Test
    fun `incomplete references and incomplete local files stay missing`() {
        val incomplete = listOf(reference().copy(fileName = null), reference().copy(fileName = ""),
            reference().copy(durationMs = null), reference().copy(durationMs = 0),
            reference().copy(fileSizeBytes = null), reference().copy(fileSizeBytes = 0),
            reference().copy(title = ""), reference().copy(artist = " "), reference().copy(album = ""))
        assertEquals(List(incomplete.size) { BackupMatchStatus.Missing },
            matchBackupSongs(incomplete, listOf(song())).map { it.status })
        assertEquals(BackupMatchStatus.Missing,
            matchBackupSongs(listOf(reference()), listOf(song().copy(fileName = ""))).single().status)
    }

    @Test
    fun `unknown tag sentinel is a literal matching value and reference ordering is preserved`() {
        val unknown = reference().copy(artist = "<unknown>", album = "<unknown>")
        val results = matchBackupSongs(listOf(reference().copy(key = "missing", fileName = "gone.mp3"), unknown),
            listOf(song().copy(artist = "<unknown>", album = "<unknown>")))
        assertEquals(listOf("missing", "song-1"), results.map { it.reference.key })
        assertEquals(listOf(BackupMatchStatus.Missing, BackupMatchStatus.Matched), results.map { it.status })
    }

    private fun reference() = BackupSong("song-1", "track.mp3", "Canción bonita", "Artista", "Álbum",
        180_000, 3_000_000, "/music")

    private fun song() = Song(1, "Canción bonita", "Artista", "Álbum", 180_000, 3_000_000, 1,
        "/music", "music", "content://media/1", null, fileName = "track.mp3")
}
