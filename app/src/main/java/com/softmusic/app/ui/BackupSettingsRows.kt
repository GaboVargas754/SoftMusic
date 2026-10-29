package com.softmusic.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

@Composable
internal fun BackupSettingsRows(ui: BackupSettingsUi) {
    val controller = ui.controller
    val actions = controller.actions ?: return
    val enabled = controller.canStart
    val disabledModifier = Modifier.alpha(if (enabled) 1f else 0.5f)
    SettingsRow(
        title = "Crear respaldo",
        description = "Guarda playlists, favoritos y preferencias en un archivo.",
        modifier = disabledModifier,
        onClick = ui.onCreate,
        enabled = enabled,
        leading = { Icon(MusicIcons.Folder, contentDescription = null, modifier = Modifier.size(24.dp)) },
        trailing = {},
    )
    SettingsDivider()
    SettingsRow(
        title = "Restaurar respaldo",
        description = "Revisa un archivo antes de restaurarlo. Máximo 10 MiB.",
        modifier = disabledModifier,
        onClick = ui.onOpen,
        enabled = enabled,
        leading = { Icon(MusicIcons.Reload, contentDescription = null, modifier = Modifier.size(24.dp)) },
        trailing = {},
    )
    if (actions.pendingCount > 0) {
        SettingsDivider()
        SettingsRow(
            title = "Revisar pendientes (${actions.pendingCount})",
            description = "Busca las canciones que faltaron en una restauración.",
            modifier = disabledModifier,
            onClick = controller::showPending,
            enabled = enabled,
            leading = { Icon(MusicIcons.Recent, contentDescription = null, modifier = Modifier.size(24.dp)) },
            trailing = {},
        )
    }
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        Text(
            text = if (actions.libraryReady) {
                "No incluye archivos de audio, carátulas externas ni historial de escucha."
            } else {
                "Espera a que termine de cargar la biblioteca. No se incluyen archivos de audio ni historial de escucha."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
