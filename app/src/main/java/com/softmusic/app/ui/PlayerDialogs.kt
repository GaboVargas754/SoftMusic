package com.softmusic.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.softmusic.app.data.MAX_PLAYLIST_NAME_LENGTH
import com.softmusic.app.data.MusicPlaylist
import com.softmusic.app.data.Song
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

@Composable
internal fun SleepTimerDialog(
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
internal fun SongDetailsDialog(
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

private fun formatDateAdded(dateAddedSeconds: Long): String {
    if (dateAddedSeconds <= 0L) return "No disponible"
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
        .format(Date(dateAddedSeconds * 1_000L))
}

