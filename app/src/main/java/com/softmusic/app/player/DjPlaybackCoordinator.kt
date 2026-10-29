package com.softmusic.app.player

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.softmusic.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

internal data class DjPlaybackConfig(
    val enabled: Boolean,
    val mixMode: DjMixMode,
    val mixDurationSeconds: Int,
    val playbackMode: PlaybackMode,
)

/** Orchestrates the live Classic/Expert monitor and transitions; never creates a media session. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class DjPlaybackCoordinator(
    context: Context,
    initialConfig: DjPlaybackConfig,
    private val resetPreviewState: () -> Unit,
    private val resetAutoDjStatus: (String?) -> Unit,
    private val onExpertPlan: (ExpertDjPlan) -> Unit = {},
    private val onExpertFadeProgress: (Float) -> Unit = {},
    private val beforeNextTarget: () -> Boolean = { true },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = DjTransitionJobs(scope)
    private val silenceAnalyzer = DjSilenceAnalyzer(context.applicationContext)
    private val beatAnalyzer = DjBeatAnalyzer(context.applicationContext)
    private lateinit var decks: DjDeckController
    private val queueGuard = DjQueueTransitionGuard(
        activeDeck = { activePlayer },
        currentMediaId = { player: ExoPlayer -> player.currentMediaItem?.mediaId },
    )
    private var config = initialConfig
    private val djEnabled: Boolean get() = config.enabled
    private val djMixMode: DjMixMode get() = config.mixMode
    private val djMixDurationSeconds: Int get() = config.mixDurationSeconds
    private val playbackMode: PlaybackMode get() = config.playbackMode
    private val activePlayer: ExoPlayer get() = decks.activePlayer
    private val standbyPlayer: ExoPlayer get() = decks.standbyPlayer
    private var handoffInProgress = false
    private var preparedPreviewPlan: PreparedPreviewPlan? = null
    private data class PreparedPreviewPlan(val current: MediaItem, val next: MediaItem, val durationMs: Long, val plan: ExpertDjPlan)

    fun attachDecks(controller: DjDeckController) {
        decks = controller
        decks.applyPlaybackMode(playbackMode)
    }

    fun playerListener(player: ExoPlayer): Player.Listener = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (!::decks.isInitialized) return
            if (queueGuard.onTimelineChanged(player, reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)) {
                resetTransition()
            }
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (!::decks.isInitialized || handoffInProgress || player !== activePlayer) return
            if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                cancelDjTransition()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (::decks.isInitialized && !handoffInProgress && player === activePlayer && !playWhenReady) {
                cancelDjTransition()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (::decks.isInitialized && !handoffInProgress && player === activePlayer && !isPlaying) {
                cancelDjTransition()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!::decks.isInitialized) return
            if (BuildConfig.DEBUG) {
                fun deck(value: ExoPlayer): String =
                    "id=${value.currentMediaItem?.mediaId},pos=${value.currentPosition},buffered=${value.bufferedPosition},duration=${value.duration}," +
                        "state=${value.playbackState},playing=${value.isPlaying},pwr=${value.playWhenReady},supp=${value.playbackSuppressionReason}"
                Log.d("SoftMusicMix", "event=player_error reason=active=${player === activePlayer}:code=${error.errorCode} " +
                    "mode=$playbackMode out=[${deck(activePlayer)}] in=[${deck(standbyPlayer)}]")
            }
            if (player === activePlayer || player === standbyPlayer) cancelDjTransition()
        }
    }

    fun applyConfig(updated: DjPlaybackConfig) {
        if (djEnabled != updated.enabled || djMixMode != updated.mixMode || playbackMode != updated.playbackMode ||
            djMixDurationSeconds != updated.mixDurationSeconds
        ) {
            stopMixPreview(restorePlayback = true)
            cancelDjTransition()
        }
        config = updated.copy(mixDurationSeconds = updated.mixDurationSeconds.coerceIn(MIN_DJ_MIX_SECONDS, MAX_DJ_MIX_SECONDS))
        decks.applyPlaybackMode(playbackMode)
        decks.resetLevelGains()
        if (!djEnabled || playbackMode == PlaybackMode.RepeatCurrent) {
            cancelDjTransition()
            decks.stopStandby()
        }
    }

    // Collection mixing/preview has no runtime entry point. Keep its observable reset API dormant.
    @Suppress("UNUSED_PARAMETER")
    fun stopMixPreview(restorePlayback: Boolean) {
        resetPreviewState()
    }

    fun sessionPlaybackMode(): PlaybackMode? =
        if (activePlayer.mediaItemCount > 0) playbackMode else null

    fun startMonitor() {
        jobs.startMonitor(
            intervalMs = { if (djEnabled) DJ_MONITOR_INTERVAL_MS else DJ_MONITOR_IDLE_INTERVAL_MS },
            tick = ::maybeStartDjTransition,
        )
    }

    /** Prepare before audition playback, so decoding never consumes its short audible window. */
    suspend fun expertPreviewStartPosition(durationMs: Long): Long {
        val current = activePlayer.currentMediaItem ?: return 0L
        val target = nextDjTarget() ?: return 0L
        val next = target.mediaItems.getOrNull(target.startIndex) ?: return 0L
        val captured = queueGuard.capture(activePlayer, current.mediaId)
        val plan = analyzeExpertPlan(current, next, durationMs, djMixDurationSeconds * 1_000L) {
            queueGuard.isCurrent(captured) && activePlayer.currentMediaItem == current
        } ?: ExpertDjPlanner.classic(durationMs, djMixDurationSeconds * 1_000L)
        if (!queueGuard.isCurrent(captured)) return 0L
        preparedPreviewPlan = PreparedPreviewPlan(current, next, durationMs, plan)
        val audibleEnd = plan.audibleEndPositionMs
        return (audibleEnd - djMixDurationSeconds * 1_000L - 2_000L).coerceAtLeast(0L)
    }

    fun nextPreviewTarget(): DjPlaybackTarget? = decks.nextTarget(playbackMode)

    fun cancelForExpertPreview() = cancelDjTransition()

    /** An already audible transition keeps its target; pending analysis/preload can be replanned. */
    fun invalidatePendingTarget() {
        if (!jobs.isTransitionCommitted) cancelDjTransition()
    }

    private fun launchTransition(block: suspend CoroutineScope.(DjTransitionJobs.Transition) -> Unit) {
        val outgoingPlayer = activePlayer
        jobs.startTransition { owner ->
            try {
                block(owner)
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                if (BuildConfig.DEBUG) Log.d("SoftMusicMix", "event=transition_abort reason=unexpected_error", throwable)
            } finally {
                // Explicit cancellation already cleans up synchronously. A still-owned job must
                // also clean up when analysis, preparation, gain updates or handoff throws.
                if (owner.isCurrent) {
                    if (activePlayer === outgoingPlayer) {
                        restoreFailedTransition(owner, outgoingPlayer)
                    } else if (owner.originalVolume != null) {
                        owner.clearVolume()
                        decks.stopStandby()
                    }
                }
            }
        }
    }

    private fun maybeStartDjTransition() {
        val player = activePlayer
        if (!player.isPlaying) return
        if (playbackMode == PlaybackMode.RepeatCurrent) return
        if (jobs.isTransitionActive) return
        if (player.mediaItemCount <= 1) return

        if (!beforeNextTarget()) return

        // Smart ordering runs independently; the DJ setting only controls audible mixing.
        if (!djEnabled || djMixMode == DjMixMode.AutoDj) return

        val currentMediaId = player.currentMediaItem?.mediaId ?: return
        val duration = player.duration.takeIf { it > 0 } ?: return
        val position = player.currentPosition.coerceAtLeast(0)
        val mixDurationMs = djMixDurationSeconds.coerceIn(MIN_DJ_MIX_SECONDS, MAX_DJ_MIX_SECONDS) * 1_000L
        val remainingMs = duration - position
        if (duration <= mixDurationMs + MIN_DJ_SONG_EXTRA_MS) return
        if (remainingMs <= MIN_DJ_FADE_MS + DJ_HANDOFF_SAFETY_MS) return

        val target = nextDjTarget() ?: return
        val transition = queueGuard.capture(player, currentMediaId)
        if (djMixMode == DjMixMode.Expert) {
            maybeStartExpertDjTransition(
                player = player,
                currentMediaId = currentMediaId,
                target = target,
                durationMs = duration,
                positionMs = position,
                requestedMixDurationMs = mixDurationMs,
                transition = transition,
            )
            return
        }

        if (remainingMs <= mixDurationMs + DJ_PRELOAD_LEAD_MS) {
            prepareStandbyPlayer(transition, target)
        }
        if (remainingMs > mixDurationMs) return

        launchTransition { owner ->
            runDjTransition(
                outgoingPlayer = player,
                incomingPlayer = standbyPlayer,
                transition = transition,
                owner = owner,
                target = target,
                mixDurationMs = mixDurationMs.coerceAtMost(remainingMs - DJ_HANDOFF_SAFETY_MS),
            )
        }
    }

    private fun maybeStartExpertDjTransition(
        player: ExoPlayer,
        currentMediaId: String,
        target: DjPlaybackTarget,
        durationMs: Long,
        positionMs: Long,
        requestedMixDurationMs: Long,
        transition: DjQueueTransition<ExoPlayer>,
    ) {
        if (durationMs - positionMs > DJ_EXPERT_ANALYSIS_LEAD_MS) return

        launchTransition { owner ->
            val analyzedPlan = buildExpertDjPlan(
                player = player,
                currentMediaId = currentMediaId,
                target = target,
                durationMs = durationMs,
                requestedMixDurationMs = requestedMixDurationMs,
                transition = transition,
                owner = owner,
            )
            if (!isSameExpertTransitionContext(transition, owner)) return@launchTransition
            val plan = analyzedPlan?.takeIf {
                ExpertDjTiming.availableFadeMs(it, durationMs, player.currentPosition) != null
            } ?: ExpertDjPlanner.classic(durationMs, requestedMixDurationMs).also {
                if (analyzedPlan != null && BuildConfig.DEBUG) {
                    Log.d("SoftMusicMix", "event=expert_fallback reason=late_analysis pos=${player.currentPosition} audibleEnd=${analyzedPlan.audibleEndPositionMs}")
                }
            }
            onExpertPlan(plan)

            while (isActive) {
                if (!isSameExpertTransitionContext(transition, owner)) return@launchTransition
                val currentPosition = player.currentPosition.coerceAtLeast(0)
                val remainingToTrigger = plan.triggerPositionMs - currentPosition
                val triggerToleranceMs = if (plan.beatAligned) DJ_EXPERT_BEAT_TRIGGER_TOLERANCE_MS else DJ_EXPERT_TRIGGER_TOLERANCE_MS
                if (remainingToTrigger <= triggerToleranceMs) break
                if (currentPosition >= plan.preloadPositionMs) {
                    if (!prepareStandbyPlayer(transition, target.copy(startPositionMs = plan.incomingStartPositionMs), owner)) {
                        return@launchTransition
                    }
                }
                val minimumWaitMs = if (plan.beatAligned) DJ_EXPERT_BEAT_WAIT_MIN_MS else DJ_MONITOR_INTERVAL_MS
                delay(remainingToTrigger.coerceIn(minimumWaitMs, DJ_EXPERT_WAIT_MAX_MS))
            }

            if (!isSameExpertTransitionContext(transition, owner)) return@launchTransition
            runDjTransition(
                outgoingPlayer = player,
                incomingPlayer = standbyPlayer,
                transition = transition,
                owner = owner,
                target = target.copy(startPositionMs = plan.incomingStartPositionMs),
                mixDurationMs = plan.mixDurationMs,
                incomingStartVolumeScale = plan.incomingStartVolumeScale,
                transitionCurve = plan.transitionCurve,
                expertPlan = plan,
            )
        }
    }

    private suspend fun buildExpertDjPlan(
        player: ExoPlayer,
        currentMediaId: String,
        target: DjPlaybackTarget,
        durationMs: Long,
        requestedMixDurationMs: Long,
        transition: DjQueueTransition<ExoPlayer>,
        owner: DjTransitionJobs.Transition,
    ): ExpertDjPlan? {
        if (!isSameExpertTransitionContext(transition, owner)) return null
        val currentItem = player.currentMediaItem?.takeIf { it.mediaId == currentMediaId } ?: return null
        val nextItem = target.mediaItems.getOrNull(target.startIndex) ?: return null
        preparedPreviewPlan?.takeIf {
            it.current == currentItem && it.next == nextItem && it.durationMs == durationMs
        }?.let { return it.plan }
        return analyzeExpertPlan(currentItem, nextItem, durationMs, requestedMixDurationMs) {
            isSameExpertTransitionContext(transition, owner)
        }
    }

    private suspend fun analyzeExpertPlan(
        currentItem: MediaItem,
        nextItem: MediaItem,
        durationMs: Long,
        requestedMixDurationMs: Long,
        isCurrent: () -> Boolean,
    ): ExpertDjPlan? {
        val currentUri = currentItem.localConfiguration?.uri ?: return null
        val nextUri = nextItem.localConfiguration?.uri ?: return null
        var safePlan: ExpertDjPlan? = null
        val analyzedPlan = withTimeoutOrNull(DJ_EXPERT_ANALYSIS_TIMEOUT_MS) {
            val currentProfile = silenceAnalyzer.tailProfile(currentUri, durationMs) ?: return@withTimeoutOrNull null
            if (!isCurrent()) return@withTimeoutOrNull null
            safePlan = ExpertDjPlanner.plan(durationMs, requestedMixDurationMs, currentProfile, null)
            val nextProfile = silenceAnalyzer.headProfile(nextUri)
            safePlan = ExpertDjPlanner.plan(durationMs, requestedMixDurationMs, currentProfile, nextProfile)
            if (!isCurrent()) return@withTimeoutOrNull null
            // The real physical tail may contain silence beyond the audible handoff. The
            // planner accepts beats only if their measured range covers the chosen trigger.
            val currentBeats = beatAnalyzer.analyze(currentUri, durationMs, tail = true)
            if (!isCurrent()) return@withTimeoutOrNull null
            val nextDuration = nextItem.mediaMetadata.durationMs?.takeIf { it > 0L } ?: 24_000L
            val nextBeats = beatAnalyzer.analyze(nextUri, nextDuration, tail = false)
            ExpertDjPlanner.plan(durationMs, requestedMixDurationMs, currentProfile, nextProfile, currentBeats, nextBeats)
        }
        if (!isCurrent()) return null
        return analyzedPlan ?: safePlan
    }

    private fun isSameExpertTransitionContext(
        transition: DjQueueTransition<ExoPlayer>,
        owner: DjTransitionJobs.Transition,
    ): Boolean {
        return djEnabled &&
            djMixMode == DjMixMode.Expert &&
            playbackMode != PlaybackMode.RepeatCurrent &&
            owner.isCurrent && queueGuard.isCurrent(transition) && transition.outgoing.isPlaying
    }

    private suspend fun runDjTransition(
        outgoingPlayer: ExoPlayer,
        incomingPlayer: ExoPlayer,
        transition: DjQueueTransition<ExoPlayer>,
        owner: DjTransitionJobs.Transition,
        target: DjPlaybackTarget,
        mixDurationMs: Long,
        incomingStartVolumeScale: Float = 1f,
        transitionCurve: DjTransitionCurve = DjTransitionCurve.Classic,
        expertPlan: ExpertDjPlan? = null,
    ) {
        if (!owner.isCurrent || !queueGuard.isCurrent(transition) || standbyPlayer !== incomingPlayer) return
        val originalVolume = outgoingPlayer.volume.coerceIn(0f, 1f)
        val safeIncomingStartVolumeScale = incomingStartVolumeScale.coerceIn(0.70f, 1f)
        if (!owner.rememberVolume(originalVolume)) return
        val prepared = prepareStandbyPlayer(transition, target, owner)
        if (!prepared) return

        fun perform(action: () -> Unit): Boolean {
            val performed = queueGuard.performIfCurrent(transition, { owner.isCurrent }, action)
            if (!performed) restoreFailedTransition(owner, outgoingPlayer)
            return performed
        }

        fun canContinue(): Boolean = djEnabled && owner.isCurrent && queueGuard.isCurrent(transition) &&
            standbyPlayer === incomingPlayer && incomingPlayer.currentMediaItem?.mediaId == target.mediaId &&
            outgoingPlayer.isPlaying

        fun canContinueExpertFade(): Boolean = canContinue() &&
            incomingPlayer.playbackState == Player.STATE_READY && incomingPlayer.playWhenReady &&
            incomingPlayer.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE

        fun applyGains(progress: Float): Boolean {
            val gains = transitionCurve.gainsAt(progress, safeIncomingStartVolumeScale, expertPlan?.incomingLevelGain ?: 1f)
            if (expertPlan != null && !perform {
                    decks.applyExpertBassSwap(outgoingPlayer, incomingPlayer, progress)
                    onExpertFadeProgress(progress)
                }) return false
            return perform { outgoingPlayer.volume = originalVolume * gains.outgoing } &&
                perform { incomingPlayer.volume = originalVolume * gains.incoming }
        }

        if (!perform { incomingPlayer.volume = 0f }) return
        if (expertPlan != null) {
            val ready = awaitExpertDjPrepared(
                timeoutMs = DJ_INCOMING_READY_TIMEOUT_MS,
                isReady = { incomingPlayer.playbackState == Player.STATE_READY },
                isCurrent = ::canContinue,
                clockMs = SystemClock::elapsedRealtime,
            )
            if (!ready) {
                if (BuildConfig.DEBUG) Log.d("SoftMusicMix", "event=expert_abort reason=incoming_not_ready")
                restoreFailedTransition(owner, outgoingPlayer)
                return
            }
            if (expertPlan.beatAligned &&
                outgoingPlayer.currentPosition - expertPlan.triggerPositionMs > DJ_EXPERT_MAX_BEAT_START_LATENESS_MS
            ) {
                // Preparation can miss a measured beat even though enough audible time
                // remains for the energy fade. Do not report this fallback as synchronized.
                onExpertPlan(expertPlan.copy(beatAligned = false, currentBpm = null, incomingBpm = null))
                if (BuildConfig.DEBUG) Log.d("SoftMusicMix", "event=expert_fallback reason=late_beat_start pos=${outgoingPlayer.currentPosition} trigger=${expertPlan.triggerPositionMs}")
            }
            // Buffering consumed real outgoing audio time. Never expand a short remaining
            // interval to the minimum fade or keep fading into the detected silent tail.
            val physicalEndMs = outgoingPlayer.duration.takeIf { it > 0L } ?: return
            val availableFadeMs = ExpertDjTiming.availableFadeMs(expertPlan, physicalEndMs, outgoingPlayer.currentPosition)
            if (availableFadeMs == null) {
                if (BuildConfig.DEBUG) Log.d("SoftMusicMix", "event=expert_abort reason=insufficient_audible_time pos=${outgoingPlayer.currentPosition} audibleEnd=${expertPlan.audibleEndPositionMs}")
                return
            }
            if (!canContinue() || !perform { incomingPlayer.play() }) return
            if (!runExpertDjFade(availableFadeMs, ::canContinueExpertFade, ::applyGains, SystemClock::elapsedRealtime)) return
        } else {
            if (!perform { incomingPlayer.play() }) return
            if (!owner.isCurrent || !queueGuard.isCurrent(transition)) return
            val ready = waitForPlayerReady(incomingPlayer, DJ_INCOMING_READY_TIMEOUT_MS)
            if (!ready || !owner.isCurrent || !queueGuard.isCurrent(transition)) return

            val stepCount = DJ_FADE_STEPS
            val stepDelayMs = (mixDurationMs.coerceAtLeast(MIN_DJ_FADE_MS) / stepCount).coerceAtLeast(50L)
            repeat(stepCount) { step ->
                delay(stepDelayMs)
                if (!canContinue()) return
                if (!applyGains((step + 1).toFloat() / stepCount.toFloat())) return
            }
        }

        if (!canContinue()) return
        if (!perform { incomingPlayer.volume = originalVolume }) return
        if (!perform { outgoingPlayer.volume = 0f }) return
        if (!perform { decks.resetTransitionEffects() }) return
        if (!queueGuard.handoffIfCurrent(transition, { owner.isCurrent }) {
                handoffInProgress = true
                try {
                    decks.swap(outgoingPlayer, incomingPlayer, playbackMode)
                } finally {
                    handoffInProgress = false
                }
            }) {
            restoreFailedTransition(owner, outgoingPlayer)
            return
        }
        decks.finishTransition(outgoingPlayer)
        owner.clearVolume()
    }

    private suspend fun waitForPlayerReady(player: Player, timeoutMs: Long): Boolean = awaitDjIncomingReady(
        timeoutMs = timeoutMs,
        isReady = { player.playbackState == Player.STATE_READY },
        playWhenReady = { player.playWhenReady },
    )

    private fun nextDjTarget(): DjPlaybackTarget? = decks.nextTarget(playbackMode)

    private fun prepareStandbyPlayer(
        transition: DjQueueTransition<ExoPlayer>,
        target: DjPlaybackTarget,
        owner: DjTransitionJobs.Transition? = null,
    ): Boolean = queueGuard.performIfCurrent(transition, { owner?.isCurrent != false }) {
        decks.prepareStandby(transition.currentMediaId, target, playbackMode, transition.revision)
    }

    private fun restoreFailedTransition(owner: DjTransitionJobs.Transition, outgoing: ExoPlayer) {
        if (owner.isCurrent && activePlayer === outgoing) decks.resetTransitionEffects()
        owner.restoreAfterAbort(
            isOutgoingActive = { activePlayer === outgoing },
            restoreVolume = { outgoing.volume = it },
            stopStandby = decks::stopStandby,
        )
    }

    private fun cancelDjTransition() {
        queueGuard.invalidate()
        resetTransition()
    }

    private fun resetTransition() {
        decks.resetTransitionEffects()
        // The retired collection runtime only published this reset/current-item status.
        resetAutoDjStatus(activePlayer.currentMediaItem?.mediaId)
        jobs.cancelTransition(restoreVolume = { activePlayer.volume = it }, stopStandby = decks::stopStandby)
    }

    fun stopMonitor() = jobs.stopMonitor()

    fun shutdownPlayers() {
        stopMixPreview(restorePlayback = false)
        cancelDjTransition()
        decks.stopAll()
    }

    fun release() = scope.cancel()

    private companion object {
        private const val DJ_MONITOR_INTERVAL_MS = 120L
        private const val DJ_MONITOR_IDLE_INTERVAL_MS = 500L
        private const val MIN_DJ_MIX_SECONDS = 5
        private const val MAX_DJ_MIX_SECONDS = 8
        private const val MIN_DJ_SONG_EXTRA_MS = 2_000L
        private const val MIN_DJ_FADE_MS = 1_000L
        private const val DJ_PRELOAD_LEAD_MS = 2_000L
        private const val DJ_HANDOFF_SAFETY_MS = 700L
        private const val DJ_EXPERT_ANALYSIS_LEAD_MS = 120_000L
        private const val DJ_EXPERT_ANALYSIS_TIMEOUT_MS = 15_000L
        private const val DJ_EXPERT_TRIGGER_TOLERANCE_MS = 120L
        private const val DJ_EXPERT_BEAT_TRIGGER_TOLERANCE_MS = 10L
        private const val DJ_EXPERT_BEAT_WAIT_MIN_MS = 5L
        private const val DJ_EXPERT_MAX_BEAT_START_LATENESS_MS = 80L
        private const val DJ_EXPERT_WAIT_MAX_MS = 500L
        private const val DJ_FADE_STEPS = 24
        private const val DJ_INCOMING_READY_TIMEOUT_MS = 1_500L
    }
}
