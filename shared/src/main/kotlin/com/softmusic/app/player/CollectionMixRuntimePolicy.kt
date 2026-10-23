package com.softmusic.app.player

import kotlin.math.abs

/** Decisions shared by the service and tests; unrelated saved transitions never change a live pair. */
object CollectionMixRuntimePolicy {
    data class EffectiveContext(
        val key: String?,
        val outgoingId: Long?,
        val incomingId: Long?,
        val enabled: Boolean,
        val permitted: Boolean,
        val outgoingMember: Boolean,
        val incomingMember: Boolean,
        val mode: MixTransitionMode?,
        val automatic: AutoDjSettings?,
        val custom: MixTransitionSettings?,
    )

    fun effectiveContext(
        context: CollectionMixContext?,
        outgoingId: String?,
        incomingId: String?,
        playbackMode: PlaybackMode,
    ): EffectiveContext {
        val outgoing = outgoingId?.toLongOrNull()
        val incoming = incomingId?.toLongOrNull()
        val enabled = context?.config?.isEnabled == true
        val pair = if (enabled && outgoing != null && incoming != null) context?.config?.transition(outgoing, incoming)?.sanitized() else null
        return EffectiveContext(
            key = context?.key,
            outgoingId = outgoing,
            incomingId = incoming,
            enabled = enabled,
            permitted = context?.permits(playbackMode) == true,
            outgoingMember = outgoing != null && outgoing in context?.songIds.orEmpty(),
            incomingMember = incoming != null && incoming in context?.songIds.orEmpty(),
            mode = pair?.mode,
            automatic = context?.config?.settings?.sanitized()?.takeIf { pair?.mode == MixTransitionMode.Automatic },
            custom = pair?.takeIf { it.mode == MixTransitionMode.Custom },
        )
    }

    data class StartDecision(val canMix: Boolean, val synchronized: Boolean, val syncErrorMs: Double, val reason: String)

    /** Keep the plan's cues, duration, and speed; phase error alone only removes the sync promise. */
    fun evaluateStart(
        plan: AutoDjPlan,
        outgoingPositionMs: Long,
        incomingPositionMs: Long,
        outgoingDurationMs: Long,
        incomingDurationMs: Long,
        bothPlaying: Boolean,
    ): StartDecision {
        val elapsed = (outgoingPositionMs - plan.triggerPositionMs).coerceAtLeast(0L)
        val remaining = plan.durationMs - elapsed
        val error = plan.incomingStartPositionMs + elapsed * plan.incomingSpeed.toDouble() - incomingPositionMs
        fun reject(reason: String) = StartDecision(false, false, error, reason)
        if (!bothPlaying) return reject("decks_not_playing")
        if (plan.durationMs <= 0L || !plan.incomingSpeed.isFinite() || plan.incomingSpeed <= 0f ||
            outgoingPositionMs !in 0 until outgoingDurationMs || incomingPositionMs !in 0 until incomingDurationMs
        ) return reject("invalid_position_or_speed")
        if (remaining < minimumOverlapMs(plan.durationMs)) return reject("insufficient_overlap")
        if (incomingPositionMs < plan.incomingStartPositionMs) return reject("before_incoming_cue")
        if (outgoingDurationMs - outgoingPositionMs + 40L < remaining ||
            incomingDurationMs - incomingPositionMs < remaining * plan.incomingSpeed.toDouble()
        ) return reject("insufficient_media_remaining")
        val synchronized = plan.synchronized && abs(error) <= ALIGNMENT_TOLERANCE_MS
        return StartDecision(true, synchronized, error, if (plan.synchronized && !synchronized) "startup_alignment_degraded" else "ready")
    }

    fun minimumOverlapMs(durationMs: Long): Long = minOf(750L, (durationMs / 2L).coerceAtLeast(1L))

    /** One automatic restart per media item; explicit transport or a new pair resets the budget. */
    fun canRetryAfterRebuffer(currentMediaId: String?, retriedMediaId: String?): Boolean =
        currentMediaId != null && currentMediaId != retriedMediaId

    private const val ALIGNMENT_TOLERANCE_MS = 65.0
}
