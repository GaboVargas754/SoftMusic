package com.softmusic.app.player

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/** Conservative estimates from local decoded PCM; these are not integrated loudness measurements. */
internal object ExpertDjMusicalRules {
    /** Fixed per-song attenuation, estimated from the non-silent body of the first 15 seconds. */
    fun estimateLevelGain(profile: DjSilenceAnalyzer.AudioProfile?): Float {
        profile ?: return 1f
        if (!profile.isReliable() || profile.startMs != 0L) return 1f
        val endMs = minOf(profile.endMs, LEVEL_HEAD_LIMIT_MS)
        if (endMs < MIN_LEVEL_BODY_MS) return 1f
        val windows = profile.windows.sortedBy { it.startMs }
        var coveredUntilMs = 0L
        val body = mutableListOf<Pair<Float, Long>>()
        for (window in windows) {
            if (window.startMs >= endMs) break
            // A profile with gaps or overlapping measurements cannot establish a stable body level.
            if (window.startMs != coveredUntilMs) return 1f
            val windowEndMs = minOf(window.endMs, endMs)
            if (window.rms >= LEVEL_SILENCE_RMS) body += window.rms to (windowEndMs - window.startMs)
            coveredUntilMs = windowEndMs
        }
        if (coveredUntilMs != endMs) return 1f
        val bodyDurationMs = body.sumOf { it.second }
        if (bodyDurationMs < MIN_LEVEL_BODY_MS) return 1f
        // A duration-weighted median rejects isolated hits and ignores the amount of leading silence.
        val middleMs = bodyDurationMs / 2L
        var elapsedMs = 0L
        val bodyRms = body.sortedBy { it.first }.first { (_, durationMs) ->
            elapsedMs += durationMs
            elapsedMs > middleMs
        }.first
        return (TARGET_BODY_RMS / bodyRms).coerceIn(MIN_LEVEL_GAIN, 1f)
    }

    /** Loud, sudden entrances shorten overlap; smooth or quiet entrances retain the configured window. */
    fun normalMixDurationMs(
        currentProfile: DjSilenceAnalyzer.AudioProfile,
        nextProfile: DjSilenceAnalyzer.AudioProfile?,
        audibleEndMs: Long,
        incomingStartMs: Long,
        maximumMs: Long,
    ): Long {
        nextProfile ?: return maximumMs
        val minimumMs = minOf(MIN_NORMAL_MIX_MS, maximumMs)
        val outgoingRms = currentProfile.averageRms(audibleEndMs - 1_500L, 1_500L)
        val incomingRms = nextProfile.averageRms(incomingStartMs + 650L, 900L)
        val incomingPeak = nextProfile.averagePeak(incomingStartMs + 360L, 520L)
        val energy = ((maxOf(outgoingRms, incomingRms) - SMOOTH_BODY_RMS) / HIGH_BODY_RMS_RANGE)
            .coerceIn(0f, 1f)
        val attack = ((incomingPeak - SMOOTH_ATTACK_PEAK) / STRONG_ATTACK_PEAK_RANGE)
            .coerceIn(0f, 1f)
        val pressure = maxOf(energy, attack)
        return (maximumMs - (maximumMs - minimumMs) * pressure.toDouble()).roundToLong()
            .coerceIn(minimumMs, maximumMs)
    }

    /** Aligns only starts. Playback speed stays native, so a long mix may still drift slightly. */
    fun alignBeats(
        plan: ExpertDjPlan,
        currentProfile: DjSilenceAnalyzer.AudioProfile,
        nextProfile: DjSilenceAnalyzer.AudioProfile?,
        currentBeats: DjBeatProfile?,
        nextBeats: DjBeatProfile?,
        maxMixDurationMs: Long,
        minMixDurationMs: Long,
    ): ExpertDjPlan {
        nextProfile ?: return plan
        currentBeats ?: return plan
        nextBeats ?: return plan
        if (!currentBeats.isSafeGrid() || !nextBeats.isSafeGrid()) return plan
        val tempoRatio = maxOf(currentBeats.bpm, nextBeats.bpm).toDouble() /
            minOf(currentBeats.bpm, nextBeats.bpm)
        if (tempoRatio > MAX_TEMPO_RATIO) return plan
        val currentAudibleStartMs = currentProfile.audibleStartMs() ?: return plan
        val nextAudibleStartMs = nextProfile.audibleStartMs() ?: return plan
        val nextAudibleEndMs = nextProfile.audibleEndMs(nextProfile.endMs) ?: return plan
        // Require decoded beat coverage over the handoff rather than extrapolating across a silent tail.
        if (plan.triggerPositionMs < currentBeats.analyzedStartMs ||
            plan.audibleEndPositionMs > currentBeats.analyzedEndMs
        ) return plan
        val earliestTriggerMs = maxOf(
            currentAudibleStartMs,
            currentBeats.analyzedStartMs,
            plan.audibleEndPositionMs - maxMixDurationMs,
            plan.triggerPositionMs - MAX_TRIGGER_SHIFT_MS,
        )
        val latestTriggerMs = minOf(
            plan.audibleEndPositionMs - minMixDurationMs,
            plan.triggerPositionMs + MAX_TRIGGER_SHIFT_MS,
        )
        val candidates = currentBeats.nearbyBeats(plan.triggerPositionMs)
            .filter { it in earliestTriggerMs..latestTriggerMs }
            .sortedBy { abs(it - plan.triggerPositionMs) }
        for (triggerMs in candidates) {
            val mixDurationMs = plan.audibleEndPositionMs - triggerMs
            if (mixDurationMs !in minMixDurationMs..maxMixDurationMs) continue
            val incomingMinimumMs = maxOf(
                plan.incomingStartPositionMs, nextAudibleStartMs, nextBeats.analyzedStartMs,
            )
            val incomingMaximumMs = minOf(
                plan.incomingStartPositionMs + MAX_INCOMING_SHIFT_MS,
                nextAudibleStartMs + MAX_AUDIBLE_INTRO_SKIP_MS,
                nextAudibleEndMs - mixDurationMs,
                nextBeats.analyzedEndMs - mixDurationMs,
            )
            val incomingMs = nextBeats.firstBeatAtOrAfter(incomingMinimumMs)
            if (incomingMs !in incomingMinimumMs..incomingMaximumMs) continue
            return plan.copy(
                triggerPositionMs = triggerMs,
                mixDurationMs = mixDurationMs,
                preloadPositionMs = (triggerMs - PRELOAD_LEAD_MS).coerceAtLeast(0L),
                incomingStartPositionMs = incomingMs,
                beatAligned = true,
                currentBpm = currentBeats.bpm,
                incomingBpm = nextBeats.bpm,
            )
        }
        return plan
    }

    private fun DjBeatProfile.isSafeGrid(): Boolean =
        bpm.isFinite() && bpm in 60f..200f && confidence.isFinite() && confidence in 0.8f..1f &&
            firstBeatMs.isFinite() && analyzedStartMs >= 0L &&
            analyzedEndMs - analyzedStartMs >= MIN_BEAT_COVERAGE_MS &&
            firstBeatMs >= analyzedStartMs && firstBeatMs < analyzedEndMs

    private fun DjBeatProfile.nearbyBeats(positionMs: Long): List<Long> {
        val periodMs = 60_000.0 / bpm
        val lowerIndex = floor((positionMs - firstBeatMs) / periodMs).coerceAtLeast(0.0)
        return listOf(lowerIndex, lowerIndex + 1.0)
            .map { index -> (firstBeatMs + index * periodMs).roundToLong() }
            .filter { it >= analyzedStartMs && it < analyzedEndMs }
            .distinct()
    }

    private fun DjBeatProfile.firstBeatAtOrAfter(positionMs: Long): Long {
        val periodMs = 60_000.0 / bpm
        val index = ceil((positionMs - firstBeatMs) / periodMs).coerceAtLeast(0.0)
        return (firstBeatMs + index * periodMs).roundToLong()
    }

    private const val LEVEL_HEAD_LIMIT_MS = 15_000L
    private const val MIN_LEVEL_BODY_MS = 1_500L
    private const val LEVEL_SILENCE_RMS = 0.004f
    private const val TARGET_BODY_RMS = 0.10f
    private const val MIN_LEVEL_GAIN = 0.4f
    private const val MIN_NORMAL_MIX_MS = 3_000L
    private const val SMOOTH_BODY_RMS = 0.10f
    private const val HIGH_BODY_RMS_RANGE = 0.15f
    private const val SMOOTH_ATTACK_PEAK = 0.20f
    private const val STRONG_ATTACK_PEAK_RANGE = 0.30f
    private const val MAX_TEMPO_RATIO = 1.030001
    private const val MIN_BEAT_COVERAGE_MS = 8_000L
    private const val MAX_TRIGGER_SHIFT_MS = 500L
    private const val MAX_INCOMING_SHIFT_MS = 1_000L
    private const val MAX_AUDIBLE_INTRO_SKIP_MS = 500L
    private const val PRELOAD_LEAD_MS = 7_000L
}
