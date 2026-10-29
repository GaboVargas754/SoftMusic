package com.softmusic.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.softmusic.app.data.Song
import kotlin.math.min

private const val FULL_DISC_ROTATION_DEGREES = 360f

private const val DISC_ROTATION_DURATION_MS = 5_500f

private const val VINYL_VIEWBOX_SIZE = 300f

@Composable
internal fun SpinningDiscArtwork(
    song: Song,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    var rotation by remember(song.id) { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying, song.id) {
        if (!isPlaying) return@LaunchedEffect
        var lastFrameNanos = withFrameNanos { it }
        while (true) {
            val frameNanos = withFrameNanos { it }
            val deltaMillis = (frameNanos - lastFrameNanos).coerceAtLeast(0L) / 1_000_000f
            lastFrameNanos = frameNanos
            rotation = (rotation + deltaMillis * FULL_DISC_ROTATION_DEGREES / DISC_ROTATION_DURATION_MS) % FULL_DISC_ROTATION_DEGREES
        }
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .graphicsLayer { rotationZ = rotation },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawVinylDisc()
        }
        Box(
            modifier = Modifier
                .fillMaxSize(0.39f)
                .clip(CircleShape)
                .background(Color.White)
                .border(4.dp, Color(0xFF333333), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (song.artworkUri != null) {
                AsyncImage(
                    model = song.artworkUri,
                    contentDescription = "Caratula de ${song.title}",
                    modifier = Modifier
                        .fillMaxSize(0.90f)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.90f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = song.title.firstOrNull()?.uppercaseChar()?.toString() ?: "S",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize(0.053f)
                .clip(CircleShape)
                .background(Color(0xFF111111)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(0.38f)
                    .clip(CircleShape)
                    .background(Color(0xFF555555)),
            )
        }
    }
}

private fun DrawScope.drawVinylDisc() {
    val diameter = min(size.width, size.height)
    val scale = diameter / VINYL_VIEWBOX_SIZE
    val origin = Offset(
        x = (size.width - diameter) / 2f,
        y = (size.height - diameter) / 2f,
    )
    fun point(x: Float, y: Float) = Offset(origin.x + x * scale, origin.y + y * scale)
    fun radius(value: Float) = value * scale
    val center = point(150f, 150f)
    val shineBrush = Brush.linearGradient(
        colorStops = arrayOf(
            0f to Color.White.copy(alpha = 0.45f),
            0.40f to Color.White.copy(alpha = 0.05f),
            1f to Color.White.copy(alpha = 0f),
        ),
        start = origin,
        end = Offset(origin.x + diameter, origin.y + diameter),
    )

    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color(0xFF222222),
                0.55f to Color(0xFF050505),
                1f to Color.Black,
            ),
            center = point(150f, 135f),
            radius = radius(165f),
        ),
        radius = radius(140f),
        center = center,
    )
    drawCircle(
        color = Color(0xFF444444),
        radius = radius(138f),
        center = center,
        style = Stroke(width = radius(2f)),
    )
    drawCircle(
        color = Color(0xFF111111),
        radius = radius(126f),
        center = center,
        style = Stroke(width = radius(4f)),
    )
    listOf(118f, 112f, 106f, 100f, 94f, 88f, 82f, 76f, 70f).forEach { grooveRadius ->
        drawCircle(
            color = Color(0xFF1D1D1D),
            radius = radius(grooveRadius),
            center = center,
            style = Stroke(width = radius(1f)),
        )
    }

    val topShine = Path().apply {
        moveTo(point(70f, 80f).x, point(70f, 80f).y)
        cubicTo(
            point(105f, 35f).x,
            point(105f, 35f).y,
            point(185f, 28f).x,
            point(185f, 28f).y,
            point(230f, 75f).x,
            point(230f, 75f).y,
        )
    }
    drawPath(
        path = topShine,
        brush = shineBrush,
        alpha = 0.75f,
        style = Stroke(width = radius(18f), cap = StrokeCap.Round),
    )

    val sideShine = Path().apply {
        moveTo(point(205f, 220f).x, point(205f, 220f).y)
        cubicTo(
            point(230f, 195f).x,
            point(230f, 195f).y,
            point(238f, 160f).x,
            point(238f, 160f).y,
            point(225f, 125f).x,
            point(225f, 125f).y,
        )
    }
    drawPath(
        path = sideShine,
        brush = shineBrush,
        alpha = 0.45f,
        style = Stroke(width = radius(14f), cap = StrokeCap.Round),
    )
}

@Composable
internal fun Artwork(
    song: Song,
    modifier: Modifier = Modifier,
    rounded: androidx.compose.ui.unit.Dp = 14.dp,
) {
    val highPerformanceMode = LocalHighPerformanceMode.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(rounded))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!highPerformanceMode && song.artworkUri != null) {
            AsyncImage(
                model = song.artworkUri,
                contentDescription = "Caratula de ${song.title}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = song.title.firstOrNull()?.uppercaseChar()?.toString() ?: "S",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
