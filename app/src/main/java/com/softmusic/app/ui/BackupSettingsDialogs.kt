package com.softmusic.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.softmusic.app.backup.BackupMatchStatus
import com.softmusic.app.backup.BackupSongMatch
import java.text.DateFormat
import java.util.Date

@Composable
internal fun BackupSettingsDialogs(controller: BackupSettingsController) {
    // The root settings screen also handles back. Consume it while a commit is in progress.
    BackHandler(enabled = controller.isCommitting) {}
    val operationLabel = controller.operationLabel
    if (operationLabel != null) {
        AlertDialog(
            onDismissRequest = controller::cancelOperation,
            properties = DialogProperties(
                dismissOnBackPress = !controller.isCommitting,
                dismissOnClickOutside = !controller.isCommitting,
            ),
            title = { Text(operationLabel) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Text(
                        if (controller.isCommitting) "Guardando los cambios confirmados…"
                        else "Puedes cancelar esta operación.",
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                if (!controller.isCommitting) {
                    TextButton(
                        onClick = controller::cancelOperation,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Cancelar") }
                }
            },
        )
    } else {
        controller.preview?.let { preview ->
            BackupPreviewDialog(
                preview = preview,
                libraryReady = controller.actions?.libraryReady == true,
                onApplyPreferences = controller::setApplyPreferences,
                onConfirm = controller::confirmRestore,
                onReviewAgain = controller::reviewAgain,
                onDismiss = controller::closePreview,
            )
        }
        controller.notice?.let { message ->
            AlertDialog(
                onDismissRequest = controller::dismissNotice,
                title = { Text("Respaldo y restauración") },
                text = {
                    Text(
                        message,
                        modifier = Modifier.heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState())
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = controller::dismissNotice,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Aceptar") }
                },
            )
        }
    }
}

@Composable
private fun BackupPreviewDialog(
    preview: BackupPreviewState,
    libraryReady: Boolean,
    onApplyPreferences: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onReviewAgain: () -> Unit,
    onDismiss: () -> Unit,
) {
    val matchedCount = remember(preview.matches) { preview.matches.count { it.status == BackupMatchStatus.Matched } }
    val missingCount = remember(preview.matches) { preview.matches.count { it.status == BackupMatchStatus.Missing } }
    val ambiguousCount = preview.matches.size - matchedCount - missingCount
    val unresolved = remember(preview.matches) { preview.matches.filter { it.status != BackupMatchStatus.Matched } }
    val isPending = preview.document == null
    val listedMatches = if (isPending) preview.matches else unresolved
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                .widthIn(max = 640.dp).fillMaxWidth().heightIn(max = maxHeight),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(key = "heading") {
                        Text(
                            if (isPending) "Canciones pendientes" else "Revisar respaldo",
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    preview.document?.let { document ->
                        item(key = "document-summary") {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Creado: ${formatBackupDate(document.createdAtEpochMs)}", style = MaterialTheme.typography.bodyMedium)
                                Text("${document.playlists.size} playlists · ${document.favoriteKeys.size} favoritos", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    item(key = "matches") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Encontradas: $matchedCount", style = MaterialTheme.typography.bodyLarge)
                            Text("Faltantes: $missingCount", style = MaterialTheme.typography.bodyLarge)
                            Text("Ambiguas: $ambiguousCount", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    item(key = "explanation") {
                        Text(
                            if (isPending) {
                                "Solo se vincularán las coincidencias únicas encontradas. Las canciones faltantes o ambiguas seguirán pendientes."
                            } else {
                                "Se crearán copias nuevas de las playlists y se añadirán los favoritos a los actuales. Las canciones faltantes o ambiguas se conservarán como pendientes. No se copian archivos de audio ni historial."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (preview.document?.preferences != null) {
                        item(key = "preferences") {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .toggleable(
                                        value = preview.applyPreferences,
                                        role = Role.Checkbox,
                                        onValueChange = onApplyPreferences,
                                    )
                                    .heightIn(min = 48.dp).padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = preview.applyPreferences, onCheckedChange = null)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Aplicar preferencias", style = MaterialTheme.typography.bodyLarge)
                                    Text("Cambia los ajustes incluidos en el respaldo.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    preview.error?.let { error ->
                        item(key = "error") {
                            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        }
                    }
                    if (!libraryReady) {
                        item(key = "library-loading") {
                            Text("Espera a que termine de cargar la biblioteca.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (listedMatches.isNotEmpty()) {
                        item(key = "unresolved-heading") {
                            Text(
                                if (isPending) "Canciones revisadas" else "Canciones por revisar",
                                modifier = Modifier.semantics { heading() },
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        items(listedMatches, key = { it.reference.key }) { match -> BackupPreviewSong(match) }
                    } else {
                        item(key = "no-unresolved") {
                            Text(
                                if (preview.matches.isEmpty() && isPending) "Ya no hay canciones pendientes."
                                else if (preview.matches.isEmpty()) "El respaldo no contiene canciones."
                                else "Todas las canciones tienen una coincidencia única.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                HorizontalDivider()
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Button(
                        onClick = onConfirm,
                        enabled = libraryReady && !preview.needsReview && (!isPending || matchedCount > 0),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(if (isPending) "Vincular encontradas" else "Restaurar copias") }
                    OutlinedButton(
                        onClick = onReviewAgain,
                        enabled = libraryReady,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Revisar de nuevo") }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Cancelar")
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupPreviewSong(match: BackupSongMatch) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(match.reference.fileName ?: match.reference.title.ifBlank { "Canción sin nombre" }, style = MaterialTheme.typography.bodyLarge)
        Text(
            when (match.status) {
                BackupMatchStatus.Matched -> "Encontrada: coincidencia única. Se vinculará al confirmar."
                BackupMatchStatus.Ambiguous -> "Ambigua: hay varias coincidencias; no se vinculará automáticamente."
                BackupMatchStatus.Missing -> "Faltante: no se encontró una coincidencia única."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatBackupDate(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))
