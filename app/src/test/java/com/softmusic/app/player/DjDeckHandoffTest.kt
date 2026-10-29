package com.softmusic.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DjDeckHandoffTest {
    @Test
    fun incomingAcquiresFocusAndSessionBeforeIdentityChangesAndOutgoingLosesFocusAfterward() {
        val outgoing = Any()
        val incoming = Any()
        val handoff = DjDeckHandoff(outgoing, incoming)
        val events = mutableListOf<String>()

        handoff.swap(outgoing, incoming,
            activate = { player ->
                assertSame(incoming, player)
                assertSame(outgoing, handoff.active)
                events += "incoming_focus"
            },
            attach = { player ->
                assertSame(incoming, player)
                assertSame(outgoing, handoff.active)
                events += "session_player"
            },
            deactivate = { player ->
                assertSame(outgoing, player)
                assertSame(incoming, handoff.active)
                assertSame(outgoing, handoff.standby)
                events += "outgoing_focus"
            })

        assertEquals(listOf("incoming_focus", "session_player", "outgoing_focus"), events)
    }

    @Test
    fun failedSessionAttachmentDoesNotChangeDeckIdentities() {
        val outgoing = Any()
        val incoming = Any()
        val handoff = DjDeckHandoff(outgoing, incoming)
        val events = mutableListOf<String>()
        try {
            handoff.swap(outgoing, incoming,
                activate = { events += "focus" },
                attach = { throw IllegalStateException("Session attachment failed") },
                deactivate = { events += "deactivate" })
        } catch (_: IllegalStateException) {
            events += "failure"
        }

        assertEquals(listOf("focus", "failure"), events)
        assertSame(outgoing, handoff.active)
        assertSame(incoming, handoff.standby)
    }
}
