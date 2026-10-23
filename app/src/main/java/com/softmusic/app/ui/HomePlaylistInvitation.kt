package com.softmusic.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun HomePlaylistInvitation(
    onCreatePlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val highPerformanceMode = LocalHighPerformanceMode.current
    val decoration = if (highPerformanceMode) Modifier else {
        Modifier.drawWithCache {
            val glow = Brush.radialGradient(
                colors = listOf(colors.primary.copy(alpha = 0.065f), Color.Transparent),
                center = Offset.Zero,
                radius = (size.width * 0.85f).coerceAtLeast(1f),
            )
            onDrawBehind { drawRect(glow) }
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.onSurface.copy(alpha = 0.065f)),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().then(decoration)) {
            val compact = maxWidth < 350.dp || LocalDensity.current.fontScale > 1.25f
            if (compact) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    HomePlaylistInvitationArtwork(
                        highPerformanceMode = highPerformanceMode,
                        modifier = Modifier.size(64.dp),
                    )
                    HomePlaylistInvitationContent(onCreatePlaylist = onCreatePlaylist, fullWidthButton = true)
                }
            } else {
                Row(
                    modifier = Modifier.padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HomePlaylistInvitationArtwork(
                        highPerformanceMode = highPerformanceMode,
                        modifier = Modifier.size(76.dp),
                    )
                    HomePlaylistInvitationContent(
                        onCreatePlaylist = onCreatePlaylist,
                        fullWidthButton = false,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomePlaylistInvitationContent(
    onCreatePlaylist: () -> Unit,
    fullWidthButton: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = "Dale un lugar a tus canciones",
            color = colors.onSurface,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Crea tu primera playlist en tu biblioteca.",
            color = colors.onSurfaceVariant,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Button(
            onClick = onCreatePlaylist,
            modifier = Modifier
                .padding(top = 3.dp)
                .then(if (fullWidthButton) Modifier.fillMaxWidth() else Modifier),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = MusicIcons.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Crear playlist", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HomePlaylistInvitationArtwork(
    highPerformanceMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val frontColor = lerp(colors.surface, colors.onSurface, 0.035f)
    val borderColor = colors.onSurface.copy(alpha = 0.075f)
    Box(modifier = modifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        if (!highPerformanceMode) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val inset = 7.dp.toPx()
                val radius = CornerRadius(13.dp.toPx())
                val cardSize = Size((size.width - inset).coerceAtLeast(0f), (size.height - inset).coerceAtLeast(0f))
                val stroke = Stroke(width = 1.dp.toPx())
                drawRoundRect(colors.onSurface.copy(alpha = 0.025f), size = cardSize, cornerRadius = radius)
                drawRoundRect(borderColor, size = cardSize, cornerRadius = radius, style = stroke)
                drawRoundRect(frontColor, topLeft = Offset(inset, inset), size = cardSize, cornerRadius = radius)
                drawRoundRect(borderColor, topLeft = Offset(inset, inset), size = cardSize, cornerRadius = radius, style = stroke)
                drawLine(
                    color = colors.primary.copy(alpha = 0.65f),
                    start = Offset(inset + 9.dp.toPx(), inset + 9.dp.toPx()),
                    end = Offset(inset + 23.dp.toPx(), inset + 9.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Icon(
            imageVector = MusicIcons.PlaylistSelected,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(42.dp),
        )
    }
}
