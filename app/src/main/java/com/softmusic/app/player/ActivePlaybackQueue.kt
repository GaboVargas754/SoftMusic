package com.softmusic.app.player

import com.softmusic.app.data.Song

/** Queue order and origin move together so a manual edit cannot lose collection metadata. */
internal data class ActivePlaybackQueueSnapshot(
    val songs: List<Song> = emptyList(),
    val sourceSongs: List<Song> = emptyList(),
    val source: PlaybackQueueSource = PlaybackQueueSource.Songs,
    val sourceKey: String? = null,
    val mode: PlaybackMode? = null,
    val hasManualEdits: Boolean = false,
) {
    val ids: List<Long> = songs.map { it.id }
    val mediaIds: List<String> = ids.map(Long::toString)
    val idSet: Set<Long> = ids.toSet()
}

internal class ActivePlaybackQueue {
    var snapshot = ActivePlaybackQueueSnapshot()
        private set

    fun replace(
        songs: List<Song>,
        sourceSongs: List<Song>,
        source: PlaybackQueueSource = snapshot.source,
        sourceKey: String? = snapshot.sourceKey,
        mode: PlaybackMode?,
        hasManualEdits: Boolean = false,
    ) {
        snapshot = ActivePlaybackQueueSnapshot(
            songs = songs.toList(),
            sourceSongs = sourceSongs.toList(),
            source = source,
            sourceKey = sourceKey,
            mode = mode,
            hasManualEdits = hasManualEdits,
        )
    }

    fun adoptOrigin(source: PlaybackQueueSource, sourceKey: String?) {
        snapshot = snapshot.copy(source = source, sourceKey = sourceKey)
    }

    fun setMode(mode: PlaybackMode) {
        snapshot = snapshot.copy(mode = mode)
    }

    fun clear() {
        snapshot = ActivePlaybackQueueSnapshot()
    }

    fun shouldGenerate(
        sourceSongs: List<Song>,
        requestedSong: Song?,
        source: PlaybackQueueSource,
        sourceKey: String?,
        mode: PlaybackMode,
    ): Boolean = with(snapshot) {
        songs.isEmpty() || this.sourceSongs.isEmpty() || hasManualEdits ||
            this.source != source || this.sourceKey != sourceKey || this.mode != mode ||
            (requestedSong != null && requestedSong.id !in idSet) ||
            this.sourceSongs.map { it.id } != sourceSongs.map { it.id }
    }

    fun removing(songId: Long, currentSongId: Long?): List<Song>? {
        if (songId == currentSongId || songId !in snapshot.idSet) return null
        return snapshot.songs.filterNot { it.id == songId }.takeIf { it.isNotEmpty() }
    }

    fun moving(songId: Long, offset: Int, currentSongId: Long?): List<Song>? {
        if (offset == 0 || currentSongId == null) return null
        val songs = snapshot.songs
        val currentIndex = songs.indexOfFirst { it.id == currentSongId }
        val fromIndex = songs.indexOfFirst { it.id == songId }
        if (currentIndex < 0 || fromIndex <= currentIndex) return null
        val toIndex = (fromIndex + offset).coerceIn(currentIndex + 1, songs.lastIndex)
        if (toIndex == fromIndex) return null
        return songs.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }

    fun keepingCurrent(currentSongId: Long?): List<Song>? {
        if (snapshot.songs.size <= 1) return null
        return snapshot.songs.firstOrNull { it.id == currentSongId }?.let(::listOf)
    }
}
