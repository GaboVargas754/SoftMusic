@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.softmusic.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.softmusic.app.data.Song
import com.softmusic.app.player.PlaybackMode
import com.softmusic.app.player.PlayerUiState

@Composable
internal fun FavoriteLibraryArtwork(
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val shape = RoundedCornerShape(if (large) 12.dp else 10.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFF477E),
                        Color(0xFF7C3AED),
                        Color(0xFF00C2FF),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.34f), Color.Transparent),
                    center = Offset(size.width * 0.22f, size.height * 0.18f),
                    radius = size.maxDimension * 0.72f,
                ),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFD166).copy(alpha = 0.36f), Color.Transparent),
                    center = Offset(size.width * 0.82f, size.height * 0.78f),
                    radius = size.maxDimension * 0.68f,
                ),
            )
        }
        Icon(
            imageVector = MusicIcons.Favorites,
            contentDescription = null,
            modifier = Modifier.size(if (large) 74.dp else 34.dp),
            tint = Color.White,
        )
    }
}

@Composable
internal fun FavoritesLibraryScreen(
    modifier: Modifier = Modifier,
    uiState: PlayerUiState,
    contentBottomPadding: Dp,
    songs: List<Song>,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onPlayList: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onPlayNext: (Song) -> Unit,
    onPlayAtEnd: (Song) -> Unit,
    onAddSongToPlaylist: (String, Long) -> Unit,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
    onBack: () -> Unit,
    onOpenMusic: () -> Unit,
) {
    val backgroundModifier = modifier.background(MaterialTheme.colorScheme.background)

    Box(modifier = backgroundModifier) {
        if (!hasAudioPermission) {
            PermissionPanel(onRequestPermission = onRequestPermission)
            return@Box
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = contentBottomPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                FavoritesDetailHeader(
                    songs = songs,
                    isPlaying = uiState.isPlaying,
                    playbackMode = uiState.playbackMode,
                    onBack = onBack,
                    onPlayList = onPlayList,
                    onPlaybackModeChange = onPlaybackModeChange,
                )
            }

            if (uiState.isLoading) {
                item {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            uiState.errorMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (!uiState.isLoading && songs.isEmpty()) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        EmptyFavorites(onOpenMusic = onOpenMusic)
                    }
                }
            }

            items(
                items = songs,
                key = { song -> song.id },
                contentType = { "song" },
            ) { song ->
                Column(
                    modifier = Modifier
                        .animateItem()
                        .padding(horizontal = 20.dp),
                ) {
                    SongRow(
                        song = song,
                        isCurrent = uiState.currentSong?.id == song.id,
                        isPlaying = uiState.currentSong?.id == song.id && uiState.isPlaying,
                        isFavorite = song.id in uiState.favoriteSongIds,
                        playlists = uiState.playlists,
                        songs = uiState.songs,
                        onClick = { onPlaySong(song) },
                        onToggleFavorite = { onToggleFavorite(song.id) },
                        onPlayNext = { onPlayNext(song) },
                        onPlayAtEnd = { onPlayAtEnd(song) },
                        onAddToPlaylist = { playlistId -> onAddSongToPlaylist(playlistId, song.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoritesDetailHeader(
    songs: List<Song>,
    isPlaying: Boolean,
    playbackMode: PlaybackMode,
    onBack: () -> Unit,
    onPlayList: () -> Unit,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
) {
    CollectionDetailHeader(
        title = "Tus favoritos",
        songs = songs,
        subtitle = "Favoritos",
        playbackMode = playbackMode,
        onBack = onBack,
        onPlay = onPlayList,
        onPlaybackModeChange = onPlaybackModeChange,
        favorite = true,
        playDescription = "Reproducir favoritos",
        isPlaying = isPlaying,
    )
}

@Composable
private fun FavoriteHeroBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFF477E),
                    Color(0xFF7C3AED),
                    Color(0xFF101B4E),
                ),
            ),
        ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFD166).copy(alpha = 0.44f), Color.Transparent),
                    center = Offset(size.width * 0.12f, size.height * 0.18f),
                    radius = size.maxDimension * 0.54f,
                ),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF00C2FF).copy(alpha = 0.42f), Color.Transparent),
                    center = Offset(size.width * 0.9f, size.height * 0.22f),
                    radius = size.maxDimension * 0.62f,
                ),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFF477E).copy(alpha = 0.34f), Color.Transparent),
                    center = Offset(size.width * 0.68f, size.height * 0.82f),
                    radius = size.maxDimension * 0.7f,
                ),
            )
        }
    }
}

@Composable
private fun EmptyFavorites(onOpenMusic: () -> Unit) {
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
                text = "Sin favoritos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Marca canciones con el corazón desde Mi Música para encontrarlas aquí.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenMusic) {
                Text("Ir a Mi Música")
            }
        }
    }
}
