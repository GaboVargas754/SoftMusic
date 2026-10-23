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
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** Short local PCM scans. Failure to establish a reliable pulse is a normal null result. */
class DjBeatAnalyzer(context: Context) {
    private val context = context.applicationContext
    private val cache = this.context.getSharedPreferences(CACHE_NAME, Context.MODE_PRIVATE)

    suspend fun analyze(uri: Uri, durationMs: Long, tail: Boolean): DjBeatProfile? = withContext(Dispatchers.IO) {
        if (durationMs < MIN_ANALYSIS_MS || durationMs > Long.MAX_VALUE / 1_000L) return@withContext null
        if (uri.scheme !in listOf("content", "file")) return@withContext null
        currentCoroutineContext().ensureActive()
        val regionStart = if (tail) (durationMs - ANALYSIS_WINDOW_MS).coerceAtLeast(0L) else 0L
        val regionEnd = minOf(durationMs, regionStart + ANALYSIS_WINDOW_MS)
        try {
            val fingerprint = sourceFingerprint(uri, durationMs, regionStart, regionEnd)
            if (fingerprint != null) {
                val cached = readCache(fingerprint)
                if (cached != null) return@withContext cached.profile
            }
            val decoded = decode(uri, regionStart, regionEnd) ?: return@withContext null
            val profile = withContext(Dispatchers.Default) {
                DjBeatDetector.detect(decoded.onsets, FRAME_MS.toDouble(), decoded.startMs, decoded.endMs)
            }
            currentCoroutineContext().ensureActive()
            if (fingerprint != null) writeCache(fingerprint, profile)
            profile
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            // Includes unsupported codecs, revoked URI access, malformed media and deadline expiry.
            null
        }
    }

    private suspend fun decode(uri: Uri, startMs: Long, endMs: Long): DecodedOnsets? {
        val startUs = startMs * 1_000L
        val endUs = endMs * 1_000L
        val deadline = SystemClock.elapsedRealtime() + DECODE_TIMEOUT_MS
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var decoderStarted = false
        try {
            extractor = MediaExtractor().also { it.setDataSource(context, uri, null) }
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val inputFormat = extractor.getTrackFormat(track)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return null
            val count = ((endMs - startMs + FRAME_MS - 1L) / FRAME_MS).toInt()
            val collector = OnsetCollector(startUs, endUs, count, deadline)
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()
            decoderStarted = true
            var outputFormat = inputFormat
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var packets = 0
            var outputBuffers = 0
            var encodedBytes = 0L
            var idleIterations = 0
            while (!outputDone) {
                currentCoroutineContext().ensureActive()
                checkDeadline(deadline)
                var progressed = false
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val timestampUs = extractor.sampleTime
                        if (timestampUs >= 0L && timestampUs < startUs - MAX_PREROLL_MS * 1_000L) return null
                        if (timestampUs < 0L || timestampUs > endUs) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val buffer = decoder.getInputBuffer(inputIndex) ?: return null
                            buffer.clear()
                            val size = extractor.readSampleData(buffer, 0)
                            if (size < 0) {
                                decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                encodedBytes += size
                                if (++packets > MAX_INPUT_PACKETS || encodedBytes > MAX_ENCODED_BYTES) return null
                                decoder.queueInputBuffer(inputIndex, 0, size, timestampUs, 0)
                                extractor.advance()
                            }
                        }
                        progressed = true
                    }
                }
                when (val outputIndex = decoder.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        outputFormat = decoder.outputFormat
                        progressed = true
                    }
                    else -> if (outputIndex >= 0) {
                        try {
                            if (++outputBuffers > MAX_OUTPUT_BUFFERS) return null
                            if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                val output = decoder.getOutputBuffer(outputIndex) ?: return null
                                if (!collector.collect(output, info, outputFormat)) return null
                                if (info.presentationTimeUs >= endUs) outputDone = true
                            }
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        } finally {
                            decoder.releaseOutputBuffer(outputIndex, false)
                        }
                        progressed = true
                    }
                }
                idleIterations = if (progressed) 0 else idleIterations + 1
                if (idleIterations > MAX_IDLE_ITERATIONS) return null
            }
            return collector.finish()
        } finally {
            // Cleanup errors must not replace cancellation or the original codec failure.
            if (decoderStarted) runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { extractor?.release() }
        }
    }

    private class OnsetCollector(
        private val startUs: Long,
        private val endUs: Long,
        count: Int,
        private val deadline: Long,
    ) {
        private val windows = Array(count) { EnergyWindow() }
        private var lower = DoubleArray(0)
        private var upper = DoubleArray(0)
        private var lastRate = 0
        private var decodedFrames = 0L
        private var lastFrameEndUs = startUs

        suspend fun collect(buffer: ByteBuffer, info: MediaCodec.BufferInfo, format: MediaFormat): Boolean {
            val rate = format.integer(MediaFormat.KEY_SAMPLE_RATE) ?: return false
            val channels = format.integer(MediaFormat.KEY_CHANNEL_COUNT) ?: return false
            val encoding = format.integer(MediaFormat.KEY_PCM_ENCODING) ?: AudioFormat.ENCODING_PCM_16BIT
            val sampleBytes = bytesPerSample(encoding) ?: return false
            if (rate !in 8_000..192_000 || channels !in 1..8) return false
            val frameBytes = sampleBytes * channels
            if (info.offset < 0 || info.size < 0 || info.offset.toLong() + info.size > buffer.limit()) return false
            val pcm = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            val frames = info.size / frameBytes
            decodedFrames += frames
            if (decodedFrames > MAX_DECODED_FRAMES) return false
            if (lower.size != channels || lastRate != rate) {
                lower = DoubleArray(channels)
                upper = DoubleArray(channels)
                lastRate = rate
            }
            val lowAlpha = 1.0 - exp(-2.0 * PI * 180.0 / rate)
            val upperAlpha = 1.0 - exp(-2.0 * PI * minOf(2_400.0, rate * 0.4) / rate)
            val coroutineContext = currentCoroutineContext()
            repeat(frames) { index ->
                if (index % CANCEL_CHECK_FRAMES == 0) {
                    coroutineContext.ensureActive()
                    checkDeadline(deadline)
                }
                val timestampUs = info.presentationTimeUs + index * 1_000_000L / rate
                val windowIndex = ((timestampUs - startUs) / (FRAME_MS * 1_000L)).toInt()
                var lowEnergy = 0.0
                var middleEnergy = 0.0
                var highEnergy = 0.0
                var peak = 0.0
                repeat(channels) { channel ->
                    val offset = info.offset + index * frameBytes + channel * sampleBytes
                    val sample = pcm.sample(offset, encoding)
                    lower[channel] += lowAlpha * (sample - lower[channel])
                    upper[channel] += upperAlpha * (sample - upper[channel])
                    val low = lower[channel]
                    val mid = upper[channel] - low
                    val high = sample - upper[channel]
                    lowEnergy += low * low
                    middleEnergy += mid * mid
                    highEnergy += high * high
                    peak = maxOf(peak, abs(sample))
                }
                if (timestampUs >= startUs && timestampUs < endUs && windowIndex in windows.indices) {
                    windows[windowIndex].add(lowEnergy, middleEnergy, highEnergy, peak, channels)
                    lastFrameEndUs = maxOf(lastFrameEndUs, minOf(endUs, timestampUs + 1_000_000L / rate))
                }
            }
            return true
        }

        fun finish(): DecodedOnsets? {
            val first = windows.indexOfFirst { it.samples > 0 }
            val last = windows.indexOfLast { it.samples > 0 }
            if (first < 0 || last < first) return null
            val missing = (first..last).count { windows[it].samples == 0 }
            if (missing > 2) return null // Never fit a grid across timestamp gaps or missing decode output.
            val startMs = startUs / 1_000L + first * FRAME_MS
            val endMs = minOf(lastFrameEndUs / 1_000L, startUs / 1_000L + (last + 1) * FRAME_MS)
            if (endMs - startMs < MIN_ANALYSIS_MS) return null
            val envelope = FloatArray(last - first + 1)
            val history = Array(3) { DoubleArray(3) }
            for (index in first..last) {
                val window = windows[index]
                val energies = window.compressedBands()
                var onset = 0.0
                for (band in 0..2) {
                    val baseline = history[0][band] * 0.5 + history[1][band] * 0.3 + history[2][band] * 0.2
                    onset += maxOf(0.0, energies[band] - baseline) * BAND_WEIGHTS[band]
                    history[2][band] = history[1][band]
                    history[1][band] = history[0][band]
                    history[0][band] = energies[band]
                }
                envelope[index - first] = onset.toFloat()
            }
            return DecodedOnsets(envelope, startMs, endMs)
        }
    }

    private class EnergyWindow {
        var samples = 0
            private set
        private val bands = DoubleArray(3)
        private var peak = 0.0

        fun add(low: Double, middle: Double, high: Double, framePeak: Double, channels: Int) {
            bands[0] += low
            bands[1] += middle
            bands[2] += high
            samples += channels
            peak = maxOf(peak, framePeak)
        }

        fun compressedBands(): DoubleArray = DoubleArray(3) { band ->
            if (samples == 0 || peak < 0.0003) 0.0 else ln(1.0 + 30.0 * sqrt(bands[band] / samples))
        }
    }

    private suspend fun sourceFingerprint(uri: Uri, durationMs: Long, startMs: Long, endMs: Long): String? {
        currentCoroutineContext().ensureActive()
        var size = -1L
        var modified = -1L
        var generation = -1L
        if (uri.scheme == "file") {
            val file = File(uri.path ?: return null)
            if (!file.isFile) return null
            size = file.length()
            modified = file.lastModified()
        } else {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        size = cursor.longOrMissing(OpenableColumns.SIZE)
                        modified = cursor.longOrMissing(MediaStore.MediaColumns.DATE_MODIFIED)
                        if (modified < 0L) modified = cursor.longOrMissing(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                        generation = cursor.longOrMissing("generation_modified")
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RuntimeException) {
                // Providers without revision metadata remain analyzable, but are never cached stale.
            }
            if (size < 0L) {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                    size = descriptor.length.takeIf { it >= 0L } ?: descriptor.parcelFileDescriptor.statSize
                }
            }
        }
        currentCoroutineContext().ensureActive()
        if (size < 0L || (modified < 0L && generation < 0L)) return null
        val source = "$ANALYSIS_VERSION|$uri|$durationMs|$size|$modified|$generation|$startMs|$endMs"
        return MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun readCache(key: String): CachedResult? = synchronized(CACHE_LOCK) {
        val serialized = cache.getString(key, null) ?: return@synchronized null
        try {
            val json = JSONObject(serialized)
            if (!json.getBoolean("reliable")) return@synchronized CachedResult(null)
            val profile = DjBeatProfile(
                json.getDouble("bpm").toFloat(), json.getDouble("confidence").toFloat(),
                json.getDouble("beat"), json.getLong("start"), json.getLong("end"),
            )
            if (!profile.bpm.isFinite() || profile.bpm !in 60f..200f || !profile.confidence.isFinite() ||
                profile.confidence !in 0.72f..1f || !profile.firstBeatMs.isFinite() ||
                profile.firstBeatMs < profile.analyzedStartMs || profile.firstBeatMs >= profile.analyzedEndMs
            ) return@synchronized null
            CachedResult(profile)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeCache(key: String, profile: DjBeatProfile?) = synchronized(CACHE_LOCK) {
        val json = JSONObject().put("reliable", profile != null).put("saved", System.currentTimeMillis())
        if (profile != null) {
            json.put("bpm", profile.bpm.toDouble()).put("confidence", profile.confidence.toDouble())
                .put("beat", profile.firstBeatMs).put("start", profile.analyzedStartMs).put("end", profile.analyzedEndMs)
        }
        val entries = cache.all.filterKeys { it != key }
        val toRemove = (entries.size + 1 - MAX_CACHE_ENTRIES).coerceAtLeast(0)
        val oldest = entries.entries.sortedBy { entry ->
            runCatching { JSONObject(entry.value as String).getLong("saved") }.getOrDefault(0L)
        }.take(toRemove)
        val editor = cache.edit().putString(key, json.toString())
        oldest.forEach { editor.remove(it.key) }
        editor.apply()
    }

    private data class DecodedOnsets(val onsets: FloatArray, val startMs: Long, val endMs: Long)
    private data class CachedResult(val profile: DjBeatProfile?)

    private companion object {
        const val ANALYSIS_VERSION = 3
        const val CACHE_NAME = "auto_dj_beats_v3"
        const val MAX_CACHE_ENTRIES = 96
        val CACHE_LOCK = Any()
        val BAND_WEIGHTS = doubleArrayOf(0.5, 0.3, 0.2)
        const val FRAME_MS = 10L
        const val ANALYSIS_WINDOW_MS = 32_000L
        const val MIN_ANALYSIS_MS = 8_000L
        const val MAX_PREROLL_MS = 5_000L
        const val DECODE_TIMEOUT_MS = 12_000L
        const val DEQUEUE_TIMEOUT_US = 5_000L
        const val MAX_INPUT_PACKETS = 15_000
        const val MAX_OUTPUT_BUFFERS = 20_000
        const val MAX_IDLE_ITERATIONS = 200
        const val MAX_ENCODED_BYTES = 96L * 1_024L * 1_024L
        const val MAX_DECODED_FRAMES = 12_000_000L
        const val CANCEL_CHECK_FRAMES = 4_096

        fun checkDeadline(deadline: Long) {
            if (SystemClock.elapsedRealtime() > deadline) throw AnalysisDeadlineException()
        }

        fun MediaFormat.integer(key: String): Int? = if (containsKey(key)) getInteger(key) else null

        fun Cursor.longOrMissing(column: String): Long = getColumnIndex(column).let { index ->
            if (index < 0 || isNull(index)) -1L else getLong(index)
        }

        fun bytesPerSample(encoding: Int): Int? = when (encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
            AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 4
            else -> null
        }

        fun ByteBuffer.sample(offset: Int, encoding: Int): Double {
            val value = when (encoding) {
                AudioFormat.ENCODING_PCM_8BIT -> ((get(offset).toInt() and 0xff) - 128) / 128.0
                AudioFormat.ENCODING_PCM_16BIT -> getShort(offset) / 32768.0
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                    val packed = (get(offset).toInt() and 0xff) or
                        ((get(offset + 1).toInt() and 0xff) shl 8) or (get(offset + 2).toInt() shl 16)
                    packed / 8_388_608.0
                }
                AudioFormat.ENCODING_PCM_32BIT -> getInt(offset) / 2_147_483_648.0
                AudioFormat.ENCODING_PCM_FLOAT -> getFloat(offset).toDouble()
                else -> 0.0
            }
            return if (value.isFinite()) value.coerceIn(-1.0, 1.0) else 0.0
        }
    }

    private class AnalysisDeadlineException : RuntimeException()
}
