package com.softmusic.app.desktop.player

import com.softmusic.app.data.Song
import java.io.BufferedWriter
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class DesktopAudioPlayer {
    private val stateLock = Any()
    private var active: VlcPlayback? = null
    private var standby: VlcPlayback? = null
    private val fadingOut = mutableSetOf<VlcPlayback>()
    private var pendingPlaybackEnded: Boolean = false
    private var djTransitionActive: Boolean = false
    @Volatile private var lastErrorMessage: String? = null

    fun play(song: Song, volumePercent: Int): Result<Unit> = runCatching {
        synchronized(stateLock) {
            lastErrorMessage = null
            stopAllLocked()
            active = startPlaybackLocked(
                song = song,
                volumePercent = volumePercent.coerceIn(MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT),
                startPaused = false,
            )
            pendingPlaybackEnded = false
            djTransitionActive = false
        }
    }.mapError()

    fun prepareDjTransition(song: Song): Result<Unit> = runCatching {
        synchronized(stateLock) {
            lastErrorMessage = null
            val playback = active ?: return@runCatching
            if (!playback.process.isAlive || djTransitionActive) return@runCatching
            if (standby?.songId == song.id && standby?.process?.isAlive == true) {
                standby?.let { setInstanceVolume(it, 0, scheduleSync = false) }
                return@runCatching
            }
            stopStandbyLocked()
            standby = startPlaybackLocked(
                song = song,
                volumePercent = 0,
                startPaused = true,
            )
        }
    }.mapError()

    fun isDjTransitionActive(): Boolean = synchronized(stateLock) { djTransitionActive }

    fun clearPreparedDjTransition(): Result<Unit> = runCatching {
        synchronized(stateLock) {
            if (!djTransitionActive) stopStandbyLocked()
        }
    }.mapError()

    fun startDjTransition(
        song: Song,
        volumePercent: Int,
        mixDurationMs: Long,
    ): Result<Unit> = runCatching {
        val safeVolume = volumePercent.coerceIn(MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT)
        val safeMixDurationMs = mixDurationMs.coerceIn(MIN_DJ_FADE_MS, MAX_DJ_MIX_SECONDS * 1_000L)
        val outgoing: VlcPlayback?
        val incoming: VlcPlayback

        synchronized(stateLock) {
            if (djTransitionActive) return@runCatching
            lastErrorMessage = null
            outgoing = active
            incoming = if (standby?.songId == song.id && standby?.process?.isAlive == true) {
                standby ?: error("Standby no disponible")
            } else {
                stopStandbyLocked()
                startPlaybackLocked(song = song, volumePercent = 0, startPaused = true)
            }
            active = incoming
            standby = null
            djTransitionActive = true
            pendingPlaybackEnded = false
            outgoing?.let(fadingOut::add)
            resumeInstanceLocked(incoming)
        }

        if (outgoing == null) {
            setInstanceVolume(incoming, safeVolume)
            synchronized(stateLock) { djTransitionActive = false }
        } else {
            startCrossfade(outgoing = outgoing, incoming = incoming, targetVolumePercent = safeVolume, durationMs = safeMixDurationMs)
        }
    }.mapError()

    fun setVolume(volumePercent: Int): Result<Unit> = runCatching {
        val safeVolume = volumePercent.coerceIn(MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT)
        synchronized(stateLock) {
            active?.let { playback ->
                setInstanceVolume(playback, safeVolume)
            }
        }
        Unit
    }.mapError()

    fun resume(): Result<Unit> = runCatching {
        synchronized(stateLock) {
            lastErrorMessage = null
            val playback = active ?: throw IllegalStateException("La reproducción ya terminó; vuelve a seleccionar la canción")
            if (!playback.process.isAlive) {
                throw IllegalStateException("La reproducción ya terminó; vuelve a seleccionar la canción")
            }
            sendCommand(playback, "pause")
            playback.positionStartedAtMs = System.currentTimeMillis()
            playback.playing = true
        }
    }.mapError()

    fun pause(): Result<Unit> = runCatching {
        synchronized(stateLock) {
            val playback = active ?: return@runCatching
            if (playback.process.isAlive && playback.playing) {
                playback.positionBaseMs = playback.currentPositionMs()
                sendCommand(playback, "pause")
                playback.playing = false
            }
        }
    }.mapError()

    fun stop(): Result<Unit> = runCatching {
        synchronized(stateLock) {
            stopAllLocked()
            pendingPlaybackEnded = false
            djTransitionActive = false
        }
    }.mapError()

    fun seekTo(positionMs: Long): Result<Unit> = runCatching {
        synchronized(stateLock) {
            val playback = active ?: return@runCatching
            val safePositionMs = positionMs.coerceIn(0L, playback.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE)
            playback.positionBaseMs = safePositionMs
            if (playback.playing) playback.positionStartedAtMs = System.currentTimeMillis()
            sendCommand(playback, "seek ${safePositionMs / 1_000L}")
        }
    }.mapError()

    fun snapshot(): DesktopPlaybackSnapshot = synchronized(stateLock) {
        val playback = active
        if (playback == null) {
            val ended = pendingPlaybackEnded
            pendingPlaybackEnded = false
            return@synchronized DesktopPlaybackSnapshot(playbackEnded = ended, errorMessage = lastErrorMessage)
        }

        if (!playback.process.isAlive) {
            pendingPlaybackEnded = playback.playing
            playback.positionBaseMs = playback.durationMs.takeIf { it > 0L } ?: playback.positionBaseMs
            stopInstanceLocked(playback)
            active = null
            djTransitionActive = false
        }

        if (standby?.process?.isAlive == false) stopStandbyLocked()

        val ended = pendingPlaybackEnded
        pendingPlaybackEnded = false
        val current = active
        DesktopPlaybackSnapshot(
            isPlaying = current?.playing == true && current.process.isAlive,
            positionMs = current?.currentPositionMs() ?: 0L,
            durationMs = current?.durationMs ?: 0L,
            playbackEnded = ended,
            errorMessage = lastErrorMessage,
        )
    }

    fun release() {
        synchronized(stateLock) {
            stopAllLocked()
        }
    }

    private fun startPlaybackLocked(song: Song, volumePercent: Int, startPaused: Boolean): VlcPlayback {
        val playableLocation = song.uri.toPlayableLocation()
        if (!Files.isReadable(playableLocation.path)) {
            throw IllegalStateException("No se puede leer el archivo: ${playableLocation.path}")
        }

        val command = mutableListOf(
            resolveVlcExecutable(),
            "--intf",
            "rc",
            "--rc-fake-tty",
            "--no-video",
            "--role=music",
            "--play-and-exit",
        ).apply {
            if (startPaused) add("--start-paused")
            add(playableLocation.path.toString())
        }

        val process = ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()

        val playback = VlcPlayback(
            songId = song.id,
            process = process,
            commandWriter = process.outputStream.bufferedWriter(),
            durationMs = song.durationMs.coerceAtLeast(0L),
            positionBaseMs = 0L,
            positionStartedAtMs = System.currentTimeMillis(),
            playing = !startPaused,
        )
        setInstanceVolume(
            playback = playback,
            volumePercent = volumePercent.coerceIn(MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT),
            scheduleSync = !startPaused,
        )
        return playback
    }

    private fun startCrossfade(
        outgoing: VlcPlayback,
        incoming: VlcPlayback,
        targetVolumePercent: Int,
        durationMs: Long,
    ) {
        Thread {
            val steps = DJ_FADE_STEPS
            val stepDelayMs = (durationMs / steps).coerceAtLeast(50L)
            repeat(steps) { step ->
                Thread.sleep(stepDelayMs)
                val progress = (step + 1).toFloat() / steps.toFloat()
                val incomingVolume = (targetVolumePercent * equalPowerIn(progress)).roundToInt().coerceIn(0, targetVolumePercent)
                val outgoingVolume = (targetVolumePercent * equalPowerOut(progress)).roundToInt().coerceIn(0, targetVolumePercent)

                synchronized(stateLock) {
                    if (active !== incoming) {
                        stopInstanceLocked(incoming)
                        stopInstanceLocked(outgoing)
                        fadingOut.remove(outgoing)
                        djTransitionActive = false
                        return@Thread
                    }
                    if (!incoming.process.isAlive) {
                        stopInstanceLocked(outgoing)
                        fadingOut.remove(outgoing)
                        active = null
                        djTransitionActive = false
                        return@Thread
                    }
                    setInstanceVolume(incoming, incomingVolume, scheduleSync = false)
                    setInstanceVolume(outgoing, outgoingVolume, scheduleSync = false)
                    syncSystemAudioStream(incoming.process.pid(), incomingVolume)
                    syncSystemAudioStream(outgoing.process.pid(), outgoingVolume)
                }
            }

            synchronized(stateLock) {
                if (active === incoming) {
                    setInstanceVolume(incoming, targetVolumePercent)
                } else {
                    stopInstanceLocked(incoming)
                }
                stopInstanceLocked(outgoing)
                fadingOut.remove(outgoing)
                djTransitionActive = false
            }
        }.apply {
            name = "SoftMusic-dj-crossfade-${incoming.process.pid()}"
            isDaemon = true
        }.start()
    }

    private fun resumeInstanceLocked(playback: VlcPlayback) {
        if (!playback.process.isAlive || playback.playing) return
        playback.positionStartedAtMs = System.currentTimeMillis()
        playback.playing = true
        sendCommand(playback, "pause")
    }

    private fun setInstanceVolume(
        playback: VlcPlayback,
        volumePercent: Int,
        scheduleSync: Boolean = true,
    ) {
        val safeVolume = volumePercent.coerceIn(MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT)
        playback.volumePercent = safeVolume
        sendCommand(playback, "volume ${safeVolume.toVlcVolume()}")
        if (scheduleSync) scheduleSystemAudioSync(playback, safeVolume)
    }

    private fun sendCommand(playback: VlcPlayback, command: String) {
        if (!playback.process.isAlive) return
        runCatching {
            playback.commandWriter.apply {
                write(command)
                newLine()
                flush()
            }
        }.onFailure { throwable ->
            lastErrorMessage = throwable.message ?: "No se pudo enviar el comando a VLC"
        }
    }

    private fun scheduleSystemAudioSync(playback: VlcPlayback, volumePercent: Int) {
        val pid = playback.process.pid()
        Thread {
            var foundStream = false
            repeat(SYSTEM_AUDIO_SYNC_ATTEMPTS) {
                if (!playback.process.isAlive) return@Thread
                if (syncSystemAudioStream(pid, volumePercent)) {
                    foundStream = true
                }
                Thread.sleep(SYSTEM_AUDIO_SYNC_INTERVAL_MS)
            }
            if (!foundStream && playback.process.isAlive) {
                lastErrorMessage = "No pude encontrar el stream de VLC para desmutearlo en PipeWire"
            }
        }.apply {
            name = "SoftMusic-audio-sync-$pid"
            isDaemon = true
        }.start()
    }

    private fun syncSystemAudioStream(pid: Long, volumePercent: Int): Boolean {
        val sinkInputId = findPulseSinkInputId(pid) ?: return false
        runCommand("pactl", "set-sink-input-mute", sinkInputId, "0")
        runCommand("pactl", "set-sink-input-volume", sinkInputId, "${volumePercent.coerceIn(MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT)}%")
        return true
    }

    private fun findPulseSinkInputId(pid: Long): String? {
        val output = runCommand("pactl", "list", "sink-inputs").getOrNull() ?: return null
        var currentSinkInputId: String? = null
        output.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("Sink Input #")) {
                currentSinkInputId = trimmed.substringAfter('#').trim()
            }
            if (trimmed == "application.process.id = \"$pid\"") {
                return currentSinkInputId
            }
        }
        return null
    }

    private fun runCommand(vararg command: String): Result<String> = runCatching {
        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw IllegalStateException(output.ifBlank { "Comando falló: ${command.joinToString(" ")}" })
        }
        output
    }

    private fun stopAllLocked() {
        active?.let(::stopInstanceLocked)
        active = null
        stopStandbyLocked()
        fadingOut.forEach(::stopInstanceLocked)
        fadingOut.clear()
        djTransitionActive = false
    }

    private fun stopStandbyLocked() {
        standby?.let(::stopInstanceLocked)
        standby = null
    }

    private fun stopInstanceLocked(playback: VlcPlayback) {
        runCatching {
            playback.commandWriter.apply {
                write("quit")
                newLine()
                flush()
            }
        }
        runCatching { playback.commandWriter.close() }
        if (playback.process.isAlive) {
            playback.process.destroy()
        }
    }

    private fun VlcPlayback.currentPositionMs(): Long {
        val currentPosition = if (playing && process.isAlive) {
            positionBaseMs + (System.currentTimeMillis() - positionStartedAtMs).coerceAtLeast(0L)
        } else {
            positionBaseMs
        }
        return if (durationMs > 0L) {
            currentPosition.coerceIn(0L, durationMs)
        } else {
            currentPosition.coerceAtLeast(0L)
        }
    }

    private fun resolveVlcExecutable(): String {
        val pathEntries = System.getenv("PATH")
            ?.split(File.pathSeparatorChar)
            .orEmpty()
        val executable = listOf("cvlc", "vlc").firstNotNullOfOrNull { command ->
            pathEntries
                .map { Path.of(it, command) }
                .firstOrNull { Files.isExecutable(it) }
                ?.toString()
        }
        return executable ?: throw IllegalStateException("No encontré VLC. En Arch instala VLC con: sudo pacman -S vlc")
    }

    private fun String.toPlayableLocation(): PlayableLocation {
        val uri = runCatching { URI.create(this) }.getOrNull()
        val path = if (uri?.scheme == "file") {
            Path.of(uri)
        } else {
            Path.of(this)
        }.toAbsolutePath().normalize()

        return PlayableLocation(path = path)
    }

    private data class VlcPlayback(
        val songId: Long,
        val process: Process,
        val commandWriter: BufferedWriter,
        val durationMs: Long,
        var positionBaseMs: Long,
        var positionStartedAtMs: Long,
        var playing: Boolean,
        var volumePercent: Int = 0,
    )

    private data class PlayableLocation(
        val path: Path,
    )

    private fun equalPowerIn(progress: Float): Float {
        val angle = progress.coerceIn(0f, 1f).toDouble() * PI / 2.0
        return sin(angle).toFloat()
    }

    private fun equalPowerOut(progress: Float): Float {
        val angle = progress.coerceIn(0f, 1f).toDouble() * PI / 2.0
        return cos(angle).toFloat()
    }

    private fun Int.toVlcVolume(): Int = (coerceIn(MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT) * 2.56f).roundToInt()

    private companion object {
        const val MIN_VOLUME_PERCENT = 0
        const val MAX_VOLUME_PERCENT = 100
        const val MAX_DJ_MIX_SECONDS = 8
        const val MIN_DJ_FADE_MS = 1_000L
        const val DJ_FADE_STEPS = 24
        const val SYSTEM_AUDIO_SYNC_ATTEMPTS = 12
        const val SYSTEM_AUDIO_SYNC_INTERVAL_MS = 250L
    }

    private fun Result<Unit>.mapError(): Result<Unit> = fold(
        onSuccess = { Result.success(Unit) },
        onFailure = { throwable ->
            Result.failure(
                IllegalStateException(
                    throwable.message ?: "No se pudo controlar la reproducción",
                    throwable,
                ),
            )
        },
    )
}

data class DesktopPlaybackSnapshot(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackEnded: Boolean = false,
    val errorMessage: String? = null,
)
