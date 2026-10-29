package com.softmusic.app.player

import android.content.Context
import android.database.Cursor
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

class DjSilenceAnalyzer(private val context: Context) {
    private val headProfileCache = DjAnalysisCache<AudioProfile>(MAX_PROFILE_CACHE_SIZE)
    private val tailProfileCache = DjAnalysisCache<AudioProfile>(MAX_PROFILE_CACHE_SIZE)

    data class AudioWindow(
        val startMs: Long,
        val endMs: Long,
        val rms: Float,
        val peak: Float,
        val isMeasured: Boolean = true,
    )

    data class AudioProfile(
        val startMs: Long,
        val endMs: Long,
        val windows: List<AudioWindow>,
        val isComplete: Boolean = true,
    ) {
        fun isReliable(): Boolean = isComplete && endMs > startMs && windows.isNotEmpty() &&
            windows.all { window ->
                window.isMeasured && window.endMs > window.startMs &&
                    window.startMs >= startMs && window.endMs <= endMs &&
                    window.rms.isFinite() && window.peak.isFinite() &&
                    window.rms in 0f..1f && window.peak in 0f..1f
            }

        fun audibleStartMs(): Long? {
            if (!isReliable()) return null
            val threshold = edgeAudibleThreshold()
            return firstAudibleWindowIndex(threshold)?.let { index ->
                windows[index].startMs - START_MARGIN_MS
            }?.coerceAtLeast(0L)
        }

        fun audibleEndMs(durationMs: Long): Long? {
            if (!isReliable() || durationMs <= 0L) return null
            val threshold = edgeAudibleThreshold()
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

        fun hasAudibleContent(): Boolean = isReliable() && lastAudibleWindowIndex(edgeAudibleThreshold()) != null

        // A quiet musical edge must not disappear merely because the body is much louder.
        // The adaptive threshold remains useful for choosing a low-energy mixing position.
        private fun edgeAudibleThreshold(): Float = minOf(audibleThreshold(), AUDIBLE_RMS_FLOOR)

        fun audibleThreshold(): Float {
            val measured = windows
                .filter { it.isMeasured }
                .map { it.rms }
                .filter { it.isFinite() && it >= MIN_MEASURED_RMS }
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
                if (window.isMeasured && window.endMs >= start && window.startMs <= end) {
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

    suspend fun audibleStartMs(uri: Uri): Long? = headProfile(uri)?.audibleStartMs()

    suspend fun audibleEndMs(uri: Uri, durationMs: Long): Long? = tailProfile(uri, durationMs)?.audibleEndMs(durationMs)

    suspend fun headProfile(uri: Uri): AudioProfile? = withContext(Dispatchers.Default) {
        analyzeCached(uri, null, headProfileCache) { watchdog ->
            decodeProfile(uri, 0L, HEAD_ANALYSIS_WINDOW_MS * 1_000L, watchdog, clampToTrackEnd = true)
        }
    }

    suspend fun tailProfile(uri: Uri, durationMs: Long): AudioProfile? = withContext(Dispatchers.Default) {
        if (durationMs <= 0L || durationMs > Long.MAX_VALUE / 1_000L) return@withContext null
        analyzeCached(uri, durationMs, tailProfileCache) { watchdog -> scanTailProfile(uri, durationMs, watchdog) }
    }

    private suspend fun analyzeCached(
        uri: Uri,
        rangeEndMs: Long?,
        cache: DjAnalysisCache<AudioProfile>,
        analyze: suspend (DjDecodeWatchdog) -> AudioProfile?,
    ): AudioProfile? {
        currentCoroutineContext().ensureActive()
        val watchdog = DjDecodeWatchdog(SystemClock.elapsedRealtime(), ANALYSIS_TIMEOUT_MS, DECODE_STALL_TIMEOUT_MS)
        return try {
            val revision = sourceRevision(uri)
            cache.get(uri.toString(), revision, rangeEndMs)?.let { return it }
            watchdog.check(SystemClock.elapsedRealtime())
            val profile = analyze(watchdog)?.takeIf { it.isReliable() } ?: return null
            currentCoroutineContext().ensureActive()
            val revisionAfterDecode = sourceRevision(uri)
            watchdog.check(SystemClock.elapsedRealtime())
            // A replacement during decoding must not produce a cached mixture of two sources.
            if (revision != revisionAfterDecode) return null
            cache.put(uri.toString(), revisionAfterDecode, profile, rangeEndMs)
            profile
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun scanTailProfile(uri: Uri, durationMs: Long, watchdog: DjDecodeWatchdog): AudioProfile? {
        val profiles = mutableListOf<AudioProfile>()
        var segmentEndMs = durationMs
        var scannedMs = 0L

        while (segmentEndMs > 0L && scannedMs < MAX_TAIL_SCAN_MS) {
            currentCoroutineContext().ensureActive()
            watchdog.check(SystemClock.elapsedRealtime())
            val segmentLengthMs = minOf(TAIL_ANALYSIS_WINDOW_MS, MAX_TAIL_SCAN_MS - scannedMs)
            val segmentStartMs = (segmentEndMs - segmentLengthMs).coerceAtLeast(0L)
            val profile = decodeProfile(uri, segmentStartMs * 1_000L, segmentEndMs * 1_000L, watchdog)
                ?.takeIf { it.isReliable() } ?: return null
            profiles += profile

            scannedMs += segmentEndMs - segmentStartMs
            if (profile.hasAudibleContent() || segmentStartMs == 0L) break
            segmentEndMs = segmentStartMs
        }

        val windows = profiles.asReversed().flatMap { it.windows }
        return AudioProfile(
            startMs = profiles.lastOrNull()?.startMs ?: return null,
            endMs = durationMs,
            windows = windows,
            isComplete = profiles.all { it.isComplete },
        ).takeIf { it.isReliable() }
    }

    private suspend fun decodeProfile(
        uri: Uri,
        startUs: Long,
        endUs: Long,
        watchdog: DjDecodeWatchdog,
        clampToTrackEnd: Boolean = false,
    ): AudioProfile? {
        if (endUs <= startUs) return null
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null

        try {
            currentCoroutineContext().ensureActive()
            watchdog.check(SystemClock.elapsedRealtime())
            extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)
            val trackIndex = extractor.firstAudioTrackIndex() ?: return null
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val trackDurationUs = inputFormat.longOrNull(MediaFormat.KEY_DURATION)?.takeIf { it > 0L }
            val effectiveEndUs = if (clampToTrackEnd && trackDurationUs != null) minOf(endUs, trackDurationUs) else endUs
            if (effectiveEndUs <= startUs) return null
            val startMs = startUs / 1_000L
            val endMs = effectiveEndUs / 1_000L
            val windowDurationUs = LEVEL_WINDOW_MS * 1_000L
            val windowCount = ((effectiveEndUs - startUs + windowDurationUs - 1L) / windowDurationUs).toInt()
            val windows = List(windowCount) { index ->
                val windowStartUs = startUs + index * windowDurationUs
                val windowEndUs = minOf(windowStartUs + windowDurationUs, effectiveEndUs)
                MutableAudioWindow(
                    startUs = windowStartUs,
                    endUs = windowEndUs,
                    startToleranceUs = if (index == 0) EDGE_COVERAGE_TOLERANCE_US else TIMESTAMP_TOLERANCE_US,
                    endToleranceUs = if (index == windowCount - 1) EDGE_COVERAGE_TOLERANCE_US else TIMESTAMP_TOLERANCE_US,
                )
            }
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val mimeType = inputFormat.getString(MediaFormat.KEY_MIME) ?: return null
            watchdog.check(SystemClock.elapsedRealtime())
            codec = MediaCodec.createDecoderByType(mimeType)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var outputFormat = inputFormat
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                currentCoroutineContext().ensureActive()
                watchdog.check(SystemClock.elapsedRealtime())
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex) ?: return null
                        val sampleTimeUs = extractor.sampleTime
                        if (sampleTimeUs < 0L || sampleTimeUs >= effectiveEndUs) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            inputBuffer.clear()
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
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
                        try {
                            if (bufferInfo.size > 0 && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                val outputBuffer = codec.getOutputBuffer(outputIndex) ?: return null
                                if (!collectMetrics(outputBuffer, bufferInfo, outputFormat, startUs, effectiveEndUs, windows, watchdog)) {
                                    return null
                                }
                                watchdog.decodedOutput(SystemClock.elapsedRealtime())
                            }
                            outputDone = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        } finally {
                            codec.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                }
            }
            val measuredWindows = windows.map { it.toWindow() }
            return AudioProfile(
                startMs = startMs,
                endMs = endMs,
                windows = measuredWindows,
                isComplete = measuredWindows.all { it.isMeasured },
            ).takeIf { it.isReliable() }
        } finally {
            codec?.let { decoder ->
                runCatching { decoder.stop() }
                runCatching { decoder.release() }
            }
            runCatching { extractor?.release() }
        }
    }

    private suspend fun collectMetrics(
        buffer: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo,
        outputFormat: MediaFormat,
        startUs: Long,
        endUs: Long,
        windows: List<MutableAudioWindow>,
        watchdog: DjDecodeWatchdog,
    ): Boolean {
        val sampleRate = outputFormat.integerOrNull(MediaFormat.KEY_SAMPLE_RATE)?.takeIf { it in 1..MAX_SAMPLE_RATE } ?: return false
        val channelCount = outputFormat.integerOrNull(MediaFormat.KEY_CHANNEL_COUNT)?.takeIf { it in 1..MAX_CHANNEL_COUNT } ?: return false
        val encoding = outputFormat.integerOrNull(MediaFormat.KEY_PCM_ENCODING) ?: AudioFormat.ENCODING_PCM_16BIT
        val bytesPerSample = encoding.bytesPerSample() ?: return false
        val frameSize = bytesPerSample * channelCount
        val safeBuffer = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        if (bufferInfo.offset < 0 || bufferInfo.size < 0 || bufferInfo.size % frameSize != 0 ||
            bufferInfo.offset.toLong() + bufferInfo.size > safeBuffer.limit()
        ) return false
        val frameCount = bufferInfo.size / frameSize
        val coroutineContext = currentCoroutineContext()
        repeat(frameCount) { frameIndex ->
            if (frameIndex % CANCEL_CHECK_FRAME_INTERVAL == 0) {
                coroutineContext.ensureActive()
                watchdog.check(SystemClock.elapsedRealtime())
            }
            val frameTimeUs = bufferInfo.presentationTimeUs + frameIndex * 1_000_000L / sampleRate
            val frameEndUs = bufferInfo.presentationTimeUs + (frameIndex + 1L) * 1_000_000L / sampleRate
            if (frameEndUs <= startUs || frameTimeUs >= endUs) return@repeat
            val windowIndex = ((maxOf(frameTimeUs, startUs) - startUs) / (LEVEL_WINDOW_MS * 1_000L)).toInt()
            if (windowIndex !in windows.indices) return@repeat

            val frameOffset = bufferInfo.offset + frameIndex * frameSize
            var peak = 0f
            var squareTotal = 0.0
            repeat(channelCount) { channel ->
                val sample = safeBuffer.normalizedSampleAt(frameOffset + channel * bytesPerSample, encoding)
                if (!sample.isFinite()) return false
                peak = maxOf(peak, sample)
                squareTotal += (sample * sample).toDouble()
            }
            windows[windowIndex].add(frameTimeUs, frameEndUs, peak, squareTotal, channelCount)
        }
        return true
    }

    private suspend fun sourceRevision(uri: Uri): DjAnalysisSourceRevision? {
        currentCoroutineContext().ensureActive()
        return try {
            val revision = when (uri.scheme) {
                "file" -> {
                    val file = File(uri.path ?: return null)
                    if (!file.isFile) return null
                    DjAnalysisSourceRevision(file.length(), file.lastModified())
                }
                "content" -> {
                    var size = -1L
                    var modifiedMs = -1L
                    var generation = -1L
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            size = cursor.longOrMissing(OpenableColumns.SIZE)
                            val modifiedSeconds = cursor.longOrMissing(MediaStore.MediaColumns.DATE_MODIFIED)
                            modifiedMs = if (modifiedSeconds in 1L..Long.MAX_VALUE / 1_000L) modifiedSeconds * 1_000L
                                else cursor.longOrMissing(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                            generation = cursor.longOrMissing("generation_modified")
                        }
                    }
                    if (size < 0L) context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                        size = descriptor.length.takeIf { it >= 0L } ?: descriptor.parcelFileDescriptor.statSize
                    }
                    DjAnalysisSourceRevision(size, modifiedMs, generation)
                }
                else -> null
            }
            currentCoroutineContext().ensureActive()
            revision?.takeIf { it.isCacheable }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private fun Cursor.longOrMissing(column: String): Long {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getLong(index) else -1L
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
        AudioFormat.ENCODING_PCM_FLOAT -> getFloat(offset).let { sample ->
            if (sample.isFinite()) abs(sample).coerceIn(0f, 1f) else Float.NaN
        }
        else -> Float.NaN
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
    private fun MediaFormat.longOrNull(key: String): Long? = if (containsKey(key)) getLong(key) else null

    private class MutableAudioWindow(
        private val startUs: Long,
        private val endUs: Long,
        startToleranceUs: Long,
        endToleranceUs: Long,
    ) {
        private val coverage = DjDecodedCoverage(
            startUs,
            endUs,
            minOf(startToleranceUs, (endUs - startUs) / 10L),
            minOf(endToleranceUs, (endUs - startUs) / 10L),
        )
        private var peak = 0f
        private var squareTotal = 0.0
        private var sampleCount = 0L

        fun add(frameStartUs: Long, frameEndUs: Long, framePeak: Float, frameSquareTotal: Double, frameSampleCount: Int) {
            coverage.record(frameStartUs, frameEndUs)
            if (framePeak > peak) peak = framePeak
            squareTotal += frameSquareTotal
            sampleCount += frameSampleCount
        }

        fun toWindow(): AudioWindow {
            val rms = if (sampleCount > 0) sqrt(squareTotal / sampleCount).toFloat() else 0f
            return AudioWindow(
                startMs = startUs / 1_000L,
                endMs = endUs / 1_000L,
                rms = rms.coerceIn(0f, 1f),
                peak = peak.coerceIn(0f, 1f),
                isMeasured = sampleCount > 0L && coverage.isComplete(),
            )
        }
    }

    private companion object {
        const val MAX_PROFILE_CACHE_SIZE = 48
        const val HEAD_ANALYSIS_WINDOW_MS = 15_000L
        const val TAIL_ANALYSIS_WINDOW_MS = 25_000L
        const val MAX_TAIL_SCAN_MS = 90_000L
        const val LEVEL_WINDOW_MS = 80L
        const val DEQUEUE_TIMEOUT_US = 10_000L
        const val CANCEL_CHECK_FRAME_INTERVAL = 4_096
        const val ANALYSIS_TIMEOUT_MS = 15_000L
        const val DECODE_STALL_TIMEOUT_MS = 2_000L
        const val EDGE_COVERAGE_TOLERANCE_US = 20_000L
        const val TIMESTAMP_TOLERANCE_US = 1_000L
        const val MAX_SAMPLE_RATE = 768_000
        const val MAX_CHANNEL_COUNT = 32
    }
}
