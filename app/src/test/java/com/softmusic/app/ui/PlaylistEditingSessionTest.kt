package com.softmusic.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistEditingSessionTest {
    @Test
    fun dragAcrossSeveralRowsKeepsTheFullSnapshotForOneCommit() {
        val full = listOf(99L, 1L, 2L, 88L, 3L, 4L)
        val visible = listOf(1L, 2L, 3L, 4L)
        val session = PlaylistReorderSession(full, visible, 1L)

        assertTrue(session.moveTo(1))
        assertTrue(session.moveTo(3))
        assertEquals(PlaylistReorderCommit(full, listOf(2L, 3L, 4L, 1L)), session.drop(full, visible))
    }

    @Test
    fun changedFullMembershipOrVisibleOrderCancelsTheGesture() {
        val full = listOf(1L, 99L, 2L, 3L)
        val visible = listOf(1L, 2L, 3L)
        val session = PlaylistReorderSession(full, visible, 3L)
        session.moveTo(0)

        assertNull(session.drop(full + 100L, visible))
        assertNull(session.drop(full.filterNot { it == 99L }, visible))
        assertNull(session.drop(full, listOf(1L, 3L)))
        assertNull(session.drop(full, listOf(2L, 1L, 3L)))
        assertNull(session.drop(full, visible + 99L))
    }

    @Test
    fun returningToTheOriginalPositionDoesNotCommit() {
        val ids = listOf(1L, 2L, 3L)
        val session = PlaylistReorderSession(ids, ids, 2L)
        session.moveTo(2)
        session.moveTo(1)

        assertNull(session.drop(ids, ids))
    }

    @Test
    fun missingSongsInvalidTargetsAndInvalidVisibleSnapshotsCannotCommit() {
        val ids = listOf(1L, 2L)
        val unknown = PlaylistReorderSession(ids, ids, 99L)
        assertFalse(unknown.moveTo(0))
        assertNull(unknown.drop(ids, ids))
        val session = PlaylistReorderSession(ids, ids, 2L)
        assertFalse(session.moveTo(-1))
        assertFalse(session.moveTo(2))
        val duplicate = PlaylistReorderSession(ids, listOf(1L, 1L, 2L), 2L)
        duplicate.moveTo(0)
        assertNull(duplicate.drop(ids, listOf(1L, 1L, 2L)))
        val unavailable = PlaylistReorderSession(ids, listOf(1L, 99L), 1L)
        unavailable.moveTo(1)
        assertNull(unavailable.drop(ids, listOf(1L, 99L)))
    }

    @Test
    fun selectionSurvivesSearchOrderingAndDeduplicatesSubmittedIds() {
        val selection = PlaylistSongSelection().toggle(3L, listOf(1, 2, 3)).toggle(1L, listOf(1, 2, 3))

        assertEquals(setOf(1L, 3L), selection.selectedIds)
        assertEquals(listOf(1L, 3L), selection.orderedFor(listOf(1, 2, 3, 1)))
        assertEquals(listOf(3L, 1L), selection.orderedFor(listOf(3, 2, 1)))
    }

    @Test
    fun refreshDropsUnavailableSelectionsWithoutSelectingUnknownSongs() {
        val selection = PlaylistSongSelection(setOf(1L, 2L, 3L)).retainedFor(listOf(1L, 3L))

        assertEquals(setOf(1L, 3L), selection.selectedIds)
        assertEquals(selection, selection.toggle(99L, listOf(1L, 3L)))
        assertEquals(setOf(3L), selection.toggle(1L, listOf(1L, 3L)).selectedIds)
        assertTrue(selection.retainedFor(emptyList()).selectedIds.isEmpty())
    }
}
