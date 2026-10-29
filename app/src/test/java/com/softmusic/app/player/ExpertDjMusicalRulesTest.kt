package com.softmusic.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpertDjMusicalRulesTest {
    @Test
    fun songLevelUsesDurationWeightedBodyAndIgnoresSilenceAndIsolatedPeaks() {
        val steady = head(window(0L, 15_000L, 0.2f))
        val withIntroAndHit = head(
            window(0L, 5_000L, 0f),
            window(5_000L, 5_100L, 0.95f),
            window(5_100L, 15_000L, 0.2f),
        )
        val subdividedHit = head(*(
            listOf(window(0L, 5_000L, 0f)) +
                (0 until 100).map { window(5_000L + it, 5_001L + it, 0.95f) } +
                listOf(window(5_100L, 15_000L, 0.2f))
            ).toTypedArray())

        assertEquals(0.5f, ExpertDjPlanner.levelGain(steady), 0.00001f)
        assertEquals(ExpertDjPlanner.levelGain(steady), ExpertDjPlanner.levelGain(withIntroAndHit), 0f)
        assertEquals(ExpertDjPlanner.levelGain(steady), ExpertDjPlanner.levelGain(subdividedHit), 0f)
    }

    @Test
    fun songLevelNeverBoostsAndLimitsAttenuation() {
        assertEquals(1f, ExpertDjPlanner.levelGain(head(window(0L, 15_000L, 0.01f))), 0f)
        assertEquals(1f, ExpertDjPlanner.levelGain(head(window(0L, 15_000L, 0f))), 0f)
        assertEquals(0.4f, ExpertDjPlanner.levelGain(head(window(0L, 15_000L, 0.9f))), 0f)
        assertEquals(1f, ExpertDjPlanner.levelGain(head(
            window(0L, 14_500L, 0.0001f), window(14_500L, 15_000L, 0.9f),
        )), 0f)
    }

    @Test
    fun songLevelRequiresReliableContiguousHeadCoverage() {
        assertEquals(1f, ExpertDjPlanner.levelGain(null), 0f)
        assertEquals(1f, ExpertDjPlanner.levelGain(head(window(0L, 15_000L, 0.5f)).copy(isComplete = false)), 0f)
        assertEquals(1f, ExpertDjPlanner.levelGain(profile(90_000L, 100_000L,
            window(90_000L, 100_000L, 0.5f))), 0f)
        assertEquals(1f, ExpertDjPlanner.levelGain(head(window(1_000L, 15_000L, 0.5f))), 0f)
        assertEquals(1f, ExpertDjPlanner.levelGain(head(
            window(0L, 8_000L, 0.5f), window(7_000L, 15_000L, 0.5f),
        )), 0f)
        assertEquals(1f, ExpertDjPlanner.levelGain(head(window(0L, 15_000L, Float.NaN))), 0f)
    }

    @Test
    fun levelEstimateUsesOnlyTheFirstFifteenSeconds() {
        val longerProfile = profile(0L, 30_000L,
            window(0L, 15_000L, 0.2f), window(15_000L, 30_000L, 0.9f))
        assertEquals(0.5f, ExpertDjPlanner.levelGain(longerProfile), 0.00001f)
    }

    @Test
    fun smoothEntriesKeepLongBlendsWhileStrongAttacksShortenThem() {
        val outgoing = tail(rms = 0.04f)
        val quiet = head(window(0L, 15_000L, 0.03f, 0.08f))
        val abrupt = head(window(0L, 15_000L, 0.25f, 0.8f))
        val long = ExpertDjPlanner.plan(100_000L, 8_000L, outgoing, quiet)!!
        val short = ExpertDjPlanner.plan(100_000L, 8_000L, outgoing, abrupt)!!

        assertEquals(8_000L, long.mixDurationMs)
        assertEquals(3_000L, short.mixDurationMs)
        assertEquals(0.4f, short.incomingLevelGain, 0.00001f)
        assertEquals(100_000L, short.triggerPositionMs + short.mixDurationMs)
        assertEquals(5_000L, ExpertDjPlanner.plan(100_000L, 5_000L, outgoing, quiet)!!.mixDurationMs)
        assertEquals(2_000L, ExpertDjPlanner.plan(100_000L, 2_000L, outgoing, abrupt)!!.mixDurationMs)
    }

    @Test
    fun adaptiveDurationsStayInsideEveryConfiguredMaximum() {
        for (maximumMs in listOf(3_000L, 5_000L, 6_000L, 8_000L)) {
            for (rms in listOf(0.01f, 0.10f, 0.15f, 0.3f)) {
                val plan = ExpertDjPlanner.plan(100_000L, maximumMs, tail(),
                    head(window(0L, 15_000L, rms, rms * 2f)))!!
                assertTrue(plan.mixDurationMs in 3_000L..maximumMs)
            }
        }
    }

    @Test
    fun compatibleReliableGridsAlignBothStartsWithinTheSafeWindow() {
        val energy = energyPlan()
        val aligned = ExpertDjPlanner.plan(100_000L, 8_000L, tail(), incoming(), currentBeats(), nextBeats())!!

        assertTrue(aligned.beatAligned)
        assertEquals(92_250L, aligned.triggerPositionMs)
        assertEquals(100L, aligned.incomingStartPositionMs)
        assertEquals(7_750L, aligned.mixDurationMs)
        assertEquals(aligned.audibleEndPositionMs, aligned.triggerPositionMs + aligned.mixDurationMs)
        assertEquals(aligned.triggerPositionMs - 7_000L, aligned.preloadPositionMs)
        assertTrue(kotlin.math.abs(aligned.triggerPositionMs - energy.triggerPositionMs) <= 500L)
        assertEquals(120f, aligned.currentBpm!!, 0f)
        assertEquals(120f, aligned.incomingBpm!!, 0f)
    }

    @Test
    fun uncertainOrIncompatibleBeatProfilesReturnTheExactEnergyPlan() {
        val energy = energyPlan()
        val rejected = listOf(
            currentBeats().copy(confidence = 0.799f) to nextBeats(),
            currentBeats() to nextBeats().copy(confidence = Float.NaN),
            currentBeats() to nextBeats().copy(bpm = 124f),
            currentBeats() to nextBeats().copy(bpm = 60f),
            currentBeats().copy(firstBeatMs = Double.NaN) to nextBeats(),
            currentBeats().copy(analyzedEndMs = 92_000L) to nextBeats(),
            currentBeats() to nextBeats().copy(analyzedEndMs = 7_999L),
        )
        for ((outgoingGrid, incomingGrid) in rejected) {
            assertEquals(energy, ExpertDjPlanner.plan(100_000L, 8_000L, tail(), incoming(), outgoingGrid, incomingGrid))
        }
        assertTrue(ExpertDjPlanner.plan(100_000L, 8_000L, tail(), incoming(),
            currentBeats().copy(confidence = 0.8f), nextBeats().copy(bpm = 123.6f))!!.beatAligned)
    }

    @Test
    fun lateFirstBeatCannotSkipAQuietMusicalIntro() {
        val quietIntro = head(window(0L, 4_000L, 0.0045f, 0.008f), window(4_000L, 15_000L, 0.12f, 0.4f))
        val energy = ExpertDjPlanner.plan(100_000L, 8_000L, tail(), quietIntro)!!
        val attempted = ExpertDjPlanner.plan(100_000L, 8_000L, tail(), quietIntro,
            currentBeats(), nextBeats().copy(firstBeatMs = 4_000.0))!!

        assertEquals(0L, energy.incomingStartPositionMs)
        assertEquals(energy, attempted)
        assertFalse(attempted.beatAligned)
    }

    @Test
    fun incomingBeatSeekCannotExceedFiveHundredMillisecondsPastTheAudibleStart() {
        val atLimit = ExpertDjPlanner.plan(100_000L, 8_000L, tail(), incoming(),
            currentBeats(), nextBeats().copy(firstBeatMs = 500.0))!!
        val beyondLimit = ExpertDjPlanner.plan(100_000L, 8_000L, tail(), incoming(),
            currentBeats(), nextBeats().copy(firstBeatMs = 501.0))!!

        assertTrue(atLimit.beatAligned)
        assertEquals(500L, atLimit.incomingStartPositionMs)
        assertEquals(energyPlan(), beyondLimit)
    }

    @Test
    fun leadingSilenceAllowsOnlyABoundedShiftIntoTheFirstAudibleBeat() {
        val leadingSilence = head(window(0L, 2_000L, 0f), window(2_000L, 15_000L, 0.05f, 0.5f))
        val energy = ExpertDjPlanner.plan(100_000L, 8_000L, tail(), leadingSilence)!!
        val aligned = ExpertDjPlanner.plan(100_000L, 8_000L, tail(), leadingSilence,
            currentBeats(), nextBeats().copy(firstBeatMs = 2_000.0))!!

        assertTrue(aligned.beatAligned)
        assertEquals(2_000L, aligned.incomingStartPositionMs)
        assertTrue(aligned.incomingStartPositionMs - energy.incomingStartPositionMs <= 1_000L)
        assertTrue(aligned.incomingStartPositionMs <= leadingSilence.audibleStartMs()!! + 500L)
    }

    @Test
    fun incomingBeatCoverageCannotReplaceMissingAudibleContent() {
        val shortContent = head(window(0L, 500L, 0.05f), window(500L, 15_000L, 0f))
        val energy = ExpertDjPlanner.plan(100_000L, 8_000L, tail(), shortContent)!!
        assertEquals(energy, ExpertDjPlanner.plan(100_000L, 8_000L, tail(), shortContent,
            currentBeats(), nextBeats()))
    }

    @Test
    fun beatAlignmentRetainsTheSilentTailCapAndRejectsUncoveredLongTails() {
        val outgoing = profile(80_000L, 180_000L,
            window(80_000L, 89_780L, 0.05f), window(89_780L, 180_000L, 0f))
        val beatsNearAudibleEnd = DjBeatProfile(120f, 0.95f, 80_250.0, 80_000L, 100_000L)
        val aligned = ExpertDjPlanner.plan(180_000L, 8_000L, outgoing, incoming(),
            beatsNearAudibleEnd, nextBeats())!!
        assertTrue(aligned.beatAligned)
        assertEquals(90_000L, aligned.audibleEndPositionMs)
        assertTrue(aligned.mixDurationMs in 1_000L..3_500L)

        val energy = ExpertDjPlanner.plan(180_000L, 8_000L, outgoing, incoming())!!
        val physicalTailBeats = DjBeatProfile(120f, 0.95f, 148_250.0, 148_000L, 180_000L)
        assertEquals(energy, ExpertDjPlanner.plan(180_000L, 8_000L, outgoing, incoming(),
            physicalTailBeats, nextBeats()))
    }

    private fun energyPlan() = ExpertDjPlanner.plan(100_000L, 8_000L, tail(), incoming())!!
    private fun incoming() = head(window(0L, 15_000L, 0.05f))
    private fun tail(rms: Float = 0.05f) = profile(90_000L, 100_000L, window(90_000L, 100_000L, rms))
    private fun head(vararg windows: DjSilenceAnalyzer.AudioWindow) = profile(0L, 15_000L, *windows)
    private fun profile(start: Long, end: Long, vararg windows: DjSilenceAnalyzer.AudioWindow) =
        DjSilenceAnalyzer.AudioProfile(start, end, windows.toList())
    private fun window(start: Long, end: Long, rms: Float, peak: Float = rms) =
        DjSilenceAnalyzer.AudioWindow(start, end, rms, peak)
    private fun currentBeats() = DjBeatProfile(120f, 0.95f, 90_250.0, 90_000L, 100_000L)
    private fun nextBeats() = DjBeatProfile(120f, 0.95f, 100.0, 0L, 15_000L)
}
