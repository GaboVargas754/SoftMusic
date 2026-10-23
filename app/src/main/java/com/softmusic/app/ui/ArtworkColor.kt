package com.softmusic.app.ui

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.ColorUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ArtworkAppearance(val color: Color, val blurredCover: ImageBitmap) {
    val playbackColor: Color = whitePlaybackColor(color)
}

internal fun whitePlaybackColor(color: Color, minimumContrast: Double = 6.5): Color =
    Color(whitePlaybackArgb(color.toArgb(), minimumContrast))

@Composable
internal fun rememberArtworkAppearance(artworkUri: String?): ArtworkAppearance? {
    val context = LocalContext.current
    // Reset immediately when the artwork changes, including missing or invalid artwork.
    var appearance by remember(artworkUri) { mutableStateOf<ArtworkAppearance?>(null) }
    LaunchedEffect(artworkUri, context) {
        if (artworkUri == null) return@LaunchedEffect
        val result = context.imageLoader.execute(
            ImageRequest.Builder(context)
                .data(artworkUri)
                .size(64, 64)
                .allowHardware(false)
                .build(),
        )
        val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@LaunchedEffect
        appearance = withContext(Dispatchers.Default) {
            val palette = Palette.from(bitmap)
                .clearFilters()
                .generate()
            val dominant = palette.dominantSwatch
            // Prefer substantial colored areas over black outlines or shadows.
            // Grayscale covers retain their dominant neutral tone.
            val colorful = palette.swatches
                .filter { it.hsl[1] >= 0.25f && it.hsl[2] in 0.15f..0.85f }
                .filter { it.population >= (dominant?.population ?: 0) * 0.15f }
                .maxByOrNull { it.population * it.hsl[1] }
            (colorful ?: dominant)?.let { swatch ->
                val hsl = swatch.hsl.copyOf()
                if (hsl[1] >= 0.25f) {
                    hsl[1] = (hsl[1] * 1.25f).coerceAtMost(1f)
                    hsl[2] = hsl[2].coerceIn(0.4f, 0.6f)
                }
                hsl[1] *= 0.8f
                ArtworkAppearance(Color(ColorUtils.HSLToColor(hsl)), blurCover(bitmap))
            }
        }
    }
    return appearance
}

// Blur a small copy off the UI thread, leaving Coil's cached artwork untouched.
// Three box-blur passes give a soft background on every supported Android version.
private fun blurCover(source: Bitmap): ImageBitmap {
    val size = 48
    val scaled = Bitmap.createScaledBitmap(source, size, size, true)
    var pixels = IntArray(size * size)
    scaled.getPixels(pixels, 0, size, 0, 0, size, size)
    if (scaled !== source) scaled.recycle()
    repeat(3) {
        val output = IntArray(pixels.size)
        for (y in 0 until size) for (x in 0 until size) {
            var red = 0
            var green = 0
            var blue = 0
            for (dy in -4..4) for (dx in -4..4) {
                val pixel = pixels[(y + dy).coerceIn(0, size - 1) * size + (x + dx).coerceIn(0, size - 1)]
                red += (pixel shr 16) and 255
                green += (pixel shr 8) and 255
                blue += pixel and 255
            }
            output[y * size + x] = android.graphics.Color.rgb(red / 81, green / 81, blue / 81)
        }
        pixels = output
    }
    for (index in pixels.indices) pixels[index] = whitePlaybackArgb(pixels[index])
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}

internal fun Modifier.artworkBackground(color: Color, artwork: ArtworkAppearance?): Modifier = drawWithCache {
    val playbackBase = whitePlaybackColor(color)
    val saturation = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.8f) })
    val veil = Brush.verticalGradient(
        0f to playbackBase.copy(alpha = 0.25f),
        0.55f to playbackBase.copy(alpha = 0.65f),
        1f to playbackBase.copy(alpha = 0.9f),
    )
    onDrawBehind {
        drawRect(playbackBase)
        artwork?.let {
            drawImage(
                image = it.blurredCover,
                dstSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)),
                alpha = 0.65f,
                colorFilter = saturation,
                filterQuality = FilterQuality.High,
            )
            drawRect(veil)
        }
    }
}
