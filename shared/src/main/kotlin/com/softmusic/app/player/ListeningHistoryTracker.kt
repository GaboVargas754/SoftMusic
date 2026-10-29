package com.softmusic.app.player

data class ListeningHistoryPlay(val songId: Long, val playedAtMs: Long)

fun listeningHistoryThresholdMs(durationMs: Long): Long =
    if (durationMs > 0L) minOf(30_000L, (durationMs / 2L).coerceAtLeast(1L)) else 30_000L

/** One instance follows one physical player, including its muted preroll and later DJ handoff. */
class ListeningHistoryTracker {
    private data class Visit(
        val songId: Long,
        var durationMs: Long,
        var audibleMs: Long = 0L,
        var qualified: Boolean = false,
    )

    private var visit: Visit? = null
    private var lastElapsedMs: Long? = null
    private var wasAudible = false

    fun reset() {
        visit = null
        lastElapsedMs = null
        wasAudible = false
    }

    fun observe(
        songId: Long?,
        durationMs: Long,
        audible: Boolean,
        elapsedRealtimeMs: Long,
        wallClockMs: Long,
        startsNewVisit: Boolean = false,
    ): List<ListeningHistoryPlay> {
        val plays = mutableListOf<ListeningHistoryPlay>()
        val previous = visit
        if (previous != null) {
            val elapsed = lastElapsedMs?.let { (elapsedRealtimeMs - it).coerceAtLeast(0L) } ?: 0L
            if (wasAudible && !previous.qualified) {
                previous.audibleMs = (previous.audibleMs + elapsed.coerceAtMost(30_000L)).coerceAtMost(30_000L)
            }
            if (previous.songId == songId && !startsNewVisit && durationMs > 0L) previous.durationMs = durationMs
            if (!previous.qualified && previous.audibleMs >= listeningHistoryThresholdMs(previous.durationMs)) {
                previous.qualified = true
                plays += ListeningHistoryPlay(previous.songId, wallClockMs.coerceAtLeast(0L))
            }
        }

        if (songId == null) visit = null
        else if (previous?.songId != songId || startsNewVisit) visit = Visit(songId, durationMs)
        lastElapsedMs = elapsedRealtimeMs
        wasAudible = audible && songId != null
        return plays
    }
}
