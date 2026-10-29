package com.softmusic.app.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListeningHistoryWriteFenceTest {
    @Test
    fun `clear rejects already queued events and accepts only post-clear listening`() {
        val fence = ListeningHistoryWriteFence()
        val queuedBeforeClear = fence.snapshot.revision
        val afterClear = fence.clear().revision
        val storage = mutableListOf<Long>()
        if (fence.accepts(queuedBeforeClear)) storage += 1L
        if (fence.accepts(afterClear)) storage += 2L
        assertEquals(listOf(2L), storage)
    }

    @Test
    fun `toggle barriers reject old writes even after quickly enabling again`() {
        val fence = ListeningHistoryWriteFence()
        val beforeToggle = fence.snapshot.revision
        val disabled = fence.setEnabled(false)
        assertFalse(fence.accepts(beforeToggle))
        assertFalse(fence.accepts(disabled.revision))
        val enabled = fence.setEnabled(true)
        assertFalse(fence.accepts(beforeToggle))
        assertFalse(fence.accepts(disabled.revision))
        assertTrue(fence.accepts(enabled.revision))
    }

    @Test
    fun `a late database settings load cannot overwrite a newer user choice`() {
        val fence = ListeningHistoryWriteFence()
        val loadRevision = fence.snapshot.revision
        fence.setEnabled(false)
        assertNull(fence.restoreEnabled(loadRevision, true))
        assertFalse(fence.snapshot.enabled)
    }

    @Test
    fun `restoring a disabled database never grants a recording ticket`() {
        val fence = ListeningHistoryWriteFence()
        fence.restoreEnabled(0L, false)
        assertFalse(fence.accepts(0L))
        val revision = fence.setEnabled(true).revision
        assertTrue(fence.accepts(revision))
    }

    @Test
    fun `clear before startup settings load keeps a saved opt-out until explicit enable`() {
        val fence = ListeningHistoryWriteFence()
        val initialRefresh = fence.snapshot.revision
        val clear = fence.clear()
        // The serialized Clear command reloads unknown settings at its own revision.
        assertNull(fence.restoreEnabled(initialRefresh, false))
        assertEquals(false, fence.restoreEnabled(clear.revision, false)?.enabled)
        assertFalse(fence.accepts(clear.revision))
        val explicitlyEnabled = fence.setEnabled(true)
        assertTrue(fence.accepts(explicitlyEnabled.revision))
        assertFalse(fence.accepts(initialRefresh))
    }

    @Test
    fun `a startup clear reload cannot override a later explicit toggle`() {
        val fence = ListeningHistoryWriteFence()
        val clear = fence.clear()
        fence.setEnabled(false)
        val explicitEnable = fence.setEnabled(true)
        assertNull(fence.restoreEnabled(clear.revision, false))
        assertTrue(fence.accepts(explicitEnable.revision))
    }
}
