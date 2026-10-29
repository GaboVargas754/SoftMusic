package com.softmusic.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.softmusic.app.data.Song

@Composable
internal fun PlaylistSelectableSongRow(song: Song, selected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song = song, modifier = Modifier.size(48.dp), rounded = 4.dp)
        PlaylistEditingSongText(song, Modifier.weight(1f))
        Checkbox(checked = selected, onCheckedChange = null)
    }
}

@Composable
internal fun PlaylistReorderSongRow(
    song: Song,
    index: Int,
    count: Int,
    dragging: Boolean,
    modifier: Modifier,
    dragModifier: Modifier,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
) {
    var showMoves by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.background)
            .heightIn(min = 60.dp)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song = song, modifier = Modifier.size(40.dp), rounded = 4.dp)
        Spacer(Modifier.width(10.dp))
        PlaylistEditingSongText(song, Modifier.weight(1f))
        Box(
            modifier = Modifier.size(48.dp).then(dragModifier).semantics(mergeDescendants = true) {
                contentDescription = "Reordenar ${song.title}"
                stateDescription = "Posición ${index + 1} de $count"
                role = Role.Button
                customActions = buildList {
                    if (onMoveUp != null) add(CustomAccessibilityAction("Subir") { onMoveUp(); true })
                    if (onMoveDown != null) add(CustomAccessibilityAction("Bajar") { onMoveDown(); true })
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (dragging) MusicActionIcons.DragReorderSelected else MusicActionIcons.DragReorder,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            MusicActionIconButton(
                icon = MusicIcons.MoreVertical,
                contentDescription = "Mover ${song.title}",
                onClick = { showMoves = true },
            )
            DropdownMenu(expanded = showMoves, onDismissRequest = { showMoves = false }) {
                MusicActionMenuItem(
                    text = "Subir",
                    icon = MusicActionIcons.MoveUp,
                    enabled = onMoveUp != null,
                    onClick = {
                        showMoves = false
                        onMoveUp?.invoke()
                    },
                )
                MusicActionMenuItem(
                    text = "Bajar",
                    icon = MusicActionIcons.MoveDown,
                    enabled = onMoveDown != null,
                    onClick = {
                        showMoves = false
                        onMoveDown?.invoke()
                    },
                )
            }
        }
    }
}

@Composable
private fun PlaylistEditingSongText(song: Song, modifier: Modifier) {
    Column(modifier) {
        Text(song.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
