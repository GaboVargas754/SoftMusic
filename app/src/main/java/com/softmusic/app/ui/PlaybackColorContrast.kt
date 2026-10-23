package com.softmusic.app.ui

import kotlin.math.pow

/** Returns an opaque tone with the requested contrast against white, darkening toward black only. */
internal fun whitePlaybackArgb(argb: Int, minimumContrast: Double = 6.5): Int {
    require(minimumContrast.isFinite() && minimumContrast in 1.0..21.0) {
        "White contrast must be finite and between 1 and 21"
    }
    val opaque = argb or (0xFF shl 24)
    val maximumLuminance = 1.05 / minimumContrast - 0.05
    if (playbackLuminance(opaque) <= maximumLuminance) return opaque

    val red = (opaque ushr 16) and 0xFF
    val green = (opaque ushr 8) and 0xFF
    val blue = opaque and 0xFF
    // A shared RGB multiplier retains hue. Integer division rounds toward the safer, darker tone.
    fun scaled(multiplier: Int): Int = (0xFF shl 24) or
        ((red * multiplier / 65_535) shl 16) or
        ((green * multiplier / 65_535) shl 8) or
        (blue * multiplier / 65_535)

    var safe = 0
    var unsafe = 65_535
    while (safe < unsafe) {
        val candidate = (safe + unsafe + 1) / 2
        if (playbackLuminance(scaled(candidate)) <= maximumLuminance) {
            safe = candidate
        } else {
            unsafe = candidate - 1
        }
    }
    return scaled(safe)
}

private fun playbackLuminance(argb: Int): Double =
    0.2126 * playbackLinearChannel((argb ushr 16) and 0xFF) +
        0.7152 * playbackLinearChannel((argb ushr 8) and 0xFF) +
        0.0722 * playbackLinearChannel(argb and 0xFF)

private fun playbackLinearChannel(component: Int): Double {
    val value = component / 255.0
    return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
}
