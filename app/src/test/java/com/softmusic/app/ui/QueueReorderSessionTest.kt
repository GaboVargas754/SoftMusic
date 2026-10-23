package com.softmusic.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class QueueReorderSessionTest {
    @Test
    fun crossingSeveralRowsCommitsOneOffsetFromTheOriginalPosition() {
        val ids = listOf(10L, 20L, 30L, 40L)
        val session = QueueReorderSession(ids, 10L)

        session.moveTo(1)
        session.moveTo(3)

        assertEquals(listOf(20L, 30L, 40L, 10L), session.orderedIds)
        assertEquals(QueueDrop(10L, 3), session.drop(ids))
    }

    @Test
    fun movingBackToTheStartingPositionDoesNotCommit() {
        val ids = listOf(10L, 20L, 30L)
        val session = QueueReorderSession(ids, 20L)

        session.moveTo(2)
        session.moveTo(1)

        assertNull(session.drop(ids))
    }

    @Test
    fun queueChangesOrPlaybackAdvancingInvalidateTheDrop() {
        val ids = listOf(10L, 20L, 30L)
        val session = QueueReorderSession(ids, 30L)
        session.moveTo(0)

        assertNull(session.drop(listOf(10L, 30L)))
        assertNull(session.drop(listOf(20L, 30L)))
        assertNull(session.drop(listOf(10L, 20L, 30L, 40L)))
        assertNull(session.drop(listOf(20L, 10L, 30L)))
    }

    @Test
    fun anUnknownSongAndTargetsOutsideUpcomingSongsCannotMove() {
        val ids = listOf(10L, 20L)
        val unknownSong = QueueReorderSession(ids, 99L)
        assertFalse(unknownSong.moveTo(0))
        assertNull(unknownSong.drop(ids))

        val session = QueueReorderSession(ids, 20L)
        assertFalse(session.moveTo(-1))
        assertFalse(session.moveTo(2))
        assertEquals(ids, session.orderedIds)
    }

    @Test
    fun movingUpKeepsOtherSongsInTheirExistingOrder() {
        val ids = listOf(10L, 20L, 30L, 40L)
        val session = QueueReorderSession(ids, 40L)
        session.moveTo(0)

        assertEquals(listOf(40L, 10L, 20L, 30L), session.orderedIds)
        assertEquals(QueueDrop(40L, -3), session.drop(ids))
    }
}
