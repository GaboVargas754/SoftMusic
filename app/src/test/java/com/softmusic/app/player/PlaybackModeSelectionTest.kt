package com.softmusic.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackModeSelectionTest {
    @Test
    fun deferredRepeatChangesKeepTheManualQueueOrder() {
        val selection = PlaybackModeSelection()
        selection.select(PlaybackMode.RepeatList, PlaybackMode.Ordered, null)
        selection.select(PlaybackMode.RepeatCurrent, PlaybackMode.RepeatList, null)
        val pending = selection.takePending(ready = true)!!
        assertEquals(PlaybackMode.RepeatCurrent, pending.mode)
        assertFalse(pending.regenerateQueue)
    }

    @Test
    fun theLatestEarlyChoiceSurvivesUntilTheControllerAndLibraryAreReady() {
        val selection = PlaybackModeSelection()
        selection.select(PlaybackMode.Shuffle, PlaybackMode.Ordered, null)
        assertNull(selection.takePending(ready = false))
        assertTrue(selection.hasPending)
        assertEquals(PlaybackMode.Shuffle, selection.selectedMode)
        assertEquals(PendingPlaybackMode(PlaybackMode.Shuffle, true), selection.takePending(ready = true))
        assertFalse(selection.hasPending)
        assertNull(selection.takePending(ready = true))
        assertEquals(PlaybackMode.Shuffle, selection.selectedMode)
    }

    @Test
    fun leavingShuffleBeforeLoadingStillRestoresAnOrderedQueue() {
        val selection = PlaybackModeSelection()
        selection.select(PlaybackMode.Shuffle, PlaybackMode.Ordered, null)
        selection.select(PlaybackMode.Ordered, PlaybackMode.Shuffle, null)
        assertEquals(PendingPlaybackMode(PlaybackMode.Ordered, true), selection.takePending(ready = true))
    }

    @Test
    fun aRunningShuffleSessionIsConsideredBeforeTheLocalStateHasCaughtUp() {
        val selection = PlaybackModeSelection()
        selection.select(PlaybackMode.RepeatList, PlaybackMode.Ordered, PlaybackMode.Shuffle)
        assertEquals(PendingPlaybackMode(PlaybackMode.RepeatList, true), selection.takePending(ready = true))
    }
}
