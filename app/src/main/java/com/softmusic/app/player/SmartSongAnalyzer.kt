package com.softmusic.app.player

import android.content.Context
import android.net.Uri
import com.softmusic.app.data.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.sqrt

/**
 * Offline queue hints from bounded opening scans (32 seconds for tempo, 15 for RMS).
 * These approximate the opening's pulse and level; they do not estimate key, genre, whole-song
 * energy or perceived loudness. The underlying analyzers own source-revision-aware caches and
 * decode deadlines, so a song ID alone is never used to cache a possibly replaced source.
 */
class SmartSongAnalyzer(context: Context) {
    private val beats = DjBeatAnalyzer(context.applicationContext)
    private val levels = DjSilenceAnalyzer(context.applicationContext)

    suspend fun analyze(song: Song): SmartSongProfile {
        currentCoroutineContext().ensureActive()
        if (song.durationMs <= 0L || song.durationMs > Long.MAX_VALUE / 1_000L || song.uri.isBlank()) {
            return SmartSongProfile()
        }
        val uri = try {
            Uri.parse(song.uri)
        } catch (_: RuntimeException) {
            return SmartSongProfile()
        }
        if (uri.scheme !in listOf("file", "content")) return SmartSongProfile()
        // Run sequentially to keep this wrapper's simultaneous decoder count at one. A failed
        // tempo estimate is normal and still permits measured level to inform the queue.
        val beat = try {
            beats.analyze(uri, song.durationMs, tail = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            null
        }
        currentCoroutineContext().ensureActive()
        val level = try {
            levels.headProfile(uri)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            null
        }
        currentCoroutineContext().ensureActive()
        return smartSongProfile(beat, level)
    }
}

/** Kept separate from Android decoding so coverage and invalid-analysis handling are testable. */
internal fun smartSongProfile(
    beat: DjBeatProfile?,
    level: DjSilenceAnalyzer.AudioProfile?,
): SmartSongProfile {
    val reliableBeat = beat?.takeIf {
        it.bpm.isFinite() && it.bpm in 40f..240f && it.confidence.isFinite() && it.confidence in 0.72f..1f
    }
    val reliableLevel = level?.takeIf { it.isReliable() }
    var squaredEnergy = 0.0
    var measuredMs = 0.0
    reliableLevel?.windows?.forEach { window ->
        val duration = (window.endMs - window.startMs).toDouble()
        squaredEnergy += window.rms.toDouble() * window.rms * duration
        measuredMs += duration
    }
    val rms = if (measuredMs > 0.0) sqrt(squaredEnergy / measuredMs).toFloat()
        .takeIf { it.isFinite() && it in 0f..1f } else null
    return SmartSongProfile(reliableBeat?.bpm, reliableBeat?.confidence ?: 0f, rms)
}
