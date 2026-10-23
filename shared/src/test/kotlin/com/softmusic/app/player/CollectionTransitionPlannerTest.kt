package com.softmusic.app.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CollectionTransitionPlannerTest {
    private val profile = DjBeatProfile(120f, .95f, 0.0, 0, 60_000)

    @Test fun `consent membership mode and pair override all gate automatic mixing`() {
        val enabled = CollectionMixContext(PlaybackQueueSource.Favorites, songIds = setOf(1, 2),
            config = CollectionMixConfig(accepted = true, enabled = true))
        fun allowed(context: CollectionMixContext?, mode: PlaybackMode = PlaybackMode.Ordered, incoming: String = "2") =
            CollectionTransitionPlanner.permitsPair(context, "1", incoming, mode)
        assertTrue(allowed(enabled))
        assertFalse(allowed(null))
        assertFalse(allowed(enabled.copy(config = enabled.config.copy(accepted = false))))
        assertFalse(allowed(enabled.copy(source = PlaybackQueueSource.Songs)))
        assertFalse(allowed(enabled, PlaybackMode.Shuffle))
        assertFalse(allowed(enabled, PlaybackMode.RepeatCurrent))
        assertFalse(allowed(enabled, incoming = "3"))
        assertFalse(allowed(enabled, incoming = "1"))
        assertFalse(allowed(enabled.copy(config = enabled.config.copy(transitions = mapOf("1>2" to
            MixTransitionSettings(mode = MixTransitionMode.Off))))))
    }

    @Test fun `custom positions and duration remain exact with reliable aligned beats`() {
        val settings = MixTransitionSettings(mode = MixTransitionMode.Custom, durationMs = 3_500,
            outgoingStartMs = 30_000, incomingStartMs = 2_000)
        val result = assertNotNull(CollectionTransitionPlanner.plan(settings, AutoDjSettings(), 60_000, 60_000, 0, profile, profile))
        assertEquals(30_000L, result.plan.triggerPositionMs)
        assertEquals(2_000L, result.plan.incomingStartPositionMs)
        assertEquals(3_500L, result.plan.durationMs)
        assertTrue(result.plan.synchronized)
    }

    @Test fun `unmeasured custom cues and off grid cues keep exact positions without promising sync`() {
        for (cue in listOf(5_000L, 30_125L)) {
            val settings = MixTransitionSettings(mode = MixTransitionMode.Custom, durationMs = 4_000,
                outgoingStartMs = cue, incomingStartMs = 2_000)
            val measuredTail = profile.copy(firstBeatMs = 30_000.0, analyzedStartMs = 30_000)
            val result = assertNotNull(CollectionTransitionPlanner.plan(settings, AutoDjSettings(), 60_000, 60_000, 0, measuredTail, profile))
            assertEquals(cue, result.plan.triggerPositionMs)
            assertFalse(result.plan.synchronized)
            assertEquals(1f, result.plan.incomingSpeed)
            assertNotNull(result.message)
        }
    }

    @Test fun `cues beyond actual durations past playback or too short are rejected`() {
        val settings = MixTransitionSettings(mode = MixTransitionMode.Custom, durationMs = 4_000,
            outgoingStartMs = 10_000, incomingStartMs = 2_000)
        assertNull(CollectionTransitionPlanner.plan(settings, AutoDjSettings(), 12_000, 60_000, 0))
        assertNull(CollectionTransitionPlanner.plan(settings, AutoDjSettings(), 60_000, 5_000, 0))
        assertNull(CollectionTransitionPlanner.plan(settings, AutoDjSettings(), 60_000, 60_000, 11_000))
        assertNull(CollectionTransitionPlanner.plan(settings.copy(outgoingStartMs = Long.MAX_VALUE), AutoDjSettings(), 60_000, 60_000, 0))
        assertNull(CollectionTransitionPlanner.plan(settings.copy(mode = MixTransitionMode.Off), AutoDjSettings(), 60_000, 60_000, 0))
    }

    @Test fun `automatic fallback is truthful and uses collection beat count`() {
        val result = assertNotNull(CollectionTransitionPlanner.plan(MixTransitionSettings(), AutoDjSettings(beats = 32), 100_000, 50_000, 0))
        assertEquals(16_000L, result.plan.durationMs)
        assertFalse(result.plan.synchronized)
        assertNotNull(result.message)
    }

    @Test fun `late automatic fallback only shortens to a useful overlap and custom remains unchanged`() {
        val late = assertNotNull(CollectionTransitionPlanner.plan(MixTransitionSettings(), AutoDjSettings(), 60_000, 60_000, 55_000))
        assertEquals(55_000L, late.plan.triggerPositionMs)
        assertEquals(4_850L, late.plan.durationMs)
        assertFalse(late.plan.synchronized)
        assertNull(CollectionTransitionPlanner.plan(MixTransitionSettings(), AutoDjSettings(), 60_000, 60_000, 58_500))
        val fixed = MixTransitionSettings(mode = MixTransitionMode.Custom, outgoingStartMs = 52_000, incomingStartMs = 2_000, durationMs = 8_000)
        assertNull(CollectionTransitionPlanner.plan(fixed, AutoDjSettings(), 60_000, 60_000, 55_000))
        val exact = assertNotNull(CollectionTransitionPlanner.plan(fixed, AutoDjSettings(), 60_000, 60_000, 50_000))
        assertEquals(52_000L, exact.plan.triggerPositionMs)
        assertEquals(8_000L, exact.plan.durationMs)
    }

    @Test fun `late automatic beat selection stays ahead of playback with a usable overlap`() {
        val late = assertNotNull(CollectionTransitionPlanner.plan(MixTransitionSettings(), AutoDjSettings(),
            60_000, 60_000, 55_125, profile, profile))
        assertTrue(late.plan.triggerPositionMs >= 55_125)
        assertTrue(late.plan.durationMs >= 1_500)
        assertTrue(late.plan.triggerPositionMs + late.plan.durationMs <= 59_850)
    }
}
