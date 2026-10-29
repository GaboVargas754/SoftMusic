package com.softmusic.app.player

data class ListeningHistoryWriteSnapshot(val enabled: Boolean, val revision: Long)

/** The repository serializes access together with command enqueueing. */
class ListeningHistoryWriteFence {
    var snapshot = ListeningHistoryWriteSnapshot(enabled = true, revision = 0L)
        private set

    fun setEnabled(enabled: Boolean): ListeningHistoryWriteSnapshot {
        if (snapshot.enabled != enabled) snapshot = ListeningHistoryWriteSnapshot(enabled, snapshot.revision + 1L)
        return snapshot
    }

    fun clear(): ListeningHistoryWriteSnapshot {
        snapshot = snapshot.copy(revision = snapshot.revision + 1L)
        return snapshot
    }

    fun restoreEnabled(revision: Long, enabled: Boolean): ListeningHistoryWriteSnapshot? {
        if (snapshot.revision != revision) return null
        snapshot = snapshot.copy(enabled = enabled)
        return snapshot
    }

    fun accepts(revision: Long): Boolean = snapshot.enabled && snapshot.revision == revision
}
