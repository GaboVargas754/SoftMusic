package com.softmusic.app.player

/** Replaces the half-open range from [fromIndex] to [toIndex] in the queue before this patch. */
data class PlaybackQueuePatch(
    val fromIndex: Int,
    val toIndex: Int,
    val songIds: List<Long>,
)

/**
 * Plans at most two replacements while retaining the existing current item.
 * The suffix is replaced first so the prefix's indices remain valid.
 * All IDs must be positive, with no duplicate IDs in the desired queue.
 * Returns null when the current item cannot be identified uniquely or the desired queue is invalid.
 */
fun planPlaybackQueueUpdate(
    currentIds: List<Long>,
    desiredIds: List<Long>,
    currentSongId: Long,
): List<PlaybackQueuePatch>? {
    if (currentIds.isEmpty() || desiredIds.isEmpty() || currentSongId <= 0L) return null
    if (currentIds.any { it <= 0L } || desiredIds.any { it <= 0L }) return null

    val currentIndex = currentIds.indexOf(currentSongId)
    if (currentIndex < 0 || currentIds.lastIndexOf(currentSongId) != currentIndex) return null
    if (desiredIds.toSet().size != desiredIds.size) return null
    val desiredCurrentIndex = desiredIds.indexOf(currentSongId)
    if (desiredCurrentIndex < 0) return null
    if (currentIds == desiredIds) return emptyList()

    val desiredSuffix = desiredIds.drop(desiredCurrentIndex + 1)
    val desiredPrefix = desiredIds.take(desiredCurrentIndex)
    return buildList(capacity = 2) {
        if (currentIds.subList(currentIndex + 1, currentIds.size) != desiredSuffix) {
            add(PlaybackQueuePatch(currentIndex + 1, currentIds.size, desiredSuffix))
        }
        if (currentIds.subList(0, currentIndex) != desiredPrefix) {
            add(PlaybackQueuePatch(0, currentIndex, desiredPrefix))
        }
    }
}
