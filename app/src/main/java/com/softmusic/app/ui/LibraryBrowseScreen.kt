@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.softmusic.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.softmusic.app.data.Song
import com.softmusic.app.player.PlaybackMode
import com.softmusic.app.player.PlaybackQueueSource
import com.softmusic.app.player.PlayerUiState
import com.softmusic.app.player.SortMode

@Composable
internal fun MusicLibraryScreen(
    modifier: Modifier = Modifier,
    uiState: PlayerUiState,
    selectedView: MusicLibraryView,
    selectedGroupTitle: String?,
    onSelectedViewChange: (MusicLibraryView) -> Unit,
    onSelectedGroupChange: (String?) -> Unit,
    onBack: () -> Unit,
    contentBottomPadding: Dp,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRefreshLibrary: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onPlayList: () -> Unit,
    onPlayQueuedSong: (Song, List<Song>, PlaybackQueueSource, String?) -> Unit,
    onPlayQueue: (List<Song>, PlaybackQueueSource, String?) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onPlayNext: (Song) -> Unit,
    onPlayAtEnd: (Song) -> Unit,
    onAddSongToPlaylist: (String, Long) -> Unit,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
    onSortModeChange: (SortMode) -> Unit,
    onFolderChange: (String?) -> Unit,
    onOpenHomeMenu: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val groups = remember(uiState.visibleSongs, selectedView, uiState.sortMode) {
        uiState.visibleSongs.toMusicGroups(selectedView, uiState.sortMode)
    }
    val selectedGroup = groups.firstOrNull { it.title == selectedGroupTitle }
    val playbackSource = selectedView.playbackQueueSource()
    val folderSourceKey = uiState.selectedFolderPath ?: "all"
    val playbackSourceKey = when (selectedView) {
        MusicLibraryView.Songs -> uiState.selectedFolderPath
        MusicLibraryView.Artists,
        MusicLibraryView.Albums -> selectedGroup?.let { "${it.key}:$folderSourceKey" }
    }
    val displayedSongs = if (selectedView == MusicLibraryView.Songs) {
        uiState.visibleSongs
    } else {
        selectedGroup?.songs.orEmpty()
    }
    val selectedArtist = if (selectedView == MusicLibraryView.Artists) selectedGroup else null
    val selectedAlbum = if (selectedView == MusicLibraryView.Albums) selectedGroup else null
    val isGroupDetail = selectedArtist != null || selectedAlbum != null
    val isSongsCollection = selectedView == MusicLibraryView.Songs
    val isCollectionDetail = isSongsCollection || isGroupDetail
    val selectedFolderName = uiState.selectedFolderPath?.let { path ->
        uiState.folders.firstOrNull { it.path == path }?.name
            ?: uiState.allFolders.firstOrNull { it.path == path }?.name
            ?: path.trimEnd('/').substringAfterLast('/').ifBlank { "Carpeta seleccionada" }
    }
    val showingSongs = selectedView == MusicLibraryView.Songs || selectedGroup != null
    val playableSongs = when {
        selectedView == MusicLibraryView.Songs -> uiState.visibleSongs
        selectedGroup != null -> selectedGroup.songs
        else -> emptyList()
    }
    val backgroundModifier = modifier.background(MaterialTheme.colorScheme.background)

    BackHandler(enabled = selectedGroupTitle != null && LocalNavigationBackEnabled.current) {
        onBack()
    }

    Box(
        modifier = backgroundModifier,
    ) {
        if (!hasAudioPermission) {
            PermissionPanel(onRequestPermission = onRequestPermission)
            return@Box
        }

        LibraryPullToRefresh(
            isRefreshing = uiState.isLoading,
            onRefresh = onRefreshLibrary,
        ) { listModifier ->
        LazyColumn(
            modifier = listModifier,
            contentPadding = if (isCollectionDetail) {
                PaddingValues(bottom = contentBottomPadding)
            } else {
                PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = contentBottomPadding)
            },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isSongsCollection) {
                item(key = "songs-collection-header") {
                    CollectionDetailHeader(
                        title = "Tus canciones",
                        songs = playableSongs,
                        subtitle = selectedFolderName?.let { "Carpeta · $it" } ?: "Todas las carpetas",
                        playbackMode = uiState.playbackMode,
                        onBack = onBack,
                        onPlay = {
                            onPlayQueue(playableSongs, PlaybackQueueSource.Songs, uiState.selectedFolderPath)
                        },
                        onPlaybackModeChange = onPlaybackModeChange,
                        playDescription = "Reproducir tus canciones",
                        leadingActions = {
                            FolderMenu(
                                folders = uiState.folders,
                                selectedFolderPath = uiState.selectedFolderPath,
                                onFolderChange = onFolderChange,
                                trigger = { openMenu ->
                                    IconButton(
                                        onClick = openMenu,
                                        modifier = Modifier.semantics {
                                            stateDescription = selectedFolderName ?: "Todas las carpetas"
                                        },
                                    ) {
                                        Icon(
                                            imageVector = MusicIcons.Folder,
                                            contentDescription = "Elegir carpeta",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                },
                            )
                            SortMenu(
                                sortMode = uiState.sortMode,
                                onSortModeChange = onSortModeChange,
                                trigger = { openMenu ->
                                    IconButton(
                                        onClick = openMenu,
                                        modifier = Modifier.semantics { stateDescription = uiState.sortMode.label },
                                    ) {
                                        Icon(
                                            imageVector = MusicIcons.Sort,
                                            contentDescription = "Ordenar canciones",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                },
                            )
                        },
                    )
                }
            } else if (selectedArtist != null) {
                item {
                    ArtistDetailHeader(
                        artist = selectedArtist,
                        playbackMode = uiState.playbackMode,
                        onBack = onBack,
                        onPlayArtist = {
                            onPlayQueue(
                                playableSongs,
                                playbackSource,
                                playbackSourceKey,
                            )
                        },
                        onPlaybackModeChange = onPlaybackModeChange,
                    )
                }
            } else if (selectedAlbum != null) {
                item {
                    AlbumDetailHeader(
                        album = selectedAlbum,
                        playbackMode = uiState.playbackMode,
                        onBack = onBack,
                        onPlayAlbum = {
                            onPlayQueue(
                                playableSongs,
                                playbackSource,
                                playbackSourceKey,
                            )
                        },
                        onPlaybackModeChange = onPlaybackModeChange,
                    )
                }
            }

            if (!isCollectionDetail) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(MusicIcons.Back, contentDescription = "Volver")
                        }
                        Text("Tu música", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
                stickyHeader {
                    LibraryTopRow(
                        songCount = playableSongs.size,
                        selectedView = selectedView,
                        isPlaying = uiState.isPlaying,
                        folders = uiState.folders,
                        selectedFolderPath = uiState.selectedFolderPath,
                        onOpenHomeMenu = onOpenHomeMenu,
                        onPlayList = {
                            onPlayQueue(
                                playableSongs,
                                playbackSource,
                                playbackSourceKey,
                            )
                        },
                        onViewChange = { view ->
                            onSelectedViewChange(view)
                        },
                        onFolderChange = onFolderChange,
                    )
                }
            }

            if (uiState.isLoading) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                            .then(if (isSongsCollection) Modifier.padding(horizontal = 20.dp) else Modifier),
                    )
                }
            }

            uiState.errorMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        modifier = if (isSongsCollection) Modifier.padding(horizontal = 20.dp) else Modifier,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (!uiState.isLoading && uiState.visibleSongs.isEmpty()) {
                item {
                    Box(modifier = if (isSongsCollection) Modifier.padding(horizontal = 20.dp) else Modifier) {
                        if (uiState.songs.isNotEmpty() && uiState.hiddenFolderPaths.isNotEmpty()) {
                            EmptyHiddenFolders(onOpenSettings = onOpenSettings)
                        } else {
                            EmptyLibrary(onRefreshLibrary = onRefreshLibrary)
                        }
                    }
                }
            }

            if (!uiState.isLoading && uiState.visibleSongs.isNotEmpty()) {
                if (showingSongs) {
                    items(
                        items = displayedSongs,
                        key = { it.id },
                        contentType = { "song" },
                    ) { song ->
                        Box(
                            modifier = Modifier
                                .animateItem()
                                .then(if (isCollectionDetail) Modifier.padding(horizontal = 20.dp) else Modifier),
                        ) {
                            SongRow(
                                song = song,
                                isCurrent = uiState.currentSong?.id == song.id,
                                isPlaying = uiState.currentSong?.id == song.id && uiState.isPlaying,
                                isFavorite = song.id in uiState.favoriteSongIds,
                                playlists = uiState.playlists,
                                songs = uiState.songs,
                                onClick = {
                                    onPlayQueuedSong(
                                        song,
                                        playableSongs,
                                        playbackSource,
                                        playbackSourceKey,
                                    )
                                },
                                onToggleFavorite = { onToggleFavorite(song.id) },
                                onPlayNext = { onPlayNext(song) },
                                onPlayAtEnd = { onPlayAtEnd(song) },
                                onAddToPlaylist = { playlistId -> onAddSongToPlaylist(playlistId, song.id) },
                            )
                        }
                    }
                } else {
                    items(
                        items = groups,
                        key = { "${selectedView.name}:${it.title}" },
                        contentType = { "group" },
                    ) { group ->
                        Box(modifier = Modifier.animateItem()) {
                            MusicGroupRow(
                                group = group,
                                onClick = { onSelectedGroupChange(group.title) },
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun ArtistDetailHeader(
    artist: MusicGroup,
    playbackMode: PlaybackMode,
    onBack: () -> Unit,
    onPlayArtist: () -> Unit,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
) {
    CollectionDetailHeader(
        title = artist.title,
        songs = artist.songs,
        subtitle = "Artista • ${artist.songs.map { it.album }.distinct().size.albumCountLabel()}",
        playbackMode = playbackMode,
        onBack = onBack,
        onPlay = onPlayArtist,
        onPlaybackModeChange = onPlaybackModeChange,
        artist = true,
        playDescription = "Reproducir artista",
    )
}

@Composable
private fun AlbumDetailHeader(
    album: MusicGroup,
    playbackMode: PlaybackMode,
    onBack: () -> Unit,
    onPlayAlbum: () -> Unit,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
) {
    CollectionDetailHeader(
        title = album.title,
        songs = album.songs,
        subtitle = "Álbum • ${album.subtitle}",
        playbackMode = playbackMode,
        onBack = onBack,
        onPlay = onPlayAlbum,
        onPlaybackModeChange = onPlaybackModeChange,
        playDescription = "Reproducir álbum",
    )
}

@Composable
internal fun MusicGroupRow(
    group: MusicGroup,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .background(Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            song = group.songs.first(),
            modifier = Modifier.size(56.dp),
            rounded = 4.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = group.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = group.songs.size.songCountLabel(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
