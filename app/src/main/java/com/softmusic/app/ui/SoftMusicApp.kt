@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.softmusic.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.softmusic.app.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import com.softmusic.app.data.MAX_PLAYLIST_NAME_LENGTH
import com.softmusic.app.data.MusicFolder
import com.softmusic.app.data.MusicPlaylist
import com.softmusic.app.data.Song
import com.softmusic.app.data.isIncludedBySmallAudioFilter
import com.softmusic.app.data.orderedSongsFrom
import com.softmusic.app.player.AutoDjSettings
import com.softmusic.app.player.DjMixMode
import com.softmusic.app.player.PlaybackMode
import com.softmusic.app.player.PlaybackProgressState
import com.softmusic.app.player.playbackQueueOriginLabel
import com.softmusic.app.player.PlaybackQueueSource
import com.softmusic.app.player.PlayerUiState
import com.softmusic.app.player.SortMode
import com.softmusic.app.player.WebPanelServerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.Normalizer
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

internal val LocalHighPerformanceMode = compositionLocalOf { false }
internal val LocalNavigationBackEnabled = compositionLocalOf { true }
private const val FULL_DISC_ROTATION_DEGREES = 360f
private const val DISC_ROTATION_DURATION_MS = 5_500f
private const val VINYL_VIEWBOX_SIZE = 300f

private enum class LibraryDestination {
    Music,
    Browse,
    Search,
    Library,
    Favorites,
    Playlists,
    Settings,
}

internal enum class MusicLibraryView(val label: String) {
    Songs("Canciones"),
    Artists("Artistas"),
    Albums("Álbumes"),
}

internal data class MusicGroup(
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
    webPanelState: WebPanelServerState,
    onRequestPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRefreshLibrary: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onPlayList: () -> Unit,
    onPlayQueuedSong: (Song, List<Song>, PlaybackQueueSource, String?) -> Unit,
    onPlayQueue: (List<Song>, PlaybackQueueSource, String?) -> Unit,
    onPlayQueueSong: (Song) -> Unit,
    onRemoveFromQueue: (Song) -> Unit,
    onMoveQueuedSong: (Song, Int) -> Unit,
    onClearUpcomingQueue: () -> Unit,
    onCreatePlaylistFromQueue: (String) -> Unit,
    onAddQueueToPlaylist: (String) -> Int,
    onResumeRestoredSession: () -> Unit,
    onDismissRestoredSession: () -> Unit,
    onStartSleepTimer: (Long) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onPlayNext: (Song) -> Unit,
    onPlayAtEnd: (Song) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onSetPlaylistCover: (String, Long?) -> Unit,
    onAddSongToPlaylist: (String, Long) -> Unit,
    onRemoveSongFromPlaylist: (String, Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
    onSortModeChange: (SortMode) -> Unit,
    onFolderChange: (String?) -> Unit,
    highPerformanceMode: Boolean,
    defaultFolderPath: String?,
    defaultPlaybackMode: PlaybackMode,
    defaultSortMode: SortMode,
    fontScale: Float,
    djModeEnabled: Boolean,
    djMixMode: DjMixMode,
    djMixDurationSeconds: Int,
    autoDjSettings: AutoDjSettings,
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
    onAutoDjSettingsChange: (AutoDjSettings) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenDonation: () -> Unit,
    onStartWebPanel: () -> Unit,
    onStopWebPanel: () -> Unit,
    isCheckingForUpdates: Boolean,
    updateCheckStatus: String?,
    onCheckForUpdates: () -> Unit,
    onCloseApp: () -> Unit,
) {
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var destination by rememberSaveable { mutableStateOf(LibraryDestination.Music) }
    var requestedLibraryFilter by rememberSaveable { mutableStateOf<LibraryCollectionFilter?>(null) }
    var libraryFilterRequestId by rememberSaveable { mutableStateOf(0L) }
    var browseView by rememberSaveable { mutableStateOf(MusicLibraryView.Songs) }
    var browseGroupTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var browseReturnDestination by rememberSaveable { mutableStateOf(LibraryDestination.Music) }
    var browseDirectGroup by rememberSaveable { mutableStateOf(false) }
    var browseWholeLibrary by rememberSaveable { mutableStateOf(false) }
    var collectionReturnDestination by rememberSaveable { mutableStateOf(LibraryDestination.Library) }
    val screenStateHolder = rememberSaveableStateHolder()
    var selectedPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    val playerVisibility = remember { MutableTransitionState(false) }
    playerVisibility.targetState = showPlayer && uiState.currentSong != null
    val playerOverlayVisible = playerVisibility.currentState || playerVisibility.targetState || !playerVisibility.isIdle
    val miniPlayerVisible = uiState.currentSong != null && destination != LibraryDestination.Music
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(showPlayer) {
        if (showPlayer) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val homeDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val homeDrawerScope = rememberCoroutineScope()
    val visibleLibrarySongs = remember(uiState.songs, uiState.hiddenFolderPaths, uiState.excludeSmallAudios) {
        uiState.visibleLibrarySongs()
    }
    val sortedLibrarySongs = remember(
        uiState.songs,
        uiState.hiddenFolderPaths,
        uiState.excludeSmallAudios,
        uiState.sortMode,
    ) {
        visibleLibrarySongs.sortedForDisplay(uiState.sortMode)
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

    fun createQueuePlaylistWithAlert(name: String): Boolean {
        val cleanName = name.trim()
        return when {
            uiState.playbackQueue.isEmpty() -> {
                showTransientAlert("No hay cola para guardar")
                false
            }
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
                onCreatePlaylistFromQueue(cleanName)
                showTransientAlert("Cola guardada como playlist")
                true
            }
        }
    }

    fun addSongToPlaylistWithAlert(playlistId: String, songId: Long) {
        onAddSongToPlaylist(playlistId, songId)
        showTransientAlert("Se agregó a Playlist")
    }

    fun setPlaylistCoverWithAlert(playlistId: String, songId: Long?) {
        onSetPlaylistCover(playlistId, songId)
        showTransientAlert(if (songId == null) "Portada automática restaurada" else "Portada actualizada")
    }

    fun addQueueToPlaylistWithAlert(playlistId: String): Int {
        val addedCount = onAddQueueToPlaylist(playlistId)
        val message = when (addedCount) {
            0 -> "La playlist ya contiene toda la cola"
            1 -> "Se agregó 1 canción a la playlist"
            else -> "Se agregaron $addedCount canciones a la playlist"
        }
        showTransientAlert(message)
        return addedCount
    }

    fun playNextWithAlert(song: Song) {
        onPlayNext(song)
        showTransientAlert("Se reproducirá después")
    }

    fun playAtEndWithAlert(song: Song) {
        onPlayAtEnd(song)
        showTransientAlert("Se agregó al final de la cola")
    }

    fun removeSongFromPlaylistWithUndo(playlistId: String, songId: Long) {
        onRemoveSongFromPlaylist(playlistId, songId)
        showTransientAlert(
            message = "Se quitó de la playlist",
            actionLabel = "Deshacer",
            action = { onAddSongToPlaylist(playlistId, songId) },
        )
    }

    fun openMusicBrowse(
        view: MusicLibraryView = MusicLibraryView.Songs,
        groupTitle: String? = null,
    ) {
        browseReturnDestination = destination
        browseView = view
        browseGroupTitle = groupTitle
        browseDirectGroup = groupTitle != null
        browseWholeLibrary = view != MusicLibraryView.Songs
        destination = LibraryDestination.Browse
    }

    fun returnFromBrowse() {
        if (browseGroupTitle != null && !browseDirectGroup) {
            browseGroupTitle = null
        } else {
            destination = browseReturnDestination
        }
    }

    fun openLibraryAlbums() {
        requestedLibraryFilter = LibraryCollectionFilter.Albums
        libraryFilterRequestId += 1L
        destination = LibraryDestination.Library
    }

    fun openFavorites() {
        collectionReturnDestination = destination
        destination = LibraryDestination.Favorites
    }

    fun openPlaylist(playlistId: String) {
        collectionReturnDestination = destination
        selectedPlaylistId = playlistId
        destination = LibraryDestination.Playlists
    }

    fun openHomeDrawer() {
        homeDrawerScope.launch { homeDrawerState.open() }
    }

    fun closeHomeDrawerAndRun(action: () -> Unit) {
        homeDrawerScope.launch { homeDrawerState.close() }
        action()
    }

    LaunchedEffect(transientAlert?.id) {
        val alertId = transientAlert?.id ?: return@LaunchedEffect
        delay(2_000L)
        if (transientAlert?.id == alertId) {
            transientAlert = null
        }
    }

    LaunchedEffect(uiState.sleepTimerFinishedEventId) {
        if (uiState.sleepTimerFinishedEventId > 0L) {
            showTransientAlert("Temporizador finalizado")
        }
    }

    BackHandler(enabled = playerOverlayVisible) {
        showPlayer = false
    }
    BackHandler(enabled = showSettings) {
        showSettings = false
    }
    BackHandler(enabled = destination != LibraryDestination.Music && !playerOverlayVisible && !showSettings) {
        when (destination) {
            LibraryDestination.Browse -> returnFromBrowse()
            LibraryDestination.Favorites, LibraryDestination.Playlists -> destination = collectionReturnDestination
            else -> destination = LibraryDestination.Music
        }
    }
    BackHandler(enabled = homeDrawerState.isOpen) {
        homeDrawerScope.launch { homeDrawerState.close() }
    }

    val navigationBlurState = remember { HazeState() }
    CompositionLocalProvider(
        LocalHighPerformanceMode provides highPerformanceMode,
        LocalNavigationBackEnabled provides (!playerOverlayVisible && !showSettings && !homeDrawerState.isOpen),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ModalNavigationDrawer(
                modifier = if (playerOverlayVisible) Modifier.clearAndSetSemantics {} else Modifier,
                drawerState = homeDrawerState,
                drawerContent = {
                    HomeSideDrawer(
                        onOpenProfile = {
                            closeHomeDrawerAndRun { showTransientAlert("Perfil estará disponible pronto") }
                        },
                        onOpenUpdates = {
                            closeHomeDrawerAndRun {
                                showTransientAlert("Buscando actualizaciones")
                                onCheckForUpdates()
                            }
                        },
                        onOpenNotifications = {
                            closeHomeDrawerAndRun(onOpenNotificationSettings)
                        },
                        onOpenSettings = {
                            closeHomeDrawerAndRun { destination = LibraryDestination.Settings }
                        },
                        onCloseApp = {
                            closeHomeDrawerAndRun(onCloseApp)
                        },
                    )
                },
            ) {
                Scaffold(
            bottomBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (miniPlayerVisible) {
                        MiniPlayer(
                            uiState = uiState,
                            playbackProgressState = playbackProgressState,
                            progressUpdatesEnabled = !playerOverlayVisible,
                            onOpenPlayer = { showPlayer = true },
                            onPrevious = onPrevious,
                            onTogglePlayPause = onTogglePlayPause,
                            onNext = onNext,
                        )
                    }
                    SpotifyBottomNavigation(
                        blurState = navigationBlurState,
                        selectedDestination = when (destination) {
                            LibraryDestination.Browse -> when (browseReturnDestination) {
                                LibraryDestination.Favorites, LibraryDestination.Playlists -> collectionReturnDestination
                                else -> browseReturnDestination
                            }
                            LibraryDestination.Favorites, LibraryDestination.Playlists -> collectionReturnDestination
                            else -> destination
                        },
                        onDestinationChange = { selectedDestination ->
                            selectedPlaylistId = null
                            destination = selectedDestination
                            showSettings = false
                        },
                    )
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier.fillMaxSize().then(
                    if (highPerformanceMode) Modifier else Modifier.haze(navigationBlurState),
                ),
            ) {
            val layoutDirection = LocalLayoutDirection.current
            val transparentBottomPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection),
                top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(layoutDirection),
                bottom = 0.dp,
            )
            if (!hasAudioPermission) {
                PermissionPanel(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(transparentBottomPadding),
                    showRationale = showAudioPermissionRationale,
                    permanentlyDenied = audioPermissionPermanentlyDenied,
                    onRequestPermission = onRequestPermission,
                    onOpenAppSettings = onOpenAppSettings,
                )
            } else {
                val screenKey = when (destination) {
                    LibraryDestination.Browse -> "browse:${browseView.name}:${browseGroupTitle ?: "list"}"
                    LibraryDestination.Playlists -> "playlist:${selectedPlaylistId ?: "list"}"
                    else -> destination.name
                }
                ScreenEntrance(screenKey = screenKey, modifier = Modifier.fillMaxSize()) {
                    screenStateHolder.SaveableStateProvider(key = screenKey) {
            when (destination) {
                LibraryDestination.Music -> HomeScreen(
                    uiState = uiState,
                    playbackProgressState = playbackProgressState,
                    progressUpdatesEnabled = !playerOverlayVisible && !showSettings && !homeDrawerState.isOpen,
                    modifier = Modifier.fillMaxSize().padding(transparentBottomPadding),
                    contentBottomPadding = padding.calculateBottomPadding() + 24.dp,
                    onOpenMenu = ::openHomeDrawer,
                    onOpenSongs = { openMusicBrowse() },
                    onOpenAlbums = ::openLibraryAlbums,
                    onOpenFavorites = ::openFavorites,
                    onOpenPlaylist = ::openPlaylist,
                    onOpenAlbum = { openMusicBrowse(MusicLibraryView.Albums, it) },
                    onOpenPlayer = { showPlayer = true },
                    onPrevious = onPrevious,
                    onTogglePlayPause = {
                        if (uiState.restoredSessionAvailable) onResumeRestoredSession() else onTogglePlayPause()
                    },
                    onNext = onNext,
                    onDismissSession = onDismissRestoredSession,
                    onCreatePlaylist = ::createPlaylistWithAlert,
                    onOpenLibrary = { destination = LibraryDestination.Library },
                    onRefreshLibrary = onRefreshLibrary,
                )
                LibraryDestination.Browse -> MusicLibraryScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(transparentBottomPadding),
                    uiState = if (browseWholeLibrary) {
                        uiState.copy(
                            visibleSongs = sortedLibrarySongs,
                            selectedFolderPath = null,
                        )
                    } else uiState,
                    selectedView = browseView,
                    selectedGroupTitle = browseGroupTitle,
                    onSelectedViewChange = {
                        browseView = it
                        browseGroupTitle = null
                        browseDirectGroup = false
                    },
                    onSelectedGroupChange = {
                        browseGroupTitle = it
                        browseDirectGroup = false
                    },
                    onBack = ::returnFromBrowse,
                    contentBottomPadding = padding.calculateBottomPadding() + 24.dp,
                    hasAudioPermission = hasAudioPermission,
                    onRequestPermission = onRequestPermission,
                    onRefreshLibrary = onRefreshLibrary,
                    onPlaySong = onPlaySong,
                    onPlayList = onPlayList,
                    onPlayQueuedSong = onPlayQueuedSong,
                    onPlayQueue = onPlayQueue,
                    onToggleFavorite = ::toggleFavoriteWithAlert,
                    onPlayNext = ::playNextWithAlert,
                    onPlayAtEnd = ::playAtEndWithAlert,
                    onAddSongToPlaylist = ::addSongToPlaylistWithAlert,
                    onPlaybackModeChange = onPlaybackModeChange,
                    onSortModeChange = onSortModeChange,
                    onFolderChange = {
                        browseWholeLibrary = false
                        onFolderChange(it)
                    },
                    onOpenHomeMenu = ::openHomeDrawer,
                    onOpenSettings = { destination = LibraryDestination.Settings },
                )
                LibraryDestination.Search -> SearchLibraryScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(transparentBottomPadding),
                    uiState = uiState,
                    onPlayQueuedSong = onPlayQueuedSong,
                    onPlayQueue = onPlayQueue,
                    onToggleFavorite = ::toggleFavoriteWithAlert,
                    onPlayNext = ::playNextWithAlert,
                    onPlayAtEnd = ::playAtEndWithAlert,
                    onAddSongToPlaylist = ::addSongToPlaylistWithAlert,
                )
                LibraryDestination.Library -> LocalLibraryScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(transparentBottomPadding),
                    uiState = uiState,
                    contentBottomPadding = padding.calculateBottomPadding() + 24.dp,
                    requestedFilter = requestedLibraryFilter,
                    filterRequestId = libraryFilterRequestId,
                    onOpenMenu = ::openHomeDrawer,
                    onCreatePlaylist = ::createPlaylistWithAlert,
                    onOpenFavorites = ::openFavorites,
                    onOpenPlaylist = ::openPlaylist,
                    onOpenAlbum = { openMusicBrowse(MusicLibraryView.Albums, it) },
                    onOpenArtist = { openMusicBrowse(MusicLibraryView.Artists, it) },
                    onOpenSongs = { openMusicBrowse() },
                )
                LibraryDestination.Favorites -> FavoritesLibraryScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(transparentBottomPadding),
                    uiState = uiState,
                    contentBottomPadding = padding.calculateBottomPadding() + 24.dp,
                    songs = favoriteSongs,
                    hasAudioPermission = hasAudioPermission,
                    onRequestPermission = onRequestPermission,
                    onPlaySong = { song ->
                        onPlayQueuedSong(song, favoriteSongs, PlaybackQueueSource.Favorites, null)
                    },
                    onPlayList = { onPlayQueue(favoriteSongs, PlaybackQueueSource.Favorites, null) },
                    onToggleFavorite = ::toggleFavoriteWithAlert,
                    onPlayNext = ::playNextWithAlert,
                    onPlayAtEnd = ::playAtEndWithAlert,
                    onAddSongToPlaylist = ::addSongToPlaylistWithAlert,
                    onPlaybackModeChange = onPlaybackModeChange,
                    onBack = { destination = collectionReturnDestination },
                    onOpenMusic = { openMusicBrowse() },
                )
                LibraryDestination.Playlists -> PlaylistsLibraryScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(transparentBottomPadding),
                    uiState = uiState,
                    contentBottomPadding = padding.calculateBottomPadding() + 24.dp,
                    selectedPlaylistId = selectedPlaylistId,
                    onSelectedPlaylistChange = { selectedPlaylistId = it },
                    onBackFromPlaylist = {
                        selectedPlaylistId = null
                        destination = collectionReturnDestination
                    },
                    hasAudioPermission = hasAudioPermission,
                    onRequestPermission = onRequestPermission,
                    onCreatePlaylist = ::createPlaylistWithAlert,
                    onDeletePlaylist = onDeletePlaylist,
                    onSetPlaylistCover = ::setPlaylistCoverWithAlert,
                    onRemoveSongFromPlaylist = ::removeSongFromPlaylistWithUndo,
                    onPlayQueuedSong = onPlayQueuedSong,
                    onPlayQueue = onPlayQueue,
                    onToggleFavorite = ::toggleFavoriteWithAlert,
                    onPlayNext = ::playNextWithAlert,
                    onPlayAtEnd = ::playAtEndWithAlert,
                    onAddSongToPlaylist = ::addSongToPlaylistWithAlert,
                    onPlaybackModeChange = onPlaybackModeChange,
                    onOpenMusic = { openMusicBrowse() },
                )
                LibraryDestination.Settings -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(transparentBottomPadding)
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    SettingsSheet(
                        highPerformanceMode = highPerformanceMode,
                        hasAudioPermission = hasAudioPermission,
                        audioPermissionPermanentlyDenied = audioPermissionPermanentlyDenied,
                        hasNotificationPermission = hasNotificationPermission,
                        webPanelState = webPanelState,
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
                        autoDjSettings = autoDjSettings,
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
                        onAutoDjSettingsChange = onAutoDjSettingsChange,
                        onOpenNotificationSettings = onOpenNotificationSettings,
                        onOpenAppSettings = onOpenAppSettings,
                        onOpenDonation = onOpenDonation,
                        onStartWebPanel = onStartWebPanel,
                        onStopWebPanel = onStopWebPanel,
                        isCheckingForUpdates = isCheckingForUpdates,
                        updateCheckStatus = updateCheckStatus,
                        onCheckForUpdates = onCheckForUpdates,
                    )
                }
            }
            }
            }
            }
            }
                }
        }

        if (playerOverlayVisible) {
            Box(
                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        }
                    }
                },
            )
        }

        AnimatedVisibility(
            visibleState = playerVisibility,
            modifier = Modifier.fillMaxSize().align(Alignment.Center),
            enter = if (highPerformanceMode) EnterTransition.None else {
                fadeIn(tween(220)) + slideInVertically(tween(280)) { it / 4 }
            },
            exit = if (highPerformanceMode) ExitTransition.None else {
                fadeOut(tween(180)) + slideOutVertically(tween(240)) { it / 5 }
            },
            label = "fullPlayerVisibility",
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                FullPlayer(
                    uiState = uiState,
                    playbackProgressState = playbackProgressState,
                    isFavorite = uiState.currentSong?.id in uiState.favoriteSongIds,
                    playlists = uiState.playlists,
                    onPlayQueueSong = onPlayQueueSong,
                    onRemoveFromQueue = onRemoveFromQueue,
                    onMoveQueuedSong = onMoveQueuedSong,
                    onClearUpcomingQueue = {
                        onClearUpcomingQueue()
                        showTransientAlert("Se limpió la cola siguiente")
                    },
                    onCreatePlaylistFromQueue = ::createQueuePlaylistWithAlert,
                    onAddQueueToPlaylist = ::addQueueToPlaylistWithAlert,
                    onStartSleepTimer = onStartSleepTimer,
                    onCancelSleepTimer = onCancelSleepTimer,
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
                    highPerformanceMode = highPerformanceMode,
                    hasAudioPermission = hasAudioPermission,
                    audioPermissionPermanentlyDenied = audioPermissionPermanentlyDenied,
                    hasNotificationPermission = hasNotificationPermission,
                    webPanelState = webPanelState,
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
                    autoDjSettings = autoDjSettings,
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
                    onAutoDjSettingsChange = onAutoDjSettingsChange,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onOpenAppSettings = onOpenAppSettings,
                    onOpenDonation = onOpenDonation,
                    onStartWebPanel = onStartWebPanel,
                    onStopWebPanel = onStopWebPanel,
                    isCheckingForUpdates = isCheckingForUpdates,
                    updateCheckStatus = updateCheckStatus,
                    onCheckForUpdates = onCheckForUpdates,
                )
            }
        }

            if (!playerOverlayVisible) transientAlert?.let { alert ->
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
                            bottom = if (miniPlayerVisible) 116.dp else 80.dp,
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
    blurState: HazeState,
    selectedDestination: LibraryDestination,
    onDestinationChange: (LibraryDestination) -> Unit,
) {
    val selectedNavigationDestination = if (selectedDestination == LibraryDestination.Playlists) {
        LibraryDestination.Library
    } else {
        selectedDestination
    }
    val destinations = listOf(
        LibraryDestination.Music,
        LibraryDestination.Search,
        LibraryDestination.Library,
    )
    val surface = MaterialTheme.colorScheme.surface
    val highPerformanceMode = LocalHighPerformanceMode.current
    NavigationBar(
        modifier = Modifier.height(56.dp).hazeChild(blurState) {
            backgroundColor = surface
            tints = listOf(HazeTint(surface.copy(alpha = 0.44f)))
            blurRadius = 20.dp
            noiseFactor = 0.025f
            blurEnabled = !highPerformanceMode
        },
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        destinations.forEach { destination ->
            val iconScale by animateFloatAsState(
                targetValue = if (selectedNavigationDestination == destination) 1.12f else 1f,
                animationSpec = softMusicMotionSpec(),
                label = "navigationIconScale",
            )
            NavigationBarItem(
                selected = selectedNavigationDestination == destination,
                onClick = { onDestinationChange(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon(selected = selectedNavigationDestination == destination),
                        contentDescription = destination.label(),
                        modifier = Modifier.size(20.dp).graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                    )
                },
                label = {
                    Text(
                        text = destination.label(),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

private fun LibraryDestination.label(): String = when (this) {
    LibraryDestination.Music -> "Inicio"
    LibraryDestination.Browse -> "Tu música"
    LibraryDestination.Search -> "Buscar"
    LibraryDestination.Library -> "Biblioteca"
    LibraryDestination.Favorites -> "Favoritos"
    LibraryDestination.Playlists -> "Playlists"
    LibraryDestination.Settings -> "Ajustes"
}

@Composable
private fun LibraryDestination.icon(selected: Boolean = false): ImageVector = when (this) {
    LibraryDestination.Music -> if (selected) MusicIcons.HomeSelected else MusicIcons.Home
    LibraryDestination.Browse -> if (selected) MusicIcons.LibrarySelected else MusicIcons.Library
    LibraryDestination.Search -> if (selected) MusicIcons.SearchSelected else MusicIcons.Search
    LibraryDestination.Library -> if (selected) MusicIcons.LibrarySelected else MusicIcons.Library
    LibraryDestination.Favorites -> if (selected) MusicIcons.FavoritesSelected else MusicIcons.Favorites
    LibraryDestination.Playlists -> if (selected) MusicIcons.PlaylistSelected else MusicIcons.Playlist
    LibraryDestination.Settings -> if (selected) MusicIcons.SettingsSelected else MusicIcons.Settings
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
                        icon = MusicIcons.Home,
                        title = "Inicio",
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
                icon = MusicIcons.Folder,
                enabled = true,
                onClick = onOpenMusic,
            )
            LibraryOptionCard(
                title = "Favoritos",
                subtitle = favoriteCount.favoriteCountLabel(),
                icon = MusicIcons.Favorites,
                enabled = true,
                onClick = onOpenFavorites,
            )
            LibraryOptionCard(
                title = "Playlist",
                subtitle = playlistCount.playlistCountLabel(),
                icon = MusicIcons.Playlist,
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
    selectedView: MusicLibraryView,
    selectedGroupTitle: String?,
    onSelectedViewChange: (MusicLibraryView) -> Unit,
    onSelectedGroupChange: (String?) -> Unit,
    onBack: () -> Unit,
    contentBottomPadding: Dp,
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
    onOpenHomeMenu: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val groups = remember(uiState.visibleSongs, selectedView, uiState.sortMode) {
        uiState.visibleSongs.toMusicGroups(selectedView, uiState.sortMode)
    }
    val selectedGroup = groups.firstOrNull { it.title == selectedGroupTitle }
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
    val isSongsCollection = selectedView == MusicLibraryView.Songs
    val isCollectionDetail = isSongsCollection || isGroupDetail
    val selectedFolderName = uiState.selectedFolderPath?.let { path ->
        uiState.folders.firstOrNull { it.path == path }?.name
            ?: uiState.allFolders.firstOrNull { it.path == path }?.name
            ?: path.trimEnd('/').substringAfterLast('/').ifBlank { "Carpeta seleccionada" }
    }
    val showingSongs = selectedView == MusicLibraryView.Songs || selectedGroup != null
    val playableSongs = when {
        selectedView == MusicLibraryView.Songs -> uiState.visibleSongs
        selectedGroup != null -> selectedGroup.songs
        else -> emptyList()
    }
    val backgroundModifier = modifier.background(MaterialTheme.colorScheme.background)

    BackHandler(enabled = selectedGroupTitle != null && LocalNavigationBackEnabled.current) {
        onBack()
    }

    Box(
        modifier = backgroundModifier,
    ) {
        if (!hasAudioPermission) {
            PermissionPanel(onRequestPermission = onRequestPermission)
            return@Box
        }

        LibraryPullToRefresh(
            isRefreshing = uiState.isLoading,
            onRefresh = onRefreshLibrary,
        ) { listModifier ->
        LazyColumn(
            modifier = listModifier,
            contentPadding = if (isCollectionDetail) {
                PaddingValues(bottom = contentBottomPadding)
            } else {
                PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = contentBottomPadding)
            },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isSongsCollection) {
                item(key = "songs-collection-header") {
                    CollectionDetailHeader(
                        title = "Tus canciones",
                        songs = playableSongs,
                        subtitle = selectedFolderName?.let { "Carpeta · $it" } ?: "Todas las carpetas",
                        playbackMode = uiState.playbackMode,
                        onBack = onBack,
                        onPlay = {
                            onPlayQueue(playableSongs, PlaybackQueueSource.Songs, uiState.selectedFolderPath)
                        },
                        onPlaybackModeChange = onPlaybackModeChange,
                        playDescription = "Reproducir tus canciones",
                        leadingActions = {
                            FolderMenu(
                                folders = uiState.folders,
                                selectedFolderPath = uiState.selectedFolderPath,
                                onFolderChange = onFolderChange,
                                trigger = { openMenu ->
                                    IconButton(
                                        onClick = openMenu,
                                        modifier = Modifier.semantics {
                                            stateDescription = selectedFolderName ?: "Todas las carpetas"
                                        },
                                    ) {
                                        Icon(
                                            imageVector = MusicIcons.Folder,
                                            contentDescription = "Elegir carpeta",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                },
                            )
                            SortMenu(
                                sortMode = uiState.sortMode,
                                onSortModeChange = onSortModeChange,
                                trigger = { openMenu ->
                                    IconButton(
                                        onClick = openMenu,
                                        modifier = Modifier.semantics { stateDescription = uiState.sortMode.label },
                                    ) {
                                        Icon(
                                            imageVector = MusicIcons.Sort,
                                            contentDescription = "Ordenar canciones",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                },
                            )
                        },
                    )
                }
            } else if (selectedArtist != null) {
                item {
                    ArtistDetailHeader(
                        artist = selectedArtist,
                        playbackMode = uiState.playbackMode,
                        onBack = onBack,
                        onPlayArtist = {
                            onPlayQueue(
                                playableSongs,
                                playbackSource,
                                playbackSourceKey,
                            )
                        },
                        onPlaybackModeChange = onPlaybackModeChange,
                    )
                }
            } else if (selectedAlbum != null) {
                item {
                    AlbumDetailHeader(
                        album = selectedAlbum,
                        playbackMode = uiState.playbackMode,
                        onBack = onBack,
                        onPlayAlbum = {
                            onPlayQueue(
                                playableSongs,
                                playbackSource,
                                playbackSourceKey,
                            )
                        },
                        onPlaybackModeChange = onPlaybackModeChange,
                    )
                }
            }

            if (!isCollectionDetail) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(MusicIcons.Back, contentDescription = "Volver")
                        }
                        Text("Tu música", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
                stickyHeader {
                    LibraryTopRow(
                        songCount = playableSongs.size,
                        selectedView = selectedView,
                        isPlaying = uiState.isPlaying,
                        folders = uiState.folders,
                        selectedFolderPath = uiState.selectedFolderPath,
                        onOpenHomeMenu = onOpenHomeMenu,
                        onPlayList = {
                            onPlayQueue(
                                playableSongs,
                                playbackSource,
                                playbackSourceKey,
                            )
                        },
                        onViewChange = { view ->
                            onSelectedViewChange(view)
                        },
                        onFolderChange = onFolderChange,
                    )
                }
            }

            if (uiState.isLoading) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                            .then(if (isSongsCollection) Modifier.padding(horizontal = 20.dp) else Modifier),
                    )
                }
            }

            uiState.errorMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        modifier = if (isSongsCollection) Modifier.padding(horizontal = 20.dp) else Modifier,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (!uiState.isLoading && uiState.visibleSongs.isEmpty()) {
                item {
                    Box(modifier = if (isSongsCollection) Modifier.padding(horizontal = 20.dp) else Modifier) {
                        if (uiState.songs.isNotEmpty() && uiState.hiddenFolderPaths.isNotEmpty()) {
                            EmptyHiddenFolders(onOpenSettings = onOpenSettings)
                        } else {
                            EmptyLibrary(onRefreshLibrary = onRefreshLibrary)
                        }
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
                                .then(if (isCollectionDetail) Modifier.padding(horizontal = 20.dp) else Modifier),
                        ) {
                            SongRow(
                                song = song,
                                isCurrent = uiState.currentSong?.id == song.id,
                                isPlaying = uiState.currentSong?.id == song.id && uiState.isPlaying,
                                isFavorite = song.id in uiState.favoriteSongIds,
                                playlists = uiState.playlists,
                                songs = uiState.songs,
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
                        key = { "${selectedView.name}:${it.title}" },
                        contentType = { "group" },
                    ) { group ->
                        Box(modifier = Modifier.animateItem()) {
                            MusicGroupRow(
                                group = group,
                                onClick = { onSelectedGroupChange(group.title) },
                            )
                        }
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
                    icon = MusicIcons.Search,
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
                        key = { "artist:${it.title}" },
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
                        key = { "album:${it.title}" },
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
                                songs = uiState.songs,
                                onClick = {
                                    val folderSongs = librarySongs
                                        .filter { it.folderPath == song.folderPath }
                                        .ifEmpty { listOf(song) }
                                    onPlayQueuedSong(
                                        song,
                                        folderSongs,
                                        PlaybackQueueSource.Songs,
                                        song.folderPath,
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
private fun LibraryModuleScreen(
    modifier: Modifier = Modifier,
    uiState: PlayerUiState,
    onCreatePlaylist: (String) -> Boolean,
    onOpenFavorites: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    val librarySongs = remember(uiState.songs, uiState.hiddenFolderPaths, uiState.excludeSmallAudios) {
        uiState.visibleLibrarySongs()
    }
    val favoriteSongs = remember(uiState.songs, uiState.favoriteSongIds, uiState.sortMode, uiState.hiddenFolderPaths, uiState.excludeSmallAudios) {
        uiState.favoriteSongs()
    }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ModuleTitle(
                    icon = MusicIcons.Library,
                    title = "Biblioteca",
                    compact = true,
                )
            }

            item { SearchSectionTitle("Favoritos") }
            item {
                LibraryCollectionRow(
                    title = "Mis favoritos",
                    subtitle = favoriteSongs.size.favoriteCountLabel(),
                    icon = MusicIcons.Favorites,
                    featuredFavoriteArtwork = true,
                    onClick = onOpenFavorites,
                )
            }
            item {
                LibrarySectionTitle(
                    title = "Playlists",
                    action = {
                        IconButton(onClick = { showCreatePlaylistDialog = true }) {
                            Icon(
                                imageVector = MusicIcons.Add,
                                contentDescription = "Crear playlist",
                            )
                        }
                    },
                )
            }
            items(
                items = uiState.playlists,
                key = { it.id },
                contentType = { "library-playlist" },
            ) { playlist ->
                val playlistSongs = playlist.songsFrom(uiState.songs, uiState.hiddenFolderPaths, uiState.excludeSmallAudios)
                LibraryCollectionRow(
                    title = playlist.name,
                    subtitle = "Playlist • ${playlistSongs.size.songCountLabel()}",
                    icon = MusicIcons.Playlist,
                    coverSong = playlist.coverSongFrom(playlistSongs),
                    onClick = { onOpenPlaylist(playlist.id) },
                )
            }
        }

        if (showCreatePlaylistDialog) {
            CreatePlaylistDialog(
                existingPlaylistNames = uiState.playlists.map { it.name },
                onCreatePlaylist = onCreatePlaylist,
                onDismiss = { showCreatePlaylistDialog = false },
            )
        }
    }
}

@Composable
private fun LibrarySectionTitle(
    title: String,
    action: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        action?.invoke()
    }
}

@Composable
private fun LibraryCollectionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    coverSong: Song? = null,
    featuredFavoriteArtwork: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = "Abrir $title",
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            featuredFavoriteArtwork -> FavoriteLibraryArtwork(modifier = Modifier.size(72.dp))
            coverSong != null -> Artwork(song = coverSong, modifier = Modifier.size(72.dp), rounded = 10.dp)
            else -> {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun FavoriteLibraryArtwork(
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val shape = RoundedCornerShape(if (large) 12.dp else 10.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFF477E),
                        Color(0xFF7C3AED),
                        Color(0xFF00C2FF),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.34f), Color.Transparent),
                    center = Offset(size.width * 0.22f, size.height * 0.18f),
                    radius = size.maxDimension * 0.72f,
                ),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFD166).copy(alpha = 0.36f), Color.Transparent),
                    center = Offset(size.width * 0.82f, size.height * 0.78f),
                    radius = size.maxDimension * 0.68f,
                ),
            )
        }
        Icon(
            imageVector = MusicIcons.Favorites,
            contentDescription = null,
            modifier = Modifier.size(if (large) 74.dp else 34.dp),
            tint = Color.White,
        )
    }
}

@Composable
private fun FavoritesLibraryScreen(
    modifier: Modifier = Modifier,
    uiState: PlayerUiState,
    contentBottomPadding: Dp,
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
    onBack: () -> Unit,
    onOpenMusic: () -> Unit,
) {
    val backgroundModifier = modifier.background(MaterialTheme.colorScheme.background)

    Box(modifier = backgroundModifier) {
        if (!hasAudioPermission) {
            PermissionPanel(onRequestPermission = onRequestPermission)
            return@Box
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = contentBottomPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                FavoritesDetailHeader(
                    songs = songs,
                    isPlaying = uiState.isPlaying,
                    playbackMode = uiState.playbackMode,
                    onBack = onBack,
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
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        EmptyFavorites(onOpenMusic = onOpenMusic)
                    }
                }
            }

            items(
                items = songs,
                key = { song -> song.id },
                contentType = { "song" },
            ) { song ->
                Column(
                    modifier = Modifier
                        .animateItem()
                        .padding(horizontal = 20.dp),
                ) {
                    SongRow(
                        song = song,
                        isCurrent = uiState.currentSong?.id == song.id,
                        isPlaying = uiState.currentSong?.id == song.id && uiState.isPlaying,
                        isFavorite = song.id in uiState.favoriteSongIds,
                        playlists = uiState.playlists,
                        songs = uiState.songs,
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
    contentBottomPadding: Dp,
    selectedPlaylistId: String?,
    onSelectedPlaylistChange: (String?) -> Unit,
    onBackFromPlaylist: () -> Unit,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
    onCreatePlaylist: (String) -> Boolean,
    onDeletePlaylist: (String) -> Unit,
    onSetPlaylistCover: (String, Long?) -> Unit,
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

    BackHandler(enabled = selectedPlaylistId != null && LocalNavigationBackEnabled.current) {
        onBackFromPlaylist()
    }

    Box(modifier = backgroundModifier) {
        if (!hasAudioPermission) {
            PermissionPanel(onRequestPermission = onRequestPermission)
            return@Box
        }

        LazyColumn(
            contentPadding = if (selectedPlaylist == null) {
                PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = contentBottomPadding)
            } else {
                PaddingValues(bottom = contentBottomPadding)
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
                    CreatePlaylistCard(
                        existingPlaylistNames = uiState.playlists.map { it.name },
                        onCreatePlaylist = onCreatePlaylist,
                    )
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
                            onClick = { onSelectedPlaylistChange(playlist.id) },
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
                        onBack = onBackFromPlaylist,
                        onPlayList = { onPlayQueue(playlistSongs, PlaybackQueueSource.Playlist, selectedPlaylist.id) },
                        onSetCover = { songId -> onSetPlaylistCover(selectedPlaylist.id, songId) },
                        onDelete = {
                            onDeletePlaylist(selectedPlaylist.id)
                            onBackFromPlaylist()
                        },
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
                    key = { song -> song.id },
                    contentType = { "playlistSong" },
                ) { song ->
                    Column(
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
                            songs = uiState.songs,
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
                    imageVector = MusicIcons.Back,
                    contentDescription = "Volver",
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ModuleTitle(
                icon = MusicIcons.Playlist,
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
    onSetCover: (Long?) -> Unit,
    onDelete: () -> Unit,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
) {
    var showCoverPicker by rememberSaveable(playlist.id) { mutableStateOf(false) }
    var showDeleteConfirmation by rememberSaveable(playlist.id) { mutableStateOf(false) }
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Eliminar playlist") },
            text = { Text("¿Eliminar ${playlist.name}? Las canciones permanecerán en tu dispositivo.") },
            confirmButton = {
                Button(onClick = {
                    showDeleteConfirmation = false
                    onDelete()
                }) { Text("Eliminar") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmation = false }) { Text("Cancelar") }
            },
        )
    }
    if (showCoverPicker) {
        PlaylistCoverPickerDialog(
            playlist = playlist,
            songs = songs,
            onSelectCover = { songId ->
                onSetCover(songId)
                showCoverPicker = false
            },
            onDismiss = { showCoverPicker = false },
        )
    }
    CollectionDetailHeader(
        title = playlist.name,
        songs = songs,
        subtitle = "Playlist",
        playbackMode = playbackMode,
        onBack = onBack,
        onPlay = onPlayList,
        onPlaybackModeChange = onPlaybackModeChange,
        coverSongId = playlist.coverSongId,
        playDescription = "Reproducir playlist",
        onChangeCover = { showCoverPicker = true },
        onDelete = { showDeleteConfirmation = true },
    )
}

@Composable
private fun PlaylistCoverPickerDialog(
    playlist: MusicPlaylist,
    songs: List<Song>,
    onSelectCover: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar portada") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Elige una canción de esta playlist para usar su carátula como portada.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "automaticCover", contentType = "coverOption") {
                        PlaylistAutomaticCoverRow(
                            selected = playlist.coverSongId == null,
                            onClick = { onSelectCover(null) },
                        )
                    }
                    items(
                        items = songs,
                        key = { it.id },
                        contentType = { "coverSong" },
                    ) { song ->
                        PlaylistCoverSongRow(
                            song = song,
                            selected = playlist.coverSongId == song.id,
                            onClick = { onSelectCover(song.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
    )
}

@Composable
private fun PlaylistAutomaticCoverRow(
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MusicIcons.Playlist,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Portada automática",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Usa la primera carátula disponible",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Icon(
                imageVector = MusicIcons.CheckSelected,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun PlaylistCoverSongRow(
    song: Song,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            song = song,
            modifier = Modifier.size(46.dp),
            rounded = 12.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
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
        if (selected) {
            Icon(
                imageVector = MusicIcons.CheckSelected,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
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
    CollectionDetailHeader(
        title = artist.title,
        songs = artist.songs,
        subtitle = "Artista • ${artist.songs.map { it.album }.distinct().size.albumCountLabel()}",
        playbackMode = playbackMode,
        onBack = onBack,
        onPlay = onPlayArtist,
        onPlaybackModeChange = onPlaybackModeChange,
        artist = true,
        playDescription = "Reproducir artista",
    )
}

@Composable
private fun AlbumDetailHeader(
    album: MusicGroup,
    playbackMode: PlaybackMode,
    onBack: () -> Unit,
    onPlayAlbum: () -> Unit,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
) {
    CollectionDetailHeader(
        title = album.title,
        songs = album.songs,
        subtitle = "Álbum • ${album.subtitle}",
        playbackMode = playbackMode,
        onBack = onBack,
        onPlay = onPlayAlbum,
        onPlaybackModeChange = onPlaybackModeChange,
        playDescription = "Reproducir álbum",
    )
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
private fun CreatePlaylistCard(
    existingPlaylistNames: List<String>,
    onCreatePlaylist: (String) -> Boolean,
) {
    var showCreatePlaylist by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(
        onClick = { showCreatePlaylist = true },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(imageVector = MusicIcons.Add, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Crear playlist")
    }
    if (showCreatePlaylist) {
        CreatePlaylistDialog(
            existingPlaylistNames = existingPlaylistNames,
            onCreatePlaylist = onCreatePlaylist,
            onDismiss = { showCreatePlaylist = false },
        )
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
                    imageVector = MusicIcons.Playlist,
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
                imageVector = MusicIcons.Delete,
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
    songs: List<Song>,
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
                    imageVector = MusicIcons.MoreVertical,
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
            songs = songs,
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
private fun LibraryTopRow(
    songCount: Int,
    selectedView: MusicLibraryView,
    isPlaying: Boolean,
    folders: List<MusicFolder>,
    selectedFolderPath: String?,
    onOpenHomeMenu: () -> Unit,
    onPlayList: () -> Unit,
    onViewChange: (MusicLibraryView) -> Unit,
    onFolderChange: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeMenuButton(onClick = onOpenHomeMenu)
        MusicLibraryViewSelector(
            selectedView = selectedView,
            onViewChange = onViewChange,
        )
        Spacer(modifier = Modifier.weight(1f))
        CompactLibraryActionButton(
            onClick = onPlayList,
            enabled = songCount > 0,
            selected = songCount > 0,
            badgeVisible = isPlaying,
            icon = MusicIcons.Play,
            contentDescription = if (selectedView == MusicLibraryView.Songs) "Reproducir lista" else "Reproducir grupo",
        )
        FolderMenu(
            folders = folders,
            selectedFolderPath = selectedFolderPath,
            onFolderChange = onFolderChange,
            modifier = Modifier.size(30.dp),
            iconOnly = true,
        )
    }
}

@Composable
private fun CompactLibraryActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    selected: Boolean = false,
    badgeVisible: Boolean = false,
) {
    val backgroundColor = when {
        selected -> MaterialTheme.colorScheme.primary
        enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
    }
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    }

    Box(
        modifier = Modifier.size(30.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(backgroundColor)
                .then(
                    if (enabled) {
                        Modifier.clickable(
                            role = Role.Button,
                            onClickLabel = contentDescription,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(17.dp),
                tint = contentColor,
            )
        }
        if (badgeVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun LibraryActionRow(
    songCount: Int,
    selectedView: MusicLibraryView,
    isPlaying: Boolean,
    folders: List<MusicFolder>,
    selectedFolderPath: String?,
    onRefreshLibrary: () -> Unit,
    onPlayList: () -> Unit,
    onFolderChange: (String?) -> Unit,
) {
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
                    imageVector = MusicIcons.Play,
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
                imageVector = MusicIcons.Reload,
                contentDescription = "Actualizar biblioteca",
            )
        }
    }
}

@Composable
internal fun HomeMenuButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .semantics { contentDescription = "Abrir menú de inicio" }
            .clickable(role = Role.Button, onClickLabel = "Abrir menú de inicio", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = R.drawable.favicon,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(44.dp),
        )
    }
}

@Composable
private fun HomeSideDrawer(
    onOpenProfile: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloseApp: () -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier
            .fillMaxHeight()
            .width(264.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MusicIcons.ListView,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column {
                    Text(
                        text = "Inicio",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "Menú principal",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HomeDrawerItem(label = "Perfil", onClick = onOpenProfile)
            HomeDrawerItem(label = "Actualizaciones", onClick = onOpenUpdates)
            HomeDrawerItem(label = "Avisos", onClick = onOpenNotifications)
            HomeDrawerItem(label = "Ajustes", onClick = onOpenSettings)
            Spacer(modifier = Modifier.weight(1f))
            HomeDrawerItem(label = "Cerrar aplicación", onClick = onCloseApp)
        }
    }
}

@Composable
private fun HomeDrawerItem(
    label: String,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = {
            Text(
                text = label,
                fontWeight = FontWeight.SemiBold,
            )
        },
        selected = false,
        onClick = onClick,
    )
}

@Composable
private fun MusicLibraryViewSelector(
    selectedView: MusicLibraryView,
    onViewChange: (MusicLibraryView) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
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
                    modifier = Modifier
                        .padding(horizontal = 5.dp, vertical = 4.dp),
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
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
                imageVector = MusicIcons.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = MusicIcons.Close,
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
private fun FavoritesDetailHeader(
    songs: List<Song>,
    isPlaying: Boolean,
    playbackMode: PlaybackMode,
    onBack: () -> Unit,
    onPlayList: () -> Unit,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
) {
    CollectionDetailHeader(
        title = "Tus favoritos",
        songs = songs,
        subtitle = "Favoritos",
        playbackMode = playbackMode,
        onBack = onBack,
        onPlay = onPlayList,
        onPlaybackModeChange = onPlaybackModeChange,
        favorite = true,
        playDescription = "Reproducir favoritos",
        isPlaying = isPlaying,
    )
}

@Composable
private fun FavoriteHeroBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFF477E),
                    Color(0xFF7C3AED),
                    Color(0xFF101B4E),
                ),
            ),
        ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFD166).copy(alpha = 0.44f), Color.Transparent),
                    center = Offset(size.width * 0.12f, size.height * 0.18f),
                    radius = size.maxDimension * 0.54f,
                ),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF00C2FF).copy(alpha = 0.42f), Color.Transparent),
                    center = Offset(size.width * 0.9f, size.height * 0.22f),
                    radius = size.maxDimension * 0.62f,
                ),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFF477E).copy(alpha = 0.34f), Color.Transparent),
                    center = Offset(size.width * 0.68f, size.height * 0.82f),
                    radius = size.maxDimension * 0.7f,
                ),
            )
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
    trigger: (@Composable (onClick: () -> Unit) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedFolder = folders.firstOrNull { it.path == selectedFolderPath }
    Box(modifier = modifier) {
        if (trigger != null) {
            trigger { expanded = true }
        } else if (iconOnly) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Elegir carpeta",
                    ) { expanded = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MusicIcons.Folder,
                    contentDescription = "Carpeta: ${selectedFolder?.name ?: "Todas"}",
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = MusicIcons.Folder,
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
    trigger: (@Composable (onClick: () -> Unit) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        if (trigger != null) {
            trigger { expanded = true }
        } else if (iconOnly) {
            OutlinedIconButton(onClick = { expanded = true }) {
                Icon(
                    imageVector = MusicIcons.Sort,
                    contentDescription = "Orden: ${sortMode.label}",
                )
            }
        } else {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = MusicIcons.Sort,
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
                imageVector = MusicIcons.Search,
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
    songs: List<Song>,
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
                    imageVector = MusicIcons.MoreVertical,
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
            songs = songs,
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
    songs: List<Song>,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayAtEnd: () -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    var showPlaylists by remember { mutableStateOf(false) }
    val songsById = remember(expanded, songs) {
        if (expanded) songs.associateBy { it.id } else emptyMap()
    }
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
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { showPlaylists = false }) {
                        Icon(
                            imageVector = MusicIcons.Back,
                            contentDescription = "Volver",
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Agregar a playlist",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Selecciona dónde guardar esta canción",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                when {
                    playlists.isEmpty() -> AddToPlaylistEmptyState("Crea una playlist primero")
                    availablePlaylists.isEmpty() -> AddToPlaylistEmptyState("Ya está en todas tus playlists")
                    else -> availablePlaylists.forEach { playlist ->
                        PlaylistPickerRow(
                            playlist = playlist,
                            songsById = songsById,
                            onClick = { onAddToPlaylist(playlist.id) },
                        )
                    }
                }
            }
        } else {
            DropdownMenuItem(
                text = { Text("Reproducir después") },
                leadingIcon = {
                    Icon(
                        imageVector = MusicIcons.PlayNext,
                        contentDescription = null,
                    )
                },
                onClick = onPlayNext,
            )
            DropdownMenuItem(
                text = { Text("Reproducir al final") },
                leadingIcon = {
                    Icon(
                        imageVector = MusicIcons.Queue,
                        contentDescription = null,
                    )
                },
                onClick = onPlayAtEnd,
            )
            DropdownMenuItem(
                text = { Text(if (isFavorite) "Quitar de favoritos" else "Agregar a favoritos") },
                leadingIcon = {
                    Icon(
                        imageVector = if (isFavorite) MusicIcons.FavoriteRemoveSelected else MusicIcons.FavoriteAdd,
                        contentDescription = null,
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = onToggleFavorite,
            )
            if (availablePlaylists.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text("Agregar a Playlist") },
                    leadingIcon = {
                        Icon(
                            imageVector = MusicIcons.PlaylistAdd,
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
                            imageVector = MusicIcons.Delete,
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
    songs: List<Song>,
    song: Song,
    onAddToPlaylist: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val songsById = remember(expanded, songs) {
        if (expanded) songs.associateBy { it.id } else emptyMap()
    }
    val availablePlaylists = remember(expanded, playlists, song.id) {
        if (expanded) playlists.filterNot { playlist -> song.id in playlist.songIds } else emptyList()
    }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = MusicIcons.PlaylistAdd,
                contentDescription = "Agregar a playlist",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(25.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Agregar a playlist",
                    modifier = Modifier.padding(horizontal = 10.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Selecciona dónde guardar esta canción",
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    playlists.isEmpty() -> AddToPlaylistEmptyState("Crea una playlist primero")
                    availablePlaylists.isEmpty() -> AddToPlaylistEmptyState("Ya está en todas tus playlists")
                    else -> availablePlaylists.forEach { playlist ->
                        PlaylistPickerRow(
                            playlist = playlist,
                            songsById = songsById,
                            onClick = {
                                expanded = false
                                onAddToPlaylist(playlist.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddToPlaylistEmptyState(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MusicIcons.PlaylistAdd,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlaylistPickerRow(
    playlist: MusicPlaylist,
    songsById: Map<Long, Song>,
    onClick: () -> Unit,
) {
    val playlistSongs = remember(playlist.songIds, songsById) {
        playlist.songIds.mapNotNull { songsById[it] }
    }
    val coverSong = remember(playlist.coverSongId, playlistSongs) { playlist.coverSongFrom(playlistSongs) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (coverSong != null) {
            Artwork(
                song = coverSong,
                modifier = Modifier.size(44.dp),
                rounded = 10.dp,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MusicIcons.PlaylistAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = playlistSongs.size.songCountLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Icon(
            imageVector = MusicIcons.PlaylistAdd,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun MiniPlayer(
    uiState: PlayerUiState,
    playbackProgressState: StateFlow<PlaybackProgressState>,
    progressUpdatesEnabled: Boolean = true,
    onOpenPlayer: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val song = uiState.currentSong ?: return
    val highPerformanceMode = LocalHighPerformanceMode.current
    val artwork = rememberArtworkAppearance(
        song.artworkUri.takeUnless { highPerformanceMode },
    )
    val artworkColor = artwork?.playbackColor
    val miniPlayerColor by animateColorAsState(
        targetValue = whitePlaybackColor(artworkColor?.let {
            lerp(MaterialTheme.colorScheme.surfaceVariant, it, 0.9f)
        } ?: MaterialTheme.colorScheme.surfaceVariant),
        animationSpec = softMusicMotionSpec(360),
        label = "miniPlayerArtworkColor",
    )
    val miniPlayerContentColor = Color.White
    val playPauseScale by animateFloatAsState(
        targetValue = if (uiState.isPlaying) 1.12f else 1f,
        animationSpec = softMusicMotionSpec(),
        label = "miniPlayerPlayPauseScale",
    )
    Surface(
        color = MaterialTheme.colorScheme.background,
        contentColor = miniPlayerContentColor,
        shadowElevation = 0.dp,
    ) {
        AnimatedArtworkBackdrop(
            color = miniPlayerColor,
            artwork = artwork,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(14.dp)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .clickable(onClick = onOpenPlayer)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedSongArtwork(
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
                        color = miniPlayerContentColor.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onPrevious) {
                    Icon(
                        imageVector = MusicIcons.Previous,
                        contentDescription = "Anterior",
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(onClick = onTogglePlayPause) {
                    Icon(
                        imageVector = if (uiState.isPlaying) MusicIcons.Pause else MusicIcons.Play,
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
                        imageVector = MusicIcons.Next,
                        contentDescription = "Siguiente",
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            MiniPlayerProgress(
                playbackProgressState = playbackProgressState,
                updatesEnabled = progressUpdatesEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
            }
        }
    }
}

@Composable
private fun MiniPlayerProgress(
    playbackProgressState: StateFlow<PlaybackProgressState>,
    updatesEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var retainedProgress by remember(playbackProgressState) {
        mutableStateOf(playbackProgressState.value)
    }
    val playbackProgress = if (updatesEnabled) {
        val currentProgress by playbackProgressState.collectAsStateWithLifecycle()
        SideEffect { retainedProgress = currentProgress }
        currentProgress
    } else {
        retainedProgress
    }
    PlaybackProgress(
        positionMs = playbackProgress.positionMs,
        durationMs = playbackProgress.durationMs,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullPlayer(
    uiState: PlayerUiState,
    playbackProgressState: StateFlow<PlaybackProgressState>,
    isFavorite: Boolean,
    playlists: List<MusicPlaylist>,
    onPlayQueueSong: (Song) -> Unit,
    onRemoveFromQueue: (Song) -> Unit,
    onMoveQueuedSong: (Song, Int) -> Unit,
    onClearUpcomingQueue: () -> Unit,
    onCreatePlaylistFromQueue: (String) -> Boolean,
    onAddQueueToPlaylist: (String) -> Int,
    onStartSleepTimer: (Long) -> Unit,
    onCancelSleepTimer: () -> Unit,
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
    val highPerformanceMode = LocalHighPerformanceMode.current
    var showVinylArtwork by remember(uiState.currentSong?.id) { mutableStateOf(false) }
    var showSongDetails by rememberSaveable(uiState.currentSong?.id) { mutableStateOf(false) }
    var showPlaybackQueue by rememberSaveable { mutableStateOf(false) }
    var showSleepTimerDialog by rememberSaveable { mutableStateOf(false) }
    val queueSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val queueHeightFraction = if (LocalConfiguration.current.screenHeightDp < 500) 1f else 0.72f
    val artwork = rememberArtworkAppearance(song.artworkUri.takeUnless { highPerformanceMode })
    val artworkColor = artwork?.color
    val playerBackground by animateColorAsState(
        targetValue = whitePlaybackColor(artwork?.playbackColor?.let {
            lerp(it, Color.Black, 0.12f)
        } ?: MaterialTheme.colorScheme.background),
        animationSpec = softMusicMotionSpec(360),
        label = "fullPlayerArtworkColor",
    )
    val primaryText = Color.White
    val playButtonColor = whitePlaybackColor(
        artworkColor ?: MaterialTheme.colorScheme.primary,
        minimumContrast = 4.5,
    )
    val mutedText = if (artworkColor != null) primaryText.copy(alpha = 0.85f)
        else MaterialTheme.colorScheme.onSurfaceVariant
    val timelineInactive = if (artworkColor != null) primaryText.copy(alpha = 0.24f)
        else MaterialTheme.colorScheme.surfaceVariant
    val sourceCaption = when (uiState.playbackQueueSource) {
        PlaybackQueueSource.Favorites -> "Reproduciendo desde favoritos"
        PlaybackQueueSource.Playlist -> "Reproduciendo desde playlist"
        PlaybackQueueSource.Artist -> "Reproduciendo desde artista"
        PlaybackQueueSource.Album -> "Reproduciendo desde álbum"
        PlaybackQueueSource.Songs -> if (uiState.playbackQueueSourceKey.isNullOrBlank()) {
            "Reproduciendo desde biblioteca"
        } else "Reproduciendo desde carpeta"
    }
    val djStatusLabel = if (uiState.djModeEnabled &&
        (uiState.djMixMode == DjMixMode.Classic || uiState.djMixMode == DjMixMode.Expert)
    ) "DJ activo" else null
    val shuffleActive = uiState.playbackMode == PlaybackMode.Shuffle
    val nextShuffleMode = if (shuffleActive) PlaybackMode.Ordered else PlaybackMode.Shuffle
    val nextRepeatMode = when (uiState.playbackMode) {
        PlaybackMode.RepeatList -> PlaybackMode.RepeatCurrent
        PlaybackMode.RepeatCurrent -> PlaybackMode.Ordered
        PlaybackMode.Ordered,
        PlaybackMode.Shuffle -> PlaybackMode.RepeatList
    }
    val favoriteTint by animateColorAsState(
        targetValue = if (isFavorite) MaterialTheme.colorScheme.primary else primaryText,
        animationSpec = softMusicMotionSpec(),
        label = "fullPlayerFavoriteTint",
    )
    val favoriteScale by animateFloatAsState(
        targetValue = if (isFavorite) 1.18f else 1f,
        animationSpec = if (highPerformanceMode) snap() else spring(),
        label = "fullPlayerFavoriteScale",
    )
    val playPauseScale by animateFloatAsState(
        targetValue = if (uiState.isPlaying) 1.06f else 1f,
        animationSpec = if (highPerformanceMode) snap() else spring(),
        label = "fullPlayerPlayPauseScale",
    )

    if (showSongDetails) {
        SongDetailsDialog(
            song = song,
            onDismiss = { showSongDetails = false },
        )
    }

    if (showPlaybackQueue) {
        ModalBottomSheet(
            onDismissRequest = { showPlaybackQueue = false },
            sheetState = queueSheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(queueHeightFraction)) {
            PlaybackQueueSheet(
                queue = uiState.playbackQueue,
                currentSong = song,
                playlists = playlists,
                songs = uiState.songs,
                sourceLabel = uiState.playbackQueueOriginLabel(),
                isPlaying = uiState.isPlaying,
                playbackMode = uiState.playbackMode,
                sleepTimerRemainingMs = uiState.sleepTimerRemainingMs,
                onPlaySong = onPlayQueueSong,
                onTogglePlayPause = onTogglePlayPause,
                onRemoveSong = onRemoveFromQueue,
                onMoveSong = onMoveQueuedSong,
                onClearUpcomingQueue = onClearUpcomingQueue,
                onCreatePlaylistFromQueue = onCreatePlaylistFromQueue,
                onAddQueueToPlaylist = onAddQueueToPlaylist,
                onToggleShuffle = { onPlaybackModeChange(nextShuffleMode) },
                onOpenSleepTimer = { showSleepTimerDialog = true },
                onDismiss = { showPlaybackQueue = false },
            )
            }
        }
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            remainingMs = uiState.sleepTimerRemainingMs,
            onStartTimer = { durationMs ->
                onStartSleepTimer(durationMs)
                showSleepTimerDialog = false
            },
            onCancelTimer = {
                onCancelSleepTimer()
                showSleepTimerDialog = false
            },
            onDismiss = { showSleepTimerDialog = false },
        )
    }

    AnimatedArtworkBackdrop(
        color = playerBackground,
        artwork = artwork,
        modifier = Modifier.fillMaxSize(),
    ) {
        FullPlayerContent(
            song = song,
            sourceCaption = sourceCaption,
            sourceName = uiState.playbackQueueOriginLabel(),
            isFavorite = isFavorite,
            isPlaying = uiState.isPlaying,
            playbackMode = uiState.playbackMode,
            playButtonColor = playButtonColor,
            mutedText = mutedText,
            favoriteTint = favoriteTint,
            favoriteScale = favoriteScale,
            playPauseScale = playPauseScale,
            djStatusLabel = djStatusLabel,
            timerRemainingMs = uiState.sleepTimerRemainingMs,
            onClose = onClose,
            onShowSongDetails = { showSongDetails = true },
            onToggleFavorite = onToggleFavorite,
            onTogglePlayPause = onTogglePlayPause,
            onPrevious = onPrevious,
            onNext = onNext,
            onToggleShuffle = { onPlaybackModeChange(nextShuffleMode) },
            onCycleRepeat = { onPlaybackModeChange(nextRepeatMode) },
            onShowSleepTimer = { showSleepTimerDialog = true },
            onShowQueue = { showPlaybackQueue = true },
            artwork = { layoutModifier ->
                val interactiveArtwork = layoutModifier.clickable(
                    enabled = !highPerformanceMode,
                    role = Role.Button,
                    onClickLabel = if (showVinylArtwork) "Mostrar portada" else "Mostrar vinilo",
                    onClick = { showVinylArtwork = !showVinylArtwork },
                )
                if (!highPerformanceMode && showVinylArtwork) {
                    SpinningDiscArtwork(
                        song = song,
                        isPlaying = uiState.isPlaying,
                        modifier = interactiveArtwork,
                    )
                } else {
                    AnimatedSongArtwork(song = song, modifier = interactiveArtwork, rounded = 12.dp)
                }
            },
            timeline = {
                PlayerTimeline(
                    songId = song.id,
                    playbackProgressState = playbackProgressState,
                    onSeek = onSeek,
                    activeColor = primaryText,
                    inactiveColor = timelineInactive,
                    timeColor = mutedText,
                )
            },
            addToPlaylist = {
                AddToPlaylistButton(
                    playlists = playlists,
                    songs = uiState.songs,
                    song = song,
                    onAddToPlaylist = onAddToPlaylist,
                )
            },
        )
    }
}

@Composable
private fun PlayerTimeline(
    songId: Long,
    playbackProgressState: StateFlow<PlaybackProgressState>,
    onSeek: (Long) -> Unit,
    activeColor: Color,
    inactiveColor: Color,
    timeColor: Color,
) {
    val playbackProgress by playbackProgressState.collectAsStateWithLifecycle()
    var scrubbedProgress by remember(songId) { mutableFloatStateOf(0f) }
    var isScrubbing by remember(songId) { mutableStateOf(false) }
    val duration = max(playbackProgress.durationMs, 1L)
    val actualProgress = (playbackProgress.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    val displayedProgress = if (isScrubbing) scrubbedProgress else actualProgress

    Column(modifier = Modifier.fillMaxWidth()) {
        ThinTimeline(
            value = displayedProgress,
            onValueChange = {
                isScrubbing = true
                scrubbedProgress = it
            },
            onValueChangeFinished = {
                onSeek((scrubbedProgress * duration).toLong())
                isScrubbing = false
            },
            activeColor = activeColor,
            inactiveColor = inactiveColor,
            thumbColor = activeColor,
            modifier = Modifier.semantics {
                stateDescription = "${formatDuration((displayedProgress * duration).toLong())} de ${formatDuration(playbackProgress.durationMs)}"
            },
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatDuration(playbackProgress.positionMs),
                color = timeColor,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatDuration(playbackProgress.durationMs),
                color = timeColor,
                style = MaterialTheme.typography.bodySmall,
            )
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
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    fun updateValue(x: Float, width: Int) {
        val safeWidth = width.coerceAtLeast(1)
        currentOnValueChange((x / safeWidth.toFloat()).coerceIn(0f, 1f))
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    down.consume()
                    updateValue(down.position.x, size.width)

                    drag(down.id) { change ->
                        change.consume()
                        updateValue(change.position.x, size.width)
                    }
                    currentOnValueChangeFinished()
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
private fun SleepTimerDialog(
    remainingMs: Long,
    onStartTimer: (Long) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        "15 min" to 15 * 60 * 1_000L,
        "30 min" to 30 * 60 * 1_000L,
        "45 min" to 45 * 60 * 1_000L,
        "60 min" to 60 * 60 * 1_000L,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Temporizador") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (remainingMs > 0L) {
                        "Activo: se pausará en ${formatDuration(remainingMs)}."
                    } else {
                        "SoftMusic pausará la reproducción cuando termine el tiempo."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                options.chunked(2).forEach { rowOptions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowOptions.forEach { (label, durationMs) ->
                            Button(
                                onClick = { onStartTimer(durationMs) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (remainingMs > 0L) {
                OutlinedButton(onClick = onCancelTimer) {
                    Text("Cancelar timer")
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
    )
}

@Composable
internal fun SaveQueueAsPlaylistDialog(
    existingPlaylistNames: List<String>,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var playlistName by rememberSaveable { mutableStateOf("") }
    val nameError = playlistNameValidationError(playlistName, existingPlaylistNames)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Guardar cola") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Crea una playlist con el orden actual de la cola.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it.take(MAX_PLAYLIST_NAME_LENGTH) },
                    label = { Text("Nombre de playlist") },
                    isError = playlistName.isNotEmpty() && nameError != null,
                    supportingText = if (playlistName.isNotEmpty() && nameError != null) {
                        { Text(nameError) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(playlistName) },
                enabled = nameError == null,
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
internal fun AddQueueToPlaylistDialog(
    playlists: List<MusicPlaylist>,
    songs: List<Song>,
    queue: List<Song>,
    onAddToPlaylist: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val songsById = remember(songs) { songs.associateBy { it.id } }
    val queueIds = remember(queue) { queue.map { it.id }.distinct() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar cola a playlist") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Se agregará la cola completa, incluyendo la canción actual. Las canciones repetidas se omiten.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (playlists.isEmpty()) {
                    AddToPlaylistEmptyState("Crea una playlist primero")
                } else {
                    playlists.forEach { playlist ->
                        QueuePlaylistPickerRow(
                            playlist = playlist,
                            songsById = songsById,
                            queueIds = queueIds,
                            onClick = { onAddToPlaylist(playlist.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
    )
}

@Composable
private fun QueuePlaylistPickerRow(
    playlist: MusicPlaylist,
    songsById: Map<Long, Song>,
    queueIds: List<Long>,
    onClick: () -> Unit,
) {
    val playlistSongs = remember(playlist.songIds, songsById) {
        playlist.songIds.mapNotNull { songsById[it] }
    }
    val coverSong = remember(playlist.coverSongId, playlistSongs) { playlist.coverSongFrom(playlistSongs) }
    val existingIds = remember(playlist.songIds) { playlist.songIds.toSet() }
    val missingCount = remember(queueIds, existingIds) { queueIds.count { it !in existingIds } }
    val enabled = missingCount > 0
    val subtitle = when (missingCount) {
        0 -> "Ya contiene toda la cola"
        1 -> "Agregará 1 canción nueva"
        else -> "Agregará $missingCount canciones nuevas"
    }
    val contentAlpha = if (enabled) 1f else 0.56f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (coverSong != null) {
            Artwork(
                song = coverSong,
                modifier = Modifier.size(44.dp),
                rounded = 10.dp,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.14f else 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MusicIcons.PlaylistAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = playlistSongs.size.songCountLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f * contentAlpha),
                maxLines = 1,
            )
        }
        Icon(
            imageVector = if (enabled) MusicIcons.PlaylistAdd else MusicIcons.CheckSelected,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f),
            modifier = Modifier.size(20.dp),
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
internal fun Artwork(
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

internal fun formatDuration(durationMs: Long): String {
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

internal fun Int.favoriteCountLabel(): String = when (this) {
    0 -> "Sin favoritos"
    1 -> "1 canción favorita"
    else -> "$this canciones favoritas"
}

internal fun Int.songCountLabel(): String = when (this) {
    1 -> "1 canción"
    else -> "$this canciones"
}

internal fun Int.artistCountLabel(): String = when (this) {
    1 -> "1 artista"
    else -> "$this artistas"
}

internal fun Int.albumCountLabel(): String = when (this) {
    1 -> "1 álbum"
    else -> "$this álbumes"
}

internal fun Int.playlistCountLabel(): String = when (this) {
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

internal fun PlayerUiState.favoriteSongs(): List<Song> = visibleLibrarySongs()
    .filter { it.id in favoriteSongIds }
    .sortedForDisplay(sortMode)

internal fun PlayerUiState.visibleLibrarySongs(): List<Song> = songs.filter {
    it.folderPath !in hiddenFolderPaths && it.isIncludedBySmallAudioFilter(excludeSmallAudios)
}

internal fun MusicPlaylist.songsFrom(
    songs: List<Song>,
    hiddenFolderPaths: Set<String> = emptySet(),
    excludeSmallAudios: Boolean = true,
): List<Song> {
    return orderedSongsFrom(songs)
        .filter { it.folderPath !in hiddenFolderPaths && it.isIncludedBySmallAudioFilter(excludeSmallAudios) }
}

internal fun MusicPlaylist.coverSongFrom(songs: List<Song>): Song? {
    return coverSongId?.let { coverId -> songs.firstOrNull { it.id == coverId } }
        ?: songs.firstOrNull { it.artworkUri != null }
        ?: songs.firstOrNull()
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

internal fun String.normalizedSearchText(): String {
    return Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
}

internal fun List<Song>.toMusicGroups(
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

internal fun List<Song>.sortedForDisplay(sortMode: SortMode): List<Song> = when (sortMode) {
    SortMode.Recent -> sortedWith(compareByDescending<Song> { it.dateAddedSeconds }.thenBy { it.title.lowercase() })
    SortMode.Title -> sortedWith(compareBy<Song> { it.title.lowercase() }.thenBy { it.artist.lowercase() })
}

private fun PlaybackMode.next(): PlaybackMode {
    val modes = PlaybackMode.entries
    return modes[(ordinal + 1) % modes.size]
}

@Composable
private fun PlaybackMode.icon(): ImageVector = when (this) {
    PlaybackMode.Ordered -> MusicIcons.ListView
    PlaybackMode.RepeatList -> MusicIcons.RepeatAllSelected
    PlaybackMode.RepeatCurrent -> MusicIcons.RepeatOneSelected
    PlaybackMode.Shuffle -> MusicIcons.ShuffleSelected
}
