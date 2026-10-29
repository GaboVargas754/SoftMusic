@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.softmusic.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.softmusic.app.data.MusicPlaylist
import com.softmusic.app.data.Song
import com.softmusic.app.player.PlaybackQueueSource
import com.softmusic.app.player.PlayerUiState
import kotlin.math.max

@Composable
internal fun PlaylistsLibraryScreen(
    modifier: Modifier = Modifier,
    uiState: PlayerUiState,
    contentBottomPadding: Dp,
    selectedPlaylistId: String?,
    onSelectedPlaylistChange: (String?) -> Unit,
    onBackFromPlaylist: () -> Unit,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
    onCreatePlaylist: (String) -> Boolean,
    onDeletePlaylist: (String) -> Unit,
    onSetPlaylistCover: (String, Long?) -> Unit,
    onRenamePlaylist: (String, String) -> Boolean,
    onReorderPlaylist: (String, List<Long>, List<Long>) -> Boolean,
    onAddSongsToPlaylist: (String, List<Long>) -> Int,
    onRemoveSongsFromPlaylist: (String, Set<Long>) -> Int,
    onRemoveSongFromPlaylist: (String, Long) -> Unit,
    onPlayQueuedSong: (Song, List<Song>, PlaybackQueueSource, String?) -> Unit,
    onPlayQueue: (List<Song>, PlaybackQueueSource, String?) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onPlayNext: (Song) -> Unit,
    onPlayAtEnd: (Song) -> Unit,
    onAddSongToPlaylist: (String, Long) -> Unit,
    onCyclePlaylistShuffleMode: (String) -> Unit,
    onOpenMusic: () -> Unit,
) {
    var searchQuery by rememberSaveable(selectedPlaylistId) { mutableStateOf("") }
    val selectedPlaylist = uiState.playlists.firstOrNull { it.id == selectedPlaylistId }
    val playlistSongs = remember(selectedPlaylist, uiState.songs, uiState.hiddenFolderPaths, uiState.excludeSmallAudios) {
        selectedPlaylist?.songsFrom(uiState.songs, uiState.hiddenFolderPaths, uiState.excludeSmallAudios).orEmpty()
    }
    val displayedPlaylists = remember(uiState.playlists, searchQuery, selectedPlaylist) {
        if (selectedPlaylist == null) {
            uiState.playlists.filterByPlaylistSearch(searchQuery)
        } else {
            emptyList()
        }
    }
    val hasSearchQuery = searchQuery.isNotBlank()
    val backgroundModifier = modifier.background(MaterialTheme.colorScheme.background)

    BackHandler(enabled = selectedPlaylistId != null && LocalNavigationBackEnabled.current) {
        onBackFromPlaylist()
    }

    Box(modifier = backgroundModifier) {
        if (!hasAudioPermission) {
            PermissionPanel(onRequestPermission = onRequestPermission)
            return@Box
        }

        if (selectedPlaylist != null) {
            PlaylistDetailScreen(
                playlist = selectedPlaylist,
                playlistSongs = playlistSongs.distinctBy { it.id },
                uiState = uiState,
                contentBottomPadding = contentBottomPadding,
                onBack = onBackFromPlaylist,
                onDelete = {
                    onDeletePlaylist(selectedPlaylist.id)
                    onBackFromPlaylist()
                },
                onSetCover = { onSetPlaylistCover(selectedPlaylist.id, it) },
                onRename = { onRenamePlaylist(selectedPlaylist.id, it) },
                onReorder = { expected, ordered -> onReorderPlaylist(selectedPlaylist.id, expected, ordered) },
                onAddSongs = { onAddSongsToPlaylist(selectedPlaylist.id, it) },
                onRemoveSongs = { onRemoveSongsFromPlaylist(selectedPlaylist.id, it) },
                onRemoveSong = { onRemoveSongFromPlaylist(selectedPlaylist.id, it) },
                onPlayQueuedSong = onPlayQueuedSong,
                onPlayQueue = onPlayQueue,
                onToggleFavorite = onToggleFavorite,
                onPlayNext = onPlayNext,
                onPlayAtEnd = onPlayAtEnd,
                onAddSongToPlaylist = onAddSongToPlaylist,
                onCyclePlaylistShuffleMode = onCyclePlaylistShuffleMode,
                onOpenMusic = onOpenMusic,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = contentBottomPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    PlaylistsHeader(
                        title = "Playlist",
                        subtitle = uiState.playlists.size.playlistCountLabel(),
                    )
                }
                if (uiState.playlists.isNotEmpty()) {
                    item {
                        SearchField(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                        )
                    }
                }
                item {
                    CreatePlaylistCard(
                        existingPlaylistNames = uiState.playlists.map { it.name },
                        onCreatePlaylist = onCreatePlaylist,
                    )
                }
                if (uiState.playlists.isEmpty()) {
                    item {
                        EmptyPlaylists()
                    }
                } else if (displayedPlaylists.isEmpty() && hasSearchQuery) {
                    item {
                        EmptySearchResults(searchQuery = searchQuery)
                    }
                }
                items(
                    items = displayedPlaylists,
                    key = { it.id },
                    contentType = { "playlist" },
                ) { playlist ->
                    Box(modifier = Modifier.animateItem()) {
                        PlaylistRow(
                            playlist = playlist,
                            songs = playlist.songsFrom(uiState.songs, uiState.hiddenFolderPaths, uiState.excludeSmallAudios),
                            onClick = { onSelectedPlaylistChange(playlist.id) },
                            onDelete = { onDeletePlaylist(playlist.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistsHeader(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = MusicIcons.Back,
                    contentDescription = "Volver",
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ModuleTitle(
                icon = MusicIcons.Playlist,
                title = title,
                modifier = Modifier.fillMaxWidth(),
                compact = true,
            )
            Text(
                text = subtitle,
                modifier = Modifier.padding(start = 38.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun PlaylistMosaicBackground(
    modifier: Modifier = Modifier,
    bottomColor: Color,
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val scaleX = width / 1200f
        val scaleY = height / 1200f

        drawRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to Color(0xFFFF6A00),
                    0.25f to Color(0xFFFFD500),
                    0.45f to Color(0xFF54E346),
                    0.65f to Color(0xFF00C8FF),
                    0.85f to Color(0xFF3454FF),
                    1f to Color(0xFF9B00FF),
                ),
                start = Offset.Zero,
                end = Offset(width, height),
            ),
        )

        drawPath(
            path = Path().apply {
                moveTo(0f, 100f * scaleY)
                cubicTo(180f * scaleX, 180f * scaleY, 140f * scaleX, 380f * scaleY, 330f * scaleX, 560f * scaleY)
                cubicTo(520f * scaleX, 740f * scaleY, 760f * scaleX, 760f * scaleY, 950f * scaleX, 850f * scaleY)
                cubicTo(1100f * scaleX, 920f * scaleY, 1160f * scaleX, 1060f * scaleY, 1200f * scaleX, 1200f * scaleY)
                lineTo(1200f * scaleX, 0f)
                lineTo(0f, 0f)
                close()
            },
            color = Color.White.copy(alpha = 0.08f),
        )

        drawPath(
            path = Path().apply {
                moveTo(0f, 680f * scaleY)
                cubicTo(180f * scaleX, 700f * scaleY, 260f * scaleX, 900f * scaleY, 460f * scaleX, 990f * scaleY)
                cubicTo(650f * scaleX, 1075f * scaleY, 900f * scaleX, 990f * scaleY, 1200f * scaleX, 1160f * scaleY)
                lineTo(1200f * scaleX, 1200f * scaleY)
                lineTo(0f, 1200f * scaleY)
                close()
            },
            color = Color.White.copy(alpha = 0.07f),
        )

        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color.White.copy(alpha = 0.22f),
                    1f to Color.Transparent,
                ),
                center = Offset(width * 0.55f, height * 0.45f),
                radius = max(width, height) * 0.55f,
            ),
        )

        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.72f to Color.Transparent,
                    1f to bottomColor,
                ),
            ),
        )
    }
}

@Composable
private fun CreatePlaylistCard(
    existingPlaylistNames: List<String>,
    onCreatePlaylist: (String) -> Boolean,
) {
    var showCreatePlaylist by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(
        onClick = { showCreatePlaylist = true },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(imageVector = MusicIcons.Add, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Crear playlist")
    }
    if (showCreatePlaylist) {
        CreatePlaylistDialog(
            existingPlaylistNames = existingPlaylistNames,
            onCreatePlaylist = onCreatePlaylist,
            onDismiss = { showCreatePlaylist = false },
        )
    }
}

@Composable
private fun PlaylistRow(
    playlist: MusicPlaylist,
    songs: List<Song>,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .background(Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (songs.isNotEmpty()) {
            Artwork(song = songs.first(), modifier = Modifier.size(56.dp), rounded = 4.dp)
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MusicIcons.Playlist,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Playlist • ${songs.size.songCountLabel()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { showDeleteConfirmation = true }) {
            Icon(
                imageVector = MusicIcons.Delete,
                contentDescription = "Eliminar playlist",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Eliminar playlist") },
            text = { Text("Se eliminará \"${playlist.name}\". Las canciones no se borrarán del teléfono.") },
            confirmButton = {
                Button(onClick = {
                    showDeleteConfirmation = false
                    onDelete()
                }) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun EmptyPlaylists() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Sin playlists",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Crea una playlist y agrega canciones desde Mi Música o Favoritos.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun EmptyPlaylist(onOpenMusic: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Playlist vacía",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Agrega canciones con el botón de playlist en Mi Música.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenMusic) {
                Text("Ir a Mi Música")
            }
        }
    }
}
