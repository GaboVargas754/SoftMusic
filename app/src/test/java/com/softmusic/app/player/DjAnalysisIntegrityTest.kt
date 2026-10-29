package com.softmusic.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DjAnalysisIntegrityTest {
    @Test
    fun aSilentDecodedWindowHasCoverageButMissingOrSparsePcmDoesNot() {
        val empty = DjDecodedCoverage(0L, 80_000L)
        assertFalse(empty.isComplete())

        val sparse = DjDecodedCoverage(0L, 80_000L)
        repeat(100) { sparse.record(0L, 23L) }
        assertFalse(sparse.isComplete())

        val decodedSilence = DjDecodedCoverage(0L, 80_000L)
        repeat(20) { frame -> decodedSilence.record(frame * 4_000L, (frame + 1L) * 4_000L) }
        assertTrue(decodedSilence.isComplete())
    }

    @Test
    fun timestampsAtBothEndsDoNotConcealAnUndecodedInterior() {
        val coverage = DjDecodedCoverage(0L, 80_000L)
        coverage.record(0L, 20_000L)
        coverage.record(60_000L, 80_000L)
        assertFalse(coverage.isComplete())
    }

    @Test
    fun smallTimestampRoundingIsAllowedButRepeatedGapsAreNot() {
        val rounded = DjDecodedCoverage(0L, 80_000L)
        rounded.record(0L, 40_000L)
        rounded.record(40_001L, 80_000L)
        assertTrue(rounded.isComplete())

        val repeatedGaps = DjDecodedCoverage(0L, 80_000L)
        repeat(20) { frame -> repeatedGaps.record(frame * 4_000L, frame * 4_000L + 3_500L) }
        assertFalse(repeatedGaps.isComplete())
    }

    @Test
    fun seekingBeforeTheRequestedWindowDoesNotRequireItsPrerollToBeMeasured() {
        val coverage = DjDecodedCoverage(25_000L, 105_000L)
        coverage.record(0L, 60_000L)
        coverage.record(60_000L, 120_000L)
        assertTrue(coverage.isComplete())
    }

    @Test
    fun absentOutputTimesOutAndContinuousOutputCannotResetTheTotalBudget() {
        val stalled = DjDecodeWatchdog(100L, totalBudgetMs = 15_000L, stallBudgetMs = 2_000L)
        stalled.check(2_099L)
        expectTimeout { stalled.check(2_100L) }

        val progressing = DjDecodeWatchdog(0L, totalBudgetMs = 5_000L, stallBudgetMs = 2_000L)
        for (time in 1_000L..4_000L step 1_000L) progressing.decodedOutput(time)
        expectTimeout { progressing.decodedOutput(5_000L) }
    }

    @Test
    fun changingEitherSizeTimestampOrGenerationInvalidatesUriProfiles() {
        val cache = DjAnalysisCache<String>(8)
        val uri = "content://audio/42"
        val original = DjAnalysisSourceRevision(1_000L, 100_000L, 1L)
        for (changed in listOf(original.copy(sizeBytes = 2_000L), original.copy(modifiedMs = 200_000L), original.copy(generation = 2L))) {
            cache.put(uri, original, "old head")
            cache.put(uri, original, "old tail", rangeEndMs = 60_000L)
            assertNull(cache.get(uri, changed))
            assertNull(cache.get(uri, original, rangeEndMs = 60_000L))
        }
    }

    @Test
    fun unknownOrInsufficientRevisionMetadataIsNeverCached() {
        val cache = DjAnalysisCache<String>(8)
        val original = DjAnalysisSourceRevision(1_000L, 100_000L)
        cache.put("track", original, "old")
        assertNull(cache.get("track", null))
        assertNull(cache.get("track", original))

        for (unknown in listOf(null, DjAnalysisSourceRevision(-1L, 10L), DjAnalysisSourceRevision(1_000L, -1L))) {
            cache.put("track", unknown, "do not retain")
            assertNull(cache.get("track", unknown))
        }
    }

    @Test
    fun cacheSeparatesTailRangesAndEvictsTheLeastRecentlyUsedEntry() {
        val cache = DjAnalysisCache<String>(2)
        val revision = DjAnalysisSourceRevision(1_000L, 100_000L)
        cache.put("track", revision, "head")
        cache.put("track", revision, "tail", rangeEndMs = 60_000L)
        assertEquals("head", cache.get("track", revision))
        cache.put("other", revision, "another head")
        assertNull(cache.get("track", revision, rangeEndMs = 60_000L))
        assertEquals("head", cache.get("track", revision))
        assertEquals("another head", cache.get("other", revision))
    }

    private fun expectTimeout(block: () -> Unit) {
        var timedOut = false
        try {
            block()
        } catch (_: DjAnalysisTimeoutException) {
            timedOut = true
        }
        assertTrue("Expected a bounded decoder timeout", timedOut)
    }
}
