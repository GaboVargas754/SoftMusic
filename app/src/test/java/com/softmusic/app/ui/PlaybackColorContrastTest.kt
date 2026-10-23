package com.softmusic.app.ui

import kotlin.math.abs
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackColorContrastTest {
    @Test
    fun brightArtworkTonesMeetWhiteTextContrast() {
        val colors = listOf(
            0xFFFFFFFF.toInt(), 0xFFFFFF00.toInt(), 0xFFFFEEBB.toInt(),
            0xFFCCCCCC.toInt(), 0xFF999999.toInt(), 0xFFFF0000.toInt(),
            0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFF2CE4D4.toInt(),
        )
        colors.forEach { original ->
            val result = whitePlaybackArgb(original)
            assertTrue("White text contrast for ${original.toUInt().toString(16)}", contrast(result) >= 6.5)
            assertEquals(255, result ushr 24)
        }
    }

    @Test
    fun alreadySafeDarkTonesRemainExact() {
        listOf(0xFF000000.toInt(), 0xFF151515.toInt(), 0xFF083A38.toInt(),
            0xFF17234B.toInt(), 0xFF402811.toInt(), 0xFF0000FF.toInt()).forEach { color ->
            assertTrue(contrast(color) >= 6.5)
            assertEquals(color, whitePlaybackArgb(color))
        }
    }

    @Test
    fun inputAlphaIsRemovedAndOpaqueRgbStillMeetsContrast() {
        assertEquals(0xFF112233.toInt(), whitePlaybackArgb(0x22112233))
        assertEquals(0xFF000000.toInt(), whitePlaybackArgb(0x00000000))
        assertEquals(whitePlaybackArgb(0xFFFFFFFF.toInt()), whitePlaybackArgb(0x00FFFFFF))
        assertTrue(contrast(whitePlaybackArgb(0x80FFFFFF.toInt())) >= 6.5)
    }

    @Test
    fun playButtonCanUseTheLessRestrictiveRequestedContrast() {
        val original = 0xFF2CE4D4.toInt()
        val button = whitePlaybackArgb(original, minimumContrast = 4.5)
        val backdrop = whitePlaybackArgb(original, minimumContrast = 6.5)
        assertTrue(contrast(button) >= 4.5)
        assertTrue(contrast(backdrop) >= 6.5)
        assertTrue(luminance(channels(button)) > luminance(channels(backdrop)))
        assertEquals(0xFFFFFFFF.toInt(), whitePlaybackArgb(0xFFFFFFFF.toInt(), 1.0))
        assertEquals(0xFF000000.toInt(), whitePlaybackArgb(0xFFFFFFFF.toInt(), 21.0))
    }

    @Test
    fun scalingPreservesArtworkHueAndDoesNotIntroduceAColorCastToNeutrals() {
        listOf(0xFFFF0000.toInt(), 0xFFFFFF00.toInt(), 0xFF00FF00.toInt(),
            0xFF0000FF.toInt(), 0xFFFF8800.toInt(), 0xFF2CE4D4.toInt()).forEach { color ->
            val difference = abs(hue(color) - hue(whitePlaybackArgb(color)))
            assertTrue("Hue changed for ${color.toUInt().toString(16)}", minOf(difference, 360.0 - difference) < 2.0)
        }
        val gray = channels(whitePlaybackArgb(0xFFCCCCCC.toInt()))
        assertEquals(gray[0], gray[1], 0.0)
        assertEquals(gray[1], gray[2], 0.0)
    }

    @Test
    fun sampledRgbCubeMeetsTheRequestedContrastWithoutLighteningChannels() {
        for (red in 0..255 step 17) for (green in 0..255 step 17) for (blue in 0..255 step 17) {
            val original = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
            for (minimum in listOf(4.5, 6.5)) {
                val result = whitePlaybackArgb(original, minimum)
                assertTrue(contrast(result) >= minimum)
                val output = channels(result)
                val input = channels(original)
                assertTrue(output.indices.all { output[it] <= input[it] })
                assertEquals(result, whitePlaybackArgb(result, minimum))
            }
        }
    }

    @Test
    fun safeBackdropPixelsStaySafeThroughSaturationAndAlphaBlends() {
        val tones = listOf(0xFFFFFFFF.toInt(), 0xFFFFFF00.toInt(), 0xFFFF0000.toInt(),
            0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFF2CE4D4.toInt(), 0xFF101217.toInt())
            .map { channels(whitePlaybackArgb(it)) }
        for (base in tones) for (pixel in tones) {
            // ColorMatrix.setToSaturation(0.8f), followed by the existing image and veil opacities.
            val gray = pixel[0] * 0.213 + pixel[1] * 0.715 + pixel[2] * 0.072
            val saturated = DoubleArray(3) { pixel[it] * 0.8 + gray * 0.2 }
            val image = blend(base, saturated, 0.65)
            for (veilAlpha in listOf(0.25, 0.65, 0.9)) {
                assertTrue(1.05 / (luminance(blend(image, base, veilAlpha)) + 0.05) >= 6.5)
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun impossibleContrastIsRejected() {
        whitePlaybackArgb(0xFFFFFFFF.toInt(), 22.0)
    }

    private fun contrast(argb: Int): Double = 1.05 / (luminance(channels(argb)) + 0.05)

    private fun channels(argb: Int) = doubleArrayOf(
        ((argb ushr 16) and 255) / 255.0,
        ((argb ushr 8) and 255) / 255.0,
        (argb and 255) / 255.0,
    )

    private fun luminance(rgb: DoubleArray): Double {
        val linear = rgb.map { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return linear[0] * 0.2126 + linear[1] * 0.7152 + linear[2] * 0.0722
    }

    private fun blend(base: DoubleArray, overlay: DoubleArray, alpha: Double) =
        DoubleArray(3) { base[it] * (1.0 - alpha) + overlay[it] * alpha }

    private fun hue(argb: Int): Double {
        val rgb = channels(argb)
        val maximum = rgb.max()
        val minimum = rgb.min()
        val delta = maximum - minimum
        if (delta == 0.0) return 0.0
        val segment = when (maximum) {
            rgb[0] -> (rgb[1] - rgb[2]) / delta
            rgb[1] -> (rgb[2] - rgb[0]) / delta + 2.0
            else -> (rgb[0] - rgb[1]) / delta + 4.0
        }
        return (segment * 60.0 + 360.0) % 360.0
    }
}
