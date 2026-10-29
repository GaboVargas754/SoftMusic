package com.softmusic.app.player

/** Keeps deck identity changes in the same order as the session's player handoff. */
internal class DjDeckHandoff<D : Any>(active: D, standby: D) {
    var active: D = active
        private set
    var standby: D = standby
        private set

    fun swap(outgoing: D, incoming: D, activate: (D) -> Unit, attach: (D) -> Unit, deactivate: (D) -> Unit) {
        activate(incoming)
        attach(incoming)
        active = incoming
        standby = outgoing
        deactivate(outgoing)
    }
}
