package com.softmusic.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softmusic.app.data.Song
import com.softmusic.app.player.PlaybackMode

/** Layout only: artwork, playback progress and menus retain their owners. */
@Composable
internal fun FullPlayerContent(
    song: Song,
    sourceCaption: String,
    sourceName: String,
    isFavorite: Boolean,
    isPlaying: Boolean,
    playbackMode: PlaybackMode,
    smartShuffleEnabled: Boolean,
    playButtonColor: Color,
    mutedText: Color,
    favoriteTint: Color,
    favoriteScale: Float,
    playPauseScale: Float,
    djStatusLabel: String?,
    timerRemainingMs: Long,
    onClose: () -> Unit,
    onShowSongDetails: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onShowSleepTimer: () -> Unit,
    onShowQueue: () -> Unit,
    artwork: @Composable (Modifier) -> Unit,
    timeline: @Composable () -> Unit,
    addToPlaylist: @Composable () -> Unit,
) {
    val scrollState = rememberScrollState()
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val highPerformanceMode = LocalHighPerformanceMode.current

    // Read the viewport before scrolling introduces unbounded vertical constraints.
    BoxWithConstraints(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        val viewportWidth = if (constraints.hasBoundedWidth) maxWidth else 393.dp
        val viewportHeight = if (constraints.hasBoundedHeight) maxHeight else 760.dp
        val horizontalPadding = if (viewportWidth < 360.dp) 16.dp else 24.dp
        val sideBySide = viewportWidth >= 600.dp && viewportHeight < 560.dp
        val contentWidth = (viewportWidth - horizontalPadding * 2)
            .coerceAtLeast(0.dp)
            .coerceAtMost(if (sideBySide) 960.dp else 440.dp)
        val compact = viewportHeight < 740.dp || fontScale > 1.15f
        val headerGap = if (compact) 8.dp else 16.dp
        val artworkGap = if (compact) 14.dp else 20.dp
        val timelineGap = if (compact) 4.dp else 8.dp
        val transportGap = if (compact) 4.dp else 8.dp
        val auxiliaryGap = if (compact) 6.dp else 12.dp
        val headerHeight = maxOf(48.dp, (32f * fontScale).dp)
        val metadataHeight = (58f * fontScale + 4f).dp
        val timelineHeight = (36f + 16f * fontScale).dp
        // Reserve all primary controls, text growth and a small safety margin before sizing art.
        val reservedHeight = 24.dp + headerHeight + headerGap + artworkGap +
            metadataHeight + timelineGap + timelineHeight + transportGap +
            72.dp + auxiliaryGap + 48.dp
        val maximumArtwork = minOf(380.dp, contentWidth)
        val minimumArtwork = minOf(180.dp, maximumArtwork)
        val portraitArtworkSize = (viewportHeight - reservedHeight)
            .coerceIn(minimumArtwork, maximumArtwork)
        val landscapeGap = 24.dp
        // Four 48 dp targets plus the 72 dp play button require at least 264 dp.
        val landscapeMaximumArtwork = minOf(
            280.dp,
            (contentWidth - 264.dp - landscapeGap).coerceAtLeast(0.dp),
        )
        val landscapeArtworkSize = (viewportHeight - headerHeight - headerGap - 24.dp)
            .coerceIn(minOf(180.dp, landscapeMaximumArtwork), landscapeMaximumArtwork)

        val metadata: @Composable () -> Unit = {
            FullPlayerSongMetadata(
                song = song,
                isFavorite = isFavorite,
                mutedText = mutedText,
                favoriteTint = favoriteTint,
                favoriteScale = if (highPerformanceMode) 1f else favoriteScale,
                onToggleFavorite = onToggleFavorite,
            )
        }
        val transport: @Composable () -> Unit = {
            FullPlayerTransport(
                isPlaying = isPlaying,
                playbackMode = playbackMode,
                smartShuffleEnabled = smartShuffleEnabled,
                playButtonColor = playButtonColor,
                playPauseScale = if (highPerformanceMode) 1f else playPauseScale,
                onTogglePlayPause = onTogglePlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
            )
        }
        val auxiliary: @Composable () -> Unit = {
            FullPlayerAuxiliaryActions(
                djStatusLabel = djStatusLabel,
                timerRemainingMs = timerRemainingMs,
                mutedText = mutedText,
                onShowSleepTimer = onShowSleepTimer,
                onShowQueue = onShowQueue,
                addToPlaylist = addToPlaylist,
            )
        }
        val controls: @Composable () -> Unit = {
            Column(modifier = Modifier.fillMaxWidth()) {
                metadata()
                Spacer(modifier = Modifier.height(timelineGap))
                timeline()
                Spacer(modifier = Modifier.height(transportGap))
                transport()
                Spacer(modifier = Modifier.height(auxiliaryGap))
                auxiliary()
            }
        }

        Column(
            modifier = Modifier
                .widthIn(max = viewportWidth)
                .heightIn(max = viewportHeight)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = horizontalPadding, end = horizontalPadding, top = 8.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = if (sideBySide) 960.dp else 440.dp)
                    .fillMaxWidth()
                    .heightIn(min = if (sideBySide) 0.dp else (viewportHeight - 24.dp).coerceAtLeast(0.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                // Share spare height between the primary sections; overflow still scrolls.
                verticalArrangement = if (sideBySide) Arrangement.Top else Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    FullPlayerHeader(
                        sourceCaption = sourceCaption,
                        sourceName = sourceName,
                        mutedText = mutedText,
                        onClose = onClose,
                        onShowSongDetails = onShowSongDetails,
                    )
                    Spacer(modifier = Modifier.height(headerGap))
                }
                if (sideBySide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(landscapeGap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        artwork(Modifier.size(landscapeArtworkSize))
                        Box(modifier = Modifier.weight(1f)) {
                            controls()
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        artwork(Modifier.size(portraitArtworkSize))
                        Spacer(modifier = Modifier.height(artworkGap))
                    }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        metadata()
                        Spacer(modifier = Modifier.height(timelineGap))
                    }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        timeline()
                        Spacer(modifier = Modifier.height(transportGap))
                    }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        transport()
                        Spacer(modifier = Modifier.height(auxiliaryGap))
                    }
                    auxiliary()
                }
            }
        }
    }
}

@Composable
private fun FullPlayerHeader(
    sourceCaption: String,
    sourceName: String,
    mutedText: Color,
    onClose: () -> Unit,
    onShowSongDetails: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = MusicIcons.ChevronDown,
                contentDescription = "Cerrar reproductor",
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = sourceCaption,
                color = mutedText,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = sourceName,
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onShowSongDetails, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = MusicIcons.MoreVertical,
                contentDescription = "Ver detalles de la canción",
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun FullPlayerSongMetadata(
    song: Song,
    isFavorite: Boolean,
    mutedText: Color,
    favoriteTint: Color,
    favoriteScale: Float,
    onToggleFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                color = mutedText,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.album,
                color = mutedText.copy(alpha = mutedText.alpha * 0.8f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(48.dp).semantics { selected = isFavorite },
        ) {
            Icon(
                imageVector = if (isFavorite) MusicIcons.FavoritesSelected else MusicIcons.FavoriteAdd,
                contentDescription = if (isFavorite) "Quitar de favoritos" else "Agregar a favoritos",
                tint = favoriteTint,
                modifier = Modifier.size(28.dp).graphicsLayer {
                    scaleX = favoriteScale
                    scaleY = favoriteScale
                },
            )
        }
    }
}

@Composable
private fun FullPlayerTransport(
    isPlaying: Boolean,
    playbackMode: PlaybackMode,
    smartShuffleEnabled: Boolean,
    playButtonColor: Color,
    playPauseScale: Float,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
) {
    val shuffleActive = playbackMode == PlaybackMode.Shuffle
    val repeatActive = playbackMode == PlaybackMode.RepeatList || playbackMode == PlaybackMode.RepeatCurrent
    Row(
        modifier = Modifier.fillMaxWidth().height(72.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onToggleShuffle,
            modifier = Modifier.size(48.dp).semantics {
                selected = shuffleActive
                stateDescription = when {
                    smartShuffleEnabled && shuffleActive -> "Aleatorio inteligente"
                    shuffleActive -> "Aleatorio"
                    else -> "Desactivado"
                }
            },
        ) {
            Icon(
                imageVector = when {
                    smartShuffleEnabled && shuffleActive -> MusicIcons.SmartShuffleSelected
                    shuffleActive -> MusicIcons.ShuffleSelected
                    else -> MusicIcons.Shuffle
                },
                contentDescription = if (shuffleActive) "Desactivar aleatorio" else "Activar aleatorio",
                tint = if (smartShuffleEnabled && shuffleActive) Color.Unspecified else if (shuffleActive) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
        IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = MusicIcons.Previous,
                contentDescription = "Anterior",
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
        Button(
            onClick = onTogglePlayPause,
            modifier = Modifier.size(72.dp).graphicsLayer {
                scaleX = playPauseScale
                scaleY = playPauseScale
            },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = playButtonColor, contentColor = Color.White),
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(
                imageVector = if (isPlaying) MusicIcons.Pause else MusicIcons.Play,
                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                modifier = Modifier.size(36.dp),
            )
        }
        IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = MusicIcons.Next,
                contentDescription = "Siguiente",
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
        IconButton(
            onClick = onCycleRepeat,
            modifier = Modifier.size(48.dp).semantics {
                selected = repeatActive
                stateDescription = playbackMode.label
            },
        ) {
            Icon(
                imageVector = when (playbackMode) {
                    PlaybackMode.RepeatCurrent -> MusicIcons.RepeatOneSelected
                    PlaybackMode.RepeatList -> MusicIcons.RepeatAllSelected
                    else -> MusicIcons.RepeatAll
                },
                contentDescription = if (repeatActive) "Cambiar repetición" else "Activar repetición",
                tint = if (repeatActive) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun FullPlayerAuxiliaryActions(
    djStatusLabel: String?,
    timerRemainingMs: Long,
    mutedText: Color,
    onShowSleepTimer: () -> Unit,
    onShowQueue: () -> Unit,
    addToPlaylist: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        horizontalArrangement = if (djStatusLabel == null) Arrangement.SpaceBetween else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (djStatusLabel != null) {
            Row(
                modifier = Modifier.weight(1f).clearAndSetSemantics {
                    contentDescription = "Estado de DJ"
                    stateDescription = djStatusLabel
                },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = MusicIcons.Dj,
                    contentDescription = null,
                    tint = mutedText,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = djStatusLabel,
                    color = mutedText,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val timerActive = timerRemainingMs > 0L
        IconButton(
            onClick = onShowSleepTimer,
            modifier = Modifier.size(48.dp).semantics {
                selected = timerActive
                stateDescription = if (timerActive) formatDuration(timerRemainingMs) else "Desactivado"
            },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Icon(
                    imageVector = if (timerActive) MusicIcons.SleepTimerSelected else MusicIcons.SleepTimer,
                    contentDescription = "Temporizador de apagado",
                    tint = if (timerActive) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.size(24.dp),
                )
                if (timerActive) {
                    Text(
                        text = formatDuration(timerRemainingMs),
                        modifier = Modifier.clearAndSetSemantics { },
                        color = mutedText,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            addToPlaylist()
        }
        IconButton(onClick = onShowQueue, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = MusicIcons.Queue,
                contentDescription = "Ver cola de reproducción",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
