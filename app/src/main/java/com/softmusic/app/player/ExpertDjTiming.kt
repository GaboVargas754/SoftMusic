package com.softmusic.app.player

import kotlinx.coroutines.delay

/** Limits a live fade using the position observed after the incoming deck has buffered. */
internal object ExpertDjTiming {
    fun availableFadeMs(plan: ExpertDjPlan, physicalEndMs: Long, outgoingPositionMs: Long): Long? {
        val positionMs = outgoingPositionMs.coerceAtLeast(0L)
        return minOf(
            plan.mixDurationMs,
            plan.audibleEndPositionMs - positionMs - AUDIBLE_END_RESERVE_MS,
            physicalEndMs - positionMs - PHYSICAL_END_RESERVE_MS,
        ).takeIf { it >= MIN_FADE_MS }
    }

    const val MIN_FADE_MS = 1_000L
    private const val AUDIBLE_END_RESERVE_MS = 100L
    private const val PHYSICAL_END_RESERVE_MS = 700L
}

/** Readiness is checked while paused, so buffering cannot consume the incoming intro. */
internal suspend fun awaitExpertDjPrepared(
    timeoutMs: Long,
    isReady: () -> Boolean,
    isCurrent: () -> Boolean,
    clockMs: () -> Long = ::djMonotonicTimeMs,
    pause: suspend (Long) -> Unit = { delay(it) },
): Boolean {
    val startedMs = clockMs()
    while (isCurrent()) {
        val elapsedMs = (clockMs() - startedMs).coerceAtLeast(0L)
        if (elapsedMs > timeoutMs) return false
        if (isReady()) return isCurrent()
        val remainingMs = timeoutMs - elapsedMs
        if (remainingMs <= 0L) return false
        pause(minOf(EXPERT_DJ_FRAME_MS, remainingMs))
    }
    return false
}

/** Scheduling stalls advance the envelope to elapsed time instead of extending the song. */
internal suspend fun runExpertDjFade(
    durationMs: Long,
    isCurrent: () -> Boolean,
    applyProgress: (Float) -> Boolean,
    clockMs: () -> Long = ::djMonotonicTimeMs,
    pause: suspend (Long) -> Unit = { delay(it) },
): Boolean {
    if (durationMs < ExpertDjTiming.MIN_FADE_MS) return false
    val startedMs = clockMs()
    while (isCurrent()) {
        val elapsedMs = (clockMs() - startedMs).coerceAtLeast(0L)
        val progress = (elapsedMs.toDouble() / durationMs).coerceIn(0.0, 1.0).toFloat()
        if (!applyProgress(progress)) return false
        if (elapsedMs >= durationMs) return isCurrent()
        pause(minOf(EXPERT_DJ_FRAME_MS, durationMs - elapsedMs))
    }
    return false
}

private fun djMonotonicTimeMs(): Long = System.nanoTime() / 1_000_000L
private const val EXPERT_DJ_FRAME_MS = 20L
