package com.softmusic.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { if (!isRefreshing) onRefresh() },
        state = state,
        modifier = Modifier.fillMaxSize().semantics {
            customActions = listOf(
                CustomAccessibilityAction("Actualizar biblioteca") {
                    if (isRefreshing) false else {
                        onRefresh()
                        true
                    }
                },
            )
        },
        indicator = {
            if (isRefreshing || state.distanceFraction > 0f) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 3.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = when {
                                isRefreshing -> "Actualizando biblioteca…"
                                state.distanceFraction > 1f -> "Suelte para actualizar"
                                else -> "Deslice hacia abajo para actualizar"
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                        if (isRefreshing) {
                            LinearProgressIndicator(modifier = Modifier.padding(top = 6.dp))
                        } else {
                            Text(
                                text = "Desplace hacia arriba para regresar",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
    ) {
        content(Modifier.fillMaxSize().graphicsLayer {
            translationY = state.distanceFraction.coerceIn(0f, 1.5f) * 80.dp.toPx()
        })
    }
}
