package com.softmusic.app.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListeningHistoryTrackerTest {
    @Test
    fun `qualification uses half a short song or thirty seconds for long and unknown audio`() {
        assertEquals(15_000L, listeningHistoryThresholdMs(30_000L))
        assertEquals(30_000L, listeningHistoryThresholdMs(300_000L))
        assertEquals(30_000L, listeningHistoryThresholdMs(0L))
        assertEquals(30_000L, listeningHistoryThresholdMs(-1L))
        assertEquals(1L, listeningHistoryThresholdMs(1L))
    }

    @Test
    fun `a visit qualifies once exactly at its actual listening threshold`() {
        val tracker = ListeningHistoryTracker()
        assertTrue(tracker.sample(0L).isEmpty())
        assertTrue(tracker.sample(29_999L).isEmpty())
        assertEquals(listOf(ListeningHistoryPlay(1L, 130_000L)), tracker.sample(30_000L))
        assertTrue(tracker.sample(120_000L).isEmpty())
    }

    @Test
    fun `pauses buffering and muted playback do not accumulate listening time`() {
        val tracker = ListeningHistoryTracker()
        tracker.sample(0L)
        tracker.sample(10_000L, audible = false)
        tracker.sample(90_000L, audible = false)
        tracker.sample(100_000L, audible = true)
        assertTrue(tracker.sample(119_999L).isEmpty())
        assertEquals(1L, tracker.sample(120_000L).single().songId)
    }

    @Test
    fun `seeking or a changed wall clock cannot add playback-position jumps`() {
        val tracker = ListeningHistoryTracker()
        tracker.observe(1L, 300_000L, true, 0L, 1_000L)
        // A seek may move the player to its outro; only the unchanged monotonic interval is observed.
        assertTrue(tracker.observe(1L, 300_000L, true, 1_000L, 9_000_000L).isEmpty())
        assertTrue(tracker.observe(1L, 300_000L, true, 29_999L, 0L).isEmpty())
        assertEquals(ListeningHistoryPlay(1L, 500L), tracker.observe(1L, 300_000L, true, 30_000L, 500L).single())
    }

    @Test
    fun `skipping early does not qualify either song and a new song gets a new visit`() {
        val tracker = ListeningHistoryTracker()
        tracker.sample(0L)
        assertTrue(tracker.sample(20_000L, songId = 2L).isEmpty())
        assertTrue(tracker.sample(49_999L, songId = 2L).isEmpty())
        assertEquals(2L, tracker.sample(50_000L, songId = 2L).single().songId)
    }

    @Test
    fun `a repeat of the same media item qualifies a second real visit`() {
        val tracker = ListeningHistoryTracker()
        tracker.sample(0L, durationMs = 20_000L)
        assertEquals(1, tracker.sample(10_000L, durationMs = 20_000L).size)
        assertTrue(tracker.observe(1L, 20_000L, true, 20_000L, 120_000L, startsNewVisit = true).isEmpty())
        assertTrue(tracker.sample(29_999L, durationMs = 20_000L).isEmpty())
        assertEquals(1, tracker.sample(30_000L, durationMs = 20_000L).size)
    }

    @Test
    fun `the final audible interval is counted when a visit ends`() {
        val tracker = ListeningHistoryTracker()
        tracker.sample(0L, durationMs = 20_000L)
        tracker.sample(9_999L, durationMs = 20_000L)
        assertEquals(1L, tracker.sample(10_000L, songId = null, audible = false).single().songId)
        assertTrue(tracker.sample(20_000L, songId = null, audible = false).isEmpty())
    }

    @Test
    fun `late duration discovery adjusts the threshold without counting muted preroll`() {
        val tracker = ListeningHistoryTracker()
        tracker.sample(0L, durationMs = 0L, audible = false)
        tracker.sample(20_000L, durationMs = 0L, audible = true)
        assertTrue(tracker.sample(25_000L, durationMs = 20_000L).isEmpty())
        assertEquals(1L, tracker.sample(30_000L, durationMs = 20_000L).single().songId)
    }

    private fun ListeningHistoryTracker.sample(
        elapsedMs: Long,
        songId: Long? = 1L,
        audible: Boolean = true,
        durationMs: Long = 300_000L,
    ) = observe(songId, durationMs, audible, elapsedMs, 100_000L + elapsedMs)
}
