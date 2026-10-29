@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.softmusic.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.softmusic.app.data.MusicPlaylist
import com.softmusic.app.data.Song

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SongRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    playlists: List<MusicPlaylist>,
    songs: List<Song>,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayAtEnd: () -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    var showActions by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .combinedClickable(
                    onClick = onClick,
                    onClickLabel = "Reproducir ${song.title}",
                    onLongClick = { showActions = true },
                    onLongClickLabel = "Mostrar opciones de ${song.title}",
                    role = Role.Button,
                )
                .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(
                song = song,
                modifier = Modifier.size(56.dp),
                rounded = 4.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (isPlaying) "Sonando • ${song.artist}" else "Canción • ${song.artist} - ${song.album}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { showActions = true }) {
                Icon(
                    imageVector = MusicIcons.MoreVertical,
                    contentDescription = "Mostrar opciones de ${song.title}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SongActionsDropdown(
            expanded = showActions,
            song = song,
            isFavorite = isFavorite,
            playlists = playlists,
            songs = songs,
            onDismiss = { showActions = false },
            onToggleFavorite = {
                showActions = false
                onToggleFavorite()
            },
            onPlayNext = {
                showActions = false
                onPlayNext()
            },
            onPlayAtEnd = {
                showActions = false
                onPlayAtEnd()
            },
            onAddToPlaylist = { playlistId ->
                showActions = false
                onAddToPlaylist(playlistId)
            },
        )
    }
}

@Composable
internal fun SongActionsDropdown(
    expanded: Boolean,
    song: Song,
    isFavorite: Boolean,
    playlists: List<MusicPlaylist>,
    songs: List<Song>,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayAtEnd: () -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    var showPlaylists by remember { mutableStateOf(false) }
    val songsById = remember(expanded, songs) {
        if (expanded) songs.associateBy { it.id } else emptyMap()
    }
    val availablePlaylists = remember(expanded, playlists, song.id) {
        if (expanded) playlists.filterNot { playlist -> song.id in playlist.songIds } else emptyList()
    }

    LaunchedEffect(expanded) {
        if (!expanded) showPlaylists = false
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        if (showPlaylists) {
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { showPlaylists = false }) {
                        Icon(
                            imageVector = MusicIcons.Back,
                            contentDescription = "Volver",
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Agregar a playlist",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Selecciona dónde guardar esta canción",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                when {
                    playlists.isEmpty() -> AddToPlaylistEmptyState("Crea una playlist primero")
                    availablePlaylists.isEmpty() -> AddToPlaylistEmptyState("Ya está en todas tus playlists")
                    else -> availablePlaylists.forEach { playlist ->
                        PlaylistPickerRow(
                            playlist = playlist,
                            songsById = songsById,
                            onClick = { onAddToPlaylist(playlist.id) },
                        )
                    }
                }
            }
        } else {
            DropdownMenuItem(
                text = { Text("Reproducir después") },
                leadingIcon = {
                    Icon(
                        imageVector = MusicIcons.PlayNext,
                        contentDescription = null,
                    )
                },
                onClick = onPlayNext,
            )
            DropdownMenuItem(
                text = { Text("Reproducir al final") },
                leadingIcon = {
                    Icon(
                        imageVector = MusicIcons.Queue,
                        contentDescription = null,
                    )
                },
                onClick = onPlayAtEnd,
            )
            DropdownMenuItem(
                text = { Text(if (isFavorite) "Quitar de favoritos" else "Agregar a favoritos") },
                leadingIcon = {
                    Icon(
                        imageVector = if (isFavorite) MusicIcons.FavoriteRemoveSelected else MusicIcons.FavoriteAdd,
                        contentDescription = null,
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = onToggleFavorite,
            )
            if (availablePlaylists.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text("Agregar a Playlist") },
                    leadingIcon = {
                        Icon(
                            imageVector = MusicIcons.PlaylistAdd,
                            contentDescription = null,
                        )
                    },
                    onClick = { showPlaylists = true },
                )
            }
            if (onRemove != null) {
                MusicActionMenuItem(
                    text = "Quitar de playlist",
                    icon = MusicActionIcons.RemoveFromPlaylist,
                    onClick = onRemove,
                )
            }
        }
    }
}

@Composable
internal fun AddToPlaylistButton(
    playlists: List<MusicPlaylist>,
    songs: List<Song>,
    song: Song,
    onAddToPlaylist: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val songsById = remember(expanded, songs) {
        if (expanded) songs.associateBy { it.id } else emptyMap()
    }
    val availablePlaylists = remember(expanded, playlists, song.id) {
        if (expanded) playlists.filterNot { playlist -> song.id in playlist.songIds } else emptyList()
    }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = MusicIcons.PlaylistAdd,
                contentDescription = "Agregar a playlist",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(25.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Agregar a playlist",
                    modifier = Modifier.padding(horizontal = 10.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Selecciona dónde guardar esta canción",
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    playlists.isEmpty() -> AddToPlaylistEmptyState("Crea una playlist primero")
                    availablePlaylists.isEmpty() -> AddToPlaylistEmptyState("Ya está en todas tus playlists")
                    else -> availablePlaylists.forEach { playlist ->
                        PlaylistPickerRow(
                            playlist = playlist,
                            songsById = songsById,
                            onClick = {
                                expanded = false
                                onAddToPlaylist(playlist.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AddToPlaylistEmptyState(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MusicIcons.PlaylistAdd,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlaylistPickerRow(
    playlist: MusicPlaylist,
    songsById: Map<Long, Song>,
    onClick: () -> Unit,
) {
    val playlistSongs = remember(playlist.songIds, songsById) {
        playlist.songIds.mapNotNull { songsById[it] }
    }
    val coverSong = remember(playlist.coverSongId, playlistSongs) { playlist.coverSongFrom(playlistSongs) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
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
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MusicIcons.PlaylistAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
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
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = playlistSongs.size.songCountLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Icon(
            imageVector = MusicIcons.PlaylistAdd,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}
