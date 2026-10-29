package com.softmusic.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
internal fun PlaylistActionMenu(
    songCount: Int,
    onRename: () -> Unit,
    onChangeCover: () -> Unit,
    onSelect: () -> Unit,
    onReorder: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    fun perform(action: () -> Unit) {
        expanded = false
        action()
    }

    Box {
        MusicActionIconButton(
            icon = MusicIcons.MoreVertical,
            contentDescription = "Más opciones de playlist",
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MusicActionMenuItem(
                text = "Renombrar",
                icon = MusicActionIcons.Rename,
                onClick = { perform(onRename) },
            )
            MusicActionMenuItem(
                text = "Cambiar portada",
                icon = MusicActionIcons.ChangeCover,
                onClick = { perform(onChangeCover) },
                enabled = songCount > 0,
            )
            MusicActionMenuItem(
                text = "Seleccionar canciones",
                icon = MusicActionIcons.SelectSongs,
                onClick = { perform(onSelect) },
                enabled = songCount > 0,
            )
            MusicActionMenuItem(
                text = "Orden manual",
                icon = MusicActionIcons.SortCustom,
                onClick = { perform(onReorder) },
                enabled = songCount > 1,
            )
            HorizontalDivider()
            MusicActionMenuItem(
                text = "Eliminar playlist",
                icon = MusicActionIcons.DeletePlaylist,
                onClick = { perform(onDelete) },
                destructive = true,
            )
        }
    }
}
