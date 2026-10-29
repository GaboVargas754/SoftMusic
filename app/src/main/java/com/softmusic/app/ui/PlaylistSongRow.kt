package com.softmusic.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
internal fun PlaylistSongRow(
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
    onRemove: () -> Unit,
) {
    var showActions by remember { mutableStateOf(false) }
    val rowBackground by animateColorAsState(
        targetValue = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent,
        animationSpec = tween(durationMillis = 180),
        label = "songRowBackground",
    )

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
                .background(rowBackground)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(song = song, modifier = Modifier.size(56.dp), rounded = 4.dp)
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
            MusicActionIconButton(
                icon = MusicIcons.MoreVertical,
                contentDescription = "Mostrar opciones de ${song.title}",
                onClick = { showActions = true },
            )
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
            onRemove = {
                showActions = false
                onRemove()
            },
        )
    }
}
