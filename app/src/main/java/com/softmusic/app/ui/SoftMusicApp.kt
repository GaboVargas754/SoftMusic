package com.softmusic.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoubleArrow
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
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
import com.softmusic.app.data.orderedSongsFrom
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

private val LocalHighPerformanceMode = compositionLocalOf { false }
private const val MAX_PLAYLIST_NAME_LENGTH = 50

private enum class LibraryDestination {
    Home,
    Music,
    Favorites,
    Playlists,
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
    djMixDurationSeconds: Int,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onColorPaletteChange: (AppColorPalette) -> Unit,
    onHighPerformanceModeChange: (Boolean) -> Unit,
    onDefaultFolderChange: (String?) -> Unit,
    onDefaultPlaybackModeChange: (PlaybackMode) -> Unit,
    onDefaultSortModeChange: (SortMode) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onHiddenFolderChange: (String, Boolean) -> Unit,
    onDjModeChange: (Boolean) -> Unit,
    onDjMixDurationChange: (Int) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onCloseApp: () -> Unit,
) {
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var destination by rememberSaveable { mutableStateOf(LibraryDestination.Home) }
    val playerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val visibleLibrarySongs = remember(uiState.songs, uiState.hiddenFolderPaths) {
        uiState.visibleLibrarySongs()
    }
    val favoriteSongs = remember(uiState.songs, uiState.favoriteSongIds, uiState.sortMode, uiState.hiddenFolderPaths) {
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
    BackHandler(enabled = destination != LibraryDestination.Home && !showPlayer && !showSettings) {
        destination = LibraryDestination.Home
    }

    CompositionLocalProvider(LocalHighPerformanceMode provides highPerformanceMode) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
            bottomBar = {
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
            },
        ) { padding ->
            when (destination) {
                LibraryDestination.Home -> LibraryHomeScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    songCount = visibleLibrarySongs.size,
                    favoriteCount = favoriteSongs.size,
                    playlistCount = uiState.playlists.size,
                    hasAudioPermission = hasAudioPermission,
                    onRequestPermission = onRequestPermission,
                    onOpenMusic = { destination = LibraryDestination.Music },
                    onOpenFavorites = { destination = LibraryDestination.Favorites },
                    onOpenPlaylists = { destination = LibraryDestination.Playlists },
                    onOpenSettings = { showSettings = true },
                    onCloseApp = onCloseApp,
                )
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
                    onOpenSettings = { showSettings = true },
                    onBackToLibrary = { destination = LibraryDestination.Home },
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
                    onSortModeChange = onSortModeChange,
                    onOpenSettings = { showSettings = true },
                    onBackToLibrary = { destination = LibraryDestination.Home },
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
                    onOpenSettings = { showSettings = true },
                    onBackToLibrary = { destination = LibraryDestination.Home },
                    onOpenMusic = { destination = LibraryDestination.Music },
                )
            }
        }

        if (showPlayer && uiState.currentSong != null) {
            ModalBottomSheet(
                onDismissRequest = { showPlayer = false },
                sheetState = playerSheetState,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
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
                    hasNotificationPermission = hasNotificationPermission,
                    allFolders = uiState.allFolders,
                    folders = uiState.folders,
                    defaultFolderPath = defaultFolderPath,
                    defaultPlaybackMode = defaultPlaybackMode,
                    defaultSortMode = defaultSortMode,
                    fontScale = fontScale,
                    hiddenFolderPaths = uiState.hiddenFolderPaths,
                    djModeEnabled = djModeEnabled,
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
                    onHiddenFolderChange = onHiddenFolderChange,
                    onDjModeChange = onDjModeChange,
                    onDjMixDurationChange = onDjMixDurationChange,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onOpenAppSettings = onOpenAppSettings,
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
    onOpenSettings: () -> Unit,
    onCloseApp: () -> Unit,
) {
    var showCloseConfirmation by remember { mutableStateOf(false) }
    val highPerformanceMode = LocalHighPerformanceMode.current
    val backgroundModifier = if (highPerformanceMode) {
        modifier.background(MaterialTheme.colorScheme.background)
    } else {
        modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                    MaterialTheme.colorScheme.background,
                    MaterialTheme.colorScheme.background,
                ),
            ),
        )
    }

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
                    Text(
                        text = "Biblioteca",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "Elige una sección para continuar",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Abrir configuración",
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
    onBackToLibrary: () -> Unit,
) {
    var selectedView by rememberSaveable { mutableStateOf(MusicLibraryView.Songs) }
    var selectedGroupTitle by rememberSaveable(selectedView, uiState.selectedFolderPath) { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable(uiState.selectedFolderPath) { mutableStateOf("") }
    val searchedSongs = remember(uiState.visibleSongs, searchQuery) {
        uiState.visibleSongs.filterBySearch(searchQuery)
    }
    val groups = remember(searchedSongs, selectedView, uiState.sortMode) {
        searchedSongs.toMusicGroups(selectedView, uiState.sortMode)
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
        searchedSongs
    } else {
        selectedGroup?.songs.orEmpty()
    }
    val showingSongs = selectedView == MusicLibraryView.Songs || selectedGroup != null
    val playableSongs = when {
        selectedView == MusicLibraryView.Songs -> uiState.visibleSongs
        selectedGroup != null -> selectedGroup.songs
        else -> emptyList()
    }
    val hasSearchQuery = searchQuery.isNotBlank()
    val highPerformanceMode = LocalHighPerformanceMode.current
    val backgroundModifier = if (highPerformanceMode) {
        modifier.background(MaterialTheme.colorScheme.background)
    } else {
        modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                    MaterialTheme.colorScheme.background,
                    MaterialTheme.colorScheme.background,
                ),
            ),
        )
    }

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
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                LibraryHeader(
                    songCount = playableSongs.size,
                    groupCount = groups.size,
                    selectedView = selectedView,
                    selectedGroupTitle = selectedGroup?.title,
                    searchQuery = searchQuery,
                    sortMode = uiState.sortMode,
                    playbackMode = uiState.playbackMode,
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
                    onSearchQueryChange = { query ->
                        searchQuery = query
                        selectedGroupTitle = null
                    },
                    onViewChange = { view ->
                        selectedView = view
                        selectedGroupTitle = null
                        searchQuery = ""
                    },
                    onClearGroup = { selectedGroupTitle = null },
                    onPlaybackModeChange = onPlaybackModeChange,
                    onSortModeChange = onSortModeChange,
                    onFolderChange = onFolderChange,
                    onOpenSettings = onOpenSettings,
                    onBackToLibrary = onBackToLibrary,
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
                    if (displayedSongs.isEmpty() && hasSearchQuery) {
                        item {
                            EmptySearchResults(searchQuery = searchQuery)
                        }
                    } else {
                        items(
                            items = displayedSongs,
                            key = { it.id },
                        ) { song ->
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
                    if (groups.isEmpty() && hasSearchQuery) {
                        item {
                            EmptySearchResults(searchQuery = searchQuery)
                        }
                    } else {
                        items(
                            items = groups,
                            key = { it.key },
                        ) { group ->
                            MusicGroupRow(
                                group = group,
                                onClick = {
                                    searchQuery = ""
                                    selectedGroupTitle = group.title
                                },
                            )
                        }
                    }
                }
            }
        }
    }
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
    onSortModeChange: (SortMode) -> Unit,
    onOpenSettings: () -> Unit,
    onBackToLibrary: () -> Unit,
    onOpenMusic: () -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val searchedSongs = remember(songs, searchQuery) {
        songs.filterBySearch(searchQuery)
    }
    val hasSearchQuery = searchQuery.isNotBlank()
    val highPerformanceMode = LocalHighPerformanceMode.current
    val backgroundModifier = if (highPerformanceMode) {
        modifier.background(MaterialTheme.colorScheme.background)
    } else {
        modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                    MaterialTheme.colorScheme.background,
                    MaterialTheme.colorScheme.background,
                ),
            ),
        )
    }

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
                    sortMode = uiState.sortMode,
                    playbackMode = uiState.playbackMode,
                    onPlayList = onPlayList,
                    onPlaybackModeChange = onPlaybackModeChange,
                    onSortModeChange = onSortModeChange,
                    onOpenSettings = onOpenSettings,
                    onBackToLibrary = onBackToLibrary,
                )
            }
            if (songs.isNotEmpty()) {
                item {
                    SearchField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
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
            ) { song ->
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
    onOpenSettings: () -> Unit,
    onBackToLibrary: () -> Unit,
    onOpenMusic: () -> Unit,
) {
    var selectedPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable(selectedPlaylistId) { mutableStateOf("") }
    val selectedPlaylist = uiState.playlists.firstOrNull { it.id == selectedPlaylistId }
    val playlistSongs = remember(selectedPlaylist, uiState.songs, uiState.hiddenFolderPaths) {
        selectedPlaylist?.songsFrom(uiState.songs, uiState.hiddenFolderPaths).orEmpty()
    }
    val displayedPlaylists = remember(uiState.playlists, searchQuery, selectedPlaylist) {
        if (selectedPlaylist == null) {
            uiState.playlists.filterByPlaylistSearch(searchQuery)
        } else {
            emptyList()
        }
    }
    val displayedPlaylistSongs = remember(playlistSongs, searchQuery) {
        playlistSongs.filterBySearch(searchQuery)
    }
    val hasSearchQuery = searchQuery.isNotBlank()
    val highPerformanceMode = LocalHighPerformanceMode.current
    val backgroundModifier = if (highPerformanceMode) {
        modifier.background(MaterialTheme.colorScheme.background)
    } else {
        modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                    MaterialTheme.colorScheme.background,
                    MaterialTheme.colorScheme.background,
                ),
            ),
        )
    }

    BackHandler(enabled = selectedPlaylistId != null) {
        selectedPlaylistId = null
    }

    Box(modifier = backgroundModifier) {
        if (!hasAudioPermission) {
            PermissionPanel(onRequestPermission = onRequestPermission)
            return@Box
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (selectedPlaylist == null) {
                item {
                    PlaylistsHeader(
                        title = "Playlist",
                        subtitle = uiState.playlists.size.playlistCountLabel(),
                        onBack = onBackToLibrary,
                        onOpenSettings = onOpenSettings,
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
                ) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        songs = playlist.songsFrom(uiState.songs, uiState.hiddenFolderPaths),
                        onClick = { selectedPlaylistId = playlist.id },
                        onDelete = { onDeletePlaylist(playlist.id) },
                    )
                }
            } else {
                item {
                    PlaylistDetailHeader(
                        playlist = selectedPlaylist,
                        songCount = playlistSongs.size,
                        playbackMode = uiState.playbackMode,
                        onBack = { selectedPlaylistId = null },
                        onOpenSettings = onOpenSettings,
                        onPlayList = { onPlayQueue(playlistSongs, PlaybackQueueSource.Playlist, selectedPlaylist.id) },
                        onPlaybackModeChange = onPlaybackModeChange,
                    )
                }
                if (playlistSongs.isNotEmpty()) {
                    item {
                        SearchField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                        )
                    }
                }
                if (playlistSongs.isEmpty()) {
                    item {
                        EmptyPlaylist(onOpenMusic = onOpenMusic)
                    }
                } else if (displayedPlaylistSongs.isEmpty() && hasSearchQuery) {
                    item {
                        EmptySearchResults(searchQuery = searchQuery)
                    }
                }
                items(
                    items = displayedPlaylistSongs,
                    key = { it.id },
                ) { song ->
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

@Composable
private fun PlaylistsHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Volver a Biblioteca",
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Abrir configuración",
            )
        }
    }
}

@Composable
private fun PlaylistDetailHeader(
    playlist: MusicPlaylist,
    songCount: Int,
    playbackMode: PlaybackMode,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlayList: () -> Unit,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PlaylistsHeader(
            title = playlist.name,
            subtitle = songCount.songCountLabel(),
            onBack = onBack,
            onOpenSettings = onOpenSettings,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onPlayList,
                enabled = songCount > 0,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reproducir playlist")
            }
            PlaybackModeButton(
                playbackMode = playbackMode,
                onPlaybackModeChange = onPlaybackModeChange,
            )
        }
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
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (songs.isNotEmpty()) {
            Artwork(song = songs.first(), modifier = Modifier.size(54.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(14.dp))
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
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = songs.size.songCountLabel(),
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

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .combinedClickable(
                    onClick = onClick,
                    onClickLabel = "Reproducir ${song.title}",
                    onLongClick = { showActions = true },
                    onLongClickLabel = "Mostrar opciones de ${song.title}",
                    role = Role.Button,
                )
                .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(song = song, modifier = Modifier.size(54.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${song.artist} - ${song.album}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (isPlaying) "Sonando" else formatDuration(song.durationMs),
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            IconButton(onClick = { showActions = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Mostrar opciones de ${song.title}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Quitar de playlist",
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
private fun PermissionPanel(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "Permite acceso a tu música",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "SoftMusic funciona offline leyendo los audios guardados en tu teléfono.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermission) {
            Text("Dar permiso")
        }
    }
}

@Composable
private fun LibraryHeader(
    songCount: Int,
    groupCount: Int,
    selectedView: MusicLibraryView,
    selectedGroupTitle: String?,
    searchQuery: String,
    sortMode: SortMode,
    playbackMode: PlaybackMode,
    folders: List<MusicFolder>,
    selectedFolderPath: String?,
    onRefreshLibrary: () -> Unit,
    onPlayList: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onViewChange: (MusicLibraryView) -> Unit,
    onClearGroup: () -> Unit,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
    onSortModeChange: (SortMode) -> Unit,
    onFolderChange: (String?) -> Unit,
    onOpenSettings: () -> Unit,
    onBackToLibrary: () -> Unit,
) {
    val subtitle = when {
        selectedGroupTitle != null -> "$selectedGroupTitle - ${songCount.songCountLabel()}"
        selectedView == MusicLibraryView.Songs -> "${songCount.songCountLabel()} offline"
        selectedView == MusicLibraryView.Artists -> groupCount.artistCountLabel()
        selectedView == MusicLibraryView.Albums -> groupCount.albumCountLabel()
        else -> "${songCount.songCountLabel()} offline"
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            IconButton(onClick = onBackToLibrary) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver a Biblioteca",
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Mi Música",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Abrir configuración",
                )
            }
        }

        if (selectedGroupTitle == null) {
            MusicLibraryViewSelector(
                selectedView = selectedView,
                onViewChange = onViewChange,
            )
        }

        SearchField(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
        )

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
            FolderMenu(
                folders = folders,
                selectedFolderPath = selectedFolderPath,
                onFolderChange = onFolderChange,
                modifier = Modifier.weight(1f),
            )
            SortMenu(
                sortMode = sortMode,
                onSortModeChange = onSortModeChange,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefreshLibrary) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Actualizar biblioteca",
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onPlayList,
                enabled = songCount > 0,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (selectedView == MusicLibraryView.Songs) "Reproducir lista" else "Reproducir grupo")
            }
            PlaybackModeButton(
                playbackMode = playbackMode,
                onPlaybackModeChange = onPlaybackModeChange,
            )
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Mostrar ${view.label.lowercase()}",
                    ) { onViewChange(view) }
                    .semantics {
                        stateDescription = if (selected) "Seleccionado" else "No seleccionado"
                    }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = view.label,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.44f)
                        .height(2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
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
    sortMode: SortMode,
    playbackMode: PlaybackMode,
    onPlayList: () -> Unit,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
    onSortModeChange: (SortMode) -> Unit,
    onOpenSettings: () -> Unit,
    onBackToLibrary: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            IconButton(onClick = onBackToLibrary) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver a Biblioteca",
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Favoritos",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = songCount.favoriteCountLabel(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Abrir configuración",
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SortMenu(
                sortMode = sortMode,
                onSortModeChange = onSortModeChange,
                modifier = Modifier.weight(1f),
            )
            PlaybackModeButton(
                playbackMode = playbackMode,
                onPlaybackModeChange = onPlaybackModeChange,
            )
        }

        Button(
            onClick = onPlayList,
            enabled = songCount > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reproducir favoritos")
        }
    }
}

@Composable
private fun SettingsSheet(
    themeMode: AppThemeMode,
    colorPalette: AppColorPalette,
    highPerformanceMode: Boolean,
    hasAudioPermission: Boolean,
    hasNotificationPermission: Boolean,
    allFolders: List<MusicFolder>,
    folders: List<MusicFolder>,
    defaultFolderPath: String?,
    defaultPlaybackMode: PlaybackMode,
    defaultSortMode: SortMode,
    fontScale: Float,
    hiddenFolderPaths: Set<String>,
    djModeEnabled: Boolean,
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
    onHiddenFolderChange: (String, Boolean) -> Unit,
    onDjModeChange: (Boolean) -> Unit,
    onDjMixDurationChange: (Int) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Configuración",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "Personaliza la app y sus valores iniciales.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsSection(
            title = "Diseño y Tema",
            subtitle = "Apariencia visual de SoftMusic.",
        ) {
            SettingBlock(title = "Tema") {
                ThemeModeMenu(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                )
            }

            SettingBlock(title = "Color") {
                ColorPaletteMenu(
                    colorPalette = colorPalette,
                    onColorPaletteChange = onColorPaletteChange,
                )
            }

            PerformanceModeToggle(
                highPerformanceMode = highPerformanceMode,
                onHighPerformanceModeChange = onHighPerformanceModeChange,
            )
        }

        SettingsSection(
            title = "Preferencias",
            subtitle = "Se aplican al iniciar la aplicación.",
        ) {
            SettingBlock(title = "Tamaño de letras") {
                FontScaleSlider(
                    fontScale = fontScale,
                    onFontScaleChange = onFontScaleChange,
                )
            }

            SettingBlock(title = "Carpeta predeterminada") {
                FolderMenu(
                    folders = folders,
                    selectedFolderPath = defaultFolderPath,
                    onFolderChange = onDefaultFolderChange,
                )
            }

            SettingBlock(title = "Ocultar carpetas") {
                HiddenFoldersBlock(
                    folders = allFolders,
                    hiddenFolderPaths = hiddenFolderPaths,
                    onHiddenFolderChange = onHiddenFolderChange,
                )
            }

            SettingBlock(title = "Filtro predeterminado") {
                PlaybackModeMenu(
                    playbackMode = defaultPlaybackMode,
                    onPlaybackModeChange = onDefaultPlaybackModeChange,
                )
            }

            SettingBlock(title = "Orden predeterminado") {
                SortMenu(
                    sortMode = defaultSortMode,
                    onSortModeChange = onDefaultSortModeChange,
                )
            }
        }

        SettingsSection(
            title = "Funciones",
            subtitle = "Herramientas experimentales de reproducción.",
        ) {
            DjModeBlock(
                enabled = djModeEnabled,
                mixDurationSeconds = djMixDurationSeconds,
                onEnabledChange = onDjModeChange,
                onMixDurationChange = onDjMixDurationChange,
            )
        }

        SettingsSection(
            title = "Permisos",
            subtitle = "Necesarios para biblioteca, notificaciones y pantalla de bloqueo.",
        ) {
            PermissionStatusBlock(
                title = "Música local",
                description = "Permite leer canciones guardadas en el teléfono.",
                granted = hasAudioPermission,
                actionLabel = "Dar permiso",
                onAction = onRequestAudioPermission,
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
    mixDurationSeconds: Int,
    onEnabledChange: (Boolean) -> Unit,
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
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highPerformanceMode) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f)
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
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
                text = "Tema: ${themeMode.label}",
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
                text = "Color: ${colorPalette.label}",
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
            OutlinedIconButton(onClick = { expanded = true }) {
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
private fun MusicGroupRow(
    group: MusicGroup,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            song = group.songs.first(),
            modifier = Modifier.size(54.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.title,
                style = MaterialTheme.typography.titleMedium,
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
) {
    var showActions by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .combinedClickable(
                    onClick = onClick,
                    onClickLabel = "Reproducir ${song.title}",
                    onLongClick = { showActions = true },
                    onLongClickLabel = "Mostrar opciones de ${song.title}",
                    role = Role.Button,
                )
                .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(
                song = song,
                modifier = Modifier.size(54.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${song.artist} - ${song.album}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (isPlaying) "Sonando" else formatDuration(song.durationMs),
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
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
    val highPerformanceMode = LocalHighPerformanceMode.current
    Surface(
        color = if (highPerformanceMode) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shadowElevation = if (highPerformanceMode) 0.dp else 18.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PlaybackProgress(
                positionMs = playbackProgress.positionMs,
                durationMs = playbackProgress.durationMs,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .clickable(onClick = onOpenPlayer)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(
                    song = song,
                    modifier = Modifier.size(52.dp),
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
                    )
                }
                IconButton(onClick = onTogglePlayPause) {
                    Icon(
                        imageVector = if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (uiState.isPlaying) "Pausar" else "Reproducir",
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Siguiente",
                    )
                }
            }
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
) {
    val song = uiState.currentSong ?: return
    val playbackProgress by playbackProgressState.collectAsStateWithLifecycle()
    val highPerformanceMode = LocalHighPerformanceMode.current
    var localSlider by remember(uiState.currentSong?.id) { mutableFloatStateOf(0f) }
    var isScrubbing by remember(uiState.currentSong?.id) { mutableStateOf(false) }
    var showSpinningDisc by remember(uiState.currentSong?.id) { mutableStateOf(true) }
    var showSongDetails by rememberSaveable(uiState.currentSong?.id) { mutableStateOf(false) }
    var showPlaybackQueue by rememberSaveable(uiState.currentSong?.id) { mutableStateOf(false) }
    val duration = max(playbackProgress.durationMs, 1L)
    val progress = (playbackProgress.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Reproduciendo ahora",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(28.dp))
        val artworkModifier = Modifier
                .fillMaxWidth(if (highPerformanceMode) 0.68f else 0.82f)
                .aspectRatio(1f)
                .clickable(enabled = !highPerformanceMode) {
                    showSpinningDisc = !showSpinningDisc
                }
        if (!highPerformanceMode && showSpinningDisc) {
            SpinningDiscArtwork(
                song = song,
                modifier = artworkModifier,
            )
        } else {
            Artwork(
                song = song,
                modifier = artworkModifier,
                rounded = 34.dp,
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = song.title,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = song.artist,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaybackModeButton(
                playbackMode = uiState.playbackMode,
                onPlaybackModeChange = onPlaybackModeChange,
            )
            OutlinedIconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (isFavorite) "Quitar de favoritos" else "Agregar a favoritos",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AddToPlaylistButton(
                playlists = playlists,
                song = song,
                onAddToPlaylist = onAddToPlaylist,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Slider(
            value = localSlider,
            onValueChange = {
                isScrubbing = true
                localSlider = it
            },
            onValueChangeFinished = {
                onSeek((localSlider * duration).toLong())
                isScrubbing = false
            },
            modifier = Modifier.semantics {
                stateDescription = "${formatDuration((localSlider * duration).toLong())} de ${formatDuration(playbackProgress.durationMs)}"
            },
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(formatDuration(playbackProgress.positionMs), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.weight(1f))
            Text(formatDuration(playbackProgress.durationMs), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { showSongDetails = true }) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "Ver detalles de la canción",
                )
            }
            IconButton(onClick = onPrevious) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Anterior",
                )
            }
            Button(
                onClick = onTogglePlayPause,
                modifier = Modifier.size(74.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(
                    imageVector = if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (uiState.isPlaying) "Pausar" else "Reproducir",
                    modifier = Modifier.size(34.dp),
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Siguiente",
                )
            }
            IconButton(onClick = { showPlaybackQueue = true }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = "Ver cola de reproducción",
                )
            }
        }
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
                        ) { index, song ->
                            PlaybackQueueItem(
                                index = index + 1,
                                song = song,
                                isCurrent = song.id == currentSong.id,
                            )
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
    val backgroundColor = if (isCurrent) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
        Color.Transparent
    }
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
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "discRotation")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5_500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "discRotationValue",
    )
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val discBrush = if (isDarkTheme) {
        Brush.radialGradient(
            colors = listOf(
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                Color(0xFF171B23),
            ),
        )
    } else {
        Brush.radialGradient(
            colors = listOf(
                Color(0xFF20242C),
                Color(0xFF0B0D10),
            ),
        )
    }
    val discBorderColor = if (isDarkTheme) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
    } else {
        Color.Transparent
    }
    val grooveColor = if (isDarkTheme) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
    }
    val labelBackgroundColor = if (isDarkTheme) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val centerHoleColor = if (isDarkTheme) {
        MaterialTheme.colorScheme.background.copy(alpha = 0.82f)
    } else {
        MaterialTheme.colorScheme.background.copy(alpha = 0.94f)
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(discBrush)
            .border(1.dp, discBorderColor, CircleShape)
            .graphicsLayer { rotationZ = rotation },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(0.88f)
                .clip(CircleShape)
                .background(grooveColor),
        )
        Box(
            modifier = Modifier
                .fillMaxSize(0.56f)
                .clip(CircleShape)
                .background(labelBackgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            if (song.artworkUri != null) {
                AsyncImage(
                    model = song.artworkUri,
                    contentDescription = "Caratula de ${song.title}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = song.title.firstOrNull()?.uppercaseChar()?.toString() ?: "S",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(centerHoleColor),
        )
    }
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

private fun PlayerUiState.visibleLibrarySongs(): List<Song> = songs.filterNot { it.folderPath in hiddenFolderPaths }

private fun MusicPlaylist.songsFrom(songs: List<Song>, hiddenFolderPaths: Set<String> = emptySet()): List<Song> {
    return orderedSongsFrom(songs)
        .filterNot { it.folderPath in hiddenFolderPaths }
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
