package com.softmusic.app.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.softmusic.app.data.MusicPlaylist
import com.softmusic.app.data.Song
import com.softmusic.app.desktop.data.DesktopMusicScanner
import com.softmusic.app.desktop.player.DesktopAudioPlayer
import com.softmusic.app.desktop.prefs.DesktopPreferences
import com.softmusic.app.desktop.prefs.DesktopLibraryView
import com.softmusic.app.desktop.prefs.DesktopSettings
import com.softmusic.app.desktop.prefs.DesktopThemeMode
import com.softmusic.app.desktop.prefs.DesktopPreferencesStore
import com.softmusic.app.player.PlaybackMode
import com.softmusic.app.player.SortMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.text.Normalizer
import java.util.UUID
import javax.swing.JFileChooser
import kotlin.math.min

private val LocalDesktopColors = staticCompositionLocalOf { DarkDesktopColors }

private enum class DesktopSection(val label: String) {
    Home("Inicio"),
    Music("Canciones"),
    Favorites("Favoritos"),
    Playlists("Playlists"),
    Settings("Configuración"),
}

private fun DesktopSection.icon(): ImageVector = when (this) {
    DesktopSection.Home -> Icons.Filled.MusicNote
    DesktopSection.Music -> Icons.Filled.LibraryMusic
    DesktopSection.Favorites -> Icons.Filled.Star
    DesktopSection.Playlists -> Icons.AutoMirrored.Filled.PlaylistAdd
    DesktopSection.Settings -> Icons.Filled.Settings
}

private data class DesktopColors(
    val isDark: Boolean,
    val background: Color,
    val card: Color,
    val cardAlt: Color,
    val text: Color,
    val muted: Color,
    val subtle: Color,
    val accent: Color,
    val danger: Color,
    val warning: Color,
    val divider: Color,
    val highlight: Color,
)

private val DarkDesktopColors = DesktopColors(
    isDark = true,
    background = Color(0xFF08090C),
    card = Color(0xFF101217),
    cardAlt = Color(0xFF151922),
    text = Color(0xFFF3F5F8),
    muted = Color(0xFFB8BEC8),
    subtle = Color(0xFF8F98A8),
    accent = Color(0xFF1DB954),
    danger = Color(0xFFFF6B6B),
    warning = Color(0xFFFFB15F),
    divider = Color(0xFF202632),
    highlight = Color(0xFF142119),
)

private val LightDesktopColors = DesktopColors(
    isDark = false,
    background = Color(0xFFF7F9FC),
    card = Color(0xFFFFFFFF),
    cardAlt = Color(0xFFEAF0F7),
    text = Color(0xFF121419),
    muted = Color(0xFF566171),
    subtle = Color(0xFF748091),
    accent = Color(0xFF138A3D),
    danger = Color(0xFFC62828),
    warning = Color(0xFFC95F00),
    divider = Color(0xFFDCE3EC),
    highlight = Color(0xFFE5F4EA),
)

private val MidnightDesktopColors = DesktopColors(
    isDark = true,
    background = Color(0xFF07111F),
    card = Color(0xFF0E1A2B),
    cardAlt = Color(0xFF172A42),
    text = Color(0xFFEAF2FF),
    muted = Color(0xFFB8C7DB),
    subtle = Color(0xFF91A4BF),
    accent = Color(0xFF5F9BFF),
    danger = Color(0xFFFF6B6B),
    warning = Color(0xFFFFB15F),
    divider = Color(0xFF223852),
    highlight = Color(0xFF102842),
)

private val ForestDesktopColors = DesktopColors(
    isDark = true,
    background = Color(0xFF07120C),
    card = Color(0xFF0E1B13),
    cardAlt = Color(0xFF183020),
    text = Color(0xFFF0F7EF),
    muted = Color(0xFFBBD0BD),
    subtle = Color(0xFF91AD96),
    accent = Color(0xFF1DB954),
    danger = Color(0xFFFF6B6B),
    warning = Color(0xFFFFCC4D),
    divider = Color(0xFF24402C),
    highlight = Color(0xFF112819),
)

private val SunsetDesktopColors = DesktopColors(
    isDark = false,
    background = Color(0xFFFFF7EF),
    card = Color(0xFFFFFCF8),
    cardAlt = Color(0xFFF2DDC8),
    text = Color(0xFF231811),
    muted = Color(0xFF6B5545),
    subtle = Color(0xFF967761),
    accent = Color(0xFFC95F00),
    danger = Color(0xFFC62828),
    warning = Color(0xFF9A6A00),
    divider = Color(0xFFE8CDB5),
    highlight = Color(0xFFFFE7D0),
)

private val LavenderDesktopColors = DesktopColors(
    isDark = false,
    background = Color(0xFFF8F3FF),
    card = Color(0xFFFFFBFF),
    cardAlt = Color(0xFFE8DDF7),
    text = Color(0xFF1F1726),
    muted = Color(0xFF5C5167),
    subtle = Color(0xFF7D708A),
    accent = Color(0xFF7B2EDB),
    danger = Color(0xFFC62828),
    warning = Color(0xFFC95F00),
    divider = Color(0xFFD8C9EA),
    highlight = Color(0xFFF0E7FA),
)

private val GraphiteDesktopColors = DesktopColors(
    isDark = true,
    background = Color(0xFF111315),
    card = Color(0xFF1A1D21),
    cardAlt = Color(0xFF2A3036),
    text = Color(0xFFF2F4F7),
    muted = Color(0xFFC0C6CF),
    subtle = Color(0xFF919AA7),
    accent = Color(0xFF5F9BFF),
    danger = Color(0xFFFF6B6B),
    warning = Color(0xFFFFB15F),
    divider = Color(0xFF343B44),
    highlight = Color(0xFF232D38),
)

fun main() = application {
    DesktopApp()
}

@Composable
private fun ApplicationScope.DesktopApp() {
    val scanner = remember { DesktopMusicScanner() }
    val audioPlayer = remember { DesktopAudioPlayer() }
    val preferencesStore = remember { DesktopPreferencesStore() }
    val initialPreferences = remember { preferencesStore.load() }
    val scope = rememberCoroutineScope()
    var preferencesErrorMessage by remember { mutableStateOf<String?>(null) }
    var selectedFolder by remember { mutableStateOf(initialPreferences.selectedFolderPath?.toExistingDirectoryPath()) }
    var songs by remember { mutableStateOf(emptyList<Song>()) }
    var isScanning by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var playbackErrorMessage by remember { mutableStateOf<String?>(null) }
    var currentSong by remember { mutableStateOf<Song?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var playbackStartDeadlineMillis by remember { mutableStateOf<Long?>(null) }
    var estimatedPlaybackEndDeadlineMillis by remember { mutableStateOf<Long?>(null) }
    var pendingDjCurrentSongId by remember { mutableStateOf<Long?>(null) }
    var pendingDjTargetSong by remember { mutableStateOf<Song?>(null) }
    var activeQueue by remember { mutableStateOf(emptyList<Song>()) }
    var favoriteSongIds by remember { mutableStateOf(initialPreferences.favoriteSongIds) }
    var playlists by remember { mutableStateOf(initialPreferences.playlists) }
    var desktopSettings by remember { mutableStateOf(initialPreferences.settings) }
    var selectedSection by remember { mutableStateOf(DesktopSection.Home) }
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    var newPlaylistName by remember { mutableStateOf("") }
    val selectedPlaylist = playlists.firstOrNull { it.id == selectedPlaylistId }
    val availableFolders = songs.toDesktopFolders()
    val librarySongs = songs.filteredForLibrary(
        folderFilterPath = desktopSettings.folderFilterPath,
        searchQuery = desktopSettings.searchQuery,
    )
    val musicPlaybackSongs = songs.filteredForLibrary(
        folderFilterPath = desktopSettings.folderFilterPath,
        searchQuery = "",
    ).sortedFor(desktopSettings.sortMode)
    val displayedSongs = librarySongs.sortedFor(desktopSettings.sortMode)
    val favoritePlaybackSongs = songs.displayedFor(
        favoriteSongIds = favoriteSongIds,
        showFavoritesOnly = true,
        selectedPlaylist = null,
    ).sortedFor(desktopSettings.sortMode)
    val favoriteSongs = songs.filteredForLibrary(
        folderFilterPath = null,
        searchQuery = desktopSettings.searchQuery,
    ).displayedFor(
        favoriteSongIds = favoriteSongIds,
        showFavoritesOnly = true,
        selectedPlaylist = null,
    ).sortedFor(desktopSettings.sortMode)
    val selectedPlaylistSongs = songs.displayedFor(
        favoriteSongIds = favoriteSongIds,
        showFavoritesOnly = false,
        selectedPlaylist = selectedPlaylist,
    ).sortedFor(desktopSettings.sortMode)

    fun persistPreferences(
        selectedFolderPath: String? = selectedFolder?.toString(),
        nextFavoriteSongIds: Set<Long> = favoriteSongIds,
        nextPlaylists: List<MusicPlaylist> = playlists,
        nextSettings: DesktopSettings = desktopSettings,
    ) {
        val nextPreferences = DesktopPreferences(
            selectedFolderPath = selectedFolderPath,
            favoriteSongIds = nextFavoriteSongIds,
            playlists = nextPlaylists,
            settings = nextSettings,
        )
        preferencesStore.save(nextPreferences)
            .onSuccess { preferencesErrorMessage = null }
            .onFailure { throwable ->
                preferencesErrorMessage = throwable.message ?: "No se pudieron guardar las preferencias"
            }
    }

    fun toggleFavorite(songId: Long) {
        val nextFavoriteSongIds = if (songId in favoriteSongIds) {
            favoriteSongIds - songId
        } else {
            favoriteSongIds + songId
        }
        favoriteSongIds = nextFavoriteSongIds
        persistPreferences(nextFavoriteSongIds = nextFavoriteSongIds)
    }

    fun createPlaylist() {
        val cleanName = newPlaylistName.trim().take(MAX_PLAYLIST_NAME_LENGTH)
        if (cleanName.isBlank()) return
        if (playlists.any { it.name.trim().lowercase() == cleanName.lowercase() }) return
        val nextPlaylists = playlists + MusicPlaylist(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            songIds = emptyList(),
        )
        playlists = nextPlaylists
        newPlaylistName = ""
        persistPreferences(nextPlaylists = nextPlaylists)
    }

    fun deleteSelectedPlaylist() {
        val playlistId = selectedPlaylistId ?: return
        val nextPlaylists = playlists.filterNot { it.id == playlistId }
        playlists = nextPlaylists
        selectedPlaylistId = null
        persistPreferences(nextPlaylists = nextPlaylists)
    }

    fun addSongToPlaylist(playlistId: String, songId: Long) {
        val nextPlaylists = playlists.map { playlist ->
            if (playlist.id == playlistId && songId !in playlist.songIds) {
                playlist.copy(songIds = playlist.songIds + songId)
            } else {
                playlist
            }
        }
        playlists = nextPlaylists
        persistPreferences(nextPlaylists = nextPlaylists)
    }

    fun removeSongFromPlaylist(playlistId: String, songId: Long) {
        val nextPlaylists = playlists.map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(songIds = playlist.songIds.filterNot { it == songId })
            } else {
                playlist
            }
        }
        playlists = nextPlaylists
        persistPreferences(nextPlaylists = nextPlaylists)
    }

    fun updateSettings(nextSettings: DesktopSettings) {
        val safeSettings = nextSettings.copy(
            volumePercent = nextSettings.volumePercent.coerceIn(0, 100),
            djMixDurationSeconds = nextSettings.djMixDurationSeconds.coerceIn(MIN_DJ_MIX_SECONDS, MAX_DJ_MIX_SECONDS),
        )
        desktopSettings = safeSettings
        persistPreferences(nextSettings = safeSettings)
        if (safeSettings.volumePercent == 0) {
            playbackErrorMessage = "El volumen está en 0%"
        } else if (playbackErrorMessage == "El volumen está en 0%") {
            playbackErrorMessage = null
        }
        if (!safeSettings.djModeEnabled || safeSettings.playbackMode == PlaybackMode.RepeatCurrent) {
            pendingDjCurrentSongId = null
            pendingDjTargetSong = null
            audioPlayer.clearPreparedDjTransition()
                .onFailure { throwable ->
                    playbackErrorMessage = throwable.message ?: "No se pudo limpiar la precarga DJ"
                }
        }
        audioPlayer.setVolume(safeSettings.volumePercent)
            .onFailure { throwable ->
                playbackErrorMessage = throwable.message ?: "No se pudo aplicar el volumen"
            }
    }

    fun applySnapshot(): Boolean {
        val snapshot = audioPlayer.snapshot()
        isPlaying = snapshot.isPlaying
        positionMs = snapshot.positionMs
        durationMs = snapshot.durationMs
        snapshot.errorMessage?.let { error ->
            playbackErrorMessage = error
            playbackStartDeadlineMillis = null
        }
        if (snapshot.isPlaying || snapshot.positionMs > 0L) {
            playbackStartDeadlineMillis = null
        }
        return snapshot.playbackEnded
    }

    fun handlePlaybackFailure(throwable: Throwable) {
        playbackErrorMessage = throwable.message ?: "No se pudo reproducir la canción"
        applySnapshot()
    }

    fun playSong(song: Song, queue: List<Song> = displayedSongs.ifEmpty { songs }) {
        activeQueue = queue.ifEmpty { listOf(song) }
        currentSong = song
        estimatedPlaybackEndDeadlineMillis = null
        pendingDjCurrentSongId = null
        pendingDjTargetSong = null
        playbackErrorMessage = if (desktopSettings.volumePercent == 0) {
            "El volumen está en 0%"
        } else {
            null
        }
        playbackStartDeadlineMillis = System.currentTimeMillis() + PLAYBACK_START_TIMEOUT_MS
        audioPlayer.play(song, desktopSettings.volumePercent)
            .onSuccess { applySnapshot() }
            .onFailure(::handlePlaybackFailure)
    }

    fun startDjTransition(nextSong: Song, mixDurationMs: Long) {
        currentSong = nextSong
        estimatedPlaybackEndDeadlineMillis = null
        pendingDjCurrentSongId = null
        pendingDjTargetSong = null
        playbackErrorMessage = if (desktopSettings.volumePercent == 0) {
            "El volumen está en 0%"
        } else {
            null
        }
        playbackStartDeadlineMillis = null
        audioPlayer.startDjTransition(
            song = nextSong,
            volumePercent = desktopSettings.volumePercent,
            mixDurationMs = mixDurationMs,
        ).onSuccess {
            applySnapshot()
        }.onFailure(::handlePlaybackFailure)
    }

    fun queueSongs(): List<Song> = activeQueue.ifEmpty { displayedSongs.ifEmpty { songs } }

    fun stopPlayback() {
        audioPlayer.stop()
        currentSong = null
        activeQueue = emptyList()
        isPlaying = false
        positionMs = 0L
        durationMs = 0L
        estimatedPlaybackEndDeadlineMillis = null
        pendingDjCurrentSongId = null
        pendingDjTargetSong = null
    }

    fun playNextSong(manual: Boolean) {
        val queue = queueSongs()
        val nextSong = queue.nextSongFor(
            currentSong = currentSong,
            playbackMode = desktopSettings.playbackMode,
            manual = manual,
        )
        if (nextSong == null) {
            if (!manual) stopPlayback()
            return
        }
        playSong(nextSong, queue)
    }

    fun playPreviousSong() {
        val queue = queueSongs()
        val previousSong = queue.previousSongFor(currentSong, desktopSettings.playbackMode) ?: return
        playSong(previousSong, queue)
    }

    fun maybeStartDjTransition(): Boolean {
        val current = currentSong ?: return false
        if (!desktopSettings.djModeEnabled) return false
        if (desktopSettings.playbackMode == PlaybackMode.RepeatCurrent) return false
        if (!isPlaying) return false
        if (audioPlayer.isDjTransitionActive()) return false

        val queue = queueSongs()
        if (queue.size <= 1) return false

        val safeDurationMs = (durationMs.takeIf { it > 0L } ?: current.durationMs).coerceAtLeast(0L)
        val safePositionMs = positionMs.coerceAtLeast(0L)
        val mixDurationMs = desktopSettings.djMixDurationSeconds.coerceIn(MIN_DJ_MIX_SECONDS, MAX_DJ_MIX_SECONDS) * 1_000L
        if (safeDurationMs <= mixDurationMs + MIN_DJ_SONG_EXTRA_MS) return false

        val remainingMs = safeDurationMs - safePositionMs
        if (remainingMs <= MIN_DJ_FADE_MS + DJ_HANDOFF_SAFETY_MS) return false

        if (remainingMs > mixDurationMs + DJ_PRELOAD_LEAD_MS) {
            pendingDjCurrentSongId = null
            pendingDjTargetSong = null
            return false
        }

        val nextSong = pendingDjTargetSong
            ?.takeIf { pendingDjCurrentSongId == current.id && queue.any { queuedSong -> queuedSong.id == it.id } }
            ?: queue.nextSongFor(
                currentSong = current,
                playbackMode = desktopSettings.playbackMode,
                manual = false,
            )?.also { target ->
                pendingDjCurrentSongId = current.id
                pendingDjTargetSong = target
            }
            ?: return false
        if (nextSong.id == current.id) return false

        audioPlayer.prepareDjTransition(nextSong)
            .onFailure(::handlePlaybackFailure)

        if (remainingMs > mixDurationMs) return false

        val safeMixDurationMs = (remainingMs - DJ_HANDOFF_SAFETY_MS)
            .coerceAtLeast(MIN_DJ_FADE_MS)
            .coerceAtMost(mixDurationMs)
        startDjTransition(nextSong, safeMixDurationMs)
        return true
    }

    fun shouldAdvanceByEstimatedEnd(): Boolean {
        val current = currentSong ?: return false
        if (!isPlaying || audioPlayer.isDjTransitionActive()) {
            estimatedPlaybackEndDeadlineMillis = null
            return false
        }
        val safeDurationMs = (durationMs.takeIf { it > 0L } ?: current.durationMs).coerceAtLeast(0L)
        if (safeDurationMs <= 0L || positionMs < safeDurationMs) {
            estimatedPlaybackEndDeadlineMillis = null
            return false
        }
        val now = System.currentTimeMillis()
        val deadline = estimatedPlaybackEndDeadlineMillis
        if (deadline == null) {
            estimatedPlaybackEndDeadlineMillis = now + ESTIMATED_PLAYBACK_END_GRACE_MS
            return false
        }
        return now >= deadline
    }

    fun playRelative(offset: Int) {
        if (offset < 0) {
            playPreviousSong()
        } else {
            playNextSong(manual = true)
        }
    }

    DisposableEffect(Unit) {
        onDispose { audioPlayer.release() }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            val playbackEnded = applySnapshot()
            val transitionStarted = maybeStartDjTransition()
            if (playbackEnded && currentSong != null) {
                estimatedPlaybackEndDeadlineMillis = null
                playNextSong(manual = false)
            } else if (!transitionStarted && shouldAdvanceByEstimatedEnd()) {
                estimatedPlaybackEndDeadlineMillis = null
                playNextSong(manual = false)
            }
            val deadline = playbackStartDeadlineMillis
            if (
                deadline != null &&
                System.currentTimeMillis() >= deadline &&
                currentSong != null &&
                !isPlaying &&
                positionMs == 0L &&
                playbackErrorMessage == null
            ) {
                playbackErrorMessage = "VLC aceptó el archivo, pero la reproducción no inició. Prueba abrir ese archivo con VLC."
                playbackStartDeadlineMillis = null
            }
            delay(500L)
        }
    }

    fun scanFolder(folder: Path, persistSelectedFolder: Boolean = true) {
        val normalizedFolder = folder.toAbsolutePath().normalize()
        selectedFolder = normalizedFolder
        isScanning = true
        errorMessage = null
        playbackErrorMessage = null
        if (persistSelectedFolder) {
            persistPreferences(selectedFolderPath = normalizedFolder.toString())
        }
        stopPlayback()
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    scanner.scan(normalizedFolder)
                }
            }.onSuccess { scannedSongs ->
                songs = scannedSongs
                if (desktopSettings.folderFilterPath != null && scannedSongs.none { it.folderPath == desktopSettings.folderFilterPath }) {
                    updateSettings(desktopSettings.copy(folderFilterPath = null))
                }
            }.onFailure { throwable ->
                songs = emptyList()
                errorMessage = throwable.message ?: "No se pudo escanear la carpeta"
            }
            isScanning = false
        }
    }

    androidx.compose.runtime.LaunchedEffect(desktopSettings.scanOnStartup, selectedFolder) {
        if (desktopSettings.scanOnStartup) selectedFolder?.let { folder ->
            scanFolder(folder, persistSelectedFolder = false)
        }
    }

    fun togglePlayback() {
        val playbackQueue = musicPlaybackSongs.ifEmpty { songs }
        val song = currentSong ?: displayedSongs.firstOrNull() ?: playbackQueue.firstOrNull()
        if (song == null) {
            playbackErrorMessage = "Selecciona una canción para reproducir"
            return
        }
        if (currentSong == null) {
            playSong(song, playbackQueue)
            return
        }
        val result = if (isPlaying) audioPlayer.pause() else audioPlayer.resume()
        result.onSuccess { applySnapshot() }.onFailure(::handlePlaybackFailure)
    }

    fun seekToPosition(nextPosition: Long) {
        estimatedPlaybackEndDeadlineMillis = null
        pendingDjCurrentSongId = null
        pendingDjTargetSong = null
        audioPlayer.seekTo(nextPosition)
            .onSuccess { applySnapshot() }
            .onFailure(::handlePlaybackFailure)
    }

    val systemDarkTheme = isSystemInDarkTheme()
    val desktopColors = when (desktopSettings.themeMode) {
        DesktopThemeMode.System -> if (systemDarkTheme) DarkDesktopColors else LightDesktopColors
        DesktopThemeMode.Light -> LightDesktopColors
        DesktopThemeMode.Dark -> DarkDesktopColors
        DesktopThemeMode.Midnight -> MidnightDesktopColors
        DesktopThemeMode.Forest -> ForestDesktopColors
        DesktopThemeMode.Sunset -> SunsetDesktopColors
        DesktopThemeMode.Lavender -> LavenderDesktopColors
        DesktopThemeMode.Graphite -> GraphiteDesktopColors
    }
    val desktopIcon = remember { loadDesktopAppIconPainter() }

    Window(
        onCloseRequest = ::exitApplication,
        title = "SoftMusic",
        icon = desktopIcon,
        state = rememberWindowState(width = 1220.dp, height = 780.dp),
    ) {
        DesktopWindowContent(desktopColors) {
            DesktopShell(
                selectedSection = selectedSection,
                songCount = songs.size,
                favoriteSongCount = favoritePlaybackSongs.size,
                playlistCount = playlists.size,
                isScanning = isScanning,
                onSelectSection = { section -> selectedSection = section },
                content = {
                    when (selectedSection) {
                        DesktopSection.Home -> MainMenuWindowContent(
                            selectedFolder = selectedFolder,
                            songCount = songs.size,
                            visibleSongCount = displayedSongs.size,
                            favoriteSongCount = favoritePlaybackSongs.size,
                            playlistCount = playlists.size,
                            isScanning = isScanning,
                            preferencesErrorMessage = preferencesErrorMessage,
                            scanErrorMessage = errorMessage,
                            currentSong = currentSong,
                            showArtwork = desktopSettings.showArtwork,
                            playbackErrorMessage = playbackErrorMessage,
                            onSelectFolder = { chooseMusicFolder()?.let(::scanFolder) },
                            onOpenMusic = { selectedSection = DesktopSection.Music },
                            onOpenFavorites = { selectedSection = DesktopSection.Favorites },
                            onOpenPlaylists = { selectedSection = DesktopSection.Playlists },
                            onOpenSettings = { selectedSection = DesktopSection.Settings },
                        )
                        DesktopSection.Music -> LibrarySectionWindowContent(
                            title = "Canciones",
                            subtitle = "Explora tu biblioteca local en su propia sección.",
                            filteredSongCount = displayedSongs.size,
                            availableFolders = availableFolders,
                            libraryView = desktopSettings.libraryView,
                            sortMode = desktopSettings.sortMode,
                            folderFilterPath = desktopSettings.folderFilterPath,
                            searchQuery = desktopSettings.searchQuery,
                            selectedFolderPath = selectedFolder?.toString(),
                            songs = displayedSongs,
                            isScanning = isScanning,
                            hasSelectedFolder = selectedFolder != null,
                            currentSong = currentSong,
                            favoriteSongIds = favoriteSongIds,
                            playlists = playlists,
                            selectedPlaylist = null,
                            showArtwork = desktopSettings.showArtwork,
                            showViewFilters = true,
                            showFolderFilters = true,
                            showAdvancedFilters = true,
                            onLibraryViewChange = { libraryView -> updateSettings(desktopSettings.copy(libraryView = libraryView)) },
                            onSortModeChange = { sortMode -> updateSettings(desktopSettings.copy(sortMode = sortMode)) },
                            onFolderFilterChange = { folderPath -> updateSettings(desktopSettings.copy(folderFilterPath = folderPath)) },
                            onSearchQueryChange = { query -> updateSettings(desktopSettings.copy(searchQuery = query)) },
                            onPlaySong = { song -> playSong(song, musicPlaybackSongs) },
                            onPlaySongGroup = { song, _ ->
                                    playSong(song, musicPlaybackSongs.groupQueueFor(desktopSettings.libraryView, song))
                            },
                            onToggleFavorite = ::toggleFavorite,
                            onAddSongToPlaylist = ::addSongToPlaylist,
                            onRemoveSongFromPlaylist = {},
                        )
                        DesktopSection.Favorites -> LibrarySectionWindowContent(
                            title = "Favoritos",
                            subtitle = "Tus canciones marcadas como favoritas, separadas de la biblioteca general.",
                            filteredSongCount = favoriteSongs.size,
                            availableFolders = availableFolders,
                            libraryView = DesktopLibraryView.Songs,
                            sortMode = desktopSettings.sortMode,
                            folderFilterPath = null,
                            searchQuery = desktopSettings.searchQuery,
                            selectedFolderPath = selectedFolder?.toString(),
                            songs = favoriteSongs,
                            isScanning = isScanning,
                            hasSelectedFolder = selectedFolder != null,
                            currentSong = currentSong,
                            favoriteSongIds = favoriteSongIds,
                            playlists = playlists,
                            selectedPlaylist = null,
                            showArtwork = desktopSettings.showArtwork,
                            showViewFilters = false,
                            showFolderFilters = false,
                            showAdvancedFilters = true,
                            onLibraryViewChange = {},
                            onSortModeChange = { sortMode -> updateSettings(desktopSettings.copy(sortMode = sortMode)) },
                            onFolderFilterChange = {},
                            onSearchQueryChange = { query -> updateSettings(desktopSettings.copy(searchQuery = query)) },
                            onPlaySong = { song -> playSong(song, favoritePlaybackSongs) },
                            onPlaySongGroup = { song, _ ->
                                playSong(song, favoritePlaybackSongs)
                            },
                            onToggleFavorite = ::toggleFavorite,
                            onAddSongToPlaylist = ::addSongToPlaylist,
                            onRemoveSongFromPlaylist = {},
                        )
                        DesktopSection.Playlists -> PlaylistsWindowContent(
                            playlists = playlists,
                            selectedPlaylist = selectedPlaylist,
                            selectedPlaylistSongs = selectedPlaylistSongs,
                            currentSong = currentSong,
                            favoriteSongIds = favoriteSongIds,
                            newPlaylistName = newPlaylistName,
                            isScanning = isScanning,
                            hasSelectedFolder = selectedFolder != null,
                            showArtwork = desktopSettings.showArtwork,
                            onSelectPlaylist = { playlistId -> selectedPlaylistId = playlistId },
                            onNewPlaylistNameChange = { newPlaylistName = it },
                            onCreatePlaylist = ::createPlaylist,
                            onDeleteSelectedPlaylist = ::deleteSelectedPlaylist,
                            onAddCurrentSongToPlaylist = { playlistId ->
                                currentSong?.let { song -> addSongToPlaylist(playlistId, song.id) }
                            },
                            onPlaySong = { song -> playSong(song, selectedPlaylistSongs) },
                            onToggleFavorite = ::toggleFavorite,
                            onAddSongToPlaylist = ::addSongToPlaylist,
                            onRemoveSongFromPlaylist = { songId ->
                                selectedPlaylistId?.let { playlistId -> removeSongFromPlaylist(playlistId, songId) }
                            },
                        )
                        DesktopSection.Settings -> Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                        ) {
                            SettingsPanel(
                                settings = desktopSettings,
                                onSettingsChange = ::updateSettings,
                            )
                        }
                    }
                },
                player = {
                    PlayerBar(
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs.takeIf { it > 0L } ?: currentSong?.durationMs ?: 0L,
                    volumePercent = desktopSettings.volumePercent,
                    playbackMode = desktopSettings.playbackMode,
                    showArtwork = desktopSettings.showArtwork,
                    playbackErrorMessage = playbackErrorMessage,
                    onTogglePlayPause = ::togglePlayback,
                    onPrevious = { playRelative(-1) },
                    onNext = { playRelative(1) },
                    onPlaybackModeChange = { playbackMode ->
                        updateSettings(desktopSettings.copy(playbackMode = playbackMode))
                    },
                    onSeek = ::seekToPosition,
                    )
                },
            )
        }
    }
}

@Composable
private fun DesktopWindowContent(
    desktopColors: DesktopColors,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalDesktopColors provides desktopColors) {
        MaterialTheme(
            colorScheme = if (desktopColors.isDark) {
                darkColorScheme(primary = desktopColors.accent)
            } else {
                lightColorScheme(primary = desktopColors.accent)
            },
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = desktopColors.background,
            ) {
                content()
            }
        }
    }
}

@Composable
private fun AppIconMark(
    size: Dp,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDesktopColors.current
    val iconPainter = remember { loadDesktopAppIconPainter() }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(colors.cardAlt),
        contentAlignment = Alignment.Center,
    ) {
        ComposeImage(
            painter = iconPainter,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun DesktopShell(
    selectedSection: DesktopSection,
    songCount: Int,
    favoriteSongCount: Int,
    playlistCount: Int,
    isScanning: Boolean,
    onSelectSection: (DesktopSection) -> Unit,
    content: @Composable () -> Unit,
    player: @Composable () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        DesktopSidebar(
            selectedSection = selectedSection,
            songCount = songCount,
            favoriteSongCount = favoriteSongCount,
            playlistCount = playlistCount,
            isScanning = isScanning,
            onSelectSection = onSelectSection,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                content()
            }
            player()
        }
    }
}

@Composable
private fun DesktopSidebar(
    selectedSection: DesktopSection,
    songCount: Int,
    favoriteSongCount: Int,
    playlistCount: Int,
    isScanning: Boolean,
    onSelectSection: (DesktopSection) -> Unit,
) {
    val colors = LocalDesktopColors.current
    Column(
        modifier = Modifier
            .width(244.dp)
            .fillMaxSize()
            .background(colors.card)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconMark(size = 46.dp, cornerRadius = 16.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SoftMusic",
                    color = colors.text,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = if (isScanning) "Escaneando..." else "$songCount canciones",
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(modifier = Modifier.height(26.dp))

        DesktopSection.entries.forEach { section ->
            val detail = when (section) {
                DesktopSection.Home -> "Menú principal"
                DesktopSection.Music -> if (songCount == 1) "1 canción" else "$songCount canciones"
                DesktopSection.Favorites -> if (favoriteSongCount == 1) "1 favorita" else "$favoriteSongCount favoritas"
                DesktopSection.Playlists -> if (playlistCount == 1) "1 playlist" else "$playlistCount playlists"
                DesktopSection.Settings -> "Tema y reproducción"
            }
            SidebarItem(
                section = section,
                detail = detail,
                selected = selectedSection == section,
                onClick = { onSelectSection(section) },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SidebarItem(
    section: DesktopSection,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalDesktopColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) colors.highlight else Color.Transparent, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (section == DesktopSection.Home) {
            AppIconMark(size = 22.dp, cornerRadius = 7.dp)
        } else {
            Icon(
                imageVector = section.icon(),
                contentDescription = null,
                tint = if (selected) colors.accent else colors.muted,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = section.label,
                color = if (selected) colors.text else colors.muted,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = detail,
                color = colors.subtle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun MainMenuWindowContent(
    selectedFolder: Path?,
    songCount: Int,
    visibleSongCount: Int,
    favoriteSongCount: Int,
    playlistCount: Int,
    isScanning: Boolean,
    preferencesErrorMessage: String?,
    scanErrorMessage: String?,
    currentSong: Song?,
    showArtwork: Boolean,
    playbackErrorMessage: String?,
    onSelectFolder: () -> Unit,
    onOpenMusic: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = LocalDesktopColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
    ) {
        Header(
            selectedFolder = selectedFolder,
            songCount = songCount,
            isScanning = isScanning,
            showSettings = false,
            onSelectFolder = onSelectFolder,
            onToggleSettings = onOpenSettings,
        )

        Spacer(modifier = Modifier.height(20.dp))

        preferencesErrorMessage?.let { message ->
            StatusCard(
                title = "No se pudieron guardar las preferencias",
                body = message,
                accentColor = colors.warning,
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        scanErrorMessage?.let { message ->
            StatusCard(
                title = "No se pudo escanear",
                body = message,
                accentColor = colors.danger,
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        Text(
            text = "Biblioteca",
            color = colors.text,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            modifier = Modifier.padding(top = 4.dp),
            text = "Usa el menú lateral para moverte entre secciones dentro de la misma aplicación.",
            color = colors.muted,
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(modifier = Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MenuActionCard(
                modifier = Modifier.weight(1f),
                title = "Canciones",
                subtitle = "$visibleSongCount visibles",
                icon = Icons.Filled.LibraryMusic,
                onClick = onOpenMusic,
            )
            MenuActionCard(
                modifier = Modifier.weight(1f),
                title = "Favoritos",
                subtitle = if (favoriteSongCount == 1) "1 favorita" else "$favoriteSongCount favoritas",
                icon = Icons.Filled.Star,
                onClick = onOpenFavorites,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MenuActionCard(
                modifier = Modifier.weight(1f),
                title = "Playlists",
                subtitle = if (playlistCount == 1) "1 playlist" else "$playlistCount playlists",
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                onClick = onOpenPlaylists,
            )
            MenuActionCard(
                modifier = Modifier.weight(1f),
                title = "Configuración",
                subtitle = "Tema y reproducción",
                icon = Icons.Filled.Settings,
                onClick = onOpenSettings,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        playbackErrorMessage?.let { message ->
            StatusCard(
                title = "Reproductor",
                body = message,
                accentColor = colors.warning,
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        currentSong?.let { song ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = colors.card),
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SongArtwork(song = song, showArtwork = showArtwork)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Reproduciendo ahora",
                            color = colors.subtle,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            text = song.title,
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = song.artist,
                            color = colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuActionCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LocalDesktopColors.current
    val contentAlpha = if (enabled) 1f else 0.45f
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(colors.cardAlt, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.accent.copy(alpha = contentAlpha),
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                color = colors.text.copy(alpha = contentAlpha),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                modifier = Modifier.padding(top = 4.dp),
                text = subtitle,
                color = colors.muted.copy(alpha = contentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun LibrarySectionWindowContent(
    title: String,
    subtitle: String,
    filteredSongCount: Int,
    availableFolders: List<DesktopFolder>,
    libraryView: DesktopLibraryView,
    sortMode: SortMode,
    folderFilterPath: String?,
    searchQuery: String,
    selectedFolderPath: String?,
    songs: List<Song>,
    isScanning: Boolean,
    hasSelectedFolder: Boolean,
    currentSong: Song?,
    favoriteSongIds: Set<Long>,
    playlists: List<MusicPlaylist>,
    selectedPlaylist: MusicPlaylist?,
    showArtwork: Boolean,
    showViewFilters: Boolean,
    showFolderFilters: Boolean,
    showAdvancedFilters: Boolean,
    onLibraryViewChange: (DesktopLibraryView) -> Unit,
    onSortModeChange: (SortMode) -> Unit,
    onFolderFilterChange: (String?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onPlaySong: (Song) -> Unit,
    onPlaySongGroup: (Song, List<Song>) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onAddSongToPlaylist: (String, Long) -> Unit,
    onRemoveSongFromPlaylist: (Long) -> Unit,
) {
    val colors = LocalDesktopColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(
            text = title,
            color = colors.text,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            modifier = Modifier.padding(top = 4.dp),
            text = subtitle,
            color = colors.muted,
            style = MaterialTheme.typography.bodyLarge,
        )

        Spacer(modifier = Modifier.height(18.dp))

        MusicFiltersPanel(
            filteredSongCount = filteredSongCount,
            availableFolders = availableFolders,
            libraryView = libraryView,
            sortMode = sortMode,
            folderFilterPath = folderFilterPath,
            searchQuery = searchQuery,
            selectedFolderPath = selectedFolderPath,
            showViewFilters = showViewFilters,
            showFolderFilters = showFolderFilters,
            showAdvancedFilters = showAdvancedFilters,
            onLibraryViewChange = onLibraryViewChange,
            onSortModeChange = onSortModeChange,
            onFolderFilterChange = onFolderFilterChange,
            onSearchQueryChange = onSearchQueryChange,
        )

        Spacer(modifier = Modifier.height(16.dp))

        LibraryContent(
            modifier = Modifier.weight(1f),
            songs = songs,
            isScanning = isScanning,
            hasSelectedFolder = hasSelectedFolder,
            currentSong = currentSong,
            favoriteSongIds = favoriteSongIds,
            playlists = playlists,
            selectedPlaylist = selectedPlaylist,
            libraryView = libraryView,
            showArtwork = showArtwork,
            onPlaySong = onPlaySong,
            onPlaySongGroup = onPlaySongGroup,
            onToggleFavorite = onToggleFavorite,
            onAddSongToPlaylist = onAddSongToPlaylist,
            onRemoveSongFromPlaylist = onRemoveSongFromPlaylist,
        )
    }
}

@Composable
private fun MusicFiltersPanel(
    filteredSongCount: Int,
    availableFolders: List<DesktopFolder>,
    libraryView: DesktopLibraryView,
    sortMode: SortMode,
    folderFilterPath: String?,
    searchQuery: String,
    selectedFolderPath: String?,
    showViewFilters: Boolean,
    showFolderFilters: Boolean,
    showAdvancedFilters: Boolean,
    onLibraryViewChange: (DesktopLibraryView) -> Unit,
    onSortModeChange: (SortMode) -> Unit,
    onFolderFilterChange: (String?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
) {
    val colors = LocalDesktopColors.current
    var expandedFilters by remember { mutableStateOf(false) }
    val hasAdvancedFilters = showAdvancedFilters
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                        )
                    },
                    label = { Text("Buscar por canción, artista o álbum") },
                )
                SummaryPill(text = "$filteredSongCount visibles")
                if (hasAdvancedFilters) {
                    FilterButton(
                        label = if (expandedFilters) "Ocultar filtros" else "Filtros",
                        selected = expandedFilters,
                        onClick = { expandedFilters = !expandedFilters },
                        icon = Icons.AutoMirrored.Filled.Sort,
                    )
                }
            }

            if (hasAdvancedFilters && expandedFilters) {
                Spacer(modifier = Modifier.height(10.dp))

                if (showViewFilters) {
                    CompactFilterRow(label = "Vista") {
                        DesktopLibraryView.entries.forEach { view ->
                            FilterButton(
                                label = view.label,
                                selected = libraryView == view,
                                onClick = { onLibraryViewChange(view) },
                                icon = view.icon(),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                CompactFilterRow(label = "Orden") {
                    SortMode.entries.forEach { mode ->
                        FilterButton(
                            label = mode.label,
                            selected = sortMode == mode,
                            onClick = { onSortModeChange(mode) },
                            icon = Icons.AutoMirrored.Filled.Sort,
                        )
                    }
                }

                if (showFolderFilters && availableFolders.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CompactFilterRow(label = "Carpetas") {
                        FilterButton(
                            label = "Todas",
                            selected = folderFilterPath == null,
                            onClick = { onFolderFilterChange(null) },
                            icon = Icons.Filled.FolderOpen,
                        )
                        availableFolders.forEach { folder ->
                            FilterButton(
                                label = "${folder.name} (${folder.songCount})",
                                selected = folderFilterPath == folder.path,
                                onClick = { onFolderFilterChange(folder.path) },
                                icon = Icons.Filled.Folder,
                            )
                        }
                    }
                }

                if (showFolderFilters) {
                    selectedFolderPath?.let { folderPath ->
                        Text(
                            modifier = Modifier.padding(top = 8.dp),
                            text = "Biblioteca: $folderPath",
                            color = colors.subtle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactFilterRow(
    label: String,
    content: @Composable () -> Unit,
) {
    val colors = LocalDesktopColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.width(76.dp),
            text = label,
            color = colors.muted,
            style = MaterialTheme.typography.labelLarge,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun PlaylistsWindowContent(
    playlists: List<MusicPlaylist>,
    selectedPlaylist: MusicPlaylist?,
    selectedPlaylistSongs: List<Song>,
    currentSong: Song?,
    favoriteSongIds: Set<Long>,
    newPlaylistName: String,
    isScanning: Boolean,
    hasSelectedFolder: Boolean,
    showArtwork: Boolean,
    onSelectPlaylist: (String) -> Unit,
    onNewPlaylistNameChange: (String) -> Unit,
    onCreatePlaylist: () -> Unit,
    onDeleteSelectedPlaylist: () -> Unit,
    onAddCurrentSongToPlaylist: (String) -> Unit,
    onPlaySong: (Song) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onAddSongToPlaylist: (String, Long) -> Unit,
    onRemoveSongFromPlaylist: (Long) -> Unit,
) {
    val colors = LocalDesktopColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(
            text = "Playlists",
            color = colors.text,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            modifier = Modifier.padding(top = 4.dp),
            text = "Crea y administra listas sin mezclar esta vista con canciones o ajustes.",
            color = colors.muted,
            style = MaterialTheme.typography.bodyLarge,
        )

        Spacer(modifier = Modifier.height(18.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colors.card),
            shape = RoundedCornerShape(22.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = newPlaylistName,
                        onValueChange = onNewPlaylistNameChange,
                        singleLine = true,
                        label = { Text("Nueva playlist") },
                    )
                    Button(onClick = onCreatePlaylist, enabled = newPlaylistName.isNotBlank()) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Crear")
                    }
                }

                if (currentSong != null && playlists.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Agregar canción actual a playlist",
                        color = colors.muted,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(playlists, key = { "add-${it.id}" }) { playlist ->
                            val alreadyAdded = currentSong.id in playlist.songIds
                            OutlinedButton(
                                enabled = !alreadyAdded,
                                onClick = { onAddCurrentSongToPlaylist(playlist.id) },
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (alreadyAdded) "${playlist.name}: agregada" else playlist.name)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (playlists.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = colors.card),
                shape = RoundedCornerShape(28.dp),
            ) {
                EmptyState(
                    title = "Aún no hay playlists",
                    body = "Crea una playlist y luego agrega la canción actual desde esta sección.",
                )
            }
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.width(300.dp),
                colors = CardDefaults.cardColors(containerColor = colors.card),
                shape = RoundedCornerShape(24.dp),
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(vertical = 10.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        PlaylistMenuRow(
                            playlist = playlist,
                            selected = selectedPlaylist?.id == playlist.id,
                            onClick = { onSelectPlaylist(playlist.id) },
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                if (selectedPlaylist == null) {
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        colors = CardDefaults.cardColors(containerColor = colors.card),
                        shape = RoundedCornerShape(28.dp),
                    ) {
                        EmptyState(
                            title = "Selecciona una playlist",
                            body = "Elige una lista de la izquierda para ver sus canciones.",
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedPlaylist.name,
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                text = if (selectedPlaylistSongs.size == 1) "1 canción" else "${selectedPlaylistSongs.size} canciones",
                                color = colors.muted,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        TextButton(onClick = onDeleteSelectedPlaylist) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Eliminar")
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    if (!isScanning && selectedPlaylistSongs.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxSize(),
                            colors = CardDefaults.cardColors(containerColor = colors.card),
                            shape = RoundedCornerShape(28.dp),
                        ) {
                            EmptyState(
                                title = "Playlist vacía",
                                body = "Reproduce una canción y agrégala desde el panel superior.",
                            )
                        }
                    } else {
                        LibraryContent(
                            modifier = Modifier.weight(1f),
                            songs = selectedPlaylistSongs,
                            isScanning = isScanning,
                            hasSelectedFolder = hasSelectedFolder,
                            currentSong = currentSong,
                            favoriteSongIds = favoriteSongIds,
                            playlists = playlists,
                            selectedPlaylist = selectedPlaylist,
                            libraryView = DesktopLibraryView.Songs,
                            showArtwork = showArtwork,
                            onPlaySong = onPlaySong,
                            onPlaySongGroup = { song, _ -> onPlaySong(song) },
                            onToggleFavorite = onToggleFavorite,
                            onAddSongToPlaylist = onAddSongToPlaylist,
                            onRemoveSongFromPlaylist = onRemoveSongFromPlaylist,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistMenuRow(
    playlist: MusicPlaylist,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalDesktopColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) colors.highlight else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(colors.cardAlt, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                color = if (selected) colors.accent else colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = if (playlist.songIds.size == 1) "1 canción" else "${playlist.songIds.size} canciones",
                color = colors.subtle,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Header(
    selectedFolder: Path?,
    songCount: Int,
    isScanning: Boolean,
    showSettings: Boolean,
    onSelectFolder: () -> Unit,
    onToggleSettings: () -> Unit,
) {
    val colors = LocalDesktopColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconMark(size = 54.dp, cornerRadius = 18.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SoftMusic Desktop",
                    color = colors.text,
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    modifier = Modifier.padding(top = 6.dp),
                    text = selectedFolder?.toString() ?: "Selecciona una carpeta para crear tu biblioteca local.",
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        Spacer(modifier = Modifier.width(20.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SummaryPill(text = if (songCount == 1) "1 canción" else "$songCount canciones")
            OutlinedButton(onClick = onToggleSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (showSettings) "Ocultar ajustes" else "Configuración")
            }
            Button(
                enabled = !isScanning,
                onClick = onSelectFolder,
            ) {
                Icon(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (selectedFolder == null) "Seleccionar carpeta" else "Cambiar carpeta")
            }
        }
    }
}

@Composable
private fun SummaryPill(text: String) {
    val colors = LocalDesktopColors.current
    Box(
        modifier = Modifier
            .background(colors.cardAlt, RoundedCornerShape(999.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = colors.text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun SettingsPanel(
    settings: DesktopSettings,
    onSettingsChange: (DesktopSettings) -> Unit,
) {
    val colors = LocalDesktopColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Configuración",
                color = colors.text,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Tema",
                color = colors.muted,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(DesktopThemeMode.entries, key = { it.name }) { themeMode ->
                    FilterButton(
                        label = themeMode.label,
                        selected = settings.themeMode == themeMode,
                        onClick = { onSettingsChange(settings.copy(themeMode = themeMode)) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSwitchRow(
                title = "Mostrar carátulas",
                description = "Usa carátulas embebidas cuando el archivo las tenga.",
                checked = settings.showArtwork,
                onCheckedChange = { checked -> onSettingsChange(settings.copy(showArtwork = checked)) },
            )

            SettingsSwitchRow(
                title = "Escanear al iniciar",
                description = "Vuelve a abrir automáticamente la última carpeta seleccionada.",
                checked = settings.scanOnStartup,
                onCheckedChange = { checked -> onSettingsChange(settings.copy(scanOnStartup = checked)) },
            )

            SettingsSwitchRow(
                title = "Modo DJ",
                description = "Mezcla automáticamente el final de una canción con el inicio de la siguiente.",
                checked = settings.djModeEnabled,
                onCheckedChange = { checked -> onSettingsChange(settings.copy(djModeEnabled = checked)) },
            )

            if (settings.djModeEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Duración de mezcla: ${settings.djMixDurationSeconds} s",
                    color = colors.text,
                    style = MaterialTheme.typography.titleMedium,
                )
                Slider(
                    value = settings.djMixDurationSeconds.toFloat(),
                    valueRange = MIN_DJ_MIX_SECONDS.toFloat()..MAX_DJ_MIX_SECONDS.toFloat(),
                    steps = MAX_DJ_MIX_SECONDS - MIN_DJ_MIX_SECONDS - 1,
                    onValueChange = { value ->
                        onSettingsChange(settings.copy(djMixDurationSeconds = value.toInt().coerceIn(MIN_DJ_MIX_SECONDS, MAX_DJ_MIX_SECONDS)))
                    },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Volumen inicial: ${settings.volumePercent}%",
                color = colors.text,
                style = MaterialTheme.typography.titleMedium,
            )
            Slider(
                value = settings.volumePercent.toFloat(),
                valueRange = 0f..100f,
                onValueChange = { value ->
                    onSettingsChange(settings.copy(volumePercent = value.toInt().coerceIn(0, 100)))
                },
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalDesktopColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.text,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                modifier = Modifier.padding(top = 3.dp),
                text = description,
                color = colors.muted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun FilterButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
) {
    @Composable
    fun Content() {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(label)
    }

    if (selected) {
        Button(onClick = onClick) {
            Content()
        }
    } else {
        OutlinedButton(onClick = onClick) {
            Content()
        }
    }
}

@Composable
private fun LibraryContent(
    modifier: Modifier = Modifier,
    songs: List<Song>,
    isScanning: Boolean,
    hasSelectedFolder: Boolean,
    currentSong: Song?,
    favoriteSongIds: Set<Long>,
    playlists: List<MusicPlaylist>,
    selectedPlaylist: MusicPlaylist?,
    libraryView: DesktopLibraryView,
    showArtwork: Boolean,
    onPlaySong: (Song) -> Unit,
    onPlaySongGroup: (Song, List<Song>) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onAddSongToPlaylist: (String, Long) -> Unit,
    onRemoveSongFromPlaylist: (Long) -> Unit,
) {
    val colors = LocalDesktopColors.current
    var selectedGroupKey by remember(libraryView) { mutableStateOf<String?>(null) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        shape = RoundedCornerShape(28.dp),
    ) {
        when {
            isScanning -> LoadingState()
            songs.isNotEmpty() -> when (libraryView) {
                DesktopLibraryView.Songs -> SongList(
                    songs = songs,
                    currentSong = currentSong,
                    favoriteSongIds = favoriteSongIds,
                    playlists = playlists,
                    selectedPlaylist = selectedPlaylist,
                    showArtwork = showArtwork,
                    onPlaySong = onPlaySong,
                    onToggleFavorite = onToggleFavorite,
                    onAddSongToPlaylist = onAddSongToPlaylist,
                    onRemoveSongFromPlaylist = onRemoveSongFromPlaylist,
                )
                DesktopLibraryView.Artists,
                DesktopLibraryView.Albums,
                DesktopLibraryView.Folders -> {
                    val groups = songs.groupedFor(libraryView)
                    val selectedGroup = groups.firstOrNull { it.key == selectedGroupKey }
                    if (selectedGroup != null) {
                        GroupSongsContent(
                            group = selectedGroup,
                            currentSong = currentSong,
                            favoriteSongIds = favoriteSongIds,
                            playlists = playlists,
                            selectedPlaylist = selectedPlaylist,
                            showArtwork = showArtwork,
                            onBack = { selectedGroupKey = null },
                            onPlaySong = { song -> onPlaySongGroup(song, selectedGroup.songs) },
                            onPlaySongGroup = { onPlaySongGroup(selectedGroup.songs.first(), selectedGroup.songs) },
                            onToggleFavorite = onToggleFavorite,
                            onAddSongToPlaylist = onAddSongToPlaylist,
                            onRemoveSongFromPlaylist = onRemoveSongFromPlaylist,
                        )
                    } else {
                        GroupList(
                            groups = groups,
                            showArtwork = showArtwork,
                            onOpenGroup = { group -> selectedGroupKey = group.key },
                            onPlaySongGroup = onPlaySongGroup,
                        )
                    }
                }
            }
            hasSelectedFolder -> EmptyState(
                title = "No encontré canciones",
                body = "La carpeta seleccionada no contiene archivos MP3, FLAC, WAV, OGG, OPUS, M4A u otros formatos compatibles.",
            )
            else -> EmptyState(
                title = "Biblioteca desktop lista",
                body = "Selecciona tu carpeta de música. SoftMusic escaneará subcarpetas y mostrará tus canciones locales.",
            )
        }
    }
}

@Composable
private fun LoadingState() {
    val colors = LocalDesktopColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = colors.accent)
        Text(
            modifier = Modifier.padding(top = 18.dp),
            text = "Escaneando biblioteca local...",
            color = colors.text,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    val colors = LocalDesktopColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(colors.cardAlt, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "SM",
                color = colors.accent,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
        }
        Text(
            modifier = Modifier.padding(top = 20.dp),
            text = title,
            color = colors.text,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            modifier = Modifier
                .padding(top = 10.dp)
                .fillMaxWidth(0.62f),
            text = body,
            color = colors.muted,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun StatusCard(title: String, body: String, accentColor: Color) {
    val colors = LocalDesktopColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.cardAlt),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(accentColor, CircleShape),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    color = colors.text,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    modifier = Modifier.padding(top = 4.dp),
                    text = body,
                    color = colors.muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun GroupSongsContent(
    group: DesktopMusicGroup,
    currentSong: Song?,
    favoriteSongIds: Set<Long>,
    playlists: List<MusicPlaylist>,
    selectedPlaylist: MusicPlaylist?,
    showArtwork: Boolean,
    onBack: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onPlaySongGroup: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onAddSongToPlaylist: (String, Long) -> Unit,
    onRemoveSongFromPlaylist: (Long) -> Unit,
) {
    val colors = LocalDesktopColors.current
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Volver")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.title,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = group.subtitle,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                enabled = group.songs.isNotEmpty(),
                onClick = onPlaySongGroup,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reproducir")
            }
        }
        HorizontalDivider(color = colors.divider)
        SongList(
            songs = group.songs,
            currentSong = currentSong,
            favoriteSongIds = favoriteSongIds,
            playlists = playlists,
            selectedPlaylist = selectedPlaylist,
            showArtwork = showArtwork,
            onPlaySong = onPlaySong,
            onToggleFavorite = onToggleFavorite,
            onAddSongToPlaylist = onAddSongToPlaylist,
            onRemoveSongFromPlaylist = onRemoveSongFromPlaylist,
        )
    }
}

@Composable
private fun GroupList(
    groups: List<DesktopMusicGroup>,
    showArtwork: Boolean,
    onOpenGroup: (DesktopMusicGroup) -> Unit,
    onPlaySongGroup: (Song, List<Song>) -> Unit,
) {
    val colors = LocalDesktopColors.current
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = "Grupo",
                color = colors.muted,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                modifier = Modifier.width(120.dp),
                text = "Canciones",
                color = colors.muted,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        HorizontalDivider(color = colors.divider)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(groups, key = { it.key }) { group ->
                GroupRow(
                    group = group,
                    showArtwork = showArtwork,
                    onOpenGroup = onOpenGroup,
                    onPlaySongGroup = onPlaySongGroup,
                )
            }
        }
    }
}

@Composable
private fun GroupRow(
    group: DesktopMusicGroup,
    showArtwork: Boolean,
    onOpenGroup: (DesktopMusicGroup) -> Unit,
    onPlaySongGroup: (Song, List<Song>) -> Unit,
) {
    val colors = LocalDesktopColors.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenGroup(group) }
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val groupSong = group.songs.firstOrNull()
            if (groupSong != null) {
                SongArtwork(song = groupSong, showArtwork = showArtwork)
            } else {
                GroupArtworkFallback(title = group.title)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.title,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    modifier = Modifier.padding(top = 3.dp),
                    text = group.subtitle,
                    color = colors.subtle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                modifier = Modifier.width(120.dp),
                text = "${group.songs.size}",
                color = colors.muted,
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(
                onClick = {
                    group.songs.firstOrNull()?.let { song -> onPlaySongGroup(song, group.songs) }
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Reproducir grupo",
                    tint = colors.accent,
                )
            }
        }
        HorizontalDivider(color = colors.divider)
    }
}

@Composable
private fun SongList(
    songs: List<Song>,
    currentSong: Song?,
    favoriteSongIds: Set<Long>,
    playlists: List<MusicPlaylist>,
    selectedPlaylist: MusicPlaylist?,
    showArtwork: Boolean,
    onPlaySong: (Song) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onAddSongToPlaylist: (String, Long) -> Unit,
    onRemoveSongFromPlaylist: (Long) -> Unit,
) {
    val colors = LocalDesktopColors.current
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = "Canción",
                color = colors.muted,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                modifier = Modifier.weight(0.58f),
                text = "Álbum",
                color = colors.muted,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                modifier = Modifier.width(148.dp),
                text = "Acciones",
                color = colors.muted,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                modifier = Modifier.width(76.dp),
                text = "Duración",
                color = colors.muted,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        HorizontalDivider(color = colors.divider)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(songs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    isCurrent = song.id == currentSong?.id,
                    isFavorite = song.id in favoriteSongIds,
                    playlists = playlists,
                    selectedPlaylist = selectedPlaylist,
                    showArtwork = showArtwork,
                    onPlaySong = onPlaySong,
                    onToggleFavorite = onToggleFavorite,
                    onAddSongToPlaylist = onAddSongToPlaylist,
                    onRemoveSongFromPlaylist = onRemoveSongFromPlaylist,
                )
            }
        }
    }
}

@Composable
private fun SongRow(
    song: Song,
    isCurrent: Boolean,
    isFavorite: Boolean,
    playlists: List<MusicPlaylist>,
    selectedPlaylist: MusicPlaylist?,
    showArtwork: Boolean,
    onPlaySong: (Song) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onAddSongToPlaylist: (String, Long) -> Unit,
    onRemoveSongFromPlaylist: (Long) -> Unit,
) {
    val colors = LocalDesktopColors.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isCurrent) colors.highlight else Color.Transparent)
                .clickable { onPlaySong(song) }
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SongArtwork(song = song, showArtwork = showArtwork)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = if (isCurrent) colors.accent else colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    modifier = Modifier.padding(top = 3.dp),
                    text = song.artist,
                    color = colors.subtle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Column(modifier = Modifier.weight(0.58f)) {
                Text(
                    text = song.album,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    modifier = Modifier.padding(top = 3.dp),
                    text = song.folderName,
                    color = colors.subtle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(
                modifier = Modifier.width(148.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onToggleFavorite(song.id) }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (isFavorite) "Quitar de favoritos" else "Agregar a favoritos",
                        tint = if (isFavorite) colors.danger else colors.muted,
                    )
                }
                AddSongToPlaylistButton(
                    song = song,
                    playlists = playlists,
                    onAddSongToPlaylist = onAddSongToPlaylist,
                )
                if (selectedPlaylist != null) {
                    IconButton(onClick = { onRemoveSongFromPlaylist(song.id) }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Quitar de playlist",
                            tint = colors.danger,
                        )
                    }
                }
            }
            Text(
                modifier = Modifier.width(76.dp),
                text = song.durationMs.formatSongDuration(),
                color = colors.muted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        HorizontalDivider(color = colors.divider)
    }
}

@Composable
private fun AddSongToPlaylistButton(
    song: Song,
    playlists: List<MusicPlaylist>,
    onAddSongToPlaylist: (String, Long) -> Unit,
) {
    val colors = LocalDesktopColors.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                contentDescription = "Agregar a playlist",
                tint = colors.muted,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (playlists.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("Crea una playlist primero") },
                    enabled = false,
                    onClick = {},
                )
            } else {
                playlists.forEach { playlist ->
                    val alreadyAdded = song.id in playlist.songIds
                    DropdownMenuItem(
                        text = { Text(if (alreadyAdded) "${playlist.name} (agregada)" else playlist.name) },
                        enabled = !alreadyAdded,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            onAddSongToPlaylist(playlist.id, song.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupArtworkFallback(title: String) {
    val colors = LocalDesktopColors.current
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(colors.cardAlt, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.firstOrNull()?.uppercaseChar()?.toString() ?: "S",
            color = colors.accent,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun SongArtwork(
    song: Song,
    showArtwork: Boolean,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
) {
    val colors = LocalDesktopColors.current
    val artwork = remember(song.artworkUri, showArtwork) {
        if (showArtwork) song.artworkUri?.loadArtworkBitmap() else null
    }
    if (artwork != null) {
        ComposeImage(
            bitmap = artwork,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp)),
        )
        return
    }

    Box(
        modifier = modifier
            .size(size)
            .background(colors.cardAlt, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = song.title.firstOrNull()?.uppercaseChar()?.toString() ?: "S",
            color = colors.accent,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun PlayerBar(
    currentSong: Song?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    volumePercent: Int,
    playbackMode: PlaybackMode,
    showArtwork: Boolean,
    playbackErrorMessage: String?,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
    onSeek: (Long) -> Unit,
) {
    val colors = LocalDesktopColors.current
    val volumeWarning = currentSong != null && volumePercent == 0
    val statusMessage = playbackErrorMessage
        ?: if (volumeWarning) "El volumen está en 0%" else currentSong?.displayPlaybackSubtitle()
        ?: "Selecciona una canción de la biblioteca"
    val hasPlaybackWarning = playbackErrorMessage != null || volumeWarning
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (currentSong != null) {
                    SongArtwork(
                        song = currentSong,
                        showArtwork = showArtwork,
                        size = 56.dp,
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentSong?.title ?: "Nada reproduciéndose",
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        modifier = Modifier.padding(top = 3.dp),
                        text = statusMessage,
                        color = if (hasPlaybackWarning) colors.danger else colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    modifier = Modifier.size(48.dp),
                    onClick = onPrevious,
                    enabled = currentSong != null,
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = if (currentSong != null) colors.text else colors.subtle.copy(alpha = 0.55f),
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    onClick = onTogglePlayPause,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(
                    modifier = Modifier.size(48.dp),
                    onClick = onNext,
                    enabled = currentSong != null,
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Siguiente",
                        tint = if (currentSong != null) colors.text else colors.subtle.copy(alpha = 0.55f),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = positionMs.formatPlaybackTime(),
                    color = colors.subtle,
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    value = if (durationMs > 0L) positionMs.toFloat() / durationMs.toFloat() else 0f,
                    enabled = currentSong != null && durationMs > 0L,
                    onValueChange = { progress ->
                        if (durationMs > 0L) {
                            onSeek((durationMs * progress.coerceIn(0f, 1f)).toLong())
                        }
                    },
                )
                Text(
                    text = durationMs.formatPlaybackTime(),
                    color = colors.subtle,
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Modo",
                    color = colors.subtle,
                    style = MaterialTheme.typography.labelMedium,
                )
                PlaybackMode.entries.forEach { mode ->
                    FilterButton(
                        label = mode.shortDesktopLabel(),
                        selected = playbackMode == mode,
                        onClick = { onPlaybackModeChange(mode) },
                        icon = mode.icon(),
                    )
                }
            }
        }
    }
}

private data class DesktopFolder(
    val path: String,
    val name: String,
    val songCount: Int,
)

private data class DesktopMusicGroup(
    val key: String,
    val title: String,
    val subtitle: String,
    val songs: List<Song>,
)

private fun String.toExistingDirectoryPath(): Path? = runCatching {
    Path.of(this).toAbsolutePath().normalize().takeIf { Files.isDirectory(it) }
}.getOrNull()

private fun List<Song>.filteredForLibrary(
    folderFilterPath: String?,
    searchQuery: String,
): List<Song> {
    val normalizedQuery = searchQuery.normalizedSearchText()
    return asSequence()
        .filter { song -> folderFilterPath == null || song.folderPath == folderFilterPath }
        .filter { song ->
            normalizedQuery.isBlank() || listOf(song.title, song.artist, song.album, song.folderName)
                .any { value -> value.normalizedSearchText().contains(normalizedQuery) }
        }
        .toList()
}

private fun List<Song>.displayedFor(
    favoriteSongIds: Set<Long>,
    showFavoritesOnly: Boolean,
    selectedPlaylist: MusicPlaylist?,
): List<Song> = when {
    selectedPlaylist != null -> selectedPlaylist.songsFrom(this)
    showFavoritesOnly -> filter { it.id in favoriteSongIds }
    else -> this
}

private fun MusicPlaylist.songsFrom(songs: List<Song>): List<Song> {
    val songsById = songs.associateBy { it.id }
    return songIds.mapNotNull(songsById::get)
}

private fun List<Song>.sortedFor(sortMode: SortMode): List<Song> = when (sortMode) {
    SortMode.Recent -> sortedWith(compareByDescending<Song> { it.dateAddedSeconds }.thenBy { it.title.normalizedSearchText() })
    SortMode.Title -> sortedWith(compareBy<Song> { it.title.normalizedSearchText() }.thenBy { it.artist.normalizedSearchText() })
}

private fun List<Song>.toDesktopFolders(): List<DesktopFolder> = groupBy { it.folderPath }
    .map { (path, songs) ->
        DesktopFolder(
            path = path,
            name = songs.firstOrNull()?.folderName ?: path.substringAfterLast('/'),
            songCount = songs.size,
        )
    }
    .sortedWith(compareBy<DesktopFolder> { it.name.normalizedSearchText() }.thenBy { it.path.normalizedSearchText() })

private fun List<Song>.groupedFor(libraryView: DesktopLibraryView): List<DesktopMusicGroup> = when (libraryView) {
    DesktopLibraryView.Songs -> emptyList()
    DesktopLibraryView.Artists -> groupBy { it.artist.ifBlank { "Artista desconocido" } }
        .map { (artist, songs) ->
            DesktopMusicGroup(
                key = "artist:$artist",
                title = artist,
                subtitle = songs.distinctBy { it.album }.size.let { count -> if (count == 1) "1 álbum" else "$count álbumes" },
                songs = songs.sortedFor(SortMode.Title),
            )
        }
        .sortedBy { it.title.normalizedSearchText() }
    DesktopLibraryView.Albums -> groupBy { "${it.album}\u0000${it.artist}" }
        .map { (_, songs) ->
            val firstSong = songs.first()
            DesktopMusicGroup(
                key = "album:${firstSong.album}:${firstSong.artist}",
                title = firstSong.album,
                subtitle = firstSong.artist,
                songs = songs.sortedFor(SortMode.Title),
            )
        }
        .sortedBy { it.title.normalizedSearchText() }
    DesktopLibraryView.Folders -> groupBy { it.folderPath }
        .map { (path, songs) ->
            DesktopMusicGroup(
                key = "folder:$path",
                title = songs.firstOrNull()?.folderName ?: path.substringAfterLast('/'),
                subtitle = path,
                songs = songs.sortedFor(SortMode.Title),
            )
        }
        .sortedBy { it.title.normalizedSearchText() }
}

private fun List<Song>.groupQueueFor(libraryView: DesktopLibraryView, song: Song): List<Song> = when (libraryView) {
    DesktopLibraryView.Songs -> this
    DesktopLibraryView.Artists -> filter { it.artist == song.artist }.sortedFor(SortMode.Title)
    DesktopLibraryView.Albums -> filter { it.album == song.album && it.artist == song.artist }.sortedFor(SortMode.Title)
    DesktopLibraryView.Folders -> filter { it.folderPath == song.folderPath }.sortedFor(SortMode.Title)
}.ifEmpty { listOf(song) }

private fun String.normalizedSearchText(): String {
    val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
    return normalized.lowercase().trim()
}

private fun List<Song>.relativeTo(currentSong: Song?, offset: Int): Song? {
    if (isEmpty()) return null
    val currentIndex = currentSong?.let { song -> indexOfFirst { it.id == song.id } } ?: -1
    if (currentIndex < 0) return firstOrNull()
    val nextIndex = (currentIndex + offset).floorMod(size)
    return getOrNull(nextIndex)
}

private fun List<Song>.nextSongFor(
    currentSong: Song?,
    playbackMode: PlaybackMode,
    manual: Boolean,
): Song? {
    if (isEmpty()) return null
    if (currentSong == null) return firstOrNull()
    return when (playbackMode) {
        PlaybackMode.RepeatCurrent -> if (manual) relativeTo(currentSong, 1) else currentSong
        PlaybackMode.RepeatList -> relativeTo(currentSong, 1)
        PlaybackMode.Shuffle -> randomNextAfter(currentSong)
        PlaybackMode.Ordered -> nextOrderedAfter(currentSong)
    }
}

private fun List<Song>.previousSongFor(currentSong: Song?, playbackMode: PlaybackMode): Song? {
    if (playbackMode != PlaybackMode.Ordered) return relativeTo(currentSong, -1)
    if (isEmpty()) return null
    val currentIndex = currentSong?.let { song -> indexOfFirst { it.id == song.id } } ?: -1
    if (currentIndex < 0) return firstOrNull()
    return getOrNull((currentIndex - 1).coerceAtLeast(0))
}

private fun List<Song>.nextOrderedAfter(currentSong: Song): Song? {
    val currentIndex = indexOfFirst { it.id == currentSong.id }
    if (currentIndex < 0) return firstOrNull()
    val nextIndex = currentIndex + 1
    return getOrNull(nextIndex)
}

private fun List<Song>.randomNextAfter(currentSong: Song): Song? {
    if (size <= 1) return currentSong
    return filterNot { it.id == currentSong.id }.randomOrNull() ?: currentSong
}

private fun PlaybackMode.shortDesktopLabel(): String = when (this) {
    PlaybackMode.Ordered -> "Orden"
    PlaybackMode.RepeatList -> "Repetir lista"
    PlaybackMode.RepeatCurrent -> "Repetir canción"
    PlaybackMode.Shuffle -> "Aleatorio"
}

private fun PlaybackMode.icon(): ImageVector = when (this) {
    PlaybackMode.Ordered -> Icons.AutoMirrored.Filled.Sort
    PlaybackMode.RepeatList -> Icons.Filled.Repeat
    PlaybackMode.RepeatCurrent -> Icons.Filled.RepeatOne
    PlaybackMode.Shuffle -> Icons.Filled.Shuffle
}

private fun DesktopLibraryView.icon(): ImageVector = when (this) {
    DesktopLibraryView.Songs -> Icons.Filled.MusicNote
    DesktopLibraryView.Artists -> Icons.Filled.Person
    DesktopLibraryView.Albums -> Icons.Filled.Album
    DesktopLibraryView.Folders -> Icons.Filled.Folder
}

private fun Int.floorMod(size: Int): Int = ((this % size) + size) % size

private fun Long.formatPlaybackTime(): String {
    val totalSeconds = (this / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun Long.formatSongDuration(): String = if (this > 0L) {
    formatPlaybackTime()
} else {
    "--:--"
}

private fun Song.displayPlaybackSubtitle(): String {
    val metadata = listOf(artist, album)
        .filter { it.isNotBlank() && it != "Artista desconocido" && it != "Álbum desconocido" }
        .joinToString(" • ")
    return metadata.ifBlank { folderName }
}

private fun String.loadArtworkBitmap(): ImageBitmap? = runCatching {
    val bytes = Files.readAllBytes(Path.of(URI.create(this)))
    SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
}.getOrNull()

private fun loadDesktopAppIconPainter(): Painter = AndroidLauncherIconPainter()

private class AndroidLauncherIconPainter : Painter() {
    override val intrinsicSize: Size = Size(ANDROID_ICON_VIEWPORT, ANDROID_ICON_VIEWPORT)

    override fun DrawScope.onDraw() {
        val side = min(size.width, size.height)
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        val scale = side / ANDROID_ICON_VIEWPORT

        withTransform({
            translate(left, top)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            drawRect(
                color = ANDROID_ICON_BACKGROUND,
                size = Size(ANDROID_ICON_VIEWPORT, ANDROID_ICON_VIEWPORT),
            )
            drawCircle(
                brush = ANDROID_ICON_GRADIENT,
                radius = 44f,
                center = Offset(54f, 54f),
            )
            drawPath(path = noteStemPath, color = ANDROID_ICON_FOREGROUND)
            drawPath(path = noteBarPath, color = ANDROID_ICON_FOREGROUND)
            drawPath(path = noteRightPath, color = ANDROID_ICON_FOREGROUND)
        }
    }

    private companion object {
        const val ANDROID_ICON_VIEWPORT = 108f
        val ANDROID_ICON_BACKGROUND = Color(0xFF080A08)
        val ANDROID_ICON_FOREGROUND = Color(0xFFE6E8EF)
        val ANDROID_ICON_GRADIENT = Brush.linearGradient(
            colorStops = arrayOf(
                0f to Color(0xFFFF3D81),
                0.28f to Color(0xFFFFB000),
                0.55f to Color(0xFF28E66D),
                0.78f to Color(0xFF18B8FF),
                1f to Color(0xFF7C4DFF),
            ),
            start = Offset(14f, 16f),
            end = Offset(92f, 90f),
        )
        val noteStemPath: ComposePath = PathParser()
            .parsePathString("M39,30h10v34.5c0,6.4 -5.5,11.5 -12.5,11.5S24,70.9 24,64.5 29.5,53 36.5,53c2,0 3.8,0.4 5.5,1.2V30z")
            .toPath()
        val noteBarPath: ComposePath = PathParser()
            .parsePathString("M49,30h29v10H49z")
            .toPath()
        val noteRightPath: ComposePath = PathParser()
            .parsePathString("M68,30h10v43c0,6.1 -5.6,11 -12.5,11S53,79.1 53,73 58.6,62 65.5,62c1.9,0 3.7,0.4 5.5,1.1V30z")
            .toPath()
    }
}

private const val MAX_PLAYLIST_NAME_LENGTH = 50
private const val PLAYBACK_START_TIMEOUT_MS = 3_000L
private const val MIN_DJ_MIX_SECONDS = 5
private const val MAX_DJ_MIX_SECONDS = 8
private const val MIN_DJ_SONG_EXTRA_MS = 2_000L
private const val MIN_DJ_FADE_MS = 1_000L
private const val DJ_PRELOAD_LEAD_MS = 2_000L
private const val DJ_HANDOFF_SAFETY_MS = 700L
private const val ESTIMATED_PLAYBACK_END_GRACE_MS = 700L

private fun chooseMusicFolder(): Path? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Selecciona tu carpeta de música"
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        approveButtonText = "Escanear"
        isAcceptAllFileFilterUsed = false
    }
    val result = chooser.showOpenDialog(null)
    return if (result == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile?.toPath()
    } else {
        null
    }
}
