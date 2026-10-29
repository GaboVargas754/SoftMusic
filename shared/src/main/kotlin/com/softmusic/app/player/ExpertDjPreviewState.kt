package com.softmusic.app.player

/** State of the temporary audition of the current song and its queued successor. */
data class ExpertDjPreviewState(
    val phase: ExpertDjPreviewPhase = ExpertDjPreviewPhase.Idle,
    val currentTitle: String = "",
    val nextTitle: String = "",
    val message: String? = null,
    val progress: Float? = null,
    val currentBpm: Float? = null,
    val nextBpm: Float? = null,
)

enum class ExpertDjPreviewPhase { Idle, Preparing, Playing, Finished, Error }
