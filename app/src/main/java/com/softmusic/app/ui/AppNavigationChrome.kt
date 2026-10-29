package com.softmusic.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

@Composable
internal fun SpotifyBottomNavigation(
    blurState: HazeState,
    selectedDestination: LibraryDestination,
    onDestinationChange: (LibraryDestination) -> Unit,
) {
    val selectedNavigationDestination = if (selectedDestination == LibraryDestination.Playlists) {
        LibraryDestination.Library
    } else {
        selectedDestination
    }
    val destinations = listOf(
        LibraryDestination.Music,
        LibraryDestination.Search,
        LibraryDestination.Library,
    )
    val surface = MaterialTheme.colorScheme.surface
    val highPerformanceMode = LocalHighPerformanceMode.current
    NavigationBar(
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(56.dp).hazeChild(blurState) {
            backgroundColor = surface
            tints = listOf(HazeTint(surface.copy(alpha = 0.44f)))
            blurRadius = 20.dp
            noiseFactor = 0.025f
            blurEnabled = !highPerformanceMode
        },
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0),
    ) {
        destinations.forEach { destination ->
            val iconScale by animateFloatAsState(
                targetValue = if (selectedNavigationDestination == destination) 1.12f else 1f,
                animationSpec = softMusicMotionSpec(),
                label = "navigationIconScale",
            )
            NavigationBarItem(
                selected = selectedNavigationDestination == destination,
                onClick = { onDestinationChange(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon(selected = selectedNavigationDestination == destination),
                        contentDescription = destination.label(),
                        modifier = Modifier.size(20.dp).graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                    )
                },
                label = {
                    Text(
                        text = destination.label(),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

private fun LibraryDestination.label(): String = when (this) {
    LibraryDestination.Music -> "Inicio"
    LibraryDestination.Browse -> "Tu música"
    LibraryDestination.Search -> "Buscar"
    LibraryDestination.Library -> "Biblioteca"
    LibraryDestination.Favorites -> "Favoritos"
    LibraryDestination.Playlists -> "Playlists"
    LibraryDestination.History -> "Historial"
    LibraryDestination.Settings -> "Ajustes"
}

@Composable
private fun LibraryDestination.icon(selected: Boolean = false): ImageVector = when (this) {
    LibraryDestination.Music -> if (selected) MusicIcons.HomeSelected else MusicIcons.Home
    LibraryDestination.Browse -> if (selected) MusicIcons.LibrarySelected else MusicIcons.Library
    LibraryDestination.Search -> if (selected) MusicIcons.SearchSelected else MusicIcons.Search
    LibraryDestination.Library -> if (selected) MusicIcons.LibrarySelected else MusicIcons.Library
    LibraryDestination.Favorites -> if (selected) MusicIcons.FavoritesSelected else MusicIcons.Favorites
    LibraryDestination.Playlists -> if (selected) MusicIcons.PlaylistSelected else MusicIcons.Playlist
    LibraryDestination.History -> if (selected) MusicIcons.RecentSelected else MusicIcons.Recent
    LibraryDestination.Settings -> if (selected) MusicIcons.SettingsSelected else MusicIcons.Settings
}

@Composable
internal fun HomeMenuButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .semantics { contentDescription = "Abrir menú de inicio" }
            .clickable(role = Role.Button, onClickLabel = "Abrir menú de inicio", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = LocalSeasonalIcon.current,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(44.dp),
        )
    }
}

@Composable
internal fun HomeSideDrawer(
    onOpenUpdates: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloseApp: () -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier
            .fillMaxHeight()
            .width(264.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MusicIcons.ListView,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column {
                    Text(
                        text = "Inicio",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "Menú principal",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HomeDrawerItem(label = "Actualizaciones", onClick = onOpenUpdates)
            HomeDrawerItem(label = "Notificaciones", onClick = onOpenNotifications)
            HomeDrawerItem(label = "Ajustes", onClick = onOpenSettings)
            Spacer(modifier = Modifier.weight(1f))
            HomeDrawerItem(label = "Cerrar aplicación", onClick = onCloseApp)
        }
    }
}

@Composable
private fun HomeDrawerItem(
    label: String,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = {
            Text(
                text = label,
                fontWeight = FontWeight.SemiBold,
            )
        },
        selected = false,
        onClick = onClick,
    )
}
