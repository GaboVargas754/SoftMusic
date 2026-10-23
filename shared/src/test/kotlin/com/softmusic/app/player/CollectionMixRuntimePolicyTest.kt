package com.softmusic.app.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CollectionMixRuntimePolicyTest {
    private val custom = MixTransitionSettings(mode = MixTransitionMode.Custom, durationMs = 4_000,
        outgoingStartMs = 30_000, incomingStartMs = 2_000, incomingVolume = .4f,
        incomingEq = MixEq(lowDb = -6f))
    private val context = CollectionMixContext(PlaybackQueueSource.Playlist, "perreo", setOf(1, 2, 3, 4),
        CollectionMixConfig(accepted = true, enabled = true, transitions = mapOf("1>2" to custom)))
    private fun effective(value: CollectionMixContext, outgoing: String = "1", incoming: String = "2") =
        CollectionMixRuntimePolicy.effectiveContext(value, outgoing, incoming, PlaybackMode.Ordered)

    @Test fun `unrelated pair edits order and automatic defaults cannot interrupt a custom pair`() {
        val unrelated = context.copy(config = context.config.copy(
            transitions = context.config.transitions + ("3>4" to custom.copy(intensity = .9f)),
            orderedSongIds = listOf(4, 3, 1, 2), settings = AutoDjSettings(beats = 32, effect = AutoDjEffect.EchoOut)))
        assertEquals(effective(context), effective(unrelated))
        assertNotEquals(effective(context), effective(context.copy(config = context.config.copy(
            transitions = mapOf("1>2" to custom.copy(incomingEq = MixEq(highDb = -8f)))))))
    }

    @Test fun `defaults only affect automatic pairs and relevant membership or consent cancels`() {
        val defaults = context.copy(config = context.config.copy(settings = AutoDjSettings(beats = 32)))
        assertNotEquals(effective(context, "3", "4"), effective(defaults, "3", "4"))
        assertNotEquals(effective(context, "3", "4"), effective(context, "4", "3"))
        assertEquals(effective(context), effective(context.copy(songIds = setOf(1, 2, 5))))
        assertNotEquals(effective(context), effective(context.copy(songIds = setOf(1, 3, 4))))
        assertNotEquals(effective(context), effective(context.copy(sourceKey = "another")))
        assertNotEquals(effective(context), effective(context.copy(config = context.config.copy(enabled = false))))
    }

    @Test fun `late MP3 startup with enough audio degrades sync while preserving plan and controls`() {
        val plan = AutoDjPlan(30_000, 2_000, 8_000, 1.02f, 120f, 117.65f, true)
        val decision = CollectionMixRuntimePolicy.evaluateStart(plan, 30_250, 2_100, 60_000, 60_000, true)
        assertTrue(decision.canMix)
        assertFalse(decision.synchronized)
        assertEquals("startup_alignment_degraded", decision.reason)
        assertEquals(30_000L, plan.triggerPositionMs)
        assertEquals(2_000L, plan.incomingStartPositionMs)
        assertEquals(8_000L, plan.durationMs)
        assertEquals(1.02f, plan.incomingSpeed)
        assertEquals(custom, effective(context).custom)
        assertTrue(CollectionMixRuntimePolicy.evaluateStart(plan, 30_250, 2_255, 60_000, 60_000, true).synchronized)
    }

    @Test fun `readiness rejects invalid cues insufficient overlap or unready decks`() {
        val plan = AutoDjPlan(30_000, 2_000, 8_000, 1f, 120f, 120f, true)
        fun start(out: Long = 30_000, incoming: Long = 2_000, end: Long = 60_000, playing: Boolean = true) =
            CollectionMixRuntimePolicy.evaluateStart(plan, out, incoming, 60_000, end, playing)
        assertFalse(start(incoming = 1_900).canMix)
        assertFalse(start(out = 37_500).canMix)
        assertFalse(start(end = 5_000).canMix)
        assertFalse(start(playing = false).canMix)
        assertFalse(start(incoming = -1).canMix)
        assertTrue(start(out = 37_000, incoming = 9_000).canMix)
    }

    @Test fun `rebuffer restart budget is bounded for the current media`() {
        assertTrue(CollectionMixRuntimePolicy.canRetryAfterRebuffer("1", null))
        assertFalse(CollectionMixRuntimePolicy.canRetryAfterRebuffer("1", "1"))
        assertTrue(CollectionMixRuntimePolicy.canRetryAfterRebuffer("2", "1"))
        assertFalse(CollectionMixRuntimePolicy.canRetryAfterRebuffer(null, null))
    }
}
