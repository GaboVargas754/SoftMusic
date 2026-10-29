package com.softmusic.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
internal fun HomeBrandHeader(
    shuffleActive: Boolean,
    onToggleShuffle: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            AsyncImage(
                model = LocalSeasonalLogo.current,
                contentDescription = "SoftMusic",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .fillMaxWidth()
                    .aspectRatio(3f)
                    .semantics { heading() },
            )
        }
        MusicActionIconButton(
            icon = if (shuffleActive) MusicIcons.ShuffleSelected else MusicIcons.Shuffle,
            contentDescription = "Alternar aleatorio",
            selected = shuffleActive,
            onClick = onToggleShuffle,
        )
        MusicActionIconButton(
            icon = MusicIcons.MoreVertical,
            contentDescription = "Abrir menú de inicio",
            onClick = onOpenMenu,
        )
    }
}
