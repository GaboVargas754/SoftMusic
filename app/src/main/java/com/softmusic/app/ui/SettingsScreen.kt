package com.softmusic.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.softmusic.app.BuildConfig
import com.softmusic.app.data.MusicFolder
import com.softmusic.app.player.AutoDjSettings
import com.softmusic.app.player.DjMixMode
import com.softmusic.app.player.MusicService
import com.softmusic.app.player.PlaybackMode
import com.softmusic.app.player.SortMode
import kotlin.math.roundToInt

// Keep the same entry point for the full screen and the player's settings sheet.
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun SettingsSheet(
    highPerformanceMode: Boolean,
    hasAudioPermission: Boolean,
    audioPermissionPermanentlyDenied: Boolean,
    hasNotificationPermission: Boolean,
    allFolders: List<MusicFolder>,
    folders: List<MusicFolder>,
    defaultFolderPath: String?,
    defaultPlaybackMode: PlaybackMode,
    defaultSortMode: SortMode,
    fontScale: Float,
    hiddenFolderPaths: Set<String>,
    excludeSmallAudios: Boolean,
    djModeEnabled: Boolean,
    djMixMode: DjMixMode,
    djMixDurationSeconds: Int,
    autoDjSettings: AutoDjSettings,
    onHighPerformanceModeChange: (Boolean) -> Unit,
    onRequestAudioPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onDefaultFolderChange: (String?) -> Unit,
    onDefaultPlaybackModeChange: (PlaybackMode) -> Unit,
    onDefaultSortModeChange: (SortMode) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onExcludeSmallAudiosChange: (Boolean) -> Unit,
    onHiddenFolderChange: (String, Boolean) -> Unit,
    onDjModeChange: (Boolean) -> Unit,
    onDjMixModeChange: (DjMixMode) -> Unit,
    onDjMixDurationChange: (Int) -> Unit,
    onAutoDjSettingsChange: (AutoDjSettings) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenDonation: () -> Unit,
    isCheckingForUpdates: Boolean,
    updateCheckStatus: String?,
    onCheckForUpdates: () -> Unit,
    historyEnabled: Boolean,
    historyHasEntries: Boolean,
    onHistoryEnabledChange: (Boolean) -> Unit,
    onClearListeningHistory: () -> Unit,
) {
    var confirmClearHistory by rememberSaveable { mutableStateOf(false) }
    val backupSettings = rememberBackupSettingsUi()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                MusicService.stopMixPreview(restorePlayback = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            MusicService.stopMixPreview(restorePlayback = true)
        }
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Ajustes",
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Personaliza tu experiencia en SoftMusic.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = MusicIcons.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "appearance") {
                SettingsGroup(title = "Apariencia") {
                    TextScaleSetting(fontScale, onFontScaleChange)
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "Alto rendimiento",
                        description = "Interfaz ligera, sin degradados ni carátulas.",
                        checked = highPerformanceMode,
                        onCheckedChange = onHighPerformanceModeChange,
                    )
                }
            }

            item(key = "library") {
                SettingsGroup(title = "Biblioteca") {
                    SettingsChoiceRow(
                        title = "Carpeta inicial",
                        selectedValue = defaultFolderPath,
                        options = listOf(SettingsOption<String?>(null, "Todas las carpetas")) + folders.map {
                            SettingsOption<String?>(it.path, it.name, "${it.songCount.songCountLabel()} · ${it.path}")
                        },
                        onSelect = onDefaultFolderChange,
                    )
                    SettingsDivider()
                    HiddenFoldersSetting(allFolders, hiddenFolderPaths, onHiddenFolderChange)
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "Excluir audios pequeños",
                        description = "Oculta audios de menos de 30 s o 1 MB en biblioteca, búsqueda y playlists.",
                        checked = excludeSmallAudios,
                        onCheckedChange = onExcludeSmallAudiosChange,
                    )
                }
            }

            if (backupSettings.controller.actions != null) {
                item(key = "backup") {
                    SettingsGroup(title = "Respaldo y restauración") {
                        BackupSettingsRows(backupSettings)
                    }
                }
            }

            item(key = "playback") {
                SettingsGroup(title = "Reproducción") {
                    SettingsChoiceRow(
                        title = "Modo de reproducción",
                        selectedValue = defaultPlaybackMode,
                        options = PlaybackMode.entries.map { SettingsOption(it, it.label) },
                        onSelect = onDefaultPlaybackModeChange,
                    )
                    SettingsDivider()
                    SettingsChoiceRow(
                        title = "Orden inicial",
                        selectedValue = defaultSortMode,
                        options = SortMode.entries.map { SettingsOption(it, it.label) },
                        onSelect = onDefaultSortModeChange,
                    )
                    SettingsDivider()
                    DjSettings(
                        enabled = djModeEnabled,
                        mixMode = djMixMode,
                        mixDurationSeconds = djMixDurationSeconds,
                        onEnabledChange = onDjModeChange,
                        onMixModeChange = onDjMixModeChange,
                        onMixDurationChange = onDjMixDurationChange,
                    )
                }
            }

            item(key = "listening-history") {
                SettingsGroup(title = "Historial local") {
                    SettingsToggleRow(
                        title = "Guardar escuchas",
                        description = "Registra lo que escuchas solo en este dispositivo. Al pausarlo, se conserva el historial anterior.",
                        checked = historyEnabled,
                        onCheckedChange = onHistoryEnabledChange,
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Borrar historial",
                        description = if (historyHasEntries) "Elimina las escuchas recientes y los conteos guardados."
                            else "Todavía no hay escuchas guardadas.",
                        onClick = { confirmClearHistory = true },
                        trailing = { SettingsChevron() },
                    )
                }
            }

            item(key = "permissions") {
                SettingsGroup(title = "Permisos") {
                    SettingsPermissionRow(
                        title = "Música local",
                        description = if (audioPermissionPermanentlyDenied) {
                            "Permiso bloqueado. Abre los permisos de la app para activarlo."
                        } else {
                            "Permite acceder a las canciones del teléfono."
                        },
                        granted = hasAudioPermission,
                        actionLabel = if (audioPermissionPermanentlyDenied) "Abrir ajustes" else "Dar permiso",
                        onAction = if (audioPermissionPermanentlyDenied) onOpenAppSettings else onRequestAudioPermission,
                    )
                    SettingsDivider()
                    SettingsPermissionRow(
                        title = "Notificaciones",
                        description = "Permite mostrar los controles de reproducción.",
                        granted = hasNotificationPermission,
                        actionLabel = "Dar permiso",
                        onAction = onRequestNotificationPermission,
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Pantalla de bloqueo",
                        description = "Configura la visibilidad de las notificaciones.",
                        onClick = onOpenNotificationSettings,
                        onClickLabel = "Abrir ajustes de notificaciones",
                        trailing = { SettingsChevron() },
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Batería",
                        description = "Quita restricciones si la música se corta en segundo plano.",
                        onClick = onOpenAppSettings,
                        onClickLabel = "Abrir ajustes de la app",
                        trailing = { SettingsChevron() },
                    )
                }
            }

            item(key = "about") {
                SettingsGroup(title = "SoftMusic") {
                    SettingsRow(
                        title = "Buscar actualizaciones",
                        description = if (isCheckingForUpdates) {
                            "Buscando una nueva versión…"
                        } else {
                            updateCheckStatus ?: "Versión ${BuildConfig.VERSION_NAME}"
                        },
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        onClick = if (isCheckingForUpdates) null else onCheckForUpdates,
                        trailing = {
                            if (isCheckingForUpdates) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                SettingsChevron()
                            }
                        },
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Apoyar SoftMusic",
                        description = "Invita un café y apoya el desarrollo de la app.",
                        onClick = onOpenDonation,
                        trailing = { SettingsChevron() },
                    )
                }
            }
        }
    }
    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text("¿Borrar historial?") },
            text = { Text("Se eliminarán todas las escuchas y sus conteos de este dispositivo. No se borrarán canciones ni playlists.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearHistory = false
                    onClearListeningHistory()
                }) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClearHistory = false }) { Text("Cancelar") } },
        )
    }
    BackupSettingsDialogs(backupSettings.controller)
}

@Composable
internal fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(content = content)
        }
    }
}

@Composable
internal fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
    )
}

@Composable
internal fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = onClickLabel,
                    onClick = onClick,
                ) else Modifier,
            )
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    SettingsRow(
        title = title,
        description = description,
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        leading = leading,
        trailing = { Switch(checked = checked, onCheckedChange = null) },
    )
}

@Composable
private fun SettingsChevron(expanded: Boolean? = null) {
    Icon(
        imageVector = MusicIcons.ChevronDown,
        contentDescription = null,
        modifier = Modifier.size(18.dp).rotate(
            when (expanded) {
                null -> -90f
                true -> 180f
                false -> 0f
            },
        ),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private data class SettingsOption<T>(val value: T, val label: String, val description: String? = null)

@Composable
private fun <T> SettingsChoiceRow(
    title: String,
    selectedValue: T,
    options: List<SettingsOption<T>>,
    onSelect: (T) -> Unit,
) {
    var showOptions by rememberSaveable { mutableStateOf(false) }
    SettingsRow(
        title = title,
        description = options.firstOrNull { it.value == selectedValue }?.label ?: options.firstOrNull()?.label,
        onClick = { showOptions = true },
        trailing = { SettingsChevron() },
    )
    if (showOptions) {
        AlertDialog(
            onDismissRequest = { showOptions = false },
            title = { Text(title) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp).selectableGroup()) {
                    items(options) { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = option.value == selectedValue,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onSelect(option.value)
                                        showOptions = false
                                    },
                                )
                                .heightIn(min = 56.dp)
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option.value == selectedValue, onClick = null)
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(option.label, style = MaterialTheme.typography.bodyLarge)
                                option.description?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showOptions = false }) { Text("Cerrar") } },
        )
    }
}

@Composable
private fun TextScaleSetting(fontScale: Float, onFontScaleChange: (Float) -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Tamaño del texto",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${(fontScale * 100).roundToInt()} %",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = fontScale.coerceIn(0.80f, 1.20f),
            onValueChange = { onFontScaleChange(it.coerceIn(0.80f, 1.20f)) },
            valueRange = 0.80f..1.20f,
            steps = 7,
            modifier = Modifier.semantics {
                contentDescription = "Tamaño del texto"
                stateDescription = "${(fontScale * 100).roundToInt()} por ciento"
            },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Pequeño", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Grande", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HiddenFoldersSetting(
    folders: List<MusicFolder>,
    hiddenFolderPaths: Set<String>,
    onHiddenFolderChange: (String, Boolean) -> Unit,
) {
    var showFolders by rememberSaveable { mutableStateOf(false) }
    SettingsRow(
        title = "Carpetas ocultas",
        description = when {
            folders.isEmpty() -> "No hay carpetas disponibles"
            hiddenFolderPaths.isEmpty() -> "Todas las carpetas son visibles"
            hiddenFolderPaths.size == 1 -> "1 carpeta oculta"
            else -> "${hiddenFolderPaths.size} carpetas ocultas"
        },
        onClick = if (folders.isEmpty()) null else ({ showFolders = true }),
        trailing = { if (folders.isNotEmpty()) SettingsChevron() },
    )
    if (showFolders) {
        AlertDialog(
            onDismissRequest = { showFolders = false },
            title = { Text("Carpetas ocultas") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "No aparecerán en biblioteca, favoritos ni playlists.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(folders, key = { it.path }) { folder ->
                            val hidden = folder.path in hiddenFolderPaths
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(value = hidden, role = Role.Checkbox) { onHiddenFolderChange(folder.path, it) }
                                    .heightIn(min = 64.dp)
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = hidden, onCheckedChange = null)
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(folder.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = "${folder.songCount.songCountLabel()} · ${folder.path}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFolders = false }) { Text("Listo") } },
        )
    }
}

@Composable
private fun DjSettings(
    enabled: Boolean,
    mixMode: DjMixMode,
    mixDurationSeconds: Int,
    onEnabledChange: (Boolean) -> Unit,
    onMixModeChange: (DjMixMode) -> Unit,
    onMixDurationChange: (Int) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val legacyMode = if (mixMode == DjMixMode.Expert) DjMixMode.Expert else DjMixMode.Classic
    SettingsToggleRow(
        title = "Modo DJ · Experimental",
        description = "Enlaza canciones antes de que termine la actual.",
        checked = enabled,
        onCheckedChange = onEnabledChange,
    )
    if (enabled) {
        SettingsRow(
            title = "Configurar mezcla",
            description = if (legacyMode == DjMixMode.Expert) {
                "${legacyMode.label} · Hasta $mixDurationSeconds s"
            } else {
                "${legacyMode.label} · $mixDurationSeconds s"
            },
            modifier = Modifier.semantics { stateDescription = if (expanded) "Expandido" else "Contraído" },
            onClick = { expanded = !expanded },
            trailing = { SettingsChevron(expanded) },
        )
        if (expanded) {
            SettingsChoiceRow(
                title = "Tipo de mezcla",
                selectedValue = legacyMode,
                options = listOf(
                    SettingsOption(DjMixMode.Classic, "Clásico", "Mezcla durante un tiempo fijo al final de la canción."),
                    SettingsOption(DjMixMode.Expert, "Experto", "Volumen uniforme, cambio de graves y ritmo detectado."),
                ),
                onSelect = onMixModeChange,
            )
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (legacyMode == DjMixMode.Expert) "Duración máxima" else "Duración de la mezcla",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (legacyMode == DjMixMode.Expert) {
                    Text(
                        "Volumen uniforme y cambio de graves con ayuda del ritmo detectado. La transición dura entre 3 y $mixDurationSeconds s; sin un análisis fiable, usa una mezcla simple.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f), RoundedCornerShape(10.dp))
                        .padding(4.dp)
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(5, 6, 7, 8).forEach { seconds ->
                        val selected = seconds == mixDurationSeconds
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(7.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                Color.Transparent
                            },
                            contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        ) {
                            Box(
                                modifier = Modifier
                                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onMixDurationChange(seconds) })
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = 4.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("$seconds s", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            if (legacyMode == DjMixMode.Expert) {
                SettingsDivider()
                ExpertDjPreviewSetting()
            }
        }
    }
}

@Composable
private fun SettingsPermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    SettingsRow(
        title = title,
        description = if (granted) "Permitido" else description,
        onClick = if (granted) null else onAction,
        onClickLabel = actionLabel,
        trailing = {
            if (granted) {
                Icon(
                    imageVector = MusicIcons.CheckSelected,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                SettingsChevron()
            }
        },
    )
}
