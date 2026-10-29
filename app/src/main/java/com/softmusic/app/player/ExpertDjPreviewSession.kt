package com.softmusic.app.player

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.math.abs

/** A bounded audition with its own decks; the media session and history stay on the original. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class ExpertDjPreviewSession(
    private val context: Context,
    private val originalDecks: DjDeckController,
    private val originalPlayback: DjPlaybackCoordinator,
    private val mixDurationSeconds: Int,
    private val publish: (ExpertDjPreviewState) -> Unit,
    private val onClosed: (ExpertDjPreviewSession) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var previewDecks: DjDeckController? = null
    private var previewPlayback: DjPlaybackCoordinator? = null
    private var original: OriginalPlayback? = null
    private var originalListener: Player.Listener? = null
    private var closed = false
    private var handoffAtMs: Long? = null
    private var state = ExpertDjPreviewState()

    private data class OriginalPlayback(
        val player: ExoPlayer,
        val queue: List<MediaItem>,
        val index: Int,
        val item: MediaItem,
        val positionMs: Long,
        val playWhenReady: Boolean,
    )

    fun start() {
        if (closed || original != null) return
        val player = originalDecks.activePlayer
        val current = player.currentMediaItem
        val target = originalPlayback.nextPreviewTarget()
        val next = target?.mediaItems?.getOrNull(target.startIndex)
        if (current == null || next == null) {
            finish(true, ExpertDjPreviewPhase.Error, MISSING_QUEUE_MESSAGE)
            return
        }
        val durationMs = player.duration.takeIf { it > 0L } ?: current.mediaMetadata.durationMs?.takeIf { it > 0L }
        if (durationMs == null || durationMs < MIN_PREVIEW_TRACK_MS) {
            finish(true, ExpertDjPreviewPhase.Error, "Espera a que cargue una canción de al menos 12 segundos para probar la mezcla.")
            return
        }

        originalPlayback.cancelForExpertPreview()
        original = OriginalPlayback(player, List(player.mediaItemCount, player::getMediaItemAt),
            player.currentMediaItemIndex, current, player.currentPosition, player.playWhenReady)
        player.pause()
        watchOriginal(player)
        state = ExpertDjPreviewState(ExpertDjPreviewPhase.Preparing, current.title(), next.title())
        publish(state)

        scope.launch {
            try {
                withTimeout(PREVIEW_TIMEOUT_MS) {
                    val runtime = DjPlaybackCoordinator(
                        context,
                        DjPlaybackConfig(true, DjMixMode.Expert, mixDurationSeconds, PlaybackMode.Ordered),
                        resetPreviewState = {},
                        resetAutoDjStatus = {},
                        onExpertPlan = { plan ->
                            if (!closed) {
                                state = state.copy(currentBpm = plan.currentBpm, nextBpm = plan.incomingBpm)
                                publish(state)
                            }
                        },
                        onExpertFadeProgress = { progress ->
                            if (!closed) {
                                state = state.copy(progress = progress)
                                publish(state)
                            }
                        },
                    )
                    previewPlayback = runtime
                    val decks = DjDeckController(
                        context,
                        attachSessionPlayer = { handoffAtMs = SystemClock.elapsedRealtime() },
                        listener = { deck ->
                            val delegate = runtime.playerListener(deck)
                            object : Player.Listener by delegate {
                                override fun onPlayerError(error: PlaybackException) {
                                    delegate.onPlayerError(error)
                                    finish(true, ExpertDjPreviewPhase.Error, "No se pudo reproducir esta prueba. Comprueba que ambas canciones estén disponibles.")
                                }
                            }
                        },
                    )
                    previewDecks = decks
                    runtime.attachDecks(decks)
                    val previewPlayer = decks.activePlayer
                    previewPlayer.volume = player.volume
                    previewPlayer.setMediaItems(listOf(current, next), 0, 0L)
                    previewPlayer.prepare()
                    val startPosition = runtime.expertPreviewStartPosition(durationMs)
                    if (closed) return@withTimeout
                    previewPlayer.seekTo(startPosition)
                    val ready = awaitExpertDjPrepared(
                        timeoutMs = PREVIEW_READY_TIMEOUT_MS,
                        isReady = { previewPlayer.playbackState == Player.STATE_READY },
                        isCurrent = { !closed },
                        clockMs = SystemClock::elapsedRealtime,
                    )
                    if (!ready) {
                        finish(true, ExpertDjPreviewPhase.Error, "No se pudo preparar la prueba. Vuelve a intentarlo con dos canciones disponibles.")
                        return@withTimeout
                    }
                    state = state.copy(phase = ExpertDjPreviewPhase.Playing, message = null)
                    publish(state)
                    runtime.startMonitor()
                    previewPlayer.play()
                    while (isActive && !closed) {
                        val handoff = handoffAtMs
                        if (handoff != null && SystemClock.elapsedRealtime() - handoff >= PREVIEW_AFTER_HANDOFF_MS) {
                            finish(true, ExpertDjPreviewPhase.Finished, null)
                            return@withTimeout
                        }
                        if (decks.activePlayer.playbackState == Player.STATE_ENDED) {
                            finish(true, ExpertDjPreviewPhase.Finished, null)
                            return@withTimeout
                        }
                        delay(100L)
                    }
                }
            } catch (_: TimeoutCancellationException) {
                finish(true, ExpertDjPreviewPhase.Error, "La prueba tardó demasiado. Puedes intentarlo de nuevo.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                finish(true, ExpertDjPreviewPhase.Error, "No se pudo iniciar la prueba con estas canciones.")
            }
        }
    }

    private fun watchOriginal(player: ExoPlayer) {
        originalListener = object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) stop(false)
            }

            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                stop(false)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady) stop(false)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem != original?.item) stop(false)
            }
        }.also(player::addListener)
    }

    fun stop(restorePlayback: Boolean) = finish(restorePlayback, ExpertDjPreviewPhase.Idle, null)

    private fun finish(restorePlayback: Boolean, phase: ExpertDjPreviewPhase, message: String?) {
        if (closed) return
        closed = true
        originalListener?.let { listener -> runCatching { original?.player?.removeListener(listener) } }
        originalListener = null
        previewPlayback?.let {
            runCatching { it.stopMonitor() }
            runCatching { it.shutdownPlayers() }
        }
        previewDecks?.let { runCatching { it.release() } }
        previewPlayback?.let { runCatching { it.release() } }
        previewDecks = null
        previewPlayback = null
        scope.cancel()
        val saved = original
        runCatching {
            if (restorePlayback && saved != null && originalDecks.activePlayer === saved.player &&
                !saved.player.playWhenReady && saved.player.currentMediaItemIndex == saved.index &&
                saved.player.currentMediaItem == saved.item &&
                abs(saved.player.currentPosition - saved.positionMs) <= 1_000L &&
                saved.player.mediaItemCount == saved.queue.size &&
                saved.queue.indices.all { saved.player.getMediaItemAt(it) == saved.queue[it] }
            ) {
                saved.player.seekTo(saved.index, saved.positionMs)
                saved.player.playWhenReady = saved.playWhenReady
            }
        }
        state = state.copy(phase = phase, message = message, progress = if (phase == ExpertDjPreviewPhase.Finished) 1f else null)
        try {
            publish(state)
        } finally {
            onClosed(this)
        }
    }

    companion object {
        const val MISSING_QUEUE_MESSAGE = "Añade al menos dos canciones a la cola y selecciona una que tenga una siguiente. Desactiva Repetir canción para probar la mezcla."
        private const val PREVIEW_TIMEOUT_MS = 40_000L
        private const val PREVIEW_READY_TIMEOUT_MS = 5_000L
        private const val PREVIEW_AFTER_HANDOFF_MS = 2_500L
        private const val MIN_PREVIEW_TRACK_MS = 12_000L

        fun contextState(decks: DjDeckController, playback: DjPlaybackCoordinator): ExpertDjPreviewState {
            val current = decks.activePlayer.currentMediaItem
            val target = playback.nextPreviewTarget()
            val next = target?.mediaItems?.getOrNull(target.startIndex)
            return ExpertDjPreviewState(
                phase = if (current != null && next != null) ExpertDjPreviewPhase.Idle else ExpertDjPreviewPhase.Error,
                currentTitle = current?.title().orEmpty(),
                nextTitle = next?.title().orEmpty(),
                message = MISSING_QUEUE_MESSAGE.takeIf { current == null || next == null },
            )
        }

        private fun MediaItem.title(): String = mediaMetadata.title?.toString()?.takeIf(String::isNotBlank) ?: "Canción $mediaId"
    }
}
