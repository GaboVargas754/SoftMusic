package com.softmusic.app.ui

internal data class QueueDrop(val songId: Long, val offset: Int)

/** A drag only owns the upcoming IDs captured at its start; playback changes invalidate it. */
internal class QueueReorderSession(
    private val originalIds: List<Long>,
    val songId: Long,
) {
    var orderedIds: List<Long> = originalIds
        private set

    fun moveTo(index: Int): Boolean {
        val fromIndex = orderedIds.indexOf(songId)
        if (fromIndex < 0 || index !in orderedIds.indices || fromIndex == index) return false
        orderedIds = orderedIds.toMutableList().apply { add(index, removeAt(fromIndex)) }
        return true
    }

    fun drop(latestUpcomingIds: List<Long>): QueueDrop? {
        if (latestUpcomingIds != originalIds) return null
        val fromIndex = originalIds.indexOf(songId)
        val toIndex = orderedIds.indexOf(songId)
        if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return null
        return QueueDrop(songId, toIndex - fromIndex)
    }
}
