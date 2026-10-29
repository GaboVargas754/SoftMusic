package com.softmusic.app.player

internal data class PendingPlaybackMode(val mode: PlaybackMode, val regenerateQueue: Boolean)

/** Keeps a user's choice while the controller and saved queue are still loading. */
internal class PlaybackModeSelection {
    var selectedMode: PlaybackMode? = null
        private set
    private var pending: PendingPlaybackMode? = null
    val hasPending: Boolean get() = pending != null

    fun select(mode: PlaybackMode, currentMode: PlaybackMode, sessionMode: PlaybackMode?) {
        selectedMode = mode
        pending = PendingPlaybackMode(
            mode,
            pending?.regenerateQueue == true || currentMode == PlaybackMode.Shuffle ||
                mode == PlaybackMode.Shuffle || sessionMode == PlaybackMode.Shuffle,
        )
    }

    fun takePending(ready: Boolean): PendingPlaybackMode? {
        if (!ready) return null
        return pending.also { pending = null }
    }
}
