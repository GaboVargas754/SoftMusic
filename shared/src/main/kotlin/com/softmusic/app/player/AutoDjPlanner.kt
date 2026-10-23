package com.softmusic.app.player

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/** Plans against media positions; [AutoDjPlan.durationMs] is elapsed playback time. */
object AutoDjPlanner {
    fun plan(
        outgoing: DjBeatProfile,
        incoming: DjBeatProfile,
        outgoingEndMs: Long,
        incomingDurationMs: Long,
        settings: AutoDjSettings,
        earliestTriggerMs: Long = 0L,
    ): AutoDjPlan? {
        if (!outgoing.isReliable() || !incoming.isReliable()) return null
        if (outgoingEndMs <= 0L || incomingDurationMs <= 0L || earliestTriggerMs < 0L) return null

        val configuration = settings.sanitized()
        val outgoingPeriodMs = 60_000.0 / outgoing.bpm
        val duration = configuration.beats * outgoingPeriodMs
        val speed = if (configuration.syncEnabled) matchingSpeed(outgoing, incoming) ?: return null else 1f
        val incomingPeriodMs = 60_000.0 / incoming.bpm
        val incomingStart = beatAtOrAfter(
            incoming.firstBeatMs,
            incomingPeriodMs,
            incoming.analyzedStartMs.toDouble() + INCOMING_PREPARATION_MS,
        )
        val incomingEnd = minOf(incomingDurationMs, incoming.analyzedEndMs).toDouble()
        // The incoming player consumes speed * elapsed time in its original media.
        if (incomingStart + duration * speed > incomingEnd + ROUNDING_TOLERANCE_MS) return null

        val end = minOf(outgoingEndMs, outgoing.analyzedEndMs).toDouble()
        val latestStart = end - duration
        val trigger = outgoing.firstBeatMs +
            floor((latestStart - outgoing.firstBeatMs + GRID_EPSILON_MS) / outgoingPeriodMs) * outgoingPeriodMs
        val earliestStart = maxOf(earliestTriggerMs.toDouble(), outgoing.analyzedStartMs.toDouble(), outgoing.firstBeatMs)
        if (!trigger.isFinite() || trigger < earliestStart - GRID_EPSILON_MS) return null
        if (trigger + duration > end + ROUNDING_TOLERANCE_MS) return null

        val triggerPositionMs = trigger.roundToLong()
        val incomingStartPositionMs = incomingStart.roundToLong()
        val durationMs = duration.roundToLong()
        // Quantize only once and recheck the actual integer positions players use.
        if (triggerPositionMs.toDouble() + durationMs > end) return null
        if (incomingStartPositionMs + durationMs.toDouble() * speed > incomingEnd + GRID_EPSILON_MS) return null

        return AutoDjPlan(
            triggerPositionMs = triggerPositionMs,
            incomingStartPositionMs = incomingStartPositionMs,
            durationMs = durationMs,
            incomingSpeed = speed,
            outgoingBpm = outgoing.bpm,
            incomingBpm = incoming.bpm,
            synchronized = configuration.syncEnabled,
        )
    }

    private fun matchingSpeed(outgoing: DjBeatProfile, incoming: DjBeatProfile): Float? {
        val nativeSpeed = outgoing.bpm / incoming.bpm
        if (nativeSpeed in MIN_SPEED..MAX_SPEED) return nativeSpeed
        // Half/double interpretations share actual beats. Require stronger evidence
        // and a normal musical tempo before treating subdivisions as equivalent.
        if (minOf(outgoing.confidence, incoming.confidence) < HARMONIC_CONFIDENCE) return null
        return listOf(0.5f, 2f)
            .map { factor -> incoming.bpm * factor }
            .filter { tempo -> tempo in MIN_HARMONIC_BPM..MAX_HARMONIC_BPM }
            .map { tempo -> outgoing.bpm / tempo }
            .filter { candidate -> candidate in MIN_SPEED..MAX_SPEED }
            .minByOrNull { candidate -> abs(candidate - 1f) }
    }

    private fun beatAtOrAfter(anchorMs: Double, periodMs: Double, positionMs: Double): Double =
        anchorMs + maxOf(0.0, ceil((positionMs - anchorMs - GRID_EPSILON_MS) / periodMs)) * periodMs

    private fun DjBeatProfile.isReliable(): Boolean =
        bpm.isFinite() && bpm in MIN_BPM..MAX_BPM &&
            confidence.isFinite() && confidence in MIN_CONFIDENCE..1f &&
            firstBeatMs.isFinite() && analyzedStartMs >= 0L && analyzedEndMs > analyzedStartMs &&
            firstBeatMs >= analyzedStartMs && firstBeatMs < analyzedEndMs &&
            analyzedEndMs - analyzedStartMs >= MIN_PROFILE_DURATION_MS

    private const val MIN_CONFIDENCE = 0.72f
    private const val HARMONIC_CONFIDENCE = 0.88f
    private const val MIN_SPEED = 0.94f
    private const val MAX_SPEED = 1.06f
    private const val MIN_BPM = 40f
    private const val MAX_BPM = 240f
    private const val MIN_HARMONIC_BPM = 60f
    private const val MAX_HARMONIC_BPM = 200f
    private const val MIN_PROFILE_DURATION_MS = 4_000L
    private const val INCOMING_PREPARATION_MS = 1_500L
    private const val GRID_EPSILON_MS = 0.0001
    private const val ROUNDING_TOLERANCE_MS = 0.5
}
