package com.softmusic.app.player

import androidx.media3.common.Player

/** One pending-range replacement avoids seeking or restarting the item currently being decoded. */
internal fun applySmartQueuePendingOrder(player: Player, captured: SmartQueueSnapshot, pendingIds: List<Long>): Boolean {
    if (captured.ids.toSet().size != captured.ids.size || captured.owner !== player || player.currentMediaItem?.mediaId != captured.currentSongId.toString() ||
        player.mediaItemIds() != captured.ids.map(Long::toString)
    ) return false
    val expected = captured.ids.drop(captured.currentIndex + 1)
    if (pendingIds.size != expected.size || pendingIds.toSet() != expected.toSet()) return false
    val firstChanged = expected.indices.firstOrNull { expected[it] != pendingIds[it] } ?: return true
    val lastChanged = expected.indices.last { expected[it] != pendingIds[it] }
    val start = captured.currentIndex + 1 + firstChanged
    val end = captured.currentIndex + 1 + lastChanged + 1
    val byId = (start until end).associate { index ->
        val item = player.getMediaItemAt(index)
        item.mediaId.toLong() to item
    }
    player.replaceMediaItems(start, end, pendingIds.subList(firstChanged, lastChanged + 1).map(byId::getValue))
    return true
}
