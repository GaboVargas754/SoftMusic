package com.softmusic.app.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListeningHistoryTrackingSessionTest {
    @Test
    fun `DJ preroll stays silent and incoming keeps its visit after the active-deck swap`() {
        val session = ListeningHistoryTrackingSession<String>()
        session.configure(true, 0L)
        session.sample("outgoing", 1L, 0L, true)
        session.sample("incoming", 2L, 0L, false)
        assertEquals(1L, session.sample("outgoing", 1L, 30_000L, true).single().songId)
        assertTrue(session.sample("incoming", 2L, 30_000L, false).isEmpty())
        session.sample("incoming", 2L, 40_000L, true)
        session.sample("outgoing", 1L, 48_000L, false)
        // The incoming physical deck becomes the session player here; no tracker is replaced.
        assertTrue(session.sample("incoming", 2L, 69_999L, true).isEmpty())
        assertEquals(2L, session.sample("incoming", 2L, 70_000L, true).single().songId)
        assertTrue(session.sample("incoming", 2L, 100_000L, true).isEmpty())
    }

    @Test
    fun `both audible decks can qualify during a short-song overlap`() {
        val session = ListeningHistoryTrackingSession<String>()
        session.configure(true, 0L)
        session.sample("left", 1L, 0L, true, duration = 10_000L)
        session.sample("right", 2L, 0L, true, duration = 10_000L)
        val plays = session.sample("left", 1L, 5_000L, true, duration = 10_000L) +
            session.sample("right", 2L, 5_000L, true, duration = 10_000L)
        assertEquals(listOf(1L, 2L), plays.map { it.songId })
    }

    @Test
    fun `clearing discards partial and already-qualified in-flight visits on all decks`() {
        val session = ListeningHistoryTrackingSession<String>()
        session.configure(true, 0L)
        session.sample("left", 1L, 0L, true)
        session.sample("right", 2L, 0L, true)
        assertTrue(session.sample("left", 1L, 29_000L, true).isEmpty())
        session.configure(true, 1L)
        assertTrue(session.sample("left", 1L, 29_000L, true).isEmpty())
        assertTrue(session.sample("right", 2L, 29_000L, true).isEmpty())
        assertTrue(session.sample("left", 1L, 30_000L, true).isEmpty())
        assertTrue(session.sample("right", 2L, 30_000L, true).isEmpty())
        assertEquals(1, session.sample("left", 1L, 59_000L, true).size)
        assertEquals(1, session.sample("right", 2L, 59_000L, true).size)
    }

    @Test
    fun `disabling and re-enabling cannot resurrect a partial visit or paused time`() {
        val session = ListeningHistoryTrackingSession<String>()
        session.configure(true, 0L)
        session.sample("deck", 1L, 0L, true)
        session.sample("deck", 1L, 29_000L, true)
        session.configure(false, 1L)
        assertTrue(session.sample("deck", 1L, 100_000L, true).isEmpty())
        session.configure(true, 2L)
        assertTrue(session.sample("deck", 1L, 100_000L, true).isEmpty())
        assertTrue(session.sample("deck", 1L, 129_999L, true).isEmpty())
        assertEquals(1, session.sample("deck", 1L, 130_000L, true).size)
    }

    @Test
    fun `normal entry updates do not reset the tracking session`() {
        val session = ListeningHistoryTrackingSession<String>()
        session.configure(true, 4L)
        session.sample("deck", 1L, 0L, true)
        session.sample("deck", 1L, 20_000L, true)
        session.configure(true, 4L)
        assertEquals(1, session.sample("deck", 1L, 30_000L, true).size)
    }

    private fun ListeningHistoryTrackingSession<String>.sample(
        deck: String, song: Long, elapsed: Long, audible: Boolean, duration: Long = 300_000L,
    ) = observe(deck, song, duration, audible, elapsed, 100_000L + elapsed)
}
