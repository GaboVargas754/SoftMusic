package com.softmusic.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DjSilenceProfileTest {
    @Test
    fun aShortQuietIntroIsPreservedEvenWhenTheLoudBodyDominatesThePercentiles() {
        val windows = (0 until 188).map { index ->
            val startMs = index * 80L
            val quietIntro = startMs < 1_000L
            DjSilenceAnalyzer.AudioWindow(
                startMs = startMs,
                endMs = minOf(startMs + 80L, 15_000L),
                rms = if (quietIntro) 0.006f else 0.20f,
                peak = if (quietIntro) 0.012f else 0.4f,
            )
        }
        val profile = DjSilenceAnalyzer.AudioProfile(0L, 15_000L, windows)

        assertTrue(profile.audibleThreshold() > 0.006f)
        assertEquals(0L, profile.audibleStartMs())
    }

    @Test
    fun aShortQuietOutroIsPreservedBeforeActualDecodedSilence() {
        val windows = (0 until 200).map { index ->
            val startMs = index * 80L
            val rms = when {
                startMs < 14_000L -> 0.20f
                startMs < 15_040L -> 0.006f
                else -> 0f
            }
            DjSilenceAnalyzer.AudioWindow(startMs, startMs + 80L, rms, rms * 2f)
        }
        val profile = DjSilenceAnalyzer.AudioProfile(0L, 16_000L, windows)

        assertTrue(profile.audibleThreshold() > 0.006f)
        assertEquals(15_260L, profile.audibleEndMs(16_000L))
        assertTrue(profile.copy(windows = windows.takeLast(13), startMs = 14_960L).hasAudibleContent())
    }

    @Test
    fun onlyCompleteMeasuredSilenceCanDeclareTheScannedTailSilent() {
        val measuredSilence = DjSilenceAnalyzer.AudioProfile(90_000L, 90_080L, listOf(
            DjSilenceAnalyzer.AudioWindow(90_000L, 90_080L, rms = 0f, peak = 0f),
        ))
        assertTrue(measuredSilence.isReliable())
        assertEquals(90_000L, measuredSilence.audibleEndMs(90_080L))

        for (unusable in listOf(
            measuredSilence.copy(isComplete = false),
            measuredSilence.copy(windows = emptyList()),
            measuredSilence.copy(windows = measuredSilence.windows.map { it.copy(isMeasured = false) }),
        )) {
            assertFalse(unusable.isReliable())
            assertNull(unusable.audibleEndMs(90_080L))
            assertNull(unusable.audibleStartMs())
        }
    }

    @Test
    fun malformedMetricsAndWindowBoundsAreUnusableInsteadOfSilent() {
        val valid = DjSilenceAnalyzer.AudioProfile(0L, 80L, listOf(
            DjSilenceAnalyzer.AudioWindow(0L, 80L, rms = 0.1f, peak = 0.2f),
        ))
        val window = valid.windows.single()
        for (invalidWindow in listOf(
            window.copy(rms = Float.NaN),
            window.copy(peak = Float.POSITIVE_INFINITY),
            window.copy(rms = -0.1f),
            window.copy(endMs = 81L),
            window.copy(startMs = 80L),
        )) {
            assertFalse(valid.copy(windows = listOf(invalidWindow)).isReliable())
        }
    }
}
