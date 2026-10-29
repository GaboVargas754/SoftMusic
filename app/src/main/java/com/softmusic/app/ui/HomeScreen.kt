package com.softmusic.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softmusic.app.data.MusicPlaylist
import com.softmusic.app.data.Song
import com.softmusic.app.player.PlaybackMode
import com.softmusic.app.player.PlaybackProgressState
import com.softmusic.app.player.PlayerUiState
import com.softmusic.app.player.SortMode
import com.softmusic.app.player.orderedSongs
import kotlinx.coroutines.flow.StateFlow

private data class HomePlaylist(
    val playlist: MusicPlaylist,
    val songs: List<Song>,
    val coverSongId: Long?,
)

private data class HomeAlbum(
    val title: String,
    val songs: List<Song>,
    val coverSongId: Long?,
    val lastAddedSeconds: Long,
)

@Composable
internal fun HomeScreen(
    uiState: PlayerUiState,
    playbackProgressState: StateFlow<PlaybackProgressState>,
    progressUpdatesEnabled: Boolean,
    modifier: Modifier = Modifier,
    contentBottomPadding: Dp = 144.dp,
    onOpenMenu: () -> Unit,
    onOpenSongs: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenPlayer: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
    onDismissSession: () -> Unit,
    onOpenLibrary: () -> Unit,
    onRefreshLibrary: () -> Unit,
    onCreatePlaylist: (String) -> Boolean,
    onOpenHistory: (ListeningHistoryView) -> Unit,
    onPlayHistorySong: (Song, List<Song>, ListeningHistoryView) -> Unit,
) {
    var showCreatePlaylist by rememberSaveable { mutableStateOf(false) }
    val librarySongs = remember(uiState.songs, uiState.hiddenFolderPaths, uiState.excludeSmallAudios) {
        uiState.visibleLibrarySongs()
    }
    val favorites = remember(librarySongs, uiState.favoriteSongIds) {
        librarySongs.filter { it.id in uiState.favoriteSongIds }
    }
    val recentHistory = remember(librarySongs, uiState.listeningHistory.entries) {
        uiState.listeningHistory.orderedSongs(librarySongs, ListeningHistoryView.Recent.key)
    }
    val mostPlayedHistory = remember(librarySongs, uiState.listeningHistory.entries) {
        uiState.listeningHistory.orderedSongs(librarySongs, ListeningHistoryView.MostPlayed.key)
    }
    val recentAlbums = remember(librarySongs) {
        librarySongs.toMusicGroups(MusicLibraryView.Albums, SortMode.Recent)
            .map { album ->
                HomeAlbum(
                    title = album.title,
                    songs = album.songs,
                    coverSongId = album.songs.firstOrNull { it.artworkUri != null }?.id,
                    lastAddedSeconds = album.songs.maxOfOrNull { it.dateAddedSeconds } ?: 0L,
                )
            }
            .sortedWith(
                compareByDescending<HomeAlbum> { it.lastAddedSeconds }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.title },
            )
            .take(12)
    }
    val playlists = remember(uiState.playlists, librarySongs) {
        if (uiState.playlists.isEmpty()) {
            emptyList()
        } else {
            val songsById = librarySongs.associateBy { it.id }
            uiState.playlists.take(12).map { playlist ->
                val songs = playlist.songIds.mapNotNull(songsById::get)
                HomePlaylist(
                    playlist = playlist,
                    songs = songs,
                    coverSongId = playlist.coverSongId?.takeIf { coverId -> songs.any { it.id == coverId } },
                )
            }
        }
    }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        LibraryPullToRefresh(
            isRefreshing = uiState.isLoading,
            onRefresh = onRefreshLibrary,
        ) { listModifier ->
            LazyColumn(
                modifier = listModifier,
                contentPadding = PaddingValues(top = 12.dp, bottom = contentBottomPadding),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                item(key = "home-header", contentType = "header") {
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        HomeBrandHeader(
                            shuffleActive = uiState.playbackMode == PlaybackMode.Shuffle,
                            onToggleShuffle = {
                                onPlaybackModeChange(
                                    if (uiState.playbackMode == PlaybackMode.Shuffle) PlaybackMode.Ordered
                                    else PlaybackMode.Shuffle,
                                )
                            },
                            onOpenMenu = onOpenMenu,
                        )
                        HomePrimaryCards(
                            favoriteCount = favorites.size,
                            songCount = uiState.visibleSongs.size,
                            onOpenFavorites = onOpenFavorites,
                            onOpenSongs = onOpenSongs,
                        )
                    }
                }
                if (librarySongs.isEmpty()) {
                    item(key = "home-empty-library", contentType = "empty-library") {
                        HomeEmptyLibrary(
                            isLoading = uiState.isLoading,
                            onRefreshLibrary = onRefreshLibrary,
                            onOpenLibrary = onOpenLibrary,
                        )
                    }
                }
                uiState.currentSong?.let { song ->
                    item(key = "home-current-song", contentType = "current-song") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HomeSectionTitle(title = "Seguir escuchando")
                            HomeListeningCard(
                                song = song,
                                isPlaying = uiState.isPlaying,
                                restoredSessionAvailable = uiState.restoredSessionAvailable,
                                playbackProgressState = playbackProgressState,
                                progressUpdatesEnabled = progressUpdatesEnabled && !showCreatePlaylist,
                                onOpenPlayer = onOpenPlayer,
                                onPrevious = onPrevious,
                                onTogglePlayPause = onTogglePlayPause,
                                onNext = onNext,
                                onDismissSession = onDismissSession,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
                if (recentAlbums.isNotEmpty()) {
                    item(key = "home-recent-albums", contentType = "carousel") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HomeSectionTitle(
                                title = "Accesos rápidos",
                                actionLabel = "Ver álbumes",
                                onAction = onOpenAlbums,
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(recentAlbums, key = { it.title }) { album ->
                                    HomeCollectionCard(
                                        title = album.title,
                                        songs = album.songs,
                                        coverSongId = album.coverSongId,
                                        cardWidth = 88.dp,
                                        compact = true,
                                        onClick = { onOpenAlbum(album.title) },
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "home-playlists", contentType = "playlists") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeSectionTitle(
                            title = "Tus playlists",
                            actionLabel = if (playlists.isNotEmpty()) "Ver todas" else null,
                            onAction = onOpenLibrary.takeIf { playlists.isNotEmpty() },
                        )
                        if (playlists.isEmpty()) {
                            HomePlaylistInvitation(
                                onCreatePlaylist = { showCreatePlaylist = true },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        } else {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(playlists, key = { it.playlist.id }) { item ->
                                    HomeCollectionCard(
                                        title = item.playlist.name,
                                        songs = item.songs,
                                        coverSongId = item.coverSongId,
                                        cardWidth = 144.dp,
                                        onClick = { onOpenPlaylist(item.playlist.id) },
                                    )
                                }
                            }
                        }
                    }
                }
                if (recentHistory.isNotEmpty()) {
                    item(key = "home-recent-history", contentType = "history") {
                        ListeningHistoryHomeSection(recentHistory, ListeningHistoryView.Recent, onOpenHistory, onPlayHistorySong)
                    }
                    item(key = "home-most-played-history", contentType = "history") {
                        ListeningHistoryHomeSection(mostPlayedHistory, ListeningHistoryView.MostPlayed, onOpenHistory, onPlayHistorySong)
                    }
                } else if (librarySongs.isNotEmpty() && !uiState.listeningHistory.isLoading) {
                    item(key = "home-history-invitation", contentType = "history") {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text("Tu historial", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(if (uiState.listeningHistory.enabled) "Aquí aparecerá la música que escuches en este dispositivo."
                                else "El historial está pausado. Puedes activarlo en Ajustes.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { onOpenHistory(ListeningHistoryView.Recent) }) { Text("Ver historial") }
                        }
                    }
                }
            }
        }
    }
    if (showCreatePlaylist) {
        CreatePlaylistDialog(
            existingPlaylistNames = uiState.playlists.map { it.name },
            onCreatePlaylist = onCreatePlaylist,
            onDismiss = { showCreatePlaylist = false },
        )
    }
}

@Composable
private fun HomePrimaryCards(
    favoriteCount: Int,
    songCount: Int,
    onOpenFavorites: () -> Unit,
    onOpenSongs: () -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    // Measure outside the equal-height row: subcomposed boxes have no intrinsic size.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        val cardWidth = (maxWidth - 10.dp) / 2
        val compact = cardWidth < 160.dp || fontScale > 1.25f
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HomePrimaryCard(
                title = "Favoritos",
                songCount = favoriteCount,
                icon = MusicIcons.Favorites,
                compact = compact,
                onClick = onOpenFavorites,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            HomePrimaryCard(
                title = "Tus canciones",
                songCount = songCount,
                icon = MusicIcons.TracksSelected,
                compact = compact,
                onClick = onOpenSongs,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun HomePrimaryCard(
    title: String,
    songCount: Int,
    icon: ImageVector,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val highPerformanceMode = LocalHighPerformanceMode.current
    val primary = colors.primary
    val shape = RoundedCornerShape(14.dp)
    val decoration = if (highPerformanceMode) {
        Modifier
    } else {
        Modifier
            .border(1.dp, colors.onSurface.copy(alpha = 0.045f), shape)
            .drawWithCache {
                val glow = Brush.linearGradient(
                    colors = listOf(primary.copy(alpha = 0.065f), Color.Transparent),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, 0f),
                )
                onDrawBehind { drawRect(glow) }
            }
    }
    val cardModifier = modifier
        .heightIn(min = 88.dp)
        .clip(shape)
        .background(colors.surface)
        .then(decoration)
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 14.dp)
    val iconContent: @Composable () -> Unit = {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = primary,
        )
    }
    val chevronContent: @Composable () -> Unit = {
        Icon(
            imageVector = MusicIcons.ChevronDown,
            contentDescription = null,
            modifier = Modifier.size(10.dp).rotate(-90f),
            tint = colors.onSurfaceVariant,
        )
    }
    if (compact) {
        Column(modifier = cardModifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                iconContent()
                chevronContent()
            }
            HomePrimaryTexts(title, songCount, modifier = Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = cardModifier,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            iconContent()
            HomePrimaryTexts(title, songCount, modifier = Modifier.weight(1f))
            chevronContent()
        }
    }
}

@Composable
private fun HomePrimaryTexts(title: String, songCount: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = songCount.songCountLabel(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeSectionTitle(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 350.dp || fontScale > 1.25f) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                HomeSectionHeading(title, modifier = Modifier.fillMaxWidth())
                if (onAction != null && actionLabel != null) {
                    HomeSectionAction(actionLabel, onAction, modifier = Modifier.align(Alignment.End))
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HomeSectionHeading(title, modifier = Modifier.weight(1f))
                if (onAction != null && actionLabel != null) {
                    HomeSectionAction(actionLabel, onAction)
                }
            }
        }
    }
}

@Composable
private fun HomeSectionHeading(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        modifier = modifier.semantics { heading() },
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 23.sp,
        lineHeight = 29.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun HomeSectionAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Icon(
            imageVector = MusicIcons.ChevronDown,
            contentDescription = null,
            modifier = Modifier.padding(start = 4.dp).size(14.dp).rotate(-90f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HomeCollectionCard(
    title: String,
    songs: List<Song>,
    coverSongId: Long?,
    cardWidth: Dp,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val highPerformanceMode = LocalHighPerformanceMode.current
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .width(cardWidth)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        CollectionArtwork(
            title = title,
            songs = songs,
            coverSongId = coverSongId,
            cornerRadius = 12.dp,
            modifier = Modifier.size(cardWidth)
                .then(
                    if (highPerformanceMode) Modifier else {
                        Modifier.border(1.dp, colors.onSurface.copy(alpha = 0.1f), shape)
                    },
                )
                .clearAndSetSemantics { },
        )
        Text(
            text = title,
            modifier = Modifier.padding(top = 2.dp),
            color = colors.onSurface,
            fontSize = if (compact) 13.sp else 15.sp,
            lineHeight = if (compact) 17.sp else 19.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = songs.size.songCountLabel(),
            color = colors.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeEmptyLibrary(
    isLoading: Boolean,
    onRefreshLibrary: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = MusicIcons.Library,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = if (isLoading) "Buscando tu música…" else "Tu música empieza aquí",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Las canciones de tu dispositivo aparecerán en Inicio.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRefreshLibrary, enabled = !isLoading) {
            Text(if (isLoading) "Actualizando…" else "Actualizar biblioteca")
        }
        TextButton(onClick = onOpenLibrary) {
            Text("Abrir Biblioteca")
        }
    }
}
