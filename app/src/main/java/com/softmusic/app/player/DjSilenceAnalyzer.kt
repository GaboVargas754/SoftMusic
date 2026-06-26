package com.softmusic.app.player

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class DjSilenceAnalyzer(private val context: Context) {
    private val startCache = object : LinkedHashMap<String, Long>(MAX_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > MAX_CACHE_SIZE
    }
    private val endCache = object : LinkedHashMap<String, Long>(MAX_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > MAX_CACHE_SIZE
    }

    suspend fun audibleStartMs(uri: Uri): Long? = withContext(Dispatchers.Default) {
        val key = uri.toString()
        synchronized(startCache) { startCache[key] } ?: runCatching {
            val levels = decodeLevels(
                uri = uri,
                startUs = 0L,
                endUs = HEAD_ANALYSIS_WINDOW_MS * 1_000L,
            )
            val firstAudibleWindow = levels.firstAudibleWindowIndex() ?: return@runCatching 0L
            (firstAudibleWindow * LEVEL_WINDOW_MS - START_MARGIN_MS).coerceAtLeast(0L)
        }.getOrNull()?.also { value ->
            synchronized(startCache) { startCache[key] = value }
        }
    }

    suspend fun audibleEndMs(uri: Uri, durationMs: Long): Long? = withContext(Dispatchers.Default) {
        if (durationMs <= 0L) return@withContext null
        val key = "${uri}|$durationMs"
        synchronized(endCache) { endCache[key] } ?: runCatching {
            val startMs = (durationMs - TAIL_ANALYSIS_WINDOW_MS).coerceAtLeast(0L)
            val levels = decodeLevels(
                uri = uri,
                startUs = startMs * 1_000L,
                endUs = durationMs * 1_000L,
            )
            val lastAudibleWindow = levels.lastAudibleWindowIndex() ?: return@runCatching durationMs
            (startMs + (lastAudibleWindow + 1) * LEVEL_WINDOW_MS + END_MARGIN_MS).coerceIn(0L, durationMs)
        }.getOrNull()?.also { value ->
            synchronized(endCache) { endCache[key] = value }
        }
    }

    private fun decodeLevels(uri: Uri, startUs: Long, endUs: Long): FloatArray {
        if (endUs <= startUs) return FloatArray(0)
        val windowCount = (((endUs - startUs) / (LEVEL_WINDOW_MS * 1_000L)).toInt() + 2).coerceAtLeast(1)
        val levels = FloatArray(windowCount)
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null

        try {
            extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)
            val trackIndex = extractor.firstAudioTrackIndex() ?: return levels
            extractor.selectTrack(trackIndex)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mimeType = inputFormat.getString(MediaFormat.KEY_MIME) ?: return levels
            codec = MediaCodec.createDecoderByType(mimeType)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var outputFormat = inputFormat
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
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
                            collectLevels(
                                buffer = outputBuffer,
                                bufferInfo = bufferInfo,
                                outputFormat = outputFormat,
                                startUs = startUs,
                                endUs = endUs,
                                levels = levels,
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

        return levels
    }

    private fun collectLevels(
        buffer: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo,
        outputFormat: MediaFormat,
        startUs: Long,
        endUs: Long,
        levels: FloatArray,
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
        repeat(frameCount) { frameIndex ->
            val frameTimeUs = bufferInfo.presentationTimeUs + frameIndex * 1_000_000L / sampleRate
            if (frameTimeUs < startUs || frameTimeUs > endUs) return@repeat
            val windowIndex = ((frameTimeUs - startUs) / (LEVEL_WINDOW_MS * 1_000L)).toInt()
            if (windowIndex !in levels.indices) return@repeat

            val frameOffset = baseOffset + frameIndex * frameSize
            var peak = 0f
            repeat(channelCount) { channel ->
                val sampleOffset = frameOffset + channel * bytesPerSample
                if (sampleOffset + bytesPerSample <= safeBuffer.capacity()) {
                    peak = maxOf(peak, safeBuffer.normalizedSampleAt(sampleOffset, encoding))
                }
            }
            if (peak > levels[windowIndex]) levels[windowIndex] = peak
        }
    }

    private fun MediaExtractor.firstAudioTrackIndex(): Int? {
        for (index in 0 until trackCount) {
            val mimeType = getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
            if (mimeType.startsWith("audio/")) return index
        }
        return null
    }

    private fun FloatArray.firstAudibleWindowIndex(): Int? {
        return indices.firstOrNull { index -> isAudibleWindow(index) }
            ?: indices.firstOrNull { index -> this[index] >= AUDIBLE_THRESHOLD }
    }

    private fun FloatArray.lastAudibleWindowIndex(): Int? {
        return indices.reversed().firstOrNull { index -> isAudibleWindow(index) }
            ?: indices.reversed().firstOrNull { index -> this[index] >= AUDIBLE_THRESHOLD }
    }

    private fun FloatArray.isAudibleWindow(index: Int): Boolean {
        if (this[index] >= STRONG_AUDIBLE_THRESHOLD) return true
        return this[index] >= AUDIBLE_THRESHOLD && (
            getOrNull(index - 1).orZero() >= AUDIBLE_THRESHOLD ||
                getOrNull(index + 1).orZero() >= AUDIBLE_THRESHOLD
            )
    }

    private fun ByteBuffer.normalizedSampleAt(offset: Int, encoding: Int): Float = when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> abs(((get(offset).toInt() and 0xff) - 128) / 128f)
        AudioFormat.ENCODING_PCM_16BIT -> abs(getShort(offset).toInt() / 32768f)
        AudioFormat.ENCODING_PCM_FLOAT -> abs(getFloat(offset)).coerceIn(0f, 1f)
        else -> 0f
    }

    private fun Int.bytesPerSample(): Int? = when (this) {
        AudioFormat.ENCODING_PCM_8BIT -> 1
        AudioFormat.ENCODING_PCM_16BIT -> 2
        AudioFormat.ENCODING_PCM_FLOAT -> 4
        else -> null
    }

    private fun MediaFormat.integerOrNull(key: String): Int? = if (containsKey(key)) getInteger(key) else null

    private fun Float?.orZero(): Float = this ?: 0f

    private companion object {
        const val MAX_CACHE_SIZE = 80
        const val HEAD_ANALYSIS_WINDOW_MS = 15_000L
        const val TAIL_ANALYSIS_WINDOW_MS = 25_000L
        const val LEVEL_WINDOW_MS = 80L
        const val START_MARGIN_MS = 250L
        const val END_MARGIN_MS = 250L
        const val AUDIBLE_THRESHOLD = 0.006f
        const val STRONG_AUDIBLE_THRESHOLD = 0.018f
        const val DEQUEUE_TIMEOUT_US = 10_000L
    }
}
