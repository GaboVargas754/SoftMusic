@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.softmusic.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoubleArrow
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.softmusic.app.data.MusicFolder
import com.softmusic.app.data.MusicPlaylist
import com.softmusic.app.data.Song
import com.softmusic.app.data.isIncludedBySmallAudioFilter
import com.softmusic.app.data.orderedSongsFrom
import com.softmusic.app.player.DjMixMode
import com.softmusic.app.player.PlaybackMode
import com.softmusic.app.player.PlaybackProgressState
import com.softmusic.app.player.PlaybackQueueSource
import com.softmusic.app.player.PlayerUiState
import com.softmusic.app.player.SortMode
import com.softmusic.app.ui.theme.AppColorPalette
import com.softmusic.app.ui.theme.AppThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.text.DateFormat
import java.text.Normalizer
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private val LocalHighPerformanceMode = compositionLocalOf { false }
private const val MAX_PLAYLIST_NAME_LENGTH = 50
private const val FULL_DISC_ROTATION_DEGREES = 360f
private const val DISC_ROTATION_DURATION_MS = 5_500f
private const val VINYL_VIEWBOX_SIZE = 300f

private enum class LibraryDestination {
    Music,
    Search,
    Favorites,
    Playlists,
    Settings,
}

private enum class MusicLibraryView(val label: String) {
    Songs("Canciones"),
    Artists("Artistas"),
    Albums("Álbumes"),
}

private data class MusicGroup(
    val key: String,
    val title: String,
    val subtitle: String,
    val songs: List<Song>,
)

private data class TransientAlert(
    val id: Long,
    val message: String,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoftMusicApp(
    uiState: PlayerUiState,
    playbackProgressState: StateFlow<PlaybackProgressState>,
    hasAudioPermission: Boolean,
    showAudioPermissionRationale: Boolean,
    audioPermissionPermanentlyDenied: Boolean,
    hasNotificationPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRefreshLibrary: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onPlayList: () -> Unit,
    onPlayQueuedSong: (Song, List<Song>, PlaybackQueueSource, String?) -> Unit,
    onPlayQueue: (List<Song>, PlaybackQueueSource, String?) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onPlayNext: (Song) -> Unit,
    onPlayAtEnd: (Song) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onAddSongToPlaylist: (String, Long) -> Unit,
    onRemoveSongFromPlaylist: (String, Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
    onSortModeChange: (SortMode) -> Unit,
    onFolderChange: (String?) -> Unit,
    themeMode: AppThemeMode,
    colorPalette: AppColorPalette,
    highPerformanceMode: Boolean,
    defaultFolderPath: String?,
    defaultPlaybackMode: PlaybackMode,
    defaultSortMode: SortMode,
    fontScale: Float,
    djModeEnabled: Boolean,
    djMixMode: DjMixMode,
    djMixDurationSeconds: Int,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onColorPaletteChange: (AppColorPalette) -> Unit,
    onHighPerformanceModeChange: (Boolean) -> Unit,
    onDefaultFolderChange: (String?) -> Unit,
    onDefaultPlaybackModeChange: (PlaybackMode) -> Unit,
    onDefaultSortModeChange: (SortMode) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onExcludeSmallAudiosChange: (Boolean) -> Unit,
    onHiddenFolderChange: (String, Boolean) -> Unit,
    onDjModeChange: (Boolean) -> Unit,
    onDjMixModeChange: (DjMixMode) -> Unit,
    onDjMixDurationChange: (Int) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenDonation: () -> Unit,
    isCheckingForUpdates: Boolean,
    updateCheckStatus: String?,
    onCheckForUpdates: () -> Unit,
    onCloseApp: () -> Unit,
) {
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var destination by rememberSaveable { mutableStateOf(LibraryDestination.Music) }
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val visibleLibrarySongs = remember(uiState.songs, uiState.hiddenFolderPaths, uiState.excludeSmallAudios) {
        uiState.visibleLibrarySongs()
    }
    val favoriteSongs = remember(uiState.songs, uiState.favoriteSongIds, uiState.sortMode, uiState.hiddenFolderPaths, uiState.excludeSmallAudios) {
        uiState.favoriteSongs()
    }
    var transientAlert by remember { mutableStateOf<TransientAlert?>(null) }

    fun showTransientAlert(
        message: String,
        actionLabel: String? = null,
        action: (() -> Unit)? = null,
    ) {
        transientAlert = TransientAlert(System.nanoTime(), message, actionLabel, action)
    }

    fun toggleFavoriteWithAlert(songId: Long) {
        val isAdding = songId !in uiState.favoriteSongIds
        onToggleFavorite(songId)
        if (isAdding) {
            showTransientAlert("Se agregó a Favoritos")
        } else {
            showTransientAlert(
                message = "Se quitó de Favoritos",
                actionLabel = "Deshacer",
                action = { onToggleFavorite(songId) },
            )
        }
    }

    fun createPlaylistWithAlert(name: String): Boolean {
        val cleanName = name.trim()
        return when {
            cleanName.isBlank() -> {
                showTransientAlert("Escribe un nombre para la playlist")
                false
            }
            cleanName.length > MAX_PLAYLIST_NAME_LENGTH -> {
                showTransientAlert("El nombre es demasiado largo")
                false
            }
            uiState.playlists.any { it.name.normalizedSearchText() == cleanName.normalizedSearchText() } -> {
                showTransientAlert("Ya existe una playlist con ese nombre")
                false
            }
            else -> {
                onCreatePlaylist(cleanName)
                showTransientAlert("Playlist creada")
                true
            }
        }
    }

    fun addSongToPlaylistWithAlert(playlistId: String, songId: Long) {
        onAddSongToPlaylist(playlistId, songId)
        showTransientAlert("Se agregó a Playlist")
    }

    fun removeSongFromPlaylistWithUndo(playlistId: String, songId: Long) {
        onRemoveSongFromPlaylist(playlistId, songId)
        showTransientAlert(
            message = "Se quitó de la playlist",
            actionLabel = "Deshacer",
            action = { onAddSongToPlaylist(playlistId, songId) },
        )
    }

    LaunchedEffect(transientAlert?.id) {
        val alertId = transientAlert?.id ?: return@LaunchedEffect
        delay(2_000L)
        if (transientAlert?.id == alertId) {
            transientAlert = null
        }
    }

    BackHandler(enabled = showPlayer) {
        showPlayer = false
    }
    BackHandler(enabled = showSettings) {
        showSettings = false
    }
    BackHandler(enabled = destination != LibraryDestination.Music && !showPlayer && !showSettings) {
        destination = LibraryDestination.Music
    }

    CompositionLocalProvider(LocalHighPerformanceMode provides highPerformanceMode) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
            bottomBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (uiState.currentSong != null) {
                        MiniPlayer(
                            uiState = uiState,
                            playbackProgressState = playbackProgressState,
                            onOpenPlayer = { showPlayer = true },
                            onPrevious = onPrevious,
                            onTogglePlayPause = onTogglePlayPause,
                            onNext = onNext,
                        )
                    }
                    SpotifyBottomNavigation(
                        selectedDestination = destination,
                        onDestinationChange = { selectedDestination ->
                            destination = selectedDestination
                            showSettings = false
                        },
                    )
                }
            },
        ) { padding ->
            if (!hasAudioPermission) {
                PermissionPanel(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    showRationale = showAudioPermissionRationale,
                    permanentlyDenied = audioPermissionPermanentlyDenied,
                    onRequestPermission = onRequestPermission,
                    onOpenAppSettings = onOpenAppSettings,
                )
            } else when (destination) {
                LibraryDestination.Music -> MusicLibraryScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    uiState = uiState,
                    hasAudioPermission = hasAudioPermission,
                    onRequestPermission = onRequestPermission,
                    onRefreshLibrary = onRefreshLibrary,
                    onPlaySong = onPlaySong,
                    onPlayList = onPlayList,
                    onPlayQueuedSong = onPlayQueuedSong,
                    onPlayQueue = onPlayQueue,
                    onToggleFavorite = ::toggleFavoriteWithAlert,
                    onPlayNext = onPlayNext,
                    onPlayAtEnd = onPlayAtEnd,
                    onAddSongToPlaylist = ::addSongToPlaylistWithAlert,
                    onPlaybackModeChange = onPlaybackModeChange,
                    onSortModeChange = onSortModeChange,
                    onFolderChange = onFolderChange,
                    onOpenSettings = { destination = LibraryDestination.Settings },
                )
                LibraryDestination.Search -> SearchLibraryScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    uiState = uiState,
                    onPlayQueuedSong = onPlayQueuedSong,
                    onPlayQueue = onPlayQueue,
                    onToggleFavorite = ::toggleFavoriteWithAlert,
                    onPlayNext = onPlayNext,
                    onPlayAtEnd = onPlayAtEnd,
                    onAddSongToPlaylist = ::addSongToPlaylistWithAlert,
                )
                LibraryDestination.Favorites -> FavoritesLibraryScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    uiState = uiState,
                    songs = favoriteSongs,
                    hasAudioPermission = hasAudioPermission,
                    onRequestPermission = onRequestPermission,
                    onPlaySong = { song ->
                        onPlayQueuedSong(song, favoriteSongs, PlaybackQueueSource.Favorites, null)
                    },
                    onPlayList = { onPlayQueue(favoriteSongs, PlaybackQueueSource.Favorites, null) },
                    onToggleFavorite = ::toggleFavoriteWithAlert,
                    onPlayNext = onPlayNext,
                    onPlayAtEnd = onPlayAtEnd,
                    onAddSongToPlaylist = ::addSongToPlaylistWithAlert,
                    onPlaybackModeChange = onPlaybackModeChange,
                    onOpenMusic = { destination = LibraryDestination.Music },
                )
                LibraryDestination.Playlists -> PlaylistsLibraryScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    uiState = uiState,
                    hasAudioPermission = hasAudioPermission,
                    onRequestPermission = onRequestPermission,
                    onCreatePlaylist = ::createPlaylistWithAlert,
                    onDeletePlaylist = onDeletePlaylist,
                    onRemoveSongFromPlaylist = ::removeSongFromPlaylistWithUndo,
                    onPlayQueuedSong = onPlayQueuedSong,
                    onPlayQueue = onPlayQueue,
                    onToggleFavorite = ::toggleFavoriteWithAlert,
                    onPlayNext = onPlayNext,
                    onPlayAtEnd = onPlayAtEnd,
                    onAddSongToPlaylist = ::addSongToPlaylistWithAlert,
                    onPlaybackModeChange = onPlaybackModeChange,
                    onOpenMusic = { destination = LibraryDestination.Music },
                )
                LibraryDestination.Settings -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    SettingsSheet(
                        themeMode = themeMode,
                        colorPalette = colorPalette,
                        highPerformanceMode = highPerformanceMode,
                        hasAudioPermission = hasAudioPermission,
                        audioPermissionPermanentlyDenied = audioPermissionPermanentlyDenied,
                        hasNotificationPermission = hasNotificationPermission,
                        allFolders = uiState.allFolders,
                        folders = uiState.folders,
                        defaultFolderPath = defaultFolderPath,
                        defaultPlaybackMode = defaultPlaybackMode,
                        defaultSortMode = defaultSortMode,
                        fontScale = fontScale,
                        hiddenFolderPaths = uiState.hiddenFolderPaths,
                        excludeSmallAudios = uiState.excludeSmallAudios,
                        djModeEnabled = djModeEnabled,
                        djMixMode = djMixMode,
                        djMixDurationSeconds = djMixDurationSeconds,
                        onThemeModeChange = onThemeModeChange,
                        onColorPaletteChange = onColorPaletteChange,
                        onHighPerformanceModeChange = onHighPerformanceModeChange,
                        onRequestAudioPermission = onRequestPermission,
                        onRequestNotificationPermission = onRequestNotificationPermission,
                        onDefaultFolderChange = onDefaultFolderChange,
                        onDefaultPlaybackModeChange = onDefaultPlaybackModeChange,
                        onDefaultSortModeChange = onDefaultSortModeChange,
                        onFontScaleChange = onFontScaleChange,
                        onExcludeSmallAudiosChange = onExcludeSmallAudiosChange,
                        onHiddenFolderChange = onHiddenFolderChange,
                        onDjModeChange = onDjModeChange,
                        onDjMixModeChange = onDjMixModeChange,
                        onDjMixDurationChange = onDjMixDurationChange,
                        onOpenNotificationSettings = onOpenNotificationSettings,
                        onOpenAppSettings = onOpenAppSettings,
                        onOpenDonation = onOpenDonation,
                        isCheckingForUpdates = isCheckingForUpdates,
                        updateCheckStatus = updateCheckStatus,
                        onCheckForUpdates = onCheckForUpdates,
                    )
                }
            }
        }

        if (showPlayer && uiState.currentSong != null) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center),
                color = MaterialTheme.colorScheme.background,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                FullPlayer(
                    uiState = uiState,
                    playbackProgressState = playbackProgressState,
                    isFavorite = uiState.currentSong?.id in uiState.favoriteSongIds,
                    playlists = uiState.playlists,
                    onTogglePlayPause = onTogglePlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSeek = onSeek,
                    onToggleFavorite = {
                        uiState.currentSong?.let { toggleFavoriteWithAlert(it.id) }
                    },
                    onAddToPlaylist = { playlistId ->
                        uiState.currentSong?.let { addSongToPlaylistWithAlert(playlistId, it.id) }
                    },
                    onPlaybackModeChange = onPlaybackModeChange,
                    onClose = { showPlayer = false },
                )
                transientAlert?.let { alert ->
                    TemporaryAlert(
                        message = alert.message,
                        actionLabel = alert.actionLabel,
                        onAction = {
                            alert.action?.invoke()
                            transientAlert = null
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                    )
                }
                }
            }
        }

        if (showSettings) {
            ModalBottomSheet(
                onDismissRequest = { showSettings = false },
                sheetState = settingsSheetState,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                SettingsSheet(
                    themeMode = themeMode,
                    colorPalette = colorPalette,
                    highPerformanceMode = highPerformanceMode,
                    hasAudioPermission = hasAudioPermission,
                    audioPermissionPermanentlyDenied = audioPermissionPermanentlyDenied,
                    hasNotificationPermission = hasNotificationPermission,
                    allFolders = uiState.allFolders,
                    folders = uiState.folders,
                    defaultFolderPath = defaultFolderPath,
                    defaultPlaybackMode = defaultPlaybackMode,
                    defaultSortMode = defaultSortMode,
                    fontScale = fontScale,
                    hiddenFolderPaths = uiState.hiddenFolderPaths,
                    excludeSmallAudios = uiState.excludeSmallAudios,
                    djModeEnabled = djModeEnabled,
                    djMixMode = djMixMode,
                    djMixDurationSeconds = djMixDurationSeconds,
                    onThemeModeChange = onThemeModeChange,
                    onColorPaletteChange = onColorPaletteChange,
                    onHighPerformanceModeChange = onHighPerformanceModeChange,
                    onRequestAudioPermission = onRequestPermission,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onDefaultFolderChange = onDefaultFolderChange,
                    onDefaultPlaybackModeChange = onDefaultPlaybackModeChange,
                    onDefaultSortModeChange = onDefaultSortModeChange,
                    onFontScaleChange = onFontScaleChange,
                    onExcludeSmallAudiosChange = onExcludeSmallAudiosChange,
                    onHiddenFolderChange = onHiddenFolderChange,
                    onDjModeChange = onDjModeChange,
                    onDjMixModeChange = onDjMixModeChange,
                    onDjMixDurationChange = onDjMixDurationChange,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onOpenAppSettings = onOpenAppSettings,
                    onOpenDonation = onOpenDonation,
                    isCheckingForUpdates = isCheckingForUpdates,
                    updateCheckStatus = updateCheckStatus,
                    onCheckForUpdates = onCheckForUpdates,
                )
            }
        }

            if (!showPlayer) transientAlert?.let { alert ->
                TemporaryAlert(
                    message = alert.message,
                    actionLabel = alert.actionLabel,
                    onAction = {
                        alert.action?.invoke()
                        transientAlert = null
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = 20.dp,
                            end = 20.dp,
                            bottom = if (uiState.currentSong != null) 116.dp else 24.dp,
                        ),
                )
            }
        }
    }
}

@Composable
private fun TemporaryAlert(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.semantics {
            liveRegion = LiveRegionMode.Polite
        },
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            if (actionLabel != null && onAction != null) {
                Text(
                    text = actionLabel,
                    modifier = Modifier.clickable(onClick = onAction),
                    color = MaterialTheme.colorScheme.inversePrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun SpotifyBottomNavigation(
    selectedDestination: LibraryDestination,
    onDestinationChange: (LibraryDestination) -> Unit,
) {
    val destinations = listOf(
        LibraryDestination.Music,
        LibraryDestination.Search,
        LibraryDestination.Favorites,
        LibraryDestination.Playlists,
        LibraryDestination.Settings,
    )
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        destinations.forEach { destination ->
            NavigationBarItem(
                selected = selectedDestination == destination,
                onClick = { onDestinationChange(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon(),
                        contentDescription = destination.label(),
                    )
                },
                label = {
                    Text(
                        text = destination.label(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onBackground,
                    selectedTextColor = MaterialTheme.colorScheme.onBackground,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

private fun LibraryDestination.label(): String = when (this) {
    LibraryDestination.Music -> "Biblioteca"
    LibraryDestination.Search -> "Buscar"
    LibraryDestination.Favorites -> "Favoritos"
    LibraryDestination.Playlists -> "Playlists"
    LibraryDestination.Settings -> "Ajustes"
}

private fun LibraryDestination.icon(): ImageVector = when (this) {
    LibraryDestination.Music -> Icons.Filled.LibraryMusic
    LibraryDestination.Search -> Icons.Filled.Search
    LibraryDestination.Favorites -> Icons.Filled.Star
    LibraryDestination.Playlists -> Icons.AutoMirrored.Filled.QueueMusic
    LibraryDestination.Settings -> Icons.Filled.Settings
}

@Composable
private fun ModuleTitle(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(if (compact) 28.dp else 34.dp),
        )
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LibraryHomeScreen(
    modifier: Modifier = Modifier,
    songCount: Int,
    favoriteCount: Int,
    playlistCount: Int,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpenMusic: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onCloseApp: () -> Unit,
) {
    var showCloseConfirmation by remember { mutableStateOf(false) }
    val backgroundModifier = modifier.background(MaterialTheme.colorScheme.background)

    Box(modifier = backgroundModifier) {
        if (!hasAudioPermission) {
            PermissionPanel(onRequestPermission = onRequestPermission)
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ModuleTitle(
                        icon = Icons.Filled.LibraryMusic,
                        title = "Biblioteca",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Elige una sección para continuar",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            LibraryOptionCard(
                title = "Mi Música",
                subtitle = "$songCount canciones locales",
                icon = Icons.Filled.LibraryMusic,
                enabled = true,
                onClick = onOpenMusic,
            )
            LibraryOptionCard(
                title = "Favoritos",
                subtitle = favoriteCount.favoriteCountLabel(),
                icon = Icons.Filled.Star,
                enabled = true,
                onClick = onOpenFavorites,
            )
            LibraryOptionCard(
                title = "Playlist",
                subtitle = playlistCount.playlistCountLabel(),
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                enabled = true,
                onClick = onOpenPlaylists,
            )
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = { showCloseConfirmation = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Cerrar")
            }
        }

        if (showCloseConfirmation) {
            AlertDialog(
                onDismissRequest = { showCloseConfirmation = false },
                title = { Text("Cerrar aplicación") },
                text = { Text("Se cerrará completamente la aplicación, ¿Estás seguro?") },
                confirmButton = {
                    Button(
                        onClick = onCloseApp,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text("Cerrar")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showCloseConfirmation = false }) {
                        Text("Cancelar")
                    }
                },
            )
        }
    }
}

@Composable
private fun LibraryOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val highPerformanceMode = LocalHighPerformanceMode.current
    val contentAlpha = if (enabled) 1f else 0.56f
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highPerformanceMode) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.16f else 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                )
            }
        }
    }
}

@Composable
private fun MusicLibraryScreen(
    modifier: Modifier = Modifier,
    uiState: PlayerUiState,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRefreshLibrary: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onPlayList: () -> Unit,
    onPlayQueuedSong: (Song, List<Song>, PlaybackQueueSource, String?) -> Unit,
    onPlayQueue: (List<Song>, PlaybackQueueSource, String?) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onPlayNext: (Song) -> Unit,
    onPlayAtEnd: (Song) -> Unit,
    onAddSongToPlaylist: (String, Long) -> Unit,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
    onSortModeChange: (SortMode) -> Unit,
    onFolderChange: (String?) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var selectedView by rememberSaveable { mutableStateOf(MusicLibraryView.Songs) }
    var selectedGroupTitle by rememberSaveable(selectedView, uiState.selectedFolderPath) { mutableStateOf<String?>(null) }
    val groups = remember(uiState.visibleSongs, selectedView, uiState.sortMode) {
        uiState.visibleSongs.toMusicGroups(selectedView, uiState.sortMode)
    }
    val playbackGroups = remember(uiState.visibleSongs, selectedView, uiState.sortMode) {
        uiState.visibleSongs.toMusicGroups(selectedView, uiState.sortMode)
    }
    val selectedGroup = playbackGroups.firstOrNull { it.title == selectedGroupTitle }
    val playbackSource = selectedView.playbackQueueSource()
    val folderSourceKey = uiState.selectedFolderPath ?: "all"
    val playbackSourceKey = when (selectedView) {
        MusicLibraryView.Songs -> uiState.selectedFolderPath
        MusicLibraryView.Artists,
        MusicLibraryView.Albums -> selectedGroup?.let { "${it.key}:$folderSourceKey" }
    }
    val displayedSongs = if (selectedView == MusicLibraryView.Songs) {
        uiState.visibleSongs
    } else {
        selectedGroup?.songs.orEmpty()
    }
    val selectedArtist = if (selectedView == MusicLibraryView.Artists) selectedGroup else null
    val selectedAlbum = if (selectedView == MusicLibraryView.Albums) selectedGroup else null
    val isGroupDetail = selectedArtist != null || selectedAlbum != null
    val showingSongs = selectedView == MusicLibraryView.Songs || selectedGroup != null
    val playableSongs = when {
        selectedView == MusicLibraryView.Songs -> uiState.visibleSongs
        selectedGroup != null -> selectedGroup.songs
        else -> emptyList()
    }
    val backgroundModifier = modifier.background(MaterialTheme.colorScheme.background)

    BackHandler(enabled = selectedGroupTitle != null) {
        selectedGroupTitle = null
    }

    Box(
        modifier = backgroundModifier,
    ) {
        if (!hasAudioPermission) {
            PermissionPanel(onRequestPermission = onRequestPermission)
            return@Box
        }

        LazyColumn(
            contentPadding = if (isGroupDetail) {
                PaddingValues(bottom = 112.dp)
            } else {
                PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 112.dp)
            },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                if (selectedArtist != null) {
                    ArtistDetailHeader(
                        artist = selectedArtist,
                        playbackMode = uiState.playbackMode,
                        onBack = { selectedGroupTitle = null },
                        onPlayArtist = {
                            onPlayQueue(
                                playableSongs,
                                playbackSource,
                                playbackSourceKey,
                            )
                        },
                        onPlaybackModeChange = onPlaybackModeChange,
                    )
                } else if (selectedAlbum != null) {
                    AlbumDetailHeader(
                        album = selectedAlbum,
                        playbackMode = uiState.playbackMode,
                        onBack = { selectedGroupTitle = null },
                        onPlayAlbum = {
                            onPlayQueue(
                                playableSongs,
                                playbackSource,
                                playbackSourceKey,
                            )
                        },
                        onPlaybackModeChange = onPlaybackModeChange,
                    )
                } else {
                    LibraryHeader(
                        songCount = playableSongs.size,
                        groupCount = groups.size,
                        selectedView = selectedView,
                        selectedGroupTitle = selectedGroup?.title,
                        isPlaying = uiState.isPlaying,
                        folders = uiState.folders,
                        selectedFolderPath = uiState.selectedFolderPath,
                        onRefreshLibrary = onRefreshLibrary,
                        onPlayList = {
                            onPlayQueue(
                                playableSongs,
                                playbackSource,
                                playbackSourceKey,
                            )
                        },
                        onViewChange = { view ->
                            selectedView = view
                            selectedGroupTitle = null
                        },
                        onClearGroup = { selectedGroupTitle = null },
                        onFolderChange = onFolderChange,
                    )
                }
            }

            if (uiState.isLoading) {
                item {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            uiState.errorMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (!uiState.isLoading && uiState.visibleSongs.isEmpty()) {
                item {
                    if (uiState.songs.isNotEmpty() && uiState.hiddenFolderPaths.isNotEmpty()) {
                        EmptyHiddenFolders(onOpenSettings = onOpenSettings)
                    } else {
                        EmptyLibrary(onRefreshLibrary = onRefreshLibrary)
                    }
                }
            }

            if (!uiState.isLoading && uiState.visibleSongs.isNotEmpty()) {
                if (showingSongs) {
                    items(
                        items = displayedSongs,
                        key = { it.id },
                        contentType = { "song" },
                    ) { song ->
                        Box(
                            modifier = Modifier
                                .animateItem()
                                .then(if (isGroupDetail) Modifier.padding(horizontal = 20.dp) else Modifier),
                        ) {
                            SongRow(
                                song = song,
                                isCurrent = uiState.currentSong?.id == song.id,
                                isPlaying = uiState.currentSong?.id == song.id && uiState.isPlaying,
                                isFavorite = song.id in uiState.favoriteSongIds,
                                playlists = uiState.playlists,
                                onClick = {
                                    onPlayQueuedSong(
                                        song,
                                        playableSongs,
                                        playbackSource,
                                        playbackSourceKey,
                                    )
                                },
                                onToggleFavorite = { onToggleFavorite(song.id) },
                                onPlayNext = { onPlayNext(song) },
                                onPlayAtEnd = { onPlayAtEnd(song) },
                                onAddToPlaylist = { playlistId -> onAddSongToPlaylist(playlistId, song.id) },
                            )
                        }
                    }
                } else {
                    items(
                        items = groups,
                        key = { it.key },
                        contentType = { "group" },
                    ) { group ->
                        Box(modifier = Modifier.animateItem()) {
                            MusicGroupRow(
                                group = group,
                                onClick = { selectedGroupTitle = group.title },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchLibraryScreen(
    modifier: Modifier = Modifier,
    uiState: PlayerUiState,
    onPlayQueuedSong: (Song, List<Song>, PlaybackQueueSource, String?) -> Unit,
    onPlayQueue: (List<Song>, PlaybackQueueSource, String?) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onPlayNext: (Song) -> Unit,
    onPlayAtEnd: (Song) -> Unit,
    onAddSongToPlaylist: (String, Long) -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val librarySongs = remember(uiState.songs, uiState.hiddenFolderPaths, uiState.excludeSmallAudios, uiState.sortMode) {
        uiState.visibleLibrarySongs().sortedForDisplay(uiState.sortMode)
    }
    val searchedSongs = remember(librarySongs, searchQuery) {
        if (searchQuery.isBlank()) emptyList() else librarySongs.filterBySearch(searchQuery)
    }
    val searchedArtists = remember(librarySongs, searchQuery, uiState.sortMode) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            librarySongs
                .toMusicGroups(MusicLibraryView.Artists, uiState.sortMode)
                .filterByGroupSearch(searchQuery)
        }
    }
    val searchedAlbums = remember(librarySongs, searchQuery, uiState.sortMode) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            librarySongs
                .toMusicGroups(MusicLibraryView.Albums, uiState.sortMode)
                .filterByGroupSearch(searchQuery)
        }
    }
    val hasAnyResult = searchedArtists.isNotEmpty() || searchedAlbums.isNotEmpty() || searchedSongs.isNotEmpty()

    Box(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ModuleTitle(
                    icon = Icons.Filled.Search,
                    title = "Buscar",
                    modifier = Modifier.fillMaxWidth(),
                    compact = true,
                )
            }

            item {
                SearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                )
            }

            if (searchQuery.isBlank()) {
                item {
                    SearchEmptyState(
                        title = "Busca en tu biblioteca",
                        body = "Encuentra canciones por título, artista, álbum o carpeta.",
                    )
                }
            } else if (!hasAnyResult) {
                item {
                    EmptySearchResults(searchQuery = searchQuery)
                }
            } else {
                if (searchedArtists.isNotEmpty()) {
                    item { SearchSectionTitle("Artistas") }
                    items(
                        items = searchedArtists,
                        key = { "artist:${it.key}" },
                        contentType = { "artist" },
                    ) { group ->
                        Box(modifier = Modifier.animateItem()) {
                            MusicGroupRow(
                                group = group,
                                onClick = {
                                    onPlayQueue(
                                        group.songs,
                                        PlaybackQueueSource.Artist,
                                        "search:${group.key}",
                                    )
                                },
                            )
                        }
                    }
                }
                if (searchedAlbums.isNotEmpty()) {
                    item { SearchSectionTitle("Álbumes") }
                    items(
                        items = searchedAlbums,
                        key = { "album:${it.key}" },
                        contentType = { "album" },
                    ) { group ->
                        Box(modifier = Modifier.animateItem()) {
                            MusicGroupRow(
                                group = group,
                                onClick = {
                                    onPlayQueue(
                                        group.songs,
                                        PlaybackQueueSource.Album,
                                        "search:${group.key}",
                                    )
                                },
                            )
                        }
                    }
                }
                if (searchedSongs.isNotEmpty()) {
                    item { SearchSectionTitle("Canciones") }
                    items(
                        items = searchedSongs,
                        key = { it.id },
                        contentType = { "song" },
                    ) { song ->
                        Box(modifier = Modifier.animateItem()) {
                            SongRow(
                                song = song,
                                isCurrent = uiState.currentSong?.id == song.id,
                                isPlaying = uiState.currentSong?.id == song.id && uiState.isPlaying,
                                isFavorite = song.id in uiState.favoriteSongIds,
                                playlists = uiState.playlists,
                                onClick = {
                                    onPlayQueuedSong(
                                        song,
                                        searchedSongs,
                                        PlaybackQueueSource.Songs,
                                        "search",
                                    )
                                },
                                onToggleFavorite = { onToggleFavorite(song.id) },
                                onPlayNext = { onPlayNext(song) },
                                onPlayAtEnd = { onPlayAtEnd(song) },
                                onAddToPlaylist = { playlistId -> onAddSongToPlaylist(playlistId, song.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun FavoritesLibraryScreen(
    modifier: Modifier = Modifier,
    uiState: PlayerUiState,
    songs: List<Song>,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onPlayList: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onPlayNext: (Song) -> Unit,
    onPlayAtEnd: (Song) -> Unit,
    onAddSongToPlaylist: (String, Long) -> Unit,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
    onOpenMusic: () -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val searchedSongs = remember(songs, searchQuery) {
        songs.filterBySearch(searchQuery)
    }
    val hasSearchQuery = searchQuery.isNotBlank()
    val backgroundModifier = modifier.background(MaterialTheme.colorScheme.background)

    Box(modifier = backgroundModifier) {
        if (!hasAudioPermission) {
            PermissionPanel(onRequestPermission = onRequestPermission)
            return@Box
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                FavoritesHeader(
                    songCount = songs.size,
                    isPlaying = uiState.isPlaying,
                    playbackMode = uiState.playbackMode,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onPlayList = onPlayList,
                    onPlaybackModeChange = onPlaybackModeChange,
                )
            }

            if (uiState.isLoading) {
                item {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            uiState.errorMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (!uiState.isLoading && songs.isEmpty()) {
                item {
                    EmptyFavorites(onOpenMusic = onOpenMusic)
                }
            } else if (!uiState.isLoading && searchedSongs.isEmpty() && hasSearchQuery) {
                item {
                    EmptySearchResults(searchQuery = searchQuery)
                }
            }

            items(
                items = searchedSongs,
                key = { it.id },
                contentType = { "song" },
            ) { song ->
                Box(modifier = Modifier.animateItem()) {
                    SongRow(
                        song = song,
                        isCurrent = uiState.currentSong?.id == song.id,
                        isPlaying = uiState.currentSong?.id == song.id && uiState.isPlaying,
                        isFavorite = song.id in uiState.favoriteSongIds,
                        playlists = uiState.playlists,
                        onClick = { onPlaySong(song) },
                        onToggleFavorite = { onToggleFavorite(song.id) },
                        onPlayNext = { onPlayNext(song) },
                        onPlayAtEnd = { onPlayAtEnd(song) },
                        onAddToPlaylist = { playlistId -> onAddSongToPlaylist(playlistId, song.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistsLibraryScreen(
    modifier: Modifier = Modifier,
    uiState: PlayerUiState,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
    onCreatePlaylist: (String) -> Boolean,
    onDeletePlaylist: (String) -> Unit,
    onRemoveSongFromPlaylist: (String, Long) -> Unit,
    onPlayQueuedSong: (Song, List<Song>, PlaybackQueueSource, String?) -> Unit,
    onPlayQueue: (List<Song>, PlaybackQueueSource, String?) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onPlayNext: (Song) -> Unit,
    onPlayAtEnd: (Song) -> Unit,
    onAddSongToPlaylist: (String, Long) -> Unit,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
    onOpenMusic: () -> Unit,
) {
    var selectedPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable(selectedPlaylistId) { mutableStateOf("") }
    val selectedPlaylist = uiState.playlists.firstOrNull { it.id == selectedPlaylistId }
    val playlistSongs = remember(selectedPlaylist, uiState.songs, uiState.hiddenFolderPaths, uiState.excludeSmallAudios) {
        selectedPlaylist?.songsFrom(uiState.songs, uiState.hiddenFolderPaths, uiState.excludeSmallAudios).orEmpty()
    }
    val displayedPlaylists = remember(uiState.playlists, searchQuery, selectedPlaylist) {
        if (selectedPlaylist == null) {
            uiState.playlists.filterByPlaylistSearch(searchQuery)
        } else {
            emptyList()
        }
    }
    val hasSearchQuery = searchQuery.isNotBlank()
    val backgroundModifier = modifier.background(MaterialTheme.colorScheme.background)

    BackHandler(enabled = selectedPlaylistId != null) {
        selectedPlaylistId = null
    }

    Box(modifier = backgroundModifier) {
        if (!hasAudioPermission) {
            PermissionPanel(onRequestPermission = onRequestPermission)
            return@Box
        }

        LazyColumn(
            contentPadding = if (selectedPlaylist == null) {
                PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 112.dp)
            } else {
                PaddingValues(bottom = 112.dp)
            },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (selectedPlaylist == null) {
                item {
                    PlaylistsHeader(
                        title = "Playlist",
                        subtitle = uiState.playlists.size.playlistCountLabel(),
                    )
                }
                if (uiState.playlists.isNotEmpty()) {
                    item {
                        SearchField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                        )
                    }
                }
                item {
                    CreatePlaylistCard(onCreatePlaylist = onCreatePlaylist)
                }
                if (uiState.playlists.isEmpty()) {
                    item {
                        EmptyPlaylists()
                    }
                } else if (displayedPlaylists.isEmpty() && hasSearchQuery) {
                    item {
                        EmptySearchResults(searchQuery = searchQuery)
                    }
                }
                items(
                    items = displayedPlaylists,
                    key = { it.id },
                    contentType = { "playlist" },
                ) { playlist ->
                    Box(modifier = Modifier.animateItem()) {
                        PlaylistRow(
                            playlist = playlist,
                            songs = playlist.songsFrom(uiState.songs, uiState.hiddenFolderPaths, uiState.excludeSmallAudios),
                            onClick = { selectedPlaylistId = playlist.id },
                            onDelete = { onDeletePlaylist(playlist.id) },
                        )
                    }
                }
            } else {
                item {
                    PlaylistDetailHeader(
                        playlist = selectedPlaylist,
                        songs = playlistSongs,
                        playbackMode = uiState.playbackMode,
                        onBack = { selectedPlaylistId = null },
                        onPlayList = { onPlayQueue(playlistSongs, PlaybackQueueSource.Playlist, selectedPlaylist.id) },
                        onPlaybackModeChange = onPlaybackModeChange,
                    )
                }
                if (playlistSongs.isEmpty()) {
                    item {
                        Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                            EmptyPlaylist(onOpenMusic = onOpenMusic)
                        }
                    }
                }
                items(
                    items = playlistSongs,
                    key = { it.id },
                    contentType = { "playlistSong" },
                ) { song ->
                    Box(
                        modifier = Modifier
                            .animateItem()
                            .padding(horizontal = 20.dp),
                    ) {
                        PlaylistSongRow(
                            song = song,
                            isCurrent = uiState.currentSong?.id == song.id,
                            isPlaying = uiState.currentSong?.id == song.id && uiState.isPlaying,
                            isFavorite = song.id in uiState.favoriteSongIds,
                            playlists = uiState.playlists,
                            onClick = { onPlayQueuedSong(song, playlistSongs, PlaybackQueueSource.Playlist, selectedPlaylist.id) },
                            onToggleFavorite = { onToggleFavorite(song.id) },
                            onPlayNext = { onPlayNext(song) },
                            onPlayAtEnd = { onPlayAtEnd(song) },
                            onAddToPlaylist = { playlistId -> onAddSongToPlaylist(playlistId, song.id) },
                            onRemove = { onRemoveSongFromPlaylist(selectedPlaylist.id, song.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistsHeader(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ModuleTitle(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                title = title,
                modifier = Modifier.fillMaxWidth(),
                compact = true,
            )
            Text(
                text = subtitle,
                modifier = Modifier.padding(start = 38.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun PlaylistDetailHeader(
    playlist: MusicPlaylist,
    songs: List<Song>,
    playbackMode: PlaybackMode,
    onBack: () -> Unit,
    onPlayList: () -> Unit,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
) {
    val firstSong = songs.firstOrNull()
    val totalDurationMs = remember(songs) { songs.sumOf { it.durationMs.coerceAtLeast(0L) } }
    val totalDurationLabel = if (totalDurationMs > 0L) formatDuration(totalDurationMs) else null
    val metadata = listOfNotNull(
        songs.size.songCountLabel(),
        totalDurationLabel,
    ).joinToString(" • ")
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(440.dp),
        ) {
            PlaylistMosaicBackground(
                modifier = Modifier.fillMaxSize(),
                bottomColor = MaterialTheme.colorScheme.background,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.34f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                            ),
                        ),
                    ),
            )
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.28f)),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = Color.White,
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (firstSong != null) {
                    Artwork(
                        song = firstSong,
                        modifier = Modifier
                            .size(148.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
                        rounded = 12.dp,
                    )
                }
                Text(
                    text = "playlist",
                    color = Color.White.copy(alpha = 0.84f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = playlist.name,
                    color = Color.White,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Tu mezcla local guardada para escuchar sin conexión.",
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "SoftMusic • $metadata",
                    color = Color.White.copy(alpha = 0.74f),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    onPlaybackModeChange(
                        if (playbackMode == PlaybackMode.Shuffle) PlaybackMode.Ordered else PlaybackMode.Shuffle,
                    )
                },
                enabled = songs.isNotEmpty(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Shuffle,
                    contentDescription = "Alternar aleatorio",
                    tint = if (playbackMode == PlaybackMode.Shuffle) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = onPlayList,
                enabled = songs.isNotEmpty(),
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(
                        if (songs.isNotEmpty()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Reproducir playlist",
                    tint = if (songs.isNotEmpty()) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(34.dp),
                )
            }
        }
    }
}

@Composable
private fun ArtistDetailHeader(
    artist: MusicGroup,
    playbackMode: PlaybackMode,
    onBack: () -> Unit,
    onPlayArtist: () -> Unit,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
) {
    val songs = artist.songs
    val firstSong = songs.firstOrNull()
    val albumCount = remember(songs) { songs.map { it.album }.distinct().size }
    val totalDurationMs = remember(songs) { songs.sumOf { it.durationMs.coerceAtLeast(0L) } }
    val totalDurationLabel = if (totalDurationMs > 0L) formatDuration(totalDurationMs) else null
    val metadata = listOfNotNull(
        songs.size.songCountLabel(),
        albumCount.albumCountLabel(),
        totalDurationLabel,
    ).joinToString(" • ")

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(440.dp),
        ) {
            PlaylistMosaicBackground(
                modifier = Modifier.fillMaxSize(),
                bottomColor = MaterialTheme.colorScheme.background,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.34f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                            ),
                        ),
                    ),
            )
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.28f)),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = Color.White,
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (firstSong != null) {
                    Artwork(
                        song = firstSong,
                        modifier = Modifier
                            .size(148.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
                        rounded = 12.dp,
                    )
                }
                Text(
                    text = "artista",
                    color = Color.White.copy(alpha = 0.84f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = artist.title,
                    color = Color.White,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${albumCount.albumCountLabel()} en tu biblioteca",
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "SoftMusic • $metadata",
                    color = Color.White.copy(alpha = 0.74f),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    onPlaybackModeChange(
                        if (playbackMode == PlaybackMode.Shuffle) PlaybackMode.Ordered else PlaybackMode.Shuffle,
                    )
                },
                enabled = songs.isNotEmpty(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Shuffle,
                    contentDescription = "Alternar aleatorio",
                    tint = if (playbackMode == PlaybackMode.Shuffle) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = onPlayArtist,
                enabled = songs.isNotEmpty(),
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(
                        if (songs.isNotEmpty()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Reproducir artista",
                    tint = if (songs.isNotEmpty()) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(34.dp),
                )
            }
        }
    }
}

@Composable
private fun AlbumDetailHeader(
    album: MusicGroup,
    playbackMode: PlaybackMode,
    onBack: () -> Unit,
    onPlayAlbum: () -> Unit,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
) {
    val songs = album.songs
    val firstSong = songs.firstOrNull()
    val totalDurationMs = remember(songs) { songs.sumOf { it.durationMs.coerceAtLeast(0L) } }
    val totalDurationLabel = if (totalDurationMs > 0L) formatDuration(totalDurationMs) else null
    val metadata = listOfNotNull(
        songs.size.songCountLabel(),
        totalDurationLabel,
    ).joinToString(" • ")

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(440.dp),
        ) {
            PlaylistMosaicBackground(
                modifier = Modifier.fillMaxSize(),
                bottomColor = MaterialTheme.colorScheme.background,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.34f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                            ),
                        ),
                    ),
            )
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.28f)),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = Color.White,
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (firstSong != null) {
                    Artwork(
                        song = firstSong,
                        modifier = Modifier
                            .size(148.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
                        rounded = 12.dp,
                    )
                }
                Text(
                    text = "álbum",
                    color = Color.White.copy(alpha = 0.84f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = album.title,
                    color = Color.White,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = album.subtitle,
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "SoftMusic • $metadata",
                    color = Color.White.copy(alpha = 0.74f),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    onPlaybackModeChange(
                        if (playbackMode == PlaybackMode.Shuffle) PlaybackMode.Ordered else PlaybackMode.Shuffle,
                    )
                },
                enabled = songs.isNotEmpty(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Shuffle,
                    contentDescription = "Alternar aleatorio",
                    tint = if (playbackMode == PlaybackMode.Shuffle) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = onPlayAlbum,
                enabled = songs.isNotEmpty(),
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(
                        if (songs.isNotEmpty()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Reproducir álbum",
                    tint = if (songs.isNotEmpty()) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(34.dp),
                )
            }
        }
    }
}

@Composable
private fun PlaylistMosaicBackground(
    modifier: Modifier = Modifier,
    bottomColor: Color,
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val scaleX = width / 1200f
        val scaleY = height / 1200f

        drawRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to Color(0xFFFF6A00),
                    0.25f to Color(0xFFFFD500),
                    0.45f to Color(0xFF54E346),
                    0.65f to Color(0xFF00C8FF),
                    0.85f to Color(0xFF3454FF),
                    1f to Color(0xFF9B00FF),
                ),
                start = Offset.Zero,
                end = Offset(width, height),
            ),
        )

        drawPath(
            path = Path().apply {
                moveTo(0f, 100f * scaleY)
                cubicTo(180f * scaleX, 180f * scaleY, 140f * scaleX, 380f * scaleY, 330f * scaleX, 560f * scaleY)
                cubicTo(520f * scaleX, 740f * scaleY, 760f * scaleX, 760f * scaleY, 950f * scaleX, 850f * scaleY)
                cubicTo(1100f * scaleX, 920f * scaleY, 1160f * scaleX, 1060f * scaleY, 1200f * scaleX, 1200f * scaleY)
                lineTo(1200f * scaleX, 0f)
                lineTo(0f, 0f)
                close()
            },
            color = Color.White.copy(alpha = 0.08f),
        )

        drawPath(
            path = Path().apply {
                moveTo(0f, 680f * scaleY)
                cubicTo(180f * scaleX, 700f * scaleY, 260f * scaleX, 900f * scaleY, 460f * scaleX, 990f * scaleY)
                cubicTo(650f * scaleX, 1075f * scaleY, 900f * scaleX, 990f * scaleY, 1200f * scaleX, 1160f * scaleY)
                lineTo(1200f * scaleX, 1200f * scaleY)
                lineTo(0f, 1200f * scaleY)
                close()
            },
            color = Color.White.copy(alpha = 0.07f),
        )

        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color.White.copy(alpha = 0.22f),
                    1f to Color.Transparent,
                ),
                center = Offset(width * 0.55f, height * 0.45f),
                radius = max(width, height) * 0.55f,
            ),
        )

        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.72f to Color.Transparent,
                    1f to bottomColor,
                ),
            ),
        )
    }
}

@Composable
private fun CreatePlaylistCard(onCreatePlaylist: (String) -> Boolean) {
    var playlistName by remember { mutableStateOf("") }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Nueva playlist",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Nombre") },
                )
                Button(
                    onClick = {
                        if (onCreatePlaylist(playlistName)) {
                            playlistName = ""
                        }
                    },
                    enabled = playlistName.isNotBlank(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Crear playlist",
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: MusicPlaylist,
    songs: List<Song>,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .background(Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (songs.isNotEmpty()) {
            Artwork(song = songs.first(), modifier = Modifier.size(56.dp), rounded = 4.dp)
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Playlist • ${songs.size.songCountLabel()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { showDeleteConfirmation = true }) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Eliminar playlist",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Eliminar playlist") },
            text = { Text("Se eliminará \"${playlist.name}\". Las canciones no se borrarán del teléfono.") },
            confirmButton = {
                Button(onClick = {
                    showDeleteConfirmation = false
                    onDelete()
                }) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistSongRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    playlists: List<MusicPlaylist>,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayAtEnd: () -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onRemove: () -> Unit,
) {
    var showActions by remember { mutableStateOf(false) }
    val rowBackground by animateColorAsState(
        targetValue = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent,
        animationSpec = tween(durationMillis = 180),
        label = "songRowBackground",
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .combinedClickable(
                    onClick = onClick,
                    onClickLabel = "Reproducir ${song.title}",
                    onLongClick = { showActions = true },
                    onLongClickLabel = "Mostrar opciones de ${song.title}",
                    role = Role.Button,
                )
                .background(rowBackground)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(song = song, modifier = Modifier.size(56.dp), rounded = 4.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (isPlaying) "Sonando • ${song.artist}" else "Canción • ${song.artist} - ${song.album}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { showActions = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Mostrar opciones de ${song.title}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SongActionsDropdown(
            expanded = showActions,
            song = song,
            isFavorite = isFavorite,
            playlists = playlists,
            onDismiss = { showActions = false },
            onToggleFavorite = {
                showActions = false
                onToggleFavorite()
            },
            onPlayNext = {
                showActions = false
                onPlayNext()
            },
            onPlayAtEnd = {
                showActions = false
                onPlayAtEnd()
            },
            onAddToPlaylist = { playlistId ->
                showActions = false
                onAddToPlaylist(playlistId)
            },
            onRemove = {
                showActions = false
                onRemove()
            },
        )
    }
}

@Composable
private fun EmptyPlaylists() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Sin playlists",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Crea una playlist y agrega canciones desde Mi Música o Favoritos.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyPlaylist(onOpenMusic: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Playlist vacía",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Agrega canciones con el botón de playlist en Mi Música.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenMusic) {
                Text("Ir a Mi Música")
            }
        }
    }
}

@Composable
private fun PermissionPanel(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
    showRationale: Boolean = false,
    permanentlyDenied: Boolean = false,
    onOpenAppSettings: () -> Unit = {},
) {
    val title = when {
        permanentlyDenied -> "Activa el permiso desde Ajustes"
        showRationale -> "Tu biblioteca necesita este permiso"
        else -> "Permite acceso a tu música"
    }
    val description = when {
        permanentlyDenied -> "Android ya no mostrará la solicitud de permiso. Abre Ajustes, entra en Permisos y permite Música y audio para SoftMusic."
        showRationale -> "Sin acceso a tus archivos de audio no puedo escanear canciones, crear la biblioteca ni reproducir música local."
        else -> "SoftMusic funciona offline leyendo los audios guardados en tu teléfono."
    }
    val primaryAction = if (permanentlyDenied) onOpenAppSettings else onRequestPermission
    val primaryLabel = if (permanentlyDenied) "Abrir ajustes" else "Dar permiso"

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = primaryAction) {
            Text(primaryLabel)
        }
        if (showRationale && !permanentlyDenied) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(onClick = onOpenAppSettings) {
                Text("Abrir ajustes")
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    songCount: Int,
    groupCount: Int,
    selectedView: MusicLibraryView,
    selectedGroupTitle: String?,
    isPlaying: Boolean,
    folders: List<MusicFolder>,
    selectedFolderPath: String?,
    onRefreshLibrary: () -> Unit,
    onPlayList: () -> Unit,
    onViewChange: (MusicLibraryView) -> Unit,
    onClearGroup: () -> Unit,
    onFolderChange: (String?) -> Unit,
) {
    val subtitle = when {
        selectedGroupTitle != null -> "$selectedGroupTitle - ${songCount.songCountLabel()}"
        selectedView == MusicLibraryView.Songs -> "${songCount.songCountLabel()} offline"
        selectedView == MusicLibraryView.Artists -> groupCount.artistCountLabel()
        selectedView == MusicLibraryView.Albums -> groupCount.albumCountLabel()
        else -> "${songCount.songCountLabel()} offline"
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ModuleTitle(
                icon = Icons.Filled.LibraryMusic,
                title = "Tu biblioteca",
                modifier = Modifier.fillMaxWidth(),
                compact = true,
            )
            Text(
                text = subtitle,
                modifier = Modifier.padding(start = 38.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (selectedGroupTitle == null) {
            MusicLibraryViewSelector(
                selectedView = selectedView,
                onViewChange = onViewChange,
            )
        }

        if (selectedGroupTitle != null) {
            OutlinedButton(onClick = onClearGroup) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Volver a ${selectedView.groupListLabel()}")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(
                    onClick = onPlayList,
                    enabled = songCount > 0,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (songCount > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = if (selectedView == MusicLibraryView.Songs) "Reproducir lista" else "Reproducir grupo",
                        tint = if (songCount > 0) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimary),
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            FolderMenu(
                folders = folders,
                selectedFolderPath = selectedFolderPath,
                onFolderChange = onFolderChange,
                iconOnly = true,
            )
            IconButton(onClick = onRefreshLibrary) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Actualizar biblioteca",
                )
            }
        }
    }
}

@Composable
private fun MusicLibraryViewSelector(
    selectedView: MusicLibraryView,
    onViewChange: (MusicLibraryView) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MusicLibraryView.entries.forEach { view ->
            val selected = view == selectedView
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Mostrar ${view.label.lowercase()}",
                    ) { onViewChange(view) }
                    .semantics {
                        stateDescription = if (selected) "Seleccionado" else "No seleccionado"
                    },
                shape = RoundedCornerShape(999.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f)
                },
            ) {
                Text(
                    text = view.label,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Limpiar búsqueda",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
        placeholder = {
            Text(
                text = "Buscar",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
    )
}

@Composable
private fun FavoritesHeader(
    songCount: Int,
    isPlaying: Boolean,
    playbackMode: PlaybackMode,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onPlayList: () -> Unit,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ModuleTitle(
                icon = Icons.Filled.Star,
                title = "Favoritos",
                modifier = Modifier.fillMaxWidth(),
                compact = true,
            )
            Text(
                text = songCount.favoriteCountLabel(),
                modifier = Modifier.padding(start = 38.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }

        if (songCount > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchField(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onPlaybackModeChange(playbackMode.next()) }) {
                    Icon(
                        imageVector = playbackMode.icon(),
                        contentDescription = "Modo actual: ${playbackMode.label}. Toca para cambiar.",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = onPlayList,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Reproducir favoritos",
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onPrimary),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSheet(
    themeMode: AppThemeMode,
    colorPalette: AppColorPalette,
    highPerformanceMode: Boolean,
    hasAudioPermission: Boolean,
    audioPermissionPermanentlyDenied: Boolean,
    hasNotificationPermission: Boolean,
    allFolders: List<MusicFolder>,
    folders: List<MusicFolder>,
    defaultFolderPath: String?,
    defaultPlaybackMode: PlaybackMode,
    defaultSortMode: SortMode,
    fontScale: Float,
    hiddenFolderPaths: Set<String>,
    excludeSmallAudios: Boolean,
    djModeEnabled: Boolean,
    djMixMode: DjMixMode,
    djMixDurationSeconds: Int,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onColorPaletteChange: (AppColorPalette) -> Unit,
    onHighPerformanceModeChange: (Boolean) -> Unit,
    onRequestAudioPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onDefaultFolderChange: (String?) -> Unit,
    onDefaultPlaybackModeChange: (PlaybackMode) -> Unit,
    onDefaultSortModeChange: (SortMode) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onExcludeSmallAudiosChange: (Boolean) -> Unit,
    onHiddenFolderChange: (String, Boolean) -> Unit,
    onDjModeChange: (Boolean) -> Unit,
    onDjMixModeChange: (DjMixMode) -> Unit,
    onDjMixDurationChange: (Int) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenDonation: () -> Unit,
    isCheckingForUpdates: Boolean,
    updateCheckStatus: String?,
    onCheckForUpdates: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ModuleTitle(
                icon = Icons.Filled.Settings,
                title = "Ajustes",
                modifier = Modifier.fillMaxWidth(),
                compact = true,
            )
            Text(
                text = "Personaliza la app y sus valores iniciales.",
                modifier = Modifier.padding(start = 38.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsSection(
            title = "Actualizaciones",
            subtitle = "Comprueba si hay una versión nueva disponible.",
        ) {
            UpdateCheckBlock(
                isCheckingForUpdates = isCheckingForUpdates,
                updateCheckStatus = updateCheckStatus,
                onCheckForUpdates = onCheckForUpdates,
            )
        }

        SettingsSection(
            title = "Donar",
            subtitle = "Apoya el desarrollo de SoftMusic.",
        ) {
            DonationBlock(onOpenDonation = onOpenDonation)
        }

        SettingsSection(
            title = "Apariencia",
            subtitle = "Tema, color, texto y rendimiento.",
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SettingBlock(
                    title = "Tema",
                    modifier = Modifier.weight(1f),
                ) {
                    ThemeModeMenu(
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                    )
                }
                SettingBlock(
                    title = "Color",
                    modifier = Modifier.weight(1f),
                ) {
                    ColorPaletteMenu(
                        colorPalette = colorPalette,
                        onColorPaletteChange = onColorPaletteChange,
                    )
                }
            }

            SettingBlock(title = "Tamaño de letras") {
                FontScaleSlider(
                    fontScale = fontScale,
                    onFontScaleChange = onFontScaleChange,
                )
            }

            PerformanceModeToggle(
                highPerformanceMode = highPerformanceMode,
                onHighPerformanceModeChange = onHighPerformanceModeChange,
            )
        }

        SettingsSection(
            title = "Biblioteca",
            subtitle = "Carpetas y canciones visibles.",
        ) {
            SettingBlock(title = "Carpeta inicial") {
                FolderMenu(
                    folders = folders,
                    selectedFolderPath = defaultFolderPath,
                    onFolderChange = onDefaultFolderChange,
                )
            }

            SettingBlock(title = "Carpetas ocultas") {
                HiddenFoldersBlock(
                    folders = allFolders,
                    hiddenFolderPaths = hiddenFolderPaths,
                    onHiddenFolderChange = onHiddenFolderChange,
                )
            }

            SmallAudioFilterBlock(
                excludeSmallAudios = excludeSmallAudios,
                onExcludeSmallAudiosChange = onExcludeSmallAudiosChange,
            )
        }

        SettingsSection(
            title = "Reproducción",
            subtitle = "Modo inicial y herramientas de mezcla.",
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SettingBlock(
                    title = "Modo inicial",
                    modifier = Modifier.weight(1f),
                ) {
                    PlaybackModeMenu(
                        playbackMode = defaultPlaybackMode,
                        onPlaybackModeChange = onDefaultPlaybackModeChange,
                    )
                }
                SettingBlock(
                    title = "Orden inicial",
                    modifier = Modifier.weight(1f),
                ) {
                    SortMenu(
                        sortMode = defaultSortMode,
                        onSortModeChange = onDefaultSortModeChange,
                    )
                }
            }

            DjModeBlock(
                enabled = djModeEnabled,
                mixMode = djMixMode,
                mixDurationSeconds = djMixDurationSeconds,
                onEnabledChange = onDjModeChange,
                onMixModeChange = onDjMixModeChange,
                onMixDurationChange = onDjMixDurationChange,
            )
        }

        SettingsSection(
            title = "Permisos",
            subtitle = "Estado de accesos y estabilidad en segundo plano.",
        ) {
            PermissionStatusBlock(
                title = "Música local",
                description = if (audioPermissionPermanentlyDenied) {
                    "El permiso está bloqueado. Abre ajustes de la app y permite Música y audio."
                } else {
                    "Permite leer canciones guardadas en el teléfono."
                },
                granted = hasAudioPermission,
                actionLabel = if (audioPermissionPermanentlyDenied) "Abrir ajustes" else "Dar permiso",
                onAction = if (audioPermissionPermanentlyDenied) onOpenAppSettings else onRequestAudioPermission,
            )
            PermissionStatusBlock(
                title = "Notificaciones",
                description = "Muestra controles en la barra y ayuda a que aparezcan en pantalla de bloqueo.",
                granted = hasNotificationPermission,
                actionLabel = "Dar permiso",
                onAction = onRequestNotificationPermission,
            )
            PermissionInfoBlock(
                title = "Pantalla de bloqueo",
                description = "No es un permiso de la app. Abre ajustes de notificaciones y permite mostrarlas en pantalla de bloqueo.",
                actionLabel = "Abrir ajustes",
                onAction = onOpenNotificationSettings,
            )
            PermissionInfoBlock(
                title = "Bateria",
                description = "Si la música se corta en segundo plano, abre ajustes de la app y quita restricciones de batería.",
                actionLabel = "Abrir ajustes",
                onAction = onOpenAppSettings,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun UpdateCheckBlock(
    isCheckingForUpdates: Boolean,
    updateCheckStatus: String?,
    onCheckForUpdates: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Buscar actualizaciones",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Comprueba si existe una APK más reciente para instalar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (updateCheckStatus != null) {
                Text(
                    text = updateCheckStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        OutlinedButton(
            onClick = onCheckForUpdates,
            enabled = !isCheckingForUpdates,
        ) {
            Text(if (isCheckingForUpdates) "Buscando..." else "Buscar")
        }
    }
}

@Composable
private fun DonationBlock(onOpenDonation: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Invitar un café",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Si disfrutas SoftMusic, puedes apoyar sus mejoras.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onOpenDonation) {
            Text("Donar")
        }
    }
}

@Composable
private fun PermissionStatusBlock(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = !granted, onClick = onAction)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (granted) "Listo" else "Pendiente",
                style = MaterialTheme.typography.labelSmall,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
            )
        }
        if (!granted) {
            OutlinedButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun PermissionInfoBlock(
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onAction)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onAction) {
            Text(actionLabel)
        }
    }
}

@Composable
private fun FontScaleSlider(
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Escala de texto",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Ajusta el tamaño visual de toda la app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${(fontScale * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = fontScale,
            onValueChange = { onFontScaleChange(it.coerceIn(0.80f, 1.20f)) },
            valueRange = 0.80f..1.20f,
            steps = 7,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Pequeño",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Grande",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SmallAudioFilterBlock(
    excludeSmallAudios: Boolean,
    onExcludeSmallAudiosChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Switch) { onExcludeSmallAudiosChange(!excludeSmallAudios) }
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Excluir audios pequeños",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Oculta audios de menos de 30s o 1 MB en biblioteca, búsqueda y playlists.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = excludeSmallAudios,
            onCheckedChange = onExcludeSmallAudiosChange,
        )
    }
}

@Composable
private fun HiddenFoldersBlock(
    folders: List<MusicFolder>,
    hiddenFolderPaths: Set<String>,
    onHiddenFolderChange: (String, Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val hiddenFoldersLabel = when (hiddenFolderPaths.size) {
        0 -> "Sin carpetas ocultas"
        1 -> "1 carpeta oculta"
        else -> "${hiddenFolderPaths.size} carpetas ocultas"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Carpetas ocultas: ${hiddenFolderPaths.size}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "No aparecerán en biblioteca, favoritos ni playlists.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (folders.isEmpty()) {
            Text(
                text = "No hay carpetas disponibles.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = hiddenFoldersLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    folders.forEach { folder ->
                        val hidden = folder.path in hiddenFolderPaths
                        DropdownMenuItem(
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "${folder.name} (${folder.songCount})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = folder.path,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            },
                            trailingIcon = {
                                Switch(
                                    checked = hidden,
                                    onCheckedChange = { onHiddenFolderChange(folder.path, it) },
                                )
                            },
                            onClick = { onHiddenFolderChange(folder.path, !hidden) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DjModeBlock(
    enabled: Boolean,
    mixMode: DjMixMode,
    mixDurationSeconds: Int,
    onEnabledChange: (Boolean) -> Unit,
    onMixModeChange: (DjMixMode) -> Unit,
    onMixDurationChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Modo DJ (Experimental)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Mezcla la siguiente canción antes de que termine la actual.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Tipo de mezcla",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DjMixMode.entries.forEach { mode ->
                    val selected = mixMode == mode
                    OutlinedButton(
                        onClick = { onMixModeChange(mode) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            } else {
                                Color.Transparent
                            },
                        ),
                    ) {
                        Text(mode.label)
                    }
                }
            }
            Text(
                text = if (mixMode == DjMixMode.Expert) {
                    "Analiza silencios al final e inicio de canciones para saltar partes mudas. Si falla, usa el modo clásico."
                } else {
                    "Mezcla por tiempo fijo antes de que termine la canción."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Tiempo de mezcla",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${mixDurationSeconds}s",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(5, 6, 7, 8).forEach { seconds ->
                    val selected = mixDurationSeconds == seconds
                    OutlinedButton(
                        onClick = { onMixDurationChange(seconds) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            } else {
                                Color.Transparent
                            },
                        ),
                    ) {
                        Text("${seconds}s")
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformanceModeToggle(
    highPerformanceMode: Boolean,
    onHighPerformanceModeChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Modo Alto rendimiento",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Interfaz mas ligera, sin degradados ni caratulas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = highPerformanceMode,
            onCheckedChange = onHighPerformanceModeChange,
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    val highPerformanceMode = LocalHighPerformanceMode.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highPerformanceMode) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun SettingBlock(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun ThemeModeMenu(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            ColorSwatch(color = themeMode.previewColor())
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = themeMode.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AppThemeMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ColorSwatch(color = mode.previewColor())
                            Text(mode.label)
                        }
                    },
                    onClick = {
                        expanded = false
                        onThemeModeChange(mode)
                    },
                )
            }
        }
    }
}

@Composable
private fun ColorPaletteMenu(
    colorPalette: AppColorPalette,
    onColorPaletteChange: (AppColorPalette) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            ColorSwatch(color = colorPalette.darkPrimary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = colorPalette.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AppColorPalette.entries.forEach { palette ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ColorSwatch(color = palette.darkPrimary)
                            Text(palette.label)
                        }
                    },
                    onClick = {
                        expanded = false
                        onColorPaletteChange(palette)
                    },
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Color) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f), CircleShape),
    )
}

private fun AppThemeMode.previewColor(): Color = when (this) {
    AppThemeMode.System -> Color(0xFF6B7280)
    AppThemeMode.Light -> Color.White
    AppThemeMode.Dark -> Color(0xFF08090C)
    AppThemeMode.Midnight -> Color(0xFF172A42)
    AppThemeMode.Forest -> Color(0xFF183020)
    AppThemeMode.Sunset -> Color(0xFFF2DDC8)
    AppThemeMode.Lavender -> Color(0xFFE8DDF7)
    AppThemeMode.Graphite -> Color(0xFF2A3036)
}

@Composable
private fun PlaybackModeMenu(
    playbackMode: PlaybackMode,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = playbackMode.icon(),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = playbackMode.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            PlaybackMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    onClick = {
                        expanded = false
                        onPlaybackModeChange(mode)
                    },
                )
            }
        }
    }
}

@Composable
private fun FolderMenu(
    folders: List<MusicFolder>,
    selectedFolderPath: String?,
    onFolderChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
    iconOnly: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedFolder = folders.firstOrNull { it.path == selectedFolderPath }
    Box(modifier = modifier) {
        if (iconOnly) {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = "Carpeta: ${selectedFolder?.name ?: "Todas"}",
                )
            }
        } else {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = (selectedFolder?.name ?: "Todas").compactLabel(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Todas las carpetas") },
                onClick = {
                    expanded = false
                    onFolderChange(null)
                },
            )
            folders.forEach { folder ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "${folder.name} (${folder.songCount})",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        expanded = false
                        onFolderChange(folder.path)
                    },
                )
            }
        }
    }
}

@Composable
private fun SortMenu(
    sortMode: SortMode,
    onSortModeChange: (SortMode) -> Unit,
    modifier: Modifier = Modifier,
    iconOnly: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        if (iconOnly) {
            OutlinedIconButton(onClick = { expanded = true }) {
                Icon(
                    imageVector = Icons.Filled.FormatListNumbered,
                    contentDescription = "Orden: ${sortMode.label}",
                )
            }
        } else {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.FormatListNumbered,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = sortMode.label.compactLabel(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            SortMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    onClick = {
                        expanded = false
                        onSortModeChange(mode)
                    },
                )
            }
        }
    }
}

@Composable
private fun PlaybackModeButton(
    playbackMode: PlaybackMode,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedIconButton(
        onClick = { onPlaybackModeChange(playbackMode.next()) },
        modifier = modifier,
    ) {
        Icon(
            imageVector = playbackMode.icon(),
            contentDescription = "Modo actual: ${playbackMode.label}. Toca para cambiar.",
        )
    }
}

@Composable
private fun EmptyLibrary(onRefreshLibrary: () -> Unit) {
    val highPerformanceMode = LocalHighPerformanceMode.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (highPerformanceMode) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            },
        ),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "No encontré canciones",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Agrega archivos MP3, M4A, FLAC, WAV u OGG al almacenamiento del teléfono y actualiza la biblioteca.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRefreshLibrary) {
                Text("Volver a escanear")
            }
        }
    }
}

@Composable
private fun EmptyHiddenFolders(onOpenSettings: () -> Unit) {
    val highPerformanceMode = LocalHighPerformanceMode.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (highPerformanceMode) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            },
        ),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Todas las carpetas están ocultas",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Muestra al menos una carpeta para ver canciones en la biblioteca.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenSettings) {
                Text("Abrir ajustes")
            }
        }
    }
}

@Composable
private fun EmptyFavorites(onOpenMusic: () -> Unit) {
    val highPerformanceMode = LocalHighPerformanceMode.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (highPerformanceMode) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            },
        ),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Sin favoritos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Marca canciones con el corazón desde Mi Música para encontrarlas aquí.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenMusic) {
                Text("Ir a Mi Música")
            }
        }
    }
}

@Composable
private fun EmptySearchResults(searchQuery: String) {
    val highPerformanceMode = LocalHighPerformanceMode.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (highPerformanceMode) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            },
        ),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Sin resultados",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "No encontré canciones para \"$searchQuery\".",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchEmptyState(
    title: String,
    body: String,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        ),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MusicGroupRow(
    group: MusicGroup,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .background(Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            song = group.songs.first(),
            modifier = Modifier.size(56.dp),
            rounded = 4.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = group.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = group.songs.size.songCountLabel(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    playlists: List<MusicPlaylist>,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayAtEnd: () -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    var showActions by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .combinedClickable(
                    onClick = onClick,
                    onClickLabel = "Reproducir ${song.title}",
                    onLongClick = { showActions = true },
                    onLongClickLabel = "Mostrar opciones de ${song.title}",
                    role = Role.Button,
                )
                .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(
                song = song,
                modifier = Modifier.size(56.dp),
                rounded = 4.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (isPlaying) "Sonando • ${song.artist}" else "Canción • ${song.artist} - ${song.album}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { showActions = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Mostrar opciones de ${song.title}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SongActionsDropdown(
            expanded = showActions,
            song = song,
            isFavorite = isFavorite,
            playlists = playlists,
            onDismiss = { showActions = false },
            onToggleFavorite = {
                showActions = false
                onToggleFavorite()
            },
            onPlayNext = {
                showActions = false
                onPlayNext()
            },
            onPlayAtEnd = {
                showActions = false
                onPlayAtEnd()
            },
            onAddToPlaylist = { playlistId ->
                showActions = false
                onAddToPlaylist(playlistId)
            },
        )
    }
}

@Composable
private fun SongActionsDropdown(
    expanded: Boolean,
    song: Song,
    isFavorite: Boolean,
    playlists: List<MusicPlaylist>,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayAtEnd: () -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    var showPlaylists by remember { mutableStateOf(false) }
    val availablePlaylists = remember(expanded, playlists, song.id) {
        if (expanded) playlists.filterNot { playlist -> song.id in playlist.songIds } else emptyList()
    }

    LaunchedEffect(expanded) {
        if (!expanded) showPlaylists = false
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        if (showPlaylists) {
            DropdownMenuItem(
                text = { Text("Volver") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                    )
                },
                onClick = { showPlaylists = false },
            )
            availablePlaylists.forEach { playlist ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = playlist.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = null,
                        )
                    },
                    onClick = { onAddToPlaylist(playlist.id) },
                )
            }
        } else {
            DropdownMenuItem(
                text = { Text("Reproducir después") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = null,
                    )
                },
                onClick = onPlayNext,
            )
            DropdownMenuItem(
                text = { Text("Reproducir al final") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.FormatListNumbered,
                        contentDescription = null,
                    )
                },
                onClick = onPlayAtEnd,
            )
            DropdownMenuItem(
                text = { Text(if (isFavorite) "Quitar de favoritos" else "Agregar a favoritos") },
                leadingIcon = {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = null,
                    )
                },
                onClick = onToggleFavorite,
            )
            if (availablePlaylists.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text("Agregar a Playlist") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = null,
                        )
                    },
                    onClick = { showPlaylists = true },
                )
            }
            if (onRemove != null) {
                DropdownMenuItem(
                    text = { Text("Quitar de playlist") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                        )
                    },
                    onClick = onRemove,
                )
            }
        }
    }
}

@Composable
private fun AddToPlaylistButton(
    playlists: List<MusicPlaylist>,
    song: Song,
    onAddToPlaylist: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showPlaylists by remember { mutableStateOf(false) }
    val availablePlaylists = remember(expanded, playlists, song.id) {
        if (expanded) playlists.filterNot { playlist -> song.id in playlist.songIds } else emptyList()
    }

    LaunchedEffect(expanded) {
        if (!expanded) showPlaylists = false
    }

    Box {
        OutlinedIconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                contentDescription = "Agregar a playlist",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (showPlaylists) {
                DropdownMenuItem(
                    text = { Text("Volver") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    },
                    onClick = { showPlaylists = false },
                )
                availablePlaylists.forEach { playlist ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = playlist.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            expanded = false
                            onAddToPlaylist(playlist.id)
                        },
                    )
                }
            } else {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = when {
                                playlists.isEmpty() -> "Crea una playlist primero"
                                availablePlaylists.isEmpty() -> "Ya está en tus playlists"
                                else -> "Agregar a Playlist"
                            },
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = null,
                        )
                    },
                    enabled = availablePlaylists.isNotEmpty(),
                    onClick = { showPlaylists = true },
                )
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    uiState: PlayerUiState,
    playbackProgressState: StateFlow<PlaybackProgressState>,
    onOpenPlayer: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val song = uiState.currentSong ?: return
    val playbackProgress by playbackProgressState.collectAsStateWithLifecycle()
    val playPauseScale by animateFloatAsState(
        targetValue = if (uiState.isPlaying) 1.12f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "miniPlayerPlayPauseScale",
    )
    Surface(
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .clickable(onClick = onOpenPlayer)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(
                    song = song,
                    modifier = Modifier.size(46.dp),
                    rounded = 5.dp,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onPrevious) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Anterior",
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(onClick = onTogglePlayPause) {
                    Icon(
                        imageVector = if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (uiState.isPlaying) "Pausar" else "Reproducir",
                        modifier = Modifier
                            .size(26.dp)
                            .graphicsLayer {
                                scaleX = playPauseScale
                                scaleY = playPauseScale
                            },
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Siguiente",
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            PlaybackProgress(
                positionMs = playbackProgress.positionMs,
                durationMs = playbackProgress.durationMs,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
        }
    }
}

@Composable
private fun FullPlayer(
    uiState: PlayerUiState,
    playbackProgressState: StateFlow<PlaybackProgressState>,
    isFavorite: Boolean,
    playlists: List<MusicPlaylist>,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
    onClose: () -> Unit,
) {
    val song = uiState.currentSong ?: return
    val playbackProgress by playbackProgressState.collectAsStateWithLifecycle()
    val highPerformanceMode = LocalHighPerformanceMode.current
    var localSlider by remember(uiState.currentSong?.id) { mutableFloatStateOf(0f) }
    var isScrubbing by remember(uiState.currentSong?.id) { mutableStateOf(false) }
    var showVinylArtwork by remember(uiState.currentSong?.id) { mutableStateOf(false) }
    var showSongDetails by rememberSaveable(uiState.currentSong?.id) { mutableStateOf(false) }
    var showPlaybackQueue by rememberSaveable(uiState.currentSong?.id) { mutableStateOf(false) }
    val duration = max(playbackProgress.durationMs, 1L)
    val progress = (playbackProgress.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    val playerAccent = MaterialTheme.colorScheme.primary
    val playerBackground = MaterialTheme.colorScheme.background
    val playerSurface = MaterialTheme.colorScheme.surface
    val primaryText = MaterialTheme.colorScheme.onBackground
    val mutedText = MaterialTheme.colorScheme.onSurfaceVariant
    val timelineInactive = MaterialTheme.colorScheme.surfaceVariant
    val sourceLabel = if (isFavorite) "TUS ME GUSTA" else "BIBLIOTECA LOCAL"
    val shuffleActive = uiState.playbackMode == PlaybackMode.Shuffle
    val repeatActive = uiState.playbackMode == PlaybackMode.RepeatList || uiState.playbackMode == PlaybackMode.RepeatCurrent
    val nextShuffleMode = if (shuffleActive) PlaybackMode.Ordered else PlaybackMode.Shuffle
    val nextRepeatMode = when (uiState.playbackMode) {
        PlaybackMode.RepeatList -> PlaybackMode.RepeatCurrent
        PlaybackMode.RepeatCurrent -> PlaybackMode.Ordered
        PlaybackMode.Ordered,
        PlaybackMode.Shuffle -> PlaybackMode.RepeatList
    }
    val favoriteTint by animateColorAsState(
        targetValue = if (isFavorite) playerAccent else primaryText,
        animationSpec = tween(durationMillis = 180),
        label = "fullPlayerFavoriteTint",
    )
    val favoriteScale by animateFloatAsState(
        targetValue = if (isFavorite) 1.18f else 1f,
        animationSpec = spring(),
        label = "fullPlayerFavoriteScale",
    )
    val playPauseScale by animateFloatAsState(
        targetValue = if (uiState.isPlaying) 1.06f else 1f,
        animationSpec = spring(),
        label = "fullPlayerPlayPauseScale",
    )

    LaunchedEffect(progress) {
        if (!isScrubbing) localSlider = progress
    }

    if (showSongDetails) {
        SongDetailsDialog(
            song = song,
            onDismiss = { showSongDetails = false },
        )
    }

    if (showPlaybackQueue) {
        PlaybackQueueDialog(
            queue = uiState.playbackQueue,
            currentSong = song,
            onDismiss = { showPlaybackQueue = false },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(playerBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Cerrar reproductor",
                        tint = primaryText,
                        modifier = Modifier.size(34.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "REPRODUCIENDO DESDE $sourceLabel",
                        style = MaterialTheme.typography.labelMedium,
                        color = primaryText.copy(alpha = 0.86f),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.folderName,
                        style = MaterialTheme.typography.titleSmall,
                        color = primaryText,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { showSongDetails = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Ver detalles de la canción",
                        tint = primaryText,
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            val artworkModifier = Modifier
                .fillMaxWidth(if (highPerformanceMode) 0.82f else 0.92f)
                .aspectRatio(1f)
                .clickable(enabled = !highPerformanceMode) {
                    showVinylArtwork = !showVinylArtwork
                }
            if (!highPerformanceMode && showVinylArtwork) {
                SpinningDiscArtwork(
                    song = song,
                    isPlaying = uiState.isPlaying,
                    modifier = artworkModifier,
                )
            } else {
                Artwork(
                    song = song,
                    modifier = artworkModifier,
                    rounded = 12.dp,
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = song.title,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = primaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = mutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.album,
                        style = MaterialTheme.typography.bodyMedium,
                        color = mutedText.copy(alpha = 0.76f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.graphicsLayer {
                        scaleX = favoriteScale
                        scaleY = favoriteScale
                    },
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.CheckCircle else Icons.Filled.StarBorder,
                        contentDescription = if (isFavorite) "Quitar de favoritos" else "Agregar a favoritos",
                        tint = favoriteTint,
                        modifier = Modifier.size(if (isFavorite) 34.dp else 30.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            ThinTimeline(
                value = localSlider,
                onValueChange = {
                    isScrubbing = true
                    localSlider = it
                },
                onValueChangeFinished = {
                    onSeek((localSlider * duration).toLong())
                    isScrubbing = false
                },
                activeColor = primaryText,
                inactiveColor = timelineInactive,
                thumbColor = primaryText,
                modifier = Modifier.semantics {
                    stateDescription = "${formatDuration((localSlider * duration).toLong())} de ${formatDuration(playbackProgress.durationMs)}"
                },
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = formatDuration(playbackProgress.positionMs),
                    color = mutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatDuration(playbackProgress.durationMs),
                    color = mutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onPlaybackModeChange(nextShuffleMode) }) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = if (shuffleActive) "Desactivar aleatorio" else "Activar aleatorio",
                        tint = if (shuffleActive) playerAccent else mutedText,
                        modifier = Modifier.size(30.dp),
                    )
                }
                IconButton(onClick = onPrevious) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = primaryText,
                        modifier = Modifier.size(52.dp),
                    )
                }
                Button(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(86.dp)
                        .graphicsLayer {
                            scaleX = playPauseScale
                            scaleY = playPauseScale
                        },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Icon(
                        imageVector = if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (uiState.isPlaying) "Pausar" else "Reproducir",
                        modifier = Modifier.size(42.dp),
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Siguiente",
                        tint = primaryText,
                        modifier = Modifier.size(52.dp),
                    )
                }
                IconButton(onClick = { onPlaybackModeChange(nextRepeatMode) }) {
                    Icon(
                        imageVector = if (uiState.playbackMode == PlaybackMode.RepeatCurrent) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = if (repeatActive) "Cambiar repetición" else "Activar repetición",
                        tint = if (repeatActive) playerAccent else mutedText,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "SoftMusic",
                    color = playerAccent,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AddToPlaylistButton(
                        playlists = playlists,
                        song = song,
                        onAddToPlaylist = onAddToPlaylist,
                    )
                    IconButton(onClick = { showPlaybackQueue = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Ver cola de reproducción",
                            tint = primaryText,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = playerSurface),
                shape = RoundedCornerShape(26.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Letra",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = primaryText,
                    )
                    Text(
                        text = "Letra no disponible para canciones locales.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = mutedText,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ThinTimeline(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    activeColor: Color,
    inactiveColor: Color,
    thumbColor: Color,
    modifier: Modifier = Modifier,
) {
    fun updateValue(x: Float, width: Int) {
        val safeWidth = width.coerceAtLeast(1)
        onValueChange((x / safeWidth.toFloat()).coerceIn(0f, 1f))
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    updateValue(offset.x, size.width)
                    onValueChangeFinished()
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> updateValue(offset.x, size.width) },
                    onDragEnd = onValueChangeFinished,
                    onDragCancel = onValueChangeFinished,
                ) { change, _ ->
                    updateValue(change.position.x, size.width)
                }
            },
    ) {
        val safeValue = value.coerceIn(0f, 1f)
        val centerY = size.height / 2f
        val progressX = size.width * safeValue
        val lineWidth = 2.dp.toPx()
        val thumbRadius = 5.dp.toPx()

        drawLine(
            color = inactiveColor,
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = lineWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = activeColor,
            start = Offset(0f, centerY),
            end = Offset(progressX, centerY),
            strokeWidth = lineWidth,
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = thumbColor,
            radius = thumbRadius,
            center = Offset(progressX, centerY),
        )
    }
}

@Composable
private fun PlaybackQueueDialog(
    queue: List<Song>,
    currentSong: Song,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cola de reproducción") },
        text = {
            if (queue.isEmpty()) {
                Text(
                    text = "No hay canciones en cola.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = queue.size.songCountLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(
                            items = queue,
                            key = { _, song -> song.id },
                            contentType = { _, _ -> "queueSong" },
                        ) { index, song ->
                            Box(modifier = Modifier.animateItem()) {
                                PlaybackQueueItem(
                                    index = index + 1,
                                    song = song,
                                    isCurrent = song.id == currentSong.id,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
    )
}

@Composable
private fun PlaybackQueueItem(
    index: Int,
    song: Song,
    isCurrent: Boolean,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isCurrent) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 180),
        label = "queueItemBackground",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = index.toString(),
            modifier = Modifier.width(28.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = formatDuration(song.durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SongDetailsDialog(
    song: Song,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Detalles de la canción") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SongDetailItem(label = "Título", value = song.title)
                SongDetailItem(label = "Artista", value = song.artist)
                SongDetailItem(label = "Álbum", value = song.album)
                SongDetailItem(label = "Duración", value = "${formatDuration(song.durationMs)} (${song.durationMs.coerceAtLeast(0)} ms)")
                SongDetailItem(label = "Fecha agregada", value = formatDateAdded(song.dateAddedSeconds))
                SongDetailItem(label = "Carpeta", value = song.folderName)
                SongDetailItem(label = "Ruta de carpeta", value = song.folderPath)
                SongDetailItem(label = "URI de contenido", value = song.uri)
                SongDetailItem(label = "URI de carátula", value = song.artworkUri ?: "No disponible")
                SongDetailItem(label = "ID", value = song.id.toString())
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
    )
}

@Composable
private fun SongDetailItem(
    label: String,
    value: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value.ifBlank { "No disponible" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SpinningDiscArtwork(
    song: Song,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    var rotation by remember(song.id) { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying, song.id) {
        if (!isPlaying) return@LaunchedEffect
        var lastFrameNanos = withFrameNanos { it }
        while (true) {
            val frameNanos = withFrameNanos { it }
            val deltaMillis = (frameNanos - lastFrameNanos).coerceAtLeast(0L) / 1_000_000f
            lastFrameNanos = frameNanos
            rotation = (rotation + deltaMillis * FULL_DISC_ROTATION_DEGREES / DISC_ROTATION_DURATION_MS) % FULL_DISC_ROTATION_DEGREES
        }
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .graphicsLayer { rotationZ = rotation },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawVinylDisc()
        }
        Box(
            modifier = Modifier
                .fillMaxSize(0.39f)
                .clip(CircleShape)
                .background(Color.White)
                .border(4.dp, Color(0xFF333333), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (song.artworkUri != null) {
                AsyncImage(
                    model = song.artworkUri,
                    contentDescription = "Caratula de ${song.title}",
                    modifier = Modifier
                        .fillMaxSize(0.90f)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.90f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = song.title.firstOrNull()?.uppercaseChar()?.toString() ?: "S",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize(0.053f)
                .clip(CircleShape)
                .background(Color(0xFF111111)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(0.38f)
                    .clip(CircleShape)
                    .background(Color(0xFF555555)),
            )
        }
    }
}

private fun DrawScope.drawVinylDisc() {
    val diameter = min(size.width, size.height)
    val scale = diameter / VINYL_VIEWBOX_SIZE
    val origin = Offset(
        x = (size.width - diameter) / 2f,
        y = (size.height - diameter) / 2f,
    )
    fun point(x: Float, y: Float) = Offset(origin.x + x * scale, origin.y + y * scale)
    fun radius(value: Float) = value * scale
    val center = point(150f, 150f)
    val shineBrush = Brush.linearGradient(
        colorStops = arrayOf(
            0f to Color.White.copy(alpha = 0.45f),
            0.40f to Color.White.copy(alpha = 0.05f),
            1f to Color.White.copy(alpha = 0f),
        ),
        start = origin,
        end = Offset(origin.x + diameter, origin.y + diameter),
    )

    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color(0xFF222222),
                0.55f to Color(0xFF050505),
                1f to Color.Black,
            ),
            center = point(150f, 135f),
            radius = radius(165f),
        ),
        radius = radius(140f),
        center = center,
    )
    drawCircle(
        color = Color(0xFF444444),
        radius = radius(138f),
        center = center,
        style = Stroke(width = radius(2f)),
    )
    drawCircle(
        color = Color(0xFF111111),
        radius = radius(126f),
        center = center,
        style = Stroke(width = radius(4f)),
    )
    listOf(118f, 112f, 106f, 100f, 94f, 88f, 82f, 76f, 70f).forEach { grooveRadius ->
        drawCircle(
            color = Color(0xFF1D1D1D),
            radius = radius(grooveRadius),
            center = center,
            style = Stroke(width = radius(1f)),
        )
    }

    val topShine = Path().apply {
        moveTo(point(70f, 80f).x, point(70f, 80f).y)
        cubicTo(
            point(105f, 35f).x,
            point(105f, 35f).y,
            point(185f, 28f).x,
            point(185f, 28f).y,
            point(230f, 75f).x,
            point(230f, 75f).y,
        )
    }
    drawPath(
        path = topShine,
        brush = shineBrush,
        alpha = 0.75f,
        style = Stroke(width = radius(18f), cap = StrokeCap.Round),
    )

    val sideShine = Path().apply {
        moveTo(point(205f, 220f).x, point(205f, 220f).y)
        cubicTo(
            point(230f, 195f).x,
            point(230f, 195f).y,
            point(238f, 160f).x,
            point(238f, 160f).y,
            point(225f, 125f).x,
            point(225f, 125f).y,
        )
    }
    drawPath(
        path = sideShine,
        brush = shineBrush,
        alpha = 0.45f,
        style = Stroke(width = radius(14f), cap = StrokeCap.Round),
    )
}

@Composable
private fun Artwork(
    song: Song,
    modifier: Modifier = Modifier,
    rounded: androidx.compose.ui.unit.Dp = 14.dp,
) {
    val highPerformanceMode = LocalHighPerformanceMode.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(rounded))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!highPerformanceMode && song.artworkUri != null) {
            AsyncImage(
                model = song.artworkUri,
                contentDescription = "Caratula de ${song.title}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = song.title.firstOrNull()?.uppercaseChar()?.toString() ?: "S",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PlaybackProgress(
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    val progress = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

private fun formatDuration(durationMs: Long): String {
    val safeMs = durationMs.coerceAtLeast(0)
    val totalSeconds = safeMs / 1_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

private fun formatDateAdded(dateAddedSeconds: Long): String {
    if (dateAddedSeconds <= 0L) return "No disponible"
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
        .format(Date(dateAddedSeconds * 1_000L))
}

private fun String.compactLabel(maxLength: Int = 15): String {
    val clean = trim()
    if (clean.length <= maxLength) return clean
    if (maxLength <= 3) return clean.take(maxLength)
    return clean.take(maxLength - 3) + "..."
}

private fun Int.favoriteCountLabel(): String = when (this) {
    0 -> "Sin favoritos"
    1 -> "1 canción favorita"
    else -> "$this canciones favoritas"
}

private fun Int.songCountLabel(): String = when (this) {
    1 -> "1 canción"
    else -> "$this canciones"
}

private fun Int.artistCountLabel(): String = when (this) {
    1 -> "1 artista"
    else -> "$this artistas"
}

private fun Int.albumCountLabel(): String = when (this) {
    1 -> "1 álbum"
    else -> "$this álbumes"
}

private fun Int.playlistCountLabel(): String = when (this) {
    0 -> "Sin playlists"
    1 -> "1 playlist"
    else -> "$this playlists"
}

private fun MusicLibraryView.groupListLabel(): String = when (this) {
    MusicLibraryView.Songs -> "canciones"
    MusicLibraryView.Artists -> "artistas"
    MusicLibraryView.Albums -> "álbumes"
}

private fun MusicLibraryView.playbackQueueSource(): PlaybackQueueSource = when (this) {
    MusicLibraryView.Songs -> PlaybackQueueSource.Songs
    MusicLibraryView.Artists -> PlaybackQueueSource.Artist
    MusicLibraryView.Albums -> PlaybackQueueSource.Album
}

private fun PlayerUiState.favoriteSongs(): List<Song> = visibleLibrarySongs()
    .filter { it.id in favoriteSongIds }
    .sortedForDisplay(sortMode)

private fun PlayerUiState.visibleLibrarySongs(): List<Song> = songs.filter {
    it.folderPath !in hiddenFolderPaths && it.isIncludedBySmallAudioFilter(excludeSmallAudios)
}

private fun MusicPlaylist.songsFrom(
    songs: List<Song>,
    hiddenFolderPaths: Set<String> = emptySet(),
    excludeSmallAudios: Boolean = true,
): List<Song> {
    return orderedSongsFrom(songs)
        .filter { it.folderPath !in hiddenFolderPaths && it.isIncludedBySmallAudioFilter(excludeSmallAudios) }
}

private fun List<Song>.filterBySearch(query: String): List<Song> {
    val cleanQuery = query.normalizedSearchText()
    if (cleanQuery.isBlank()) return this

    return filter { song ->
        song.title.normalizedSearchText().contains(cleanQuery) ||
            song.artist.normalizedSearchText().contains(cleanQuery) ||
            song.album.normalizedSearchText().contains(cleanQuery) ||
            song.folderName.normalizedSearchText().contains(cleanQuery)
    }
}

private fun List<MusicPlaylist>.filterByPlaylistSearch(query: String): List<MusicPlaylist> {
    val cleanQuery = query.normalizedSearchText()
    if (cleanQuery.isBlank()) return this

    return filter { playlist -> playlist.name.normalizedSearchText().contains(cleanQuery) }
}

private fun List<MusicGroup>.filterByGroupSearch(query: String): List<MusicGroup> {
    val cleanQuery = query.normalizedSearchText()
    if (cleanQuery.isBlank()) return this

    return filter { group -> group.title.normalizedSearchText().contains(cleanQuery) }
}

private fun String.normalizedSearchText(): String {
    return Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
}

private fun List<Song>.toMusicGroups(
    view: MusicLibraryView,
    sortMode: SortMode,
): List<MusicGroup> {
    val grouped = when (view) {
        MusicLibraryView.Songs -> return emptyList()
        MusicLibraryView.Artists -> groupBy { it.artist }
        MusicLibraryView.Albums -> groupBy { it.album }
    }

    return grouped.map { (title, songs) ->
        val sortedSongs = songs.sortedForDisplay(sortMode)
        val subtitle = when (view) {
            MusicLibraryView.Songs -> ""
            MusicLibraryView.Artists -> {
                val albumCount = songs.map { it.album }.distinct().size
                "${songs.size.songCountLabel()} - ${albumCount.albumCountLabel()}"
            }
            MusicLibraryView.Albums -> songs
                .map { it.artist }
                .distinct()
                .sortedBy { it.lowercase() }
                .joinToString(", ")
        }
        MusicGroup(
            key = "${view.name}:${title.lowercase()}",
            title = title,
            subtitle = subtitle.ifBlank { songs.size.songCountLabel() },
            songs = sortedSongs,
        )
    }.sortedBy { it.title.lowercase() }
}

private fun List<Song>.sortedForDisplay(sortMode: SortMode): List<Song> = when (sortMode) {
    SortMode.Recent -> sortedWith(compareByDescending<Song> { it.dateAddedSeconds }.thenBy { it.title.lowercase() })
    SortMode.Title -> sortedWith(compareBy<Song> { it.title.lowercase() }.thenBy { it.artist.lowercase() })
}

private fun PlaybackMode.next(): PlaybackMode {
    val modes = PlaybackMode.entries
    return modes[(ordinal + 1) % modes.size]
}

private fun PlaybackMode.icon(): ImageVector = when (this) {
    PlaybackMode.Ordered -> Icons.Filled.DoubleArrow
    PlaybackMode.RepeatList -> Icons.Filled.Repeat
    PlaybackMode.RepeatCurrent -> Icons.Filled.RepeatOne
    PlaybackMode.Shuffle -> Icons.Filled.Shuffle
}
