package com.softmusic.app.player

import androidx.media3.common.Player
import com.softmusic.app.data.Song

/** Applies queue changes while suppressing the player's synchronous intermediate events. */
internal class PlaybackQueueController {
    private var mutationDepth = 0
    val isMutating: Boolean get() = mutationDepth > 0

    fun replacePreservingPlayback(
        player: Player,
        queue: List<Song>,
        currentSongId: Long?,
        mode: PlaybackMode,
        positionMs: Long?,
        shouldPlay: Boolean?,
        forceFullReset: Boolean,
        onReplaced: () -> Unit,
    ): Boolean {
        if (queue.isEmpty()) return false
        val currentIndex = queue.indexOfFirst { it.id == currentSongId }
        if (currentIndex < 0) return false
        val wasPlaying = shouldPlay ?: (player.isPlaying || player.playWhenReady)
        val currentPosition = positionMs ?: player.currentPosition.coerceAtLeast(0)
        mutate {
            val updatedIncrementally = !forceFullReset && syncByBlocks(player, queue, currentSongId)
            if (!updatedIncrementally) {
                player.setMediaItems(queue.toMediaItems(), currentIndex, currentPosition)
                player.prepare()
            }
            player.applyPlaybackMode(mode)
            if (player.playWhenReady != wasPlaying) player.playWhenReady = wasPlaying
            onReplaced()
        }
        return true
    }

    fun playIndex(player: Player, queue: ActivePlaybackQueueSnapshot, index: Int, mode: PlaybackMode): Boolean {
        if (queue.songs.isEmpty()) return false
        val safeIndex = index.coerceIn(0, queue.songs.lastIndex)
        val shouldPlay = player.isPlaying || player.playWhenReady
        mutate {
            if (player.mediaItemIds() == queue.mediaIds) {
                player.seekTo(safeIndex, 0L)
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
            } else {
                player.setMediaItems(queue.songs.toMediaItems(), safeIndex, 0L)
                player.prepare()
            }
            player.applyPlaybackMode(mode)
            if (player.playWhenReady != shouldPlay) player.playWhenReady = shouldPlay
        }
        return true
    }

    private fun syncByBlocks(player: Player, queue: List<Song>, currentSongId: Long?): Boolean {
        val currentId = currentSongId ?: return false
        if (player.currentMediaItem?.mediaId != currentId.toString()) return false
        val currentIds = player.mediaItemIds().map { it.toLongOrNull() ?: return false }
        val desiredIds = queue.map { it.id }
        val patches = planPlaybackQueueUpdate(currentIds, desiredIds, currentId) ?: return false
        if (patches.isEmpty()) return true
        if (!player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)) return false
        val byId = queue.associateBy { it.id }
        // Replace the suffix before the prefix; neither patch includes the playing item.
        for (patch in patches) {
            player.replaceMediaItems(patch.fromIndex, patch.toIndex, patch.songIds.map { byId.getValue(it).toMediaItem() })
        }
        return player.mediaItemIds() == desiredIds.map(Long::toString) &&
            player.currentMediaItem?.mediaId == currentId.toString()
    }

    private inline fun <T> mutate(block: () -> T): T {
        mutationDepth++
        return try {
            block()
        } finally {
            mutationDepth--
        }
    }
}

internal fun Player.applyPlaybackMode(mode: PlaybackMode) {
    shuffleModeEnabled = false
    repeatMode = when (mode) {
        PlaybackMode.Ordered -> Player.REPEAT_MODE_OFF
        PlaybackMode.RepeatList, PlaybackMode.Shuffle -> Player.REPEAT_MODE_ALL
        PlaybackMode.RepeatCurrent -> Player.REPEAT_MODE_ONE
    }
}
