package com.softmusic.app.player

import kotlin.math.abs

/** Applies the collection's alternate order without changing its underlying membership. */
fun collectionMixOrder(songIds: List<Long>, config: CollectionMixConfig): List<Long> {
    if (!config.isEnabled || config.orderedSongIds.isEmpty()) return songIds
    val sourceIds = songIds.toSet()
    val ordered = config.orderedSongIds.filter { it in sourceIds }.distinct()
    val placed = ordered.toSet()
    return ordered + songIds.filterNot { it in placed }.distinct()
}

/**
 * Keeps existing Custom/Off pairs intact while grouping reliable tempos.
 * Unknown songs, and complete blocks containing one, retain their exact positions.
 */
fun suggestMixOrder(
    songIds: List<Long>,
    profiles: Map<Long, DjBeatProfile>,
    transitions: Map<String, MixTransitionSettings>,
): List<Long> {
    if (songIds.size < 2) return songIds
    val blocks = mutableListOf<MutableList<Long>>()
    for ((index, id) in songIds.withIndex()) {
        val previous = songIds.getOrNull(index - 1)
        val mode = previous?.let { transitions[mixPairKey(it, id)]?.mode }
        if (mode == MixTransitionMode.Custom || mode == MixTransitionMode.Off) {
            blocks.last().add(id)
        } else {
            blocks += mutableListOf(id)
        }
    }

    val result = ArrayList<Long>(songIds.size)
    val movable = mutableListOf<List<Long>>()
    fun flushMovable() {
        if (movable.isEmpty()) return
        // Keep the original opening song as the seed. Ties retain the original
        // block order, so the same inputs always produce the same suggestion.
        var current = movable.removeAt(0)
        result.addAll(current)
        while (movable.isNotEmpty()) {
            val outgoingBpm = profiles.getValue(current.last()).bpm
            val next = movable.indices.minByOrNull { index ->
                abs(outgoingBpm - profiles.getValue(movable[index].first()).bpm)
            }!!
            current = movable.removeAt(next)
            result.addAll(current)
        }
    }
    for (block in blocks) {
        if (block.all { profiles[it].hasReliableTempo() }) {
            movable += block
        } else {
            flushMovable()
            result.addAll(block)
        }
    }
    flushMovable()
    return result
}

private fun DjBeatProfile?.hasReliableTempo(): Boolean = this != null &&
    bpm.isFinite() && bpm in 40f..240f && confidence.isFinite() && confidence in 0.72f..1f
