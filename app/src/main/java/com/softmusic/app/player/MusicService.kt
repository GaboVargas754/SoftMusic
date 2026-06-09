package com.softmusic.app.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.softmusic.app.MainActivity
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MusicService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var primaryPlayer: ExoPlayer
    private lateinit var secondaryPlayer: ExoPlayer
    private lateinit var activePlayer: ExoPlayer
    private lateinit var standbyPlayer: ExoPlayer
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var djMonitorJob: Job? = null
    private var djTransitionJob: Job? = null
    private var djEnabled = latestDjConfig.enabled
    private var djMixDurationSeconds = latestDjConfig.mixDurationSeconds
    private var playbackMode = latestDjConfig.playbackMode
    private var preloadedForMediaId: String? = null
    private var preloadedTarget: ServiceDjTarget? = null

    private val musicAudioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    @UnstableApi
    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(CompactMediaNotificationProvider(this))
        primaryPlayer = createPlayer(handlesAudioFocus = true)
        secondaryPlayer = createPlayer(handlesAudioFocus = false)
        activePlayer = primaryPlayer
        standbyPlayer = secondaryPlayer
        applyPlaybackModeToPlayers()
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSession.Builder(this, activePlayer)
            .setSessionActivity(sessionActivity)
            .build()
        activeInstance = this
        startDjMonitor()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        shutdownPlayers()
        stopSelf()
    }

    private fun createPlayer(handlesAudioFocus: Boolean): ExoPlayer {
        val player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(musicAudioAttributes, handlesAudioFocus)
            setHandleAudioBecomingNoisy(true)
            volume = 1f
        }
        player.addListener(ServicePlayerListener(player))
        return player
    }

    private fun shutdownPlayers() {
        cancelDjTransition()
        stopPlayer(primaryPlayer)
        stopPlayer(secondaryPlayer)
    }

    private fun stopPlayer(player: ExoPlayer) {
        player.pause()
        player.stop()
        player.clearMediaItems()
    }

    private inner class ServicePlayerListener(private val observedPlayer: Player) : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (::activePlayer.isInitialized && observedPlayer === activePlayer) {
                cancelDjTransition()
            } else if (::standbyPlayer.isInitialized && observedPlayer === standbyPlayer) {
                stopStandbyPlayer()
            }
        }
    }

    private fun applyDjConfig(config: DjServiceConfig) {
        djEnabled = config.enabled
        djMixDurationSeconds = config.mixDurationSeconds.coerceIn(MIN_DJ_MIX_SECONDS, MAX_DJ_MIX_SECONDS)
        playbackMode = config.playbackMode
        applyPlaybackModeToPlayers()
        if (!djEnabled || playbackMode == PlaybackMode.RepeatCurrent) {
            cancelDjTransition()
            stopStandbyPlayer()
        }
    }

    private fun applyPlaybackModeToPlayers() {
        if (::activePlayer.isInitialized) applyPlaybackModeToPlayer(activePlayer)
        if (::standbyPlayer.isInitialized) applyPlaybackModeToPlayer(standbyPlayer)
    }

    private fun applyPlaybackModeToPlayer(player: Player) {
        player.shuffleModeEnabled = false
        player.repeatMode = when (playbackMode) {
            PlaybackMode.Ordered -> Player.REPEAT_MODE_OFF
            PlaybackMode.RepeatList -> Player.REPEAT_MODE_ALL
            PlaybackMode.RepeatCurrent -> Player.REPEAT_MODE_ONE
            PlaybackMode.Shuffle -> Player.REPEAT_MODE_ALL
        }
    }

    private fun startDjMonitor() {
        djMonitorJob?.cancel()
        djMonitorJob = serviceScope.launch {
            while (isActive) {
                maybeStartDjTransition()
                delay(if (djEnabled) DJ_MONITOR_INTERVAL_MS else DJ_MONITOR_IDLE_INTERVAL_MS)
            }
        }
    }

    private fun maybeStartDjTransition() {
        val player = activePlayer
        if (!djEnabled) return
        if (playbackMode == PlaybackMode.RepeatCurrent) return
        if (!player.isPlaying) return
        if (djTransitionJob?.isActive == true) return
        if (player.mediaItemCount <= 1) return

        val currentMediaId = player.currentMediaItem?.mediaId ?: return
        val duration = player.duration.takeIf { it > 0 } ?: return
        val position = player.currentPosition.coerceAtLeast(0)
        val mixDurationMs = djMixDurationSeconds.coerceIn(MIN_DJ_MIX_SECONDS, MAX_DJ_MIX_SECONDS) * 1_000L
        val remainingMs = duration - position
        if (duration <= mixDurationMs + MIN_DJ_SONG_EXTRA_MS) return
        if (remainingMs <= MIN_DJ_FADE_MS + DJ_HANDOFF_SAFETY_MS) return

        val target = nextDjTarget(player) ?: return
        if (remainingMs <= mixDurationMs + DJ_PRELOAD_LEAD_MS) {
            prepareStandbyPlayer(currentMediaId, target)
        }
        if (remainingMs > mixDurationMs) return

        djTransitionJob = serviceScope.launch {
            try {
                runDjTransition(
                    outgoingPlayer = player,
                    incomingPlayer = standbyPlayer,
                    currentMediaId = currentMediaId,
                    target = target,
                    mixDurationMs = mixDurationMs.coerceAtMost(remainingMs - DJ_HANDOFF_SAFETY_MS),
                )
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
            } finally {
                djTransitionJob = null
            }
        }
    }

    private suspend fun runDjTransition(
        outgoingPlayer: ExoPlayer,
        incomingPlayer: ExoPlayer,
        currentMediaId: String,
        target: ServiceDjTarget,
        mixDurationMs: Long,
    ) {
        if (activePlayer !== outgoingPlayer || standbyPlayer !== incomingPlayer) return
        val originalVolume = outgoingPlayer.volume.coerceIn(0f, 1f).takeIf { it > 0f } ?: 1f
        val prepared = prepareStandbyPlayer(currentMediaId, target)
        if (!prepared) return

        incomingPlayer.volume = 0f
        incomingPlayer.play()
        if (!waitForPlayerReady(incomingPlayer, DJ_INCOMING_READY_TIMEOUT_MS)) {
            outgoingPlayer.volume = originalVolume
            stopStandbyPlayer()
            return
        }

        val stepCount = DJ_FADE_STEPS
        val stepDelayMs = (mixDurationMs.coerceAtLeast(MIN_DJ_FADE_MS) / stepCount).coerceAtLeast(50L)
        repeat(stepCount) { step ->
            delay(stepDelayMs)
            if (
                !djEnabled ||
                activePlayer !== outgoingPlayer ||
                standbyPlayer !== incomingPlayer ||
                outgoingPlayer.currentMediaItem?.mediaId != currentMediaId ||
                incomingPlayer.currentMediaItem?.mediaId != target.mediaId ||
                !outgoingPlayer.isPlaying
            ) {
                outgoingPlayer.volume = originalVolume
                stopStandbyPlayer()
                return
            }
            val progress = (step + 1).toFloat() / stepCount.toFloat()
            outgoingPlayer.volume = originalVolume * equalPowerOut(progress)
            incomingPlayer.volume = originalVolume * equalPowerIn(progress)
        }

        incomingPlayer.volume = originalVolume
        outgoingPlayer.volume = 0f
        swapActivePlayer(outgoingPlayer, incomingPlayer)
        outgoingPlayer.pause()
        outgoingPlayer.stop()
        outgoingPlayer.clearMediaItems()
        outgoingPlayer.volume = 0f
        clearPreloadedTarget()
    }

    private fun nextDjTarget(player: ExoPlayer): ServiceDjTarget? {
        val itemCount = player.mediaItemCount
        if (itemCount <= 1) return null
        val currentIndex = player.currentMediaItemIndex.takeIf { it >= 0 } ?: return null
        val mediaItems = List(itemCount) { index -> player.getMediaItemAt(index) }

        if (playbackMode == PlaybackMode.Ordered && currentIndex >= mediaItems.lastIndex) return null
        if (playbackMode == PlaybackMode.Shuffle && currentIndex >= mediaItems.lastIndex) {
            val currentItem = mediaItems.getOrNull(currentIndex) ?: return null
            val shuffledItems = listOf(currentItem) + mediaItems
                .filterNot { it.mediaId == currentItem.mediaId }
                .shuffled()
            val nextItem = shuffledItems.getOrNull(1) ?: return null
            return ServiceDjTarget(
                mediaItems = shuffledItems,
                startIndex = 1,
                mediaId = nextItem.mediaId,
            )
        }

        val nextIndex = when (playbackMode) {
            PlaybackMode.Ordered -> currentIndex + 1
            PlaybackMode.RepeatList,
            PlaybackMode.Shuffle -> (currentIndex + 1) % itemCount
            PlaybackMode.RepeatCurrent -> return null
        }
        val nextItem = mediaItems.getOrNull(nextIndex) ?: return null
        return ServiceDjTarget(
            mediaItems = mediaItems,
            startIndex = nextIndex,
            mediaId = nextItem.mediaId,
        )
    }

    private fun prepareStandbyPlayer(currentMediaId: String, target: ServiceDjTarget): Boolean {
        if (
            preloadedForMediaId == currentMediaId &&
            preloadedTarget == target &&
            standbyPlayer.currentMediaItem?.mediaId == target.mediaId &&
            standbyPlayer.mediaItemCount > 0
        ) {
            standbyPlayer.volume = 0f
            return true
        }

        standbyPlayer.pause()
        standbyPlayer.stop()
        standbyPlayer.clearMediaItems()
        standbyPlayer.setAudioAttributes(musicAudioAttributes, false)
        standbyPlayer.volume = 0f
        standbyPlayer.setMediaItems(target.mediaItems, target.startIndex, 0L)
        applyPlaybackModeToPlayer(standbyPlayer)
        standbyPlayer.prepare()
        preloadedForMediaId = currentMediaId
        preloadedTarget = target
        return true
    }

    private fun swapActivePlayer(outgoingPlayer: ExoPlayer, incomingPlayer: ExoPlayer) {
        incomingPlayer.setAudioAttributes(musicAudioAttributes, true)
        applyPlaybackModeToPlayer(incomingPlayer)
        mediaSession?.setPlayer(incomingPlayer)
        activePlayer = incomingPlayer
        standbyPlayer = outgoingPlayer
        standbyPlayer.setAudioAttributes(musicAudioAttributes, false)
        applyPlaybackModeToPlayer(standbyPlayer)
    }

    private suspend fun waitForPlayerReady(player: Player, timeoutMs: Long): Boolean {
        var elapsedMs = 0L
        while (elapsedMs < timeoutMs) {
            if (player.playbackState == Player.STATE_READY && player.playWhenReady) return true
            delay(DJ_READY_POLL_MS)
            elapsedMs += DJ_READY_POLL_MS
        }
        return player.playbackState == Player.STATE_READY
    }

    private fun cancelDjTransition() {
        djTransitionJob?.cancel()
        djTransitionJob = null
        activePlayer.volume = 1f
        stopStandbyPlayer()
    }

    private fun stopStandbyPlayer() {
        clearPreloadedTarget()
        if (::standbyPlayer.isInitialized) {
            standbyPlayer.pause()
            standbyPlayer.stop()
            standbyPlayer.clearMediaItems()
            standbyPlayer.volume = 0f
        }
    }

    private fun clearPreloadedTarget() {
        preloadedForMediaId = null
        preloadedTarget = null
    }

    private fun equalPowerIn(progress: Float): Float {
        val angle = progress.coerceIn(0f, 1f).toDouble() * PI / 2.0
        return sin(angle).toFloat()
    }

    private fun equalPowerOut(progress: Float): Float {
        val angle = progress.coerceIn(0f, 1f).toDouble() * PI / 2.0
        return cos(angle).toFloat()
    }

    @UnstableApi
    private class CompactMediaNotificationProvider(context: Context) : DefaultMediaNotificationProvider(context) {
        override fun getMediaButtons(
            session: MediaSession,
            playerCommands: Player.Commands,
            customLayout: ImmutableList<CommandButton>,
            showPauseButton: Boolean,
        ): ImmutableList<CommandButton> {
            val buttons = ImmutableList.builder<CommandButton>()

            if (
                playerCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) ||
                playerCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS)
            ) {
                val previousCommand = if (playerCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS)) {
                    Player.COMMAND_SEEK_TO_PREVIOUS
                } else {
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
                }
                buttons.add(
                    compactButton(
                        icon = CommandButton.ICON_PREVIOUS,
                        playerCommand = previousCommand,
                        displayName = "Anterior",
                        compactIndex = 0,
                    ),
                )
            }

            if (playerCommands.contains(Player.COMMAND_PLAY_PAUSE)) {
                buttons.add(
                    compactButton(
                        icon = if (showPauseButton) CommandButton.ICON_PAUSE else CommandButton.ICON_PLAY,
                        playerCommand = Player.COMMAND_PLAY_PAUSE,
                        displayName = if (showPauseButton) "Pausar" else "Reproducir",
                        compactIndex = 1,
                    ),
                )
            }

            if (
                playerCommands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) ||
                playerCommands.contains(Player.COMMAND_SEEK_TO_NEXT)
            ) {
                val nextCommand = if (playerCommands.contains(Player.COMMAND_SEEK_TO_NEXT)) {
                    Player.COMMAND_SEEK_TO_NEXT
                } else {
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
                }
                buttons.add(
                    compactButton(
                        icon = CommandButton.ICON_NEXT,
                        playerCommand = nextCommand,
                        displayName = "Siguiente",
                        compactIndex = 2,
                    ),
                )
            }

            return buttons.build()
        }

        private fun compactButton(
            icon: Int,
            playerCommand: Int,
            displayName: String,
            compactIndex: Int,
        ): CommandButton {
            val extras = Bundle().apply {
                putInt(COMMAND_KEY_COMPACT_VIEW_INDEX, compactIndex)
            }
            return CommandButton.Builder(icon)
                .setPlayerCommand(playerCommand)
                .setDisplayName(displayName)
                .setExtras(extras)
                .build()
        }
    }

    override fun onDestroy() {
        djMonitorJob?.cancel()
        shutdownPlayers()
        mediaSession?.run {
            release()
        }
        primaryPlayer.release()
        secondaryPlayer.release()
        mediaSession = null
        if (activeInstance === this) activeInstance = null
        super.onDestroy()
    }

    private data class ServiceDjTarget(
        val mediaItems: List<MediaItem>,
        val startIndex: Int,
        val mediaId: String,
    )

    private data class DjServiceConfig(
        val enabled: Boolean,
        val mixDurationSeconds: Int,
        val playbackMode: PlaybackMode,
    )

    companion object {
        private var activeInstance: MusicService? = null
        private var latestDjConfig = DjServiceConfig(
            enabled = false,
            mixDurationSeconds = 8,
            playbackMode = PlaybackMode.Ordered,
        )

        fun updateDjConfig(enabled: Boolean, mixDurationSeconds: Int, playbackMode: PlaybackMode) {
            val config = DjServiceConfig(
                enabled = enabled,
                mixDurationSeconds = mixDurationSeconds,
                playbackMode = playbackMode,
            )
            latestDjConfig = config
            activeInstance?.applyDjConfig(config)
        }

        private const val DJ_MONITOR_INTERVAL_MS = 120L
        private const val DJ_MONITOR_IDLE_INTERVAL_MS = 500L
        private const val MIN_DJ_MIX_SECONDS = 5
        private const val MAX_DJ_MIX_SECONDS = 8
        private const val MIN_DJ_SONG_EXTRA_MS = 2_000L
        private const val MIN_DJ_FADE_MS = 1_000L
        private const val DJ_PRELOAD_LEAD_MS = 2_000L
        private const val DJ_HANDOFF_SAFETY_MS = 700L
        private const val DJ_FADE_STEPS = 24
        private const val DJ_INCOMING_READY_TIMEOUT_MS = 1_500L
        private const val DJ_READY_POLL_MS = 50L
    }
}
