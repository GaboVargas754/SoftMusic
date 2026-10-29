@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.softmusic.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.softmusic.app.data.MusicPlaylist
import com.softmusic.app.data.Song
import com.softmusic.app.player.PlaylistShuffleMode
import com.softmusic.app.player.PlaybackQueueSource
import com.softmusic.app.player.PlayerUiState
import kotlinx.coroutines.delay

@Composable
internal fun PlaylistDetailScreen(
    playlist: MusicPlaylist,
    playlistSongs: List<Song>,
    uiState: PlayerUiState,
    contentBottomPadding: Dp,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onSetCover: (Long?) -> Unit,
    onRename: (String) -> Boolean,
    onReorder: (List<Long>, List<Long>) -> Boolean,
    onAddSongs: (List<Long>) -> Int,
    onRemoveSongs: (Set<Long>) -> Int,
    onRemoveSong: (Long) -> Unit,
    onPlayQueuedSong: (Song, List<Song>, PlaybackQueueSource, String?) -> Unit,
    onPlayQueue: (List<Song>, PlaybackQueueSource, String?) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onPlayNext: (Song) -> Unit,
    onPlayAtEnd: (Song) -> Unit,
    onAddSongToPlaylist: (String, Long) -> Unit,
    onCyclePlaylistShuffleMode: (String) -> Unit,
    onOpenMusic: () -> Unit,
) {
    var selectionMode by rememberSaveable(playlist.id) { mutableStateOf(false) }
    var reorderMode by rememberSaveable(playlist.id) { mutableStateOf(false) }
    var selectedIds by rememberSaveable(playlist.id) { mutableStateOf(emptyList<Long>()) }
    var pendingRemovalIds by rememberSaveable(playlist.id) { mutableStateOf(emptyList<Long>()) }
    var showRename by rememberSaveable(playlist.id) { mutableStateOf(false) }
    var showAddSongs by rememberSaveable(playlist.id) { mutableStateOf(false) }
    var feedback by remember(playlist.id) { mutableStateOf<String?>(null) }
    val visibleIds = remember(playlistSongs) { playlistSongs.map { it.id } }
    val availableSongs = remember(uiState.songs, uiState.hiddenFolderPaths, uiState.excludeSmallAudios) {
        uiState.visibleLibrarySongs().distinctBy { it.id }
    }
    val selected = PlaylistSongSelection(selectedIds.toSet()).retainedFor(visibleIds)
    val pendingRemoval = pendingRemovalIds.toSet().intersect(visibleIds.toSet())
    val songsById = remember(playlistSongs) { playlistSongs.associateBy { it.id } }
    val dragState = remember(playlist.id, playlist.songIds, visibleIds) {
        PlaylistDragState(playlist.songIds.toList(), visibleIds)
    }
    val orderedSongs = dragState.orderedIds.mapNotNull(songsById::get)
    val listState = rememberLazyListState()
    val placementSpec = softMusicMotionSpec<IntOffset>(160)
    val latestFullIds by rememberUpdatedState(playlist.songIds)
    val latestVisibleIds by rememberUpdatedState(visibleIds)
    val latestReorder by rememberUpdatedState(onReorder)
    val finishDrag by rememberUpdatedState(newValue = {
        val commit = dragState.finish(latestFullIds, latestVisibleIds)
        if (commit != null) {
            if (latestReorder(commit.expectedFullIds, commit.orderedVisibleIds)) {
                feedback = "Orden guardado"
            } else {
                dragState.cancel()
                feedback = "La playlist cambió. Vuelve a ordenar las canciones."
            }
        }
    })

    fun finishEditing() {
        selectionMode = false
        reorderMode = false
        selectedIds = emptyList()
        dragState.cancel()
    }
    fun handleBack() {
        if (selectionMode || reorderMode) finishEditing() else onBack()
    }
    fun moveSong(songId: Long, offset: Int) {
        if (dragState.draggedSongId != null) return
        val session = PlaylistReorderSession(latestFullIds.toList(), latestVisibleIds.toList(), songId)
        if (!session.moveTo(latestVisibleIds.indexOf(songId) + offset)) return
        val commit = session.drop(latestFullIds, latestVisibleIds) ?: return
        feedback = if (latestReorder(commit.expectedFullIds, commit.orderedVisibleIds)) "Orden guardado"
        else "La playlist cambió. Vuelve a ordenar las canciones."
    }

    BackHandler(enabled = (selectionMode || reorderMode) && LocalNavigationBackEnabled.current) {
        finishEditing()
    }
    LaunchedEffect(visibleIds) {
        selectedIds = PlaylistSongSelection(selectedIds.toSet()).retainedFor(visibleIds).selectedIds.toList()
        if (pendingRemovalIds.isNotEmpty() && pendingRemoval.isEmpty()) pendingRemovalIds = emptyList()
    }
    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(2_500L)
            feedback = null
        }
    }
    PlaylistDragEffects(dragState, listState)

    if (showRename) {
        PlaylistRenameDialog(
            playlist = playlist,
            otherPlaylistNames = uiState.playlists.filterNot { it.id == playlist.id }.map { it.name },
            onRename = { name -> onRename(name).also { if (it) feedback = "Nombre actualizado" } },
            onDismiss = { showRename = false },
        )
    }
    if (showAddSongs) {
        PlaylistSongPickerDialog(
            playlist = playlist,
            songs = availableSongs,
            onAddSongs = onAddSongs,
            onAdded = { count ->
                feedback = if (count == 1) "Se agregó 1 canción" else "Se agregaron $count canciones"
            },
            onDismiss = { showAddSongs = false },
        )
    }
    if (pendingRemoval.isNotEmpty()) {
        PlaylistRemoveSongsDialog(
            count = pendingRemoval.size,
            onConfirm = {
                val count = onRemoveSongs(pendingRemoval)
                pendingRemovalIds = emptyList()
                selectedIds = emptyList()
                selectionMode = false
                feedback = when (count) {
                    0 -> "No había canciones para quitar."
                    1 -> "Se quitó 1 canción de la playlist"
                    else -> "Se quitaron $count canciones de la playlist"
                }
            },
            onDismiss = { pendingRemovalIds = emptyList() },
        )
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = contentBottomPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "playlist-detail-header") {
            PlaylistDetailHeader(
                playlist = playlist,
                songs = playlistSongs,
                shuffleMode = uiState.playlistShuffleModes[playlist.id] ?: PlaylistShuffleMode.Off,
                onBack = ::handleBack,
                onPlayList = { onPlayQueue(playlistSongs, PlaybackQueueSource.Playlist, playlist.id) },
                onSetCover = onSetCover,
                onDelete = onDelete,
                onCycleShuffleMode = { onCyclePlaylistShuffleMode(playlist.id) },
                selectionMode = selectionMode,
                reorderMode = reorderMode,
                selectedCount = selected.selectedIds.size,
                onRename = { showRename = true },
                onAdd = { showAddSongs = true },
                onSelect = { selectionMode = true },
                onReorder = { reorderMode = true },
                onSelectAll = {
                    val currentSelection = PlaylistSongSelection(selectedIds.toSet()).retainedFor(visibleIds)
                    selectedIds = if (currentSelection.selectedIds.size == visibleIds.size) emptyList() else visibleIds
                },
                onRemoveSelected = { pendingRemovalIds = selected.orderedFor(visibleIds) },
                onFinishEditing = ::finishEditing,
            )
        }
        if (reorderMode) {
            item(key = "playlist-reorder-help") {
                Text(
                    text = "Arrastra para ordenar. Cada movimiento se guarda al soltar.",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        feedback?.let { message ->
            item(key = "playlist-edit-feedback") {
                Text(
                    message,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (playlistSongs.isEmpty()) {
            item(key = "playlist-empty") {
                Box(Modifier.padding(horizontal = 20.dp)) { EmptyPlaylist(onOpenMusic = onOpenMusic) }
            }
        }
        itemsIndexed(items = orderedSongs, key = { _, song -> song.id }, contentType = { _, _ -> "playlistSong" }) { index, song ->
            if (reorderMode) {
                val dragging = dragState.draggedSongId == song.id
                PlaylistReorderSongRow(
                    song = song,
                    index = index,
                    count = orderedSongs.size,
                    dragging = dragging,
                    modifier = Modifier
                        .animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = if (dragging) null else placementSpec)
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            if (dragging) {
                                val offset = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == song.id }?.offset
                                translationY = if (offset == null) 0f else dragState.draggedTop - offset
                            }
                        },
                    dragModifier = Modifier.pointerInput(playlist.id, song.id, dragState) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            down.consume()
                            if (dragState.start(song.id, listState)) {
                                try {
                                    val completed = drag(down.id) { change ->
                                        val distance = change.positionChange().y
                                        change.consume()
                                        dragState.dragBy(distance, listState)
                                    }
                                    if (completed) finishDrag() else dragState.cancel()
                                } finally {
                                    if (dragState.draggedSongId == song.id) dragState.cancel()
                                }
                            }
                        }
                    },
                    onMoveUp = if (index > 0) ({ moveSong(song.id, -1) }) else null,
                    onMoveDown = if (index < orderedSongs.lastIndex) ({ moveSong(song.id, 1) }) else null,
                )
            } else {
                Column(Modifier.animateItem().padding(horizontal = 20.dp)) {
                    if (selectionMode) {
                        PlaylistSelectableSongRow(song, song.id in selected.selectedIds) {
                            selectedIds = PlaylistSongSelection(selectedIds.toSet()).toggle(song.id, visibleIds).selectedIds.toList()
                        }
                    } else {
                        PlaylistSongRow(
                            song = song,
                            isCurrent = uiState.currentSong?.id == song.id,
                            isPlaying = uiState.currentSong?.id == song.id && uiState.isPlaying,
                            isFavorite = song.id in uiState.favoriteSongIds,
                            playlists = uiState.playlists,
                            songs = uiState.songs,
                            onClick = { onPlayQueuedSong(song, playlistSongs, PlaybackQueueSource.Playlist, playlist.id) },
                            onToggleFavorite = { onToggleFavorite(song.id) },
                            onPlayNext = { onPlayNext(song) },
                            onPlayAtEnd = { onPlayAtEnd(song) },
                            onAddToPlaylist = { onAddSongToPlaylist(it, song.id) },
                            onRemove = { onRemoveSong(song.id) },
                        )
                    }
                }
            }
        }
    }
}
