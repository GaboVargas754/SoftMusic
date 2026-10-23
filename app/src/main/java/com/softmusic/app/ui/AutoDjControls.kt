package com.softmusic.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.softmusic.app.player.AutoDjEffect
import com.softmusic.app.player.AutoDjPhase
import com.softmusic.app.player.AutoDjSettings
import com.softmusic.app.player.AutoDjStatus
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun AutoDjSettingsControls(
    settings: AutoDjSettings,
    enabled: Boolean,
    onSettingsChange: (AutoDjSettings) -> Unit,
) {
    val safeSettings = settings.sanitized()
    var intensity by remember(safeSettings.intensity) { mutableFloatStateOf(safeSettings.intensity) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Duración de la mezcla",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(8, 16, 32).forEach { beats ->
                AutoDjOptionButton(
                    label = "$beats pulsos",
                    selected = safeSettings.beats == beats,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = { onSettingsChange(safeSettings.copy(beats = beats)) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("Sincronizar BPM", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = "Ajusta el tempo de la siguiente canción sin cambiar su tono.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = safeSettings.syncEnabled,
                enabled = enabled,
                onCheckedChange = { onSettingsChange(safeSettings.copy(syncEnabled = it)) },
            )
        }

        Text(
            text = "Efecto de transición",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AutoDjEffect.entries.chunked(2).forEach { effects ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                effects.forEach { effect ->
                    AutoDjOptionButton(
                        label = effect.label,
                        selected = safeSettings.effect == effect,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onClick = { onSettingsChange(safeSettings.copy(effect = effect)) },
                    )
                }
            }
        }
        Text(
            text = when (safeSettings.effect) {
                AutoDjEffect.None -> "Cruza los volúmenes de las dos canciones de forma suave."
                AutoDjEffect.BassSwap -> "Intercambia los graves para que los bajos se mezclen con claridad."
                AutoDjEffect.FilterSweep -> "Aplica un filtro gradual durante la transición."
                AutoDjEffect.EchoOut -> "Desvanece la canción saliente con un eco breve."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (safeSettings.effect != AutoDjEffect.None) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Intensidad", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = "${(intensity * 100).roundToInt()} %",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = intensity,
                    enabled = enabled,
                    onValueChange = { intensity = it },
                    onValueChangeFinished = {
                        onSettingsChange(safeSettings.copy(intensity = intensity))
                    },
                    modifier = Modifier.semantics { contentDescription = "Intensidad del efecto" },
                )
            }
        }
        Text(
            text = "Si no se puede reconocer el ritmo, la mezcla continúa de forma suave.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AutoDjOptionButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics { this.selected = selected },
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent,
        ),
    ) {
        Text(label)
    }
}

@Composable
internal fun AutoDjStatusChip(status: AutoDjStatus) {
    Surface(
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.semantics { stateDescription = status.phase.displayLabel },
    ) {
        Text(
            text = "Auto DJ",
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}

@Composable
internal fun AutoDjStatusPanel(
    status: AutoDjStatus,
    syncEnabled: Boolean,
    accent: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    mutedColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    val speed = status.incomingSpeed.takeIf { it.isFinite() && it > 0f } ?: 1f
    val progress = status.progress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val hasNextSong = status.nextMediaId != null && status.nextMediaId != status.currentMediaId
    val hasTransitionProgress = status.phase == AutoDjPhase.Mixing || status.phase == AutoDjPhase.Restoring

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Auto DJ · ${status.phase.displayLabel}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AutoDjTempo("Actual", status.currentBpm, contentColor, mutedColor, Modifier.weight(1f))
                if (hasNextSong) {
                    AutoDjTempo("Siguiente", status.nextBpm, contentColor, mutedColor, Modifier.weight(1f))
                }
            }
            status.nextTitle?.takeIf { hasNextSong && it.isNotBlank() }?.let { title ->
                Text(
                    text = "Sigue: $title",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = when {
                    status.phase == AutoDjPhase.Ready -> {
                        val syncLabel = when {
                            status.synchronized -> "Sincronía preparada"
                            !syncEnabled -> "Sincronía desactivada"
                            else -> "Mezcla preparada"
                        }
                        "$syncLabel · ${formatAutoDjSpeed(speed, planned = true)}"
                    }
                    status.phase == AutoDjPhase.Restoring -> "Volviendo al tempo original · ${formatAutoDjSpeed(speed)}"
                    status.phase == AutoDjPhase.Fallback -> "La reproducción continúa sin mezcla."
                    status.synchronized -> "Ritmos sincronizados · ${formatAutoDjSpeed(speed)}"
                    !syncEnabled -> "Sincronía desactivada · ${formatAutoDjSpeed(speed)}"
                    status.phase == AutoDjPhase.Mixing -> "Mezcla en curso · ${formatAutoDjSpeed(speed)}"
                    else -> "Tempo original · ${formatAutoDjSpeed(speed)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = mutedColor,
            )
            if (status.phase == AutoDjPhase.Analyzing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = accent)
            } else if (hasTransitionProgress) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = accent,
                    trackColor = accent.copy(alpha = 0.15f),
                )
                Text(
                    text = "${if (status.phase == AutoDjPhase.Restoring) "Ajuste de tempo" else "Transición"}: ${(progress * 100).roundToInt()} %",
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedColor,
                )
            }
            status.message?.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = mutedColor,
                )
            }
        }
    }
}

@Composable
private fun AutoDjTempo(
    label: String,
    bpm: Float?,
    contentColor: Color,
    mutedColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = mutedColor)
        Text(
            text = bpm?.takeIf { it.isFinite() && it > 0f }
                ?.let { "${String.format(Locale.getDefault(), "%.1f", it)} BPM" } ?: "BPM por detectar",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
        )
    }
}

private fun formatAutoDjSpeed(speed: Float, planned: Boolean = false): String =
    "${if (planned) "Velocidad prevista" else "Velocidad"} ${String.format(Locale.getDefault(), "%.2f", speed)}×"

private val AutoDjPhase.displayLabel: String
    get() = when (this) {
        AutoDjPhase.Idle -> "En espera"
        AutoDjPhase.Analyzing -> "Analizando ritmo"
        AutoDjPhase.Ready -> "Mezcla lista"
        AutoDjPhase.Mixing -> "Mezclando"
        AutoDjPhase.Restoring -> "Recuperando tempo"
        AutoDjPhase.Fallback -> "Sin mezcla"
        AutoDjPhase.Paused -> "En pausa"
    }
