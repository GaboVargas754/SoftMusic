package com.softmusic.app.desktop.data

import com.softmusic.app.data.Song
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.Locale

class DesktopMusicScanner {
    private val artworkCacheDir = resolveArtworkCacheDir()

    fun scan(root: Path): List<Song> {
        require(Files.isDirectory(root)) { "La ruta seleccionada no es una carpeta" }

        val normalizedRoot = root.toAbsolutePath().normalize()
        val songs = mutableListOf<Song>()
        Files.walkFileTree(
            normalizedRoot,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (attrs.isRegularFile && file.isSupportedAudioFile()) {
                        songs += file.toSong(normalizedRoot, attrs)
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: java.io.IOException): FileVisitResult {
                    return FileVisitResult.CONTINUE
                }
            },
        )

        return songs.sortedWith(compareBy<Song> { it.title.lowercase(Locale.getDefault()) }.thenBy { it.folderPath })
    }

    private fun Path.toSong(root: Path, attrs: BasicFileAttributes): Song {
        val normalizedPath = toAbsolutePath().normalize()
        val parent = normalizedPath.parent ?: root
        val folderPath = parent.toString()
        val folderName = parent.fileName?.toString()?.takeIf { it.isNotBlank() } ?: root.fileName?.toString() ?: "Música"
        val fileNameMetadata = normalizedPath.metadataFromFileName()
        val audioMetadata = normalizedPath.readAudioMetadata()

        return Song(
            id = normalizedPath.stableId(),
            title = audioMetadata.title ?: fileNameMetadata.title,
            artist = audioMetadata.artist ?: fileNameMetadata.artist ?: "Artista desconocido",
            album = audioMetadata.album ?: "Álbum desconocido",
            durationMs = audioMetadata.durationMs,
            fileSizeBytes = attrs.size().coerceAtLeast(0L),
            dateAddedSeconds = attrs.lastModifiedTime().toMillis().coerceAtLeast(0L) / 1_000L,
            folderPath = folderPath,
            folderName = folderName,
            uri = normalizedPath.toUri().toString(),
            artworkUri = audioMetadata.artworkUri,
        )
    }

    private fun Path.readAudioMetadata(): AudioMetadata {
        val audioFile = runCatching { AudioFileIO.read(toFile()) }.getOrNull()
        val tag = audioFile?.tag
        val durationMs = audioFile?.audioHeader?.trackLength
            ?.takeIf { it > 0 }
            ?.toLong()
            ?.times(1_000L)
            ?: 0L

        return AudioMetadata(
            title = tag?.getFirst(FieldKey.TITLE).cleanMetadataValue(),
            artist = tag?.getFirst(FieldKey.ARTIST).cleanMetadataValue()
                ?: tag?.getFirst(FieldKey.ALBUM_ARTIST).cleanMetadataValue(),
            album = tag?.getFirst(FieldKey.ALBUM).cleanMetadataValue(),
            durationMs = durationMs,
            artworkUri = tag?.firstArtwork?.let { artwork ->
                writeArtworkToCache(
                    songPath = this,
                    bytes = artwork.binaryData,
                    mimeType = artwork.mimeType,
                )
            },
        )
    }

    private fun writeArtworkToCache(songPath: Path, bytes: ByteArray?, mimeType: String?): String? {
        if (bytes == null || bytes.isEmpty()) return null
        return runCatching {
            Files.createDirectories(artworkCacheDir)
            val artworkPath = artworkCacheDir.resolve("${songPath.stableId()}.${mimeType.toArtworkExtension()}")
            Files.write(
                artworkPath,
                bytes,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
            artworkPath.toUri().toString()
        }.getOrNull()
    }

    private fun Path.isSupportedAudioFile(): Boolean {
        val extension = fileName?.toString()
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase(Locale.ROOT)
        return extension in supportedAudioExtensions
    }

    private fun Path.metadataFromFileName(): FileNameMetadata {
        val name = fileName?.toString().orEmpty()
        val withoutExtension = name.substringBeforeLast('.', missingDelimiterValue = name)
        val cleanName = withoutExtension
            .replace('_', ' ')
            .trim()
            .takeIf { it.isNotBlank() }
            ?: "Sin título"
        val parts = cleanName.split(" - ", limit = 2)
        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            return FileNameMetadata(
                title = parts[1].trim(),
                artist = parts[0].trim(),
            )
        }
        return FileNameMetadata(title = cleanName, artist = null)
    }

    private fun String?.cleanMetadataValue(): String? = this
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    private data class AudioMetadata(
        val title: String?,
        val artist: String?,
        val album: String?,
        val durationMs: Long,
        val artworkUri: String?,
    )

    private data class FileNameMetadata(
        val title: String,
        val artist: String?,
    )

    private fun Path.stableId(): Long {
        val value = toString()
        var hash = 1125899906842597L
        value.forEach { char ->
            hash = 31L * hash + char.code.toLong()
        }
        return hash and Long.MAX_VALUE
    }

    private fun String?.toArtworkExtension(): String = when (this?.lowercase(Locale.ROOT)) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "img"
    }

    private fun resolveArtworkCacheDir(): Path {
        val xdgCacheHome = System.getenv("XDG_CACHE_HOME")
            ?.takeIf { it.isNotBlank() }
            ?.let(Paths::get)
        val baseDir = xdgCacheHome ?: Paths.get(System.getProperty("user.home"), ".cache")
        return baseDir.resolve("SoftMusic").resolve("artwork")
    }

    private companion object {
        val supportedAudioExtensions = setOf(
            "aac",
            "aiff",
            "alac",
            "flac",
            "m4a",
            "mp3",
            "oga",
            "ogg",
            "opus",
            "wav",
            "wma",
        )
    }
}
