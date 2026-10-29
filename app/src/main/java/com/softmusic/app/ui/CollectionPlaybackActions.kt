package com.softmusic.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.softmusic.app.player.PlaybackMode
import com.softmusic.app.player.PlaylistShuffleMode

@Composable
internal fun RowScope.CollectionPlaybackActions(
    songsAvailable: Boolean,
    playbackMode: PlaybackMode,
    onPlay: () -> Unit,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
    playDescription: String,
    isPlaying: Boolean = false,
    playlistShuffleMode: PlaylistShuffleMode? = null,
    onCyclePlaylistShuffleMode: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val enabled = songsAvailable
    val shuffleActive = playlistShuffleMode?.let { it != PlaylistShuffleMode.Off }
        ?: (playbackMode == PlaybackMode.Shuffle)
    val smartShuffleEnabled = playlistShuffleMode == PlaylistShuffleMode.SmartShuffle
    MusicActionIconButton(
        icon = when {
            smartShuffleEnabled -> MusicIcons.SmartShuffleSelected
            shuffleActive -> MusicIcons.ShuffleSelected
            else -> MusicIcons.Shuffle
        },
        contentDescription = if (playlistShuffleMode != null) "Cambiar aleatorio de esta playlist" else "Alternar aleatorio",
        selected = shuffleActive,
        enabled = enabled || playlistShuffleMode != null,
        preserveIconColors = smartShuffleEnabled,
        stateDescription = when (playlistShuffleMode) {
            PlaylistShuffleMode.Off -> "Desactivado"
            PlaylistShuffleMode.Shuffle -> "Aleatorio"
            PlaylistShuffleMode.SmartShuffle -> "Aleatorio inteligente"
            null -> null
        },
        onClickLabel = when (playlistShuffleMode?.next()) {
            PlaylistShuffleMode.Off -> "Desactivar aleatorio"
            PlaylistShuffleMode.Shuffle -> "Activar aleatorio"
            PlaylistShuffleMode.SmartShuffle -> "Activar aleatorio inteligente"
            null -> null
        },
        onClick = {
            if (playlistShuffleMode != null && onCyclePlaylistShuffleMode != null) {
                onCyclePlaylistShuffleMode()
            } else {
                onPlaybackModeChange(if (shuffleActive) PlaybackMode.Ordered else PlaybackMode.Shuffle)
            }
        },
    )
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
