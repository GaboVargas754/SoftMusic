package com.softmusic.app.player

import com.softmusic.app.data.Song

internal data class PlaybackSessionSnapshot(
    val queueIds: List<Long>,
    val currentSongId: Long,
    val positionMs: Long,
    val playbackMode: PlaybackMode?,
    val source: PlaybackQueueSource = PlaybackQueueSource.Songs,
    val sourceKey: String? = null,
)

internal interface PlaybackSessionStore {
    fun loadPlaybackSession(): PlaybackSessionSnapshot?
    fun savePlaybackSession(session: PlaybackSessionSnapshot)
    fun clearPlaybackSession()
}

internal fun interface PlaybackSessionClock {
    fun currentTimeMillis(): Long
}

internal data class PlaybackSessionRestore(
    val queue: List<Song>,
    val currentIndex: Int,
    val positionMs: Long,
    val playbackMode: PlaybackMode,
    val source: PlaybackQueueSource,
    val sourceKey: String?,
)

/** Owns restoration readiness and write policy; neither operation reads mutable UI/player state. */
internal class PlaybackSessionCoordinator(
    private val store: PlaybackSessionStore,
    private val clock: PlaybackSessionClock = PlaybackSessionClock(System::currentTimeMillis),
    private val saveIntervalMs: Long = 2_000L,
) {
    var hasRestoredSession = false
        private set
    private var lastSaveTimeMs = 0L
    private var lastSnapshot: PlaybackSessionSnapshot? = null

    fun matchingTimelineSession(mediaIds: List<String>, currentMediaId: String?): PlaybackSessionSnapshot? =
        store.loadPlaybackSession()?.takeIf { saved ->
            playbackQueueOriginMatchesTimeline(saved.queueIds, saved.currentSongId, mediaIds, currentMediaId)
        }

    fun restore(
        playerHasItems: Boolean,
        songs: List<Song>,
        fallbackMode: PlaybackMode,
    ): PlaybackSessionRestore? {
        if (hasRestoredSession || playerHasItems || songs.isEmpty()) return null
        val session = store.loadPlaybackSession()
        hasRestoredSession = true
        session ?: return null
        val songsById = songs.associateBy { it.id }
        val queue = session.queueIds.mapNotNull(songsById::get).distinctSongsById()
        if (queue.isEmpty()) return null
        val index = queue.indexOfFirst { it.id == session.currentSongId }.takeIf { it >= 0 } ?: 0
        val position = session.positionMs.coerceAtLeast(0L)
            .coerceAtMost(queue[index].durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
        return PlaybackSessionRestore(queue, index, position, session.playbackMode ?: fallbackMode, session.source, session.sourceKey)
    }

    fun scheduleSave(snapshot: PlaybackSessionSnapshot?, queueHasItems: Boolean, force: Boolean = false) {
        if (!queueHasItems) return
        val now = clock.currentTimeMillis()
        if (!force && now - lastSaveTimeMs < saveIntervalMs) return
        lastSaveTimeMs = now
        save(snapshot)
    }

    fun save(snapshot: PlaybackSessionSnapshot?) {
        snapshot ?: return
        if (snapshot == lastSnapshot) return
        val stableSnapshot = snapshot.copy(queueIds = snapshot.queueIds.toList())
        lastSnapshot = stableSnapshot
        store.savePlaybackSession(stableSnapshot)
    }

    fun clear() {
        store.clearPlaybackSession()
        lastSnapshot = null
        hasRestoredSession = true
    }
}

internal fun ActivePlaybackQueueSnapshot.playbackSessionSnapshot(
    currentSongId: Long?,
    positionMs: Long,
    playbackMode: PlaybackMode,
): PlaybackSessionSnapshot? {
    if (currentSongId == null || currentSongId !in idSet) return null
    return PlaybackSessionSnapshot(ids, currentSongId, positionMs.coerceAtLeast(0L), playbackMode, source, sourceKey)
}
