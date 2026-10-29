package com.softmusic.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.softmusic.app.data.MAX_PLAYLIST_NAME_LENGTH
import com.softmusic.app.data.Song
import com.softmusic.app.player.AutoDjSettings
import com.softmusic.app.player.DjMixMode
import com.softmusic.app.player.PlaybackMode
import com.softmusic.app.player.PlaybackProgressState
import com.softmusic.app.player.PlaybackQueueSource
import com.softmusic.app.player.PlayerUiState
import com.softmusic.app.player.SortMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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
    onPlayQueueSong: (Song) -> Unit,
    onRemoveFromQueue: (Song) -> Unit,
    onMoveQueuedSong: (Song, Int) -> Unit,
    onClearUpcomingQueue: () -> Unit,
    onSmartOrderQueue: () -> Unit,
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
    onRenamePlaylist: (String, String) -> Boolean,
    onReorderPlaylist: (String, List<Long>, List<Long>) -> Boolean,
    onAddSongsToPlaylist: (String, List<Long>) -> Int,
    onRemoveSongsFromPlaylist: (String, Set<Long>) -> Int,
    onCyclePlaylistShuffleMode: (String) -> Unit,
    onHistoryEnabledChange: (Boolean) -> Unit,
    onClearListeningHistory: () -> Unit,
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
    isCheckingForUpdates: Boolean,
    updateCheckStatus: String?,
    onCheckForUpdates: () -> Unit,
    onCloseApp: () -> Unit,
) {
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var destination by rememberSaveable { mutableStateOf(LibraryDestination.Music) }
    var requestedLibraryFilter by rememberSaveable { mutableStateOf<LibraryCollectionFilter?>(null) }
    var libraryFilterRequestId by rememberSaveable { mutableLongStateOf(0L) }
    var browseView by rememberSaveable { mutableStateOf(MusicLibraryView.Songs) }
    var browseGroupTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var browseReturnDestination by rememberSaveable { mutableStateOf(LibraryDestination.Music) }
    var browseDirectGroup by rememberSaveable { mutableStateOf(false) }
    var browseWholeLibrary by rememberSaveable { mutableStateOf(false) }
    var collectionReturnDestination by rememberSaveable { mutableStateOf(LibraryDestination.Library) }
    val screenStateHolder = rememberSaveableStateHolder()
    var selectedPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var historyView by rememberSaveable { mutableStateOf(ListeningHistoryView.Recent) }
    var historyReturnDestination by rememberSaveable { mutableStateOf(LibraryDestination.Music) }
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

    // Keep restoration ownership and slot order here; navigation decisions are pure.
    fun navigationState() = AppNavigationState(
        destination = destination,
        requestedLibraryFilter = requestedLibraryFilter,
        libraryFilterRequestId = libraryFilterRequestId,
        browseView = browseView,
        browseGroupTitle = browseGroupTitle,
        browseReturnDestination = browseReturnDestination,
        browseDirectGroup = browseDirectGroup,
        browseWholeLibrary = browseWholeLibrary,
        collectionReturnDestination = collectionReturnDestination,
        selectedPlaylistId = selectedPlaylistId,
        historyView = historyView,
        historyReturnDestination = historyReturnDestination,
    )

    fun applyNavigation(state: AppNavigationState) {
        destination = state.destination
        requestedLibraryFilter = state.requestedLibraryFilter
        libraryFilterRequestId = state.libraryFilterRequestId
        browseView = state.browseView
        browseGroupTitle = state.browseGroupTitle
        browseReturnDestination = state.browseReturnDestination
        browseDirectGroup = state.browseDirectGroup
        browseWholeLibrary = state.browseWholeLibrary
        collectionReturnDestination = state.collectionReturnDestination
        selectedPlaylistId = state.selectedPlaylistId
        historyView = state.historyView
        historyReturnDestination = state.historyReturnDestination
    }

    fun openMusicBrowse(
        view: MusicLibraryView = MusicLibraryView.Songs,
        groupTitle: String? = null,
    ) {
        applyNavigation(navigationState().openMusicBrowse(view, groupTitle))
    }

    fun returnFromBrowse() {
        applyNavigation(navigationState().returnFromBrowse())
    }

    fun openLibraryAlbums() {
        applyNavigation(navigationState().openLibraryAlbums())
    }

    fun openFavorites() {
        applyNavigation(navigationState().openFavorites())
    }

    fun openPlaylist(playlistId: String) {
        applyNavigation(navigationState().openPlaylist(playlistId))
    }

    fun openHistory(view: ListeningHistoryView) {
        applyNavigation(navigationState().openHistory(view))
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
        applyNavigation(navigationState().onBack())
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
                                selectedDestination = navigationState().selectedNavigationDestination,
                                onDestinationChange = { selectedDestination ->
                                    applyNavigation(navigationState().selectDestination(selectedDestination))
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
                            val screenKey = navigationState().screenKey
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
                                            onPlaybackModeChange = onPlaybackModeChange,
                                            onDismissSession = onDismissRestoredSession,
                                            onCreatePlaylist = ::createPlaylistWithAlert,
                                            onOpenLibrary = { destination = LibraryDestination.Library },
                                            onRefreshLibrary = onRefreshLibrary,
                                            onOpenHistory = ::openHistory,
                                            onPlayHistorySong = { song, songs, view ->
                                                onPlayQueuedSong(song, songs, PlaybackQueueSource.History, view.key)
                                            },
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
                                                applyNavigation(navigationState().selectBrowseView(it))
                                            },
                                            onSelectedGroupChange = {
                                                applyNavigation(navigationState().selectBrowseGroup(it))
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
                                                applyNavigation(navigationState().useSelectedFolder())
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
                                            onBack = { applyNavigation(navigationState().returnFromCollection()) },
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
                                                applyNavigation(navigationState().returnFromPlaylist())
                                            },
                                            hasAudioPermission = hasAudioPermission,
                                            onRequestPermission = onRequestPermission,
                                            onCreatePlaylist = ::createPlaylistWithAlert,
                                            onDeletePlaylist = onDeletePlaylist,
                                            onSetPlaylistCover = ::setPlaylistCoverWithAlert,
                                            onRemoveSongFromPlaylist = ::removeSongFromPlaylistWithUndo,
                                            onRenamePlaylist = onRenamePlaylist,
                                            onReorderPlaylist = onReorderPlaylist,
                                            onAddSongsToPlaylist = onAddSongsToPlaylist,
                                            onRemoveSongsFromPlaylist = onRemoveSongsFromPlaylist,
                                            onPlayQueuedSong = onPlayQueuedSong,
                                            onPlayQueue = onPlayQueue,
                                            onToggleFavorite = ::toggleFavoriteWithAlert,
                                            onPlayNext = ::playNextWithAlert,
                                            onPlayAtEnd = ::playAtEndWithAlert,
                                            onAddSongToPlaylist = ::addSongToPlaylistWithAlert,
                                            onCyclePlaylistShuffleMode = onCyclePlaylistShuffleMode,
                                            onOpenMusic = { openMusicBrowse() },
                                        )
                                        LibraryDestination.History -> ListeningHistoryScreen(
                                            modifier = Modifier.fillMaxSize().padding(transparentBottomPadding),
                                            uiState = uiState,
                                            view = historyView,
                                            onViewChange = { applyNavigation(navigationState().selectHistoryView(it)) },
                                            onBack = { applyNavigation(navigationState().returnFromHistory()) },
                                            contentBottomPadding = padding.calculateBottomPadding() + 24.dp,
                                            onPlayHistorySong = { song, songs, view ->
                                                onPlayQueuedSong(song, songs, PlaybackQueueSource.History, view.key)
                                            },
                                            onPlayHistoryQueue = { songs, view ->
                                                onPlayQueue(songs, PlaybackQueueSource.History, view.key)
                                            },
                                            onToggleFavorite = ::toggleFavoriteWithAlert,
                                            onPlayNext = ::playNextWithAlert,
                                            onPlayAtEnd = ::playAtEndWithAlert,
                                            onAddSongToPlaylist = ::addSongToPlaylistWithAlert,
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
                                                historyEnabled = uiState.listeningHistory.enabled,
                                                historyHasEntries = uiState.listeningHistory.entries.isNotEmpty(),
                                                onHistoryEnabledChange = onHistoryEnabledChange,
                                                onClearListeningHistory = onClearListeningHistory,
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
                            onSmartOrderQueue = onSmartOrderQueue,
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
                        historyEnabled = uiState.listeningHistory.enabled,
                        historyHasEntries = uiState.listeningHistory.entries.isNotEmpty(),
                        onHistoryEnabledChange = onHistoryEnabledChange,
                        onClearListeningHistory = onClearListeningHistory,
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
