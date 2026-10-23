package com.softmusic.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.softmusic.app.data.Song

@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun AnimatedSongArtwork(
    song: Song,
    modifier: Modifier = Modifier,
    rounded: Dp = 14.dp,
) {
    if (LocalHighPerformanceMode.current) {
        Artwork(song = song, modifier = modifier, rounded = rounded)
        return
    }

    val transition = updateTransition(targetState = song, label = "Song artwork")
    transition.Crossfade(
        modifier = modifier,
        animationSpec = softMusicMotionSpec(durationMillis = 280),
        contentKey = { it.artworkUri ?: it.id },
    ) { artworkSong ->
        Artwork(
            song = artworkSong,
            modifier = Modifier.fillMaxSize(),
            rounded = rounded,
        )
    }
}

@Composable
internal fun AnimatedArtworkBackdrop(
    color: Color,
    artwork: ArtworkAppearance?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val highPerformanceMode = LocalHighPerformanceMode.current
    Box(modifier = modifier) {
        if (highPerformanceMode) {
            Box(
                modifier = Modifier.matchParentSize().artworkBackground(color, artwork),
            )
        } else {
            // The caller can animate color without restarting the artwork transition.
            Crossfade(
                targetState = artwork,
                modifier = Modifier.matchParentSize(),
                animationSpec = softMusicMotionSpec(durationMillis = 360),
                label = "Artwork backdrop",
            ) { frameArtwork ->
                Box(
                    modifier = Modifier.fillMaxSize()
                        .artworkBackground(color, frameArtwork),
                )
            }
        }
        content()
    }
}
