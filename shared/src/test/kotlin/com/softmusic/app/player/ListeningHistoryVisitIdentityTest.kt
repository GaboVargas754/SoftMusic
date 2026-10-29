package com.softmusic.app.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ListeningHistoryVisitIdentityTest {
    @Test
    fun `same-song replay through a replacement window starts exactly one new visit`() {
        val identity = ListeningHistoryVisitIdentity()
        val tracker = ListeningHistoryTracker()
        fun sample(time: Long, uid: String, playlistChanged: Boolean, position: Long = time) = tracker.observe(
            1L, 20_000L, true, time, time,
            startsNewVisit = identity.observe(1L, uid, position, playlistChanged),
        )
        sample(0L, "first-window", true)
        assertEquals(1, sample(10_000L, "first-window", false).size)
        assertTrue(sample(15_000L, "replay-window", true, position = 0L).isEmpty())
        assertTrue(sample(15_000L, "replay-window", true, position = 0L).isEmpty())
        assertTrue(sample(24_999L, "replay-window", false, position = 9_999L).isEmpty())
        assertEquals(1, sample(25_000L, "replay-window", false, position = 10_000L).size)
    }

    @Test
    fun `prefix suffix and reorder patches preserve the playing window visit`() {
        val identity = ListeningHistoryVisitIdentity()
        assertFalse(identity.observe(1L, "playing-window", 20_000L, playlistChanged = true))
        repeat(4) { assertFalse(identity.observe(1L, "playing-window", 20_000L, playlistChanged = true)) }
        assertFalse(identity.observe(1L, "playing-window", 0L))
    }

    @Test
    fun `source refreshes update identity without pretending they are replayed songs`() {
        val identity = ListeningHistoryVisitIdentity()
        identity.observe(1L, "placeholder", 0L)
        assertFalse(identity.observe(1L, "resolved-window", 0L, playlistChanged = false))
        assertFalse(identity.observe(1L, "resolved-window", 0L, playlistChanged = true))
        assertTrue(identity.observe(1L, "resolved-window", 0L, repeated = true))
    }

    @Test
    fun `clear-upcoming full replacement preserves an already-qualified visit`() {
        assertPositionPreservingReplacementDoesNotCountAgain("clear-upcoming-window", 45_000L)
    }

    @Test
    fun `fallback full queue reset preserves an already-qualified visit`() {
        assertPositionPreservingReplacementDoesNotCountAgain("fallback-window", 31_000L)
    }

    @Test
    fun `same-window seeks to the beginning never become repeated visits`() {
        val identity = ListeningHistoryVisitIdentity()
        identity.observe(1L, "window", 60_000L)
        assertFalse(identity.observe(1L, "window", 0L, playlistChanged = true))
        assertFalse(identity.observe(1L, "window", 500L))
    }

    private fun assertPositionPreservingReplacementDoesNotCountAgain(newUid: String, position: Long) {
        val identity = ListeningHistoryVisitIdentity()
        val tracker = ListeningHistoryTracker()
        fun sample(elapsed: Long, uid: String, currentPosition: Long, playlistChanged: Boolean) = tracker.observe(
            1L, 180_000L, true, elapsed, elapsed,
            startsNewVisit = identity.observe(1L, uid, currentPosition, playlistChanged),
        )
        sample(0L, "original-window", 0L, true)
        assertEquals(1, sample(30_000L, "original-window", 30_000L, false).size)
        assertTrue(sample(position, newUid, position, true).isEmpty())
        assertTrue(sample(position + 30_000L, newUid, position + 30_000L, false).isEmpty())
    }
}
