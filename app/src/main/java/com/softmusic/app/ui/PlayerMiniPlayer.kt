package com.softmusic.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softmusic.app.player.PlaybackProgressState
import com.softmusic.app.player.PlayerUiState
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun MiniPlayer(
    uiState: PlayerUiState,
    playbackProgressState: StateFlow<PlaybackProgressState>,
    progressUpdatesEnabled: Boolean = true,
    onOpenPlayer: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val song = uiState.currentSong ?: return
    val highPerformanceMode = LocalHighPerformanceMode.current
    val artwork = rememberArtworkAppearance(
        song.artworkUri.takeUnless { highPerformanceMode },
    )
    val artworkColor = artwork?.playbackColor
    val miniPlayerColor by animateColorAsState(
        targetValue = whitePlaybackColor(artworkColor?.let {
            lerp(MaterialTheme.colorScheme.surfaceVariant, it, 0.9f)
        } ?: MaterialTheme.colorScheme.surfaceVariant),
        animationSpec = softMusicMotionSpec(360),
        label = "miniPlayerArtworkColor",
    )
    val miniPlayerContentColor = Color.White
    val playPauseScale by animateFloatAsState(
        targetValue = if (uiState.isPlaying) 1.12f else 1f,
        animationSpec = softMusicMotionSpec(),
        label = "miniPlayerPlayPauseScale",
    )
    Surface(
        color = MaterialTheme.colorScheme.background,
        contentColor = miniPlayerContentColor,
        shadowElevation = 0.dp,
    ) {
        AnimatedArtworkBackdrop(
            color = miniPlayerColor,
            artwork = artwork,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(14.dp)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .clickable(onClick = onOpenPlayer)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedSongArtwork(
                    song = song,
                    modifier = Modifier.size(46.dp),
                    rounded = 5.dp,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = miniPlayerContentColor.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onPrevious) {
                    Icon(
                        imageVector = MusicIcons.Previous,
                        contentDescription = "Anterior",
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(onClick = onTogglePlayPause) {
                    Icon(
                        imageVector = if (uiState.isPlaying) MusicIcons.Pause else MusicIcons.Play,
                        contentDescription = if (uiState.isPlaying) "Pausar" else "Reproducir",
                        modifier = Modifier
                            .size(26.dp)
                            .graphicsLayer {
                                scaleX = playPauseScale
                                scaleY = playPauseScale
                            },
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(
                        imageVector = MusicIcons.Next,
                        contentDescription = "Siguiente",
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            MiniPlayerProgress(
                playbackProgressState = playbackProgressState,
                updatesEnabled = progressUpdatesEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
            }
        }
    }
}

@Composable
private fun MiniPlayerProgress(
    playbackProgressState: StateFlow<PlaybackProgressState>,
    updatesEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var retainedProgress by remember(playbackProgressState) {
        mutableStateOf(playbackProgressState.value)
    }
    val playbackProgress = if (updatesEnabled) {
        val currentProgress by playbackProgressState.collectAsStateWithLifecycle()
        SideEffect { retainedProgress = currentProgress }
        currentProgress
    } else {
        retainedProgress
    }
    PlaybackProgress(
        positionMs = playbackProgress.positionMs,
        durationMs = playbackProgress.durationMs,
        modifier = modifier,
    )
}

@Composable
private fun PlaybackProgress(
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    val progress = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}
