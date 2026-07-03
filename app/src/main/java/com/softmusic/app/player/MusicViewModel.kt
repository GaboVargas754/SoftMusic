package com.softmusic.app.player

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.softmusic.app.data.MusicPlaylist
import com.softmusic.app.data.MusicRepository
import com.softmusic.app.data.PlaylistMutation
import com.softmusic.app.data.Song
import com.softmusic.app.data.isIncludedBySmallAudioFilter
import com.softmusic.app.data.withNewPlaylist
import com.softmusic.app.data.withPlaylistCover
import com.softmusic.app.data.withQueueAddedToPlaylist
import com.softmusic.app.data.withSongAddedToPlaylist
import com.softmusic.app.data.withSongRemovedFromPlaylist
import com.softmusic.app.data.withoutPlaylist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MusicRepository()
    private val persistenceStore = MusicPersistenceStore(application)
    private val _uiState = MutableStateFlow(PlayerUiState(isLoading = true))
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    private val _progressState = MutableStateFlow(PlaybackProgressState())
    val progressState: StateFlow<PlaybackProgressState> = _progressState.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var loadSongsJob: Job? = null
    private var positionJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var activeSourceSongs: List<Song> = emptyList()
    private var activeQueue: List<Song> = emptyList()
    private var activeQueueSource: PlaybackQueueSource = PlaybackQueueSource.Songs
    private var activeQueueSourceKey: String? = null
    private var activeQueueMode: PlaybackMode? = null
    private var activeQueueHasManualEdits: Boolean = false
    private var lastKnownSongId: Long? = null
    private var hasRestoredPlaybackSession = false
    private var lastPlaybackSessionSaveMs = 0L

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateFromPlayer(player)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val nextSongId = mediaItem?.mediaId?.toLongOrNull()
            val previousSongId = lastKnownSongId
            if (
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                _uiState.value.playbackMode == PlaybackMode.Shuffle &&
                activeQueue.size > 1 &&
                previousSongId == activeQueue.lastOrNull()?.id &&
                nextSongId == activeQueue.firstOrNull()?.id
            ) {
                val currentSong = activeQueue.firstOrNull { it.id == nextSongId } ?: return
                val player = controller ?: return
                rebuildShuffleQueueFromCurrent(
                    player = player,
                    currentSong = currentSong,
                    positionMs = player.currentPosition.coerceAtLeast(0),
                    shouldPlay = player.isPlaying || player.playWhenReady,
                )
                return
            }

        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.update {
                it.copy(errorMessage = playbackErrorMessage(error))
            }
        }
    }

    init {
        _uiState.update {
            it.copy(
                favoriteSongIds = persistenceStore.loadFavoriteSongIds(),
                playlists = persistenceStore.loadPlaylists(),
            )
        }
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
    ) {
        _uiState.update { current ->
            val allFolders = current.songs.toFolders()
            val visibleFolders = current.songs.visibleSongs(emptySet(), excludeSmallAudios).toFolders()
            val selectedFolderPath = defaultFolderPath?.takeIf { folderPath ->
                folderPath !in hiddenFolderPaths && (
                    current.songs.isEmpty() ||
                        current.songs.any { it.folderPath == folderPath && it.isIncludedBySmallAudioFilter(excludeSmallAudios) }
                    )
            }
            current.copy(
                selectedFolderPath = selectedFolderPath,
                hiddenFolderPaths = hiddenFolderPaths,
                excludeSmallAudios = excludeSmallAudios,
                playbackMode = defaultPlaybackMode,
                sortMode = defaultSortMode,
                djModeEnabled = djModeEnabled,
                djMixMode = djMixMode,
                djMixDurationSeconds = djMixDurationSeconds.coerceIn(MIN_DJ_MIX_SECONDS, MAX_DJ_MIX_SECONDS),
                shuffleEnabled = defaultPlaybackMode == PlaybackMode.Shuffle,
                repeatSetting = defaultPlaybackMode.toRepeatSetting(),
                allFolders = allFolders,
                folders = visibleFolders.visibleFolders(hiddenFolderPaths),
                visibleSongs = current.songs.filteredAndSorted(selectedFolderPath, defaultSortMode, hiddenFolderPaths, excludeSmallAudios),
            )
        }
        MusicService.updateDjConfig(
            enabled = djModeEnabled,
            mixMode = djMixMode,
            mixDurationSeconds = djMixDurationSeconds,
            playbackMode = defaultPlaybackMode,
        )
        controller?.let { applyPlaybackMode(defaultPlaybackMode, it) }
    }

    fun loadSongs() {
        loadSongsJob?.cancel()
        loadSongsJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.loadSongs(getApplication())
                }
            }.onSuccess { songs ->
                _uiState.update { current ->
                    val allFolders = songs.toFolders()
                    val visibleFolders = songs.visibleSongs(emptySet(), current.excludeSmallAudios).toFolders()
                    val availableFolderPaths = allFolders.map { it.path }.toSet()
                    val hiddenFolderPaths = current.hiddenFolderPaths.filterTo(mutableSetOf()) { it in availableFolderPaths }
                    val selectedFolderPath = current.selectedFolderPath?.takeIf { folderPath ->
                        folderPath !in hiddenFolderPaths && songs.any {
                            it.folderPath == folderPath && it.isIncludedBySmallAudioFilter(current.excludeSmallAudios)
                        }
                    }
                    val sorted = songs.filteredAndSorted(
                        selectedFolderPath = selectedFolderPath,
                        sortMode = current.sortMode,
                        hiddenFolderPaths = hiddenFolderPaths,
                        excludeSmallAudios = current.excludeSmallAudios,
                    )
                    current.copy(
                        songs = songs,
                        visibleSongs = sorted,
                        allFolders = allFolders,
                        folders = visibleFolders.visibleFolders(hiddenFolderPaths),
                        selectedFolderPath = selectedFolderPath,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
                controller?.let { player ->
                    if (!tryRestorePlaybackSession(player)) syncQueueOrder()
                    updateFromPlayer(player)
                }
            }.onFailure { throwable ->
                if (throwable is CancellationException) return@launch
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "No se pudo cargar la música local",
                    )
                }
            }
        }
    }

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
        val currentIds = _uiState.value.favoriteSongIds
        val updatedIds = if (songId in currentIds) {
            currentIds - songId
        } else {
            currentIds + songId
        }
        persistenceStore.saveFavoriteSongIds(updatedIds)
        _uiState.update { it.copy(favoriteSongIds = updatedIds) }
    }

    fun createPlaylist(name: String) {
        persistPlaylistMutation(
            _uiState.value.playlists.withNewPlaylist(
                name = name,
                id = newPlaylistId(),
            ),
        )
    }

    fun createPlaylistFromQueue(name: String) {
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
        val result = _uiState.value.playlists.withQueueAddedToPlaylist(
            playlistId = playlistId,
            queueSongIds = activeQueue.map { it.id },
        )
        if (result.changed) persistPlaylists(result.playlists)
        return result.addedCount
    }

    fun deletePlaylist(playlistId: String) {
        persistPlaylistMutation(_uiState.value.playlists.withoutPlaylist(playlistId))
    }

    fun setPlaylistCover(playlistId: String, songId: Long?) {
        persistPlaylistMutation(_uiState.value.playlists.withPlaylistCover(playlistId, songId))
    }

    fun addSongToPlaylist(playlistId: String, songId: Long) {
        persistPlaylistMutation(_uiState.value.playlists.withSongAddedToPlaylist(playlistId, songId))
    }

    fun removeSongFromPlaylist(playlistId: String, songId: Long) {
        persistPlaylistMutation(_uiState.value.playlists.withSongRemovedFromPlaylist(playlistId, songId))
    }

    fun playNext(song: Song) {
        insertIntoGeneratedQueue(song = song, afterCurrent = true)
    }

    fun playAtEnd(song: Song) {
        insertIntoGeneratedQueue(song = song, afterCurrent = false)
    }

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
        val player = controller ?: return
        _uiState.update { it.copy(restoredSessionAvailable = false) }
        player.play()
        updateFromPlayer(player)
    }

    fun dismissRestoredSession() {
        persistenceStore.clearPlaybackSession()
        hasRestoredPlaybackSession = true
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
        if (song.id == currentId || activeQueue.none { it.id == song.id }) return
        val nextQueue = activeQueue.filterNot { it.id == song.id }
        if (nextQueue.isEmpty()) return
        replaceActiveQueuePreservingPlayback(nextQueue, hasManualEdits = true)
    }

    fun moveQueuedSong(song: Song, offset: Int) {
        if (offset == 0) return
        val currentId = controller?.currentMediaItem?.mediaId?.toLongOrNull() ?: _uiState.value.currentSong?.id ?: return
        val currentIndex = activeQueue.indexOfFirst { it.id == currentId }
        val fromIndex = activeQueue.indexOfFirst { it.id == song.id }
        if (currentIndex < 0 || fromIndex <= currentIndex) return
        val toIndex = (fromIndex + offset).coerceIn(currentIndex + 1, activeQueue.lastIndex)
        if (toIndex == fromIndex) return

        val nextQueue = activeQueue.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
        replaceActiveQueuePreservingPlayback(nextQueue, hasManualEdits = true)
    }

    fun clearUpcomingQueue() {
        val currentId = controller?.currentMediaItem?.mediaId?.toLongOrNull() ?: _uiState.value.currentSong?.id ?: return
        val currentSong = activeQueue.firstOrNull { it.id == currentId } ?: return
        if (activeQueue.size <= 1) return
        replaceActiveQueuePreservingPlayback(listOf(currentSong), hasManualEdits = true, forceFullReset = true)
    }

    private fun startQueuePlayback(
        queue: List<Song>,
        requestedSong: Song? = null,
        source: PlaybackQueueSource,
        sourceKey: String? = null,
        forceRegenerate: Boolean = false,
    ) {
        val player = controller ?: run {
            _uiState.update { it.copy(errorMessage = "El reproductor todavía se está preparando") }
            return
        }
        val state = _uiState.value
        _uiState.update { it.copy(restoredSessionAvailable = false, restoredSessionPositionMs = 0) }
        val sourceSongs = queue.visibleSongs(state.hiddenFolderPaths, state.excludeSmallAudios).distinctSongsById()
        if (sourceSongs.isEmpty()) return
        val safeRequestedSong = requestedSong?.takeIf { requested -> sourceSongs.any { it.id == requested.id } }
        if (requestedSong != null && safeRequestedSong == null) return

        val playbackMode = state.playbackMode
        if (forceRegenerate || shouldGenerateQueue(sourceSongs, safeRequestedSong, source, sourceKey, playbackMode)) {
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
        val player = controller ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
        updateFromPlayer(player)
    }

    fun next() {
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
        val player = controller ?: return
        val duration = player.duration.takeIf { it > 0 } ?: _progressState.value.durationMs
        val safePosition = positionMs
            .coerceAtLeast(0)
            .coerceAtMost(duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
        updateProgress(duration, safePosition)
        player.seekTo(safePosition)
    }

    fun setPlaybackMode(playbackMode: PlaybackMode) {
        val previousMode = _uiState.value.playbackMode
        val player = controller
        val shouldPlay = player?.let { it.isPlaying || it.playWhenReady } ?: false
        val currentPosition = player?.currentPosition?.coerceAtLeast(0) ?: 0L
        val currentSong = _uiState.value.currentSong

        controller?.let { applyPlaybackMode(playbackMode, it) }
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

        if (previousMode != playbackMode && activeSourceSongs.isNotEmpty()) {
            val state = _uiState.value
            val availableSongs = state.songs.visibleSongs(state.hiddenFolderPaths, state.excludeSmallAudios)
            val sourceSongs = activeSourceSongs.refreshedFrom(availableSongs).ifEmpty {
                activeSourceSongs.visibleSongs(state.hiddenFolderPaths, state.excludeSmallAudios)
            }
            if (sourceSongs.isEmpty()) return
            val generatedQueue = sourceSongs.generatePlaybackQueue(playbackMode, currentSong)
            setActiveQueue(
                queue = generatedQueue,
                sourceSongs = sourceSongs,
                mode = playbackMode,
            )
            if (player != null && currentSong != null) {
                replaceActiveQueuePreservingPlayback(
                    queue = generatedQueue,
                    sourceSongs = sourceSongs,
                    mode = playbackMode,
                    positionMs = currentPosition,
                    shouldPlay = shouldPlay,
                )
            }
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
        _uiState.update { current ->
            val allFolders = current.songs.toFolders()
            val visibleFolders = current.songs.visibleSongs(emptySet(), current.excludeSmallAudios).toFolders()
            val availableFolderPaths = allFolders.map { it.path }.toSet()
            val cleanHiddenFolderPaths = hiddenFolderPaths.filterTo(mutableSetOf()) { it in availableFolderPaths }
            val selectedFolderPath = current.selectedFolderPath?.takeIf { folderPath ->
                folderPath !in cleanHiddenFolderPaths && current.songs.any {
                    it.folderPath == folderPath && it.isIncludedBySmallAudioFilter(current.excludeSmallAudios)
                }
            }
            current.copy(
                hiddenFolderPaths = cleanHiddenFolderPaths,
                allFolders = allFolders,
                folders = visibleFolders.visibleFolders(cleanHiddenFolderPaths),
                selectedFolderPath = selectedFolderPath,
                visibleSongs = current.songs.filteredAndSorted(
                    selectedFolderPath = selectedFolderPath,
                    sortMode = current.sortMode,
                    hiddenFolderPaths = cleanHiddenFolderPaths,
                    excludeSmallAudios = current.excludeSmallAudios,
                ),
            )
        }
        syncQueueOrder()
    }

    fun setExcludeSmallAudios(excludeSmallAudios: Boolean) {
        _uiState.update { current ->
            val visibleFolders = current.songs.visibleSongs(emptySet(), excludeSmallAudios).toFolders()
            val selectedFolderPath = current.selectedFolderPath?.takeIf { folderPath ->
                folderPath !in current.hiddenFolderPaths && (
                    current.songs.isEmpty() ||
                        current.songs.any { it.folderPath == folderPath && it.isIncludedBySmallAudioFilter(excludeSmallAudios) }
                    )
            }
            current.copy(
                excludeSmallAudios = excludeSmallAudios,
                folders = visibleFolders.visibleFolders(current.hiddenFolderPaths),
                selectedFolderPath = selectedFolderPath,
                visibleSongs = current.songs.filteredAndSorted(
                    selectedFolderPath = selectedFolderPath,
                    sortMode = current.sortMode,
                    hiddenFolderPaths = current.hiddenFolderPaths,
                    excludeSmallAudios = excludeSmallAudios,
                ),
            )
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
        MusicService.updateDjConfig(
            enabled = enabled,
            mixMode = mixMode,
            mixDurationSeconds = mixDurationSeconds.coerceIn(MIN_DJ_MIX_SECONDS, MAX_DJ_MIX_SECONDS),
            playbackMode = _uiState.value.playbackMode,
        )
    }

    fun shutdownPlayback() {
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
        val context = getApplication<Application>()
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val mediaController = runCatching { future.get() }.getOrElse { throwable ->
                    if (controllerFuture === future) {
                        _uiState.update {
                            it.copy(
                                hasController = false,
                                errorMessage = throwable.message ?: "No se pudo conectar el reproductor",
                            )
                        }
                    }
                    return@addListener
                }
                if (controllerFuture !== future) {
                    mediaController.release()
                    return@addListener
                }
                controller = mediaController
                mediaController.addListener(listener)
                _uiState.update {
                    it.copy(
                        hasController = true,
                    )
                }
                applyPlaybackMode(_uiState.value.playbackMode, mediaController)
                tryRestorePlaybackSession(mediaController)
                updateFromPlayer(mediaController)
                startPositionUpdates()
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private fun persistPlaylistMutation(mutation: PlaylistMutation) {
        if (mutation.changed) persistPlaylists(mutation.playlists)
    }

    private fun persistPlaylists(playlists: List<MusicPlaylist>) {
        persistenceStore.savePlaylists(playlists)
        _uiState.update { it.copy(playlists = playlists) }
    }

    private fun newPlaylistId(): String = "${System.currentTimeMillis()}-${Random.nextInt(1_000, 9_999)}"

    private fun tryRestorePlaybackSession(player: MediaController): Boolean {
        if (hasRestoredPlaybackSession || player.mediaItemCount > 0) return false
        val songs = _uiState.value.songs
        if (songs.isEmpty()) return false
        val session = persistenceStore.loadPlaybackSession() ?: run {
            hasRestoredPlaybackSession = true
            return false
        }
        val songsById = songs.associateBy { it.id }
        val restoredQueue = session.queueIds.mapNotNull { songsById[it] }.distinctSongsById()
        if (restoredQueue.isEmpty()) {
            hasRestoredPlaybackSession = true
            return false
        }
        val currentIndex = restoredQueue.indexOfFirst { it.id == session.currentSongId }.takeIf { it >= 0 } ?: 0
        val currentSong = restoredQueue[currentIndex]
        val positionMs = session.positionMs
            .coerceAtLeast(0)
            .coerceAtMost(currentSong.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
        val playbackMode = session.playbackMode ?: _uiState.value.playbackMode

        hasRestoredPlaybackSession = true
        _uiState.update {
            it.copy(
                playbackMode = playbackMode,
                shuffleEnabled = playbackMode == PlaybackMode.Shuffle,
                repeatSetting = playbackMode.toRepeatSetting(),
                restoredSessionAvailable = true,
                restoredSessionPositionMs = positionMs,
            )
        }
        setActiveQueue(
            queue = restoredQueue,
            sourceSongs = restoredQueue,
            mode = playbackMode,
            hasManualEdits = true,
        )
        player.setMediaItems(restoredQueue.toMediaItems(), currentIndex, positionMs)
        applyPlaybackMode(playbackMode, player)
        player.prepare()
        updateFromPlayer(player)
        return true
    }

    private fun schedulePlaybackSessionSave(force: Boolean = false) {
        if (activeQueue.isEmpty()) return
        val now = System.currentTimeMillis()
        if (!force && now - lastPlaybackSessionSaveMs < PLAYBACK_SESSION_SAVE_INTERVAL_MS) return
        lastPlaybackSessionSaveMs = now
        savePlaybackSession()
    }

    private fun savePlaybackSession() {
        val player = controller
        val queue = activeQueue
        val currentSongId = player?.currentMediaItem?.mediaId?.toLongOrNull() ?: _uiState.value.currentSong?.id ?: return
        if (queue.none { it.id == currentSongId }) return
        val positionMs = player?.currentPosition?.coerceAtLeast(0) ?: _progressState.value.positionMs.coerceAtLeast(0)
        persistenceStore.savePlaybackSession(
            PlaybackSessionSnapshot(
                queueIds = queue.map { it.id },
                currentSongId = currentSongId,
                positionMs = positionMs,
                playbackMode = _uiState.value.playbackMode,
            ),
        )
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

    private fun rebuildShuffleQueueFromCurrent(
        player: Player,
        currentSong: Song,
        positionMs: Long = player.currentPosition.coerceAtLeast(0),
        shouldPlay: Boolean,
    ): Boolean {
        if (!rebuildShuffleQueueInMemoryFromCurrent(currentSong)) return false
        val nextQueue = activeQueue
        player.setMediaItems(nextQueue.toMediaItems(), 0, positionMs.coerceAtLeast(0))
        applyPlaybackMode(PlaybackMode.Shuffle, player)
        player.prepare()
        if (shouldPlay) player.play()
        updateFromPlayer(player)
        return true
    }

    private fun rebuildShuffleQueueInMemoryFromCurrent(currentSong: Song): Boolean {
        val sourceSongs = activeSourceSongs.ifEmpty { _uiState.value.playableSongs() }.distinctSongsById()
        val current = sourceSongs.firstOrNull { it.id == currentSong.id } ?: return false
        if (sourceSongs.size <= 1) return false

        setActiveQueue(
            queue = sourceSongs.shuffledQueueWithCurrentFirst(current),
            sourceSongs = sourceSongs,
            mode = PlaybackMode.Shuffle,
        )
        return true
    }

    private fun syncQueueOrder() {
        val player = controller ?: return
        if (player.mediaItemCount == 0) return
        val state = _uiState.value
        val availableSongs = state.songs.visibleSongs(state.hiddenFolderPaths, state.excludeSmallAudios)
        val nextSourceSongs = activeSourceSongs.refreshedFrom(availableSongs)
        val nextQueue = activeQueue.refreshedFrom(availableSongs)
        if (nextQueue.isEmpty()) return

        val currentId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val currentIndex = nextQueue.indexOfFirst { it.id == currentId }
        if (currentIndex < 0) return

        setActiveQueue(
            queue = nextQueue,
            sourceSongs = nextSourceSongs.ifEmpty { nextQueue },
            mode = activeQueueMode ?: _uiState.value.playbackMode,
            hasManualEdits = activeQueueHasManualEdits,
        )
        val wasPlaying = player.isPlaying
        val currentPosition = player.currentPosition.coerceAtLeast(0)
        player.setMediaItems(nextQueue.toMediaItems(), currentIndex, currentPosition)
        player.prepare()
        if (wasPlaying) player.play()
        updateFromPlayer(player)
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
        val player = controller ?: return
        if (queue.isEmpty()) return

        val currentId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: _uiState.value.currentSong?.id
        val currentIndex = queue.indexOfFirst { it.id == currentId }
        if (currentIndex < 0) return

        val wasPlaying = shouldPlay ?: (player.isPlaying || player.playWhenReady)
        val currentPosition = positionMs ?: player.currentPosition.coerceAtLeast(0)
        val updatedIncrementally = !forceFullReset && syncPlayerQueueIncrementally(player, queue, currentId)

        setActiveQueue(queue = queue, sourceSongs = sourceSongs, mode = mode, hasManualEdits = hasManualEdits)
        if (updatedIncrementally) {
            applyPlaybackMode(mode, player)
            if (wasPlaying && !player.playWhenReady) player.play()
        } else {
            player.setMediaItems(queue.toMediaItems(), currentIndex, currentPosition)
            applyPlaybackMode(mode, player)
            player.prepare()
            if (wasPlaying) player.play()
        }
        updateFromPlayer(player)
    }

    private fun syncPlayerQueueIncrementally(player: Player, queue: List<Song>, currentSongId: Long?): Boolean {
        val currentId = currentSongId?.toString() ?: return false
        if (player.mediaItemCount == 0 || player.currentMediaItem?.mediaId != currentId) return false

        val desiredIds = queue.map { it.id.toString() }
        if (currentId !in desiredIds || desiredIds.distinct().size != desiredIds.size) return false
        val songsByMediaId = queue.associateBy { it.id.toString() }

        for (index in player.mediaItemCount - 1 downTo 0) {
            val mediaId = player.getMediaItemAt(index).mediaId
            if (mediaId !in songsByMediaId) {
                if (mediaId == currentId) return false
                player.removeMediaItem(index)
            }
        }

        desiredIds.forEachIndexed { desiredIndex, mediaId ->
            if (player.indexOfMediaItem(mediaId) < 0) {
                val song = songsByMediaId[mediaId] ?: return false
                player.addMediaItem(desiredIndex.coerceIn(0, player.mediaItemCount), song.toMediaItem())
            }
        }

        desiredIds.forEachIndexed { desiredIndex, mediaId ->
            val currentIndex = player.indexOfMediaItem(mediaId)
            if (currentIndex < 0) return false
            if (currentIndex != desiredIndex) {
                player.moveMediaItem(currentIndex, desiredIndex)
            }
        }

        return player.mediaItemIds() == desiredIds && player.currentMediaItem?.mediaId == currentId
    }

    private fun Player.indexOfMediaItem(mediaId: String): Int {
        for (index in 0 until mediaItemCount) {
            if (getMediaItemAt(index).mediaId == mediaId) return index
        }
        return -1
    }

    private fun Player.mediaItemIds(): List<String> = List(mediaItemCount) { index ->
        getMediaItemAt(index).mediaId
    }

    private fun setActiveQueue(
        queue: List<Song>,
        source: PlaybackQueueSource = activeQueueSource,
        sourceKey: String? = activeQueueSourceKey,
        sourceSongs: List<Song> = activeSourceSongs.ifEmpty { queue },
        mode: PlaybackMode = _uiState.value.playbackMode,
        hasManualEdits: Boolean = false,
    ) {
        activeSourceSongs = sourceSongs
        activeQueue = queue
        activeQueueSource = source
        activeQueueSourceKey = sourceKey
        activeQueueMode = mode
        activeQueueHasManualEdits = hasManualEdits
        _uiState.update {
            it.copy(
                playbackQueue = queue,
            )
        }
        schedulePlaybackSessionSave(force = true)
    }

    private fun clearActiveQueue() {
        activeSourceSongs = emptyList()
        activeQueue = emptyList()
        activeQueueSource = PlaybackQueueSource.Songs
        activeQueueSourceKey = null
        activeQueueMode = null
        activeQueueHasManualEdits = false
        _uiState.update { it.copy(playbackQueue = emptyList()) }
    }

    private fun playActiveQueueIndex(index: Int) {
        val player = controller ?: return
        val queue = activeQueue
        if (queue.isEmpty()) return

        val safeIndex = index.coerceIn(0, queue.lastIndex)
        val shouldPlay = player.isPlaying || player.playWhenReady
        player.setMediaItems(queue.toMediaItems(), safeIndex, 0L)
        applyPlaybackMode(_uiState.value.playbackMode, player)
        player.prepare()
        if (shouldPlay) player.play()
        updateFromPlayer(player)
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

    private fun shouldGenerateQueue(
        sourceSongs: List<Song>,
        requestedSong: Song?,
        source: PlaybackQueueSource,
        sourceKey: String?,
        playbackMode: PlaybackMode,
    ): Boolean {
        if (activeQueue.isEmpty() || activeSourceSongs.isEmpty()) return true
        if (activeQueueHasManualEdits) return true
        if (activeQueueSource != source || activeQueueSourceKey != sourceKey) return true
        if (activeQueueMode != playbackMode) return true
        if (requestedSong != null && activeQueue.none { it.id == requestedSong.id }) return true
        if (activeSourceSongs.map { it.id } != sourceSongs.map { it.id }) return true
        return false
    }

    private fun applyPlaybackMode(playbackMode: PlaybackMode, player: Player) {
        player.shuffleModeEnabled = false
        player.repeatMode = when (playbackMode) {
            PlaybackMode.Ordered -> Player.REPEAT_MODE_OFF
            PlaybackMode.RepeatList -> Player.REPEAT_MODE_ALL
            PlaybackMode.RepeatCurrent -> Player.REPEAT_MODE_ONE
            PlaybackMode.Shuffle -> Player.REPEAT_MODE_ALL
        }
        updateFromPlayer(player)
    }

    private fun updateFromPlayer(player: Player) {
        syncActiveQueueFromPlayerTimeline(player)
        val currentId = player.currentMediaItem?.mediaId?.toLongOrNull()
        val song = currentId?.let { id ->
            _uiState.value.songs.firstOrNull { it.id == id } ?: activeQueue.firstOrNull { it.id == id }
        }
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
        schedulePlaybackSessionSave()
    }

    private fun updateProgressFromPlayer(player: Player) {
        val currentId = player.currentMediaItem?.mediaId?.toLongOrNull()
        val song = currentId?.let { id ->
            _uiState.value.songs.firstOrNull { it.id == id } ?: activeQueue.firstOrNull { it.id == id }
        }
        val duration = player.duration.takeIf { it > 0 } ?: song?.durationMs ?: 0L
        updateProgress(duration, player.currentPosition.coerceAtLeast(0))
        schedulePlaybackSessionSave()
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
        val songsById = _uiState.value.songs.associateBy { it.id.toString() }
        val timelineQueue = List(player.mediaItemCount) { index ->
            songsById[player.getMediaItemAt(index).mediaId]
        }.filterNotNull()
        if (timelineQueue.isEmpty()) return
        if (timelineQueue.map { it.id } == activeQueue.map { it.id }) return

        setActiveQueue(
            queue = timelineQueue,
            sourceSongs = activeSourceSongs.ifEmpty { timelineQueue },
            mode = activeQueueMode ?: _uiState.value.playbackMode,
            hasManualEdits = activeQueueHasManualEdits,
        )
    }

    private fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(Uri.parse(uri))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setDisplayTitle(title)
                .setSubtitle(artist)
                .setDescription(album)
                .setArtworkUri(artworkUri?.let(Uri::parse))
                .setDurationMs(durationMs.coerceAtLeast(0L))
                .setIsPlayable(true)
                .build(),
        )
        .build()

    private fun List<Song>.toMediaItems(): List<MediaItem> = map { song ->
        song.toMediaItem()
    }

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

    private companion object {
        const val POSITION_UPDATE_INTERVAL_MS = 500L
        const val PLAYBACK_SESSION_SAVE_INTERVAL_MS = 2_000L
        const val SLEEP_TIMER_UPDATE_INTERVAL_MS = 1_000L
        const val SLEEP_TIMER_MIN_DURATION_MS = 1_000L
        const val DJ_POSITION_UPDATE_FAST_MS = 200L
        const val MIN_DJ_MIX_SECONDS = 5
        const val MAX_DJ_MIX_SECONDS = 8
    }

    override fun onCleared() {
        savePlaybackSession()
        val future = controllerFuture
        controllerFuture = null
        controller?.removeListener(listener)
        loadSongsJob?.cancel()
        positionJob?.cancel()
        sleepTimerJob?.cancel()
        future?.let(MediaController::releaseFuture)
        controller = null
        super.onCleared()
    }
}
