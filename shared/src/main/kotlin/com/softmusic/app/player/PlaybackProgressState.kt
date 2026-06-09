package com.softmusic.app.player

data class PlaybackProgressState(
    val durationMs: Long = 0,
    val positionMs: Long = 0,
)
