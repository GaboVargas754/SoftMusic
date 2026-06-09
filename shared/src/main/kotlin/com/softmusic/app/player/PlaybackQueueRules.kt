package com.softmusic.app.player

import com.softmusic.app.data.Song

data class PlaybackQueueMove(
    val queue: List<Song>,
    val index: Int,
)

fun List<Song>.distinctSongsById(): List<Song> = distinctBy { it.id }

fun List<Song>.generatePlaybackQueue(
    playbackMode: PlaybackMode,
    requestedSong: Song?,
): List<Song> = when (playbackMode) {
    PlaybackMode.Shuffle -> shuffled().withRequestedFirst(requestedSong)
    PlaybackMode.Ordered,
    PlaybackMode.RepeatList,
    PlaybackMode.RepeatCurrent -> this
}

fun List<Song>.shuffledQueueWithCurrentFirst(currentSong: Song): List<Song> {
    return listOf(currentSong) + filterNot { it.id == currentSong.id }.shuffled()
}

fun List<Song>.insertSongNearCurrent(
    song: Song,
    currentSongId: Long?,
    afterCurrent: Boolean,
): List<Song>? {
    if (currentSongId == null || song.id == currentSongId) return null

    val queueWithoutSong = filterNot { it.id == song.id }
    val currentIndex = queueWithoutSong.indexOfFirst { it.id == currentSongId }
    if (currentIndex < 0) return null

    val insertIndex = if (afterCurrent) currentIndex + 1 else queueWithoutSong.size
    return queueWithoutSong.toMutableList().apply {
        add(insertIndex.coerceIn(0, size), song)
    }
}

fun List<Song>.nextQueueMove(
    sourceSongs: List<Song>,
    currentSong: Song?,
    playbackMode: PlaybackMode,
    manual: Boolean,
): PlaybackQueueMove? {
    if (isEmpty()) return null
    val currentIndex = currentSong?.let { song -> indexOfFirst { it.id == song.id } } ?: -1
    if (currentIndex < 0) return PlaybackQueueMove(queue = this, index = 0)

    return when (playbackMode) {
        PlaybackMode.Ordered -> {
            if (currentIndex >= lastIndex) null else PlaybackQueueMove(queue = this, index = currentIndex + 1)
        }
        PlaybackMode.RepeatCurrent -> {
            val nextIndex = if (manual) (currentIndex + 1) % size else currentIndex
            PlaybackQueueMove(queue = this, index = nextIndex)
        }
        PlaybackMode.RepeatList -> PlaybackQueueMove(queue = this, index = (currentIndex + 1) % size)
        PlaybackMode.Shuffle -> {
            if (currentIndex < lastIndex) {
                PlaybackQueueMove(queue = this, index = currentIndex + 1)
            } else {
                val current = currentSong ?: getOrNull(currentIndex) ?: return null
                val shuffleSource = sourceSongs.ifEmpty { this }.distinctSongsById()
                val nextQueue = shuffleSource.shuffledQueueWithCurrentFirst(current)
                if (nextQueue.size <= 1) {
                    if (manual) null else PlaybackQueueMove(queue = nextQueue, index = 0)
                } else {
                    PlaybackQueueMove(queue = nextQueue, index = 1)
                }
            }
        }
    }
}

fun List<Song>.previousQueueMove(
    currentSong: Song?,
    playbackMode: PlaybackMode,
    currentPositionMs: Long,
    restartThresholdMs: Long = RESTART_PREVIOUS_THRESHOLD_MS,
): PlaybackQueueMove? {
    if (isEmpty()) return null
    val currentIndex = currentSong?.let { song -> indexOfFirst { it.id == song.id } } ?: -1
    if (currentIndex < 0) return PlaybackQueueMove(queue = this, index = 0)
    if (currentPositionMs > restartThresholdMs) return PlaybackQueueMove(queue = this, index = currentIndex)

    val previousIndex = when (playbackMode) {
        PlaybackMode.Ordered -> (currentIndex - 1).coerceAtLeast(0)
        PlaybackMode.Shuffle,
        PlaybackMode.RepeatList,
        PlaybackMode.RepeatCurrent -> (currentIndex - 1 + size) % size
    }
    return PlaybackQueueMove(queue = this, index = previousIndex)
}

private fun List<Song>.withRequestedFirst(requestedSong: Song?): List<Song> {
    requestedSong ?: return this
    if (firstOrNull()?.id == requestedSong.id) return this
    val requested = firstOrNull { it.id == requestedSong.id } ?: return this
    return listOf(requested) + filterNot { it.id == requestedSong.id }
}

private const val RESTART_PREVIOUS_THRESHOLD_MS = 3_000L
