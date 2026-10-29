package com.softmusic.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.sqrt

class SmartSongAnalyzerTest {
    @Test
    fun `head RMS weights energy by measured duration`() {
        val level = DjSilenceAnalyzer.AudioProfile(
            startMs = 0L,
            endMs = 400L,
            windows = listOf(window(0, 100, 0.1f), window(100, 400, 0.3f)),
        )

        val result = smartSongProfile(beat(), level)

        assertEquals(120f, result.bpm)
        assertEquals(0.95f, result.confidence)
        assertEquals(sqrt((0.01 + 0.09 * 3) / 4).toFloat(), result.rms!!, 0.00001f)
    }

    @Test
    fun `incomplete audio and unreliable pulse stay unknown`() {
        val level = DjSilenceAnalyzer.AudioProfile(0, 100, listOf(window(0, 100, 0.2f)), isComplete = false)

        val result = smartSongProfile(beat().copy(confidence = 0.4f), level)

        assertNull(result.bpm)
        assertEquals(0f, result.confidence)
        assertNull(result.rms)
    }

    @Test
    fun `missing tempo still preserves independently measured level`() {
        val level = DjSilenceAnalyzer.AudioProfile(0, 100, listOf(window(0, 100, 0.2f)))

        val result = smartSongProfile(null, level)

        assertNull(result.bpm)
        assertEquals(0.2f, result.rms)
    }

    @Test
    fun `nonfinite measurements are rejected`() {
        val level = DjSilenceAnalyzer.AudioProfile(0, 100, listOf(window(0, 100, Float.NaN)))

        val result = smartSongProfile(beat().copy(bpm = Float.NaN), level)

        assertEquals(SmartSongProfile(), result)
    }

    private fun beat() = DjBeatProfile(120f, 0.95f, 100.0, 0, 32_000)

    private fun window(start: Long, end: Long, rms: Float) = DjSilenceAnalyzer.AudioWindow(start, end, rms, peak = rms)
}
