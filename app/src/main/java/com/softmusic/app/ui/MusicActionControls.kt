@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.softmusic.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/** Shared action sizing, colors, accessibility and long-press hints. */
@Composable
internal fun MusicActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    enabled: Boolean = true,
    preserveIconColors: Boolean = false,
    stateDescription: String? = null,
    onClickLabel: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(contentDescription) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.size(48.dp).semantics {
                if (selected != null) {
                    this.selected = selected
                    this.stateDescription = if (selected) "Activado" else "Desactivado"
                }
                if (stateDescription != null) this.stateDescription = stateDescription
                if (onClickLabel != null) semanticsOnClick(label = onClickLabel, action = null)
            },
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = when {
                    preserveIconColors -> Color.Unspecified
                    !enabled -> colors.onSurfaceVariant.copy(alpha = 0.38f)
                    selected == true -> colors.primary
                    else -> colors.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp).alpha(if (preserveIconColors && !enabled) 0.38f else 1f),
            )
        }
    }
}

@Composable
internal fun MusicActionMenuItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp)) },
        onClick = onClick,
        enabled = enabled,
        colors = MenuDefaults.itemColors(
            textColor = if (destructive) colors.error else colors.onSurface,
            leadingIconColor = if (destructive) colors.error else colors.onSurfaceVariant,
        ),
    )
}
