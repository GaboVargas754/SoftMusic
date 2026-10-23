package com.softmusic.app.player

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/** Plans only against measured durations. Explicit cues are never moved to make a beat grid fit. */
object CollectionTransitionPlanner {
    data class Result(val plan: AutoDjPlan, val message: String? = null)

    fun plan(
        transition: MixTransitionSettings,
        collectionSettings: AutoDjSettings,
        outgoingDurationMs: Long,
        incomingDurationMs: Long,
        earliestTriggerMs: Long,
        outgoing: DjBeatProfile? = null,
        incoming: DjBeatProfile? = null,
        outgoingAudibleEndMs: Long = outgoingDurationMs,
    ): Result? {
        val custom = transition.sanitized()
        if (custom.mode == MixTransitionMode.Off || outgoingDurationMs <= 0 || incomingDurationMs <= 0 || earliestTriggerMs < 0) return null
        val settings = collectionSettings.sanitized()
        val isCustom = custom.mode == MixTransitionMode.Custom
        val out = outgoing?.takeIf { it.isReliable() }
        val next = incoming?.takeIf { it.isReliable() }
        val end = minOf(outgoingDurationMs, outgoingAudibleEndMs) - if (isCustom) 0L else HANDOFF_SAFETY_MS
        if (custom.mode == MixTransitionMode.Automatic && out != null && next != null) {
            AutoDjPlanner.plan(out, next, end, incomingDurationMs, settings, earliestTriggerMs)?.let { return Result(it) }
        }
        var duration = if (isCustom) custom.durationMs else {
            (settings.beats * 60_000.0 / (out?.bpm ?: 120f)).roundToLong().coerceIn(1_000L, 24_000L)
        }
        if (!isCustom) {
            val available = end - earliestTriggerMs
            if (available < MIN_AUTOMATIC_OVERLAP_MS) return null
            duration = minOf(duration, available)
        }
        val syncRequested = if (isCustom) custom.syncEnabled else settings.syncEnabled
        val automaticOutgoing = end - duration
        var trigger = if (isCustom) custom.outgoingStartMs ?: automaticOutgoing else automaticOutgoing
        var entry = if (isCustom) custom.incomingStartMs ?: 0L else 0L
        if (syncRequested && out != null && next != null) {
            if (!isCustom || custom.outgoingStartMs == null) {
                val aligned = beatAtOrBefore(out, automaticOutgoing)
                trigger = if (!isCustom && aligned < earliestTriggerMs) {
                    val nextBeat = beatAtOrAfter(out, earliestTriggerMs)
                    if (end - nextBeat >= MIN_AUTOMATIC_OVERLAP_MS) nextBeat else automaticOutgoing
                } else aligned
            }
            if (!isCustom || custom.incomingStartMs == null) {
                entry = beatAtOrAfter(next, maxOf(1_500L, next.analyzedStartMs))
            }
        }
        if (!isCustom) duration = minOf(duration, end - trigger)
        // Avoid addition overflow for hostile/stale cues and retain the requested duration exactly.
        if (trigger < earliestTriggerMs || trigger < 0L || duration > end || trigger > end - duration) return null
        if (entry < 0L || duration > incomingDurationMs || entry > incomingDurationMs - duration) return null
        val matchingSpeed = if (out != null && next != null) out.bpm / next.bpm else 1f
        val synchronized = syncRequested && out != null && next != null && matchingSpeed in 0.94f..1.06f &&
            out.covers(trigger, duration.toDouble()) && next.covers(entry, duration * matchingSpeed.toDouble()) &&
            onBeat(out, trigger) && onBeat(next, entry) && entry + duration * matchingSpeed.toDouble() <= incomingDurationMs
        val speed = if (synchronized) matchingSpeed else 1f
        return Result(
            AutoDjPlan(trigger, entry, duration, speed, out?.bpm ?: 120f, next?.bpm ?: 120f, synchronized),
            if (syncRequested && !synchronized) "Sin sincronización: los BPM o los puntos elegidos no permiten alinear el ritmo." else null,
        )
    }

    /** A caller must also enforce this check immediately before starting a transition. */
    fun permitsPair(context: CollectionMixContext?, outgoingId: String, incomingId: String, mode: PlaybackMode): Boolean {
        val outgoing = outgoingId.toLongOrNull() ?: return false
        val incoming = incomingId.toLongOrNull() ?: return false
        return context?.permitsPair(outgoing, incoming, mode) == true &&
            context.config.transition(outgoing, incoming).mode != MixTransitionMode.Off
    }

    private fun DjBeatProfile.isReliable(): Boolean = bpm.isFinite() && bpm in 40f..240f &&
        confidence.isFinite() && confidence in 0.72f..1f && firstBeatMs.isFinite() &&
        analyzedStartMs >= 0L && analyzedEndMs - analyzedStartMs >= 4_000L &&
        firstBeatMs >= analyzedStartMs && firstBeatMs < analyzedEndMs

    private fun DjBeatProfile.covers(cue: Long, duration: Double): Boolean =
        cue >= analyzedStartMs && cue + duration <= analyzedEndMs

    private fun onBeat(profile: DjBeatProfile, cue: Long): Boolean {
        val period = 60_000.0 / profile.bpm
        val phase = ((cue - profile.firstBeatMs) % period + period) % period
        return minOf(phase, abs(period - phase)) <= 35.0
    }

    private fun beatAtOrBefore(profile: DjBeatProfile, position: Long): Long {
        val period = 60_000.0 / profile.bpm
        return (profile.firstBeatMs + floor((position - profile.firstBeatMs) / period) * period).roundToLong()
    }

    private fun beatAtOrAfter(profile: DjBeatProfile, position: Long): Long {
        val period = 60_000.0 / profile.bpm
        return (profile.firstBeatMs + ceil((position - profile.firstBeatMs) / period) * period).roundToLong()
    }

    private const val HANDOFF_SAFETY_MS = 150L
    private const val MIN_AUTOMATIC_OVERLAP_MS = 1_500L
}
