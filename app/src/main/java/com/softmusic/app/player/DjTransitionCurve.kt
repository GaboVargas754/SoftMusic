package com.softmusic.app.player

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Gain envelope shared by Classic and Expert transitions. */
internal data class DjTransitionCurve(
    val incomingPower: Double,
    val outgoingPower: Double,
    val mixHeadroom: Float,
) {
    fun curvedIncomingProgress(progress: Float): Float = progress.curvedBy(incomingPower)

    fun curvedOutgoingProgress(progress: Float): Float = progress.curvedBy(outgoingPower)

    fun headroomAt(progress: Float): Float {
        val centerWeight = sin(progress.coerceIn(0f, 1f).toDouble() * PI).toFloat()
        return 1f - (1f - mixHeadroom.coerceIn(0f, 1f)) * centerWeight
    }

    private fun Float.curvedBy(power: Double): Float {
        return coerceIn(0f, 1f).toDouble().pow(power).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        val Classic = DjTransitionCurve(incomingPower = 1.0, outgoingPower = 1.0, mixHeadroom = 1f)
        val ExpertSilence = DjTransitionCurve(incomingPower = 0.90, outgoingPower = 1.05, mixHeadroom = 0.96f)
        val ExpertSilenceStrongIncoming = DjTransitionCurve(incomingPower = 1.18, outgoingPower = 1.05, mixHeadroom = 0.95f)
        val ExpertNormal = DjTransitionCurve(incomingPower = 1.15, outgoingPower = 0.95, mixHeadroom = 0.93f)
        val ExpertStrongIncoming = DjTransitionCurve(incomingPower = 1.35, outgoingPower = 0.90, mixHeadroom = 0.91f)
    }
}

internal data class DjTransitionGains(val outgoing: Float, val incoming: Float)

internal fun DjTransitionCurve.gainsAt(
    progress: Float,
    incomingStartVolumeScale: Float = 1f,
    incomingLevelGain: Float = 1f,
): DjTransitionGains {
    if (progress <= 0f) return DjTransitionGains(outgoing = 1f, incoming = 0f)
    if (progress >= 1f) return DjTransitionGains(outgoing = 0f, incoming = 1f)
    val incomingProgress = curvedIncomingProgress(progress)
    val outgoingProgress = curvedOutgoingProgress(progress)
    val headroom = headroomAt(progress)
    val startScale = incomingStartVolumeScale.coerceIn(0.70f, 1f)
    val incomingScale = startScale + (1f - startScale) * incomingProgress * incomingProgress
    val levelGain = if (incomingLevelGain.isFinite()) incomingLevelGain.coerceIn(0.4f, 1f) else 1f
    // Release compensation during the overlap, with zero slope at both ends.
    // The handed-off track then continues at its original level without a DSP ramp.
    val recovery = progress * progress * (3f - 2f * progress)
    val transitionLevel = levelGain + (1f - levelGain) * recovery
    return DjTransitionGains(
        outgoing = cos(outgoingProgress.toDouble() * PI / 2.0).toFloat() * headroom,
        incoming = sin(incomingProgress.toDouble() * PI / 2.0).toFloat() * incomingScale * headroom * transitionLevel,
    )
}
