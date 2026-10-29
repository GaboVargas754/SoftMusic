package com.softmusic.app.player

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.softmusic.app.data.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Dependency-free device validation, launched with:
 * adb shell am instrument -w -r com.softmusic.app.test/com.softmusic.app.player.DjValidationInstrumentation
 *
 * Fixtures and JSON traces live in the target app's files/dj-validation directory. Tests use the
 * actual Android extractor/decoder and the production two-player coordinator. Volume observations
 * are player controls; they are not a recording or proof of the device's acoustic output.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class DjValidationInstrumentation : Instrumentation() {
    private lateinit var outputDirectory: File
    private lateinit var fixtures: Fixtures
    private val results = JSONArray()
    private val suiteStartedMs = SystemClock.elapsedRealtime()
    private var foregroundActivity: Activity? = null
    private var selectedScenario: String? = null

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        selectedScenario = arguments?.getString("scenario")
        start()
    }

    override fun onStart() {
        // Instrumentation's worker thread remains free to suspend; every player access uses Main.
        runBlocking(Dispatchers.Default) {
            outputDirectory = File(targetContext.filesDir, "dj-validation").apply { mkdirs() }
            try {
                withTimeout(180_000L) {
                    fixtures = createFixtures()
                    case("android_decoder_reliability") { validateDecoder() }
                    case("pcm_processor_level_and_bass_swap") { validateAudioProcessor() }
                    case("decoded_adaptive_duration_and_beat_rules") { validateMusicalPlans() }
                    case("smart_queue_real_audio_ranking") { validateSmartRanking() }
                    case("playlist_shuffle_preference_isolation") { validatePlaylistShufflePreferences() }
                    // API 35 requires a foreground app for audio focus. No media permissions are
                    // needed: these are app-owned files and an ordinary foreground activity.
                    foregroundActivity = startActivitySync(
                        Intent().setClassName(targetContext.packageName, "com.softmusic.app.MainActivity")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    case("smart_queue_player_preserves_session") { validateSmartQueuePlayer() }
                    case("long_silent_tail_early_handoff") { validateLongTail() }
                    case("normal_blend_with_quiet_then_loud_intro") { validateNormalBlend() }
                    case("pause_during_fade") { validateInterruption(Interruption.Pause) }
                    case("seek_during_fade") { validateInterruption(Interruption.Seek) }
                    case("remove_queued_target_during_fade") { validateInterruption(Interruption.RemoveTarget) }
                    case("confident_beat_start_at_native_speed") { validateBeatPlayback() }
                    case("preview_stop_restores_playing_original") { validatePreview(PreviewAction.StopPlaying) }
                    case("preview_completion_restores_playing_original") { validatePreview(PreviewAction.Complete) }
                    case("preview_stop_preserves_originally_paused") { validatePreview(PreviewAction.StopPaused) }
                    case("preview_seek_override_stays_paused") { validatePreview(PreviewAction.SeekOverride) }
                    case("preview_queue_override_stays_paused") { validatePreview(PreviewAction.QueueOverride) }
                }
            } catch (failure: Throwable) {
                results.put(failureResult("suite_setup_or_timeout", failure, suiteStartedMs))
            } finally {
                withContext(Dispatchers.Main) { foregroundActivity?.finish() }
            }
            val report = report()
            File(outputDirectory, "report.json").writeText(report.toString(2))
            val passed = results.length() > 0 &&
                (0 until results.length()).all { results.getJSONObject(it).optBoolean("passed") }
            finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle().apply {
                putString("stream", "\n${report}\n")
                putString("dj_validation_report", report.toString())
                putString("report_path", "files/dj-validation/report.json")
                if (!passed) putString("shortMsg", "DJ validation failed; inspect report.json and scenario traces")
            })
        }
    }

    private suspend fun case(name: String, block: suspend () -> JSONObject) {
        if (selectedScenario != null && selectedScenario != name) return
        val started = SystemClock.elapsedRealtime()
        sendStatus(1, Bundle().apply { putString("stream", "DJ validation: $name\n") })
        val result = try {
            block().put("name", name).put("passed", true)
                .put("elapsedMs", SystemClock.elapsedRealtime() - started)
        } catch (failure: Throwable) {
            if (failure is CancellationException && !currentCoroutineContext().isActive) throw failure
            failureResult(name, failure, started)
        }
        results.put(result)
        File(outputDirectory, "report.json").writeText(report().toString(2))
        sendStatus(if (result.getBoolean("passed")) 0 else -2, Bundle().apply {
            putString("stream", "$result\n")
        })
    }

    private fun report() = JSONObject()
        .put("runner", javaClass.name)
        .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
        .put("sdk", Build.VERSION.SDK_INT)
        .put("selectedScenario", selectedScenario ?: "all")
        .put("elapsedMs", SystemClock.elapsedRealtime() - suiteStartedMs)
        .put("observation", "Actual Android decoder and ExoPlayer state/volume; no captured system audio")
        .put("outputDirectory", "files/dj-validation")
        .put("fixtures", if (::fixtures.isInitialized) fixtures.json() else JSONObject())
        .put("results", results)

    private fun failureResult(name: String, failure: Throwable, started: Long) = JSONObject()
        .put("name", name).put("passed", false)
        .put("elapsedMs", SystemClock.elapsedRealtime() - started)
        .put("error", failure.toString())
        .put("stackTrace", failure.stackTraceToString().take(12_000))

    private suspend fun validateDecoder(): JSONObject {
        val analyzer = DjSilenceAnalyzer(targetContext)
        val tail = requireNotNull(analyzer.tailProfile(Uri.fromFile(fixtures.longTail), 60_000L)) {
            "Real PCM tail decode returned no profile"
        }
        check(tail.isReliable()) { "Complete PCM tail must be reliable" }
        val audibleEnd = requireNotNull(tail.audibleEndMs(60_000L))
        check(audibleEnd in 19_700L..20_700L) { "Expected audible end near 20 s, got $audibleEnd" }
        val head = requireNotNull(analyzer.headProfile(Uri.fromFile(fixtures.leadingSilence)))
        check(head.isReliable())
        val audibleStart = requireNotNull(head.audibleStartMs())
        check(audibleStart in 2_600L..3_100L) { "Expected 3 s leading silence, got $audibleStart" }
        val quietIntro = requireNotNull(analyzer.headProfile(Uri.fromFile(fixtures.quietIntro)))
        check(quietIntro.isReliable())
        check(requireNotNull(quietIntro.audibleStartMs()) <= 300L) { "Quiet musical intro was trimmed" }
        val quietOutroFile = writeWav("quiet-outro-24s.wav", 24_000L) { ms ->
            when { ms < 17_000L -> 0.55; ms < 18_000L -> 0.014; else -> 0.0 }
        }
        val quietOutro = requireNotNull(analyzer.tailProfile(Uri.fromFile(quietOutroFile), 24_000L))
        val quietOutroEnd = requireNotNull(quietOutro.audibleEndMs(24_000L))
        check(quietOutroEnd in 17_800L..18_700L) { "Quiet musical outro was trimmed: $quietOutroEnd" }
        val silence = requireNotNull(analyzer.headProfile(Uri.fromFile(fixtures.silence)))
        check(silence.isReliable()) { "Decoded zero-valued PCM is measured silence, not missing data" }
        check(!silence.hasAudibleContent())
        check(analyzer.tailProfile(Uri.fromFile(fixtures.truncated), 60_000L) == null) {
            "A 60 s WAV whose PCM ends 20 s in must not synthesize 40 s of trusted silence"
        }
        check(analyzer.tailProfile(Uri.fromFile(File(outputDirectory, "does-not-exist.wav")), 60_000L) == null)
        check(analyzer.tailProfile(Uri.fromFile(fixtures.empty), 60_000L) == null) {
            "An empty WAV must not be classified as measured silence"
        }
        val replacement = File(outputDirectory, "cache-replacement-12s.wav")
        fixtures.silence.copyTo(replacement, overwrite = true)
        val beforeReplacement = requireNotNull(analyzer.headProfile(Uri.fromFile(replacement)))
        check(!beforeReplacement.hasAudibleContent())
        val oldModifiedMs = replacement.lastModified()
        writeWav(replacement.name, 12_000L) { 0.18 }
        check(replacement.setLastModified(oldModifiedMs + 2_000L))
        val afterReplacement = requireNotNull(analyzer.headProfile(Uri.fromFile(replacement)))
        check(afterReplacement.hasAudibleContent()) { "Same-URI, same-size source replacement reused stale silence" }
        val fallback = ExpertDjPlanner.plan(60_000L, 8_000L, null, head)
            ?: ExpertDjPlanner.classic(60_000L, 8_000L)
        check(fallback.triggerPositionMs == 52_000L && fallback.mixDurationMs == 8_000L)
        return JSONObject().put("audibleEndMs", audibleEnd).put("audibleStartMs", audibleStart)
            .put("tailWindowCount", tail.windows.size)
            .put("quietIntroStartMs", quietIntro.audibleStartMs())
            .put("quietOutroEndMs", quietOutroEnd)
            .put("measuredSilenceWindowCount", silence.windows.size)
            .put("truncatedDecodeRejected", true).put("emptyDecodeRejected", true)
            .put("sameUriSameSizeReplacementReanalyzed", true)
            .put("unavailableDecodeFallbackTriggerMs", fallback.triggerPositionMs)
    }

    private fun validatePlaylistShufflePreferences(): JSONObject {
        val storage = targetContext.getSharedPreferences("playlist-shuffle-validation", android.content.Context.MODE_PRIVATE)
        storage.edit().clear().putBoolean("smart_shuffle_enabled", true).commit()
        try {
            val preferences = SmartQueuePreferences(storage)
            check(preferences.loadPlaylistModes().isEmpty()) { "Legacy global flag must not enable playlists" }
            preferences.setPlaylistMode("playlist-A", PlaylistShuffleMode.Shuffle)
            preferences.setPlaylistMode("playlist-B", PlaylistShuffleMode.SmartShuffle)
            var restored = SmartQueuePreferences(storage).loadPlaylistModes()
            check(restored["playlist-A"] == PlaylistShuffleMode.Shuffle)
            check(restored["playlist-B"] == PlaylistShuffleMode.SmartShuffle)
            preferences.setPlaylistMode("playlist-A", PlaylistShuffleMode.SmartShuffle)
            preferences.setPlaylistMode("playlist-A", PlaylistShuffleMode.Off)
            restored = SmartQueuePreferences(storage).loadPlaylistModes()
            check(restored["playlist-A"] == null && restored["playlist-B"] == PlaylistShuffleMode.SmartShuffle)
            preferences.retainPlaylists(setOf("playlist-A"))
            check(SmartQueuePreferences(storage).loadPlaylistModes().isEmpty())
            return JSONObject().put("legacyFlagIgnored", true).put("independentPlaylistModes", true)
                .put("offRemovesOnlyItsOwnPreference", true).put("deletedPlaylistPreferenceRemoved", true)
        } finally { storage.edit().clear().commit() }
    }

    private suspend fun validateSmartRanking(): JSONObject {
        val otherBeat = writeWav("smart-90bpm-24s.wav", 24_000L, frequency = 100.0) { ms ->
            if (ms % 667L < 45L) 0.70 else 0.025
        }
        fun song(id: Long, file: File) = Song(
            id = id, title = file.nameWithoutExtension, artist = "Fixture", album = "Validation",
            durationMs = 24_000L, fileSizeBytes = file.length(), dateAddedSeconds = 0L,
            folderPath = outputDirectory.path, folderName = outputDirectory.name,
            uri = Uri.fromFile(file).toString(), artworkUri = null,
        )
        val analyzer = SmartSongAnalyzer(targetContext)
        val profiles = listOf(song(1L, fixtures.beatOutgoing), song(2L, otherBeat), song(3L, fixtures.beatIncoming))
            .associate { it.id to analyzer.analyze(it) }
        check(requireNotNull(profiles[1L]?.bpm) in 118f..122f)
        check(requireNotNull(profiles[2L]?.bpm) in 88f..92f)
        check(requireNotNull(profiles[3L]?.bpm) in 118f..122f)
        check(profiles.values.all { it.rms != null && it.rms!!.isFinite() && it.rms!! > 0f })
        val order = SmartQueuePlanner.order(1L, listOf(2L, 3L), profiles, randomized = false)
        check(order == listOf(3L, 2L)) { "Compatible actual PCM beat should precede 90 BPM: $order" }
        repeat(20) { seed ->
            check(SmartQueuePlanner.order(1L, listOf(2L, 3L), profiles, true, kotlin.random.Random(seed)).first() == 3L)
        }
        val missing = analyzer.analyze(song(4L, File(outputDirectory, "missing-smart.wav")))
        check(missing.bpm == null && missing.rms == null)
        check(SmartQueuePlanner.order(1L, listOf(2L, 3L), emptyMap(), false) == listOf(2L, 3L))
        return JSONObject().put("actualDecodedBpms", JSONArray(listOf(profiles[1L]?.bpm, profiles[2L]?.bpm, profiles[3L]?.bpm)))
            .put("orderedIds", JSONArray(order)).put("randomizedSeeds", 20)
            .put("unavailableAudioFallsBack", true)
    }

    private suspend fun validateSmartQueuePlayer(): JSONObject = withContext(Dispatchers.Main) {
        val player = ExoPlayer.Builder(targetContext).build()
        try {
            val songs = (1L..5L).map { id ->
                Song(id, "Smart fixture $id", "Fixture", "Validation", 24_000L, fixtures.normal.length(),
                    0L, outputDirectory.path, outputDirectory.name, Uri.fromFile(fixtures.normal).toString(), null)
            }
            player.setMediaItems(songs.map { MediaItem.Builder().setMediaId(it.id.toString()).setUri(it.uri).build() }, 1, 3_000L)
            player.prepare()
            withTimeout(5_000L) { while (player.playbackState != Player.STATE_READY) delay(20L) }
            val captured = SmartQueueSnapshot(player, songs, 2L)
            check(applySmartQueuePendingOrder(player, captured, listOf(5L, 3L, 4L)))
            delay(100L)
            val reordered = List(player.mediaItemCount) { player.getMediaItemAt(it).mediaId }
            check(reordered == listOf("1", "2", "5", "3", "4"))
            check(player.currentMediaItem?.mediaId == "2" && player.currentPosition == 3_000L && !player.playWhenReady)
            check(!applySmartQueuePendingOrder(player, captured, listOf(4L, 3L, 5L))) { "Stale snapshot must be rejected" }
            val liveSongs = reordered.map { id -> songs.single { it.id.toString() == id } }
            val live = SmartQueueSnapshot(player, liveSongs, 2L)
            check(!applySmartQueuePendingOrder(player, live, listOf(3L, 3L, 4L))) { "Duplicate reorder must be rejected" }
            player.play()
            withTimeout(5_000L) { while (!player.isPlaying) delay(20L) }
            val before = player.currentPosition
            check(applySmartQueuePendingOrder(player, live, listOf(4L, 5L, 3L)))
            delay(120L)
            check(player.currentMediaItem?.mediaId == "2" && player.playWhenReady && player.currentPosition >= before)
            return@withContext JSONObject().put("pausedPositionPreservedMs", 3_000L)
                .put("playedPrefixPreserved", true).put("playingContinued", true)
                .put("staleSnapshotRejected", true).put("duplicateRejected", true)
        } finally { player.release() }
    }

    private fun validateAudioProcessor(): JSONObject {
        fun processor(level: Float = 1f) = DjEffectsAudioProcessor().apply {
            setLevelGain(level)
            check(configure(AudioProcessor.AudioFormat(SAMPLE_RATE, 1, C.ENCODING_PCM_16BIT)) != AudioProcessor.AudioFormat.NOT_SET)
            flush()
        }
        val leveled = processor(0.55f)
        try {
            val before = processedToneGain(leveled, 1_000.0)
            check(before in 0.54..0.56) { "Per-track attenuation was not applied to real PCM: $before" }
            leveled.setTransition(AutoDjEffect.BassSwap, 1f, 0.65f, 120f, incoming = false)
            processedToneGain(leveled, 60.0)
            leveled.resetEffect()
            val afterReset = processedToneGain(leveled, 1_000.0)
            check(afterReset in 0.54..0.56) { "Effect reset discarded per-track attenuation: $afterReset" }
            leveled.flush()
            val afterFlush = processedToneGain(leveled, 1_000.0)
            check(afterFlush in 0.54..0.56) { "Audio pipeline flush discarded per-track attenuation: $afterFlush" }

            fun bassGain(frequency: Double, incoming: Boolean, progress: Float): Double {
                val bass = processor()
                return try {
                    bass.setTransition(AutoDjEffect.BassSwap, progress, 0.65f, 120f, incoming)
                    processedToneGain(bass, frequency)
                } finally { bass.reset() }
            }
            val incomingLow = bassGain(60.0, true, 0f)
            val incomingHigh = bassGain(3_000.0, true, 0f)
            val outgoingLow = bassGain(60.0, false, 1f)
            val outgoingHigh = bassGain(3_000.0, false, 1f)
            check(incomingLow < 0.65 && outgoingLow < 0.65) { "BassSwap did not attenuate low-frequency PCM" }
            check(incomingHigh > 0.90 && outgoingHigh > 0.90) { "BassSwap attenuated high frequencies excessively" }
            check(incomingLow / incomingHigh < 0.70 && outgoingLow / outgoingHigh < 0.70)
            return JSONObject().put("fixedLevelGain", 0.55).put("gainBeforeEffect", before)
                .put("gainAfterEffectReset", afterReset).put("gainAfterFlush", afterFlush)
                .put("incomingLowFrequencyGain", incomingLow).put("incomingHighFrequencyGain", incomingHigh)
                .put("outgoingLowFrequencyGain", outgoingLow).put("outgoingHighFrequencyGain", outgoingHigh)
                .put("measurement", "RMS ratio of actual processor PCM output, ignoring first 400 ms of control/filter settling")
        } finally { leveled.reset() }
    }

    private fun processedToneGain(processor: DjEffectsAudioProcessor, frequency: Double): Double {
        val frameCount = SAMPLE_RATE
        val input = ByteBuffer.allocateDirect(frameCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        var inputSquares = 0.0
        repeat(frameCount) { frame ->
            val sample = (sin(2.0 * PI * frequency * frame / SAMPLE_RATE) * 0.20 * Short.MAX_VALUE).toInt().toShort()
            input.putShort(sample)
            if (frame >= SAMPLE_RATE * 4 / 10) inputSquares += sample.toDouble() * sample
        }
        input.flip()
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.LITTLE_ENDIAN)
        check(output.remaining() == frameCount * 2) { "Processor changed the PCM frame count" }
        var outputSquares = 0.0
        repeat(frameCount) { frame ->
            val sample = output.short
            if (frame >= SAMPLE_RATE * 4 / 10) outputSquares += sample.toDouble() * sample
        }
        return sqrt(outputSquares / inputSquares)
    }

    private suspend fun validateMusicalPlans(): JSONObject {
        val analyzer = DjSilenceAnalyzer(targetContext)
        val current = requireNotNull(analyzer.tailProfile(Uri.fromFile(fixtures.normal), 24_000L))
        val smooth = requireNotNull(analyzer.headProfile(Uri.fromFile(fixtures.normal)))
        val loud = requireNotNull(analyzer.headProfile(Uri.fromFile(fixtures.loud)))
        val smooth8 = requireNotNull(ExpertDjPlanner.plan(24_000L, 8_000L, current, smooth))
        val loud8 = requireNotNull(ExpertDjPlanner.plan(24_000L, 8_000L, current, loud))
        val loud5 = requireNotNull(ExpertDjPlanner.plan(24_000L, 5_000L, current, loud))
        check(smooth8.mixDurationMs in 3_000L..8_000L && loud8.mixDurationMs in 3_000L..8_000L)
        check(loud5.mixDurationMs in 3_000L..5_000L)
        check(loud8.mixDurationMs < smooth8.mixDurationMs) { "A loud attack did not shorten the decoded-profile overlap" }
        val loudGain = ExpertDjPlanner.levelGain(loud)
        val smoothGain = ExpertDjPlanner.levelGain(smooth)
        check(loudGain in 0.4f..0.6f && smoothGain == 1f) { "Expected attenuation without boosting: loud=$loudGain, smooth=$smoothGain" }

        val beatAnalyzer = DjBeatAnalyzer(targetContext)
        val currentBeats = beatAnalyzer.analyze(Uri.fromFile(fixtures.beatOutgoing), 24_000L, tail = true)
        val nextBeats = beatAnalyzer.analyze(Uri.fromFile(fixtures.beatIncoming), 24_000L, tail = false)
        val beatTail = requireNotNull(analyzer.tailProfile(Uri.fromFile(fixtures.beatOutgoing), 24_000L))
        val beatHead = requireNotNull(analyzer.headProfile(Uri.fromFile(fixtures.beatIncoming)))
        val aligned = requireNotNull(ExpertDjPlanner.plan(24_000L, 8_000L, beatTail, beatHead, currentBeats, nextBeats))
        val confident = currentBeats != null && nextBeats != null && currentBeats.confidence >= 0.8f && nextBeats.confidence >= 0.8f
        if (confident) {
            val reliableCurrentBeats = requireNotNull(currentBeats)
            val reliableNextBeats = requireNotNull(nextBeats)
            check(reliableCurrentBeats.bpm in 118f..122f && reliableNextBeats.bpm in 118f..122f) {
                "The decoded regular 120 BPM fixture produced an unexpected grid"
            }
            check(aligned.beatAligned) { "Confident, covered, matching-tempo decoded fixtures were not aligned" }
            check(beatDistanceMs(aligned.triggerPositionMs, reliableCurrentBeats) <= 2.0)
            check(beatDistanceMs(aligned.incomingStartPositionMs, reliableNextBeats) <= 2.0)
        } else {
            check(!aligned.beatAligned) { "Uncertain decoding must not claim beat alignment" }
        }
        val fallback = requireNotNull(ExpertDjPlanner.plan(24_000L, 8_000L, beatTail, beatHead))
        check(!fallback.beatAligned && fallback.currentBpm == null && fallback.incomingBpm == null)
        return JSONObject().put("smoothMixMs", smooth8.mixDurationMs).put("loudMixMsAt8sMaximum", loud8.mixDurationMs)
            .put("loudMixMsAt5sMaximum", loud5.mixDurationMs).put("loudLevelGain", loudGain).put("smoothLevelGain", smoothGain)
            .put("currentDecodedBeats", beatJson(currentBeats)).put("incomingDecodedBeats", beatJson(nextBeats))
            .put("decodedGridsConfident", confident).put("beatAligned", aligned.beatAligned)
            .put("alignedPlan", planJson(aligned)).put("uncertainBeatFallbackVerified", true)
    }

    private fun beatDistanceMs(positionMs: Long, beats: DjBeatProfile): Double {
        val period = 60_000.0 / beats.bpm
        val phase = ((positionMs - beats.firstBeatMs) % period + period) % period
        return minOf(phase, period - phase)
    }

    private fun beatJson(beats: DjBeatProfile?): Any = beats?.let {
        JSONObject().put("bpm", it.bpm).put("confidence", it.confidence).put("firstBeatMs", it.firstBeatMs)
            .put("analyzedStartMs", it.analyzedStartMs).put("analyzedEndMs", it.analyzedEndMs)
    } ?: JSONObject.NULL

    private fun planJson(plan: ExpertDjPlan) = JSONObject()
        .put("triggerPositionMs", plan.triggerPositionMs).put("mixDurationMs", plan.mixDurationMs)
        .put("incomingStartPositionMs", plan.incomingStartPositionMs).put("incomingLevelGain", plan.incomingLevelGain)
        .put("audibleEndPositionMs", plan.audibleEndPositionMs).put("beatAligned", plan.beatAligned)
        .put("currentBpm", plan.currentBpm ?: JSONObject.NULL).put("incomingBpm", plan.incomingBpm ?: JSONObject.NULL)

    private suspend fun validateLongTail(): JSONObject = withHarness(
        "long_silent_tail_early_handoff", fixtures.longTail, fixtures.leadingSilence, 0L,
    ) { harness ->
        val prepared = harness.await("standby prepared before the old 30 s analysis threshold", 17_000L) {
            it.incomingMediaId == "incoming" && it.incomingState == Player.STATE_READY
        }
        check(prepared.outgoingPositionMs < 16_000L) { "Preload was late: $prepared" }
        check(!prepared.incomingPlayWhenReady) { "Preload must not start incoming playback" }
        val fading = harness.await("nonzero incoming gain", 12_000L) {
            it.incomingVolume > 0.015f && it.outgoingVolume < BASE_VOLUME - 0.005f
        }
        val firstFade = harness.samples.first { it.incomingVolume > 0.001f }
        check(firstFade.incomingPositionMs >= 2_000L) { "Incoming leading silence was not skipped" }
        check(fading.outgoingPositionMs < 21_000L)
        harness.await("handoff near the 20 s audible end", 9_000L) { harness.handoffs.isNotEmpty() }
        val handoff = harness.handoffs.single()
        val outgoingPosition = handoff.getLong("outgoingPositionMs")
        check(outgoingPosition in 18_700L..21_100L) { "Handoff missed audible end: $handoff" }
        check(outgoingPosition < 30_000L) { "Handoff waited for the previous 30 s analysis threshold" }
        check(handoff.getLong("incomingPositionMs") >= 4_600L)
        val settled = harness.sample()
        check(settled.activeMediaId == "incoming")
        check(!settled.outgoingPlayWhenReady && settled.outgoingItemCount == 0)
        check(abs(settled.incomingVolume - BASE_VOLUME) < 0.002f)
        check(settled.incomingTargetLevelGain == 1f) { "Incoming attenuation survived handoff" }
        check(harness.plans.last().incomingLevelGain < 0.6f) { "Fixture must exercise loud-track compensation" }
        val handoffLevel = settled.incomingTargetLevelGain
        delay(300L)
        val afterCleanup = harness.sample()
        check(afterCleanup.incomingTargetLevelGain == handoffLevel && afterCleanup.incomingEffect == AutoDjEffect.None.name) {
            "Transition cleanup must preserve normal playback level"
        }
        val beforeFade = harness.samples.filter { it.outgoingPlaying && it.incomingVolume == 0f && it.outgoingItemCount > 0 }
        check(beforeFade.size >= 15)
        check(beforeFade.all { it.outgoingTargetLevelGain == 1f && abs(it.outgoingVolume - BASE_VOLUME) < 0.002f }) {
            "Ordinary playback was attenuated before transition"
        }
        val gains = harness.checkGainTrace()
        JSONObject().put("preloadedAtOutgoingMs", prepared.outgoingPositionMs)
            .put("firstFadeOutgoingMs", firstFade.outgoingPositionMs)
            .put("firstFadeIncomingMs", firstFade.incomingPositionMs)
            .put("handoff", handoff).put("postTransitionLevelGain", handoffLevel).put("gainMetrics", gains)
    }

    private suspend fun validateNormalBlend(): JSONObject = withHarness(
        "normal_blend_with_quiet_then_loud_intro", fixtures.normal, fixtures.quietIntro, 15_000L,
    ) { harness ->
        harness.await("normal Expert fade", 8_000L) { it.incomingVolume > 0.015f }
        val firstFade = harness.samples.first { it.incomingVolume > 0.001f }
        val plannedCue = harness.plans.lastOrNull()?.incomingStartPositionMs ?: 0L
        check(plannedCue <= 500L && firstFade.incomingPositionMs < plannedCue + 250L) {
            "The quiet musical intro was skipped beyond the bounded confident beat cue"
        }
        harness.await("normal Expert handoff", 11_000L) { harness.handoffs.isNotEmpty() }
        val handoff = harness.handoffs.single()
        check(handoff.getLong("outgoingPositionMs") in 22_400L..24_100L) { "Normal song cut too early: $handoff" }
        val fadeSpan = handoff.getLong("outgoingPositionMs") - firstFade.outgoingPositionMs
        check(fadeSpan in 2_400L..8_400L) { "Normal blend must sustain a real fade, got $fadeSpan ms" }
        JSONObject().put("firstFadeIncomingMs", firstFade.incomingPositionMs)
            .put("observedFadeDurationMs", fadeSpan).put("handoff", handoff)
            .put("gainMetrics", harness.checkGainTrace())
    }

    private suspend fun validateBeatPlayback(): JSONObject = withHarness(
        "confident_beat_start_at_native_speed", fixtures.beatOutgoing, fixtures.beatIncoming, 15_000L,
    ) { harness ->
        harness.await("decoded-beat transition", 12_000L) { it.incomingVolume > 0.015f }
        val firstFade = harness.samples.first { it.incomingVolume > 0.001f }
        harness.await("decoded-beat handoff", 11_000L) { harness.handoffs.isNotEmpty() }
        val plan = requireNotNull(harness.plans.lastOrNull())
        val actualStart = requireNotNull(harness.incomingStarts.firstOrNull())
        if (plan.beatAligned) {
            val launchLatenessMs = actualStart.getLong("outgoingPositionMs") - plan.triggerPositionMs
            check(launchLatenessMs in -20L..100L) { "Claimed beat alignment missed the actual player launch by $launchLatenessMs ms" }
            check(abs(firstFade.outgoingPositionMs - plan.triggerPositionMs) <= 250L)
            check(abs(firstFade.incomingPositionMs - plan.incomingStartPositionMs) <= 250L)
        } else {
            check(plan.currentBpm == null && plan.incomingBpm == null) { "Fallback claimed an unverified beat grid" }
        }
        check(harness.samples.all { it.outgoingSpeed == 1f && it.incomingSpeed == 1f }) { "Expert changed native playback speed" }
        JSONObject().put("plan", planJson(plan)).put("firstFade", firstFade.json())
            .put("actualIncomingStart", actualStart)
            .put("nativeSpeedThroughout", true).put("handoff", harness.handoffs.single())
            .put("gainMetrics", harness.checkGainTrace())
    }

    private enum class PreviewAction { StopPlaying, Complete, StopPaused, SeekOverride, QueueOverride }

    private suspend fun validatePreview(action: PreviewAction): JSONObject = withHarness(
        "preview_${action.name.lowercase()}", fixtures.normal, fixtures.leadingSilence, 3_000L,
    ) { harness ->
        val states = JSONArray()
        var phase = ExpertDjPreviewPhase.Idle
        var closeCount = 0
        var session: ExpertDjPreviewSession? = null
        val startingQueue = withContext(Dispatchers.Main) {
            harness.outgoing.addMediaItem(MediaItem.Builder().setMediaId("third")
                .setUri(Uri.fromFile(fixtures.quietIntro)).build())
            if (action == PreviewAction.StopPaused) harness.outgoing.pause()
            List(harness.outgoing.mediaItemCount, harness.outgoing::getMediaItemAt)
        }
        delay(50L)
        val originalPosition = withContext(Dispatchers.Main) { harness.outgoing.currentPosition }
        val shouldResume = action != PreviewAction.StopPaused
        val started = SystemClock.elapsedRealtime()
        suspend fun awaitPhase(description: String, timeoutMs: Long, predicate: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + timeoutMs
            while (true) {
                val done = withContext(Dispatchers.Main) {
                    check(phase != ExpertDjPreviewPhase.Error) { "Preview failed: $states" }
                    predicate()
                }
                harness.sample()
                if (done) return
                check(SystemClock.elapsedRealtime() < deadline) { "Timed out waiting for $description; phase=$phase" }
                delay(30L)
            }
        }
        try {
            withContext(Dispatchers.Main) {
                session = ExpertDjPreviewSession(targetContext, harness.decks, harness.coordinator, 8,
                    publish = { state ->
                        phase = state.phase
                        states.put(JSONObject().put("wallMs", SystemClock.elapsedRealtime() - started)
                            .put("phase", state.phase.name).put("progress", state.progress ?: JSONObject.NULL)
                            .put("currentTitle", state.currentTitle).put("nextTitle", state.nextTitle)
                            .put("message", state.message ?: JSONObject.NULL))
                    }, onClosed = { closeCount++ })
                session!!.start()
            }
            awaitPhase("isolated preview playback", 20_000L) { phase == ExpertDjPreviewPhase.Playing }
            val pausedOriginal = harness.sample()
            check(!pausedOriginal.outgoingPlayWhenReady && !pausedOriginal.outgoingPlaying)
            check(abs(pausedOriginal.outgoingPositionMs - originalPosition) < 250L)
            check(harness.handoffs.isEmpty()) { "Preview replaced the original session deck" }
            withContext(Dispatchers.Main) {
                check(harness.outgoing.mediaItemCount == startingQueue.size)
                check(startingQueue.indices.all { harness.outgoing.getMediaItemAt(it) == startingQueue[it] })
            }
            if (action == PreviewAction.Complete) {
                awaitPhase("preview natural completion", 32_000L) { phase == ExpertDjPreviewPhase.Finished && closeCount == 1 }
            } else {
                withContext(Dispatchers.Main) {
                    when (action) {
                        PreviewAction.StopPlaying, PreviewAction.StopPaused -> session!!.stop(true)
                        PreviewAction.SeekOverride -> harness.outgoing.seekTo(9_000L)
                        PreviewAction.QueueOverride -> harness.outgoing.removeMediaItem(1)
                        PreviewAction.Complete -> Unit
                    }
                }
                awaitPhase("preview close", 2_000L) { closeCount == 1 }
            }
            delay(90L)
            val restored = harness.sample()
            check(restored.activeMediaId == "outgoing" && harness.handoffs.isEmpty())
            when (action) {
                PreviewAction.SeekOverride -> {
                    check(!restored.outgoingPlayWhenReady && restored.outgoingPositionMs in 8_950L..9_100L)
                    withContext(Dispatchers.Main) { session!!.stop(true) }
                    delay(60L)
                    check(!harness.sample().outgoingPlayWhenReady) { "Closed override resumed on a later restore request" }
                }
                PreviewAction.QueueOverride -> {
                    check(!restored.outgoingPlayWhenReady && restored.outgoingItemCount == startingQueue.size - 1)
                    withContext(Dispatchers.Main) {
                        check(harness.outgoing.getMediaItemAt(1).mediaId == "third")
                        session!!.stop(true)
                    }
                    delay(60L)
                    check(!harness.sample().outgoingPlayWhenReady) { "Queue override was resumed after cancellation" }
                }
                else -> {
                    check(restored.outgoingPlayWhenReady == shouldResume)
                    check(abs(restored.outgoingPositionMs - originalPosition) < 500L) { "Original preview position was not restored" }
                    withContext(Dispatchers.Main) {
                        check(harness.outgoing.mediaItemCount == startingQueue.size)
                        check(startingQueue.indices.all { harness.outgoing.getMediaItemAt(it) == startingQueue[it] })
                    }
                }
            }
            check(closeCount == 1)
            val result = JSONObject().put("action", action.name).put("initialOriginalPositionMs", originalPosition)
                .put("originalPausedDuringPreview", true).put("originalDeckNeverReplaced", true)
                .put("closeCount", closeCount).put("stateEvents", states).put("finalSnapshot", restored.json())
            File(outputDirectory, "preview_${action.name.lowercase()}_states.json").writeText(result.toString(2))
            result
        } finally {
            withContext(NonCancellable + Dispatchers.Main) { session?.stop(false) }
        }
    }

    private enum class Interruption { Pause, Seek, RemoveTarget }

    private suspend fun validateInterruption(interruption: Interruption): JSONObject = withHarness(
        interruption.name.lowercase(), fixtures.shortTail, fixtures.leadingSilence, 14_000L,
    ) { harness ->
        harness.await("fade before ${interruption.name}", 7_000L) {
            it.incomingVolume > 0.04f && it.outgoingVolume < BASE_VOLUME - 0.01f
        }
        val actionAt = SystemClock.elapsedRealtime()
        withContext(Dispatchers.Main) {
            when (interruption) {
                Interruption.Pause -> harness.outgoing.pause()
                Interruption.Seek -> harness.outgoing.seekTo(2_000L)
                Interruption.RemoveTarget -> harness.outgoing.removeMediaItems(1, harness.outgoing.mediaItemCount)
            }
        }
        harness.mark("${interruption.name.lowercase()}_requested")
        // Allow posted Player.Listener events to run, then inspect restoration before a full
        // monitor interval. Cancellation belongs to the actual pause/seek/playlist callbacks.
        delay(45L)
        val restored = harness.sample()
        val cancellationMs = SystemClock.elapsedRealtime() - actionAt
        check(abs(restored.outgoingVolume - BASE_VOLUME) < 0.002f) { "Active gain not restored: $restored" }
        check(!restored.incomingPlayWhenReady && restored.incomingItemCount == 0 && restored.incomingVolume == 0f) {
            "Standby survived ${interruption.name}: $restored"
        }
        check(restored.outgoingEffect == AutoDjEffect.None.name && restored.incomingEffect == AutoDjEffect.None.name) {
            "Temporary BassSwap controls survived ${interruption.name}"
        }
        check(restored.outgoingTargetLevelGain == 1f && restored.incomingTargetLevelGain == 1f) {
            "Level attenuation survived cancellation"
        }
        check(cancellationMs <= 250L) { "Cancellation took $cancellationMs ms" }
        repeat(24) { delay(30L); harness.sample() }
        val held = harness.sample()
        check(harness.handoffs.isEmpty()) { "Stale transition handed off after ${interruption.name}" }
        check(held.activeMediaId == "outgoing" && abs(held.outgoingVolume - BASE_VOLUME) < 0.002f)
        when (interruption) {
            Interruption.Pause -> check(!held.outgoingPlayWhenReady && !held.incomingPlayWhenReady)
            Interruption.Seek -> check(held.outgoingPositionMs in 1_900L..4_000L)
            Interruption.RemoveTarget -> check(held.outgoingItemCount == 1 && held.incomingItemCount == 0)
        }
        JSONObject().put("cancellationObservedMs", cancellationMs)
            .put("restoredVolume", restored.outgoingVolume).put("finalSnapshot", held.json())
    }

    private suspend fun withHarness(
        name: String, outgoing: File, incoming: File, startPositionMs: Long,
        block: suspend (PlayerHarness) -> JSONObject,
    ): JSONObject {
        val harness = withContext(Dispatchers.Main) { PlayerHarness(name, outgoing, incoming, startPositionMs) }
        try {
            harness.await("outgoing ready", 7_000L) { it.outgoingState == Player.STATE_READY }
            withContext(Dispatchers.Main) { harness.outgoing.play(); harness.coordinator.startMonitor() }
            harness.await("outgoing playing", 5_000L) { it.outgoingPlaying }
            return block(harness).put("trace", "$name.json")
        } finally {
            withContext(NonCancellable) {
                try {
                    harness.writeTrace()
                } finally {
                    withContext(Dispatchers.Main) {
                        harness.coordinator.stopMonitor()
                        harness.coordinator.shutdownPlayers()
                        harness.decks.release()
                        harness.coordinator.release()
                    }
                }
            }
        }
    }

    private inner class PlayerHarness(
        val name: String,
        private val outgoingFile: File,
        private val incomingFile: File,
        private val initialPositionMs: Long,
    ) {
        val samples = mutableListOf<Snapshot>()
        val handoffs = mutableListOf<JSONObject>()
        val incomingStarts = mutableListOf<JSONObject>()
        val plans = mutableListOf<ExpertDjPlan>()
        private val events = JSONArray()
        private val errors = mutableListOf<String>()
        private val startedMs = SystemClock.elapsedRealtime()
        val coordinator = DjPlaybackCoordinator(
            targetContext,
            DjPlaybackConfig(true, DjMixMode.Expert, 8, PlaybackMode.Ordered),
            resetPreviewState = {}, resetAutoDjStatus = {},
            onExpertPlan = { plans += it },
        )
        val decks = DjDeckController(targetContext, attachSessionPlayer = { incoming ->
            handoffs += JSONObject().put("wallMs", SystemClock.elapsedRealtime() - startedMs)
                .put("outgoingPositionMs", outgoing.currentPosition)
                .put("incomingPositionMs", incoming.currentPosition)
                .put("incomingMediaId", incoming.currentMediaItem?.mediaId)
                .put("outgoingVolume", outgoing.volume).put("incomingVolume", incoming.volume)
        }, listener = { player ->
            player.addListener(object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (playWhenReady && player.currentMediaItem?.mediaId == "incoming") {
                        incomingStarts += JSONObject().put("wallMs", SystemClock.elapsedRealtime() - startedMs)
                            .put("outgoingPositionMs", outgoing.currentPosition)
                            .put("incomingPositionMs", player.currentPosition)
                            .put("incomingSpeed", player.playbackParameters.speed)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    errors += "${player.currentMediaItem?.mediaId}: ${error.errorCodeName}: ${error.message}"
                }
            })
            coordinator.playerListener(player)
        })
        val outgoing: ExoPlayer = decks.activePlayer
        private val incoming: ExoPlayer = decks.standbyPlayer

        init {
            coordinator.attachDecks(decks)
            outgoing.volume = BASE_VOLUME
            outgoing.setMediaItems(listOf(
                MediaItem.Builder().setMediaId("outgoing").setUri(Uri.fromFile(outgoingFile)).build(),
                MediaItem.Builder().setMediaId("incoming").setUri(Uri.fromFile(incomingFile)).build(),
            ), 0, initialPositionMs)
            outgoing.prepare()
        }

        suspend fun sample(): Snapshot = withContext(Dispatchers.Main) {
            check(errors.isEmpty()) { "Real player error: ${errors.joinToString()}" }
            val outgoingControls = observeProcessorControls(decks, outgoing)
            val incomingControls = observeProcessorControls(decks, incoming)
            Snapshot(
                SystemClock.elapsedRealtime() - startedMs,
                outgoing.currentPosition, incoming.currentPosition,
                outgoing.volume, incoming.volume,
                decks.activePlayer.currentMediaItem?.mediaId,
                incoming.currentMediaItem?.mediaId,
                outgoing.playbackState, incoming.playbackState,
                outgoing.isPlaying, incoming.isPlaying,
                outgoing.playWhenReady, incoming.playWhenReady,
                outgoing.mediaItemCount, incoming.mediaItemCount,
                outgoing.playbackParameters.speed, incoming.playbackParameters.speed,
                outgoingControls.levelGain, incomingControls.levelGain,
                outgoingControls.effect, incomingControls.effect,
                outgoingControls.progress, incomingControls.progress,
            ).also(samples::add)
        }

        suspend fun await(description: String, timeoutMs: Long, predicate: (Snapshot) -> Boolean): Snapshot {
            val deadline = SystemClock.elapsedRealtime() + timeoutMs
            while (true) {
                val current = sample()
                if (predicate(current)) return current
                check(SystemClock.elapsedRealtime() < deadline) { "Timed out waiting for $description; last=$current" }
                delay(30L)
            }
        }

        fun mark(event: String) {
            events.put(JSONObject().put("wallMs", SystemClock.elapsedRealtime() - startedMs).put("event", event))
        }

        fun checkGainTrace(): JSONObject {
            val fade = samples.filter { it.incomingVolume > 0f && it.outgoingItemCount > 0 }
            check(fade.size >= 15) { "Insufficient real player gain observations: ${fade.size}" }
            check(fade.count { it.outgoingEffect == AutoDjEffect.BassSwap.name && it.incomingEffect == AutoDjEffect.BassSwap.name } >= 5) {
                "Live Expert transition did not apply BassSwap to both real processors"
            }
            samples.forEach {
                check(it.outgoingVolume.isFinite() && it.incomingVolume.isFinite())
                check(it.outgoingTargetLevelGain == 1f && it.incomingTargetLevelGain == 1f) {
                    "Persistent DSP level must remain unity; compensation belongs to the transition envelope"
                }
                check(it.outgoingVolume in 0f..(BASE_VOLUME + 0.002f) && it.incomingVolume in 0f..(BASE_VOLUME + 0.002f))
            }
            fade.zipWithNext().forEach { (previous, next) ->
                check(next.outgoingVolume <= previous.outgoingVolume + 0.003f) { "Outgoing gain rose during fade" }
                check(next.incomingVolume >= previous.incomingVolume - 0.003f) { "Incoming gain fell during fade" }
            }
            val peakGainPower = fade.maxOf {
                (it.outgoingVolume * it.outgoingVolume + it.incomingVolume * it.incomingVolume) / (BASE_VOLUME * BASE_VOLUME)
            }
            check(peakGainPower <= 1.08f) { "Fade exceeded equal-power budget: $peakGainPower" }
            return JSONObject().put("sampleCount", fade.size).put("maxNormalizedGainPower", peakGainPower)
                .put("outgoingMonotonic", true).put("incomingMonotonic", true)
                .put("bassSwapAppliedToBothProcessors", true)
        }

        fun writeTrace() {
            File(outputDirectory, "$name.json").writeText(JSONObject()
                .put("scenario", name).put("observation", "Actual player positions and volume controls; not captured audio")
                .put("outgoingFixture", outgoingFile.name).put("incomingFixture", incomingFile.name)
                .put("initialOutgoingPositionMs", initialPositionMs).put("baseVolume", BASE_VOLUME)
                .put("samples", JSONArray(samples.map(Snapshot::json)))
                .put("plans", JSONArray(plans.map(::planJson)))
                .put("events", events).put("handoffs", JSONArray(handoffs)).put("errors", JSONArray(errors))
                .put("incomingStarts", JSONArray(incomingStarts))
                .toString(2))
        }
    }

    private data class Snapshot(
        val wallMs: Long,
        val outgoingPositionMs: Long, val incomingPositionMs: Long,
        val outgoingVolume: Float, val incomingVolume: Float,
        val activeMediaId: String?, val incomingMediaId: String?,
        val outgoingState: Int, val incomingState: Int,
        val outgoingPlaying: Boolean, val incomingPlaying: Boolean,
        val outgoingPlayWhenReady: Boolean, val incomingPlayWhenReady: Boolean,
        val outgoingItemCount: Int, val incomingItemCount: Int,
        val outgoingSpeed: Float, val incomingSpeed: Float,
        val outgoingTargetLevelGain: Float, val incomingTargetLevelGain: Float,
        val outgoingEffect: String, val incomingEffect: String,
        val outgoingEffectProgress: Float, val incomingEffectProgress: Float,
    ) {
        fun json() = JSONObject()
            .put("wallMs", wallMs).put("outgoingPositionMs", outgoingPositionMs).put("incomingPositionMs", incomingPositionMs)
            .put("outgoingVolume", outgoingVolume).put("incomingVolume", incomingVolume)
            .put("activeMediaId", activeMediaId ?: JSONObject.NULL).put("incomingMediaId", incomingMediaId ?: JSONObject.NULL)
            .put("outgoingState", outgoingState).put("incomingState", incomingState)
            .put("outgoingPlaying", outgoingPlaying).put("incomingPlaying", incomingPlaying)
            .put("outgoingPlayWhenReady", outgoingPlayWhenReady).put("incomingPlayWhenReady", incomingPlayWhenReady)
            .put("outgoingItemCount", outgoingItemCount).put("incomingItemCount", incomingItemCount)
            .put("outgoingSpeed", outgoingSpeed).put("incomingSpeed", incomingSpeed)
            .put("outgoingTargetLevelGain", outgoingTargetLevelGain).put("incomingTargetLevelGain", incomingTargetLevelGain)
            .put("outgoingEffect", outgoingEffect).put("incomingEffect", incomingEffect)
            .put("outgoingEffectProgress", outgoingEffectProgress).put("incomingEffectProgress", incomingEffectProgress)
    }

    private data class ProcessorControlsObservation(val levelGain: Float, val effect: String, val progress: Float)

    /** Read immutable atomic targets only; never drain or mutate a live Media3 audio processor. */
    private fun observeProcessorControls(decks: DjDeckController, player: ExoPlayer): ProcessorControlsObservation {
        val processors = DjDeckController::class.java.getDeclaredField("effects").apply { isAccessible = true }
            .get(decks) as Map<*, *>
        val processor = requireNotNull(processors[player])
        val atomic = DjEffectsAudioProcessor::class.java.getDeclaredField("controls").apply { isAccessible = true }
            .get(processor) as AtomicReference<*>
        val controls = requireNotNull(atomic.get())
        fun field(name: String): Any = controls.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(controls)!!
        return ProcessorControlsObservation(field("levelGain") as Float, (field("effect") as AutoDjEffect).name, field("progress") as Float)
    }

    private data class Fixtures(
        val longTail: File, val shortTail: File, val leadingSilence: File, val normal: File,
        val quietIntro: File, val silence: File, val truncated: File, val empty: File,
        val loud: File, val beatOutgoing: File, val beatIncoming: File,
    ) {
        fun json() = JSONObject().put("sampleRate", SAMPLE_RATE).put("channels", 1).put("encoding", "PCM16LE")
            .put("longTail", longTail.name).put("longTailAudibleEndMs", 20_000L).put("longTailDurationMs", 60_000L)
            .put("shortTail", shortTail.name).put("shortTailAudibleEndMs", 18_000L)
            .put("leadingSilence", leadingSilence.name).put("leadingSilenceEndMs", 3_000L)
            .put("normal", normal.name).put("quietIntro", quietIntro.name)
            .put("quietIntroEndMs", 1_000L).put("quietIntroAmplitude", 0.014).put("loudBodyAmplitude", 0.55)
            .put("silence", silence.name).put("truncated", truncated.name).put("empty", empty.name)
            .put("loud", loud.name).put("beatOutgoing", beatOutgoing.name).put("beatIncoming", beatIncoming.name)
            .put("beatBpm", 120).put("incomingBeatPhaseMs", 125)
    }

    private fun createFixtures(): Fixtures {
        val longTail = writeWav("long-tail-60s.wav", 60_000L, frequency = 220.0) { ms -> if (ms < 20_000L) 0.18 else 0.0 }
        val shortTail = writeWav("short-tail-24s.wav", 24_000L, frequency = 220.0) { ms -> if (ms < 18_000L) 0.18 else 0.0 }
        val incoming = writeWav("leading-silence-loud-30s.wav", 30_000L, frequency = 330.0) { ms -> if (ms < 3_000L) 0.0 else 0.55 }
        val normal = writeWav("normal-24s.wav", 24_000L, frequency = 220.0) { 0.18 }
        // Only 1/15 of the analyzed head is quiet music (RMS about 0.006, peak below 0.014).
        // A percentile threshold derived from the loud body must preserve this musical intro.
        val quiet = writeWav("quiet-intro-loud-body-30s.wav", 30_000L, frequency = 330.0) { ms -> if (ms < 1_000L) 0.014 else 0.55 }
        val silence = writeWav("measured-silence-12s.wav", 12_000L) { 0.0 }
        val truncated = writeWav("truncated-declared-60s-actual-20s.wav", 20_000L, declaredDurationMs = 60_000L) { 0.18 }
        val empty = writeWav("empty-declared-60s.wav", 0L, declaredDurationMs = 60_000L) { 0.0 }
        val loud = writeWav("loud-body-30s.wav", 30_000L, frequency = 330.0) { 0.55 }
        val beatOutgoing = writeWav("beat-120bpm-outgoing-24s.wav", 24_000L, frequency = 80.0) { ms ->
            when { ms >= 23_500L -> 0.0; ms % 500L < 45L -> 0.70; else -> 0.025 }
        }
        val beatIncoming = writeWav("beat-120bpm-incoming-phase125-24s.wav", 24_000L, frequency = 100.0) { ms ->
            if ((ms + 375L) % 500L < 45L) 0.70 else 0.025
        }
        return Fixtures(longTail, shortTail, incoming, normal, quiet, silence, truncated, empty, loud, beatOutgoing, beatIncoming)
    }

    private fun writeWav(
        name: String, actualDurationMs: Long, declaredDurationMs: Long = actualDurationMs,
        frequency: Double = 220.0, amplitude: (Long) -> Double,
    ): File {
        val file = File(outputDirectory, name)
        val declaredBytes = (declaredDurationMs * SAMPLE_RATE / 1_000L * 2L).toInt()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + declaredBytes)
            .put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16).putShort(1).putShort(1)
            .putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2).putShort(2).putShort(16)
            .put("data".toByteArray(Charsets.US_ASCII)).putInt(declaredBytes).array()
        BufferedOutputStream(FileOutputStream(file)).use { output ->
            output.write(header)
            val frames = actualDurationMs * SAMPLE_RATE / 1_000L
            val buffer = ByteBuffer.allocate(8_192).order(ByteOrder.LITTLE_ENDIAN)
            for (frame in 0L until frames) {
                val seconds = frame.toDouble() / SAMPLE_RATE
                val tone = 0.68 * sin(2.0 * PI * frequency * seconds) +
                    0.22 * sin(2.0 * PI * frequency * 1.5 * seconds) +
                    0.10 * sin(2.0 * PI * frequency * 2.0 * seconds)
                val pulse = 0.88 + 0.12 * cos(2.0 * PI * 2.0 * seconds)
                val value = (tone * pulse * amplitude(frame * 1_000L / SAMPLE_RATE) * Short.MAX_VALUE)
                    .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                buffer.putShort(value)
                if (!buffer.hasRemaining()) { output.write(buffer.array()); buffer.clear() }
            }
            if (buffer.position() > 0) output.write(buffer.array(), 0, buffer.position())
        }
        return file
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val BASE_VOLUME = 0.82f
    }
}
