package com.softmusic.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.softmusic.app.data.Song
import com.softmusic.app.player.PlayerUiState
import com.softmusic.app.player.orderedSongs

internal enum class ListeningHistoryView(val key: String) {
    Recent("recent"), MostPlayed("most_played");

    val title: String get() = if (this == Recent) "Escuchadas recientemente" else "Más escuchadas"
}

@Composable
internal fun ListeningHistoryScreen(
    uiState: PlayerUiState,
    view: ListeningHistoryView,
    onViewChange: (ListeningHistoryView) -> Unit,
    onBack: () -> Unit,
    contentBottomPadding: Dp,
    onPlayHistorySong: (Song, List<Song>, ListeningHistoryView) -> Unit,
    onPlayHistoryQueue: (List<Song>, ListeningHistoryView) -> Unit,
    modifier: Modifier = Modifier,
    onToggleFavorite: (Long) -> Unit = {},
    onPlayNext: (Song) -> Unit = {},
    onPlayAtEnd: (Song) -> Unit = {},
    onAddSongToPlaylist: (String, Long) -> Unit = { _, _ -> },
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val availableSongs = remember(uiState.songs, uiState.hiddenFolderPaths, uiState.excludeSmallAudios) {
        uiState.visibleLibrarySongs()
    }
    val orderedSongs = remember(availableSongs, uiState.listeningHistory.entries, view) {
        uiState.listeningHistory.orderedSongs(availableSongs, view.key)
    }
    val songs = remember(orderedSongs, searchQuery) { orderedSongs.filterBySearch(searchQuery) }
    val entriesById = remember(uiState.listeningHistory.entries) { uiState.listeningHistory.entries.associateBy { it.songId } }

    LazyColumn(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = contentBottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "history-header") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(MusicIcons.Back, contentDescription = "Volver") }
                    Text("Historial", style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                }
                Text("Guardado solo en este dispositivo.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ListeningHistoryView.entries.forEach { option ->
                        FilterChip(selected = option == view, onClick = { onViewChange(option) },
                            label = { Text(if (option == ListeningHistoryView.Recent) "Recientes" else "Más escuchadas") })
                    }
                }
                SearchField(query = searchQuery, onQueryChange = { searchQuery = it })
                if (songs.isNotEmpty()) {
                    Button(onClick = { onPlayHistoryQueue(songs, view) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(MusicIcons.Play, contentDescription = null)
                        Text("Reproducir ${songs.size.songCountLabel()}", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        if (uiState.listeningHistory.isLoading) {
            item(key = "history-loading") { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        }
        uiState.listeningHistory.errorMessage?.let { message ->
            item(key = "history-error") { Text(message, color = MaterialTheme.colorScheme.error) }
        }
        if (!uiState.listeningHistory.enabled) {
            item(key = "history-disabled") {
                Text("El historial está pausado. Puedes activarlo en Ajustes; las escuchas anteriores se conservan.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!uiState.listeningHistory.isLoading && songs.isEmpty()) {
            item(key = "history-empty") {
                if (searchQuery.isNotBlank()) EmptySearchResults(searchQuery)
                else Text(
                    if (uiState.listeningHistory.entries.isEmpty())
                        "Todavía no hay escuchas. Escucha al menos 30 segundos o la mitad de una canción para añadirla aquí."
                    else "No hay canciones del historial disponibles con tus filtros de biblioteca.",
                    modifier = Modifier.padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(songs, key = { it.id }, contentType = { "history-song" }) { song ->
            Column {
                SongRow(
                    song = song,
                    isCurrent = uiState.currentSong?.id == song.id,
                    isPlaying = uiState.currentSong?.id == song.id && uiState.isPlaying,
                    isFavorite = song.id in uiState.favoriteSongIds,
                    playlists = uiState.playlists,
                    songs = uiState.songs,
                    onClick = { onPlayHistorySong(song, songs, view) },
                    onToggleFavorite = { onToggleFavorite(song.id) },
                    onPlayNext = { onPlayNext(song) },
                    onPlayAtEnd = { onPlayAtEnd(song) },
                    onAddToPlaylist = { onAddSongToPlaylist(it, song.id) },
                )
                if (view == ListeningHistoryView.MostPlayed) {
                    val count = entriesById[song.id]?.playCount ?: 0L
                    Text(if (count == 1L) "1 escucha" else "$count escuchas", modifier = Modifier.padding(start = 72.dp),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
