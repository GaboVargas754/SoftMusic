package com.softmusic.app.player

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.softmusic.app.data.ListeningHistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Follows physical decks so an audible DJ overlap and its later handoff form one visit. */
internal class ListeningHistoryRecorder(private val repository: ListeningHistoryRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private data class Deck(val listener: Player.Listener, val identity: ListeningHistoryVisitIdentity)
    private val decks = mutableMapOf<Player, Deck>()
    private val tracking = ListeningHistoryTrackingSession<Player>()

    init {
        scope.launch { repository.state.collect { sampleAll() } }
        scope.launch {
            while (isActive) {
                sampleAll()
                delay(500L)
            }
        }
    }

    fun observe(player: Player) {
        if (decks.containsKey(player)) return
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = sample(player)

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) =
                sample(player, repeated = reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT,
                    playlistChanged = reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)

            override fun onTimelineChanged(timeline: Timeline, reason: Int) =
                sample(player, playlistChanged = reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
        }
        decks[player] = Deck(listener, ListeningHistoryVisitIdentity())
        player.addListener(listener)
        sample(player)
    }

    private fun syncSettings(): ListeningHistoryState {
        val state = repository.state.value
        val trackingEnabled = state.enabled && !state.isLoading && state.errorMessage == null
        tracking.configure(trackingEnabled, state.trackingRevision)
        return state
    }

    private fun sampleAll() {
        syncSettings()
        decks.keys.toList().forEach { sample(it) }
    }

    private fun sample(player: Player, repeated: Boolean = false, playlistChanged: Boolean = false) {
        val state = syncSettings()
        val deck = decks[player] ?: return
        val songId = player.currentMediaItem?.mediaId?.toLongOrNull()
        val timeline = player.currentTimeline
        val index = player.currentMediaItemIndex
        val uid = if (index in 0 until timeline.windowCount) timeline.getWindow(index, Timeline.Window()).uid else null
        val startsNewVisit = deck.identity.observe(songId, uid, player.currentPosition, playlistChanged, repeated)
        tracking.observe(
            deck = player,
            songId = songId,
            durationMs = player.duration,
            audible = player.isPlaying && player.volume > 0f,
            elapsedRealtimeMs = SystemClock.elapsedRealtime(),
            wallClockMs = System.currentTimeMillis(),
            startsNewVisit = startsNewVisit,
        ).forEach { repository.record(it, state.trackingRevision) }
    }

    fun release() {
        sampleAll()
        scope.cancel()
        decks.forEach { (player, deck) -> player.removeListener(deck.listener) }
        decks.clear()
        tracking.reset()
    }
}
