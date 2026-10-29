package com.softmusic.app.player

internal data class ExpertDjPlan(
    val triggerPositionMs: Long,
    val mixDurationMs: Long,
    val preloadPositionMs: Long,
    val incomingStartPositionMs: Long = 0L,
    val incomingStartVolumeScale: Float = 1f,
    val transitionCurve: DjTransitionCurve = DjTransitionCurve.Classic,
    val audibleEndPositionMs: Long = triggerPositionMs + mixDurationMs,
    // Applied only during the transition and fully released before handoff.
    val incomingLevelGain: Float = 1f,
    val beatAligned: Boolean = false,
    val currentBpm: Float? = null,
    val incomingBpm: Float? = null,
)

/** Calculates handoff timing and gains from decoded profiles without controlling either player. */
internal object ExpertDjPlanner {
    fun plan(
        durationMs: Long,
        requestedMixDurationMs: Long,
        currentProfile: DjSilenceAnalyzer.AudioProfile?,
        nextProfile: DjSilenceAnalyzer.AudioProfile?,
        currentBeatProfile: DjBeatProfile? = null,
        nextBeatProfile: DjBeatProfile? = null,
    ): ExpertDjPlan? {
        currentProfile ?: return null
        if (!currentProfile.isReliable() || durationMs <= 0L) return null
        val reliableNextProfile = nextProfile?.takeIf { it.isReliable() }
        val audibleEndMs = currentProfile.audibleEndMs(durationMs) ?: return null
        if (audibleEndMs < MIN_DJ_FADE_MS) return null
        val nextStartMs = nextStartPositionMs(reliableNextProfile)
        val trailingSilenceMs = (durationMs - audibleEndMs).coerceAtLeast(0L)
        val hasMeaningfulTailSilence = trailingSilenceMs >= DJ_EXPERT_MIN_TRAILING_SILENCE_MS
        val maxMixDurationMs = requestedMixDurationMs.coerceAtLeast(MIN_DJ_FADE_MS).coerceAtMost(audibleEndMs)
        val triggerPositionMs: Long
        val mixDurationMs: Long

        if (hasMeaningfulTailSilence) {
            val silenceMixDurationMs = maxMixDurationMs
                .coerceAtMost(DJ_EXPERT_SILENCE_HANDOFF_FADE_MS)
                .coerceAtLeast(MIN_DJ_FADE_MS)
            triggerPositionMs = (audibleEndMs - silenceMixDurationMs).coerceIn(0L, durationMs)
            mixDurationMs = (audibleEndMs - triggerPositionMs)
                .coerceIn(MIN_DJ_FADE_MS, silenceMixDurationMs)
        } else {
            val adaptiveMixDurationMs = ExpertDjMusicalRules.normalMixDurationMs(
                currentProfile, reliableNextProfile, audibleEndMs, nextStartMs, maxMixDurationMs,
            )
            val minNormalMixDurationMs = minOf(DJ_EXPERT_MIN_NORMAL_BLEND_MS, adaptiveMixDurationMs)
                .coerceAtLeast(MIN_DJ_FADE_MS)
            val earliestTriggerMs = (audibleEndMs - adaptiveMixDurationMs).coerceAtLeast(0L)
            val latestTriggerMs = (audibleEndMs - minNormalMixDurationMs).coerceAtLeast(earliestTriggerMs)
            val searchEndMs = minOf(latestTriggerMs, earliestTriggerMs + DJ_EXPERT_TRIGGER_SEARCH_MS)
            triggerPositionMs = currentProfile.bestLowEnergyTrigger(
                startMs = earliestTriggerMs,
                endMs = searchEndMs,
            ) ?: earliestTriggerMs
            mixDurationMs = (audibleEndMs - triggerPositionMs)
                .coerceIn(MIN_DJ_FADE_MS, maxMixDurationMs)
        }

        val incomingStartVolumeScale = reliableNextProfile?.let { profile ->
            incomingStartVolumeScale(
                currentProfile = currentProfile,
                nextProfile = profile,
                triggerPositionMs = triggerPositionMs,
                nextStartMs = nextStartMs,
            )
        } ?: 1f
        val transitionCurve = expertTransitionCurve(
            hasMeaningfulTailSilence = hasMeaningfulTailSilence,
            currentProfile = currentProfile,
            nextProfile = reliableNextProfile,
            triggerPositionMs = triggerPositionMs,
            nextStartMs = nextStartMs,
        )

        val energyPlan = ExpertDjPlan(
            incomingStartPositionMs = nextStartMs,
            triggerPositionMs = triggerPositionMs,
            mixDurationMs = mixDurationMs,
            preloadPositionMs = (triggerPositionMs - DJ_EXPERT_PRELOAD_LEAD_MS).coerceAtLeast(0L),
            incomingStartVolumeScale = incomingStartVolumeScale,
            transitionCurve = transitionCurve,
            audibleEndPositionMs = audibleEndMs,
            incomingLevelGain = levelGain(reliableNextProfile),
        )
        return ExpertDjMusicalRules.alignBeats(
            plan = energyPlan,
            currentProfile = currentProfile,
            nextProfile = reliableNextProfile,
            currentBeats = currentBeatProfile,
            nextBeats = nextBeatProfile,
            maxMixDurationMs = if (hasMeaningfulTailSilence) {
                minOf(maxMixDurationMs, DJ_EXPERT_SILENCE_HANDOFF_FADE_MS)
            } else maxMixDurationMs,
            minMixDurationMs = if (hasMeaningfulTailSilence) MIN_DJ_FADE_MS else {
                minOf(DJ_EXPERT_MIN_NORMAL_BLEND_MS, maxMixDurationMs)
            },
        )
    }

    fun levelGain(headProfile: DjSilenceAnalyzer.AudioProfile?): Float =
        ExpertDjMusicalRules.estimateLevelGain(headProfile)

    fun classic(durationMs: Long, requestedMixDurationMs: Long): ExpertDjPlan {
        return ExpertDjPlan(
            triggerPositionMs = (durationMs - requestedMixDurationMs).coerceAtLeast(0L),
            mixDurationMs = requestedMixDurationMs.coerceAtLeast(MIN_DJ_FADE_MS),
            preloadPositionMs = (durationMs - requestedMixDurationMs - DJ_EXPERT_PRELOAD_LEAD_MS).coerceAtLeast(0L),
            audibleEndPositionMs = durationMs,
        )
    }

    private fun nextStartPositionMs(nextProfile: DjSilenceAnalyzer.AudioProfile?): Long {
        nextProfile ?: return 0L
        val audibleStartMs = nextProfile.audibleStartMs() ?: return 0L
        val earlyPeak = nextProfile.averagePeak(
            positionMs = audibleStartMs + DJ_EXPERT_ATTACK_SAMPLE_OFFSET_MS,
            radiusMs = DJ_EXPERT_ATTACK_SAMPLE_RADIUS_MS,
        )
        val preRollMs = if (earlyPeak >= DJ_EXPERT_STRONG_ATTACK_PEAK) {
            DJ_EXPERT_STRONG_ATTACK_PREROLL_MS
        } else {
            DJ_EXPERT_NEXT_PREROLL_MS
        }
        return (audibleStartMs - preRollMs).coerceAtLeast(0L)
    }

    private fun DjSilenceAnalyzer.AudioProfile.bestLowEnergyTrigger(startMs: Long, endMs: Long): Long? {
        if (endMs <= startMs) return null
        val threshold = audibleThreshold()
        return windows
            .asSequence()
            .filter { window -> window.startMs in startMs..endMs }
            .minByOrNull { window ->
                val positionMs = window.startMs
                val localRms = averageRms(positionMs, DJ_EXPERT_ENERGY_SCORE_RADIUS_MS)
                val localPeak = averagePeak(positionMs, DJ_EXPERT_ENERGY_SCORE_RADIUS_MS)
                val previousRms = averageRms(positionMs - DJ_EXPERT_ENERGY_SCORE_RADIUS_MS, DJ_EXPERT_ENERGY_SCORE_RADIUS_MS)
                val nextRms = averageRms(positionMs + DJ_EXPERT_ENERGY_SCORE_RADIUS_MS, DJ_EXPERT_ENERGY_SCORE_RADIUS_MS)
                val risingPenalty = (nextRms - previousRms).coerceAtLeast(0f)
                val audiblePenalty = (localRms - threshold).coerceAtLeast(0f)
                localRms + localPeak * 0.18f + risingPenalty * 0.35f + audiblePenalty * 0.25f
            }
            ?.startMs
    }

    private fun incomingStartVolumeScale(
        currentProfile: DjSilenceAnalyzer.AudioProfile,
        nextProfile: DjSilenceAnalyzer.AudioProfile,
        triggerPositionMs: Long,
        nextStartMs: Long,
    ): Float {
        val outgoingRms = currentProfile.averageRms(
            positionMs = triggerPositionMs + DJ_EXPERT_LEVEL_SAMPLE_OFFSET_MS,
            radiusMs = DJ_EXPERT_LEVEL_SAMPLE_RADIUS_MS,
        )
        val incomingRms = nextProfile.averageRms(
            positionMs = nextStartMs + DJ_EXPERT_LEVEL_SAMPLE_OFFSET_MS,
            radiusMs = DJ_EXPERT_LEVEL_SAMPLE_RADIUS_MS,
        )
        if (outgoingRms < DJ_EXPERT_MIN_LEVEL_RMS || incomingRms < DJ_EXPERT_MIN_LEVEL_RMS) return 1f

        val ratio = (outgoingRms / incomingRms).coerceIn(0f, 1f)
        val softenedScale = 1f - (1f - ratio) * DJ_EXPERT_LEVEL_MATCH_STRENGTH
        return softenedScale.coerceIn(DJ_EXPERT_MIN_INCOMING_VOLUME_SCALE, 1f)
    }

    private fun expertTransitionCurve(
        hasMeaningfulTailSilence: Boolean,
        currentProfile: DjSilenceAnalyzer.AudioProfile,
        nextProfile: DjSilenceAnalyzer.AudioProfile?,
        triggerPositionMs: Long,
        nextStartMs: Long,
    ): DjTransitionCurve {
        nextProfile ?: return DjTransitionCurve.ExpertNormal
        val outgoingRms = currentProfile.averageRms(
            positionMs = triggerPositionMs + DJ_EXPERT_LEVEL_SAMPLE_OFFSET_MS,
            radiusMs = DJ_EXPERT_LEVEL_SAMPLE_RADIUS_MS,
        )
        val incomingRms = nextProfile.averageRms(
            positionMs = nextStartMs + DJ_EXPERT_LEVEL_SAMPLE_OFFSET_MS,
            radiusMs = DJ_EXPERT_LEVEL_SAMPLE_RADIUS_MS,
        )
        val incomingPeak = nextProfile.averagePeak(
            positionMs = nextStartMs + DJ_EXPERT_ATTACK_SAMPLE_OFFSET_MS,
            radiusMs = DJ_EXPERT_ATTACK_SAMPLE_RADIUS_MS,
        )
        val strongIncoming = incomingPeak >= DJ_EXPERT_STRONG_ATTACK_PEAK ||
            (outgoingRms >= DJ_EXPERT_MIN_LEVEL_RMS && incomingRms > outgoingRms * DJ_EXPERT_STRONG_INCOMING_RMS_RATIO)

        return when {
            hasMeaningfulTailSilence && strongIncoming -> DjTransitionCurve.ExpertSilenceStrongIncoming
            hasMeaningfulTailSilence -> DjTransitionCurve.ExpertSilence
            strongIncoming -> DjTransitionCurve.ExpertStrongIncoming
            else -> DjTransitionCurve.ExpertNormal
        }
    }

    private const val MIN_DJ_FADE_MS = 1_000L
    private const val DJ_EXPERT_MIN_TRAILING_SILENCE_MS = 1_500L
    private const val DJ_EXPERT_SILENCE_HANDOFF_FADE_MS = 3_500L
    private const val DJ_EXPERT_PRELOAD_LEAD_MS = 7_000L
    private const val DJ_EXPERT_NEXT_PREROLL_MS = 220L
    private const val DJ_EXPERT_STRONG_ATTACK_PREROLL_MS = 520L
    private const val DJ_EXPERT_MIN_NORMAL_BLEND_MS = 3_000L
    private const val DJ_EXPERT_TRIGGER_SEARCH_MS = 4_000L
    private const val DJ_EXPERT_ENERGY_SCORE_RADIUS_MS = 480L
    private const val DJ_EXPERT_LEVEL_SAMPLE_OFFSET_MS = 650L
    private const val DJ_EXPERT_LEVEL_SAMPLE_RADIUS_MS = 900L
    private const val DJ_EXPERT_ATTACK_SAMPLE_OFFSET_MS = 360L
    private const val DJ_EXPERT_ATTACK_SAMPLE_RADIUS_MS = 520L
    private const val DJ_EXPERT_MIN_LEVEL_RMS = 0.004f
    private const val DJ_EXPERT_LEVEL_MATCH_STRENGTH = 0.65f
    private const val DJ_EXPERT_MIN_INCOMING_VOLUME_SCALE = 0.70f
    private const val DJ_EXPERT_STRONG_ATTACK_PEAK = 0.24f
    private const val DJ_EXPERT_STRONG_INCOMING_RMS_RATIO = 1.35f
}
