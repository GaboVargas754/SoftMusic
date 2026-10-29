package com.softmusic.app.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.softmusic.app.MainActivity
import com.softmusic.app.R
import com.softmusic.app.data.ListeningHistoryRepository
import com.softmusic.app.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Android lifecycle, media session and notification boundary for music playback. */
@androidx.annotation.OptIn(UnstableApi::class)
class MusicService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var decks: DjDeckController
    private lateinit var playback: DjPlaybackCoordinator
    private lateinit var historyRecorder: ListeningHistoryRecorder
    private var expertPreview: ExpertDjPreviewSession? = null
    private val smartScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var smartQueue: SmartQueueCoordinator
    private var smartQueueMutation = false
    private var previewPreparation: Job? = null

    @UnstableApi
    override fun onCreate() {
        super.onCreate()
        createMediaNotificationChannel()
        setMediaNotificationProvider(CompactMediaNotificationProvider(this, MEDIA_NOTIFICATION_ID, MEDIA_NOTIFICATION_CHANNEL_ID))
        playback = DjPlaybackCoordinator(
            context = this,
            initialConfig = latestDjConfig,
            resetPreviewState = { _mixPreviewState.value = MixPreviewState() },
            resetAutoDjStatus = { currentMediaId -> _autoDjStatus.value = AutoDjStatus(currentMediaId = currentMediaId) },
            beforeNextTarget = { smartQueue.prepareAutomatic() },
        )
        val analyzer = SmartSongAnalyzer(this)
        smartQueue = SmartQueueCoordinator(
            scope = smartScope,
            snapshot = ::smartQueueSnapshot,
            analyze = analyzer::analyze,
            applyOrder = ::applySmartQueueOrder,
            publish = { _smartQueueState.value = it },
            canAnalyzeAutomatic = {
                val player = decks.activePlayer
                val mixingLeadMs = if (latestDjConfig.enabled && latestDjConfig.mixMode != DjMixMode.AutoDj) {
                    latestDjConfig.mixDurationSeconds * 1_000L
                } else 0L
                player.duration > 0L && player.duration - player.currentPosition > 15_000L + mixingLeadMs + 3_000L
            },
            currentIdentity = {
                val player = decks.activePlayer
                player.currentMediaItem?.mediaId?.toLongOrNull()?.let { player to it }
            },
        )
        configureSmartQueue()
        historyRecorder = ListeningHistoryRecorder(ListeningHistoryRepository.get(this))
        decks = DjDeckController(
            context = this,
            attachSessionPlayer = {
                mediaSession?.setPlayer(it)
                cancelPendingExpertPreview()
                smartQueue.currentChanged()
            },
            listener = { player ->
                historyRecorder.observe(player)
                smartQueuePlayerListener(player, playback.playerListener(player))
            },
        )
        playback.attachDecks(decks)
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSession.Builder(this, decks.activePlayer)
            .setId(MEDIA_SESSION_ID)
            .setSessionActivity(sessionActivity)
            .build()
        activeInstance = this
        playback.startMonitor()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    private fun createMediaNotificationChannel() {
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
        stopExpertPreview(restorePlayback = false)
        playback.shutdownPlayers()
        stopSelf()
    }

    override fun onDestroy() {
        stopExpertPreview(restorePlayback = false)
        playback.stopMonitor()
        smartQueue.release()
        smartScope.cancel()
        playback.shutdownPlayers()
        mediaSession?.release()
        historyRecorder.release()
        decks.release()
        playback.release()
        _autoDjStatus.value = AutoDjStatus()
        _expertPreviewState.value = ExpertDjPreviewState()
        _smartQueueState.value = SmartQueueRuntimeState(enabled = latestSmartShuffleEnabled == true)
        mediaSession = null
        if (activeInstance === this) activeInstance = null
        super.onDestroy()
    }

    private fun startExpertPreviewSession() {
        if (expertPreview != null || previewPreparation?.isActive == true) return
        val captured = smartQueueSnapshot()
        _expertPreviewState.value = ExpertDjPreviewState(phase = ExpertDjPreviewPhase.Preparing)
        val preparation = smartScope.launch(start = CoroutineStart.LAZY) {
            smartQueue.awaitPrepared()
            val live = smartQueueSnapshot()
            if (captured == null || live == null || live.owner !== captured.owner || live.currentSongId != captured.currentSongId) {
                previewPreparation = null
                _expertPreviewState.value = ExpertDjPreviewState()
                return@launch
            }
            // From this point ExpertDjPreviewSession owns its original-player pause and cleanup.
            previewPreparation = null
            ExpertDjPreviewSession(
                context = this@MusicService,
                originalDecks = decks,
                originalPlayback = playback,
                mixDurationSeconds = latestDjConfig.mixDurationSeconds,
                publish = { _expertPreviewState.value = it },
                onClosed = { closed -> if (expertPreview === closed) expertPreview = null },
            ).also {
                expertPreview = it
                it.start()
            }
        }
        previewPreparation = preparation
        preparation.start()
    }

    private fun stopExpertPreview(restorePlayback: Boolean) {
        cancelPendingExpertPreview()
        expertPreview?.stop(restorePlayback)
    }

    private fun cancelPendingExpertPreview() {
        if (previewPreparation == null) return
        previewPreparation?.cancel()
        previewPreparation = null
        if (expertPreview == null) _expertPreviewState.value = ExpertDjPreviewState()
    }

    private fun configureSmartQueue() {
        val config = latestDjConfig
        smartQueue.configure(
            enabled = latestSmartShuffleEnabled == true,
            automatic = latestSmartShuffleEnabled == true && config.playbackMode == PlaybackMode.Shuffle,
        )
    }

    private fun smartQueueSnapshot(): SmartQueueSnapshot? {
        if (!::decks.isInitialized) return null
        val player = decks.activePlayer
        val current = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return null
        val songs = List(player.mediaItemCount) { index ->
            val item = player.getMediaItemAt(index)
            val id = item.mediaId.toLongOrNull() ?: return null
            latestSmartQueueSongs[id] ?: item.smartQueueSong(id) ?: return null
        }
        return SmartQueueSnapshot(player, songs, current)
    }

    /** Replacing only pending items leaves the decoder, playing position and prefix intact. */
    private fun applySmartQueueOrder(captured: SmartQueueSnapshot, pendingIds: List<Long>): Boolean {
        smartQueueMutation = true
        return try {
            applySmartQueuePendingOrder(decks.activePlayer, captured, pendingIds)
        } finally {
            smartQueueMutation = false
        }
    }

    private fun smartQueuePlayerListener(player: ExoPlayer, delegate: Player.Listener): Player.Listener =
        object : Player.Listener by delegate {
            private var previousMediaId: String? = null

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                delegate.onTimelineChanged(timeline, reason)
                if (::decks.isInitialized && player === decks.activePlayer && !smartQueueMutation &&
                    reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED
                ) {
                    cancelPendingExpertPreview()
                    smartQueue.queueChanged()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                delegate.onMediaItemTransition(mediaItem, reason)
                val previous = previousMediaId
                previousMediaId = mediaItem?.mediaId
                if (!::decks.isInitialized || player !== decks.activePlayer || smartQueueMutation) return
                cancelPendingExpertPreview()
                // Media3's repeat-all wrap also needs a fresh cycle while the activity is absent.
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && latestDjConfig.playbackMode == PlaybackMode.Shuffle &&
                    player.mediaItemCount > 1 && player.currentMediaItemIndex == 0 &&
                    previous == player.getMediaItemAt(player.mediaItemCount - 1).mediaId
                ) {
                    smartQueueSnapshot()?.let { captured -> applySmartQueueOrder(captured, captured.pendingSongs.map { it.id }.shuffled()) }
                    smartQueue.startNewQueue()
                } else {
                    smartQueue.currentChanged()
                }
            }

            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                delegate.onPositionDiscontinuity(oldPosition, newPosition, reason)
                if (::decks.isInitialized && player === decks.activePlayer && !smartQueueMutation &&
                    (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT)
                ) {
                    cancelPendingExpertPreview()
                    smartQueue.cancelForTransport()
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                delegate.onPlayWhenReadyChanged(playWhenReady, reason)
                if (!playWhenReady && ::decks.isInitialized && player === decks.activePlayer) {
                    cancelPendingExpertPreview()
                    smartQueue.cancelForTransport()
                }
            }
        }

    private fun MediaItem.smartQueueSong(id: Long): Song? {
        val uri = localConfiguration?.uri?.toString() ?: return null
        return Song(id, mediaMetadata.title?.toString().orEmpty(), mediaMetadata.artist?.toString().orEmpty(),
            mediaMetadata.albumTitle?.toString().orEmpty(), mediaMetadata.durationMs ?: 0L, 0L, 0L, "", "", uri,
            mediaMetadata.artworkUri?.toString())
    }

    companion object {
        private val _mixPreviewState = MutableStateFlow(MixPreviewState())
        val mixPreviewState: StateFlow<MixPreviewState> = _mixPreviewState.asStateFlow()
        private val _expertPreviewState = MutableStateFlow(ExpertDjPreviewState())
        val expertPreviewState: StateFlow<ExpertDjPreviewState> = _expertPreviewState.asStateFlow()
        val currentPlaybackMode: PlaybackMode? get() = activeInstance?.playback?.sessionPlaybackMode()
        internal val currentQueueOrigin: Pair<PlaybackQueueSource, String?>?
            get() = latestQueueOrigin.takeIf { currentPlaybackMode != null }
        private val _autoDjStatus = MutableStateFlow(AutoDjStatus())
        val autoDjStatus: StateFlow<AutoDjStatus> = _autoDjStatus.asStateFlow()
        private val _smartQueueState = MutableStateFlow(SmartQueueRuntimeState())
        internal val smartQueueState: StateFlow<SmartQueueRuntimeState> = _smartQueueState.asStateFlow()
        private var latestSmartShuffleEnabled: Boolean? = null
        private var latestQueueOrigin: Pair<PlaybackQueueSource, String?>? = null
        private var latestSmartQueueSongs: Map<Long, Song> = emptyMap()
        private var latestAutoDjSettings = AutoDjSettings()
        private var activeInstance: MusicService? = null
        private var latestDjConfig = DjPlaybackConfig(
            enabled = false,
            mixMode = DjMixMode.Classic,
            mixDurationSeconds = 8,
            playbackMode = PlaybackMode.Ordered,
        )

        fun stopMixPreview(restorePlayback: Boolean = true) {
            activeInstance?.stopExpertPreview(restorePlayback)
            activeInstance?.playback?.stopMixPreview(restorePlayback)
        }

        fun startExpertPreview() {
            val service = activeInstance
            if (service == null) {
                _expertPreviewState.value = ExpertDjPreviewState(
                    phase = ExpertDjPreviewPhase.Error,
                    message = ExpertDjPreviewSession.MISSING_QUEUE_MESSAGE,
                )
            } else {
                service.startExpertPreviewSession()
            }
        }

        fun refreshExpertPreviewContext() {
            val service = activeInstance
            if (service?.expertPreview != null || service?.previewPreparation?.isActive == true) return
            _expertPreviewState.value = if (service == null) {
                ExpertDjPreviewState(phase = ExpertDjPreviewPhase.Error, message = ExpertDjPreviewSession.MISSING_QUEUE_MESSAGE)
            } else {
                ExpertDjPreviewSession.contextState(service.decks, service.playback)
            }
        }

        fun updateDjConfig(enabled: Boolean, mixMode: DjMixMode, mixDurationSeconds: Int, playbackMode: PlaybackMode) {
            val config = DjPlaybackConfig(
                enabled = enabled,
                mixMode = mixMode,
                mixDurationSeconds = mixDurationSeconds,
                playbackMode = playbackMode,
            )
            val previousConfig = latestDjConfig
            latestDjConfig = config
            if (previousConfig != config) activeInstance?.stopExpertPreview(restorePlayback = true)
            activeInstance?.playback?.applyConfig(config)
            activeInstance?.configureSmartQueue()
        }

        fun updateSmartShuffleEnabled(enabled: Boolean) {
            if (latestSmartShuffleEnabled == enabled) return
            latestSmartShuffleEnabled = enabled
            activeInstance?.let { service ->
                service.stopExpertPreview(restorePlayback = true)
                service.playback.invalidatePendingTarget()
                service.configureSmartQueue()
            }
            if (activeInstance == null) _smartQueueState.value = SmartQueueRuntimeState(enabled = enabled)
        }

        fun updateSmartQueueSongs(songs: List<Song>) {
            latestSmartQueueSongs = songs.associateBy { it.id }
        }

        internal fun updateQueueOrigin(source: PlaybackQueueSource, sourceKey: String?) {
            latestQueueOrigin = source to sourceKey
        }

        fun smartOrderQueue() {
            val service = activeInstance ?: return
            service.stopExpertPreview(restorePlayback = true)
            service.playback.cancelForExpertPreview()
            service.smartQueue.orderRemaining()
        }

        fun markSmartQueueEdited() { activeInstance?.smartQueue?.markManualEdit() }
        fun startNewSmartQueue() { activeInstance?.smartQueue?.startNewQueue() }
        fun cancelSmartQueueForTransport() { activeInstance?.smartQueue?.cancelForTransport() }

        fun updateAutoDjSettings(settings: AutoDjSettings) {
            val safeSettings = settings.sanitized()
            latestAutoDjSettings = safeSettings
        }

        private const val MEDIA_NOTIFICATION_ID = 2101
        private const val MEDIA_NOTIFICATION_CHANNEL_ID = "softmusic_media_playback"
        private const val MEDIA_SESSION_ID = "softmusic-media-session"
    }
}
