package com.softmusic.app.player

internal data class DjQueueTransition<D : Any>(
    val revision: Long,
    val outgoing: D,
    val currentMediaId: String,
)

/** Invalidates captured plans without ever changing the active deck's queue or position. */
internal class DjQueueTransitionGuard<D : Any>(
    private val activeDeck: () -> D,
    private val currentMediaId: (D) -> String?,
) {
    private var revision = 0L
    private var handingOff = false

    fun capture(outgoing: D, mediaId: String): DjQueueTransition<D> =
        DjQueueTransition(revision, outgoing, mediaId)

    fun isCurrent(transition: DjQueueTransition<D>): Boolean =
        transition.revision == revision && activeDeck() === transition.outgoing &&
            currentMediaId(transition.outgoing) == transition.currentMediaId

    fun invalidate() {
        revision++
    }

    fun onTimelineChanged(observedDeck: D, playlistChanged: Boolean): Boolean {
        if (!playlistChanged || handingOff || observedDeck !== activeDeck()) return false
        invalidate()
        return true
    }

    fun performIfCurrent(transition: DjQueueTransition<D>, ownsWork: () -> Boolean, action: () -> Unit): Boolean {
        if (!ownsWork() || !isCurrent(transition)) return false
        action()
        return true
    }

    fun handoffIfCurrent(transition: DjQueueTransition<D>, ownsWork: () -> Boolean, action: () -> Unit): Boolean =
        performIfCurrent(transition, ownsWork) {
            handingOff = true
            try {
                action()
            } finally {
                handingOff = false
            }
        }
}
