package com.softmusic.app.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

/** One instance per player. Only immutable control snapshots cross onto Media3's audio thread. */
@UnstableApi
class DjEffectsAudioProcessor : BaseAudioProcessor() {
    private data class Controls(
        val effect: AutoDjEffect = AutoDjEffect.None,
        val progress: Float = 0f,
        val intensity: Float = 0f,
        val bpm: Float = 120f,
        val incoming: Boolean = false,
        val eq: MixEq = MixEq(),
        val levelGain: Float = 1f,
        val resetGeneration: Long = 0,
    )

    // AtomicReference supplies volatile publication and keeps concurrent reset/update calls lock free.
    private val controls = AtomicReference(Controls())
    private var appliedControls: Controls? = null
    private var appliedResetGeneration = 0L
    private var dsp: DjEffectsDsp? = null

    fun setTransition(
        effect: AutoDjEffect,
        progress: Float,
        intensity: Float,
        bpm: Float,
        incoming: Boolean,
    ) {
        controls.updateAndGet { previous ->
            previous.copy(effect = effect, progress = progress, intensity = intensity, bpm = bpm, incoming = incoming)
        }
    }

    fun setLevelGain(gain: Float) {
        val safeGain = if (gain.isFinite()) gain.coerceIn(0.4f, 1f) else 1f
        controls.updateAndGet { it.copy(levelGain = safeGain) }
    }

    fun setDeckEq(eq: MixEq) {
        controls.updateAndGet { previous -> previous.copy(eq = eq.sanitized()) }
    }

    /** The audio thread fades to dry, then clears delay/filter history without blocking the caller. */
    fun resetEffect() {
        controls.updateAndGet { previous -> Controls(levelGain = previous.levelGain, resetGeneration = previous.resetGeneration + 1) }
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat =
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT &&
            DjEffectsDsp.supports(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        ) {
            // Stay configured while bypassed, so control changes never rebuild the audio pipeline.
            inputAudioFormat
        } else {
            AudioFormat.NOT_SET
        }

    override fun queueInput(inputBuffer: ByteBuffer) {
        // Media3 also calls this with EMPTY_BUFFER while draining. Do not replace
        // pending output or attempt to copy the shared empty buffer onto itself.
        if (!inputBuffer.hasRemaining()) return
        val output = replaceOutputBuffer(inputBuffer.remaining())
        val engine = dsp
        if (engine == null) {
            output.put(inputBuffer)
        } else {
            val snapshot = controls.get()
            if (snapshot !== appliedControls) {
                if (snapshot.resetGeneration != appliedResetGeneration) {
                    engine.requestReset()
                    appliedResetGeneration = snapshot.resetGeneration
                }
                engine.setTransition(
                    snapshot.effect,
                    snapshot.progress,
                    snapshot.intensity,
                    snapshot.bpm,
                    snapshot.incoming,
                )
                engine.setDeckEq(snapshot.eq)
                engine.setLevelGain(snapshot.levelGain)
                appliedControls = snapshot
            }
            engine.processPcm16(inputBuffer, output)
        }
        output.flip()
    }

    override fun onFlush() {
        val format = inputAudioFormat
        dsp = if (format.encoding == C.ENCODING_PCM_16BIT &&
            DjEffectsDsp.supports(format.sampleRate, format.channelCount)
        ) {
            dsp?.takeIf { it.sampleRate == format.sampleRate && it.channelCount == format.channelCount }
                ?.also { it.reset() }
                ?: DjEffectsDsp(format.sampleRate, format.channelCount)
        } else {
            null
        }
        dsp?.setLevelGain(controls.get().levelGain, immediate = true)
        appliedControls = null
        appliedResetGeneration = controls.get().resetGeneration
    }

    override fun onReset() {
        dsp = null
        appliedControls = null
    }
}
