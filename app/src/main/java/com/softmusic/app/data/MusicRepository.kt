package com.softmusic.app.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.core.database.getLongOrNull
import java.io.File

class MusicRepository {
    fun loadSongs(context: Context): List<Song> {
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        @Suppress("DEPRECATION")
        val folderColumnName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.RELATIVE_PATH
        } else {
            MediaStore.Audio.Media.DATA
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.ALBUM_ID,
            folderColumnName,
        )

        val songs = mutableListOf<Song>()
        resolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val folderColumn = cursor.getColumnIndex(folderColumnName)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val albumId = cursor.getLongOrNull(albumIdColumn)
                val contentUri = ContentUris.withAppendedId(collection, id)
                val artworkUri = albumId?.let {
                    ContentUris.withAppendedId(ALBUM_ART_URI, it)
                }
                val folder = resolveFolder(cursor.getStringOrNull(folderColumn))

                songs += Song(
                    id = id,
                    title = cursor.getString(titleColumn)?.takeIf { it.isNotBlank() } ?: "Sin título",
                    artist = cursor.getString(artistColumn)?.takeIf { it.isNotBlank() } ?: "Artista desconocido",
                    album = cursor.getString(albumColumn)?.takeIf { it.isNotBlank() } ?: "Álbum desconocido",
                    durationMs = cursor.getLong(durationColumn),
                    fileSizeBytes = cursor.getLongOrNull(sizeColumn) ?: 0L,
                    dateAddedSeconds = cursor.getLong(dateAddedColumn),
                    folderPath = folder.path,
                    folderName = folder.name,
                    uri = contentUri.toString(),
                    artworkUri = artworkUri?.toString(),
                )
            }
        }

        return songs
    }

    private fun resolveFolder(rawPath: String?): FolderInfo {
        val cleanPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            rawPath
                ?.trim()
                ?.trim('/')
                ?.takeIf { it.isNotBlank() }
        } else {
            rawPath
                ?.let { File(it).parent }
                ?.trim()
                ?.trim('/')
                ?.takeIf { it.isNotBlank() }
        } ?: DEFAULT_FOLDER

        val folderName = cleanPath.substringAfterLast('/').ifBlank { DEFAULT_FOLDER }
        return FolderInfo(path = cleanPath, name = folderName)
    }

    private fun android.database.Cursor.getStringOrNull(columnIndex: Int): String? =
        if (columnIndex >= 0 && !isNull(columnIndex)) getString(columnIndex) else null

    private data class FolderInfo(
        val path: String,
        val name: String,
    )

    private companion object {
        const val DEFAULT_FOLDER = "Música"
        val ALBUM_ART_URI = android.net.Uri.parse("content://media/external/audio/albumart")
    }
}
