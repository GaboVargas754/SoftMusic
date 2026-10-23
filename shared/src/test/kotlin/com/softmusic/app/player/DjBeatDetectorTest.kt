package com.softmusic.app.player

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DjBeatDetectorTest {
    @Test
    fun `steady click tracks recover tempo including fractional frame periods`() {
        for (bpm in listOf(60.0, 72.0, 90.0, 120.0, 128.0, 160.0, 180.0, 200.0)) {
            val profile = assertNotNull(DjBeatDetector.detect(clicks(bpm)), "$bpm BPM")
            assertEquals(bpm, profile.bpm.toDouble(), 0.25, "$bpm BPM")
            assertTrue(profile.confidence >= 0.9f)
        }
    }

    @Test
    fun `grid fitting permits a beat at the very first decoded frame`() {
        for (bpm in listOf(60.0, 120.0, 123.45, 128.0, 180.0, 200.0)) {
            val profile = assertNotNull(DjBeatDetector.detect(clicks(bpm, offsetMs = 0.0)), "$bpm BPM")
            assertEquals(bpm, profile.bpm.toDouble(), 0.25)
            assertTrue(profile.firstBeatMs >= 0.0)
        }
    }

    @Test
    fun `beat phase includes absolute media offset`() {
        val profile = assertNotNull(
            DjBeatDetector.detect(clicks(128.0, offsetMs = 170.0), analyzedStartMs = 148_000L),
        )
        assertEquals(148_170.0, profile.firstBeatMs, 6.0)
        assertEquals(148_000L, profile.analyzedStartMs)
        assertEquals(180_000L, profile.analyzedEndMs)
    }

    @Test
    fun `first supported beat does not extrapolate into a silent introduction`() {
        val profile = assertNotNull(DjBeatDetector.detect(clicks(120.0, offsetMs = 2_170.0)))
        assertEquals(2_170.0, profile.firstBeatMs, 5.0)
    }

    @Test
    fun `silence and constant energy do not invent a tempo`() {
        assertNull(DjBeatDetector.detect(FloatArray(3_200)))
        assertNull(DjBeatDetector.detect(FloatArray(3_200) { 0.1f }))
    }

    @Test
    fun `uncorrelated noise never establishes a beat grid`() {
        repeat(8) { seed ->
            val random = Random(seed)
            assertNull(DjBeatDetector.detect(FloatArray(3_200) { random.nextFloat() }), "seed $seed")
        }
    }

    @Test
    fun `irregular pulse intervals and large tempo drift are rejected`() {
        val random = Random(23)
        val irregular = FloatArray(3_200)
        var time = 120.0
        while (time < 32_000) {
            irregular[(time / 10).roundToInt().coerceAtMost(irregular.lastIndex)] = 1f
            time += random.nextDouble(320.0, 740.0)
        }
        assertNull(DjBeatDetector.detect(irregular))
        val drift = FloatArray(3_200)
        time = 120.0
        var period = 600.0
        while (time < 32_000) {
            drift[(time / 10).roundToInt().coerceAtMost(drift.lastIndex)] = 1f
            time += period
            period -= 3.0
        }
        assertNull(DjBeatDetector.detect(drift))
    }

    @Test
    fun `alternating accents preserve the supported native pulse instead of halving tempo`() {
        val profile = assertNotNull(DjBeatDetector.detect(clicks(120.0, alternatingAccent = true)))
        assertEquals(120.0, profile.bpm.toDouble(), 0.25)
    }

    @Test
    fun `missing isolated beats and quiet uncorrelated onsets retain a stable grid`() {
        val signal = clicks(128.0)
        val random = Random(51)
        signal.indices.forEach { index -> signal[index] += random.nextFloat() * 0.015f }
        signal[(170.0 / 10).roundToInt()] = 0f
        val profile = assertNotNull(DjBeatDetector.detect(signal))
        assertEquals(128.0, profile.bpm.toDouble(), 0.3)
        assertTrue(profile.confidence >= 0.85f)
    }

    @Test
    fun `short sparse invalid and mismatched data are rejected`() {
        assertNull(DjBeatDetector.detect(clicks(120.0).copyOf(700)))
        assertNull(DjBeatDetector.detect(FloatArray(3_200).also { it[100] = 1f }))
        assertNull(DjBeatDetector.detect(clicks(120.0).also { it[200] = Float.NaN }))
        assertNull(DjBeatDetector.detect(clicks(120.0), frameDurationMs = 0.0))
        assertNull(DjBeatDetector.detect(clicks(120.0), analyzedStartMs = -1))
        assertNull(DjBeatDetector.detect(clicks(120.0), analyzedEndMs = 20_000))
    }

    @Test
    fun `beat phase does not drift when extrapolated across analyzed window`() {
        val profile = assertNotNull(DjBeatDetector.detect(clicks(123.45, offsetMs = 170.0)))
        val predicted = profile.firstBeatMs + 50 * 60_000.0 / profile.bpm
        val actual = 170.0 + 50 * 60_000.0 / 123.45
        assertTrue(abs(predicted - actual) < 6.0)
    }

    private fun clicks(bpm: Double, offsetMs: Double = 170.0, alternatingAccent: Boolean = false): FloatArray {
        val envelope = FloatArray(3_200)
        var time = offsetMs
        var beat = 0
        while (time < envelope.size * 10) {
            val index = (time / 10).roundToInt()
            if (index in envelope.indices) envelope[index] = if (alternatingAccent && beat % 2 == 1) 0.45f else 1f
            time += 60_000.0 / bpm
            beat++
        }
        return envelope
    }
}
