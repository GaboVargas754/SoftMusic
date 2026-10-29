package com.softmusic.app.ui

internal data class PlaylistReorderCommit(
    val expectedFullIds: List<Long>,
    val orderedVisibleIds: List<Long>,
)

/** A gesture owns both saved membership and visible order; either changing cancels its commit. */
internal class PlaylistReorderSession(
    private val originalFullIds: List<Long>,
    private val originalVisibleIds: List<Long>,
    val songId: Long,
) {
    private val originalVisibleSet = originalVisibleIds.toSet()
    var orderedIds: List<Long> = originalVisibleIds
        private set

    fun moveTo(index: Int): Boolean {
        val from = orderedIds.indexOf(songId)
        if (from < 0 || index !in orderedIds.indices || from == index) return false
        orderedIds = orderedIds.toMutableList().apply { add(index, removeAt(from)) }
        return true
    }

    fun drop(latestFullIds: List<Long>, latestVisibleIds: List<Long>): PlaylistReorderCommit? {
        if (latestFullIds != originalFullIds || latestVisibleIds != originalVisibleIds ||
            orderedIds == originalVisibleIds || songId !in originalVisibleIds ||
            originalVisibleSet.size != originalVisibleIds.size ||
            originalFullIds.filter { it in originalVisibleSet } != originalVisibleIds
        ) return null
        return PlaylistReorderCommit(originalFullIds, orderedIds)
    }
}

internal data class PlaylistSongSelection(val selectedIds: Set<Long> = emptySet()) {
    fun toggle(songId: Long, availableIds: Collection<Long>): PlaylistSongSelection {
        val retained = selectedIds.intersect(availableIds.toSet())
        return if (songId !in availableIds) copy(selectedIds = retained)
        else copy(selectedIds = if (songId in retained) retained - songId else retained + songId)
    }

    fun retainedFor(availableIds: Collection<Long>): PlaylistSongSelection =
        copy(selectedIds = selectedIds.intersect(availableIds.toSet()))

    fun orderedFor(availableIds: List<Long>): List<Long> = availableIds.distinct().filter { it in selectedIds }
}
