package com.softmusic.app.desktop.player

import com.softmusic.app.data.Song

interface DesktopPlaybackBackend {
    fun play(song: Song, volumePercent: Int): Result<Unit>
    fun prepareDjTransition(song: Song): Result<Unit>
    fun isDjTransitionActive(): Boolean
    fun clearPreparedDjTransition(): Result<Unit>
    fun startDjTransition(
        song: Song,
        volumePercent: Int,
        mixDurationMs: Long,
    ): Result<Unit>
    fun setVolume(volumePercent: Int): Result<Unit>
    fun resume(): Result<Unit>
    fun pause(): Result<Unit>
    fun stop(): Result<Unit>
    fun seekTo(positionMs: Long): Result<Unit>
    fun snapshot(): DesktopPlaybackSnapshot
    fun release()
}

enum class DesktopPlaybackBackendType(
    val label: String,
    val description: String,
) {
    Vlc(
        label = "VLC",
        description = "Backend estable predeterminado. Requiere VLC instalado.",
    ),
    Mpv(
        label = "MPV experimental",
        description = "Backend alternativo en prueba. Requiere MPV instalado.",
    ),
}

data class DesktopPlaybackBackendSelection(
    val type: DesktopPlaybackBackendType,
    val isOverridden: Boolean,
)

fun resolveDesktopPlaybackBackend(preferredBackend: DesktopPlaybackBackendType): DesktopPlaybackBackendSelection {
    val backendName = System.getProperty("softmusic.desktop.backend")
        ?: System.getenv("SOFTMUSIC_DESKTOP_BACKEND")
    return when (backendName?.trim()?.lowercase()) {
        "mpv" -> DesktopPlaybackBackendSelection(DesktopPlaybackBackendType.Mpv, isOverridden = true)
        "vlc" -> DesktopPlaybackBackendSelection(DesktopPlaybackBackendType.Vlc, isOverridden = true)
        null, "" -> DesktopPlaybackBackendSelection(preferredBackend, isOverridden = false)
        else -> DesktopPlaybackBackendSelection(preferredBackend, isOverridden = false)
    }
}

fun createDesktopPlaybackBackend(backendType: DesktopPlaybackBackendType): DesktopPlaybackBackend {
    return when (backendType) {
        DesktopPlaybackBackendType.Mpv -> MpvPlaybackBackend()
        DesktopPlaybackBackendType.Vlc -> DesktopAudioPlayer()
    }
}
