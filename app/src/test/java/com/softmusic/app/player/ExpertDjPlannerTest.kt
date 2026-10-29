package com.softmusic.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpertDjPlannerTest {
    @Test
    fun missingOrUnusableOutgoingAnalysisLeavesClassicFallbackAvailable() {
        assertNull(ExpertDjPlanner.plan(100_000L, 8_000L, null, null))
        assertNull(ExpertDjPlanner.plan(100_000L, 8_000L, profile(0L, 100_000L), null))

        val fallback = ExpertDjPlanner.classic(100_000L, 8_000L)
        assertEquals(92_000L, fallback.triggerPositionMs)
        assertEquals(8_000L, fallback.mixDurationMs)
        assertEquals(85_000L, fallback.preloadPositionMs)
        assertEquals(100_000L, fallback.audibleEndPositionMs)
        assertEquals(0L, fallback.incomingStartPositionMs)
        assertEquals(1f, fallback.incomingStartVolumeScale, 0f)
        assertEquals(1f, fallback.incomingLevelGain, 0f)
        assertEquals(DjTransitionCurve.Classic, fallback.transitionCurve)
    }

    @Test
    fun silenceAtTheThresholdUsesTheShortHandoffAndSilenceCurve() {
        val next = profile(0L, 5_000L, window(0L, 5_000L))
        val atThreshold = ExpertDjPlanner.plan(100_000L, 8_000L,
            profile(90_000L, 100_000L, window(90_000L, 98_280L)), next)!!
        val belowThreshold = ExpertDjPlanner.plan(100_000L, 8_000L,
            profile(90_000L, 100_000L, window(90_000L, 98_281L)), next)!!

        assertEquals(3_500L, atThreshold.mixDurationMs)
        assertEquals(95_000L, atThreshold.triggerPositionMs)
        assertEquals(DjTransitionCurve.ExpertSilence, atThreshold.transitionCurve)
        assertEquals(8_000L, belowThreshold.mixDurationMs)
        assertEquals(DjTransitionCurve.ExpertNormal, belowThreshold.transitionCurve)
    }

    @Test
    fun fullySilentScannedTailUsesItsOldestKnownPosition() {
        val plan = ExpertDjPlanner.plan(100_000L, 8_000L,
            profile(90_000L, 100_000L, window(90_000L, 100_000L, 0f, 0f)), null)!!

        assertEquals(86_500L, plan.triggerPositionMs)
        assertEquals(3_500L, plan.mixDurationMs)
        assertEquals(90_000L, plan.audibleEndPositionMs)
        assertEquals(79_500L, plan.preloadPositionMs)
        assertEquals(0L, plan.incomingStartPositionMs)
        assertEquals(1f, plan.incomingStartVolumeScale, 0f)
        assertEquals(1f, plan.incomingLevelGain, 0f)
        assertEquals(DjTransitionCurve.ExpertNormal, plan.transitionCurve)
    }

    @Test
    fun normalBlendFindsALowEnergyPocketWithinTheSearchWindow() {
        val windows = (180 until 200).map { index ->
            val start = index * 500L
            val rms = if (start in 94_000L..95_000L) 0.005f else 0.1f
            window(start, start + 500L, rms, rms)
        }
        val plan = ExpertDjPlanner.plan(100_000L, 8_000L,
            DjSilenceAnalyzer.AudioProfile(90_000L, 100_000L, windows), null)!!

        assertTrue(plan.triggerPositionMs in 94_000L..95_000L)
        assertEquals(100_000L, plan.triggerPositionMs + plan.mixDurationMs)
        assertTrue(plan.mixDurationMs in 3_000L..8_000L)
    }

    @Test
    fun strongIncomingAttackKeepsMorePrerollWithoutSeekingBeforeZero() {
        val outgoing = profile(90_000L, 100_000L, window(90_000L, 100_000L))
        fun incoming(peak: Float) = profile(0L, 5_000L,
            window(0L, 1_999L, 0f, 0f), window(2_000L, 5_000L, 0.1f, peak))
        val strong = ExpertDjPlanner.plan(100_000L, 8_000L, outgoing, incoming(0.5f))!!
        val normal = ExpertDjPlanner.plan(100_000L, 8_000L, outgoing, incoming(0.1f))!!

        assertEquals(1_260L, strong.incomingStartPositionMs)
        assertEquals(1_560L, normal.incomingStartPositionMs)
        assertEquals(0L, ExpertDjPlanner.plan(100_000L, 8_000L, outgoing,
            profile(0L, 5_000L, window(0L, 5_000L, peak = 0.5f)))!!.incomingStartPositionMs)
    }

    @Test
    fun levelMatchingReducesLoudIncomingAudioAndNeverBoostsQuietAudio() {
        fun plan(outgoingRms: Float, incomingRms: Float) = ExpertDjPlanner.plan(100_000L, 8_000L,
            profile(90_000L, 100_000L, window(90_000L, 100_000L, outgoingRms)),
            profile(0L, 5_000L, window(0L, 5_000L, incomingRms)))!!

        assertEquals(0.70f, plan(0.02f, 0.2f).incomingStartVolumeScale, 0.00001f)
        assertEquals(1f, plan(0.2f, 0.02f).incomingStartVolumeScale, 0f)
        assertEquals(1f, plan(0.001f, 0.2f).incomingStartVolumeScale, 0f)
    }

    @Test
    fun classicFallbackKeepsTheMinimumFadeAndNonnegativePreload() {
        assertEquals(1_000L, ExpertDjPlanner.classic(500L, 500L).mixDurationMs)
        assertEquals(0L, ExpertDjPlanner.classic(500L, 500L).preloadPositionMs)
    }

    @Test
    fun incompleteOutgoingAnalysisCannotPlanAnEarlyCut() {
        val incomplete = DjSilenceAnalyzer.AudioProfile(90_000L, 100_000L,
            listOf(window(90_000L, 100_000L, 0f, 0f)), isComplete = false)
        assertNull(ExpertDjPlanner.plan(100_000L, 8_000L, incomplete, null))
    }

    @Test
    fun unreliableIncomingAnalysisCannotSkipItsIntroOrChangeItsGain() {
        val outgoing = profile(90_000L, 100_000L, window(90_000L, 100_000L))
        val unreliableIncoming = DjSilenceAnalyzer.AudioProfile(0L, 5_000L,
            listOf(window(0L, 3_000L, 0f, 0f), window(3_000L, 5_000L, 0.5f, 0.8f)), isComplete = false)
        val plan = ExpertDjPlanner.plan(100_000L, 8_000L, outgoing, unreliableIncoming)!!
        assertEquals(0L, plan.incomingStartPositionMs)
        assertEquals(1f, plan.incomingStartVolumeScale, 0f)
        assertEquals(1f, plan.incomingLevelGain, 0f)
        assertEquals(DjTransitionCurve.ExpertNormal, plan.transitionCurve)
    }

    @Test
    fun aNinetySecondSilentTailKeepsTheHandoffAtTheAudibleEnd() {
        val plan = ExpertDjPlanner.plan(180_000L, 8_000L,
            profile(80_000L, 180_000L, window(80_000L, 89_780L), window(89_780L, 180_000L, 0f, 0f)), null)!!
        assertEquals(90_000L, plan.audibleEndPositionMs)
        assertEquals(86_500L, plan.triggerPositionMs)
        assertEquals(79_500L, plan.preloadPositionMs)
    }

    private fun profile(start: Long, end: Long, vararg windows: DjSilenceAnalyzer.AudioWindow) =
        DjSilenceAnalyzer.AudioProfile(start, end, windows.toList())

    private fun window(start: Long, end: Long, rms: Float = 0.1f, peak: Float = 0.1f) =
        DjSilenceAnalyzer.AudioWindow(start, end, rms, peak)
}
