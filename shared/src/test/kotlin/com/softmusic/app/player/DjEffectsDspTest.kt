package com.softmusic.app.player

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DjEffectsDspTest {
    @Test
    fun `level attenuation ramps without steps and survives transition effect reset`() {
        val dsp = DjEffectsDsp(48_000, 1)
        dsp.setLevelGain(0.4f)
        val ramp = process(dsp, ShortArray(48_000) { 12_000 })
        assertTrue(ramp.first() > 11_990)
        assertTrue(abs(ramp.last().toInt() - 4_800) <= 1)
        assertTrue((1 until ramp.size).all { ramp[it] <= ramp[it-1] && ramp[it-1] - ramp[it] <= 2 })
        dsp.setTransition(AutoDjEffect.BassSwap, 0.5f, 0.65f, 120f, incoming = true)
        process(dsp, ShortArray(4_800) { 12_000 })
        dsp.requestReset()
        val afterReset = process(dsp, ShortArray(4_800) { 12_000 })
        assertTrue(abs(afterReset.last().toInt() - 4_800) <= 1)
        dsp.setLevelGain(1f)
        val restored = process(dsp, ShortArray(48_000) { 12_000 })
        assertEquals(12_000, restored.last().toInt())
    }

    @Test
    fun `normalization never boosts and initializes stereo gains equally`() {
        val exact = ShortArray(65_536) { (it - 32_768).toShort() }
        for (gain in listOf(2f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val dsp = DjEffectsDsp(48_000, 2)
            dsp.setLevelGain(gain, immediate = true)
            assertContentEquals(exact, process(dsp, exact))
        }
        val dsp = DjEffectsDsp(48_000, 2)
        dsp.setLevelGain(-3f, immediate = true)
        val stereo = process(dsp, ShortArray(9_600) { if (it % 2 == 0) 10_000 else -10_000 })
        assertTrue(stereo.indices.all { abs(stereo[it].toInt() - if (it % 2 == 0) 4_000 else -4_000) <= 1 })
        dsp.reset()
        assertContentEquals(exact, process(dsp, exact))
    }
    @Test
    fun `three band EQ is audible by band and resets smoothly to bit exact dry`() {
        fun withEq(frequency: Double, eq: MixEq): Double {
            val dsp = DjEffectsDsp(48_000, 1).also { it.setDeckEq(eq) }
            val signal = tone(48_000, frequency)
            return rms(process(dsp, signal)) / rms(signal)
        }
        assertTrue(withEq(45.0, MixEq(lowDb = -12f)) < .4)
        assertTrue(withEq(1_000.0, MixEq(midDb = -12f)) < .55)
        assertTrue(withEq(12_000.0, MixEq(highDb = -12f)) < .65)
        assertTrue(withEq(45.0, MixEq(lowDb = 6f)) > 1.7)
        assertTrue(withEq(12_000.0, MixEq(lowDb = -12f)) > .9)
        assertTrue(withEq(45.0, MixEq(highDb = -12f)) > .95)
        val dsp = DjEffectsDsp(48_000, 1)
        val exact = ShortArray(65_536) { (it - 32_768).toShort() }
        dsp.setDeckEq(MixEq())
        assertContentEquals(exact, process(dsp, exact))
        dsp.setDeckEq(MixEq(-12f, -12f, -12f))
        val settled = process(dsp, ShortArray(4_800) { 12_000 })
        dsp.requestReset()
        val fading = process(dsp, ShortArray(4_800) { 12_000 })
        assertTrue(abs(fading.first().toInt() - settled.last().toInt()) < 100)
        assertTrue((1 until fading.size).all { abs(fading[it].toInt() - fading[it - 1].toInt()) < 100 })
        assertContentEquals(exact, process(dsp, exact))
        val separate = DjEffectsDsp(48_000, 1)
        assertContentEquals(exact, process(separate, exact))
    }

    @Test
    fun `empty pipeline buffers never copy onto themselves`() {
        for (rate in listOf(44_100, 48_000)) {
            for (channels in 1..2) {
                val dsp = DjEffectsDsp(rate, channels)
                for (effect in AutoDjEffect.entries) {
                    dsp.setTransition(effect, 0.5f, 0.8f, 120f, incoming = false)
                    // Media3 uses a shared empty direct buffer while draining its pipeline.
                    val empty = ByteBuffer.allocateDirect(0)
                    dsp.processPcm16(empty, empty)
                    assertEquals(0, empty.position())
                    assertEquals(0, empty.limit())

                    val consumed = ByteBuffer.allocateDirect(4).apply {
                        putInt(0x12345678)
                    }
                    dsp.processPcm16(consumed, consumed)
                    assertEquals(4, consumed.position())
                    assertEquals(4, consumed.limit())
                    assertEquals(0x12345678, consumed.getInt(0))
                }
            }
        }
    }

    @Test
    fun `bypass and zero strength preserve every PCM16 value in mono and stereo`() {
        val samples = ShortArray(65_536) { (it - 32_768).toShort() }
        for (rate in listOf(44_100, 48_000)) {
            for (channels in 1..2) {
                val dsp = DjEffectsDsp(rate, channels)
                assertContentEquals(samples, process(dsp, samples))
                for (effect in AutoDjEffect.entries) {
                    dsp.setTransition(effect, 0.5f, 0f, 120f, incoming = false)
                    assertContentEquals(samples, process(dsp, samples), "$effect, $rate, $channels")
                }
                dsp.setTransition(AutoDjEffect.EchoOut, 0.5f, 1f, 120f, incoming = true)
                assertContentEquals(samples, process(dsp, samples))
            }
        }
    }

    @Test
    fun `bass passes between decks while retaining high frequencies`() {
        val rate = 48_000
        val low = tone(rate, 65.0)
        val high = tone(rate, 4_000.0)
        val dryLow = rms(low)
        val outgoingStart = effected(low, AutoDjEffect.BassSwap, 0f, incoming = false)
        val outgoingEnd = effected(low, AutoDjEffect.BassSwap, 1f, incoming = false)
        val incomingStart = effected(low, AutoDjEffect.BassSwap, 0f, incoming = true)
        val incomingEnd = effected(low, AutoDjEffect.BassSwap, 1f, incoming = true)

        assertContentEquals(low, outgoingStart)
        assertContentEquals(low, incomingEnd)
        assertTrue(rms(outgoingEnd) / dryLow < 0.4, "Outgoing bass must leave the mix")
        assertTrue(rms(incomingStart) / dryLow < 0.4, "Incoming bass must initially be restrained")
        assertEquals(rms(outgoingEnd), rms(incomingStart), 0.001)
        assertTrue(rms(effected(high, AutoDjEffect.BassSwap, 1f, incoming = false)) / rms(high) > 0.9)

        val halfwayOut = effected(low, AutoDjEffect.BassSwap, 0.5f, incoming = false)
        val halfwayIn = effected(low, AutoDjEffect.BassSwap, 0.5f, incoming = true)
        assertEquals(rms(halfwayOut), rms(halfwayIn), 0.001)
        assertTrue(rms(halfwayOut) in rms(outgoingEnd)..dryLow)
    }

    @Test
    fun `filter removes outgoing lows and opens incoming highs with dry endpoints`() {
        val low = tone(48_000, 65.0)
        val high = tone(48_000, 8_000.0)
        assertTrue(rms(effected(low, AutoDjEffect.FilterSweep, 0.5f, incoming = false)) / rms(low) < 0.25)
        assertTrue(rms(effected(high, AutoDjEffect.FilterSweep, 0.5f, incoming = false)) / rms(high) > 0.85)
        assertTrue(rms(effected(low, AutoDjEffect.FilterSweep, 0.5f, incoming = true)) / rms(low) > 0.95)
        assertTrue(rms(effected(high, AutoDjEffect.FilterSweep, 0.5f, incoming = true)) / rms(high) < 0.35)
        for (incoming in listOf(false, true)) {
            for (progress in listOf(0f, 1f)) {
                assertContentEquals(high, effected(high, AutoDjEffect.FilterSweep, progress, incoming))
            }
        }
    }

    @Test
    fun `echo delay follows half beats at both sample rates without stereo crossfeed`() {
        for (rate in listOf(44_100, 48_000)) {
            for (bpm in listOf(90f, 120f, 180f)) {
                val channels = 2
                val dsp = DjEffectsDsp(rate, channels)
                dsp.setTransition(AutoDjEffect.EchoOut, 0.5f, 1f, bpm, incoming = false)
                process(dsp, ShortArray((rate * 0.025f).roundToInt() * channels))
                val delayFrames = (rate * 30f / bpm).roundToInt()
                val impulse = ShortArray((delayFrames * 3 + 1) * channels).also { it[0] = 16_000 }

                val output = process(dsp, impulse)

                assertTrue(output[0] > 0)
                assertTrue(output[delayFrames * channels] > 1_000, "First echo should be audible")
                assertTrue(output[delayFrames * channels * 2] in 1 until output[delayFrames * channels].toInt())
                for (frame in 1 until delayFrames) assertEquals(0, output[frame * channels].toInt())
                for (frame in 0 until output.size / channels) assertEquals(0, output[frame * channels + 1].toInt())

                dsp.reset()
                dsp.setTransition(AutoDjEffect.EchoOut, 0.5f, 1f, bpm, incoming = false)
                assertTrue(process(dsp, ShortArray(impulse.size)).all { it == 0.toShort() })
            }
        }
    }

    @Test
    fun `reset fades to exact dry without a discontinuity and clears echo before reuse`() {
        val rate = 48_000
        val dsp = DjEffectsDsp(rate, 1)
        dsp.setTransition(AutoDjEffect.BassSwap, 0f, 1f, 120f, incoming = true)
        val settled = process(dsp, ShortArray(rate / 10) { 16_000 })
        assertEquals(0, settled.last().toInt())

        dsp.requestReset()
        val fading = process(dsp, ShortArray(rate / 10) { 16_000 })

        assertTrue(fading.first() in 0..100)
        assertTrue((1 until fading.size).all { abs(fading[it].toInt() - fading[it - 1].toInt()) < 100 })
        assertEquals(16_000, fading.last().toInt())
        val exact = ShortArray(1_024) { (it * 61 - 32_768).toShort() }
        assertContentEquals(exact, process(dsp, exact))

        dsp.setTransition(AutoDjEffect.EchoOut, 0.5f, 1f, 120f, incoming = false)
        process(dsp, ShortArray(rate) { 12_000 })
        dsp.requestReset()
        // Starting another phrase before the fade ends must still discard the old song's delay.
        dsp.setTransition(AutoDjEffect.EchoOut, 0.5f, 1f, 90f, incoming = false)
        val reused = process(dsp, ShortArray(rate))
        assertTrue(reused.drop((rate * 0.012f).roundToInt() + 2).all { it == 0.toShort() })
    }

    @Test
    fun `processing is independent of input chunk size and player instances`() {
        val random = Random(84)
        val samples = ShortArray(48_000 * 2) { random.nextInt(-20_000, 20_001).toShort() }
        for (effect in AutoDjEffect.entries) {
            val whole = DjEffectsDsp(48_000, 2)
            val chunked = DjEffectsDsp(48_000, 2)
            whole.setTransition(effect, 0.6f, 0.85f, 128f, incoming = false)
            chunked.setTransition(effect, 0.6f, 0.85f, 128f, incoming = false)
            val expected = process(whole, samples)
            val actual = ShortArray(samples.size)
            var offset = 0
            while (offset < samples.size) {
                val count = minOf(1_024, samples.size - offset)
                process(chunked, samples.copyOfRange(offset, offset + count)).copyInto(actual, offset)
                offset += count
            }
            assertContentEquals(expected, actual, effect.name)
            assertContentEquals(samples, process(DjEffectsDsp(48_000, 2), samples))
        }
    }

    @Test
    fun `extreme samples and malformed controls remain bounded and silent input stays silent`() {
        val extremes = ShortArray(48_000 * 2) { if ((it / 32) % 2 == 0) Short.MAX_VALUE else Short.MIN_VALUE }
        for (effect in AutoDjEffect.entries.filter { it != AutoDjEffect.None }) {
            for (rate in listOf(44_100, 48_000)) {
                val dsp = DjEffectsDsp(rate, 2)
                for (progress in listOf(Float.NaN, Float.NEGATIVE_INFINITY, -2f, 0.25f, 0.5f, 2f)) {
                    dsp.setTransition(effect, progress, Float.POSITIVE_INFINITY, Float.NaN, incoming = false)
                    assertContentEquals(extremes, process(dsp, extremes), "Invalid strength must bypass")
                    dsp.setTransition(effect, progress, 1f, Float.POSITIVE_INFINITY, incoming = false)
                    val output = process(dsp, extremes)
                    assertTrue(output.all { abs(it.toInt()) <= 32_768 })
                    if (progress == 0.5f) {
                        assertTrue(output.all { abs(it.toInt()) < 32_767 }, "Active peaks must avoid hard clipping")
                    }
                    // A fresh engine cannot manufacture a tail or nonfinite output from silence.
                    val silent = DjEffectsDsp(rate, 2)
                    silent.setTransition(effect, progress, 1f, 120f, incoming = false)
                    assertTrue(process(silent, ShortArray(4_096)).all { it == 0.toShort() })
                    dsp.reset()
                }
            }
        }
    }

    private fun effected(samples: ShortArray, effect: AutoDjEffect, progress: Float, incoming: Boolean): ShortArray =
        process(DjEffectsDsp(48_000, 1).also { it.setTransition(effect, progress, 1f, 120f, incoming) }, samples)

    private fun process(dsp: DjEffectsDsp, samples: ShortArray): ShortArray {
        val input = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { input.putShort(it) }
        input.flip()
        val output = ByteBuffer.allocate(input.remaining()).order(ByteOrder.LITTLE_ENDIAN)
        dsp.processPcm16(input, output)
        assertEquals(input.limit(), input.position())
        output.flip()
        return ShortArray(samples.size) { output.short }
    }

    private fun tone(rate: Int, frequency: Double): ShortArray =
        ShortArray(rate) { (sin(2.0 * PI * frequency * it / rate) * 12_000).roundToInt().toShort() }

    private fun rms(samples: ShortArray): Double {
        val settled = samples.drop(samples.size / 5)
        return sqrt(settled.sumOf { it.toDouble() * it.toDouble() } / settled.size)
    }
}
