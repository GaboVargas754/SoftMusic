package com.softmusic.app.player

import android.app.Application
import android.content.ComponentName
import android.content.Context
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
import com.softmusic.app.data.MusicFolder
import com.softmusic.app.data.MusicPlaylist
import com.softmusic.app.data.MusicRepository
import com.softmusic.app.data.Song
import com.softmusic.app.data.isIncludedBySmallAudioFilter
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
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MusicRepository()
    private val favoritesPreferences = application.getSharedPreferences(FAVORITES_PREFS_NAME, Context.MODE_PRIVATE)
    private val playlistsPreferences = application.getSharedPreferences(PLAYLISTS_PREFS_NAME, Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(PlayerUiState(isLoading = true))
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    private val _progressState = MutableStateFlow(PlaybackProgressState())
    val progressState: StateFlow<PlaybackProgressState> = _progressState.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var positionJob: Job? = null
    private var activeSourceSongs: List<Song> = emptyList()
    private var activeQueue: List<Song> = emptyList()
    private var activeQueueSource: PlaybackQueueSource = PlaybackQueueSource.Songs
    private var activeQueueSourceKey: String? = null
    private var activeQueueMode: PlaybackMode? = null
    private var activeQueueHasManualEdits: Boolean = false
    private var lastKnownSongId: Long? = null

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
        loadFavoriteSongIds()
        loadPlaylists()
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
        viewModelScope.launch {
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
                    syncQueueOrder()
                    updateFromPlayer(player)
                }
            }.onFailure { throwable ->
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
        saveFavoriteSongIds(updatedIds)
        _uiState.update { it.copy(favoriteSongIds = updatedIds) }
    }

    fun createPlaylist(name: String) {
        val cleanName = name.trim()
            .take(MAX_PLAYLIST_NAME_LENGTH)
            .takeIf { it.isNotBlank() } ?: return
        if (_uiState.value.playlists.any { it.name.trim().lowercase() == cleanName.lowercase() }) return
        val playlist = MusicPlaylist(
            id = "${System.currentTimeMillis()}-${Random.nextInt(1_000, 9_999)}",
            name = cleanName,
            songIds = emptyList(),
        )
        val playlists = _uiState.value.playlists + playlist
        savePlaylists(playlists)
        _uiState.update { it.copy(playlists = playlists) }
    }

    fun deletePlaylist(playlistId: String) {
        val playlists = _uiState.value.playlists.filterNot { it.id == playlistId }
        savePlaylists(playlists)
        _uiState.update { it.copy(playlists = playlists) }
    }

    fun addSongToPlaylist(playlistId: String, songId: Long) {
        val playlists = _uiState.value.playlists.map { playlist ->
            if (playlist.id == playlistId && songId !in playlist.songIds) {
                playlist.copy(songIds = playlist.songIds + songId)
            } else {
                playlist
            }
        }
        savePlaylists(playlists)
        _uiState.update { it.copy(playlists = playlists) }
    }

    fun removeSongFromPlaylist(playlistId: String, songId: Long) {
        val playlists = _uiState.value.playlists.map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(songIds = playlist.songIds.filterNot { it == songId })
            } else {
                playlist
            }
        }
        savePlaylists(playlists)
        _uiState.update { it.copy(playlists = playlists) }
    }

    fun playNext(song: Song) {
        insertIntoGeneratedQueue(song = song, afterCurrent = true)
    }

    fun playAtEnd(song: Song) {
        insertIntoGeneratedQueue(song = song, afterCurrent = false)
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
        val queue = activeQueue.ifEmpty { _uiState.value.playableSongs() }
        if (queue.isEmpty()) return
        val player = controller ?: return
        if (activeQueue.isEmpty()) {
            setActiveQueue(queue = queue, sourceSongs = queue, mode = _uiState.value.playbackMode)
        }

        val currentIndex = queue.currentQueueIndex().coerceAtLeast(0)
        if (_uiState.value.playbackMode == PlaybackMode.Shuffle && currentIndex >= queue.lastIndex) {
            val currentSong = queue.getOrNull(currentIndex) ?: return
            if (rebuildShuffleQueueFromCurrent(player, currentSong, shouldPlay = player.isPlaying || player.playWhenReady)) {
                playActiveQueueIndex(1)
            }
            return
        }
        val nextIndex = when (_uiState.value.playbackMode) {
            PlaybackMode.Ordered -> {
                if (currentIndex >= queue.lastIndex) return
                currentIndex + 1
            }
            PlaybackMode.Shuffle,
            PlaybackMode.RepeatList,
            PlaybackMode.RepeatCurrent -> (currentIndex + 1) % queue.size
        }
        playActiveQueueIndex(nextIndex)
    }

    fun previous() {
        val player = controller ?: return
        val queue = activeQueue.ifEmpty { _uiState.value.playableSongs() }
        if (queue.isEmpty()) return
        if (activeQueue.isEmpty()) {
            setActiveQueue(queue = queue, sourceSongs = queue, mode = _uiState.value.playbackMode)
        }

        val currentIndex = queue.currentQueueIndex().coerceAtLeast(0)
        val previousIndex = when (_uiState.value.playbackMode) {
            PlaybackMode.Ordered -> if (player.currentPosition > RESTART_PREVIOUS_THRESHOLD_MS) {
                currentIndex
            } else {
                (currentIndex - 1).coerceAtLeast(0)
            }
            PlaybackMode.Shuffle,
            PlaybackMode.RepeatList,
            PlaybackMode.RepeatCurrent -> if (player.currentPosition > RESTART_PREVIOUS_THRESHOLD_MS) {
                currentIndex
            } else {
                (currentIndex - 1 + queue.size) % queue.size
            }
        }
        playActiveQueueIndex(previousIndex)
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
                updateFromPlayer(mediaController)
                startPositionUpdates()
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private fun loadFavoriteSongIds() {
        val ids = favoritesPreferences
            .getStringSet(KEY_FAVORITE_SONG_IDS, emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()
        _uiState.update { it.copy(favoriteSongIds = ids) }
    }

    private fun loadPlaylists() {
        val raw = playlistsPreferences.getString(KEY_PLAYLISTS, null).orEmpty()
        val playlists = runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val songsArray = item.optJSONArray("songIds") ?: JSONArray()
                MusicPlaylist(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    songIds = List(songsArray.length()) { songIndex -> songsArray.optLong(songIndex) },
                )
            }.filter { it.id.isNotBlank() && it.name.isNotBlank() }
        }.getOrDefault(emptyList())
        _uiState.update { it.copy(playlists = playlists) }
    }

    private fun saveFavoriteSongIds(ids: Set<Long>) {
        favoritesPreferences.edit()
            .putStringSet(KEY_FAVORITE_SONG_IDS, ids.map { it.toString() }.toSet())
            .apply()
    }

    private fun savePlaylists(playlists: List<MusicPlaylist>) {
        val array = JSONArray()
        playlists.forEach { playlist ->
            val songIds = JSONArray()
            playlist.songIds.forEach(songIds::put)
            array.put(
                JSONObject()
                    .put("id", playlist.id)
                    .put("name", playlist.name)
                    .put("songIds", songIds),
            )
        }
        playlistsPreferences.edit()
            .putString(KEY_PLAYLISTS, array.toString())
            .apply()
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
    ) {
        val player = controller ?: return
        if (queue.isEmpty()) return

        val currentId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: _uiState.value.currentSong?.id
        val currentIndex = queue.indexOfFirst { it.id == currentId }
        if (currentIndex < 0) return

        val wasPlaying = shouldPlay ?: (player.isPlaying || player.playWhenReady)
        val currentPosition = positionMs ?: player.currentPosition.coerceAtLeast(0)
        val updatedIncrementally = syncPlayerQueueIncrementally(player, queue, currentId)

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

    private fun List<Song>.currentQueueIndex(): Int {
        val currentId = controller?.currentMediaItem?.mediaId?.toLongOrNull() ?: _uiState.value.currentSong?.id
        return indexOfFirst { it.id == currentId }
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
            _uiState.value.songs.firstOrNull { it.id == id }
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
            )
        }
    }

    private fun updateProgressFromPlayer(player: Player) {
        val currentId = player.currentMediaItem?.mediaId?.toLongOrNull()
        val song = currentId?.let { id ->
            _uiState.value.songs.firstOrNull { it.id == id }
        }
        val duration = player.duration.takeIf { it > 0 } ?: song?.durationMs ?: 0L
        updateProgress(duration, player.currentPosition.coerceAtLeast(0))
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
                .setArtworkUri(artworkUri?.let(Uri::parse))
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

    private fun SortMode.comparator(): Comparator<Song> = when (this) {
        SortMode.Recent -> compareByDescending<Song> { it.dateAddedSeconds }.thenBy { it.title.lowercase() }
        SortMode.Title -> compareBy<Song> { it.title.lowercase() }.thenBy { it.artist.lowercase() }
    }

    private fun List<Song>.filteredAndSorted(
        selectedFolderPath: String?,
        sortMode: SortMode,
        hiddenFolderPaths: Set<String>,
        excludeSmallAudios: Boolean,
    ): List<Song> = asSequence()
        .filter { it.folderPath !in hiddenFolderPaths }
        .filter { it.isIncludedBySmallAudioFilter(excludeSmallAudios) }
        .filter { selectedFolderPath == null || it.folderPath == selectedFolderPath }
        .sortedWith(sortMode.comparator())
        .toList()

    private fun List<Song>.visibleSongs(hiddenFolderPaths: Set<String>, excludeSmallAudios: Boolean): List<Song> = filter {
        it.folderPath !in hiddenFolderPaths && it.isIncludedBySmallAudioFilter(excludeSmallAudios)
    }

    private fun List<MusicFolder>.visibleFolders(hiddenFolderPaths: Set<String>): List<MusicFolder> = filterNot { it.path in hiddenFolderPaths }

    private fun PlayerUiState.playableSongs(): List<Song> = visibleSongs.ifEmpty { songs.visibleSongs(hiddenFolderPaths, excludeSmallAudios) }

    private fun List<Song>.toFolders(): List<MusicFolder> = groupBy { it.folderPath }
        .map { (path, songs) ->
            MusicFolder(
                path = path,
                name = songs.firstOrNull()?.folderName ?: path.substringAfterLast('/'),
                songCount = songs.size,
            )
        }
        .sortedWith(compareBy<MusicFolder> { it.name.lowercase() }.thenBy { it.path.lowercase() })

    private fun PlaybackMode.toRepeatSetting(): RepeatSetting = when (this) {
        PlaybackMode.RepeatCurrent -> RepeatSetting.One
        PlaybackMode.RepeatList -> RepeatSetting.All
        PlaybackMode.Ordered,
        PlaybackMode.Shuffle -> RepeatSetting.Off
    }

    private companion object {
        const val FAVORITES_PREFS_NAME = "favorite_settings"
        const val KEY_FAVORITE_SONG_IDS = "favorite_song_ids"
        const val PLAYLISTS_PREFS_NAME = "playlist_settings"
        const val KEY_PLAYLISTS = "playlists"
        const val RESTART_PREVIOUS_THRESHOLD_MS = 3_000L
        const val POSITION_UPDATE_INTERVAL_MS = 500L
        const val DJ_POSITION_UPDATE_FAST_MS = 200L
        const val MIN_DJ_MIX_SECONDS = 5
        const val MAX_DJ_MIX_SECONDS = 8
        const val MAX_PLAYLIST_NAME_LENGTH = 50
    }

    override fun onCleared() {
        val future = controllerFuture
        controllerFuture = null
        controller?.removeListener(listener)
        positionJob?.cancel()
        future?.let(MediaController::releaseFuture)
        controller = null
        super.onCleared()
    }
}
