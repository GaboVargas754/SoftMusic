package com.softmusic.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.softmusic.app.player.PlaylistShuffleMode

/** Replaces the complete header action row while selecting or reordering songs. */
@Composable
internal fun RowScope.PlaylistEditingToolbar(
    selectionMode: Boolean,
    reorderMode: Boolean,
    selectedCount: Int,
    visibleCount: Int,
    shuffleMode: PlaylistShuffleMode,
    onRename: () -> Unit,
    onAdd: () -> Unit,
    onChangeCover: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit,
    onReorder: () -> Unit,
    onSelectAll: () -> Unit,
    onRemoveSelected: () -> Unit,
    onFinish: () -> Unit,
    onPlay: () -> Unit,
    onCycleShuffleMode: () -> Unit,
) {
    when {
        selectionMode -> {
            val allSelected = visibleCount > 0 && selectedCount == visibleCount
            MusicActionIconButton(
                icon = MusicIcons.Close,
                contentDescription = "Salir de la selección",
                onClick = onFinish,
            )
            Text(
                text = "Seleccionadas: $selectedCount",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MusicActionIconButton(
                icon = if (allSelected) MusicActionIcons.DeselectAllSelected else MusicActionIcons.SelectAll,
                contentDescription = if (allSelected) "Deseleccionar todo" else "Seleccionar todo",
                onClick = onSelectAll,
                selected = allSelected,
                enabled = visibleCount > 0,
            )
            MusicActionIconButton(
                icon = MusicActionIcons.RemoveFromPlaylist,
                contentDescription = "Quitar canciones seleccionadas de la playlist",
                onClick = onRemoveSelected,
                enabled = selectedCount > 0,
            )
        }
        reorderMode -> {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = MusicActionIcons.SortCustomSelected,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "Orden manual",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MusicActionIconButton(
                icon = MusicIcons.Check,
                contentDescription = "Listo",
                onClick = onFinish,
            )
        }
        else -> {
            MusicActionIconButton(
                icon = MusicActionIcons.AddManySongs,
                contentDescription = "Agregar canciones",
                onClick = onAdd,
            )
            PlaylistActionMenu(
                songCount = visibleCount,
                onRename = onRename,
                onChangeCover = onChangeCover,
                onSelect = onSelect,
                onReorder = onReorder,
                onDelete = onDelete,
            )
            Spacer(Modifier.weight(1f))
            CollectionPlaybackActions(
                songsAvailable = visibleCount > 0,
                playbackMode = shuffleMode.playbackMode,
                onPlay = onPlay,
                onPlaybackModeChange = { onCycleShuffleMode() },
                playDescription = "Reproducir playlist",
                playlistShuffleMode = shuffleMode,
                onCyclePlaylistShuffleMode = onCycleShuffleMode,
            )
        }
    }
}
