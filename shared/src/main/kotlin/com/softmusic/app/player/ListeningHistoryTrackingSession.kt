package com.softmusic.app.player

/** Applies clear/toggle barriers to all physical decks together. */
class ListeningHistoryTrackingSession<D : Any> {
    private val trackers = mutableMapOf<D, ListeningHistoryTracker>()
    private var enabled = false
    private var revision = Long.MIN_VALUE

    fun configure(enabled: Boolean, trackingRevision: Long) {
        if (this.enabled != enabled || revision != trackingRevision) trackers.clear()
        this.enabled = enabled
        revision = trackingRevision
    }

    fun observe(
        deck: D,
        songId: Long?,
        durationMs: Long,
        audible: Boolean,
        elapsedRealtimeMs: Long,
        wallClockMs: Long,
        startsNewVisit: Boolean = false,
    ): List<ListeningHistoryPlay> {
        if (!enabled) return emptyList()
        return trackers.getOrPut(deck) { ListeningHistoryTracker() }.observe(
            songId, durationMs, audible, elapsedRealtimeMs, wallClockMs, startsNewVisit,
        )
    }

    fun reset() = trackers.clear()
}
