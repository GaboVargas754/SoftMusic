package com.softmusic.app.player

import kotlinx.coroutines.delay

/** Keeps the existing readiness timeout, including its final ready-state fallback. */
internal suspend fun awaitDjIncomingReady(
    timeoutMs: Long,
    isReady: () -> Boolean,
    playWhenReady: () -> Boolean,
    pause: suspend (Long) -> Unit = { delay(it) },
): Boolean {
    var elapsedMs = 0L
    while (elapsedMs < timeoutMs) {
        if (isReady() && playWhenReady()) return true
        pause(50L)
        elapsedMs += 50L
    }
    return isReady()
}
