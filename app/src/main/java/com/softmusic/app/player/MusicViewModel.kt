package com.softmusic.app.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.softmusic.app.BuildConfig
import com.softmusic.app.backup.BackupDocument
import com.softmusic.app.backup.BackupPreferences
import com.softmusic.app.backup.BackupSong
import com.softmusic.app.backup.BackupSongMatch
import com.softmusic.app.backup.matchBackupSongs
import com.softmusic.app.backup.validateBackupDocument
import com.softmusic.app.data.MusicPlaylist
import com.softmusic.app.data.MusicRepository
import com.softmusic.app.data.MusicLibraryLoader
import com.softmusic.app.data.MusicLibraryMonitor
import com.softmusic.app.data.ListeningHistoryRepository
import com.softmusic.app.data.PlaylistMutation
import com.softmusic.app.data.Song
import com.softmusic.app.data.isIncludedBySmallAudioFilter
import com.softmusic.app.data.withNewPlaylist
import com.softmusic.app.data.withPlaylistCover
import com.softmusic.app.data.withQueueAddedToPlaylist
import com.softmusic.app.data.withSongAddedToPlaylist
import com.softmusic.app.data.withSongRemovedFromPlaylist
import com.softmusic.app.data.withoutPlaylist
import com.softmusic.app.data.withPlaylistRenamed
import com.softmusic.app.data.withPlaylistSongsReordered
import com.softmusic.app.data.withSongsAddedToPlaylist
import com.softmusic.app.data.withoutSongsFromPlaylist
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MusicRepository()
    private val persistenceStore = MusicPersistenceStore(application)
    private val historyRepository = ListeningHistoryRepository.get(application)
    private val smartQueuePreferences = SmartQueuePreferences(application)
    private val _uiState = MutableStateFlow(PlayerUiState(isLoading = true, playlistShuffleModes = smartQueuePreferences.loadPlaylistModes()))
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    private val _progressState = MutableStateFlow(PlaybackProgressState())
    val progressState: StateFlow<PlaybackProgressState> = _progressState.asStateFlow()
    private val _pendingBackupCount = MutableStateFlow(0)
    val pendingBackupCount: StateFlow<Int> = _pendingBackupCount.asStateFlow()
    private val _backupBusy = MutableStateFlow(false)
    val backupBusy: StateFlow<Boolean> = _backupBusy.asStateFlow()
    private val backupLibraryReadiness = MutableStateFlow(BackupLibraryReadiness.PermissionRequired)
    private var pendingBackupSongs: Map<Long, BackupSong> = emptyMap()
    private var collectionRevision = 0L
    private var libraryRevision = 0L
    private var libraryLoaded = false
    private val backupCommitInProgress get() = _backupBusy.value
    private var backupPreview: BackupPreviewSnapshot? = null
    private var pendingBackupPreview: BackupPreviewSnapshot? = null

    private val playbackQueue = ActivePlaybackQueue()
    private val queueController = PlaybackQueueController()
    private val playbackSession = PlaybackSessionCoordinator(persistenceStore)
    private val songIndex = MediaItemSongIndex()
    private val controller: MediaController? get() = controllerConnection.controller
    private var libraryAccessGranted = false
    private val playbackModeSelection = PlaybackModeSelection()
    private var defaultPlaybackMode = PlaybackMode.Ordered
    private var pendingPlaylistModeChange: String? = null
    private var positionJob: Job? = null
    private var sleepTimerJob: Job? = null
    private val activeSourceSongs get() = playbackQueue.snapshot.sourceSongs
    private val activeQueue get() = playbackQueue.snapshot.songs
    private val activeQueueIds get() = playbackQueue.snapshot.ids
    private val activeQueueMediaIds get() = playbackQueue.snapshot.mediaIds
    private val activeQueueSource get() = playbackQueue.snapshot.source
    private val activeQueueSourceKey get() = playbackQueue.snapshot.sourceKey
    private val activeQueueMode get() = playbackQueue.snapshot.mode
    private val activeQueueHasManualEdits get() = playbackQueue.snapshot.hasManualEdits
    private var lastKnownSongId: Long? = null
    private val libraryLoader = MusicLibraryLoader(
        scope = viewModelScope,
        load = { repository.loadSongs(application) },
        onStarted = {
            libraryRevision++
            libraryLoaded = false
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            backupLibraryReadiness.value = if (libraryAccessGranted) BackupLibraryReadiness.Loading
                else BackupLibraryReadiness.PermissionRequired
        },
        onLoaded = ::applyLibrarySongs,
        onFailed = { failure ->
            libraryLoaded = false
            _uiState.update {
                it.copy(isLoading = false, errorMessage = failure.message ?: "No se pudo cargar la música local")
            }
            backupLibraryReadiness.value = if (libraryAccessGranted) BackupLibraryReadiness.Failed
                else BackupLibraryReadiness.PermissionRequired
        },
    )
    private val libraryMonitor = MusicLibraryMonitor(application, viewModelScope, libraryLoader::refresh)

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (queueController.isMutating) return
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) {
                syncActiveQueueFromPlayerTimeline(player)
            }
            updateFromPlayer(
                player,
                forceSessionSave = events.contains(Player.EVENT_POSITION_DISCONTINUITY),
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.update {
                it.copy(errorMessage = playbackErrorMessage(error))
            }
        }
    }

    private val controllerConnection = MediaControllerConnection(application, listener)

    init {
        viewModelScope.launch {
            MusicService.smartQueueState.collect { smart ->
                _uiState.update {
                    it.copy(smartShuffleEnabled = smart.enabled, smartQueueBusy = smart.busy, smartQueueMessage = smart.message)
                }
            }
        }
        viewModelScope.launch {
            historyRepository.state.collect { history ->
                _uiState.update { it.copy(listeningHistory = history) }
            }
        }
        _uiState.update {
            it.copy(
                favoriteSongIds = persistenceStore.loadFavoriteSongIds(),
                playlists = persistenceStore.loadPlaylists(),
            )
        }
        prunePlaylistShuffleModes(_uiState.value.playlists)
        pendingBackupSongs = currentBackupCollections()
            .copy(pendingSongs = persistenceStore.loadPendingBackupSongs()).pruned().pendingSongs
        _pendingBackupCount.value = pendingBackupSongs.size
        connectController()
    }

    fun applyDefaultPreferences(
        defaultFolderPath: String?,
        defaultPlaybackMode: PlaybackMode,
        defaultSortMode: SortMode,
        hiddenFolderPaths: Set<String>,
        excludeSmallAudios: Boolean,
        djModeEnabled: Boolean,
        djMixMode: DjMixMode,
        djMixDurationSeconds: Int,
        autoDjSettings: AutoDjSettings = AutoDjSettings(),
    ) {
        this.defaultPlaybackMode = defaultPlaybackMode
        // Reopening the activity must retain the running session's playback mode.
        val sessionMode = playbackModeAfterPreferenceRefresh(
            source = MusicService.currentQueueOrigin?.first ?: activeQueueSource,
            selectedMode = playbackModeSelection.selectedMode,
            serviceMode = MusicService.currentPlaybackMode,
            currentMode = _uiState.value.playbackMode,
            hasRestoredSession = playbackSession.hasRestoredSession,
            defaultMode = defaultPlaybackMode,
        )
        _uiState.update { current ->
            val allFolders = current.songs.toFolders()
            val visibleFolders = current.songs.visibleSongs(emptySet(), excludeSmallAudios).toFolders()
            val selectedFolderPath = defaultFolderPath?.takeIf { folderPath ->
                folderPath.isNotBlank() && folderPath !in hiddenFolderPaths
            }
            current.copy(
                selectedFolderPath = selectedFolderPath,
                hiddenFolderPaths = hiddenFolderPaths,
                excludeSmallAudios = excludeSmallAudios,
                playbackMode = sessionMode,
                sortMode = defaultSortMode,
                djModeEnabled = djModeEnabled,
                djMixMode = djMixMode,
                djMixDurationSeconds = djMixDurationSeconds.coerceIn(MIN_DJ_MIX_SECONDS, MAX_DJ_MIX_SECONDS),
                autoDjSettings = autoDjSettings.sanitized(),
                shuffleEnabled = sessionMode == PlaybackMode.Shuffle,
                repeatSetting = sessionMode.toRepeatSetting(),
                allFolders = allFolders,
                folders = visibleFolders.visibleFolders(hiddenFolderPaths),
                visibleSongs = current.songs.filteredAndSorted(selectedFolderPath, defaultSortMode, hiddenFolderPaths, excludeSmallAudios),
            )
        }
        MusicService.updateAutoDjSettings(autoDjSettings.sanitized())
        MusicService.updateDjConfig(
            enabled = djModeEnabled,
            mixMode = djMixMode,
            mixDurationSeconds = djMixDurationSeconds,
            playbackMode = sessionMode,
        )
        controller?.let { applyPlaybackMode(sessionMode, it) }
    }

    fun setLibraryAccess(granted: Boolean) {
        if (libraryAccessGranted != granted) {
            libraryRevision++
            backupLibraryReadiness.value = if (granted) BackupLibraryReadiness.Loading
                else BackupLibraryReadiness.PermissionRequired
        }
        libraryAccessGranted = granted
        libraryLoader.setEnabled(granted)
        libraryMonitor.setPermissionGranted(granted)
        if (!granted) {
            libraryLoaded = false
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun onLibraryResume() {
        if (libraryAccessGranted) libraryMonitor.onResume()
    }

    fun loadSongs() = libraryLoader.refresh()

    private fun applyLibrarySongs(songs: List<Song>) {
        MusicService.updateSmartQueueSongs(songs)
        libraryRevision++
        libraryLoaded = true
        _uiState.update { it.withRefreshedLibrary(songs) }
        backupLibraryReadiness.value = if (libraryAccessGranted) BackupLibraryReadiness.Ready
            else BackupLibraryReadiness.PermissionRequired
        controller?.let { player ->
            if (!tryRestorePlaybackSession(player)) {
                syncActiveQueueFromPlayerTimeline(player)
                syncQueueOrder()
            }
            applyPendingPlaybackMode()
            updateFromPlayer(player)
        }
    }

    fun setListeningHistoryEnabled(enabled: Boolean) = historyRepository.setEnabled(enabled)

    fun clearListeningHistory() = historyRepository.clear()

    fun playSong(song: Song) {
        val queue = _uiState.value.playableSongs()
        startQueuePlayback(
            queue = queue,
            requestedSong = song,
            source = PlaybackQueueSource.Songs,
            sourceKey = _uiState.value.selectedFolderPath,
            forceRegenerate = true,
        )
    }

    fun playVisibleList() {
        startQueuePlayback(
            queue = _uiState.value.visibleSongs,
            source = PlaybackQueueSource.Songs,
            sourceKey = _uiState.value.selectedFolderPath,
            forceRegenerate = true,
        )
    }

    fun playQueuedSong(
        song: Song,
        queue: List<Song>,
        source: PlaybackQueueSource,
        sourceKey: String?,
    ) {
        startQueuePlayback(
            queue = queue,
            requestedSong = song,
            source = source,
            sourceKey = sourceKey,
            forceRegenerate = true,
        )
    }

    fun playQueue(
        queue: List<Song>,
        source: PlaybackQueueSource,
        sourceKey: String?,
    ) {
        startQueuePlayback(
            queue = queue,
            source = source,
            sourceKey = sourceKey,
            forceRegenerate = true,
        )
    }

    fun toggleFavorite(songId: Long) {
        if (!allowCollectionMutation()) return
        val currentIds = _uiState.value.favoriteSongIds
        val updatedIds = if (songId in currentIds) {
            currentIds - songId
        } else {
            currentIds + songId
        }
        val pending = currentBackupCollections().copy(favoriteSongIds = updatedIds).pruned().pendingSongs
        persistenceStore.saveFavoriteSongIds(updatedIds, pending)
        pendingBackupSongs = pending
        _pendingBackupCount.value = pending.size
        collectionRevision++
        _uiState.update { it.copy(favoriteSongIds = updatedIds) }
    }

    fun createPlaylist(name: String) {
        if (!allowCollectionMutation()) return
        persistPlaylistMutation(
            _uiState.value.playlists.withNewPlaylist(
                name = name,
                id = newPlaylistId(),
            ),
        )
    }

    fun createPlaylistFromQueue(name: String) {
        if (!allowCollectionMutation()) return
        if (activeQueue.isEmpty()) return
        persistPlaylistMutation(
            _uiState.value.playlists.withNewPlaylist(
                name = name,
                id = newPlaylistId(),
                songIds = activeQueue.map { it.id },
            ),
        )
    }

    fun addQueueToPlaylist(playlistId: String): Int {
        if (!allowCollectionMutation()) return 0
        val result = _uiState.value.playlists.withQueueAddedToPlaylist(
            playlistId = playlistId,
            queueSongIds = activeQueue.map { it.id },
        )
        if (result.changed) persistPlaylists(result.playlists)
        return result.addedCount
    }

    fun deletePlaylist(playlistId: String) {
        if (!allowCollectionMutation()) return
        persistPlaylistMutation(_uiState.value.playlists.withoutPlaylist(playlistId))
    }

    fun setPlaylistCover(playlistId: String, songId: Long?) {
        if (!allowCollectionMutation()) return
        persistPlaylistMutation(_uiState.value.playlists.withPlaylistCover(playlistId, songId))
    }

    fun addSongToPlaylist(playlistId: String, songId: Long) {
        if (!allowCollectionMutation()) return
        persistPlaylistMutation(_uiState.value.playlists.withSongAddedToPlaylist(playlistId, songId))
    }

    fun removeSongFromPlaylist(playlistId: String, songId: Long) {
        if (!allowCollectionMutation()) return
        persistPlaylistMutation(_uiState.value.playlists.withSongRemovedFromPlaylist(playlistId, songId))
    }

    fun renamePlaylist(playlistId: String, name: String): Boolean {
        if (!allowCollectionMutation()) return false
        val playlists = _uiState.value.playlists
        if (playlists.firstOrNull { it.id == playlistId }?.name == name.trim()) return true
        val mutation = playlists.withPlaylistRenamed(playlistId, name)
        persistPlaylistMutation(mutation)
        return mutation.changed
    }

    fun reorderPlaylist(playlistId: String, expectedSongIds: List<Long>, orderedVisibleIds: List<Long>): Boolean {
        if (!allowCollectionMutation()) return false
        val state = _uiState.value
        val playlist = state.playlists.firstOrNull { it.id == playlistId } ?: return false
        val availableIds = state.songs.visibleSongs(state.hiddenFolderPaths, state.excludeSmallAudios)
            .mapTo(HashSet()) { it.id }
        val visibleIds = playlist.songIds.filter { it in availableIds }
        val mutation = state.playlists.withPlaylistSongsReordered(
            playlistId, expectedSongIds, visibleIds, orderedVisibleIds,
        )
        persistPlaylistMutation(mutation)
        return mutation.changed
    }

    fun addSongsToPlaylist(playlistId: String, songIds: List<Long>): Int {
        if (!allowCollectionMutation()) return 0
        val state = _uiState.value
        val availableIds = state.songs.visibleSongs(state.hiddenFolderPaths, state.excludeSmallAudios)
            .mapTo(HashSet()) { it.id }
        val result = state.playlists.withSongsAddedToPlaylist(playlistId, songIds.filter { it in availableIds })
        if (result.changed) persistPlaylists(result.playlists)
        return result.addedCount
    }

    fun removeSongsFromPlaylist(playlistId: String, songIds: Set<Long>): Int {
        if (!allowCollectionMutation()) return 0
        val result = _uiState.value.playlists.withoutSongsFromPlaylist(playlistId, songIds)
        if (result.changed) persistPlaylists(result.playlists)
        return result.removedCount
    }

    suspend fun createBackupDocument(preferences: BackupPreferences): BackupDocument =
        withContext(Dispatchers.Main.immediate) {
            prepareWithReadyBackupLibrary { snapshot ->
                withContext(Dispatchers.Default) {
                    buildBackupDocument(snapshot.collections, snapshot.library, preferences,
                        System.currentTimeMillis(), BuildConfig.VERSION_NAME)
                }
            }
        }

    suspend fun previewBackup(document: BackupDocument): List<BackupSongMatch> =
        withContext(Dispatchers.Main.immediate) {
            prepareBackupPreview(document).also { backupPreview = it }.matches
        }

    suspend fun restoreBackup(
        document: BackupDocument,
        expectedMatches: List<BackupSongMatch>,
        applyPreferences: Boolean = false,
    ): BackupRestoreResult = withContext(Dispatchers.Main.immediate) {
        val preview = backupPreview
        check(preview != null && preview.document == document && preview.matches === expectedMatches) {
            BACKUP_CHANGED_MESSAGE
        }
        applyBackupPreview(preview, applyPreferences)
    }

    suspend fun previewPendingBackup(): List<BackupSongMatch> = withContext(Dispatchers.Main.immediate) {
        prepareBackupPreview(null).also { pendingBackupPreview = it }.matches
    }

    suspend fun resolvePendingBackup(expectedMatches: List<BackupSongMatch>): BackupRestoreResult =
        withContext(Dispatchers.Main.immediate) {
            val preview = pendingBackupPreview
            check(preview != null && preview.matches === expectedMatches) { BACKUP_CHANGED_MESSAGE }
            applyBackupPreview(preview)
        }

    private suspend fun prepareBackupPreview(document: BackupDocument?): BackupPreviewSnapshot =
        prepareWithReadyBackupLibrary { snapshot ->
            val references = document?.songs ?: snapshot.collections.pendingSongs.values.toList()
            val matches = withContext(Dispatchers.Default) {
                document?.let(::validateBackupDocument)
                matchBackupSongs(references, snapshot.library)
            }
            BackupPreviewSnapshot(document, snapshot.collections, snapshot.library, snapshot.collectionRevision,
                snapshot.libraryRevision, references, matches)
        }

    private suspend fun <T : Any> prepareWithReadyBackupLibrary(
        prepare: suspend (BackupPreparationSnapshot) -> T,
    ): T {
        check(!backupCommitInProgress) { "Espera a que termine la restauración actual." }
        return prepareWhenBackupLibraryReady(backupLibraryReadiness) {
            requireBackupLibraryReady()
            val snapshot = BackupPreparationSnapshot(currentBackupCollections().pruned(), _uiState.value.songs,
                collectionRevision, libraryRevision)
            val prepared = prepare(snapshot)
            check(libraryAccessGranted) { BACKUP_LIBRARY_PERMISSION_MESSAGE }
            check(!backupCommitInProgress) { "Espera a que termine la restauración actual." }
            prepared.takeIf {
                backupLibraryReadiness.value == BackupLibraryReadiness.Ready &&
                    snapshot.collectionRevision == collectionRevision && snapshot.libraryRevision == libraryRevision
            }
        }
    }

    private suspend fun applyBackupPreview(
        preview: BackupPreviewSnapshot,
        applyPreferences: Boolean = false,
    ): BackupRestoreResult {
        requireBackupLibraryReady()
        requireCurrentBackupPreview(preview.collectionRevision, collectionRevision,
            preview.libraryRevision, libraryRevision, preview.matches, preview.matches)
        val (plan, validFolderPaths) = withContext(Dispatchers.Default) {
            val latestMatches = matchBackupSongs(preview.references, preview.library)
            requireCurrentBackupPreview(preview.collectionRevision, preview.collectionRevision,
                preview.libraryRevision, preview.libraryRevision, preview.matches, latestMatches)
            val plan = preview.document?.let { planBackupRestore(preview.collections, it, latestMatches) }
                ?: planPendingBackupResolution(preview.collections, latestMatches)
            plan to preview.library.mapTo(HashSet()) { it.folderPath }
        }
        requireBackupLibraryReady()
        requireCurrentBackupPreview(preview.collectionRevision, collectionRevision,
            preview.libraryRevision, libraryRevision, preview.matches, preview.matches)
        currentCoroutineContext().ensureActive()
        _backupBusy.value = true
        try {
            // Cancellation is honored up to this boundary; a started write always publishes
            // its matching in-memory state before collection editing can resume.
            return withContext(NonCancellable) {
                val preferences = preview.document?.preferences?.takeIf { applyPreferences }
                val saved = withContext(Dispatchers.IO) {
                    persistenceStore.commitBackupCollections(plan.state,
                        preferences, validFolderPaths)
                }
                check(saved) { "No se pudo guardar la restauración. No se cambiaron tus listas ni favoritos." }
                pendingBackupSongs = plan.state.pendingSongs
                collectionRevision++
                _uiState.update { it.copy(playlists = plan.state.playlists, favoriteSongIds = plan.state.favoriteSongIds) }
                prunePlaylistShuffleModes(plan.state.playlists)
                _pendingBackupCount.value = pendingBackupSongs.size
                preferences?.let(::applyRestoredBackupPreferences)
                backupPreview = null
                pendingBackupPreview = null
                plan.result
            }
        } finally {
            _backupBusy.value = false
        }
    }

    private fun applyRestoredBackupPreferences(preferences: BackupPreferences) {
        val folderPaths = _uiState.value.allFolders.mapTo(HashSet()) { it.path }
        val hiddenFolders = preferences.hiddenFolderPaths.intersect(folderPaths)
        applyDefaultPreferences(
            defaultFolderPath = preferences.defaultFolderPath?.takeIf { it in folderPaths && it !in hiddenFolders },
            defaultPlaybackMode = preferences.playbackMode,
            defaultSortMode = preferences.sortMode,
            hiddenFolderPaths = hiddenFolders,
            excludeSmallAudios = preferences.excludeSmallAudios,
            djModeEnabled = preferences.djModeEnabled,
            djMixMode = preferences.djMixMode,
            djMixDurationSeconds = preferences.djMixDurationSeconds,
            autoDjSettings = preferences.autoDjSettings,
        )
        // General preference application preserves the running session mode. A restore is
        // an explicit mode choice and must also update that session and its current queue.
        setPlaybackMode(preferences.playbackMode)
    }

    private fun requireBackupLibraryReady() {
        check(!backupCommitInProgress) { "Espera a que termine la restauración actual." }
        check(libraryAccessGranted) { BACKUP_LIBRARY_PERMISSION_MESSAGE }
        check(libraryLoaded && !_uiState.value.isLoading) { "Espera a que termine de cargar la biblioteca y vuelve a intentarlo." }
    }

    private fun currentBackupCollections(): BackupCollectionState = _uiState.value.let {
        BackupCollectionState(it.playlists, it.favoriteSongIds, pendingBackupSongs)
    }

    private fun allowCollectionMutation(): Boolean {
        if (!backupCommitInProgress) return true
        _uiState.update { it.copy(errorMessage = "Espera a que termine la restauración para editar tus listas o favoritos.") }
        return false
    }

    fun playNext(song: Song) {
        insertIntoGeneratedQueue(song = song, afterCurrent = true)
    }

    fun playAtEnd(song: Song) {
        insertIntoGeneratedQueue(song = song, afterCurrent = false)
    }

    fun cyclePlaylistShuffleMode(playlistId: String) {
        val state = _uiState.value
        if (state.playlists.none { it.id == playlistId }) return
        val mode = (state.playlistShuffleModes[playlistId] ?: PlaylistShuffleMode.Off).next()
        smartQueuePreferences.setPlaylistMode(playlistId, mode)
        _uiState.update { it.copy(playlistShuffleModes = smartQueuePreferences.loadPlaylistModes()) }
        if (activeQueueSource != PlaybackQueueSource.Playlist || activeQueueSourceKey != playlistId) return
        playbackModeSelection.takePending(ready = true)
        pendingPlaylistModeChange = playlistId
        applyPendingPlaylistShuffleMode()
    }

    private fun applyPendingPlaylistShuffleMode() {
        val playlistId = pendingPlaylistModeChange ?: return
        if (activeQueueSource != PlaybackQueueSource.Playlist || activeQueueSourceKey != playlistId) {
            pendingPlaylistModeChange = null
            return
        }
        if (!canApplyPendingPlaybackMode()) return
        pendingPlaylistModeChange = null
        val mode = _uiState.value.playlistShuffleModes[playlistId] ?: PlaylistShuffleMode.Off
        applyPlaybackModeSelection(mode.playbackMode, force = true, regenerateQueue = true, playlistShuffleMode = mode)
    }

    fun smartOrderQueue() = MusicService.smartOrderQueue()

    fun startSleepTimer(durationMs: Long) {
        val safeDurationMs = durationMs.coerceAtLeast(SLEEP_TIMER_MIN_DURATION_MS)
        sleepTimerJob?.cancel()
        val startedAtMs = System.currentTimeMillis()
        val endAtMs = startedAtMs + safeDurationMs
        _uiState.update { it.copy(sleepTimerRemainingMs = safeDurationMs) }
        sleepTimerJob = viewModelScope.launch {
            while (isActive) {
                val remainingMs = (endAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
                _uiState.update { it.copy(sleepTimerRemainingMs = remainingMs) }
                if (remainingMs <= 0L) break
                delay(SLEEP_TIMER_UPDATE_INTERVAL_MS)
            }
            MusicService.stopMixPreview(restorePlayback = false)
            controller?.pause()
            savePlaybackSession()
            _uiState.update {
                it.copy(
                    isPlaying = false,
                    sleepTimerRemainingMs = 0,
                    sleepTimerFinishedEventId = System.currentTimeMillis(),
                )
            }
            sleepTimerJob = null
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _uiState.update { it.copy(sleepTimerRemainingMs = 0) }
    }

    fun resumeRestoredSession() {
        MusicService.stopMixPreview(restorePlayback = false)
        val player = controller ?: return
        _uiState.update { it.copy(restoredSessionAvailable = false) }
        player.play()
        updateFromPlayer(player)
    }

    fun dismissRestoredSession() {
        MusicService.stopMixPreview(restorePlayback = false)
        playbackSession.clear()
        controller?.let { player ->
            player.stop()
            player.clearMediaItems()
        }
        clearActiveQueue()
        lastKnownSongId = null
        _progressState.value = PlaybackProgressState()
        _uiState.update {
            it.copy(
                currentSong = null,
                isPlaying = false,
                restoredSessionAvailable = false,
                restoredSessionPositionMs = 0,
            )
        }
    }

    fun playQueueSong(song: Song) {
        val index = activeQueue.indexOfFirst { it.id == song.id }
        if (index >= 0) playActiveQueueIndex(index)
    }

    fun removeQueuedSong(song: Song) {
        val currentId = controller?.currentMediaItem?.mediaId?.toLongOrNull() ?: _uiState.value.currentSong?.id
        val nextQueue = playbackQueue.removing(song.id, currentId) ?: return
        replaceActiveQueuePreservingPlayback(nextQueue, hasManualEdits = true)
    }

    fun moveQueuedSong(song: Song, offset: Int) {
        val currentId = controller?.currentMediaItem?.mediaId?.toLongOrNull() ?: _uiState.value.currentSong?.id
        val nextQueue = playbackQueue.moving(song.id, offset, currentId) ?: return
        replaceActiveQueuePreservingPlayback(nextQueue, hasManualEdits = true)
    }

    fun clearUpcomingQueue() {
        val currentId = controller?.currentMediaItem?.mediaId?.toLongOrNull() ?: _uiState.value.currentSong?.id
        val nextQueue = playbackQueue.keepingCurrent(currentId) ?: return
        replaceActiveQueuePreservingPlayback(nextQueue, hasManualEdits = true, forceFullReset = true)
    }

    private fun startQueuePlayback(
        queue: List<Song>,
        requestedSong: Song? = null,
        source: PlaybackQueueSource,
        sourceKey: String? = null,
        forceRegenerate: Boolean = false,
    ) {
        MusicService.stopMixPreview(restorePlayback = false)
        MusicService.cancelSmartQueueForTransport()
        val player = controller ?: run {
            _uiState.update { it.copy(errorMessage = "El reproductor todavía se está preparando") }
            return
        }
        val state = _uiState.value
        _uiState.update { it.copy(restoredSessionAvailable = false, restoredSessionPositionMs = 0) }
        val availableQueue = queue.visibleSongs(state.hiddenFolderPaths, state.excludeSmallAudios).distinctSongsById()
        val collectionSongs = collectionMixKey(source, sourceKey)?.let(::collectionSongs)
        val requestedIds = availableQueue.map { it.id }.toSet()
        val sourceSongs = collectionSongs?.filter { it.id in requestedIds } ?: availableQueue
        if (sourceSongs.isEmpty()) return
        val safeRequestedSong = requestedSong?.takeIf { requested -> sourceSongs.any { it.id == requested.id } }
        if (requestedSong != null && safeRequestedSong == null) return

        val playbackSettings = playbackSettingsForSource(source, sourceKey, state.playlistShuffleModes, defaultPlaybackMode)
        val playbackMode = playbackSettings.playbackMode
        pendingPlaylistModeChange = null
        playbackModeSelection.takePending(ready = true)
        adoptPlaybackSettings(playbackSettings)
        if (forceRegenerate || playbackQueue.shouldGenerate(sourceSongs, safeRequestedSong, source, sourceKey, playbackMode)) {
            MusicService.startNewSmartQueue()
            setActiveQueue(
                queue = sourceSongs.generatePlaybackQueue(playbackMode, safeRequestedSong),
                source = source,
                sourceKey = sourceKey,
                sourceSongs = sourceSongs,
                mode = playbackMode,
            )
        }

        val requestedIndex = safeRequestedSong?.let { requested ->
            activeQueue.indexOfFirst { it.id == requested.id }
        } ?: -1
        val startIndex = if (requestedIndex >= 0) {
            requestedIndex
        } else {
            when (_uiState.value.playbackMode) {
                PlaybackMode.Ordered,
                PlaybackMode.RepeatList,
                PlaybackMode.RepeatCurrent,
                PlaybackMode.Shuffle -> 0
            }
        }

        player.setMediaItems(activeQueue.toMediaItems(), startIndex, 0L)
        applyPlaybackMode(_uiState.value.playbackMode, player)
        player.prepare()
        player.play()
        updateFromPlayer(player)
    }

    fun togglePlayPause() {
        MusicService.stopMixPreview(restorePlayback = false)
        MusicService.cancelSmartQueueForTransport()
        val player = controller ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
        updateFromPlayer(player)
    }

    fun next() {
        MusicService.stopMixPreview(restorePlayback = false)
        MusicService.cancelSmartQueueForTransport()
        val player = controller ?: return
        val queue = activeQueue.ifEmpty { _uiState.value.playableSongs() }
        if (queue.isEmpty()) return
        if (activeQueue.isEmpty()) {
            setActiveQueue(queue = queue, sourceSongs = queue, mode = _uiState.value.playbackMode)
        }

        val move = queue.nextQueueMove(
            sourceSongs = activeSourceSongs.ifEmpty { queue },
            currentSong = queue.currentQueueSong(),
            playbackMode = _uiState.value.playbackMode,
            manual = true,
        ) ?: return
        playQueueMove(move)
    }

    fun previous() {
        MusicService.stopMixPreview(restorePlayback = false)
        MusicService.cancelSmartQueueForTransport()
        val player = controller ?: return
        val queue = activeQueue.ifEmpty { _uiState.value.playableSongs() }
        if (queue.isEmpty()) return
        if (activeQueue.isEmpty()) {
            setActiveQueue(queue = queue, sourceSongs = queue, mode = _uiState.value.playbackMode)
        }

        val move = queue.previousQueueMove(
            currentSong = queue.currentQueueSong(),
            playbackMode = _uiState.value.playbackMode,
            currentPositionMs = player.currentPosition,
        ) ?: return
        playQueueMove(move)
    }

    fun seekTo(positionMs: Long) {
        MusicService.stopMixPreview(restorePlayback = true)
        MusicService.cancelSmartQueueForTransport()
        val player = controller ?: return
        val duration = player.duration.takeIf { it > 0 } ?: _progressState.value.durationMs
        val safePosition = positionMs
            .coerceAtLeast(0)
            .coerceAtMost(duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
        updateProgress(duration, safePosition)
        player.seekTo(safePosition)
        schedulePlaybackSessionSave(force = true)
    }

    fun setPlaybackMode(playbackMode: PlaybackMode) {
        defaultPlaybackMode = playbackMode
        val wasSmartShuffleEnabled = _uiState.value.smartShuffleEnabled
        val wasPending = playbackModeSelection.hasPending
        playbackModeSelection.select(playbackMode, _uiState.value.playbackMode, MusicService.currentPlaybackMode)
        val pending = playbackModeSelection.takePending(canApplyPendingPlaybackMode())
        applyPlaybackModeSelection(
            playbackMode,
            force = wasSmartShuffleEnabled || wasPending && pending != null,
            regenerateQueue = pending?.regenerateQueue == true,
        )
    }

    private fun canApplyPendingPlaybackMode(): Boolean {
        val player = controller ?: return false
        return if (player.mediaItemCount > 0) activeSourceSongs.isNotEmpty() else playbackSession.hasRestoredSession
    }

    private fun applyPendingPlaybackMode() {
        applyPendingPlaylistShuffleMode()
        val pending = playbackModeSelection.takePending(canApplyPendingPlaybackMode()) ?: return
        applyPlaybackModeSelection(pending.mode, force = true, regenerateQueue = pending.regenerateQueue)
    }

    private fun applyPlaybackModeSelection(
        playbackMode: PlaybackMode,
        force: Boolean = false,
        regenerateQueue: Boolean = false,
        playlistShuffleMode: PlaylistShuffleMode? = null,
    ) {
        val previousMode = _uiState.value.playbackMode
        updateActiveSmartShuffle(playbackMode, smartShuffleEnabled = playlistShuffleMode == PlaylistShuffleMode.SmartShuffle)
        if (previousMode == playbackMode && !force) return
        MusicService.stopMixPreview(restorePlayback = true)
        val player = controller
        val shouldPlay = player?.let { it.isPlaying || it.playWhenReady } ?: false
        val currentPosition = player?.currentPosition?.coerceAtLeast(0) ?: 0L
        val currentSong = songForMediaId(player?.currentMediaItem?.mediaId) ?: _uiState.value.currentSong

        _uiState.update { current ->
            current.copy(
                playbackMode = playbackMode,
                shuffleEnabled = playbackMode == PlaybackMode.Shuffle,
                repeatSetting = playbackMode.toRepeatSetting(),
            )
        }
        MusicService.updateDjConfig(
            enabled = _uiState.value.djModeEnabled,
            mixMode = _uiState.value.djMixMode,
            mixDurationSeconds = _uiState.value.djMixDurationSeconds,
            playbackMode = playbackMode,
        )
        // Applying deferred repeat settings must preserve a manually arranged queue.
        val preservesPlaylistRepeatOrder = activeQueueSource == PlaybackQueueSource.Playlist &&
            playlistShuffleMode == null && (playbackMode == PlaybackMode.RepeatList || playbackMode == PlaybackMode.RepeatCurrent)
        val changesQueueOrder = !preservesPlaylistRepeatOrder &&
            (regenerateQueue || previousMode == PlaybackMode.Shuffle || playbackMode == PlaybackMode.Shuffle)
        var queueUpdated = false
        if (changesQueueOrder && activeSourceSongs.isNotEmpty()) {
            val state = _uiState.value
            val availableSongs = state.songs.visibleSongs(state.hiddenFolderPaths, state.excludeSmallAudios)
            val sourceSongs = (collectionMixKey(activeQueueSource, activeQueueSourceKey)?.let(::collectionSongs)
                ?: activeSourceSongs.refreshedFrom(availableSongs)).ifEmpty {
                activeSourceSongs.visibleSongs(state.hiddenFolderPaths, state.excludeSmallAudios)
            }
            if (sourceSongs.isNotEmpty()) {
                MusicService.startNewSmartQueue()
                val generatedQueue = if (playlistShuffleMode != null) {
                    activeQueue.withPlaylistPendingOrder(sourceSongs, currentSong?.id, playlistShuffleMode)
                } else sourceSongs.generatePlaybackQueue(playbackMode, currentSong)
                if (player != null && currentSong != null && generatedQueue.any { it.id == currentSong.id }) {
                    replaceActiveQueuePreservingPlayback(
                        queue = generatedQueue,
                        sourceSongs = sourceSongs,
                        mode = playbackMode,
                        positionMs = currentPosition,
                        shouldPlay = shouldPlay,
                    )
                    queueUpdated = true
                } else if (player == null || currentSong == null) {
                    setActiveQueue(queue = generatedQueue, sourceSongs = sourceSongs, mode = playbackMode)
                    queueUpdated = true
                }
            }
        }
        if (!queueUpdated) {
            playbackQueue.setMode(playbackMode)
            player?.let { applyPlaybackMode(playbackMode, it) }
            schedulePlaybackSessionSave(force = true)
            player?.let { updateFromPlayer(it) }
        }
    }

    fun setSortMode(sortMode: SortMode) {
        val previousState = _uiState.value
        val sorted = previousState.songs.filteredAndSorted(
            selectedFolderPath = previousState.selectedFolderPath,
            sortMode = sortMode,
            hiddenFolderPaths = previousState.hiddenFolderPaths,
            excludeSmallAudios = previousState.excludeSmallAudios,
        )
        _uiState.update { it.copy(sortMode = sortMode, visibleSongs = sorted) }
        syncQueueOrder()
    }

    fun setSelectedFolder(folderPath: String?) {
        val previousState = _uiState.value
        val selectedFolderPath = folderPath?.takeIf { selected ->
            selected !in previousState.hiddenFolderPaths && previousState.songs.any {
                it.folderPath == selected && it.isIncludedBySmallAudioFilter(previousState.excludeSmallAudios)
            }
        }
        val sorted = previousState.songs.filteredAndSorted(
            selectedFolderPath = selectedFolderPath,
            sortMode = previousState.sortMode,
            hiddenFolderPaths = previousState.hiddenFolderPaths,
            excludeSmallAudios = previousState.excludeSmallAudios,
        )
        _uiState.update {
            it.copy(
                selectedFolderPath = selectedFolderPath,
                visibleSongs = sorted,
            )
        }
        syncQueueOrder()
    }

    fun setHiddenFolderPaths(hiddenFolderPaths: Set<String>) {
        val paths = hiddenFolderPaths.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.toSet()
        _uiState.update { current ->
            current.copy(hiddenFolderPaths = paths).withRefreshedLibrary(current.songs)
        }
        syncQueueOrder()
    }

    fun setExcludeSmallAudios(excludeSmallAudios: Boolean) {
        _uiState.update { current ->
            current.copy(excludeSmallAudios = excludeSmallAudios).withRefreshedLibrary(current.songs)
        }
        syncQueueOrder()
    }

    fun setDjModeConfig(enabled: Boolean, mixMode: DjMixMode, mixDurationSeconds: Int) {
        _uiState.update {
            it.copy(
                djModeEnabled = enabled,
                djMixMode = mixMode,
                djMixDurationSeconds = mixDurationSeconds.coerceIn(MIN_DJ_MIX_SECONDS, MAX_DJ_MIX_SECONDS),
            )
        }
        MusicService.updateAutoDjSettings(_uiState.value.autoDjSettings)
        MusicService.updateDjConfig(
            enabled = enabled,
            mixMode = mixMode,
            mixDurationSeconds = mixDurationSeconds.coerceIn(MIN_DJ_MIX_SECONDS, MAX_DJ_MIX_SECONDS),
            playbackMode = _uiState.value.playbackMode,
        )
    }

    fun setAutoDjSettings(settings: AutoDjSettings) {
        val safeSettings = settings.sanitized()
        _uiState.update { it.copy(autoDjSettings = safeSettings) }
        MusicService.updateAutoDjSettings(safeSettings)
    }

    private fun collectionSongs(key: String): List<Song> {
        val state = _uiState.value
        val available = state.songs.visibleSongs(state.hiddenFolderPaths, state.excludeSmallAudios)
        return when {
            key == "favorites" -> available.filter { it.id in state.favoriteSongIds }.sortedWith(state.sortMode.songComparator())
            key.startsWith("playlist:") -> {
                val byId = available.associateBy { it.id }
                state.playlists.firstOrNull { it.id == key.removePrefix("playlist:") }
                    ?.songIds.orEmpty().distinct().mapNotNull(byId::get)
            }
            else -> emptyList()
        }
    }

    fun shutdownPlayback() {
        MusicService.stopMixPreview(restorePlayback = false)
        positionJob?.cancel()
        controller?.let { player ->
            player.pause()
            player.stop()
            player.clearMediaItems()
        }
        _uiState.update {
            it.copy(
                currentSong = null,
                isPlaying = false,
                playbackQueue = emptyList(),
            )
        }
        _progressState.value = PlaybackProgressState()
        clearActiveQueue()
        lastKnownSongId = null
    }

    private fun connectController() {
        controllerConnection.connect(
            onConnected = { mediaController ->
                val session = if (mediaController.mediaItemCount > 0) {
                    playbackSession.matchingTimelineSession(
                        mediaController.mediaItemIds(),
                        mediaController.currentMediaItem?.mediaId,
                    )
                } else null
                val liveOrigin = MusicService.currentQueueOrigin
                playbackQueue.adoptOrigin(liveOrigin?.first ?: session?.source ?: PlaybackQueueSource.Songs,
                    if (liveOrigin != null) liveOrigin.second else session?.sourceKey)
                if (session != null || liveOrigin != null) {
                    adoptPlaybackSettings(playbackSettingsForSource(activeQueueSource, activeQueueSourceKey,
                        _uiState.value.playlistShuffleModes, MusicService.currentPlaybackMode ?: session?.playbackMode ?: _uiState.value.playbackMode))
                } else if (mediaController.mediaItemCount > 0) {
                    updateActiveSmartShuffle(_uiState.value.playbackMode)
                }
                _uiState.update {
                    it.copy(
                        hasController = true,
                        playbackQueueSource = activeQueueSource,
                        playbackQueueSourceKey = activeQueueSourceKey,
                    )
                }
                syncActiveQueueFromPlayerTimeline(mediaController)
                applyPlaybackMode(_uiState.value.playbackMode, mediaController)
                if (!tryRestorePlaybackSession(mediaController)) syncQueueOrder()
                applyPendingPlaybackMode()
                updateFromPlayer(mediaController)
                startPositionUpdates()
            },
            onFailure = { throwable ->
                _uiState.update {
                    it.copy(
                        hasController = false,
                        errorMessage = throwable.message ?: "No se pudo conectar el reproductor",
                    )
                }
            },
        )
    }

    private fun persistPlaylistMutation(mutation: PlaylistMutation) {
        if (mutation.changed) persistPlaylists(mutation.playlists)
    }

    private fun persistPlaylists(playlists: List<MusicPlaylist>) {
        if (!allowCollectionMutation()) return
        val pending = currentBackupCollections().copy(playlists = playlists).pruned().pendingSongs
        persistenceStore.savePlaylists(playlists, pending)
        pendingBackupSongs = pending
        _pendingBackupCount.value = pending.size
        collectionRevision++
        _uiState.update { it.copy(playlists = playlists) }
        prunePlaylistShuffleModes(playlists)
    }

    private fun prunePlaylistShuffleModes(playlists: List<MusicPlaylist>) {
        smartQueuePreferences.retainPlaylists(playlists.mapTo(HashSet()) { it.id })
        _uiState.update { it.copy(playlistShuffleModes = smartQueuePreferences.loadPlaylistModes()) }
        if (activeQueueSource == PlaybackQueueSource.Playlist) updateActiveSmartShuffle(_uiState.value.playbackMode)
    }

    private fun updateActiveSmartShuffle(playbackMode: PlaybackMode, smartShuffleEnabled: Boolean = _uiState.value.smartShuffleEnabled) {
        val enabled = smartShuffleEnabled && activeQueueSource == PlaybackQueueSource.Playlist && playbackMode == PlaybackMode.Shuffle &&
            _uiState.value.playlistShuffleModes[activeQueueSourceKey] == PlaylistShuffleMode.SmartShuffle
        _uiState.update { it.copy(smartShuffleEnabled = enabled) }
        MusicService.updateSmartShuffleEnabled(enabled)
    }

    private fun adoptPlaybackSettings(settings: PlaylistPlaybackSettings) {
        _uiState.update { it.copy(playbackMode = settings.playbackMode,
            shuffleEnabled = settings.playbackMode == PlaybackMode.Shuffle,
            repeatSetting = settings.playbackMode.toRepeatSetting(), smartShuffleEnabled = settings.smartShuffleEnabled) }
        MusicService.updateSmartShuffleEnabled(settings.smartShuffleEnabled)
        MusicService.updateDjConfig(_uiState.value.djModeEnabled, _uiState.value.djMixMode,
            _uiState.value.djMixDurationSeconds, settings.playbackMode)
    }

    private fun newPlaylistId(): String = "${System.currentTimeMillis()}-${Random.nextInt(1_000, 9_999)}"

    private fun tryRestorePlaybackSession(player: MediaController): Boolean {
        val restored = playbackSession.restore(
            playerHasItems = player.mediaItemCount > 0,
            songs = _uiState.value.songs,
            fallbackMode = _uiState.value.playbackMode,
        ) ?: return false
        val playbackSettings = playbackSettingsForSource(restored.source, restored.sourceKey,
            _uiState.value.playlistShuffleModes, restored.playbackMode)
        val playbackMode = playbackSettings.playbackMode
        adoptPlaybackSettings(playbackSettings)
        _uiState.update {
            it.copy(
                playbackMode = playbackMode,
                shuffleEnabled = playbackMode == PlaybackMode.Shuffle,
                repeatSetting = playbackMode.toRepeatSetting(),
                restoredSessionAvailable = true,
                restoredSessionPositionMs = restored.positionMs,
            )
        }
        setActiveQueue(
            queue = restored.queue,
            source = restored.source,
            sourceKey = restored.sourceKey,
            sourceSongs = sourceSongsForTimeline(restored.queue, restored.source, restored.sourceKey),
            mode = playbackMode,
            hasManualEdits = true,
        )
        MusicService.markSmartQueueEdited()
        player.setMediaItems(restored.queue.toMediaItems(), restored.currentIndex, restored.positionMs)
        applyPlaybackMode(playbackMode, player)
        MusicService.updateDjConfig(_uiState.value.djModeEnabled, _uiState.value.djMixMode,
            _uiState.value.djMixDurationSeconds, playbackMode)
        player.prepare()
        updateFromPlayer(player)
        return true
    }

    private fun schedulePlaybackSessionSave(force: Boolean = false) {
        playbackSession.scheduleSave(playbackSessionSnapshot(), activeQueue.isNotEmpty(), force)
    }

    private fun savePlaybackSession() {
        playbackSession.save(playbackSessionSnapshot())
    }

    private fun playbackSessionSnapshot(): PlaybackSessionSnapshot? {
        val player = controller
        val currentSongId = player?.currentMediaItem?.mediaId?.toLongOrNull() ?: _uiState.value.currentSong?.id
        val positionMs = player?.currentPosition ?: _progressState.value.positionMs
        return playbackQueue.snapshot.playbackSessionSnapshot(currentSongId, positionMs, _uiState.value.playbackMode)
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (isActive) {
                controller?.let { player ->
                    updateProgressFromPlayer(player)
                }
                delay(if (_uiState.value.djModeEnabled) DJ_POSITION_UPDATE_FAST_MS else POSITION_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun syncQueueOrder() {
        val player = controller ?: return
        if (player.mediaItemCount == 0) return
        val state = _uiState.value
        val availableSongs = state.songs.visibleSongs(state.hiddenFolderPaths, state.excludeSmallAudios)
        val nextSourceSongs = activeSourceSongs.refreshedFrom(availableSongs)
        val nextQueue = activeQueue.refreshedQueuePreservingCurrent(
            availableSongs, player.currentMediaItem?.mediaId?.toLongOrNull(),
        )
        if (nextQueue.isEmpty()) return

        val currentId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val currentIndex = nextQueue.indexOfFirst { it.id == currentId }
        if (currentIndex < 0) return

        replaceActiveQueuePreservingPlayback(
            queue = nextQueue,
            sourceSongs = nextSourceSongs.ifEmpty { nextQueue },
            mode = activeQueueMode ?: _uiState.value.playbackMode,
            hasManualEdits = activeQueueHasManualEdits,
        )
    }

    private fun insertIntoGeneratedQueue(song: Song, afterCurrent: Boolean) {
        val state = _uiState.value
        if (song.folderPath in state.hiddenFolderPaths || !song.isIncludedBySmallAudioFilter(state.excludeSmallAudios)) return
        val player = controller
        val currentSong = state.currentSong
        if (player == null || currentSong == null || activeQueue.isEmpty()) {
            startQueuePlayback(
                queue = listOf(song),
                requestedSong = song,
                source = PlaybackQueueSource.Songs,
                forceRegenerate = true,
            )
            return
        }
        if (song.id == currentSong.id) return

        val currentId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: currentSong.id
        val nextQueue = activeQueue.insertSongNearCurrent(
            song = song,
            currentSongId = currentId,
            afterCurrent = afterCurrent,
        ) ?: return
        replaceActiveQueuePreservingPlayback(nextQueue, hasManualEdits = true)
    }

    private fun replaceActiveQueuePreservingPlayback(
        queue: List<Song>,
        sourceSongs: List<Song> = activeSourceSongs,
        mode: PlaybackMode = activeQueueMode ?: _uiState.value.playbackMode,
        positionMs: Long? = null,
        shouldPlay: Boolean? = null,
        hasManualEdits: Boolean = false,
        forceFullReset: Boolean = false,
    ) {
        MusicService.stopMixPreview(restorePlayback = true)
        if (hasManualEdits) MusicService.markSmartQueueEdited()
        val player = controller ?: return
        val currentId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: _uiState.value.currentSong?.id
        val replaced = queueController.replacePreservingPlayback(
            player = player,
            queue = queue,
            currentSongId = currentId,
            mode = mode,
            positionMs = positionMs,
            shouldPlay = shouldPlay,
            forceFullReset = forceFullReset,
            onReplaced = {
                setActiveQueue(queue = queue, sourceSongs = sourceSongs, mode = mode, hasManualEdits = hasManualEdits)
            },
        )
        if (replaced) updateFromPlayer(player)
    }

    private fun setActiveQueue(
        queue: List<Song>,
        source: PlaybackQueueSource = activeQueueSource,
        sourceKey: String? = activeQueueSourceKey,
        sourceSongs: List<Song> = activeSourceSongs.ifEmpty { queue },
        mode: PlaybackMode = _uiState.value.playbackMode,
        hasManualEdits: Boolean = false,
    ) {
        playbackQueue.replace(queue, sourceSongs, source, sourceKey, mode, hasManualEdits)
        MusicService.updateQueueOrigin(source, sourceKey)
        _uiState.update {
            it.copy(
                playbackQueue = queue,
                playbackQueueSource = source,
                playbackQueueSourceKey = sourceKey,
            )
        }
        schedulePlaybackSessionSave(force = true)
    }

    private fun clearActiveQueue() {
        playbackQueue.clear()
        MusicService.updateQueueOrigin(PlaybackQueueSource.Songs, null)
        pendingPlaylistModeChange = null
        updateActiveSmartShuffle(_uiState.value.playbackMode)
        _uiState.update {
            it.copy(
                playbackQueue = emptyList(),
                playbackQueueSource = PlaybackQueueSource.Songs,
                playbackQueueSourceKey = null,
            )
        }
    }

    private fun playActiveQueueIndex(index: Int) {
        MusicService.stopMixPreview(restorePlayback = false)
        MusicService.cancelSmartQueueForTransport()
        val player = controller ?: return
        if (queueController.playIndex(player, playbackQueue.snapshot, index, _uiState.value.playbackMode)) {
            updateFromPlayer(player, forceSessionSave = true)
        }
    }

    private fun playQueueMove(move: PlaybackQueueMove) {
        if (move.queue != activeQueue) {
            setActiveQueue(
                queue = move.queue,
                sourceSongs = activeSourceSongs.ifEmpty { move.queue },
                mode = _uiState.value.playbackMode,
                hasManualEdits = activeQueueHasManualEdits,
            )
        }
        playActiveQueueIndex(move.index)
    }

    private fun List<Song>.currentQueueSong(): Song? {
        val currentId = controller?.currentMediaItem?.mediaId?.toLongOrNull() ?: _uiState.value.currentSong?.id
        return firstOrNull { it.id == currentId }
    }

    private fun applyPlaybackMode(playbackMode: PlaybackMode, player: Player) {
        player.applyPlaybackMode(playbackMode)
    }

    private fun updateFromPlayer(player: Player, forceSessionSave: Boolean = false) {
        val previousState = _uiState.value
        val song = songForMediaId(player.currentMediaItem?.mediaId)
        val duration = player.duration.takeIf { it > 0 } ?: song?.durationMs ?: 0L
        val position = player.currentPosition.coerceAtLeast(0)
        lastKnownSongId = song?.id
        updateProgress(duration, position)

        _uiState.update {
            it.copy(
                currentSong = song,
                isPlaying = player.isPlaying,
                shuffleEnabled = it.playbackMode == PlaybackMode.Shuffle,
                repeatSetting = it.playbackMode.toRepeatSetting(),
                restoredSessionAvailable = if (player.isPlaying) false else it.restoredSessionAvailable,
            )
        }
        schedulePlaybackSessionSave(
            force = forceSessionSave || previousState.currentSong?.id != song?.id || previousState.isPlaying != player.isPlaying,
        )
    }

    private fun updateProgressFromPlayer(player: Player) {
        val song = songForMediaId(player.currentMediaItem?.mediaId)
        val duration = player.duration.takeIf { it > 0 } ?: song?.durationMs ?: 0L
        updateProgress(duration, player.currentPosition.coerceAtLeast(0))
        if (player.isPlaying || player.playWhenReady) schedulePlaybackSessionSave()
    }

    private fun updateProgress(durationMs: Long, positionMs: Long) {
        val safeDuration = durationMs.coerceAtLeast(0)
        val safePosition = positionMs.coerceAtMost(safeDuration.takeIf { it > 0 } ?: Long.MAX_VALUE)
        val nextProgress = PlaybackProgressState(
            durationMs = safeDuration,
            positionMs = safePosition,
        )
        if (_progressState.value != nextProgress) {
            _progressState.value = nextProgress
        }
    }

    private fun playbackErrorMessage(error: PlaybackException): String {
        return error.localizedMessage?.takeIf { it.isNotBlank() } ?: "No se pudo reproducir la canción"
    }

    private fun syncActiveQueueFromPlayerTimeline(player: Player) {
        if (player.mediaItemCount == 0 || _uiState.value.songs.isEmpty()) return
        val mediaIds = player.mediaItemIds()
        if (mediaIds == activeQueueMediaIds) return
        val timelineQueue = mediaIds.mapNotNull(::songForMediaId)
        if (timelineQueue.isEmpty()) return
        if (timelineQueue.map { it.id } == activeQueueIds) return

        setActiveQueue(
            queue = timelineQueue,
            sourceSongs = activeSourceSongs.ifEmpty { sourceSongsForTimeline(timelineQueue) },
            mode = activeQueueMode ?: _uiState.value.playbackMode,
            hasManualEdits = activeQueueHasManualEdits,
        )
    }

    private fun sourceSongsForTimeline(
        queue: List<Song>,
        source: PlaybackQueueSource = activeQueueSource,
        sourceKey: String? = activeQueueSourceKey,
    ): List<Song> {
        val state = _uiState.value
        val ids = queue.mapTo(HashSet()) { it.id }
        val availableSongs = state.songs.visibleSongs(state.hiddenFolderPaths, state.excludeSmallAudios)
        val sourceSongs = if (source == PlaybackQueueSource.History) {
            state.listeningHistory.orderedSongs(availableSongs, sourceKey.orEmpty())
        } else {
            collectionMixKey(source, sourceKey)?.let(::collectionSongs)
                ?: availableSongs.sortedWith(state.sortMode.songComparator())
        }
        return sourceSongs.filter { it.id in ids }.ifEmpty { queue }
    }

    private fun songForMediaId(mediaId: String?): Song? =
        songIndex.find(mediaId, _uiState.value.songs, activeQueue)

    private fun List<Song>.refreshedFrom(songs: List<Song>): List<Song> {
        val songsById = songs.associateBy { it.id }
        return mapNotNull { songsById[it.id] }
    }

    private fun PlaybackMode.toRepeatSetting(): RepeatSetting = when (this) {
        PlaybackMode.RepeatCurrent -> RepeatSetting.One
        PlaybackMode.RepeatList -> RepeatSetting.All
        PlaybackMode.Ordered,
        PlaybackMode.Shuffle -> RepeatSetting.Off
    }

    private data class BackupPreparationSnapshot(
        val collections: BackupCollectionState,
        val library: List<Song>,
        val collectionRevision: Long,
        val libraryRevision: Long,
    )

    private data class BackupPreviewSnapshot(
        val document: BackupDocument?,
        val collections: BackupCollectionState,
        val library: List<Song>,
        val collectionRevision: Long,
        val libraryRevision: Long,
        val references: List<BackupSong>,
        val matches: List<BackupSongMatch>,
    )

    private companion object {
        const val POSITION_UPDATE_INTERVAL_MS = 500L
        const val SLEEP_TIMER_UPDATE_INTERVAL_MS = 1_000L
        const val SLEEP_TIMER_MIN_DURATION_MS = 1_000L
        const val DJ_POSITION_UPDATE_FAST_MS = 200L
        const val MIN_DJ_MIX_SECONDS = 5
        const val MAX_DJ_MIX_SECONDS = 8
    }

    override fun onCleared() {
        MusicService.stopMixPreview(restorePlayback = true)
        savePlaybackSession()
        libraryMonitor.close()
        libraryLoader.close()
        positionJob?.cancel()
        sleepTimerJob?.cancel()
        controllerConnection.close()
        super.onCleared()
    }
}
