@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.softmusic.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.softmusic.app.data.Song
import com.softmusic.app.player.PlaybackQueueSource
import com.softmusic.app.player.PlayerUiState

@Composable
internal fun SearchLibraryScreen(
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
internal fun SearchField(
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
internal fun EmptySearchResults(searchQuery: String) {
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
