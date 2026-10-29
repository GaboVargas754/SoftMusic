package com.softmusic.app.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

internal data class DjPlaybackTarget(
    val mediaItems: List<MediaItem>,
    val startIndex: Int,
    val mediaId: String,
    val startPositionMs: Long = 0L,
)

/** Owns both players, their audio processors and the prepared standby queue. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class DjDeckController(
    private val context: Context,
    private val attachSessionPlayer: (ExoPlayer) -> Unit,
    private val listener: (ExoPlayer) -> Player.Listener,
) {
    private val musicAudioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()
    private val effects = mutableMapOf<ExoPlayer, DjEffectsAudioProcessor>()
    private val primaryPlayer = createPlayer(handlesAudioFocus = true)
    private val secondaryPlayer = createPlayer(handlesAudioFocus = false)
    private val handoff = DjDeckHandoff(primaryPlayer, secondaryPlayer)
    private var preloadedForMediaId: String? = null
    private var preloadedTarget: DjPlaybackTarget? = null
    private var preloadedQueueRevision: Long? = null
    private var wrapSource: List<MediaItem>? = null
    private var wrapTarget: DjPlaybackTarget? = null

    val activePlayer: ExoPlayer get() = handoff.active
    val standbyPlayer: ExoPlayer get() = handoff.standby

    private fun createPlayer(handlesAudioFocus: Boolean): ExoPlayer {
        val processor = DjEffectsAudioProcessor()
        val player = ExoPlayer.Builder(context, DjRenderersFactory(context, processor)).build().apply {
            setAudioAttributes(musicAudioAttributes, handlesAudioFocus)
            setHandleAudioBecomingNoisy(true)
            volume = 1f
        }
        effects[player] = processor
        player.addListener(listener(player))
        return player
    }

    fun applyPlaybackMode(mode: PlaybackMode) {
        applyPlaybackMode(activePlayer, mode)
        applyPlaybackMode(standbyPlayer, mode)
    }

    private fun applyPlaybackMode(player: Player, mode: PlaybackMode) {
        player.shuffleModeEnabled = false
        player.repeatMode = when (mode) {
            PlaybackMode.Ordered -> Player.REPEAT_MODE_OFF
            PlaybackMode.RepeatList -> Player.REPEAT_MODE_ALL
            PlaybackMode.RepeatCurrent -> Player.REPEAT_MODE_ONE
            PlaybackMode.Shuffle -> Player.REPEAT_MODE_ALL
        }
    }

    fun nextTarget(mode: PlaybackMode): DjPlaybackTarget? {
        val player = activePlayer
        val itemCount = player.mediaItemCount
        if (itemCount <= 1) return null
        val currentIndex = player.currentMediaItemIndex.takeIf { it >= 0 } ?: return null
        val mediaItems = List(itemCount) { index -> player.getMediaItemAt(index) }

        if (mode == PlaybackMode.Ordered && currentIndex >= mediaItems.lastIndex) return null
        if (mode == PlaybackMode.Shuffle && currentIndex >= mediaItems.lastIndex) {
            if (wrapSource == mediaItems) return wrapTarget
            val currentItem = mediaItems.getOrNull(currentIndex) ?: return null
            val shuffledItems = listOf(currentItem) + mediaItems
                .filterNot { it.mediaId == currentItem.mediaId }
                .shuffled()
            val nextItem = shuffledItems.getOrNull(1) ?: return null
            return DjPlaybackTarget(shuffledItems, 1, nextItem.mediaId).also {
                wrapSource = mediaItems
                wrapTarget = it
            }
        }
        wrapSource = null
        wrapTarget = null

        val nextIndex = when (mode) {
            PlaybackMode.Ordered -> currentIndex + 1
            PlaybackMode.RepeatList, PlaybackMode.Shuffle -> (currentIndex + 1) % itemCount
            PlaybackMode.RepeatCurrent -> return null
        }
        val nextItem = mediaItems.getOrNull(nextIndex) ?: return null
        return DjPlaybackTarget(mediaItems, nextIndex, nextItem.mediaId)
    }

    fun prepareStandby(currentMediaId: String, target: DjPlaybackTarget, mode: PlaybackMode, queueRevision: Long): Boolean {
        val standby = standbyPlayer
        if (preloadedForMediaId == currentMediaId && preloadedTarget == target && preloadedQueueRevision == queueRevision &&
            standby.currentMediaItem?.mediaId == target.mediaId && standby.mediaItemCount > 0
        ) {
            standby.volume = 0f
            return true
        }
        stopPlayer(standby)
        standby.setAudioAttributes(musicAudioAttributes, false)
        standby.volume = 0f
        standby.setMediaItems(target.mediaItems, target.startIndex, target.startPositionMs.coerceAtLeast(0L))
        applyPlaybackMode(standby, mode)
        standby.prepare()
        preloadedForMediaId = currentMediaId
        preloadedTarget = target
        preloadedQueueRevision = queueRevision
        return true
    }

    fun swap(outgoing: ExoPlayer, incoming: ExoPlayer, mode: PlaybackMode) {
        handoff.swap(
            outgoing = outgoing,
            incoming = incoming,
            activate = { player ->
                player.setAudioAttributes(musicAudioAttributes, true)
                applyPlaybackMode(player, mode)
            },
            attach = attachSessionPlayer,
            deactivate = { player ->
                player.setAudioAttributes(musicAudioAttributes, false)
                applyPlaybackMode(player, mode)
            },
        )
    }

    fun applyExpertBassSwap(outgoing: ExoPlayer, incoming: ExoPlayer, progress: Float) {
        effects[outgoing]?.setTransition(AutoDjEffect.BassSwap, progress, 0.65f, 120f, incoming = false)
        effects[incoming]?.setTransition(AutoDjEffect.BassSwap, progress, 0.65f, 120f, incoming = true)
    }

    /** Transition attenuation belongs to the player envelope; DSP stays at unity. */
    fun resetTransitionEffects() {
        effects.values.forEach { processor ->
            processor.resetEffect()
            processor.setLevelGain(1f)
        }
    }

    fun resetLevelGains() {
        effects.values.forEach { it.setLevelGain(1f) }
    }

    fun finishTransition(outgoing: ExoPlayer) {
        stopPlayer(outgoing)
        outgoing.volume = 0f
        clearPreloadedTarget()
    }

    fun stopStandby() {
        clearPreloadedTarget()
        val player = standbyPlayer
        effects[player]?.resetEffect()
        if (player.playbackParameters != PlaybackParameters.DEFAULT) player.playbackParameters = PlaybackParameters.DEFAULT
        stopPlayer(player)
        player.volume = 0f
    }

    private fun clearPreloadedTarget() {
        preloadedForMediaId = null
        preloadedTarget = null
        preloadedQueueRevision = null
    }

    fun stopAll() {
        stopPlayer(primaryPlayer)
        stopPlayer(secondaryPlayer)
    }

    private fun stopPlayer(player: ExoPlayer) {
        effects[player]?.resetEffect()
        effects[player]?.setLevelGain(1f)
        player.pause()
        player.stop()
        player.clearMediaItems()
    }

    fun release() {
        try {
            primaryPlayer.release()
        } finally {
            try {
                secondaryPlayer.release()
            } finally {
                effects.clear()
            }
        }
    }
}
