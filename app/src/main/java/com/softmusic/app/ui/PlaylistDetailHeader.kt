package com.softmusic.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.softmusic.app.data.MusicPlaylist
import com.softmusic.app.data.Song
import com.softmusic.app.player.PlaylistShuffleMode

@Composable
internal fun PlaylistDetailHeader(
    playlist: MusicPlaylist,
    songs: List<Song>,
    shuffleMode: PlaylistShuffleMode,
    onBack: () -> Unit,
    onPlayList: () -> Unit,
    onSetCover: (Long?) -> Unit,
    onDelete: () -> Unit,
    onCycleShuffleMode: () -> Unit,
    selectionMode: Boolean,
    reorderMode: Boolean,
    selectedCount: Int,
    onRename: () -> Unit,
    onAdd: () -> Unit,
    onSelect: () -> Unit,
    onReorder: () -> Unit,
    onSelectAll: () -> Unit,
    onRemoveSelected: () -> Unit,
    onFinishEditing: () -> Unit,
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
        playbackMode = shuffleMode.playbackMode,
        onBack = onBack,
        onPlay = onPlayList,
        onPlaybackModeChange = { onCycleShuffleMode() },
        coverSongId = playlist.coverSongId,
        playDescription = "Reproducir playlist",
        actionRow = {
            PlaylistEditingToolbar(
                selectionMode = selectionMode,
                reorderMode = reorderMode,
                selectedCount = selectedCount,
                visibleCount = songs.size,
                shuffleMode = shuffleMode,
                onRename = onRename,
                onAdd = onAdd,
                onChangeCover = { showCoverPicker = true },
                onDelete = { showDeleteConfirmation = true },
                onSelect = onSelect,
                onReorder = onReorder,
                onSelectAll = onSelectAll,
                onRemoveSelected = onRemoveSelected,
                onFinish = onFinishEditing,
                onPlay = onPlayList,
                onCycleShuffleMode = onCycleShuffleMode,
            )
        },
    )
}
