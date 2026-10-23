package com.softmusic.app.player

enum class AutoDjEffect(val label: String) {
    None("Suave"),
    BassSwap("Cambio de graves"),
    FilterSweep("Filtro"),
    EchoOut("Eco de salida"),
}

data class AutoDjSettings(
    val beats: Int = 16,
    val effect: AutoDjEffect = AutoDjEffect.BassSwap,
    val intensity: Float = 0.5f,
    val syncEnabled: Boolean = true,
) {
    fun sanitized(): AutoDjSettings = copy(
        beats = beats.takeIf { it in listOf(8, 16, 32) } ?: 16,
        intensity = if (intensity.isFinite()) intensity.coerceIn(0f, 1f) else 0.5f,
    )
}

enum class AutoDjPhase {
    Idle, Analyzing, Ready, Mixing, Restoring, Fallback, Paused,
}

data class AutoDjStatus(
    val currentMediaId: String? = null,
    val nextMediaId: String? = null,
    val nextTitle: String? = null,
    val currentBpm: Float? = null,
    val nextBpm: Float? = null,
    val phase: AutoDjPhase = AutoDjPhase.Idle,
    val progress: Float = 0f,
    val incomingSpeed: Float = 1f,
    val synchronized: Boolean = false,
    val message: String? = null,
)

/** Beat positions are absolute positions in the original media, before time stretching. */
data class DjBeatProfile(
    val bpm: Float,
    val confidence: Float,
    val firstBeatMs: Double,
    val analyzedStartMs: Long,
    val analyzedEndMs: Long,
)

data class AutoDjPlan(
    val triggerPositionMs: Long,
    val incomingStartPositionMs: Long,
    val durationMs: Long,
    val incomingSpeed: Float,
    val outgoingBpm: Float,
    val incomingBpm: Float,
    val synchronized: Boolean,
)
