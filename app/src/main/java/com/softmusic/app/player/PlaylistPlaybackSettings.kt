package com.softmusic.app.player

internal data class PlaylistPlaybackSettings(val playbackMode: PlaybackMode, val smartShuffleEnabled: Boolean)

/** Refreshing global preferences must not change the active playlist's playback mode. */
internal fun playbackModeAfterPreferenceRefresh(
    source: PlaybackQueueSource,
    selectedMode: PlaybackMode?,
    serviceMode: PlaybackMode?,
    currentMode: PlaybackMode,
    hasRestoredSession: Boolean,
    defaultMode: PlaybackMode,
): PlaybackMode = if (source == PlaybackQueueSource.Playlist) {
    serviceMode ?: currentMode
} else {
    selectedMode ?: serviceMode ?: currentMode.takeIf { hasRestoredSession } ?: defaultMode
}

internal fun playbackSettingsForSource(
    source: PlaybackQueueSource,
    sourceKey: String?,
    playlistModes: Map<String, PlaylistShuffleMode>,
    defaultMode: PlaybackMode,
): PlaylistPlaybackSettings {
    if (source != PlaybackQueueSource.Playlist) return PlaylistPlaybackSettings(defaultMode, false)
    val mode = playlistModes[sourceKey] ?: PlaylistShuffleMode.Off
    return PlaylistPlaybackSettings(mode.playbackMode, mode == PlaylistShuffleMode.SmartShuffle)
}
