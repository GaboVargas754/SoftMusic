package com.softmusic.app.player

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Conservative tempo/grid estimation from a regularly sampled, nonnegative onset envelope. */
object DjBeatDetector {
    fun detect(
        onsetEnvelope: FloatArray,
        frameDurationMs: Double = 10.0,
        analyzedStartMs: Long = 0L,
        analyzedEndMs: Long = analyzedStartMs + (onsetEnvelope.size * frameDurationMs).toLong(),
    ): DjBeatProfile? {
        if (!frameDurationMs.isFinite() || frameDurationMs !in 2.0..25.0 || analyzedStartMs < 0L) return null
        val durationMs = analyzedEndMs.toDouble() - analyzedStartMs
        if (durationMs < MIN_DURATION_MS || durationMs > MAX_DURATION_MS || onsetEnvelope.size > MAX_FRAMES) return null
        if (abs(onsetEnvelope.size * frameDurationMs - durationMs) > frameDurationMs * 2) return null
        if (onsetEnvelope.any { !it.isFinite() || it < 0f }) return null

        val peaks = findPeaks(onsetEnvelope, frameDurationMs)
        if (peaks.size < MIN_BEATS) return null
        val totalWeight = peaks.sumOf { it.weight }
        val minPeriod = 60_000.0 / MAX_BPM / frameDurationMs
        val maxPeriod = 60_000.0 / MIN_BPM / frameDurationMs
        val candidates = mutableListOf<Grid>()
        // At 10 ms resolution this is 701 periods. Regression below removes the
        // sample quantization and avoids accumulating rounding drift over a song.
        val periodStep = 1.0 / frameDurationMs
        var period = minPeriod
        while (period <= maxPeriod + periodStep * 0.5) {
            fitGrid(peaks, totalWeight, period, onsetEnvelope.size)?.let(candidates::add)
            period += periodStep
        }
        val best = candidates.maxByOrNull { it.score } ?: return null
        if (best.score < MIN_CONFIDENCE) return null
        // Close, incompatible grids imply uncertain tempo. Octave alternatives
        // are normal for music and are scored by their coverage of actual onsets.
        val competing = candidates.any { candidate ->
            val ratio = candidate.period / best.period
            abs(ratio - 1.0) > 0.05 && abs(ratio - 0.5) > 0.035 && abs(ratio - 2.0) > 0.07 &&
                candidate.score >= best.score - AMBIGUITY_MARGIN
        }
        if (competing) return null
        val bpm = (60_000.0 / (best.period * frameDurationMs)).toFloat()
        if (bpm !in MIN_BPM.toFloat()..MAX_BPM.toFloat()) return null
        // Quantized peaks can regress a fraction of a frame before zero. Keep
        // that supported boundary beat within the actual decoded region.
        val firstBeatFrame = when {
            best.firstBeat >= 0.0 -> best.firstBeat
            best.firstBeat >= -0.5 -> 0.0
            else -> best.firstBeat + best.period
        }
        val firstBeatMs = analyzedStartMs + firstBeatFrame * frameDurationMs
        if (firstBeatMs < analyzedStartMs || firstBeatMs >= analyzedEndMs) return null
        return DjBeatProfile(
            bpm = bpm,
            confidence = best.score.toFloat().coerceIn(0f, 1f),
            firstBeatMs = firstBeatMs,
            analyzedStartMs = analyzedStartMs,
            analyzedEndMs = analyzedEndMs,
        )
    }

    private fun findPeaks(envelope: FloatArray, frameMs: Double): List<Peak> {
        if (envelope.isEmpty()) return emptyList()
        val mean = envelope.sumOf { it.toDouble() } / envelope.size
        val deviation = sqrt(envelope.sumOf { (it - mean) * (it - mean) } / envelope.size)
        val maximum = envelope.maxOrNull()?.toDouble() ?: return emptyList()
        if (maximum <= MIN_ONSET || deviation <= MIN_ONSET) return emptyList()
        val threshold = maxOf(mean + deviation * 0.65, maximum * 0.06)
        val minimumGap = ceil(80.0 / frameMs).toInt()
        val peaks = mutableListOf<Peak>()
        for (index in envelope.indices) {
            val value = envelope[index].toDouble()
            if (value < threshold) continue
            if (index > 0 && value <= envelope[index - 1]) continue
            if (index < envelope.lastIndex && value < envelope[index + 1]) continue
            val peak = Peak(index.toDouble(), minOf(value / threshold, 4.0))
            if (peaks.isNotEmpty() && index - peaks.last().position < minimumGap) {
                if (peak.weight > peaks.last().weight) peaks[peaks.lastIndex] = peak
            } else {
                peaks += peak
            }
        }
        return peaks
    }

    private fun fitGrid(peaks: List<Peak>, totalWeight: Double, initialPeriod: Double, frameCount: Int): Grid? {
        var period = initialPeriod
        val binCount = ceil(period).toInt()
        val phases = DoubleArray(binCount)
        val phaseTolerance = minOf(2.5, period * 0.07)
        for (peak in peaks) {
            val phase = peak.position % period
            val center = (phase / period * binCount).roundToInt() % binCount
            for (offset in -2..2) {
                val bin = (center + offset + binCount) % binCount
                phases[bin] += peak.weight * (1.0 - abs(offset) / 3.0)
            }
        }
        var anchor = phases.indices.maxByOrNull { phases[it] }!!.toDouble() / binCount * period
        // Fit weighted event positions = anchor + beatIndex * period.
        repeat(3) {
            var weight = 0.0
            var sumBeat = 0.0
            var sumPosition = 0.0
            var sumBeatSquared = 0.0
            var sumBeatPosition = 0.0
            for (peak in peaks) {
                val beat = floor((peak.position - anchor) / period + 0.5)
                if (abs(peak.position - (anchor + beat * period)) > phaseTolerance) continue
                weight += peak.weight
                sumBeat += beat * peak.weight
                sumPosition += peak.position * peak.weight
                sumBeatSquared += beat * beat * peak.weight
                sumBeatPosition += beat * peak.position * peak.weight
            }
            val denominator = weight * sumBeatSquared - sumBeat * sumBeat
            if (weight <= 0.0 || denominator <= 0.0) return null
            val fittedPeriod = (weight * sumBeatPosition - sumBeat * sumPosition) / denominator
            if (abs(fittedPeriod / initialPeriod - 1.0) > 0.025) return null
            period = fittedPeriod
            anchor = (sumPosition - period * sumBeat) / weight
        }

        val matchedBeats = mutableSetOf<Int>()
        var matchedWeight = 0.0
        var squaredError = 0.0
        for (peak in peaks) {
            val beat = ((peak.position - anchor) / period).roundToInt()
            val error = peak.position - (anchor + beat * period)
            if (abs(error) > minOf(2.5, period * 0.07)) continue
            matchedBeats += beat
            matchedWeight += peak.weight
            squaredError += error * error * peak.weight
        }
        if (matchedBeats.size < MIN_BEATS || matchedWeight <= 0.0) return null
        val firstBeatIndex = matchedBeats.minOrNull() ?: return null
        val lastBeatIndex = matchedBeats.maxOrNull() ?: return null
        val supportedSpan = (lastBeatIndex - firstBeatIndex) * period
        if (supportedSpan < MIN_SUPPORTED_SPAN_FRAMES_RATIO * frameCount) return null
        val expectedBeats = floor((frameCount - 1 - anchor) / period) - ceil(-anchor / period) + 1.0
        val coverage = (matchedBeats.size / expectedBeats).coerceAtMost(1.0)
        val recall = matchedWeight / totalWeight
        val jitter = sqrt(squaredError / matchedWeight) / period
        if (coverage < MIN_COVERAGE || recall < MIN_RECALL || jitter > MAX_JITTER) return null
        val timingQuality = (1.0 - jitter / 0.08).coerceIn(0.0, 1.0)
        val score = coverage * 0.50 + recall * 0.35 + timingQuality * 0.15
        val firstBeat = anchor + firstBeatIndex * period
        return Grid(period, firstBeat, score)
    }

    private data class Peak(val position: Double, val weight: Double)
    private data class Grid(val period: Double, val firstBeat: Double, val score: Double)

    private const val MIN_BPM = 60.0
    private const val MAX_BPM = 200.0
    private const val MIN_DURATION_MS = 8_000.0
    private const val MAX_DURATION_MS = 40_000.0
    private const val MAX_FRAMES = 20_000
    private const val MIN_BEATS = 12
    private const val MIN_ONSET = 0.000001
    private const val MIN_COVERAGE = 0.70
    private const val MIN_RECALL = 0.48
    private const val MAX_JITTER = 0.045
    private const val MIN_SUPPORTED_SPAN_FRAMES_RATIO = 0.60
    private const val MIN_CONFIDENCE = 0.72
    private const val AMBIGUITY_MARGIN = 0.035
}
