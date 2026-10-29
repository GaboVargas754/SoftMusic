package com.softmusic.app.player

import com.softmusic.app.data.Song
import kotlin.random.Random

/** Each playlist keeps its own shuffle choice independently of the global playback default. */
enum class PlaylistShuffleMode {
    Off,
    Shuffle,
    SmartShuffle;

    fun next(): PlaylistShuffleMode = when (this) {
        Off -> Shuffle
        Shuffle -> SmartShuffle
        SmartShuffle -> Off
    }

    val playbackMode: PlaybackMode
        get() = if (this == Off) PlaybackMode.Ordered else PlaybackMode.Shuffle
}

/** A deliberate mode change only rearranges pending entries, including manually queued extras. */
fun List<Song>.withPlaylistPendingOrder(
    playlistSongs: List<Song>,
    currentSongId: Long?,
    shuffleMode: PlaylistShuffleMode,
    random: Random = Random.Default,
): List<Song> {
    val currentIndex = indexOfFirst { it.id == currentSongId }
    if (currentIndex < 0) return this
    val pending = drop(currentIndex + 1)
    val orderedPending = if (shuffleMode == PlaylistShuffleMode.Off) {
        val playlistPositions = playlistSongs.mapIndexed { index, song -> song.id to index }.toMap()
        pending.sortedBy { playlistPositions[it.id] ?: Int.MAX_VALUE }
    } else {
        pending.shuffled(random)
    }
    return take(currentIndex + 1) + orderedPending
}
