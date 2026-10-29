package com.softmusic.app.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.softmusic.app.data.Song

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(Uri.parse(uri))
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setDisplayTitle(title)
            .setSubtitle(artist)
            .setDescription(album)
            .setArtworkUri(artworkUri?.let(Uri::parse))
            .setDurationMs(durationMs.coerceAtLeast(0L))
            .setIsPlayable(true)
            .build(),
    )
    .build()

internal fun List<Song>.toMediaItems(): List<MediaItem> = map(Song::toMediaItem)

internal fun Player.mediaItemIds(): List<String> = List(mediaItemCount) { getMediaItemAt(it).mediaId }

internal class MediaItemSongIndex {
    private var indexedSongs: List<Song>? = null
    private var songsByMediaId: Map<String, Song> = emptyMap()

    fun find(mediaId: String?, songs: List<Song>, queue: List<Song>): Song? {
        mediaId ?: return null
        if (indexedSongs !== songs) {
            indexedSongs = songs
            songsByMediaId = songs.associateBy { it.id.toString() }
        }
        return songsByMediaId[mediaId] ?: queue.firstOrNull { it.id.toString() == mediaId }
    }
}
