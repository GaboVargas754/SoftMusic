package com.softmusic.app.player

import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoDjPlannerTest {
    @Test
    fun `chosen beat counts determine musical overlap duration`() {
        for (beats in listOf(8, 16, 32)) {
            val plan = assertNotNull(plan(settings = AutoDjSettings(beats = beats)))
            assertEquals(beats * 500L, plan.durationMs)
            assertTrue(plan.triggerPositionMs >= 148_000L)
            assertTrue(plan.triggerPositionMs + plan.durationMs <= 180_000L)
            assertTrue(plan.synchronized)
        }
    }

    @Test
    fun `outgoing trigger and prepared incoming cue stay on native beat grids`() {
        val result = assertNotNull(plan())
        assertEquals(171_670L, result.triggerPositionMs)
        assertEquals(1_670L, result.incomingStartPositionMs)
        assertEquals(0L, (result.triggerPositionMs - 148_170L) % 500L)
        assertEquals(0L, (result.incomingStartPositionMs - 170L) % 500L)
    }

    @Test
    fun `speed changes are bounded and use outgoing to incoming tempo ratio`() {
        val result = assertNotNull(plan(incoming = head(bpm = 125f)))
        assertEquals(0.96f, result.incomingSpeed, 0.00001f)
        assertEquals(120f, result.outgoingBpm)
        assertEquals(125f, result.incomingBpm)
        assertNull(plan(incoming = head(bpm = 110f)))
        assertNull(plan(incoming = head(bpm = 130f)))
    }

    @Test
    fun `both exact speed boundaries are accepted`() {
        assertNotNull(plan(outgoing = tail(bpm = 94f), incoming = head(bpm = 100f)))
        assertNotNull(plan(outgoing = tail(bpm = 106f), incoming = head(bpm = 100f)))
        assertNull(plan(outgoing = tail(bpm = 93.9f), incoming = head(bpm = 100f)))
        assertNull(plan(outgoing = tail(bpm = 106.1f), incoming = head(bpm = 100f)))
    }

    @Test
    fun `incoming media availability accounts for playback speed`() {
        val incoming = head(bpm = 100f)
        // A 106 BPM eight second-ish mix consumes more original incoming media at 1.06x.
        val outgoing = tail(bpm = 106f)
        val configuration = AutoDjSettings(beats = 16)
        val enoughAtNativeButNotStretched = 11_520L
        val result = plan(outgoing, incoming, settings = configuration, incomingDurationMs = enoughAtNativeButNotStretched)
        assertNull(result)
        assertNotNull(plan(outgoing, incoming, settings = configuration, incomingDurationMs = 11_571L))
    }

    @Test
    fun `late trigger preserves quantization and rejects unavailable full overlap`() {
        val result = assertNotNull(plan(earliest = 171_600L))
        assertTrue(result.triggerPositionMs >= 171_600L)
        assertNull(plan(earliest = 171_671L))
        assertNull(plan(earliest = 181_000L))
    }

    @Test
    fun `complete overlap must fit both analyzed regions`() {
        assertNull(plan(outgoing = tail().copy(analyzedStartMs = 175_000L, firstBeatMs = 175_170.0)))
        assertNull(plan(incoming = head().copy(analyzedEndMs = 9_000L)))
        assertNull(plan(outgoingEndMs = 151_000L))
        assertNull(plan(incomingDurationMs = 8_000L))
    }

    @Test
    fun `native speed mode still quantizes and honors musical duration`() {
        val result = assertNotNull(plan(incoming = head(bpm = 83f), settings = AutoDjSettings(beats = 8, syncEnabled = false)))
        assertEquals(1f, result.incomingSpeed)
        assertEquals(4_000L, result.durationMs)
        assertFalse(result.synchronized)
        assertEquals(0L, (result.triggerPositionMs - 148_170L) % 500L)
    }

    @Test
    fun `half and double interpretations require strong confidence and retain native display bpm`() {
        val outgoing = tail(bpm = 128f)
        val result = assertNotNull(plan(outgoing = outgoing, incoming = head(bpm = 64f)))
        assertEquals(1f, result.incomingSpeed)
        assertEquals(64f, result.incomingBpm)
        assertEquals(7_500L, result.durationMs)
        assertNull(plan(outgoing = outgoing, incoming = head(bpm = 64f).copy(confidence = 0.8f)))
        assertNotNull(plan(outgoing = tail(bpm = 90f), incoming = head(bpm = 180f)))
    }

    @Test
    fun `unreliable malformed and invalid media profiles use fallback`() {
        assertNull(plan(outgoing = tail().copy(confidence = 0.71f)))
        assertNull(plan(incoming = head().copy(confidence = Float.NaN)))
        assertNull(plan(outgoing = tail().copy(bpm = Float.POSITIVE_INFINITY)))
        assertNull(plan(incoming = head().copy(firstBeatMs = Double.NaN)))
        assertNull(plan(outgoing = tail().copy(firstBeatMs = 0.0)))
        assertNull(plan(incoming = head().copy(analyzedStartMs = -1L)))
        assertNull(plan(incomingDurationMs = 0L))
        assertNull(plan(outgoingEndMs = -1L))
    }

    @Test
    fun `fractional periods do not accumulate per beat rounding errors`() {
        val bpm = 123.45f
        val result = assertNotNull(plan(outgoing = tail(bpm), incoming = head(bpm), settings = AutoDjSettings(beats = 32)))
        assertEquals((32 * 60_000.0 / bpm).roundToLong(), result.durationMs)
        val gridIndex = (result.triggerPositionMs - 148_170.0) / (60_000.0 / bpm)
        assertTrue(abs(gridIndex - gridIndex.roundToLong()) < 0.002)
    }

    private fun plan(
        outgoing: DjBeatProfile = tail(),
        incoming: DjBeatProfile = head(),
        settings: AutoDjSettings = AutoDjSettings(),
        outgoingEndMs: Long = 180_000L,
        incomingDurationMs: Long = 180_000L,
        earliest: Long = 0L,
    ): AutoDjPlan? = AutoDjPlanner.plan(outgoing, incoming, outgoingEndMs, incomingDurationMs, settings, earliest)

    private fun tail(bpm: Float = 120f) = DjBeatProfile(bpm, 0.97f, 148_170.0, 148_000L, 180_000L)
    private fun head(bpm: Float = 120f) = DjBeatProfile(bpm, 0.97f, 170.0, 0L, 32_000L)
}
