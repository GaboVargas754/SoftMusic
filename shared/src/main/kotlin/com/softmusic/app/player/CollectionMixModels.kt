package com.softmusic.app.player

/** A collection's consent is independent of the legacy global crossfade preference. */
fun collectionMixKey(source: PlaybackQueueSource, sourceKey: String? = null): String? = when (source) {
    PlaybackQueueSource.Favorites -> "favorites"
    PlaybackQueueSource.Playlist -> sourceKey?.takeIf { it.isNotBlank() }?.let { "playlist:$it" }
    else -> null
}

fun mixPairKey(outgoingId: Long, incomingId: Long): String = "$outgoingId>$incomingId"

enum class MixTransitionMode { Automatic, Custom, Off }

data class MixEq(val lowDb: Float = 0f, val midDb: Float = 0f, val highDb: Float = 0f) {
    fun sanitized() = MixEq(lowDb.safe(-12f, 6f, 0f), midDb.safe(-12f, 6f, 0f), highDb.safe(-12f, 6f, 0f))
}

data class MixTransitionSettings(
    val mode: MixTransitionMode = MixTransitionMode.Automatic,
    val durationMs: Long = 8_000L,
    // Absolute cue positions; null lets the engine select the cue.
    val outgoingStartMs: Long? = null,
    val incomingStartMs: Long? = null,
    val syncEnabled: Boolean = true,
    val effect: AutoDjEffect = AutoDjEffect.BassSwap,
    val intensity: Float = .5f,
    val outgoingVolume: Float = 1f,
    val incomingVolume: Float = 1f,
    val outgoingEq: MixEq = MixEq(),
    val incomingEq: MixEq = MixEq(),
) {
    fun sanitized() = copy(
        durationMs = durationMs.coerceIn(1_000L, 24_000L),
        outgoingStartMs = outgoingStartMs?.coerceAtLeast(0),
        incomingStartMs = incomingStartMs?.coerceAtLeast(0),
        intensity = intensity.safe(0f, 1f, .5f),
        outgoingVolume = outgoingVolume.safe(0f, 1f, 1f),
        incomingVolume = incomingVolume.safe(0f, 1f, 1f),
        outgoingEq = outgoingEq.sanitized(), incomingEq = incomingEq.sanitized(),
    )
}

data class CollectionMixConfig(
    val accepted: Boolean = false,
    val enabled: Boolean = false,
    val settings: AutoDjSettings = AutoDjSettings(),
    // A separate playback order; never changes the playlist's stored songIds.
    val orderedSongIds: List<Long> = emptyList(),
    val transitions: Map<String, MixTransitionSettings> = emptyMap(),
) {
    val isEnabled: Boolean get() = accepted && enabled
    fun transition(outgoingId: Long, incomingId: Long) = transitions[mixPairKey(outgoingId, incomingId)]
        ?: MixTransitionSettings()
    fun sanitized() = copy(enabled = accepted && enabled, settings = settings.sanitized(),
        orderedSongIds = orderedSongIds.filter { it > 0L }.distinct(),
        transitions = transitions.mapValues { it.value.sanitized() })
}

data class CollectionMixContext(
    val source: PlaybackQueueSource,
    val sourceKey: String? = null,
    val songIds: Set<Long>,
    val config: CollectionMixConfig,
) {
    val key: String? get() = collectionMixKey(source, sourceKey)
    fun permits(playbackMode: PlaybackMode): Boolean = key != null && config.isEnabled &&
        playbackMode != PlaybackMode.Shuffle && playbackMode != PlaybackMode.RepeatCurrent
    fun permitsPair(outgoingId: Long, incomingId: Long, playbackMode: PlaybackMode): Boolean =
        permits(playbackMode) && outgoingId in songIds && incomingId in songIds && outgoingId != incomingId
}

data class MixAnalysisState(
    val collectionKey: String? = null,
    val running: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val profiles: Map<Long, DjBeatProfile> = emptyMap(),
    val suggestedOrder: List<Long>? = null,
    val message: String? = null,
)

data class MixPreviewState(
    val active: Boolean = false,
    val outgoingId: Long? = null,
    val incomingId: Long? = null,
    val message: String? = null,
)

sealed interface CollectionMixAction {
    data class SetEnabled(val key: String, val enabled: Boolean, val accept: Boolean = false) : CollectionMixAction
    data class UpdateSettings(val key: String, val settings: AutoDjSettings) : CollectionMixAction
    data class PlayInOrder(val key: String) : CollectionMixAction
    data class SuggestOrder(val key: String) : CollectionMixAction
    data object CancelAnalysis : CollectionMixAction
    data class ApplyOrder(val key: String) : CollectionMixAction
    data class RestoreOrder(val key: String) : CollectionMixAction
    data class SaveTransition(val key: String, val outgoingId: Long, val incomingId: Long,
        val settings: MixTransitionSettings?) : CollectionMixAction
    data class Preview(val key: String, val outgoingId: Long, val incomingId: Long,
        val settings: MixTransitionSettings) : CollectionMixAction
    data object StopPreview : CollectionMixAction
}

private fun Float.safe(min: Float, max: Float, fallback: Float) = if (isFinite()) coerceIn(min, max) else fallback
