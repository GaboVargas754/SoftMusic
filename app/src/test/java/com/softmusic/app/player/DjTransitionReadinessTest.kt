package com.softmusic.app.player

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DjTransitionReadinessTest {
    @Test
    fun preparedPlayingIncomingDeckDoesNotDelayTheFade() = runBlocking {
        var polls = 0
        assertTrue(awaitDjIncomingReady(1_500L, { true }, { true }, { polls++ }))
        assertEquals(0, polls)
    }

    @Test
    fun bufferedIncomingDeckCanBecomeReadyDuringTheTimeout() = runBlocking {
        var elapsedMs = 0L
        assertTrue(awaitDjIncomingReady(1_500L, { elapsedMs >= 100L }, { true }, { elapsedMs += it }))
        assertEquals(100L, elapsedMs)
    }

    @Test
    fun bufferingPastTheTimeoutFallsBackToNormalPlayback() = runBlocking {
        var elapsedMs = 0L
        assertFalse(awaitDjIncomingReady(1_500L, { false }, { true }, { elapsedMs += it }))
        assertEquals(1_500L, elapsedMs)
    }

    @Test
    fun finalReadyStateStillPassesWhenPlayWhenReadyIsFalse() = runBlocking {
        var elapsedMs = 0L
        assertTrue(awaitDjIncomingReady(1_500L, { true }, { false }, { elapsedMs += it }))
        assertEquals(1_500L, elapsedMs)
    }
}
