package com.softmusic.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.softmusic.app.data.MusicFolder
import com.softmusic.app.player.SortMode

@Composable
internal fun ModuleTitle(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(if (compact) 28.dp else 34.dp),
        )
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun PermissionPanel(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
    showRationale: Boolean = false,
    permanentlyDenied: Boolean = false,
    onOpenAppSettings: () -> Unit = {},
) {
    val title = when {
        permanentlyDenied -> "Activa el permiso desde Ajustes"
        showRationale -> "Tu biblioteca necesita este permiso"
        else -> "Permite acceso a tu música"
    }
    val description = when {
        permanentlyDenied -> "Android ya no mostrará la solicitud de permiso. Abre Ajustes, entra en Permisos y permite Música y audio para SoftMusic."
        showRationale -> "Sin acceso a tus archivos de audio no puedo escanear canciones, crear la biblioteca ni reproducir música local."
        else -> "SoftMusic funciona offline leyendo los audios guardados en tu teléfono."
    }
    val primaryAction = if (permanentlyDenied) onOpenAppSettings else onRequestPermission
    val primaryLabel = if (permanentlyDenied) "Abrir ajustes" else "Dar permiso"

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = primaryAction) {
            Text(primaryLabel)
        }
        if (showRationale && !permanentlyDenied) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(onClick = onOpenAppSettings) {
                Text("Abrir ajustes")
            }
        }
    }
}

@Composable
internal fun LibraryTopRow(
    songCount: Int,
    selectedView: MusicLibraryView,
    isPlaying: Boolean,
    folders: List<MusicFolder>,
    selectedFolderPath: String?,
    onOpenHomeMenu: () -> Unit,
    onPlayList: () -> Unit,
    onViewChange: (MusicLibraryView) -> Unit,
    onFolderChange: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeMenuButton(onClick = onOpenHomeMenu)
        MusicLibraryViewSelector(
            selectedView = selectedView,
            onViewChange = onViewChange,
        )
        Spacer(modifier = Modifier.weight(1f))
        CompactLibraryActionButton(
            onClick = onPlayList,
            enabled = songCount > 0,
            selected = songCount > 0,
            badgeVisible = isPlaying,
            icon = MusicIcons.Play,
            contentDescription = if (selectedView == MusicLibraryView.Songs) "Reproducir lista" else "Reproducir grupo",
        )
        FolderMenu(
            folders = folders,
            selectedFolderPath = selectedFolderPath,
            onFolderChange = onFolderChange,
            modifier = Modifier.size(30.dp),
            iconOnly = true,
        )
    }
}

@Composable
private fun CompactLibraryActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    selected: Boolean = false,
    badgeVisible: Boolean = false,
) {
    val backgroundColor = when {
        selected -> MaterialTheme.colorScheme.primary
        enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
    }
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    }

    Box(
        modifier = Modifier.size(30.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(backgroundColor)
                .then(
                    if (enabled) {
                        Modifier.clickable(
                            role = Role.Button,
                            onClickLabel = contentDescription,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(17.dp),
                tint = contentColor,
            )
        }
        if (badgeVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun LibraryActionRow(
    songCount: Int,
    selectedView: MusicLibraryView,
    isPlaying: Boolean,
    folders: List<MusicFolder>,
    selectedFolderPath: String?,
    onRefreshLibrary: () -> Unit,
    onPlayList: () -> Unit,
    onFolderChange: (String?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(
                onClick = onPlayList,
                enabled = songCount > 0,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (songCount > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
            ) {
                Icon(
                    imageVector = MusicIcons.Play,
                    contentDescription = if (selectedView == MusicLibraryView.Songs) "Reproducir lista" else "Reproducir grupo",
                    tint = if (songCount > 0) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary),
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        FolderMenu(
            folders = folders,
            selectedFolderPath = selectedFolderPath,
            onFolderChange = onFolderChange,
            iconOnly = true,
        )
        IconButton(onClick = onRefreshLibrary) {
            Icon(
                imageVector = MusicIcons.Reload,
                contentDescription = "Actualizar biblioteca",
            )
        }
    }
}

@Composable
private fun MusicLibraryViewSelector(
    selectedView: MusicLibraryView,
    onViewChange: (MusicLibraryView) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        MusicLibraryView.entries.forEach { view ->
            val selected = view == selectedView
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Mostrar ${view.label.lowercase()}",
                    ) { onViewChange(view) }
                    .semantics {
                        stateDescription = if (selected) "Seleccionado" else "No seleccionado"
                    },
                shape = RoundedCornerShape(999.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f)
                },
            ) {
                Text(
                    text = view.label,
                    modifier = Modifier
                        .padding(horizontal = 5.dp, vertical = 4.dp),
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun FolderMenu(
    folders: List<MusicFolder>,
    selectedFolderPath: String?,
    onFolderChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
    iconOnly: Boolean = false,
    trigger: (@Composable (onClick: () -> Unit) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedFolder = folders.firstOrNull { it.path == selectedFolderPath }
    Box(modifier = modifier) {
        if (trigger != null) {
            trigger { expanded = true }
        } else if (iconOnly) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Elegir carpeta",
                    ) { expanded = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MusicIcons.Folder,
                    contentDescription = "Carpeta: ${selectedFolder?.name ?: "Todas"}",
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = MusicIcons.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = (selectedFolder?.name ?: "Todas").compactLabel(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Todas las carpetas") },
                onClick = {
                    expanded = false
                    onFolderChange(null)
                },
            )
            folders.forEach { folder ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "${folder.name} (${folder.songCount})",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        expanded = false
                        onFolderChange(folder.path)
                    },
                )
            }
        }
    }
}

@Composable
internal fun SortMenu(
    sortMode: SortMode,
    onSortModeChange: (SortMode) -> Unit,
    modifier: Modifier = Modifier,
    iconOnly: Boolean = false,
    trigger: (@Composable (onClick: () -> Unit) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        if (trigger != null) {
            trigger { expanded = true }
        } else if (iconOnly) {
            OutlinedIconButton(onClick = { expanded = true }) {
                Icon(
                    imageVector = MusicIcons.Sort,
                    contentDescription = "Orden: ${sortMode.label}",
                )
            }
        } else {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = MusicIcons.Sort,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = sortMode.label.compactLabel(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            SortMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    onClick = {
                        expanded = false
                        onSortModeChange(mode)
                    },
                )
            }
        }
    }
}

@Composable
internal fun EmptyLibrary(onRefreshLibrary: () -> Unit) {
    val highPerformanceMode = LocalHighPerformanceMode.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (highPerformanceMode) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            },
        ),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "No encontré canciones",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Agrega archivos MP3, M4A, FLAC, WAV u OGG al almacenamiento del teléfono y actualiza la biblioteca.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRefreshLibrary) {
                Text("Volver a escanear")
            }
        }
    }
}

@Composable
internal fun EmptyHiddenFolders(onOpenSettings: () -> Unit) {
    val highPerformanceMode = LocalHighPerformanceMode.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (highPerformanceMode) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            },
        ),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Todas las carpetas están ocultas",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Muestra al menos una carpeta para ver canciones en la biblioteca.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenSettings) {
                Text("Abrir ajustes")
            }
        }
    }
}

private fun String.compactLabel(maxLength: Int = 15): String {
    val clean = trim()
    if (clean.length <= maxLength) return clean
    if (maxLength <= 3) return clean.take(maxLength)
    return clean.take(maxLength - 3) + "..."
}

