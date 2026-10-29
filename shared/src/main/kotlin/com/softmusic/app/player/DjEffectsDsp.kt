package com.softmusic.app.player

import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A small, independent PCM16 effects engine. All methods belong to the audio thread.
 *
 * Controls ramp over 12 ms. A bypassed engine copies bytes verbatim, including samples at full scale.
 * The delay is half a beat of the original media; downstream playback speed also scales its duration.
 * Echo is deliberately disabled on the incoming deck, whose playback speed may be changing.
 */
class DjEffectsDsp(val sampleRate: Int, val channelCount: Int) {
    init {
        require(supports(sampleRate, channelCount))
    }

    private data class Parameters(
        val effect: AutoDjEffect = AutoDjEffect.None,
        val progress: Float = 0f,
        val intensity: Float = 0f,
        val bpm: Float = 120f,
        val incoming: Boolean = false,
    )

    private val rampStep = 1f / (sampleRate * CONTROL_RAMP_SECONDS)
    private val levelRampStep = 1f / (sampleRate * 0.75f)
    private var levelGain = 1f
    private var targetLevelGain = 1f
    private val bassAlpha = lowPassAlpha(180f)
    private val bassState = FloatArray(channelCount)
    private val sweepState = FloatArray(channelCount)
    private val eqLowState = FloatArray(channelCount)
    private val eqUpperState = FloatArray(channelCount)
    private val eqLowAlpha = lowPassAlpha(250f)
    private val eqUpperAlpha = lowPassAlpha(4_000f)
    private var eq = MixEq()
    private var lowGain = 1f
    private var midGain = 1f
    private var highGain = 1f
    private var targetLowGain = 1f
    private var targetMidGain = 1f
    private var targetHighGain = 1f
    private val delayCapacityFrames = (sampleRate * MAX_DELAY_SECONDS).toInt() + 2
    private val delay = FloatArray(delayCapacityFrames * channelCount)
    private var parameters = Parameters()
    private var clearPending = false
    private var filtersPrimed = false
    private var bassAmount = 0f
    private var targetBassAmount = 0f
    private var sweepAmount = 0f
    private var targetSweepAmount = 0f
    private var sweepAlpha = lowPassAlpha(18_000f)
    private var targetSweepAlpha = sweepAlpha
    private var sweepHighPass = 0f
    private var targetSweepHighPass = 0f
    private var echoAmount = 0f
    private var targetEchoAmount = 0f
    private var echoFeedback = 0f
    private var targetEchoFeedback = 0f
    private var echoRecording = false
    private var delayFrames = (sampleRate * 0.25f).roundToInt()
    private var delayWriteFrame = 0
    private var delayValidFrames = 0

    fun setTransition(
        effect: AutoDjEffect,
        progress: Float,
        intensity: Float,
        bpm: Float,
        incoming: Boolean,
    ) {
        parameters = Parameters(
            effect = effect,
            progress = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f,
            intensity = if (intensity.isFinite()) intensity.coerceIn(0f, 1f) else 0f,
            bpm = if (bpm.isFinite() && bpm > 0f) bpm.coerceIn(40f, 240f) else 120f,
            incoming = incoming,
        )
        updateTargets()
    }

    /** Stable per-song attenuation, independent of temporary transition effects. Never boosts. */
    fun setLevelGain(value: Float, immediate: Boolean = false) {
        targetLevelGain = if (value.isFinite()) value.coerceIn(0.4f, 1f) else 1f
        if (immediate) levelGain = targetLevelGain
    }

    /** Three complementary bands, with 250 Hz and 4 kHz crossovers and smooth gain changes. */
    fun setDeckEq(value: MixEq) {
        eq = value.sanitized()
        updateTargets()
    }

    /** Fade an audible effect to dry, erase its history, then allow any newly supplied controls. */
    fun requestReset() {
        parameters = Parameters()
        eq = MixEq()
        clearPending = true
        updateTargets()
        finishResetIfDry()
    }

    /** Immediate discontinuity reset, used when Media3 flushes or changes the decoded stream. */
    fun reset() {
        parameters = Parameters()
        eq = MixEq()
        levelGain = 1f
        targetLevelGain = 1f
        lowGain = 1f
        midGain = 1f
        highGain = 1f
        targetLowGain = 1f
        targetMidGain = 1f
        targetHighGain = 1f
        clearPending = false
        bassAmount = 0f
        targetBassAmount = 0f
        sweepAmount = 0f
        targetSweepAmount = 0f
        echoAmount = 0f
        targetEchoAmount = 0f
        echoFeedback = 0f
        targetEchoFeedback = 0f
        sweepAlpha = lowPassAlpha(18_000f)
        targetSweepAlpha = sweepAlpha
        sweepHighPass = 0f
        targetSweepHighPass = 0f
        echoRecording = false
        clearHistory()
    }

    /**
     * Consume little endian, interleaved PCM16 into an existing output buffer without allocations.
     * Media3 supplies complete frames; any incomplete final frame is conservatively copied dry.
     */
    fun processPcm16(input: ByteBuffer, output: ByteBuffer) {
        if (!input.hasRemaining()) return
        require(output.remaining() >= input.remaining())
        val bytesPerFrame = channelCount * 2
        while (input.remaining() >= bytesPerFrame) {
            advanceControls()
            if (isFullyBypassed()) {
                output.put(input)
                return
            }

            val primeFilters = !filtersPrimed
            val recordEcho = echoRecording || echoAmount > 0f
            val readFrame = (delayWriteFrame - delayFrames + delayCapacityFrames) % delayCapacityFrames
            val hasDelayedFrame = delayValidFrames >= delayFrames
            val audible = bassAmount > 0f || sweepAmount > 0f || echoAmount > 0f || !eqIsFlat()
            for (channel in 0 until channelCount) {
                val sample = ((input.get().toInt() and 0xff) or (input.get().toInt() shl 8)).toShort()
                val dry = sample.toFloat() / 32768f
                if (primeFilters) {
                    bassState[channel] = dry
                    sweepState[channel] = dry
                    eqLowState[channel] = dry
                    eqUpperState[channel] = dry
                }
                bassState[channel] += bassAlpha * (dry - bassState[channel])
                sweepState[channel] += sweepAlpha * (dry - sweepState[channel])
                val delayed = if (recordEcho && hasDelayedFrame) {
                    delay[readFrame * channelCount + channel]
                } else {
                    0f
                }
                if (recordEcho) {
                    // Convex feedback and wet mixes keep an echo stable even at full-scale input.
                    delay[delayWriteFrame * channelCount + channel] =
                        dry * (1f - echoFeedback) + delayed * echoFeedback
                }

                val result = if (!audible && levelGain == 1f) {
                    sample
                } else {
                    var wet = dry - bassAmount * bassState[channel]
                    val filtered = sweepState[channel] * (1f - sweepHighPass) +
                        (dry - sweepState[channel]) * sweepHighPass
                    wet += sweepAmount * (filtered - dry)
                    wet = wet * (1f - echoAmount) + delayed * echoAmount
                    eqLowState[channel] += eqLowAlpha * (wet - eqLowState[channel])
                    eqUpperState[channel] += eqUpperAlpha * (wet - eqUpperState[channel])
                    if (!eqIsFlat()) {
                        val low = eqLowState[channel]
                        val upper = eqUpperState[channel]
                        wet = low * lowGain + (upper - low) * midGain + (wet - upper) * highGain
                    }
                    (softLimit(wet * levelGain) * 32768f).roundToInt().coerceIn(-32768, 32767).toShort()
                }
                output.put(result.toByte())
                output.put((result.toInt() shr 8).toByte())
            }
            filtersPrimed = true
            if (recordEcho) {
                delayWriteFrame = (delayWriteFrame + 1) % delayCapacityFrames
                delayValidFrames = (delayValidFrames + 1).coerceAtMost(delayCapacityFrames)
            }
        }
        output.put(input)
    }

    private fun updateTargets() {
        targetLowGain = if (clearPending) 1f else 10f.pow(eq.lowDb / 20f)
        targetMidGain = if (clearPending) 1f else 10f.pow(eq.midDb / 20f)
        targetHighGain = if (clearPending) 1f else 10f.pow(eq.highDb / 20f)
        targetBassAmount = 0f
        targetSweepAmount = 0f
        targetEchoAmount = 0f
        if (clearPending) return
        val p = parameters.progress
        val strength = parameters.intensity
        when (parameters.effect) {
            AutoDjEffect.None -> Unit
            AutoDjEffect.BassSwap -> {
                val handover = smoothStep(p)
                targetBassAmount = strength * if (parameters.incoming) 1f - handover else handover
            }
            AutoDjEffect.FilterSweep -> {
                // A logarithmic opening on the incoming deck and rising high pass on the outgoing.
                val cutoff = if (parameters.incoming) {
                    180f * 100f.pow(p)
                } else {
                    40f * 100f.pow(p)
                }
                targetSweepAlpha = lowPassAlpha(cutoff)
                targetSweepHighPass = if (parameters.incoming) 0f else 1f
                targetSweepAmount = strength * boundaryEnvelope(p)
            }
            AutoDjEffect.EchoOut -> if (!parameters.incoming && strength > 0f) {
                if (!echoRecording) {
                    clearDelay()
                    // Latch tempo for this echo phrase, avoiding delay-read jumps on UI updates.
                    delayFrames = (sampleRate * 30f / parameters.bpm).roundToInt()
                        .coerceIn(1, delayCapacityFrames - 1)
                }
                targetEchoFeedback = 0.25f + 0.3f * strength
                targetEchoAmount = 0.55f * strength * boundaryEnvelope(p) * smoothStep(p)
            }
        }
        echoRecording = parameters.effect == AutoDjEffect.EchoOut && !parameters.incoming && strength > 0f
    }

    private fun advanceControls() {
        levelGain = when {
            levelGain < targetLevelGain -> (levelGain + levelRampStep).coerceAtMost(targetLevelGain)
            levelGain > targetLevelGain -> (levelGain - levelRampStep).coerceAtLeast(targetLevelGain)
            else -> levelGain
        }
        bassAmount = approach(bassAmount, targetBassAmount)
        sweepAmount = approach(sweepAmount, targetSweepAmount)
        echoAmount = approach(echoAmount, targetEchoAmount)
        sweepAlpha = approach(sweepAlpha, targetSweepAlpha)
        sweepHighPass = approach(sweepHighPass, targetSweepHighPass)
        echoFeedback = approach(echoFeedback, targetEchoFeedback)
        lowGain = approach(lowGain, targetLowGain)
        midGain = approach(midGain, targetMidGain)
        highGain = approach(highGain, targetHighGain)
        finishResetIfDry()
        if (!echoRecording && echoAmount == 0f && delayValidFrames != 0) clearDelay()
    }

    private fun finishResetIfDry() {
        if (!clearPending || bassAmount != 0f || sweepAmount != 0f || echoAmount != 0f || !eqIsFlat()) return
        clearHistory()
        echoRecording = false
        clearPending = false
        updateTargets()
    }

    private fun isFullyBypassed(): Boolean =
        levelGain == 1f && targetLevelGain == 1f &&
        bassAmount == 0f && sweepAmount == 0f && echoAmount == 0f && !echoRecording && eqIsFlat() &&
            targetLowGain == 1f && targetMidGain == 1f && targetHighGain == 1f &&
            (parameters.effect == AutoDjEffect.None || parameters.intensity == 0f ||
                (parameters.effect == AutoDjEffect.EchoOut && parameters.incoming))

    private fun clearHistory() {
        bassState.fill(0f)
        sweepState.fill(0f)
        eqLowState.fill(0f)
        eqUpperState.fill(0f)
        filtersPrimed = false
        clearDelay()
    }

    private fun clearDelay() {
        // No fill on the realtime thread: samples before this generation can never be read.
        delayWriteFrame = 0
        delayValidFrames = 0
    }

    private fun eqIsFlat(): Boolean = lowGain == 1f && midGain == 1f && highGain == 1f

    private fun approach(value: Float, target: Float): Float = when {
        value < target -> (value + rampStep).coerceAtMost(target)
        value > target -> (value - rampStep).coerceAtLeast(target)
        else -> value
    }

    private fun lowPassAlpha(cutoff: Float): Float =
        (1.0 - exp(-2.0 * PI * cutoff.coerceAtMost(sampleRate * 0.45f) / sampleRate)).toFloat()

    private fun softLimit(value: Float): Float {
        val magnitude = abs(value)
        if (magnitude <= 0.98f) return value
        // Continuous first derivative at the knee; no flat, hard-clipped waveform peaks.
        val excess = magnitude - 0.98f
        val limited = 0.98f + excess / (1f + excess * 50f)
        return if (value < 0f) -limited else limited
    }

    private fun smoothStep(value: Float): Float = value * value * (3f - 2f * value)

    private fun boundaryEnvelope(progress: Float): Float =
        if (progress <= 0f || progress >= 1f) 0f else sin(PI * progress).toFloat()

    companion object {
        private const val CONTROL_RAMP_SECONDS = 0.012f
        private const val MAX_DELAY_SECONDS = 0.75f

        fun supports(sampleRate: Int, channelCount: Int): Boolean =
            sampleRate in 8_000..192_000 && channelCount in 1..2
    }
}
