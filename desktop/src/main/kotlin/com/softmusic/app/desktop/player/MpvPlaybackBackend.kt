package com.softmusic.app.desktop.player

import com.softmusic.app.data.Song
import java.io.File
import java.net.URI
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class MpvPlaybackBackend : DesktopPlaybackBackend {
    private val stateLock = Any()
    private var active: MpvPlayback? = null
    private var standby: MpvPlayback? = null
    private val fadingOut = mutableSetOf<MpvPlayback>()
    private var pendingPlaybackEnded: Boolean = false
    private var djTransitionActive: Boolean = false
    @Volatile private var lastErrorMessage: String? = null

    override fun play(song: Song, volumePercent: Int): Result<Unit> = runCatching {
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

    override fun prepareDjTransition(song: Song): Result<Unit> = runCatching {
        synchronized(stateLock) {
            lastErrorMessage = null
            val playback = active ?: return@runCatching
            if (!playback.process.isAlive || djTransitionActive) return@runCatching
            if (standby?.songId == song.id && standby?.process?.isAlive == true) {
                standby?.let { setInstanceVolume(it, 0) }
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

    override fun isDjTransitionActive(): Boolean = synchronized(stateLock) { djTransitionActive }

    override fun clearPreparedDjTransition(): Result<Unit> = runCatching {
        synchronized(stateLock) {
            if (!djTransitionActive) stopStandbyLocked()
        }
    }.mapError()

    override fun startDjTransition(
        song: Song,
        volumePercent: Int,
        mixDurationMs: Long,
    ): Result<Unit> = runCatching {
        val safeVolume = volumePercent.coerceIn(MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT)
        val safeMixDurationMs = mixDurationMs.coerceIn(MIN_DJ_FADE_MS, MAX_DJ_MIX_SECONDS * 1_000L)
        val outgoing: MpvPlayback?
        val incoming: MpvPlayback

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

    override fun setVolume(volumePercent: Int): Result<Unit> = runCatching {
        val safeVolume = volumePercent.coerceIn(MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT)
        synchronized(stateLock) {
            active?.let { playback -> setInstanceVolume(playback, safeVolume) }
        }
        Unit
    }.mapError()

    override fun resume(): Result<Unit> = runCatching {
        synchronized(stateLock) {
            lastErrorMessage = null
            val playback = active ?: throw IllegalStateException("La reproducción ya terminó; vuelve a seleccionar la canción")
            if (!playback.process.isAlive) {
                throw IllegalStateException("La reproducción ya terminó; vuelve a seleccionar la canción")
            }
            resumeInstanceLocked(playback)
        }
    }.mapError()

    override fun pause(): Result<Unit> = runCatching {
        synchronized(stateLock) {
            val playback = active ?: return@runCatching
            if (playback.process.isAlive && playback.playing) {
                playback.positionBaseMs = playback.currentPositionMs()
                sendCommand(playback, setPauseCommand(paused = true))
                playback.playing = false
            }
        }
    }.mapError()

    override fun stop(): Result<Unit> = runCatching {
        synchronized(stateLock) {
            stopAllLocked()
            pendingPlaybackEnded = false
            djTransitionActive = false
        }
    }.mapError()

    override fun seekTo(positionMs: Long): Result<Unit> = runCatching {
        synchronized(stateLock) {
            val playback = active ?: return@runCatching
            val safePositionMs = positionMs.coerceIn(0L, playback.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE)
            playback.positionBaseMs = safePositionMs
            if (playback.playing) playback.positionStartedAtMs = System.currentTimeMillis()
            sendCommand(playback, setTimePositionCommand(safePositionMs))
        }
    }.mapError()

    override fun snapshot(): DesktopPlaybackSnapshot = synchronized(stateLock) {
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
        refreshPlaybackState(playback)

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

    override fun release() {
        synchronized(stateLock) {
            stopAllLocked()
        }
    }

    private fun startPlaybackLocked(song: Song, volumePercent: Int, startPaused: Boolean): MpvPlayback {
        val playableLocation = song.uri.toPlayableLocation()
        if (!Files.isReadable(playableLocation.path)) {
            throw IllegalStateException("No se puede leer el archivo: ${playableLocation.path}")
        }

        val ipcSocket = Files.createTempFile("softmusic-mpv-", ".sock")
        Files.deleteIfExists(ipcSocket)
        val command = mutableListOf(
            resolveMpvExecutable(),
            "--no-video",
            "--force-window=no",
            "--idle=no",
            "--no-terminal",
            "--really-quiet",
            "--input-ipc-server=$ipcSocket",
            "--volume=${volumePercent.coerceIn(MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT)}",
        ).apply {
            if (startPaused) add("--pause=yes")
            add(playableLocation.path.toString())
        }

        val process = ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()

        waitForIpcSocket(ipcSocket, process)

        val playback = MpvPlayback(
            songId = song.id,
            process = process,
            ipcSocket = ipcSocket,
            durationMs = song.durationMs.coerceAtLeast(0L),
            positionBaseMs = 0L,
            positionStartedAtMs = System.currentTimeMillis(),
            playing = !startPaused,
            volumePercent = volumePercent.coerceIn(MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT),
        )
        refreshPlaybackState(playback)
        return playback
    }

    private fun startCrossfade(
        outgoing: MpvPlayback,
        incoming: MpvPlayback,
        targetVolumePercent: Int,
        durationMs: Long,
    ) {
        val initialIncomingVolume = initialIncomingVolume(targetVolumePercent)
        synchronized(stateLock) {
            if (active === incoming && incoming.process.isAlive) {
                setInstanceVolume(incoming, initialIncomingVolume)
            }
        }

        Thread {
            val steps = DJ_FADE_STEPS
            val stepDelayMs = (durationMs / steps).coerceAtLeast(50L)
            repeat(steps) { step ->
                Thread.sleep(stepDelayMs)
                val progress = (step + 1).toFloat() / steps.toFloat()
                val incomingVolume = (initialIncomingVolume + (targetVolumePercent - initialIncomingVolume) * equalPowerIn(progress))
                    .roundToInt()
                    .coerceIn(initialIncomingVolume, targetVolumePercent)
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
                    setInstanceVolume(incoming, incomingVolume)
                    setInstanceVolume(outgoing, outgoingVolume)
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
            name = "SoftMusic-mpv-dj-crossfade-${incoming.process.pid()}"
            isDaemon = true
        }.start()
    }

    private fun resumeInstanceLocked(playback: MpvPlayback) {
        if (!playback.process.isAlive || playback.playing) return
        playback.positionStartedAtMs = System.currentTimeMillis()
        playback.playing = true
        sendCommand(playback, setPauseCommand(paused = false))
    }

    private fun setInstanceVolume(playback: MpvPlayback, volumePercent: Int) {
        val safeVolume = volumePercent.coerceIn(MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT)
        playback.volumePercent = safeVolume
        sendCommand(playback, setVolumeCommand(safeVolume))
    }

    private fun refreshPlaybackState(playback: MpvPlayback) {
        if (!playback.process.isAlive) return
        readBooleanProperty(playback, "eof-reached")?.let { ended ->
            if (ended && playback.playing) {
                pendingPlaybackEnded = true
                playback.playing = false
            }
        }
        readBooleanProperty(playback, "pause")?.let { paused ->
            val nextPlaying = !paused
            if (playback.playing != nextPlaying) {
                playback.positionBaseMs = playback.currentPositionMs()
                playback.positionStartedAtMs = System.currentTimeMillis()
                playback.playing = nextPlaying
            }
        }
        readDoubleProperty(playback, "duration")
            ?.takeIf { it > 0.0 }
            ?.let { playback.durationMs = (it * 1_000.0).toLong().coerceAtLeast(0L) }
        readDoubleProperty(playback, "time-pos")
            ?.takeIf { it >= 0.0 }
            ?.let { positionSeconds ->
                playback.positionBaseMs = (positionSeconds * 1_000.0).toLong().coerceAtLeast(0L)
                playback.positionStartedAtMs = System.currentTimeMillis()
            }
    }

    private fun sendCommand(playback: MpvPlayback, command: String) {
        if (!playback.process.isAlive || !Files.exists(playback.ipcSocket)) return
        runCatching { sendIpcRequest(playback, command, readResponse = false) }
            .onFailure { throwable ->
                lastErrorMessage = throwable.message ?: "No se pudo enviar el comando a MPV"
            }
    }

    private fun readDoubleProperty(playback: MpvPlayback, property: String): Double? {
        return readPropertyResponse(playback, property)?.toDoubleOrNull()
    }

    private fun readBooleanProperty(playback: MpvPlayback, property: String): Boolean? {
        return when (readPropertyResponse(playback, property)) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

    private fun readPropertyResponse(playback: MpvPlayback, property: String): String? {
        if (!playback.process.isAlive || !Files.exists(playback.ipcSocket)) return null
        val request = "{\"command\":[\"get_property\",\"$property\"]}"
        return runCatching { sendIpcRequest(playback, request, readResponse = true) }
            .onFailure { throwable -> lastErrorMessage = throwable.message ?: "No se pudo leer estado de MPV" }
            .getOrNull()
            ?.substringAfter("\"data\":", missingDelimiterValue = "")
            ?.substringBefore(",\"error\"", missingDelimiterValue = "")
            ?.trim()
            ?.trim('"')
            ?.takeIf { it.isNotBlank() && it != "null" }
    }

    private fun sendIpcRequest(playback: MpvPlayback, command: String, readResponse: Boolean): String? {
        SocketChannel.open(UnixDomainSocketAddress.of(playback.ipcSocket)).use { channel ->
            val bytes = (command + "\n").toByteArray(StandardCharsets.UTF_8)
            val writeBuffer = ByteBuffer.wrap(bytes)
            while (writeBuffer.hasRemaining()) {
                channel.write(writeBuffer)
            }
            if (!readResponse) return null

            channel.configureBlocking(false)
            val readBuffer = ByteBuffer.allocate(IPC_RESPONSE_BUFFER_BYTES)
            val response = StringBuilder()
            val deadlineMs = System.currentTimeMillis() + IPC_READ_TIMEOUT_MS
            while (System.currentTimeMillis() < deadlineMs) {
                val bytesRead = channel.read(readBuffer)
                if (bytesRead < 0) break
                if (bytesRead == 0) {
                    Thread.sleep(IPC_READ_POLL_INTERVAL_MS)
                    continue
                }
                readBuffer.flip()
                response.append(StandardCharsets.UTF_8.decode(readBuffer).toString())
                readBuffer.clear()
                val responseLine = response.lineSequence()
                    .map { it.trim() }
                    .firstOrNull { it.contains("\"error\":") }
                if (responseLine != null) return responseLine
            }
            return response.toString().lineSequence().firstOrNull { it.contains("\"error\":") }?.trim()
        }
    }

    private fun waitForIpcSocket(ipcSocket: Path, process: Process) {
        repeat(IPC_START_ATTEMPTS) {
            if (!process.isAlive) throw IllegalStateException("MPV terminó antes de iniciar la reproducción")
            if (Files.exists(ipcSocket)) return
            Thread.sleep(IPC_START_INTERVAL_MS)
        }
        throw IllegalStateException("MPV no abrió el socket de control")
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

    private fun stopInstanceLocked(playback: MpvPlayback) {
        sendCommand(playback, quitCommand())
        if (playback.process.isAlive) {
            playback.process.destroy()
            if (!playback.process.waitFor(500, TimeUnit.MILLISECONDS)) {
                playback.process.destroyForcibly()
            }
        }
        runCatching { Files.deleteIfExists(playback.ipcSocket) }
    }

    private fun MpvPlayback.currentPositionMs(): Long {
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

    private fun resolveMpvExecutable(): String {
        val pathEntries = System.getenv("PATH")
            ?.split(File.pathSeparatorChar)
            .orEmpty()
        val executable = pathEntries
            .map { Path.of(it, "mpv") }
            .firstOrNull { Files.isExecutable(it) }
            ?.toString()
        return executable ?: throw IllegalStateException("No encontré MPV. En Arch instala MPV con: sudo pacman -S mpv")
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

    private fun setPauseCommand(paused: Boolean): String = "{\"command\":[\"set_property\",\"pause\",$paused]}"

    private fun setVolumeCommand(volumePercent: Int): String = "{\"command\":[\"set_property\",\"volume\",${volumePercent.coerceIn(MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT)}]}"

    private fun setTimePositionCommand(positionMs: Long): String {
        val seconds = String.format(Locale.ROOT, "%.3f", positionMs.coerceAtLeast(0L) / 1_000.0)
        return "{\"command\":[\"set_property\",\"time-pos\",$seconds]}"
    }

    private fun quitCommand(): String = "{\"command\":[\"quit\"]}"

    private data class MpvPlayback(
        val songId: Long,
        val process: Process,
        val ipcSocket: Path,
        var durationMs: Long,
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

    private fun initialIncomingVolume(targetVolumePercent: Int): Int {
        val safeVolume = targetVolumePercent.coerceIn(MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT)
        if (safeVolume <= MIN_VOLUME_PERCENT) return MIN_VOLUME_PERCENT
        return (safeVolume * DJ_INITIAL_INCOMING_VOLUME_RATIO)
            .roundToInt()
            .coerceIn(1, safeVolume)
    }

    private companion object {
        const val MIN_VOLUME_PERCENT = 0
        const val MAX_VOLUME_PERCENT = 100
        const val MAX_DJ_MIX_SECONDS = 8
        const val MIN_DJ_FADE_MS = 1_000L
        const val DJ_FADE_STEPS = 24
        const val DJ_INITIAL_INCOMING_VOLUME_RATIO = 0.15f
        const val IPC_START_ATTEMPTS = 100
        const val IPC_START_INTERVAL_MS = 20L
        const val IPC_RESPONSE_BUFFER_BYTES = 4_096
        const val IPC_READ_TIMEOUT_MS = 120L
        const val IPC_READ_POLL_INTERVAL_MS = 5L
    }

    private fun Result<Unit>.mapError(): Result<Unit> = fold(
        onSuccess = { Result.success(Unit) },
        onFailure = { throwable ->
            Result.failure(
                IllegalStateException(
                    throwable.message ?: "No se pudo controlar la reproducción con MPV",
                    throwable,
                ),
            )
        },
    )
}
