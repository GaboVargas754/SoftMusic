package com.softmusic.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.softmusic.app.data.Song
import com.softmusic.app.player.PlaybackMode

/** Shared artwork for collection cards and detail screens; color extraction belongs to the header. */
@Composable
internal fun CollectionArtwork(
    title: String,
    songs: List<Song>,
    modifier: Modifier = Modifier,
    coverSongId: Long? = null,
    favorite: Boolean = false,
    artist: Boolean = false,
    cornerRadius: Dp = 6.dp,
) {
    val highPerformanceMode = LocalHighPerformanceMode.current
    val artworkSongs = remember(songs, coverSongId) { collectionArtworkSongs(songs, coverSongId) }
    val shape = if (artist) CircleShape else RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .clip(shape)
            .clearAndSetSemantics { contentDescription = "Portada de $title" },
        contentAlignment = Alignment.Center,
    ) {
        when {
            favorite -> CollectionFavoriteArtwork()
            highPerformanceMode || artworkSongs.isEmpty() -> CollectionArtworkPlaceholder(title)
            artworkSongs.size == 1 -> CollectionArtworkTile(artworkSongs.first(), title)
            artworkSongs.size == 2 -> Row(Modifier.fillMaxSize()) {
                artworkSongs.forEach { song ->
                    CollectionArtworkTile(song, title, Modifier.weight(1f).fillMaxSize())
                }
            }
            artworkSongs.size == 3 -> Row(Modifier.fillMaxSize()) {
                CollectionArtworkTile(artworkSongs[0], title, Modifier.weight(1f).fillMaxSize())
                Column(Modifier.weight(1f).fillMaxSize()) {
                    CollectionArtworkTile(artworkSongs[1], title, Modifier.weight(1f).fillMaxWidth())
                    CollectionArtworkTile(artworkSongs[2], title, Modifier.weight(1f).fillMaxWidth())
                }
            }
            else -> Column(Modifier.fillMaxSize()) {
                repeat(2) { row ->
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        repeat(2) { column ->
                            CollectionArtworkTile(
                                song = artworkSongs[row * 2 + column],
                                title = title,
                                modifier = Modifier.weight(1f).fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CollectionDetailHeader(
    title: String,
    songs: List<Song>,
    subtitle: String,
    playbackMode: PlaybackMode,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
    modifier: Modifier = Modifier,
    coverSongId: Long? = null,
    favorite: Boolean = false,
    artist: Boolean = false,
    playDescription: String = "Reproducir colección",
    onChangeCover: (() -> Unit)? = null,
    isPlaying: Boolean = false,
    onDelete: (() -> Unit)? = null,
    leadingActions: @Composable RowScope.() -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val highPerformanceMode = LocalHighPerformanceMode.current
    val artworkSongs = remember(songs, coverSongId) { collectionArtworkSongs(songs, coverSongId) }
    val appearance = rememberArtworkAppearance(
        artworkSongs.firstOrNull()?.artworkUri.takeUnless { highPerformanceMode || favorite },
    )
    val accent = when {
        favorite -> Color(0xFF008D86)
        appearance != null -> appearance.color
        else -> colors.primary
    }
    val darkBackground = colors.background.luminance() < 0.5f
    val topColor = lerp(colors.background, accent, if (darkBackground) 0.48f else 0.24f)
    val middleColor = lerp(colors.background, accent, if (darkBackground) 0.18f else 0.09f)
    val metadata = remember(songs) {
        val count = if (songs.size == 1) "1 canción" else "${songs.size} canciones"
        val duration = songs.sumOf { it.durationMs.coerceAtLeast(0L) }
        "$count • ${collectionDurationLabel(duration)}"
    }
    val collectionLabel = subtitle.ifBlank {
        when {
            favorite -> "Favoritos"
            artist -> "Artista"
            else -> "Playlist"
        }
    }
    val fontScale = LocalDensity.current.fontScale
    val shuffleActive = playbackMode == PlaybackMode.Shuffle
    val enabled = songs.isNotEmpty()
    val hasCustomCover = coverSongId != null && artworkSongs.any { it.id == coverSongId }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to topColor,
                    0.58f to middleColor,
                    1f to colors.background,
                ),
            ),
    ) {
        val compact = maxWidth < 360.dp || fontScale > 1.3f
        val artworkSize = minOf(
            if (compact) 196.dp else 216.dp,
            (maxWidth - 64.dp).coerceAtLeast(0.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 56.dp, bottom = 8.dp),
        ) {
            CollectionArtwork(
                title = title,
                songs = songs,
                coverSongId = coverSongId,
                favorite = favorite,
                artist = artist,
                modifier = Modifier.align(Alignment.CenterHorizontally).size(artworkSize),
                cornerRadius = 6.dp,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = collectionLabel,
                color = colors.onBackground.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                color = colors.onBackground,
                style = MaterialTheme.typography.headlineSmall,
                fontSize = if (compact) 26.sp else 28.sp,
                lineHeight = if (compact) 31.sp else 34.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = metadata,
                color = colors.onBackground.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leadingActions()
                if (onChangeCover != null) {
                    IconButton(onClick = onChangeCover, enabled = enabled) {
                        Icon(
                            imageVector = if (hasCustomCover) MusicIcons.AlbumSelected else MusicIcons.Album,
                            contentDescription = "Cambiar portada",
                            tint = when {
                                !enabled -> colors.onSurfaceVariant.copy(alpha = 0.38f)
                                hasCustomCover -> colors.primary
                                else -> colors.onSurfaceVariant
                            },
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                if (onDelete != null) {
                    var showOptions by remember(title) { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showOptions = true }) {
                            Icon(
                                imageVector = MusicIcons.MoreVertical,
                                contentDescription = "Más opciones de playlist",
                                tint = colors.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(
                            expanded = showOptions,
                            onDismissRequest = { showOptions = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Eliminar playlist", color = colors.error) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = MusicIcons.Delete,
                                        contentDescription = null,
                                        tint = colors.error,
                                    )
                                },
                                onClick = {
                                    showOptions = false
                                    onDelete()
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        onPlaybackModeChange(
                            if (shuffleActive) PlaybackMode.Ordered else PlaybackMode.Shuffle,
                        )
                    },
                    enabled = enabled,
                    modifier = Modifier.semantics {
                        selected = shuffleActive
                        stateDescription = if (shuffleActive) "Activado" else "Desactivado"
                    },
                ) {
                    Icon(
                        imageVector = if (shuffleActive) MusicIcons.ShuffleSelected else MusicIcons.Shuffle,
                        contentDescription = "Alternar aleatorio",
                        tint = when {
                            !enabled -> colors.onSurfaceVariant.copy(alpha = 0.38f)
                            shuffleActive -> colors.primary
                            else -> colors.onSurfaceVariant
                        },
                        modifier = Modifier.size(25.dp),
                    )
                }
                Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    IconButton(
                        onClick = onPlay,
                        enabled = enabled,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(if (enabled) colors.primary else colors.surfaceVariant)
                            .semantics {
                                if (isPlaying) stateDescription = "Reproduciendo"
                            },
                    ) {
                        Icon(
                            imageVector = MusicIcons.Play,
                            contentDescription = playDescription,
                            tint = if (enabled) colors.onPrimary else colors.onSurfaceVariant.copy(alpha = 0.38f),
                            modifier = Modifier.size(34.dp),
                        )
                    }
                    if (isPlaying && enabled) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(colors.primary),
                        )
                    }
                }
            }
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 6.dp),
        ) {
            Icon(
                imageVector = MusicIcons.Back,
                contentDescription = "Volver",
                tint = colors.onBackground,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

private fun collectionArtworkSongs(songs: List<Song>, coverSongId: Long?): List<Song> {
    val customCover = coverSongId?.let { id ->
        songs.firstOrNull { it.id == id && !it.artworkUri.isNullOrBlank() }
    }
    if (customCover != null) return listOf(customCover)
    return songs.asSequence()
        .filter { !it.artworkUri.isNullOrBlank() }
        .distinctBy { it.artworkUri }
        .take(4)
        .toList()
}

@Composable
private fun CollectionArtworkTile(song: Song, title: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        // An unavailable local file leaves a readable cover instead of an empty tile.
        CollectionArtworkPlaceholder(title)
        AsyncImage(
            model = song.artworkUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun CollectionArtworkPlaceholder(title: String) {
    val initials = remember(title) {
        title.trim().split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
    }
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(
            Brush.linearGradient(
                colors = listOf(colors.primaryContainer, lerp(colors.primaryContainer, colors.primary, 0.3f)),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (initials.isNotEmpty()) {
            Text(
                text = initials,
                fontSize = (maxWidth.value * 0.25f).coerceIn(16f, 64f).sp,
                color = colors.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        } else {
            Icon(
                imageVector = MusicIcons.Tracks,
                contentDescription = null,
                tint = colors.onPrimaryContainer,
                modifier = Modifier.fillMaxSize(0.4f),
            )
        }
    }
}

@Composable
private fun CollectionFavoriteArtwork() {
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.linearGradient(
                colors = listOf(Color(0xFF075A73), Color(0xFF008A83), Color(0xFF8DE5CA)),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MusicIcons.FavoritesSelected,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.fillMaxSize(0.46f),
        )
    }
}

private fun collectionDurationLabel(durationMs: Long): String {
    val minutes = durationMs.coerceAtLeast(0L) / 60_000L
    val hours = minutes / 60L
    return if (hours > 0L) "$hours h ${minutes % 60L} min" else "$minutes min"
}
