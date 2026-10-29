package com.softmusic.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.softmusic.app.data.MAX_PLAYLIST_NAME_LENGTH
import com.softmusic.app.data.MusicPlaylist
import com.softmusic.app.data.Song

@Composable
internal fun PlaylistRenameDialog(
    playlist: MusicPlaylist,
    otherPlaylistNames: List<String>,
    onRename: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(playlist.id) { mutableStateOf(playlist.name) }
    var submissionError by rememberSaveable(playlist.id) { mutableStateOf<String?>(null) }
    val validationError = playlistNameValidationError(name, otherPlaylistNames)
    val error = validationError ?: submissionError
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renombrar playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        submissionError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { Text(error ?: "${name.trim().length}/$MAX_PLAYLIST_NAME_LENGTH") },
                )
            }
        },
        confirmButton = {
            Button(
                enabled = validationError == null && name.trim() != playlist.name,
                onClick = {
                    if (onRename(name.trim())) onDismiss()
                    else submissionError = "No se pudo guardar. Revisa el nombre e inténtalo de nuevo."
                },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
internal fun PlaylistSongPickerDialog(
    playlist: MusicPlaylist,
    songs: List<Song>,
    onAddSongs: (List<Long>) -> Int,
    onAdded: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable(playlist.id) { mutableStateOf("") }
    var selectedIds by rememberSaveable(playlist.id) { mutableStateOf(emptyList<Long>()) }
    var submissionError by remember(playlist.id) { mutableStateOf<String?>(null) }
    val candidates = remember(songs, playlist.songIds) {
        val existingIds = playlist.songIds.toSet()
        songs.distinctBy { it.id }.filterNot { it.id in existingIds }
    }
    val availableIds = remember(candidates) { candidates.map { it.id } }
    val displayed = remember(candidates, query) { candidates.filterBySearch(query) }
    val selection = PlaylistSongSelection(selectedIds.toSet()).retainedFor(availableIds)
    val resultHeight = (LocalConfiguration.current.screenHeightDp * 0.35f).dp.coerceIn(56.dp, 320.dp)
    LaunchedEffect(availableIds) {
        selectedIds = PlaylistSongSelection(selectedIds.toSet()).retainedFor(availableIds).selectedIds.toList()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar canciones") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchField(query = query, onQueryChange = {
                    query = it
                    submissionError = null
                })
                Text("Seleccionadas: ${selection.selectedIds.size}", style = MaterialTheme.typography.labelLarge)
                if (displayed.isNotEmpty()) {
                    TextButton(onClick = {
                        selectedIds = (selectedIds.toSet() + displayed.map { it.id }).toList()
                    }) { Text("Seleccionar resultados") }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = resultHeight),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (displayed.isEmpty()) {
                        item {
                            Text(
                                if (candidates.isEmpty()) "No hay canciones nuevas para agregar."
                                else "No se encontraron canciones.",
                            )
                        }
                    }
                    items(displayed, key = { it.id }) { song ->
                        PlaylistSelectableSongRow(song, song.id in selection.selectedIds) {
                            selectedIds = PlaylistSongSelection(selectedIds.toSet()).toggle(song.id, availableIds).selectedIds.toList()
                            submissionError = null
                        }
                    }
                }
                submissionError?.let { message ->
                    Text(
                        message,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selection.selectedIds.isNotEmpty(),
                onClick = {
                    val count = onAddSongs(selection.orderedFor(availableIds))
                    if (count > 0) {
                        onAdded(count)
                        onDismiss()
                    } else {
                        submissionError = "No se agregaron canciones. La playlist o la biblioteca cambió."
                    }
                },
            ) { Text("Agregar (${selection.selectedIds.size})") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
internal fun PlaylistRemoveSongsDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "¿Quitar canción?" else "¿Quitar $count canciones?") },
        text = { Text("Se quitarán de esta playlist. Los archivos de música permanecerán en tu dispositivo.") },
        confirmButton = { Button(onClick = onConfirm) { Text("Quitar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
