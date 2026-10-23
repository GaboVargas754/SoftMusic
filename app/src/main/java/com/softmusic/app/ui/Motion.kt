package com.softmusic.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

// Compose applies Android's animation duration scale to these finite animations.
@Composable
internal fun <T> softMusicMotionSpec(durationMillis: Int = 180): FiniteAnimationSpec<T> =
    if (LocalHighPerformanceMode.current) snap() else tween(
        durationMillis = durationMillis,
        easing = FastOutSlowInEasing,
    )

@Composable
internal fun ScreenEntrance(
    screenKey: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val highPerformanceMode = LocalHighPerformanceMode.current
    val progress = remember(screenKey, highPerformanceMode) {
        Animatable(if (highPerformanceMode) 1f else 0f)
    }
    val animationSpec = softMusicMotionSpec<Float>(220)
    LaunchedEffect(progress) {
        progress.animateTo(1f, animationSpec)
    }
    // Only the active screen is composed, preserving focus and BackHandler ownership.
    Box(
        modifier = modifier.graphicsLayer {
            alpha = 0.35f + 0.65f * progress.value
            translationY = 12.dp.toPx() * (1f - progress.value)
        },
        content = content,
    )
}
