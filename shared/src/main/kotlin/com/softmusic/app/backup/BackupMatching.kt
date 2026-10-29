package com.softmusic.app.backup

import com.softmusic.app.data.Song
import java.text.Normalizer
import java.util.Locale

/**
 * Uses a complete file fingerprint and never guesses from a title or a device-local ID.
 * A unique unchanged folder can distinguish copies. Moving a unique file remains safe.
 * Distinct references cannot silently claim the same local file: keep only a sole exact
 * folder claim, otherwise retain those references as ambiguous for later review.
 * Both indexes are built once, so work grows linearly with the library and reference counts.
 */
fun matchBackupSongs(references: List<BackupSong>, library: List<Song>): List<BackupSongMatch> {
    val byFingerprint = HashMap<FileFingerprint, Candidate>()
    val byLocation = HashMap<LocatedFingerprint, Candidate>()
    library.forEach { song ->
        val fingerprint = fingerprint(song.fileName, song.title, song.artist, song.album, song.durationMs, song.fileSizeBytes)
            ?: return@forEach
        byFingerprint.getOrPut(fingerprint) { Candidate() }.add(song.id)
        normalizeFolder(song.folderPath)?.let { folder ->
            byLocation.getOrPut(LocatedFingerprint(fingerprint, folder)) { Candidate() }.add(song.id)
        }
    }
    val claimsById = HashMap<Long, ReferenceClaims>()
    val initialMatches = references.mapIndexed { index, reference ->
        val fingerprint = fingerprint(reference.fileName, reference.title, reference.artist, reference.album,
            reference.durationMs, reference.fileSizeBytes)
            ?: return@mapIndexed BackupSongMatch(reference, BackupMatchStatus.Missing)
        val candidates = byFingerprint[fingerprint]
        val located = normalizeFolder(reference.folderPath)?.let { folder ->
            byLocation[LocatedFingerprint(fingerprint, folder)]
        }
        val match = when {
            candidates == null -> BackupSongMatch(reference, BackupMatchStatus.Missing)
            candidates.count == 1 -> BackupSongMatch(reference, BackupMatchStatus.Matched, candidates.songId)
            else -> {
                if (located?.count == 1) BackupSongMatch(reference, BackupMatchStatus.Matched, located.songId)
                else BackupSongMatch(reference, BackupMatchStatus.Ambiguous)
            }
        }
        match.songId?.let { id ->
            claimsById.getOrPut(id) { ReferenceClaims() }
                .add(index, exactFolder = located?.count == 1 && located.songId == id)
        }
        match
    }
    return initialMatches.mapIndexed { index, match ->
        val claims = match.songId?.let(claimsById::get)
        if (claims != null && claims.count > 1 &&
            (claims.exactFolderCount != 1 || claims.exactFolderIndex != index)
        ) BackupSongMatch(match.reference, BackupMatchStatus.Ambiguous)
        else match
    }
}

private data class FileFingerprint(
    val fileName: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
)

private data class LocatedFingerprint(val fingerprint: FileFingerprint, val folder: String)

private class Candidate {
    var count: Int = 0
        private set
    var songId: Long = 0
        private set

    fun add(id: Long) {
        count++
        songId = id
    }
}

private class ReferenceClaims {
    var count = 0
        private set
    var exactFolderCount = 0
        private set
    var exactFolderIndex = -1
        private set

    fun add(index: Int, exactFolder: Boolean) {
        count++
        if (exactFolder) {
            exactFolderCount++
            exactFolderIndex = index
        }
    }
}

private fun fingerprint(fileName: String?, title: String, artist: String, album: String,
    durationMs: Long?, fileSizeBytes: Long?): FileFingerprint? {
    if (fileName.isNullOrBlank() || title.isBlank() || artist.isBlank() || album.isBlank() ||
        durationMs == null || durationMs <= 0 || fileSizeBytes == null || fileSizeBytes <= 0
    ) return null
    // Preserve filename case and spacing: different actual filenames must not collapse into one guess.
    return FileFingerprint(Normalizer.normalize(fileName, Normalizer.Form.NFC), normalizeMetadata(title),
        normalizeMetadata(artist), normalizeMetadata(album), durationMs, fileSizeBytes)
}

private val metadataWhitespace = Regex("\\s+")
private fun normalizeMetadata(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFC)
    .trim().replace(metadataWhitespace, " ").lowercase(Locale.ROOT)

private fun normalizeFolder(value: String?): String? = value?.takeIf { it.isNotBlank() }
    ?.let { Normalizer.normalize(it, Normalizer.Form.NFC).trimEnd('/') }
