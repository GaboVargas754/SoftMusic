package com.softmusic.app.player

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

class DjSilenceAnalyzer(private val context: Context) {
    private val startCache = object : LinkedHashMap<String, Long>(MAX_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > MAX_CACHE_SIZE
    }
    private val endCache = object : LinkedHashMap<String, Long>(MAX_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > MAX_CACHE_SIZE
    }
    private val headProfileCache = object : LinkedHashMap<String, AudioProfile>(MAX_PROFILE_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AudioProfile>?): Boolean = size > MAX_PROFILE_CACHE_SIZE
    }
    private val tailProfileCache = object : LinkedHashMap<String, AudioProfile>(MAX_PROFILE_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AudioProfile>?): Boolean = size > MAX_PROFILE_CACHE_SIZE
    }

    data class AudioWindow(
        val startMs: Long,
        val endMs: Long,
        val rms: Float,
        val peak: Float,
    )

    data class AudioProfile(
        val startMs: Long,
        val endMs: Long,
        val windows: List<AudioWindow>,
    ) {
        fun audibleStartMs(): Long? {
            val threshold = audibleThreshold()
            return firstAudibleWindowIndex(threshold)?.let { index ->
                windows[index].startMs - START_MARGIN_MS
            }?.coerceAtLeast(0L)
        }

        fun audibleEndMs(durationMs: Long): Long? {
            val threshold = audibleThreshold()
            val lastAudibleWindow = lastAudibleWindowIndex(threshold)?.let(windows::get)
            return if (lastAudibleWindow != null) {
                (lastAudibleWindow.endMs + END_MARGIN_MS).coerceIn(0L, durationMs)
            } else {
                // If the scanned tail is entirely silent, the oldest scanned point is
                // the safest known handoff point for long outros.
                startMs.takeIf { it > 0L }?.coerceAtMost(durationMs)
            }
        }

        fun averageRms(positionMs: Long, radiusMs: Long): Float = averageAround(positionMs, radiusMs) { it.rms }

        fun averagePeak(positionMs: Long, radiusMs: Long): Float = averageAround(positionMs, radiusMs) { it.peak }

        fun hasAudibleContent(): Boolean = lastAudibleWindowIndex(audibleThreshold()) != null

        fun audibleThreshold(): Float {
            val measured = windows
                .map { it.rms }
                .filter { it >= MIN_MEASURED_RMS }
                .sorted()
            if (measured.isEmpty()) return AUDIBLE_RMS_FLOOR

            val floor = measured.percentile(0.20f)
            val body = measured.percentile(0.70f)
            val adaptiveThreshold = minOf(
                floor * NOISE_FLOOR_MULTIPLIER,
                body * BODY_LEVEL_AUDIBLE_RATIO,
            )
            return maxOf(
                AUDIBLE_RMS_FLOOR,
                adaptiveThreshold,
            ).coerceIn(AUDIBLE_RMS_FLOOR, AUDIBLE_RMS_CEILING)
        }

        private fun firstAudibleWindowIndex(threshold: Float): Int? {
            return windows.indices.firstOrNull { index -> isAudibleWindow(index, threshold) }
                ?: windows.indices.firstOrNull { index -> windows[index].rms >= threshold }
        }

        private fun lastAudibleWindowIndex(threshold: Float): Int? {
            return windows.indices.reversed().firstOrNull { index -> isAudibleWindow(index, threshold) }
                ?: windows.indices.reversed().firstOrNull { index -> windows[index].rms >= threshold }
        }

        private fun isAudibleWindow(index: Int, threshold: Float): Boolean {
            val window = windows[index]
            if (window.peak >= STRONG_AUDIBLE_PEAK || window.rms >= threshold * STRONG_RMS_MULTIPLIER) return true
            if (window.rms < threshold) return false
            return windows.getOrNull(index - 1).orSilent().rms >= threshold ||
                windows.getOrNull(index + 1).orSilent().rms >= threshold
        }

        private fun averageAround(positionMs: Long, radiusMs: Long, value: (AudioWindow) -> Float): Float {
            val start = positionMs - radiusMs
            val end = positionMs + radiusMs
            var total = 0f
            var count = 0
            windows.forEach { window ->
                if (window.endMs >= start && window.startMs <= end) {
                    total += value(window)
                    count += 1
                }
            }
            return if (count > 0) total / count else 0f
        }

        private fun List<Float>.percentile(percentile: Float): Float {
            if (isEmpty()) return 0f
            val index = ((lastIndex) * percentile.coerceIn(0f, 1f)).toInt().coerceIn(indices)
            return this[index]
        }

        private fun AudioWindow?.orSilent(): AudioWindow = this ?: SILENT_WINDOW

        private companion object {
            const val MIN_MEASURED_RMS = 0.0004f
            const val AUDIBLE_RMS_FLOOR = 0.0035f
            const val AUDIBLE_RMS_CEILING = 0.018f
            const val NOISE_FLOOR_MULTIPLIER = 2.8f
            const val BODY_LEVEL_AUDIBLE_RATIO = 0.08f
            const val STRONG_RMS_MULTIPLIER = 2.2f
            const val STRONG_AUDIBLE_PEAK = 0.035f
            const val START_MARGIN_MS = 220L
            const val END_MARGIN_MS = 220L
            val SILENT_WINDOW = AudioWindow(startMs = 0L, endMs = 0L, rms = 0f, peak = 0f)
        }
    }

    suspend fun audibleStartMs(uri: Uri): Long? = withContext(Dispatchers.Default) {
        val key = uri.toString()
        synchronized(startCache) { startCache[key] } ?: headProfile(uri)?.audibleStartMs()?.also { value ->
            synchronized(startCache) { startCache[key] = value }
        }
    }

    suspend fun audibleEndMs(uri: Uri, durationMs: Long): Long? = withContext(Dispatchers.Default) {
        if (durationMs <= 0L) return@withContext null
        val key = "${uri}|$durationMs"
        synchronized(endCache) { endCache[key] } ?: tailProfile(uri, durationMs)?.audibleEndMs(durationMs)?.also { value ->
            synchronized(endCache) { endCache[key] = value }
        }
    }

    suspend fun headProfile(uri: Uri): AudioProfile? = withContext(Dispatchers.Default) {
        val key = uri.toString()
        synchronized(headProfileCache) { headProfileCache[key] } ?: runCatching {
            decodeProfile(
                uri = uri,
                startUs = 0L,
                endUs = HEAD_ANALYSIS_WINDOW_MS * 1_000L,
            )
        }.getOrNull()?.also { value ->
            synchronized(headProfileCache) { headProfileCache[key] = value }
        }
    }

    suspend fun tailProfile(uri: Uri, durationMs: Long): AudioProfile? = withContext(Dispatchers.Default) {
        if (durationMs <= 0L) return@withContext null
        val key = "${uri}|$durationMs"
        synchronized(tailProfileCache) { tailProfileCache[key] } ?: runCatching {
            scanTailProfile(uri, durationMs)
        }.getOrNull()?.also { value ->
            synchronized(tailProfileCache) { tailProfileCache[key] = value }
        }
    }

    private suspend fun scanTailProfile(uri: Uri, durationMs: Long): AudioProfile {
        val profiles = mutableListOf<AudioProfile>()
        var segmentEndMs = durationMs
        var scannedMs = 0L

        while (segmentEndMs > 0L && scannedMs < MAX_TAIL_SCAN_MS) {
            currentCoroutineContext().ensureActive()
            val segmentStartMs = (segmentEndMs - TAIL_ANALYSIS_WINDOW_MS).coerceAtLeast(0L)
            val profile = decodeProfile(
                uri = uri,
                startUs = segmentStartMs * 1_000L,
                endUs = segmentEndMs * 1_000L,
            )
            profiles += profile

            scannedMs += segmentEndMs - segmentStartMs
            if (profile.hasAudibleContent()) break
            if (segmentStartMs == 0L) break
            segmentEndMs = segmentStartMs
        }

        val windows = profiles
            .asReversed()
            .flatMap { it.windows }
            .distinctBy { it.startMs }
            .sortedBy { it.startMs }
        val startMs = windows.firstOrNull()?.startMs ?: (durationMs - scannedMs).coerceAtLeast(0L)
        return AudioProfile(
            startMs = startMs,
            endMs = durationMs,
            windows = windows,
        )
    }

    private suspend fun decodeProfile(uri: Uri, startUs: Long, endUs: Long): AudioProfile {
        if (endUs <= startUs) {
            val startMs = startUs / 1_000L
            return AudioProfile(startMs = startMs, endMs = startMs, windows = emptyList())
        }
        val windowDurationUs = LEVEL_WINDOW_MS * 1_000L
        val windowCount = (((endUs - startUs + windowDurationUs - 1L) / windowDurationUs).toInt()).coerceAtLeast(1)
        val startMs = startUs / 1_000L
        val endMs = endUs / 1_000L
        val windows = List(windowCount) { index ->
            MutableAudioWindow(
                startMs = startMs + index * LEVEL_WINDOW_MS,
                endMs = (startMs + (index + 1) * LEVEL_WINDOW_MS).coerceAtMost(endMs),
            )
        }
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null

        try {
            currentCoroutineContext().ensureActive()
            extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)
            val trackIndex = extractor.firstAudioTrackIndex() ?: return AudioProfile(startMs, endMs, windows.map { it.toWindow() })
            extractor.selectTrack(trackIndex)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mimeType = inputFormat.getString(MediaFormat.KEY_MIME) ?: return AudioProfile(startMs, endMs, windows.map { it.toWindow() })
            codec = MediaCodec.createDecoderByType(mimeType)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var outputFormat = inputFormat
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                currentCoroutineContext().ensureActive()
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        val sampleTimeUs = extractor.sampleTime
                        if (sampleTimeUs < 0L || sampleTimeUs > endUs) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val sampleSize = inputBuffer?.let { extractor.readSampleData(it, 0) } ?: -1
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTimeUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = codec.outputFormat
                    else -> if (outputIndex >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0 && bufferInfo.presentationTimeUs <= endUs) {
                            collectMetrics(
                                buffer = outputBuffer,
                                bufferInfo = bufferInfo,
                                outputFormat = outputFormat,
                                startUs = startUs,
                                endUs = endUs,
                                windows = windows,
                            )
                        }
                        outputDone = outputDone || (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
        } finally {
            codec?.let { decoder ->
                runCatching { decoder.stop() }
                decoder.release()
            }
            extractor?.release()
        }

        return AudioProfile(
            startMs = startMs,
            endMs = endMs,
            windows = windows.map { it.toWindow() },
        )
    }

    private suspend fun collectMetrics(
        buffer: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo,
        outputFormat: MediaFormat,
        startUs: Long,
        endUs: Long,
        windows: List<MutableAudioWindow>,
    ) {
        val sampleRate = outputFormat.integerOrNull(MediaFormat.KEY_SAMPLE_RATE)?.takeIf { it > 0 } ?: return
        val channelCount = outputFormat.integerOrNull(MediaFormat.KEY_CHANNEL_COUNT)?.takeIf { it > 0 } ?: return
        val encoding = outputFormat.integerOrNull(MediaFormat.KEY_PCM_ENCODING) ?: AudioFormat.ENCODING_PCM_16BIT
        val bytesPerSample = encoding.bytesPerSample() ?: return
        val frameSize = bytesPerSample * channelCount
        if (frameSize <= 0) return

        val safeBuffer = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val frameCount = bufferInfo.size / frameSize
        val baseOffset = bufferInfo.offset
        val coroutineContext = currentCoroutineContext()
        repeat(frameCount) { frameIndex ->
            if (frameIndex % CANCEL_CHECK_FRAME_INTERVAL == 0) coroutineContext.ensureActive()
            val frameTimeUs = bufferInfo.presentationTimeUs + frameIndex * 1_000_000L / sampleRate
            if (frameTimeUs < startUs || frameTimeUs > endUs) return@repeat
            val windowIndex = ((frameTimeUs - startUs) / (LEVEL_WINDOW_MS * 1_000L)).toInt()
            if (windowIndex !in windows.indices) return@repeat

            val frameOffset = baseOffset + frameIndex * frameSize
            var peak = 0f
            var squareTotal = 0.0
            var sampleCount = 0
            repeat(channelCount) { channel ->
                val sampleOffset = frameOffset + channel * bytesPerSample
                if (sampleOffset + bytesPerSample <= safeBuffer.limit()) {
                    val sample = safeBuffer.normalizedSampleAt(sampleOffset, encoding)
                    peak = maxOf(peak, sample)
                    squareTotal += (sample * sample).toDouble()
                    sampleCount += 1
                }
            }
            windows[windowIndex].add(peak, squareTotal, sampleCount)
        }
    }

    private fun MediaExtractor.firstAudioTrackIndex(): Int? {
        for (index in 0 until trackCount) {
            val mimeType = getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
            if (mimeType.startsWith("audio/")) return index
        }
        return null
    }

    private fun ByteBuffer.normalizedSampleAt(offset: Int, encoding: Int): Float = when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> abs(((get(offset).toInt() and 0xff) - 128) / 128f)
        AudioFormat.ENCODING_PCM_16BIT -> abs(getShort(offset).toInt() / 32768f)
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> abs(signed24BitAt(offset) / 8_388_608f)
        AudioFormat.ENCODING_PCM_32BIT -> abs(getInt(offset) / 2_147_483_648f)
        AudioFormat.ENCODING_PCM_FLOAT -> abs(getFloat(offset)).coerceIn(0f, 1f)
        else -> 0f
    }

    private fun Int.bytesPerSample(): Int? = when (this) {
        AudioFormat.ENCODING_PCM_8BIT -> 1
        AudioFormat.ENCODING_PCM_16BIT -> 2
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
        AudioFormat.ENCODING_PCM_32BIT -> 4
        AudioFormat.ENCODING_PCM_FLOAT -> 4
        else -> null
    }

    private fun ByteBuffer.signed24BitAt(offset: Int): Int {
        val value = (get(offset).toInt() and 0xff) or
            ((get(offset + 1).toInt() and 0xff) shl 8) or
            ((get(offset + 2).toInt() and 0xff) shl 16)
        return if ((value and 0x800000) != 0) value or -0x1000000 else value
    }

    private fun MediaFormat.integerOrNull(key: String): Int? = if (containsKey(key)) getInteger(key) else null

    private class MutableAudioWindow(
        private val startMs: Long,
        private val endMs: Long,
    ) {
        private var peak = 0f
        private var squareTotal = 0.0
        private var sampleCount = 0

        fun add(framePeak: Float, frameSquareTotal: Double, frameSampleCount: Int) {
            if (framePeak > peak) peak = framePeak
            squareTotal += frameSquareTotal
            sampleCount += frameSampleCount
        }

        fun toWindow(): AudioWindow {
            val rms = if (sampleCount > 0) sqrt(squareTotal / sampleCount).toFloat() else 0f
            return AudioWindow(
                startMs = startMs,
                endMs = endMs,
                rms = rms.coerceIn(0f, 1f),
                peak = peak.coerceIn(0f, 1f),
            )
        }
    }

    private companion object {
        const val MAX_CACHE_SIZE = 80
        const val MAX_PROFILE_CACHE_SIZE = 48
        const val HEAD_ANALYSIS_WINDOW_MS = 15_000L
        const val TAIL_ANALYSIS_WINDOW_MS = 25_000L
        const val MAX_TAIL_SCAN_MS = 90_000L
        const val LEVEL_WINDOW_MS = 80L
        const val DEQUEUE_TIMEOUT_US = 10_000L
        const val CANCEL_CHECK_FRAME_INTERVAL = 4_096
    }
}
