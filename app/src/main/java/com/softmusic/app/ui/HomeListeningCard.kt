package com.softmusic.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softmusic.app.data.Song
import com.softmusic.app.player.PlaybackProgressState
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun HomeListeningCard(
    song: Song,
    isPlaying: Boolean,
    restoredSessionAvailable: Boolean,
    playbackProgressState: StateFlow<PlaybackProgressState>,
    progressUpdatesEnabled: Boolean,
    onOpenPlayer: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onDismissSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val highPerformanceMode = LocalHighPerformanceMode.current
    val artwork = rememberArtworkAppearance(song.artworkUri.takeUnless { highPerformanceMode })
    val backgroundColor by animateColorAsState(
        targetValue = artwork?.playbackColor ?: colors.surfaceVariant,
        animationSpec = softMusicMotionSpec(360),
        label = "homeListeningArtworkColor",
    )
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = colors.surfaceVariant,
        contentColor = Color.White,
        border = if (highPerformanceMode) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        AnimatedArtworkBackdrop(
            color = backgroundColor,
            artwork = artwork,
            modifier = Modifier.fillMaxWidth(),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val compact = maxWidth < 350.dp || LocalDensity.current.fontScale > 1.25f
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HomeListeningMetadata(
                            song = song,
                            isPlaying = isPlaying,
                            restoredSessionAvailable = restoredSessionAvailable,
                            compact = compact,
                            onOpenPlayer = onOpenPlayer,
                            modifier = Modifier.weight(1f),
                        )
                        if (restoredSessionAvailable) {
                            IconButton(
                                onClick = onDismissSession,
                                modifier = Modifier.size(48.dp).align(Alignment.Top),
                            ) {
                                Icon(
                                    imageVector = MusicIcons.Close,
                                    contentDescription = "Descartar sesión guardada",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    key(song.id) {
                        HomeListeningProgress(
                            songId = song.id,
                            songDurationMs = song.durationMs,
                            playbackProgressState = playbackProgressState,
                            updatesEnabled = progressUpdatesEnabled,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    HomeListeningTransport(
                        isPlaying = isPlaying,
                        restoredSessionAvailable = restoredSessionAvailable,
                        onPrevious = onPrevious,
                        onTogglePlayPause = onTogglePlayPause,
                        onNext = onNext,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 20.dp)
                    .width(24.dp)
                    .height(2.dp)
                    .background(colors.primary, CircleShape),
            )
        }
    }
}

@Composable
private fun HomeListeningMetadata(
    song: Song,
    isPlaying: Boolean,
    restoredSessionAvailable: Boolean,
    compact: Boolean,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClickLabel = "Abrir reproductor", onClick = onOpenPlayer)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedSongArtwork(
            song = song,
            modifier = Modifier.size(if (compact) 72.dp else 80.dp).clearAndSetSemantics { },
            rounded = 10.dp,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        restoredSessionAvailable -> "Continuar reproducción"
                        isPlaying -> "Reproduciendo"
                        else -> "En pausa"
                    },
                    modifier = Modifier.weight(1f),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isPlaying) {
                    Icon(
                        imageVector = MusicIcons.WaveformSelected,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeListeningTransport(
    isPlaying: Boolean,
    restoredSessionAvailable: Boolean,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = MusicIcons.Previous,
                contentDescription = "Anterior",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
        IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = if (isPlaying) MusicIcons.PauseSelected else MusicIcons.PlaySelected,
                contentDescription = when {
                    isPlaying -> "Pausar"
                    restoredSessionAvailable -> "Continuar reproducción"
                    else -> "Reproducir"
                },
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
        IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = MusicIcons.Next,
                contentDescription = "Siguiente",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun HomeListeningProgress(
    songId: Long,
    songDurationMs: Long,
    playbackProgressState: StateFlow<PlaybackProgressState>,
    updatesEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var retainedProgress by remember(songId, playbackProgressState) {
        mutableStateOf(PlaybackProgressState(durationMs = songDurationMs.coerceAtLeast(0L)))
    }
    val playbackProgress = if (updatesEnabled) {
        val currentProgress by playbackProgressState.collectAsStateWithLifecycle()
        SideEffect { retainedProgress = currentProgress }
        currentProgress
    } else {
        retainedProgress
    }
    val durationMs = playbackProgress.durationMs.takeIf { it > 0L } ?: songDurationMs.coerceAtLeast(0L)
    val positionMs = playbackProgress.positionMs.coerceAtLeast(0L)
        .coerceAtMost(durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE)
    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape),
            color = colors.primary,
            trackColor = Color.White.copy(alpha = 0.2f),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = homeListeningDurationLabel(positionMs),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 10.sp,
                lineHeight = 12.sp,
            )
            Text(
                text = homeListeningDurationLabel(durationMs),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 10.sp,
                lineHeight = 12.sp,
            )
        }
    }
}

private fun homeListeningDurationLabel(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1_000L
    val seconds = (totalSeconds % 60L).toString().padStart(2, '0')
    val totalMinutes = totalSeconds / 60L
    return if (totalMinutes >= 60L) {
        "${totalMinutes / 60L}:${(totalMinutes % 60L).toString().padStart(2, '0')}:$seconds"
    } else {
        "$totalMinutes:$seconds"
    }
}
