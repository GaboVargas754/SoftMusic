package com.softmusic.app.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softmusic.app.player.ExpertDjPreviewPhase
import com.softmusic.app.player.ExpertDjPreviewState
import com.softmusic.app.player.MusicService
import kotlin.math.roundToInt

@Composable
internal fun ExpertDjPreviewSetting() {
    var showPreview by rememberSaveable { mutableStateOf(false) }
    SettingsRow(
        title = "Probar transición",
        description = "Escucha la canción actual y la siguiente de la cola.",
        onClick = {
            MusicService.refreshExpertPreviewContext()
            showPreview = true
        },
        leading = {
            Icon(
                imageVector = MusicIcons.Play,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailing = {
            Icon(
                imageVector = MusicIcons.ChevronDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp).rotate(-90f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
    if (showPreview) {
        val state by MusicService.expertPreviewState.collectAsStateWithLifecycle()
        DisposableEffect(Unit) {
            onDispose { MusicService.stopMixPreview(restorePlayback = true) }
        }
        ExpertDjPreviewDialog(
            state = state,
            onStart = { MusicService.startExpertPreview() },
            onStop = { MusicService.stopMixPreview(restorePlayback = true) },
            onDismiss = {
                MusicService.stopMixPreview(restorePlayback = true)
                showPreview = false
            },
        )
    }
}

@Composable
internal fun ExpertDjPreviewDialog(
    state: ExpertDjPreviewState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    val active = state.phase == ExpertDjPreviewPhase.Preparing || state.phase == ExpertDjPreviewPhase.Playing
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                .widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    item(key = "title") {
                        Text(
                            "Probar transición",
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    item(key = "description") {
                        Text(
                            "Prueba el enlace entre el final de la canción actual y el inicio de la siguiente. Al terminar, se recupera tu reproducción.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item(key = "pair") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                ExpertDjPreviewSong("Actual", state.currentTitle, state.currentBpm)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                ExpertDjPreviewSong("Siguiente", state.nextTitle, state.nextBpm)
                            }
                        }
                    }
                    item(key = "status") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = expertDjPreviewStatus(state),
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (state.phase == ExpertDjPreviewPhase.Error) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            val progress = state.progress?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
                            if (progress != null && active) {
                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                            } else if (state.phase == ExpertDjPreviewPhase.Preparing) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    item(key = "transport") {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FilledTonalIconButton(
                                onClick = if (active) onStop else onStart,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = if (active) MusicIcons.PauseSelected else MusicIcons.PlaySelected,
                                    contentDescription = if (active) "Detener prueba" else "Iniciar prueba",
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Text(
                                if (active) "Detener prueba" else "Iniciar prueba",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Cerrar")
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpertDjPreviewSong(label: String, title: String, bpm: Float?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            title.ifBlank { "No disponible" },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        bpm?.takeIf { it.isFinite() && it > 0f }?.let {
            Text(
                "Ritmo detectado · ${it.roundToInt()} BPM",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun expertDjPreviewStatus(state: ExpertDjPreviewState): String = state.message?.takeIf { it.isNotBlank() }
    ?: when (state.phase) {
        ExpertDjPreviewPhase.Idle -> "Se usarán la canción actual y la siguiente de tu cola."
        ExpertDjPreviewPhase.Preparing -> "Preparando la transición…"
        ExpertDjPreviewPhase.Playing -> "Reproduciendo la transición."
        ExpertDjPreviewPhase.Finished -> "Prueba finalizada."
        ExpertDjPreviewPhase.Error -> "No se pudo preparar la prueba. Revisa la cola e inténtalo de nuevo."
    }
