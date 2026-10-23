package com.softmusic.app.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.softmusic.app.MainActivity
import com.softmusic.app.R
import com.softmusic.app.BuildConfig
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.abs
import kotlin.math.roundToLong

@androidx.annotation.OptIn(UnstableApi::class)
class MusicService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var primaryPlayer: ExoPlayer
    private lateinit var secondaryPlayer: ExoPlayer
    private lateinit var activePlayer: ExoPlayer
    private lateinit var standbyPlayer: ExoPlayer
    private lateinit var silenceAnalyzer: DjSilenceAnalyzer
    private lateinit var beatAnalyzer: DjBeatAnalyzer
    private val deckEffects = mutableMapOf<ExoPlayer, DjEffectsAudioProcessor>()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var djMonitorJob: Job? = null
    private var djTransitionJob: Job? = null
    private var djTransitionOriginalVolume: Float? = null
    private var autoDjJob: Job? = null
    private var autoDjGeneration = 0L
    private var autoDjInternalChange = false
    private var autoDjOriginalVolume: Float? = null
    private var autoDjSkippedMediaId: String? = null
    private var autoDjPair: Pair<String, String>? = null
    private var autoDjRebufferRetryMediaId: String? = null
    private var autoDjPendingReplanMediaId: String? = null
    private var autoDjSyncInvalidated = false
    private var autoDjActiveBufferingSinceMs: Long? = null
    private var autoDjSettings = latestAutoDjSettings
    // Collection Auto DJ is retired; stored configurations cannot enter the runtime.
    private val collectionMixContext: CollectionMixContext? = null
    private var previewJob: Job? = null
    private var previewGeneration = 0L
    private var previewSnapshot: PreviewSnapshot? = null
    private var previewOutgoing: ExoPlayer? = null
    private var previewIncoming: ExoPlayer? = null
    private var djEnabled = latestDjConfig.enabled
    private var djMixMode = latestDjConfig.mixMode
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
        createMediaNotificationChannel()
        setMediaNotificationProvider(CompactMediaNotificationProvider(this))
        primaryPlayer = createPlayer(handlesAudioFocus = true)
        secondaryPlayer = createPlayer(handlesAudioFocus = false)
        activePlayer = primaryPlayer
        standbyPlayer = secondaryPlayer
        silenceAnalyzer = DjSilenceAnalyzer(applicationContext)
        beatAnalyzer = DjBeatAnalyzer(applicationContext)
        applyPlaybackModeToPlayers()
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSession.Builder(this, activePlayer)
            .setId(MEDIA_SESSION_ID)
            .setSessionActivity(sessionActivity)
            .build()
        activeInstance = this
        startDjMonitor()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    private fun createMediaNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (notificationManager.getNotificationChannel(MEDIA_NOTIFICATION_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            MEDIA_NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_media_playback),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_media_playback)
            setShowBadge(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        shutdownPlayers()
        stopSelf()
    }

    private fun createPlayer(handlesAudioFocus: Boolean): ExoPlayer {
        val effects = DjEffectsAudioProcessor()
        val player = ExoPlayer.Builder(this, DjRenderersFactory(this, effects)).build().apply {
            setAudioAttributes(musicAudioAttributes, handlesAudioFocus)
            setHandleAudioBecomingNoisy(true)
            volume = 1f
        }
        deckEffects[player] = effects
        player.addListener(ServicePlayerListener(player))
        return player
    }

    private fun shutdownPlayers() {
        stopPreview(restorePlayback = false)
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
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (previewSnapshot?.player === observedPlayer && playWhenReady) stopPreview(restorePlayback = false)
            if (!playWhenReady && ::activePlayer.isInitialized && observedPlayer === activePlayer &&
                !autoDjInternalChange && previewSnapshot == null &&
                (autoDjJob != null || autoDjPendingReplanMediaId != null || collectionMixContext?.permits(playbackMode) == true)
            ) cancelAutoDj(AutoDjPhase.Paused, reason = "play_when_ready_false:$reason")
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            if (previewSnapshot?.player === observedPlayer) stopPreview(restorePlayback = false)
        }

        override fun onVolumeChanged(volume: Float) {
            val snapshot = previewSnapshot
            if (snapshot?.player === observedPlayer && snapshot.volume != volume) stopPreview(restorePlayback = false)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (::activePlayer.isInitialized && observedPlayer === activePlayer && isPlaying &&
                autoDjActiveBufferingSinceMs != null
            ) {
                traceDj("active_rebuffer_recovered", "continued_incoming_without_seek")
                autoDjActiveBufferingSinceMs = null
            }
            if (::activePlayer.isInitialized && observedPlayer === activePlayer && !autoDjInternalChange &&
                previewSnapshot == null && isPlaying && autoDjPendingReplanMediaId != null
            ) {
                traceDj("rebuffer_ready", "pending=${autoDjPendingReplanMediaId}")
                autoDjPendingReplanMediaId = null
            }
            if (::activePlayer.isInitialized && !autoDjInternalChange && observedPlayer === activePlayer && !isPlaying &&
                previewSnapshot == null && (autoDjJob != null || collectionMixContext?.permits(playbackMode) == true)
            ) {
                if (observedPlayer.playWhenReady && observedPlayer.playbackState == Player.STATE_BUFFERING) {
                    if (autoDjJob != null) {
                        if (_autoDjStatus.value.phase == AutoDjPhase.Mixing && standbyPlayer.isPlaying) {
                            // Keep the already audible incoming deck running through a short stall.
                            autoDjActiveBufferingSinceMs = SystemClock.elapsedRealtime()
                            autoDjSyncInvalidated = true
                            traceDj("active_rebuffer", "bridge_with_incoming")
                        } else handleActiveRebuffer()
                    }
                    return
                }
                cancelAutoDj(if (observedPlayer.playbackState == Player.STATE_ENDED) AutoDjPhase.Idle else AutoDjPhase.Paused,
                    reason = "not_playing:state=${observedPlayer.playbackState}:suppression=${observedPlayer.playbackSuppressionReason}")
            }
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (previewSnapshot?.player === observedPlayer) stopPreview(restorePlayback = false)
            if (::activePlayer.isInitialized && !autoDjInternalChange && observedPlayer === activePlayer &&
                (autoDjJob != null || collectionMixContext?.permits(playbackMode) == true)
            ) {
                if (reason == Player.DISCONTINUITY_REASON_INTERNAL &&
                    oldPosition.mediaItem?.mediaId == newPosition.mediaItem?.mediaId && observedPlayer.playWhenReady
                ) {
                    autoDjSyncInvalidated = true
                    traceDj("position_adjusted", "internal:${oldPosition.positionMs}>${newPosition.positionMs}")
                    return
                }
                cancelAutoDj(reason = "position_discontinuity:$reason:${oldPosition.positionMs}>${newPosition.positionMs}")
            }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (previewSnapshot?.player === observedPlayer && reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                stopPreview(restorePlayback = false)
            }
            if (::activePlayer.isInitialized && !autoDjInternalChange && observedPlayer === activePlayer &&
                (autoDjJob != null || collectionMixContext?.permits(playbackMode) == true) &&
                reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED
            ) {
                cancelAutoDj(reason = "timeline_changed:$reason")
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            traceDj("player_error", "active=${observedPlayer === activePlayer}:code=${error.errorCode}")
            if (previewSnapshot?.player === observedPlayer) stopPreview(restorePlayback = false)
            if (::activePlayer.isInitialized && observedPlayer === activePlayer) {
                cancelDjTransition()
            } else if (::standbyPlayer.isInitialized && observedPlayer === standbyPlayer) {
                if (autoDjJob != null) {
                    val currentId = activePlayer.currentMediaItem?.mediaId
                    cancelAutoDj()
                    autoDjSkippedMediaId = currentId
                    _autoDjStatus.value = AutoDjStatus(
                        currentMediaId = currentId, phase = AutoDjPhase.Fallback,
                        message = "No se pudo preparar la siguiente canción. La reproducción continuará normalmente.",
                    )
                }
                stopStandbyPlayer()
            }
        }
    }

    private fun applyDjConfig(config: DjServiceConfig) {
        if (djEnabled != config.enabled || djMixMode != config.mixMode || playbackMode != config.playbackMode ||
            djMixDurationSeconds != config.mixDurationSeconds
        ) {
            stopPreview(restorePlayback = true)
            if (playbackMode != config.playbackMode || collectionMixContext?.config?.isEnabled != true) cancelDjTransition()
        }
        djEnabled = config.enabled
        djMixMode = config.mixMode
        djMixDurationSeconds = config.mixDurationSeconds.coerceIn(MIN_DJ_MIX_SECONDS, MAX_DJ_MIX_SECONDS)
        playbackMode = config.playbackMode
        applyPlaybackModeToPlayers()
        if ((!djEnabled && collectionMixContext?.permits(playbackMode) != true) || playbackMode == PlaybackMode.RepeatCurrent) {
            cancelDjTransition()
            stopStandbyPlayer()
        }
    }

    private fun handleActiveRebuffer() {
        val mediaId = activePlayer.currentMediaItem?.mediaId
        val retry = CollectionMixRuntimePolicy.canRetryAfterRebuffer(mediaId, autoDjRebufferRetryMediaId)
        traceDj("rebuffer", if (retry) "retry_once_after_ready" else "retry_budget_exhausted")
        cancelAutoDj(reason = "active_buffering", resetRebufferBudget = false)
        autoDjRebufferRetryMediaId = mediaId
        if (retry) {
            autoDjPendingReplanMediaId = mediaId
            _autoDjStatus.value = AutoDjStatus(currentMediaId = mediaId, phase = AutoDjPhase.Ready,
                message = "Esperando audio; se preparará de nuevo la mezcla al continuar.")
        } else {
            autoDjSkippedMediaId = mediaId
            _autoDjStatus.value = AutoDjStatus(currentMediaId = mediaId, phase = AutoDjPhase.Fallback,
                message = "El audio volvió a detenerse; esta canción continuará sin mezcla.")
        }
    }

    private fun traceDj(event: String, reason: String, syncErrorMs: Double? = null) {
        if (!BuildConfig.DEBUG) return
        fun deck(player: ExoPlayer?): String = player?.let {
            "id=${it.currentMediaItem?.mediaId},pos=${it.currentPosition},buffered=${it.bufferedPosition},duration=${it.duration}," +
                "state=${it.playbackState},playing=${it.isPlaying},pwr=${it.playWhenReady},supp=${it.playbackSuppressionReason}"
        } ?: "none"
        Log.d("SoftMusicMix", "event=$event reason=$reason generation=$autoDjGeneration phase=${_autoDjStatus.value.phase} " +
            "mode=$playbackMode syncErrorMs=$syncErrorMs out=[${deck(if (::activePlayer.isInitialized) activePlayer else null)}] " +
            "in=[${deck(if (::standbyPlayer.isInitialized) standbyPlayer else null)}]")
    }

    private fun abortAutoMix(reason: String, syncErrorMs: Double? = null): Boolean {
        traceDj("abort", reason, syncErrorMs)
        return false
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
        if (previewSnapshot != null) return
        if (playbackMode == PlaybackMode.RepeatCurrent) return
        if (!player.isPlaying) return
        if (djTransitionJob?.isActive == true) return
        if (player.mediaItemCount <= 1) return

        // Only the global Classic and Expert modes can start a DJ transition.
        if (!djEnabled || djMixMode == DjMixMode.AutoDj) return

        val currentMediaId = player.currentMediaItem?.mediaId ?: return
        val duration = player.duration.takeIf { it > 0 } ?: return
        val position = player.currentPosition.coerceAtLeast(0)
        val mixDurationMs = djMixDurationSeconds.coerceIn(MIN_DJ_MIX_SECONDS, MAX_DJ_MIX_SECONDS) * 1_000L
        val remainingMs = duration - position
        if (duration <= mixDurationMs + MIN_DJ_SONG_EXTRA_MS) return
        if (remainingMs <= MIN_DJ_FADE_MS + DJ_HANDOFF_SAFETY_MS) return

        val target = nextDjTarget(player) ?: return
        if (djMixMode == DjMixMode.Expert) {
            maybeStartExpertDjTransition(
                player = player,
                currentMediaId = currentMediaId,
                target = target,
                durationMs = duration,
                positionMs = position,
                requestedMixDurationMs = mixDurationMs,
            )
            return
        }

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

    private fun maybeStartAutoDj(player: ExoPlayer) {
        if (autoDjJob?.isActive == true) return
        val currentId = player.currentMediaItem?.mediaId ?: return
        if (autoDjSkippedMediaId == currentId) return
        val durationMs = player.duration.takeIf { it > 0L } ?: return
        val target = nextDjTarget(player) ?: return
        val context = collectionMixContext ?: return
        if (!CollectionTransitionPlanner.permitsPair(context, currentId, target.mediaId, playbackMode)) return
        val transition = context.config.transition(currentId.toLong(), target.mediaId.toLong()).sanitized()
        val nextItem = target.mediaItems.getOrNull(target.startIndex) ?: return
        val currentUri = player.currentMediaItem?.localConfiguration?.uri ?: return
        val nextUri = nextItem.localConfiguration?.uri ?: return
        val settings = context.config.settings.sanitized()
        val effectiveSettings = if (transition.mode == MixTransitionMode.Custom) settings.copy(
            syncEnabled = transition.syncEnabled, effect = transition.effect, intensity = transition.intensity,
        ) else settings
        val generation = ++autoDjGeneration
        autoDjPair = currentId to target.mediaId
        autoDjSyncInvalidated = false
        traceDj("analysis_start", "pair=$currentId>${target.mediaId}:transition=${transition.mode}")
        _autoDjStatus.value = AutoDjStatus(
            currentMediaId = currentId,
            nextMediaId = target.mediaId,
            nextTitle = nextItem.mediaMetadata.title?.toString(),
            phase = AutoDjPhase.Analyzing,
        )
        autoDjJob = serviceScope.launch(start = CoroutineStart.LAZY) {
            try {
                prepareStandbyPlayer(currentId, target)
                val incomingDuration = awaitPreparedDuration(standbyPlayer) ?: run {
                    traceDj("abort", "initial_prepare_timeout")
                    _autoDjStatus.value = _autoDjStatus.value.copy(phase = AutoDjPhase.Fallback,
                        message = "No se pudo verificar la duración de entrada; continuará sin mezcla.")
                    return@launch
                }
                // Work only on this pair. Cancelling a seek/queue change also cancels its decoders.
                val profiles = withTimeoutOrNull(AUTO_DJ_ANALYSIS_TIMEOUT_MS) {
                    val outgoing = beatAnalyzer.analyze(currentUri, durationMs, tail = true)
                    val incoming = beatAnalyzer.analyze(nextUri, incomingDuration, tail = false)
                    outgoing to incoming
                }
                if (!isAutoContext(player, currentId, generation)) return@launch
                val outgoing = profiles?.first
                val incoming = profiles?.second
                val outgoingBpm = outgoing?.takeIf { it.confidence >= AUTO_DJ_MIN_CONFIDENCE }?.bpm
                val incomingBpm = incoming?.takeIf { it.confidence >= AUTO_DJ_MIN_CONFIDENCE }?.bpm
                traceDj("analysis_ready", "outBpm=$outgoingBpm:inBpm=$incomingBpm:timeout=${profiles == null}")
                _autoDjStatus.value = _autoDjStatus.value.copy(currentBpm = outgoingBpm, nextBpm = incomingBpm)
                val audibleEnd = if (transition.mode == MixTransitionMode.Automatic && outgoing != null && incoming != null) {
                    withTimeoutOrNull(5_000L) { silenceAnalyzer.tailProfile(currentUri, durationMs)?.audibleEndMs(durationMs) }
                } else null
                if (!isAutoContext(player, currentId, generation)) return@launch
                val result = CollectionTransitionPlanner.plan(transition, settings, durationMs, incomingDuration,
                    player.currentPosition + AUTO_DJ_PREPARE_LEAD_MS, outgoing, incoming, audibleEnd ?: durationMs)
                if (result == null) {
                    traceDj("abort", "plan_unavailable:transition=${transition.mode}")
                    _autoDjStatus.value = _autoDjStatus.value.copy(phase = AutoDjPhase.Fallback, synchronized = false,
                        message = "Los puntos o la duración no caben en estas canciones; continuará sin mezcla.")
                    return@launch
                }
                traceDj("plan", "trigger=${result.plan.triggerPositionMs}:entry=${result.plan.incomingStartPositionMs}:" +
                    "duration=${result.plan.durationMs}:speed=${result.plan.incomingSpeed}:sync=${result.plan.synchronized}")
                val mixed = runAutoDjTransition(player, currentId, target, result.plan, effectiveSettings, generation,
                    transition.takeIf { it.mode == MixTransitionMode.Custom }, result.message)
                if (!mixed && isAutoContext(player, currentId, generation)) {
                    _autoDjStatus.value = _autoDjStatus.value.copy(phase = AutoDjPhase.Fallback, synchronized = false,
                        message = "No se pudo preparar la entrada a tiempo; continuará sin mezcla.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                traceDj("abort", "unexpected_exception:${error.javaClass.simpleName}")
                if (generation == autoDjGeneration) {
                    autoDjSkippedMediaId = currentId
                    _autoDjStatus.value = _autoDjStatus.value.copy(
                        phase = AutoDjPhase.Fallback, synchronized = false, incomingSpeed = 1f,
                        message = "No se pudo preparar la mezcla. La reproducción continuará normalmente.",
                    )
                }
            } finally {
                // An older cancelled job must never reset a newer transition's decks.
                if (generation == autoDjGeneration) {
                    resetDeckProcessing(activePlayer)
                    autoDjOriginalVolume?.let { activePlayer.volume = it }
                    autoDjOriginalVolume = null
                    stopStandbyPlayer()
                    autoDjJob = null
                    autoDjPair = null
                    autoDjActiveBufferingSinceMs = null
                    if (activePlayer.currentMediaItem?.mediaId != currentId) {
                        _autoDjStatus.value = AutoDjStatus(
                            currentMediaId = activePlayer.currentMediaItem?.mediaId,
                            currentBpm = if (_autoDjStatus.value.currentMediaId == activePlayer.currentMediaItem?.mediaId) {
                                _autoDjStatus.value.currentBpm
                            } else _autoDjStatus.value.nextBpm,
                        )
                    } else {
                        // Do not decode/retry continuously when the outro is already too short.
                        autoDjSkippedMediaId = currentId
                    }
                }
            }
        }
        autoDjJob?.start()
    }

    private fun isAutoContext(player: ExoPlayer, currentId: String, generation: Long): Boolean =
        generation == autoDjGeneration && collectionMixContext?.permits(playbackMode) == true &&
            activePlayer === player && previewSnapshot == null &&
            (player.isPlaying || (autoDjActiveBufferingSinceMs != null && player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)) &&
            player.currentMediaItem?.mediaId == currentId && nextDjTarget(player)?.let { target ->
                CollectionTransitionPlanner.permitsPair(collectionMixContext, currentId, target.mediaId, playbackMode)
            } == true

    private suspend fun waitForAutoPosition(player: ExoPlayer, currentId: String, positionMs: Long, generation: Long): Boolean {
        while (isAutoContext(player, currentId, generation)) {
            val remaining = positionMs - player.currentPosition
            if (remaining <= 0L) return true
            delay(remaining.coerceIn(10L, DJ_MONITOR_INTERVAL_MS))
        }
        return false
    }

    private suspend fun runAutoDjTransition(
        outgoing: ExoPlayer,
        currentId: String,
        target: ServiceDjTarget,
        plan: AutoDjPlan,
        settings: AutoDjSettings,
        generation: Long,
        custom: MixTransitionSettings? = null,
        message: String? = null,
    ): Boolean {
        _autoDjStatus.value = _autoDjStatus.value.copy(
            phase = AutoDjPhase.Ready, incomingSpeed = plan.incomingSpeed,
            synchronized = plan.synchronized, message = message,
        )
        if (!waitForAutoPosition(outgoing, currentId, plan.triggerPositionMs - AUTO_DJ_PREPARE_LEAD_MS, generation)) {
            return abortAutoMix("context_before_prepare")
        }
        val incoming = standbyPlayer
        resetDeckProcessing(incoming)
        // Seed controls before PCM is queued; echo history can fill during the muted preroll.
        deckEffects[outgoing]?.setTransition(settings.effect, 0f, settings.intensity, plan.outgoingBpm, incoming = false)
        deckEffects[incoming]?.setTransition(settings.effect, 0f, settings.intensity, plan.outgoingBpm, incoming = true)
        deckEffects[outgoing]?.setDeckEq(MixEq())
        deckEffects[incoming]?.setDeckEq(custom?.incomingEq ?: MixEq())
        val prerollMs = minOf(AUTO_DJ_PREROLL_MS, (plan.incomingStartPositionMs / plan.incomingSpeed).toLong())
        val mutedStart = (plan.incomingStartPositionMs - prerollMs * plan.incomingSpeed).roundToLong().coerceAtLeast(0L)
        val preparedTarget = target.copy(startPositionMs = mutedStart)
        traceDj("prepare", "start=$mutedStart:preroll=$prerollMs")
        prepareStandbyPlayer(currentId, preparedTarget)
        incoming.playbackParameters = PlaybackParameters(plan.incomingSpeed, 1f)
        // Recheck against decoder durations, rather than trusting stale library metadata.
        val actualIncomingDuration = awaitPreparedDuration(incoming) ?: return abortAutoMix("prepare_timeout")
        if (plan.incomingStartPositionMs + plan.durationMs * plan.incomingSpeed.toDouble() > actualIncomingDuration ||
            plan.triggerPositionMs + plan.durationMs > outgoing.duration) return abortAutoMix("changed_media_duration")
        if (!waitForAutoPosition(outgoing, currentId, plan.triggerPositionMs - prerollMs, generation)) {
            return abortAutoMix("context_before_preroll")
        }
        // A slow decoder can enter late while the planned overlap still has enough audio left.
        if (remainingStartBudget(outgoing, plan) <= 0L) return abortAutoMix("preroll_too_late")
        traceDj("preroll", "start")
        incoming.play()
        if (!awaitIncomingPlaying(outgoing, incoming, currentId, generation, remainingStartBudget(outgoing, plan))) {
            return abortAutoMix("preroll_not_playing")
        }
        var corrections = 0
        while (isAutoContext(outgoing, currentId, generation) && outgoing.currentPosition < plan.triggerPositionMs) {
            val remaining = plan.triggerPositionMs - outgoing.currentPosition
            val expectedPosition = plan.incomingStartPositionMs - remaining * plan.incomingSpeed
            val error = expectedPosition - incoming.currentPosition
            if (abs(error) > AUTO_DJ_ALIGNMENT_TOLERANCE_MS && incoming.isPlaying && remaining > 600L && corrections < 1) {
                // Any phase correction happens while the incoming deck is completely inaudible.
                traceDj("alignment_correction", "muted:remaining=$remaining", error.toDouble())
                incoming.seekTo(expectedPosition.roundToLong().coerceAtLeast(0L))
                corrections++
                delay(120L)
            } else {
                delay(remaining.coerceIn(10L, 25L))
            }
        }
        if (!isAutoContext(outgoing, currentId, generation)) return abortAutoMix("context_at_trigger")
        if (!awaitIncomingPlaying(outgoing, incoming, currentId, generation, remainingStartBudget(outgoing, plan))) {
            return abortAutoMix("trigger_not_playing")
        }
        // Do not expose audio before a custom cue merely because decoder startup ran late.
        val reachedCue = withTimeoutOrNull(remainingStartBudget(outgoing, plan).coerceAtLeast(1L)) {
            while (incoming.currentPosition < plan.incomingStartPositionMs) {
                if (!isAutoContext(outgoing, currentId, generation) || !incoming.playWhenReady ||
                    incoming.playerError != null || remainingStartBudget(outgoing, plan) <= 0L
                ) return@withTimeoutOrNull false
                delay(10L)
            }
            true
        } ?: false
        if (!reachedCue) return abortAutoMix("incoming_cue_unavailable")
        val decision = CollectionMixRuntimePolicy.evaluateStart(plan, outgoing.currentPosition, incoming.currentPosition,
            outgoing.duration, incoming.duration, outgoing.isPlaying && incoming.isPlaying)
        if (!decision.canMix) return abortAutoMix(decision.reason, decision.syncErrorMs)

        val volume = outgoing.volume.coerceIn(0f, 1f)
        autoDjOriginalVolume = volume
        deckEffects[outgoing]?.setDeckEq(custom?.outgoingEq ?: MixEq())
        var syncStable = decision.synchronized && !autoDjSyncInvalidated
        val runtimeMessage = if (plan.synchronized && !syncStable) {
            "La entrada tuvo retraso; se mezclará sin sincronización."
        } else message
        traceDj("mix_start", "${decision.reason}:sync=$syncStable", decision.syncErrorMs)
        var incomingRecoveryUsed = false
        var lastStatusUpdateMs = 0L
        while (isAutoContext(outgoing, currentId, generation)) {
            val bufferingSince = autoDjActiveBufferingSinceMs
            if (bufferingSince != null && SystemClock.elapsedRealtime() - bufferingSince > AUTO_DJ_REBUFFER_GRACE_MS) {
                handleActiveRebuffer()
                return abortAutoMix("active_rebuffer_grace_exhausted")
            }
            if (incoming.currentMediaItem?.mediaId != target.mediaId) return abortAutoMix("incoming_media_changed")
            if (!incoming.isPlaying) {
                if (!incomingRecoveryUsed && incoming.playWhenReady && incoming.playbackState == Player.STATE_BUFFERING) {
                    incomingRecoveryUsed = true
                    syncStable = false
                    traceDj("incoming_rebuffer", "one_grace_period")
                    if (!awaitIncomingPlaying(outgoing, incoming, currentId, generation, AUTO_DJ_REBUFFER_GRACE_MS)) {
                        return abortAutoMix("incoming_rebuffer_timeout")
                    }
                } else return abortAutoMix("incoming_not_playing")
            }
            val elapsed = outgoing.currentPosition - plan.triggerPositionMs
            val progress = if (elapsed >= plan.durationMs - 40L) 1f else (elapsed.toFloat() / plan.durationMs).coerceIn(0f, 1f)
            val expected = plan.incomingStartPositionMs + (outgoing.currentPosition - plan.triggerPositionMs) * plan.incomingSpeed
            val syncError = expected - incoming.currentPosition
            if (syncStable && (autoDjSyncInvalidated || abs(syncError) > AUTO_DJ_DRIFT_TOLERANCE_MS)) {
                syncStable = false
                traceDj("sync_degraded", "runtime_drift", syncError.toDouble())
            }
            val curve = DjTransitionCurve.ExpertNormal
            val headroom = curve.headroomAt(progress)
            val gainEntry = ((outgoing.currentPosition - plan.triggerPositionMs) / 150f).coerceIn(0f, 1f)
            val outgoingScale = 1f + ((custom?.outgoingVolume ?: 1f) - 1f) * gainEntry
            outgoing.volume = volume * outgoingScale * equalPowerOut(curve.curvedOutgoingProgress(progress)) * headroom
            incoming.volume = volume * (custom?.incomingVolume ?: 1f) * equalPowerIn(curve.curvedIncomingProgress(progress)) * headroom
            deckEffects[outgoing]?.setTransition(settings.effect, progress, settings.intensity, plan.outgoingBpm, incoming = false)
            deckEffects[incoming]?.setTransition(settings.effect, progress, settings.intensity, plan.outgoingBpm, incoming = true)
            val nowMs = SystemClock.elapsedRealtime()
            if (nowMs - lastStatusUpdateMs >= 100L || progress >= 1f) {
                _autoDjStatus.value = _autoDjStatus.value.copy(
                    phase = AutoDjPhase.Mixing, progress = progress, synchronized = syncStable,
                    message = if (plan.synchronized && !syncStable) runtimeMessage ?: "Mezcla en curso sin sincronización." else runtimeMessage,
                )
                lastStatusUpdateMs = nowMs
            }
            if (progress >= 1f) break
            delay(AUTO_DJ_ENVELOPE_INTERVAL_MS)
        }
        if (!isAutoContext(outgoing, currentId, generation)) return abortAutoMix("context_before_handoff")
        traceDj("handoff", "complete")
        incoming.volume = volume * (custom?.incomingVolume ?: 1f)
        outgoing.volume = 0f
        swapActivePlayer(outgoing, incoming)
        stopStandbyPlayer()
        deckEffects[incoming]?.resetEffect()
        restoreIncomingVolume(incoming, volume, custom?.incomingVolume ?: 1f, generation)
        restoreAutoDjTempo(incoming, target.mediaId, plan, generation)
        return true
    }

    private fun remainingStartBudget(outgoing: ExoPlayer, plan: AutoDjPlan): Long =
        minOf(AUTO_DJ_STARTUP_TIMEOUT_MS, plan.triggerPositionMs + plan.durationMs - outgoing.currentPosition -
            CollectionMixRuntimePolicy.minimumOverlapMs(plan.durationMs))

    private fun sessionPlaybackMode(): PlaybackMode? =
        if (::activePlayer.isInitialized && activePlayer.mediaItemCount > 0) playbackMode else null

    private suspend fun awaitIncomingPlaying(
        outgoing: ExoPlayer,
        incoming: ExoPlayer,
        currentId: String,
        generation: Long,
        timeoutMs: Long,
    ): Boolean {
        if (timeoutMs <= 0L) return false
        return withTimeoutOrNull(timeoutMs) {
            while (!incoming.isPlaying) {
                if (!isAutoContext(outgoing, currentId, generation) || !incoming.playWhenReady || incoming.playerError != null) {
                    return@withTimeoutOrNull false
                }
                delay(10L)
            }
            true
        } ?: false
    }

    private suspend fun restoreIncomingVolume(player: ExoPlayer, baseVolume: Float, fromScale: Float, generation: Long) {
        if (fromScale == 1f) return
        for (step in 1..20) {
            if (generation != autoDjGeneration || activePlayer !== player) return
            val p = step / 20f
            val smooth = p * p * (3f - 2f * p)
            player.volume = baseVolume * (fromScale + (1f - fromScale) * smooth)
            delay(20L)
        }
        player.volume = baseVolume
    }

    private suspend fun restoreAutoDjTempo(player: ExoPlayer, mediaId: String, plan: AutoDjPlan, generation: Long) {
        if (abs(plan.incomingSpeed - 1f) < 0.0001f) return
        val durationMs = (4 * 60_000f / plan.outgoingBpm).roundToLong().coerceIn(1_500L, 4_000L)
        val startedAt = SystemClock.elapsedRealtime()
        val nativeBpm = _autoDjStatus.value.nextBpm
        while (generation == autoDjGeneration && activePlayer === player && player.isPlaying && player.currentMediaItem?.mediaId == mediaId) {
            val progress = ((SystemClock.elapsedRealtime() - startedAt).toFloat() / durationMs).coerceIn(0f, 1f)
            val eased = progress * progress * (3f - 2f * progress)
            val speed = plan.incomingSpeed + (1f - plan.incomingSpeed) * eased
            player.playbackParameters = PlaybackParameters(speed, 1f)
            _autoDjStatus.value = _autoDjStatus.value.copy(
                currentMediaId = mediaId, currentBpm = nativeBpm,
                nextMediaId = null, nextTitle = null, nextBpm = null,
                phase = AutoDjPhase.Restoring, progress = progress, incomingSpeed = speed,
                synchronized = false, message = "Volviendo suavemente al tempo original.",
            )
            if (progress >= 1f) break
            delay(100L)
        }
        player.playbackParameters = PlaybackParameters.DEFAULT
    }

    private fun resetDeckProcessing(player: ExoPlayer) {
        deckEffects[player]?.resetEffect()
        if (player.playbackParameters != PlaybackParameters.DEFAULT) player.playbackParameters = PlaybackParameters.DEFAULT
    }

    private suspend fun awaitPreparedDuration(player: ExoPlayer): Long? = withTimeoutOrNull(5_000L) {
        while (player.playbackState != Player.STATE_READY) {
            if (player.playerError != null || player.playbackState == Player.STATE_ENDED) return@withTimeoutOrNull null
            delay(20L)
        }
        player.duration.takeIf { it > 0L }
    }

    private fun createPreviewPlayer(handlesAudioFocus: Boolean): ExoPlayer {
        val effects = DjEffectsAudioProcessor()
        return ExoPlayer.Builder(this, DjRenderersFactory(this, effects)).build().also { player ->
            player.setAudioAttributes(musicAudioAttributes, handlesAudioFocus)
            player.setHandleAudioBecomingNoisy(true)
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.volume = 0f
            deckEffects[player] = effects
            player.addListener(object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (!playWhenReady && (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY ||
                            reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) &&
                        (player === previewOutgoing || player === previewIncoming)
                    ) stopPreview(false, "Previa detenida por una interrupción de audio.")
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (player === previewOutgoing || player === previewIncoming) {
                        stopPreview(true, "No se pudo reproducir la previa.")
                    }
                }
            })
        }
    }

    private fun startPreview(
        context: CollectionMixContext,
        outgoingItem: MediaItem,
        incomingItem: MediaItem,
        draft: MixTransitionSettings,
    ): Boolean {
        val outgoingId = outgoingItem.mediaId.toLongOrNull()
        val incomingId = incomingItem.mediaId.toLongOrNull()
        val safe = draft.sanitized()
        if (outgoingId == null || incomingId == null ||
            !context.permitsPair(outgoingId, incomingId, playbackMode) ||
            outgoingItem.localConfiguration?.uri == null || incomingItem.localConfiguration?.uri == null ||
            safe.mode == MixTransitionMode.Off
        ) {
            stopPreview(true, if (safe.mode == MixTransitionMode.Off) "Esta pareja tiene la mezcla desactivada."
                else "La previa requiere una colección con mezcla aceptada y ambas canciones dentro de ella.")
            return false
        }
        stopPreview(restorePlayback = true)
        cancelDjTransition()
        val main = activePlayer
        val wasPlaying = main.playWhenReady
        // The session and its complete queue stay on this same player throughout the preview.
        main.pause()
        val snapshot = PreviewSnapshot(main, List(main.mediaItemCount) { main.getMediaItemAt(it).mediaId },
            main.currentMediaItem?.mediaId, main.currentMediaItemIndex, main.currentPosition,
            wasPlaying, main.volume, main.playbackParameters)
        previewSnapshot = snapshot
        val generation = ++previewGeneration
        _mixPreviewState.value = MixPreviewState(true, outgoingId, incomingId, "Preparando previa…")
        previewJob = serviceScope.launch(start = CoroutineStart.LAZY) {
            try {
                val outgoing = createPreviewPlayer(handlesAudioFocus = true).also { previewOutgoing = it }
                val incoming = createPreviewPlayer(handlesAudioFocus = false).also { previewIncoming = it }
                outgoing.setMediaItem(outgoingItem)
                incoming.setMediaItem(incomingItem)
                outgoing.prepare()
                incoming.prepare()
                val outgoingDuration = awaitPreparedDuration(outgoing)
                val incomingDuration = awaitPreparedDuration(incoming)
                if (outgoingDuration == null || incomingDuration == null) {
                    stopPreview(true, "No se pudo verificar la duración de las canciones.")
                    return@launch
                }
                val profiles = withTimeoutOrNull(AUTO_DJ_ANALYSIS_TIMEOUT_MS) {
                    val out = beatAnalyzer.analyze(outgoingItem.localConfiguration!!.uri, outgoingDuration, tail = true)
                    val next = beatAnalyzer.analyze(incomingItem.localConfiguration!!.uri, incomingDuration, tail = false)
                    out to next
                }
                if (previewGeneration != generation || previewSnapshot !== snapshot) return@launch
                val result = CollectionTransitionPlanner.plan(safe, context.config.settings,
                    outgoingDuration, incomingDuration, 0L, profiles?.first, profiles?.second)
                if (result == null) {
                    stopPreview(true, "Los puntos o la duración no caben en estas canciones.")
                    return@launch
                }
                val plan = result.plan
                val settings = if (safe.mode == MixTransitionMode.Custom) safe else safe.copy(
                    syncEnabled = context.config.settings.syncEnabled, effect = context.config.settings.effect,
                    intensity = context.config.settings.intensity, outgoingVolume = 1f, incomingVolume = 1f,
                    outgoingEq = MixEq(), incomingEq = MixEq())
                val baseVolume = snapshot.volume.coerceIn(0f, 1f)
                val outFx = deckEffects.getValue(outgoing)
                val inFx = deckEffects.getValue(incoming)
                outFx.setTransition(settings.effect, 0f, settings.intensity, plan.outgoingBpm, incoming = false)
                inFx.setTransition(settings.effect, 0f, settings.intensity, plan.outgoingBpm, incoming = true)
                outFx.setDeckEq(settings.outgoingEq)
                inFx.setDeckEq(settings.incomingEq)
                incoming.playbackParameters = PlaybackParameters(plan.incomingSpeed, 1f)
                outgoing.seekTo(plan.triggerPositionMs)
                incoming.seekTo(plan.incomingStartPositionMs)
                if (awaitPreparedDuration(outgoing) == null || awaitPreparedDuration(incoming) == null) {
                    stopPreview(true, "No se pudo preparar el punto de entrada.")
                    return@launch
                }
                _mixPreviewState.value = MixPreviewState(true, outgoingId, incomingId,
                    result.message ?: "Comprobando la entrada de ambos reproductores…")
                outgoing.volume = baseVolume
                outgoing.play()
                // A focus denial may synchronously stop the preview and release both decks.
                if (previewGeneration != generation || previewSnapshot !== snapshot) return@launch
                incoming.play()
                if (previewGeneration != generation || previewSnapshot !== snapshot) return@launch
                val started = SystemClock.elapsedRealtime()
                var stableSync = plan.synchronized
                var entered = false
                while (previewGeneration == generation && previewSnapshot === snapshot) {
                    if (outgoing.playerError != null || incoming.playerError != null) error("Preview decoder failed")
                    val elapsed = outgoing.currentPosition - plan.triggerPositionMs
                    val progress = if (elapsed >= plan.durationMs - 40L) 1f else (elapsed.toFloat() / plan.durationMs).coerceIn(0f, 1f)
                    if (outgoing.isPlaying && incoming.isPlaying) {
                        val expected = plan.incomingStartPositionMs + elapsed * plan.incomingSpeed
                        if (abs(expected - incoming.currentPosition) > AUTO_DJ_ALIGNMENT_TOLERANCE_MS) stableSync = false
                        if (!entered || !stableSync) {
                            _mixPreviewState.value = MixPreviewState(true, outgoingId, incomingId,
                                result.message ?: if (stableSync) "Previa sincronizada" else "Previa sin sincronización; la entrada perdió precisión.")
                            entered = true
                        }
                    }
                    val curve = DjTransitionCurve.ExpertNormal
                    val headroom = curve.headroomAt(progress)
                    val gainEntry = (elapsed / 150f).coerceIn(0f, 1f)
                    val outgoingScale = 1f + (settings.outgoingVolume - 1f) * gainEntry
                    outgoing.volume = baseVolume * outgoingScale * equalPowerOut(curve.curvedOutgoingProgress(progress)) * headroom
                    incoming.volume = baseVolume * settings.incomingVolume * equalPowerIn(curve.curvedIncomingProgress(progress)) * headroom
                    outFx.setTransition(settings.effect, progress, settings.intensity, plan.outgoingBpm, incoming = false)
                    inFx.setTransition(settings.effect, progress, settings.intensity, plan.outgoingBpm, incoming = true)
                    if (progress >= 1f) {
                        outgoing.pause()
                        incoming.volume = baseVolume * settings.incomingVolume
                        delay(400L)
                        break
                    }
                    if (outgoing.playbackState == Player.STATE_ENDED || incoming.playbackState == Player.STATE_ENDED ||
                        SystemClock.elapsedRealtime() - started > plan.durationMs + 8_000L) error("Preview stalled")
                    delay(AUTO_DJ_ENVELOPE_INTERVAL_MS)
                }
                if (previewGeneration == generation) stopPreview(restorePlayback = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (previewGeneration == generation) stopPreview(true, "La previa se interrumpió.")
            }
        }
        previewJob?.start()
        return true
    }

    private fun stopPreview(restorePlayback: Boolean, message: String? = null) {
        previewGeneration++
        previewJob?.cancel()
        previewJob = null
        val snapshot = previewSnapshot
        previewSnapshot = null
        val players = listOfNotNull(previewOutgoing, previewIncoming)
        previewOutgoing = null
        previewIncoming = null
        players.forEach { player ->
            deckEffects.remove(player)?.resetEffect()
            player.release()
        }
        if (restorePlayback && snapshot != null && activePlayer === snapshot.player &&
            !activePlayer.playWhenReady && activePlayer.currentMediaItemIndex == snapshot.index &&
            activePlayer.currentMediaItem?.mediaId == snapshot.mediaId &&
            abs(activePlayer.currentPosition - snapshot.positionMs) <= 100L &&
            activePlayer.volume == snapshot.volume && activePlayer.playbackParameters == snapshot.parameters &&
            List(activePlayer.mediaItemCount) { activePlayer.getMediaItemAt(it).mediaId } == snapshot.queueIds
        ) {
            // No seek/queue rewrite is needed: the session player was paused in place.
            activePlayer.volume = snapshot.volume
            activePlayer.playbackParameters = snapshot.parameters
            if (snapshot.playWhenReady) activePlayer.play()
        }
        _mixPreviewState.value = MixPreviewState(message = message)
    }

    private fun cancelAutoDj(
        phase: AutoDjPhase = AutoDjPhase.Idle,
        reason: String = "cancelled",
        resetRebufferBudget: Boolean = true,
    ) {
        val hadJob = autoDjJob != null
        if (hadJob || autoDjPendingReplanMediaId != null) traceDj("cancel", reason)
        autoDjGeneration++
        autoDjJob?.cancel()
        autoDjJob = null
        autoDjSkippedMediaId = null
        autoDjPair = null
        autoDjSyncInvalidated = false
        autoDjActiveBufferingSinceMs = null
        if (resetRebufferBudget) {
            autoDjRebufferRetryMediaId = null
            autoDjPendingReplanMediaId = null
        }
        if (hadJob) {
            if (::activePlayer.isInitialized) {
                resetDeckProcessing(activePlayer)
                autoDjOriginalVolume?.let { activePlayer.volume = it }
            }
            stopStandbyPlayer()
            djTransitionOriginalVolume = null
        }
        autoDjOriginalVolume = null
        _autoDjStatus.value = if (phase == AutoDjPhase.Paused) {
            _autoDjStatus.value.copy(phase = phase, progress = 0f, incomingSpeed = 1f, synchronized = false, message = null)
        } else AutoDjStatus(currentMediaId = if (::activePlayer.isInitialized) activePlayer.currentMediaItem?.mediaId else null)
    }

    private fun maybeStartExpertDjTransition(
        player: ExoPlayer,
        currentMediaId: String,
        target: ServiceDjTarget,
        durationMs: Long,
        positionMs: Long,
        requestedMixDurationMs: Long,
    ) {
        if (durationMs - positionMs > DJ_EXPERT_ANALYSIS_LEAD_MS) return

        djTransitionJob = serviceScope.launch {
            try {
                val plan = buildExpertDjPlan(
                    player = player,
                    currentMediaId = currentMediaId,
                    target = target,
                    durationMs = durationMs,
                    requestedMixDurationMs = requestedMixDurationMs,
                ) ?: classicDjPlan(target, durationMs, requestedMixDurationMs)

                while (isActive) {
                    if (!isSameExpertTransitionContext(player, currentMediaId)) return@launch
                    val currentPosition = player.currentPosition.coerceAtLeast(0)
                    val remainingToTrigger = plan.triggerPositionMs - currentPosition
                    if (remainingToTrigger <= DJ_EXPERT_TRIGGER_TOLERANCE_MS) break
                    if (currentPosition >= plan.preloadPositionMs) {
                        prepareStandbyPlayer(currentMediaId, plan.target)
                    }
                    delay(remainingToTrigger.coerceIn(DJ_MONITOR_INTERVAL_MS, DJ_EXPERT_WAIT_MAX_MS))
                }

                if (!isSameExpertTransitionContext(player, currentMediaId)) return@launch
                val remainingMs = durationMs - player.currentPosition.coerceAtLeast(0)
                if (remainingMs <= MIN_DJ_FADE_MS + DJ_HANDOFF_SAFETY_MS) return@launch
                runDjTransition(
                    outgoingPlayer = player,
                    incomingPlayer = standbyPlayer,
                    currentMediaId = currentMediaId,
                    target = plan.target,
                    mixDurationMs = plan.mixDurationMs.coerceAtMost(remainingMs - DJ_HANDOFF_SAFETY_MS),
                    incomingStartVolumeScale = plan.incomingStartVolumeScale,
                    transitionCurve = plan.transitionCurve,
                )
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
            } finally {
                djTransitionJob = null
            }
        }
    }

    private suspend fun buildExpertDjPlan(
        player: ExoPlayer,
        currentMediaId: String,
        target: ServiceDjTarget,
        durationMs: Long,
        requestedMixDurationMs: Long,
    ): ExpertDjPlan? {
        val currentItem = player.currentMediaItem?.takeIf { it.mediaId == currentMediaId } ?: return null
        val nextItem = target.mediaItems.getOrNull(target.startIndex) ?: return null
        val currentUri = currentItem.localConfiguration?.uri ?: return null
        val nextUri = nextItem.localConfiguration?.uri ?: return null
        val currentProfile = silenceAnalyzer.tailProfile(currentUri, durationMs) ?: return null
        val nextProfile = silenceAnalyzer.headProfile(nextUri)
        val audibleEndMs = currentProfile.audibleEndMs(durationMs) ?: return null
        val nextStartMs = nextStartPositionMs(nextProfile)
        val trailingSilenceMs = (durationMs - audibleEndMs).coerceAtLeast(0L)
        val hasMeaningfulTailSilence = trailingSilenceMs >= DJ_EXPERT_MIN_TRAILING_SILENCE_MS
        val maxMixDurationMs = requestedMixDurationMs.coerceAtMost(audibleEndMs.coerceAtLeast(MIN_DJ_FADE_MS))
        val triggerPositionMs: Long
        val mixDurationMs: Long

        if (hasMeaningfulTailSilence) {
            val silenceMixDurationMs = maxMixDurationMs
                .coerceAtMost(DJ_EXPERT_SILENCE_HANDOFF_FADE_MS)
                .coerceAtLeast(MIN_DJ_FADE_MS)
            triggerPositionMs = (audibleEndMs - silenceMixDurationMs).coerceIn(0L, durationMs)
            mixDurationMs = (audibleEndMs - triggerPositionMs)
                .coerceIn(MIN_DJ_FADE_MS, silenceMixDurationMs)
        } else {
            val minNormalMixDurationMs = minOf(DJ_EXPERT_MIN_NORMAL_BLEND_MS, maxMixDurationMs)
                .coerceAtLeast(MIN_DJ_FADE_MS)
            val earliestTriggerMs = (audibleEndMs - maxMixDurationMs).coerceAtLeast(0L)
            val latestTriggerMs = (audibleEndMs - minNormalMixDurationMs).coerceAtLeast(earliestTriggerMs)
            val searchEndMs = minOf(latestTriggerMs, earliestTriggerMs + DJ_EXPERT_TRIGGER_SEARCH_MS)
            triggerPositionMs = currentProfile.bestLowEnergyTrigger(
                startMs = earliestTriggerMs,
                endMs = searchEndMs,
            ) ?: earliestTriggerMs
            mixDurationMs = (audibleEndMs - triggerPositionMs)
                .coerceIn(MIN_DJ_FADE_MS, maxMixDurationMs)
        }

        val incomingStartVolumeScale = nextProfile?.let { profile ->
            incomingStartVolumeScale(
                currentProfile = currentProfile,
                nextProfile = profile,
                triggerPositionMs = triggerPositionMs,
                nextStartMs = nextStartMs,
            )
        } ?: 1f
        val transitionCurve = expertTransitionCurve(
            hasMeaningfulTailSilence = hasMeaningfulTailSilence,
            currentProfile = currentProfile,
            nextProfile = nextProfile,
            triggerPositionMs = triggerPositionMs,
            nextStartMs = nextStartMs,
        )

        return ExpertDjPlan(
            target = target.copy(startPositionMs = nextStartMs),
            triggerPositionMs = triggerPositionMs,
            mixDurationMs = mixDurationMs,
            preloadPositionMs = (triggerPositionMs - DJ_EXPERT_PRELOAD_LEAD_MS).coerceAtLeast(0L),
            incomingStartVolumeScale = incomingStartVolumeScale,
            transitionCurve = transitionCurve,
        )
    }

    private fun classicDjPlan(target: ServiceDjTarget, durationMs: Long, requestedMixDurationMs: Long): ExpertDjPlan {
        return ExpertDjPlan(
            target = target,
            triggerPositionMs = (durationMs - requestedMixDurationMs).coerceAtLeast(0L),
            mixDurationMs = requestedMixDurationMs.coerceAtLeast(MIN_DJ_FADE_MS),
            preloadPositionMs = (durationMs - requestedMixDurationMs - DJ_EXPERT_PRELOAD_LEAD_MS).coerceAtLeast(0L),
        )
    }

    private fun nextStartPositionMs(nextProfile: DjSilenceAnalyzer.AudioProfile?): Long {
        nextProfile ?: return 0L
        val audibleStartMs = nextProfile.audibleStartMs() ?: return 0L
        val earlyPeak = nextProfile.averagePeak(
            positionMs = audibleStartMs + DJ_EXPERT_ATTACK_SAMPLE_OFFSET_MS,
            radiusMs = DJ_EXPERT_ATTACK_SAMPLE_RADIUS_MS,
        )
        val preRollMs = if (earlyPeak >= DJ_EXPERT_STRONG_ATTACK_PEAK) {
            DJ_EXPERT_STRONG_ATTACK_PREROLL_MS
        } else {
            DJ_EXPERT_NEXT_PREROLL_MS
        }
        return (audibleStartMs - preRollMs).coerceAtLeast(0L)
    }

    private fun DjSilenceAnalyzer.AudioProfile.bestLowEnergyTrigger(startMs: Long, endMs: Long): Long? {
        if (endMs <= startMs) return null
        val threshold = audibleThreshold()
        return windows
            .asSequence()
            .filter { window -> window.startMs in startMs..endMs }
            .minByOrNull { window ->
                val positionMs = window.startMs
                val localRms = averageRms(positionMs, DJ_EXPERT_ENERGY_SCORE_RADIUS_MS)
                val localPeak = averagePeak(positionMs, DJ_EXPERT_ENERGY_SCORE_RADIUS_MS)
                val previousRms = averageRms(positionMs - DJ_EXPERT_ENERGY_SCORE_RADIUS_MS, DJ_EXPERT_ENERGY_SCORE_RADIUS_MS)
                val nextRms = averageRms(positionMs + DJ_EXPERT_ENERGY_SCORE_RADIUS_MS, DJ_EXPERT_ENERGY_SCORE_RADIUS_MS)
                val risingPenalty = (nextRms - previousRms).coerceAtLeast(0f)
                val audiblePenalty = (localRms - threshold).coerceAtLeast(0f)
                localRms + localPeak * 0.18f + risingPenalty * 0.35f + audiblePenalty * 0.25f
            }
            ?.startMs
    }

    private fun incomingStartVolumeScale(
        currentProfile: DjSilenceAnalyzer.AudioProfile,
        nextProfile: DjSilenceAnalyzer.AudioProfile,
        triggerPositionMs: Long,
        nextStartMs: Long,
    ): Float {
        val outgoingRms = currentProfile.averageRms(
            positionMs = triggerPositionMs + DJ_EXPERT_LEVEL_SAMPLE_OFFSET_MS,
            radiusMs = DJ_EXPERT_LEVEL_SAMPLE_RADIUS_MS,
        )
        val incomingRms = nextProfile.averageRms(
            positionMs = nextStartMs + DJ_EXPERT_LEVEL_SAMPLE_OFFSET_MS,
            radiusMs = DJ_EXPERT_LEVEL_SAMPLE_RADIUS_MS,
        )
        if (outgoingRms < DJ_EXPERT_MIN_LEVEL_RMS || incomingRms < DJ_EXPERT_MIN_LEVEL_RMS) return 1f

        val ratio = (outgoingRms / incomingRms).coerceIn(0f, 1f)
        val softenedScale = 1f - (1f - ratio) * DJ_EXPERT_LEVEL_MATCH_STRENGTH
        return softenedScale.coerceIn(DJ_EXPERT_MIN_INCOMING_VOLUME_SCALE, 1f)
    }

    private fun expertTransitionCurve(
        hasMeaningfulTailSilence: Boolean,
        currentProfile: DjSilenceAnalyzer.AudioProfile,
        nextProfile: DjSilenceAnalyzer.AudioProfile?,
        triggerPositionMs: Long,
        nextStartMs: Long,
    ): DjTransitionCurve {
        nextProfile ?: return DjTransitionCurve.ExpertNormal
        val outgoingRms = currentProfile.averageRms(
            positionMs = triggerPositionMs + DJ_EXPERT_LEVEL_SAMPLE_OFFSET_MS,
            radiusMs = DJ_EXPERT_LEVEL_SAMPLE_RADIUS_MS,
        )
        val incomingRms = nextProfile.averageRms(
            positionMs = nextStartMs + DJ_EXPERT_LEVEL_SAMPLE_OFFSET_MS,
            radiusMs = DJ_EXPERT_LEVEL_SAMPLE_RADIUS_MS,
        )
        val incomingPeak = nextProfile.averagePeak(
            positionMs = nextStartMs + DJ_EXPERT_ATTACK_SAMPLE_OFFSET_MS,
            radiusMs = DJ_EXPERT_ATTACK_SAMPLE_RADIUS_MS,
        )
        val strongIncoming = incomingPeak >= DJ_EXPERT_STRONG_ATTACK_PEAK ||
            (outgoingRms >= DJ_EXPERT_MIN_LEVEL_RMS && incomingRms > outgoingRms * DJ_EXPERT_STRONG_INCOMING_RMS_RATIO)

        return when {
            hasMeaningfulTailSilence && strongIncoming -> DjTransitionCurve.ExpertSilenceStrongIncoming
            hasMeaningfulTailSilence -> DjTransitionCurve.ExpertSilence
            strongIncoming -> DjTransitionCurve.ExpertStrongIncoming
            else -> DjTransitionCurve.ExpertNormal
        }
    }

    private fun isSameExpertTransitionContext(player: ExoPlayer, currentMediaId: String): Boolean {
        return djEnabled &&
            djMixMode == DjMixMode.Expert &&
            playbackMode != PlaybackMode.RepeatCurrent &&
            activePlayer === player &&
            player.isPlaying &&
            player.currentMediaItem?.mediaId == currentMediaId
    }

    private suspend fun runDjTransition(
        outgoingPlayer: ExoPlayer,
        incomingPlayer: ExoPlayer,
        currentMediaId: String,
        target: ServiceDjTarget,
        mixDurationMs: Long,
        incomingStartVolumeScale: Float = 1f,
        transitionCurve: DjTransitionCurve = DjTransitionCurve.Classic,
        onProgress: ((Float) -> Unit)? = null,
    ) {
        if (activePlayer !== outgoingPlayer || standbyPlayer !== incomingPlayer) return
        val originalVolume = outgoingPlayer.volume.coerceIn(0f, 1f)
        val safeIncomingStartVolumeScale = incomingStartVolumeScale.coerceIn(DJ_EXPERT_MIN_INCOMING_VOLUME_SCALE, 1f)
        val prepared = prepareStandbyPlayer(currentMediaId, target)
        if (!prepared) return
        djTransitionOriginalVolume = originalVolume

        incomingPlayer.volume = 0f
        incomingPlayer.play()
        if (!waitForPlayerReady(incomingPlayer, DJ_INCOMING_READY_TIMEOUT_MS)) {
            outgoingPlayer.volume = originalVolume
            djTransitionOriginalVolume = null
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
                djTransitionOriginalVolume = null
                stopStandbyPlayer()
                return
            }
            val progress = (step + 1).toFloat() / stepCount.toFloat()
            onProgress?.invoke(progress)
            val incomingProgress = transitionCurve.curvedIncomingProgress(progress)
            val outgoingProgress = transitionCurve.curvedOutgoingProgress(progress)
            val transitionHeadroom = transitionCurve.headroomAt(progress)
            val incomingScaleProgress = incomingProgress * incomingProgress
            val incomingVolumeScale = safeIncomingStartVolumeScale +
                (1f - safeIncomingStartVolumeScale) * incomingScaleProgress
            outgoingPlayer.volume = originalVolume * equalPowerOut(outgoingProgress) * transitionHeadroom
            incomingPlayer.volume = originalVolume * equalPowerIn(incomingProgress) * incomingVolumeScale * transitionHeadroom
        }

        incomingPlayer.volume = originalVolume
        outgoingPlayer.volume = 0f
        swapActivePlayer(outgoingPlayer, incomingPlayer)
        outgoingPlayer.pause()
        outgoingPlayer.stop()
        outgoingPlayer.clearMediaItems()
        outgoingPlayer.volume = 0f
        clearPreloadedTarget()
        djTransitionOriginalVolume = null
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
        standbyPlayer.setMediaItems(target.mediaItems, target.startIndex, target.startPositionMs.coerceAtLeast(0L))
        applyPlaybackModeToPlayer(standbyPlayer)
        standbyPlayer.prepare()
        preloadedForMediaId = currentMediaId
        preloadedTarget = target
        return true
    }

    private fun swapActivePlayer(outgoingPlayer: ExoPlayer, incomingPlayer: ExoPlayer) {
        autoDjInternalChange = true
        try {
            incomingPlayer.setAudioAttributes(musicAudioAttributes, true)
            applyPlaybackModeToPlayer(incomingPlayer)
            mediaSession?.setPlayer(incomingPlayer)
            activePlayer = incomingPlayer
            standbyPlayer = outgoingPlayer
            standbyPlayer.setAudioAttributes(musicAudioAttributes, false)
            applyPlaybackModeToPlayer(standbyPlayer)
        } finally {
            autoDjInternalChange = false
        }
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
        val originalVolume = autoDjOriginalVolume ?: djTransitionOriginalVolume
        cancelAutoDj()
        djTransitionJob?.cancel()
        djTransitionJob = null
        if (::activePlayer.isInitialized) originalVolume?.let { activePlayer.volume = it }
        djTransitionOriginalVolume = null
        stopStandbyPlayer()
    }

    private fun stopStandbyPlayer() {
        clearPreloadedTarget()
        if (::standbyPlayer.isInitialized) {
            resetDeckProcessing(standbyPlayer)
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
    private class CompactMediaNotificationProvider(context: Context) : DefaultMediaNotificationProvider(
        context,
        { MEDIA_NOTIFICATION_ID },
        MEDIA_NOTIFICATION_CHANNEL_ID,
        R.string.notification_channel_media_playback,
    ) {
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
        deckEffects.clear()
        serviceScope.cancel()
        _autoDjStatus.value = AutoDjStatus()
        mediaSession = null
        if (activeInstance === this) activeInstance = null
        super.onDestroy()
    }

    private data class ServiceDjTarget(
        val mediaItems: List<MediaItem>,
        val startIndex: Int,
        val mediaId: String,
        val startPositionMs: Long = 0L,
    )

    private data class PreviewSnapshot(
        val player: ExoPlayer,
        val queueIds: List<String>,
        val mediaId: String?,
        val index: Int,
        val positionMs: Long,
        val playWhenReady: Boolean,
        val volume: Float,
        val parameters: PlaybackParameters,
    )

    private data class ExpertDjPlan(
        val target: ServiceDjTarget,
        val triggerPositionMs: Long,
        val mixDurationMs: Long,
        val preloadPositionMs: Long,
        val incomingStartVolumeScale: Float = 1f,
        val transitionCurve: DjTransitionCurve = DjTransitionCurve.Classic,
    )

    private data class DjTransitionCurve(
        val incomingPower: Double,
        val outgoingPower: Double,
        val mixHeadroom: Float,
    ) {
        fun curvedIncomingProgress(progress: Float): Float = progress.curvedBy(incomingPower)

        fun curvedOutgoingProgress(progress: Float): Float = progress.curvedBy(outgoingPower)

        fun headroomAt(progress: Float): Float {
            val centerWeight = sin(progress.coerceIn(0f, 1f).toDouble() * PI).toFloat()
            return 1f - (1f - mixHeadroom.coerceIn(0f, 1f)) * centerWeight
        }

        private fun Float.curvedBy(power: Double): Float {
            return coerceIn(0f, 1f).toDouble().pow(power).toFloat().coerceIn(0f, 1f)
        }

        companion object {
            val Classic = DjTransitionCurve(incomingPower = 1.0, outgoingPower = 1.0, mixHeadroom = 1f)
            val ExpertSilence = DjTransitionCurve(incomingPower = 0.90, outgoingPower = 1.05, mixHeadroom = 0.96f)
            val ExpertSilenceStrongIncoming = DjTransitionCurve(incomingPower = 1.18, outgoingPower = 1.05, mixHeadroom = 0.95f)
            val ExpertNormal = DjTransitionCurve(incomingPower = 1.15, outgoingPower = 0.95, mixHeadroom = 0.93f)
            val ExpertStrongIncoming = DjTransitionCurve(incomingPower = 1.35, outgoingPower = 0.90, mixHeadroom = 0.91f)
        }
    }

    private data class DjServiceConfig(
        val enabled: Boolean,
        val mixMode: DjMixMode,
        val mixDurationSeconds: Int,
        val playbackMode: PlaybackMode,
    )

    companion object {
        private val _mixPreviewState = MutableStateFlow(MixPreviewState())
        val mixPreviewState: StateFlow<MixPreviewState> = _mixPreviewState.asStateFlow()
        val currentPlaybackMode: PlaybackMode? get() = activeInstance?.sessionPlaybackMode()
        private val _autoDjStatus = MutableStateFlow(AutoDjStatus())
        val autoDjStatus: StateFlow<AutoDjStatus> = _autoDjStatus.asStateFlow()
        private var latestAutoDjSettings = AutoDjSettings()
        private var activeInstance: MusicService? = null
        private var latestDjConfig = DjServiceConfig(
            enabled = false,
            mixMode = DjMixMode.Classic,
            mixDurationSeconds = 8,
            playbackMode = PlaybackMode.Ordered,
        )

        fun stopMixPreview(restorePlayback: Boolean = true) {
            activeInstance?.stopPreview(restorePlayback)
        }

        fun updateDjConfig(enabled: Boolean, mixMode: DjMixMode, mixDurationSeconds: Int, playbackMode: PlaybackMode) {
            val config = DjServiceConfig(
                enabled = enabled,
                mixMode = mixMode,
                mixDurationSeconds = mixDurationSeconds,
                playbackMode = playbackMode,
            )
            latestDjConfig = config
            activeInstance?.applyDjConfig(config)
        }

        fun updateAutoDjSettings(settings: AutoDjSettings) {
            val safeSettings = settings.sanitized()
            latestAutoDjSettings = safeSettings
            activeInstance?.let { service ->
                if (service.autoDjSettings != safeSettings) {
                    service.autoDjSettings = safeSettings
                }
            }
        }

        private const val DJ_MONITOR_INTERVAL_MS = 120L
        private const val AUTO_DJ_ANALYSIS_TIMEOUT_MS = 20_000L
        private const val AUTO_DJ_MIN_CONFIDENCE = 0.72f
        private const val AUTO_DJ_PREPARE_LEAD_MS = 3_000L
        private const val AUTO_DJ_PREROLL_MS = 1_000L
        private const val AUTO_DJ_STARTUP_TIMEOUT_MS = 2_500L
        private const val AUTO_DJ_REBUFFER_GRACE_MS = 500L
        private const val AUTO_DJ_ALIGNMENT_TOLERANCE_MS = 65L
        private const val AUTO_DJ_DRIFT_TOLERANCE_MS = 120L
        private const val AUTO_DJ_ENVELOPE_INTERVAL_MS = 20L
        private const val DJ_MONITOR_IDLE_INTERVAL_MS = 500L
        private const val MIN_DJ_MIX_SECONDS = 5
        private const val MAX_DJ_MIX_SECONDS = 8
        private const val MIN_DJ_SONG_EXTRA_MS = 2_000L
        private const val MIN_DJ_FADE_MS = 1_000L
        private const val DJ_PRELOAD_LEAD_MS = 2_000L
        private const val DJ_HANDOFF_SAFETY_MS = 700L
        private const val DJ_EXPERT_ANALYSIS_LEAD_MS = 30_000L
        private const val DJ_EXPERT_MIN_TRAILING_SILENCE_MS = 1_500L
        private const val DJ_EXPERT_SILENCE_HANDOFF_FADE_MS = 3_500L
        private const val DJ_EXPERT_PRELOAD_LEAD_MS = 7_000L
        private const val DJ_EXPERT_NEXT_PREROLL_MS = 220L
        private const val DJ_EXPERT_STRONG_ATTACK_PREROLL_MS = 520L
        private const val DJ_EXPERT_MIN_NORMAL_BLEND_MS = 3_000L
        private const val DJ_EXPERT_TRIGGER_SEARCH_MS = 4_000L
        private const val DJ_EXPERT_ENERGY_SCORE_RADIUS_MS = 480L
        private const val DJ_EXPERT_LEVEL_SAMPLE_OFFSET_MS = 650L
        private const val DJ_EXPERT_LEVEL_SAMPLE_RADIUS_MS = 900L
        private const val DJ_EXPERT_ATTACK_SAMPLE_OFFSET_MS = 360L
        private const val DJ_EXPERT_ATTACK_SAMPLE_RADIUS_MS = 520L
        private const val DJ_EXPERT_MIN_LEVEL_RMS = 0.004f
        private const val DJ_EXPERT_LEVEL_MATCH_STRENGTH = 0.65f
        private const val DJ_EXPERT_MIN_INCOMING_VOLUME_SCALE = 0.70f
        private const val DJ_EXPERT_STRONG_ATTACK_PEAK = 0.24f
        private const val DJ_EXPERT_STRONG_INCOMING_RMS_RATIO = 1.35f
        private const val DJ_EXPERT_TRIGGER_TOLERANCE_MS = 120L
        private const val DJ_EXPERT_WAIT_MAX_MS = 500L
        private const val DJ_FADE_STEPS = 24
        private const val DJ_INCOMING_READY_TIMEOUT_MS = 1_500L
        private const val DJ_READY_POLL_MS = 50L
        private const val MEDIA_NOTIFICATION_ID = 2101
        private const val MEDIA_NOTIFICATION_CHANNEL_ID = "softmusic_media_playback"
        private const val MEDIA_SESSION_ID = "softmusic-media-session"
    }
}
