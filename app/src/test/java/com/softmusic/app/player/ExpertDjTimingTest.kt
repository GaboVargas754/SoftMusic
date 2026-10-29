package com.softmusic.app.player

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpertDjTimingTest {
    @Test
    fun preparationTimeIsSubtractedFromTheAudibleWindow() {
        val plan = plan(audibleEndMs = 100_000L)
        assertEquals(7_900L, ExpertDjTiming.availableFadeMs(plan, 140_000L, 92_000L))
        assertEquals(6_400L, ExpertDjTiming.availableFadeMs(plan, 140_000L, 93_500L))
        assertEquals(1_000L, ExpertDjTiming.availableFadeMs(plan, 140_000L, 98_900L))
    }

    @Test
    fun shortOrExpiredAudibleWindowsNeverExpandToAnUnsafeFade() {
        val plan = plan(audibleEndMs = 100_000L)
        assertNull(ExpertDjTiming.availableFadeMs(plan, 140_000L, 98_901L))
        assertNull(ExpertDjTiming.availableFadeMs(plan, 140_000L, 100_000L))
        assertNull(ExpertDjTiming.availableFadeMs(plan, 140_000L, 110_000L))
        assertEquals(7_300L, ExpertDjTiming.availableFadeMs(plan, 100_000L, 92_000L))
    }

    @Test
    fun schedulingStallsAdvanceTheEnvelopeWithoutAddingAnotherFullFade() = runBlocking {
        var clockMs = 0L
        val progress = mutableListOf<Float>()
        var waits = 0
        assertTrue(runExpertDjFade(
            durationMs = 2_000L,
            isCurrent = { true },
            applyProgress = { progress += it; true },
            clockMs = { clockMs },
            pause = {
                assertTrue(it <= 20L)
                waits++
                clockMs += if (waits == 1) 1_500L else 500L
            },
        ))
        assertEquals(listOf(0f, 0.75f, 1f), progress)
        assertEquals(2, waits)
        assertEquals(2_000L, clockMs)
    }

    @Test
    fun aStalledClockDoesNotInventProgressFromTheNumberOfUpdates() = runBlocking {
        var clockMs = 0L
        var updates = 0
        val progress = mutableListOf<Float>()
        assertTrue(runExpertDjFade(
            durationMs = 1_000L,
            isCurrent = { true },
            applyProgress = { progress += it; true },
            clockMs = { clockMs },
            pause = { if (++updates == 3) clockMs = 1_000L },
        ))
        assertEquals(listOf(0f, 0f, 0f, 1f), progress)
    }

    @Test
    fun cancellationAfterAWaitPreventsFurtherGainWritesAndCompletion() = runBlocking {
        var current = true
        var clockMs = 0L
        val progress = mutableListOf<Float>()
        assertFalse(runExpertDjFade(
            durationMs = 2_000L,
            isCurrent = { current },
            applyProgress = { progress += it; true },
            clockMs = { clockMs },
            pause = { clockMs += 2_000L; current = false },
        ))
        assertEquals(listOf(0f), progress)
    }

    @Test
    fun gainGuardRejectionStopsTheEnvelopeImmediately() = runBlocking {
        var writes = 0
        assertFalse(runExpertDjFade(2_000L, { true }, { writes++; false }, { 0L }, { error("Unexpected wait") }))
        assertEquals(1, writes)
        assertFalse(runExpertDjFade(999L, { true }, { error("Unsafe fade write") }))
    }

    @Test
    fun preparedPausedIncomingDeckDoesNotWaitForPlayWhenReady() = runBlocking {
        assertTrue(awaitExpertDjPrepared(1_500L, { true }, { true }, { 0L }, { error("Unexpected wait") }))
    }

    @Test
    fun readinessTimeoutUsesRealElapsedTimeWhenTheMainThreadStalls() = runBlocking {
        var clockMs = 0L
        var waits = 0
        assertFalse(awaitExpertDjPrepared(1_500L, { false }, { true }, { clockMs }, {
            waits++
            clockMs += 2_000L
        }))
        assertEquals(1, waits)
    }

    @Test
    fun readinessThatArrivesAfterTheDeadlineDoesNotResumeAFade() = runBlocking {
        var clockMs = 0L
        assertFalse(awaitExpertDjPrepared(1_500L, { clockMs >= 2_000L }, { true }, { clockMs }, {
            clockMs = 2_000L
        }))
    }

    @Test
    fun aLateReadyResultCannotResumeCancelledPreparation() = runBlocking {
        var current = true
        var ready = false
        assertFalse(awaitExpertDjPrepared(1_500L, { ready }, { current }, { 0L }, {
            ready = true
            current = false
        }))
    }

    private fun plan(audibleEndMs: Long) = ExpertDjPlan(
        triggerPositionMs = audibleEndMs - 8_000L,
        mixDurationMs = 8_000L,
        preloadPositionMs = audibleEndMs - 15_000L,
        audibleEndPositionMs = audibleEndMs,
    )
}
