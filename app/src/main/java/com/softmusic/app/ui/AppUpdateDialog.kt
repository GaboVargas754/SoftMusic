package com.softmusic.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.softmusic.app.BuildConfig
import com.softmusic.app.update.AppUpdateInfo

@Composable
internal fun AppUpdateDialog(
    updateInfo: AppUpdateInfo,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val releaseNotes = updateInfo.releaseNotes.trim().takeIf { it.isNotEmpty() }
    AlertDialog(
        onDismissRequest = { if (!updateInfo.required) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !updateInfo.required,
            dismissOnClickOutside = !updateInfo.required,
        ),
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        icon = {
            Image(
                painter = painterResource(LocalSeasonalIcon.current),
                contentDescription = null,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
            )
        },
        title = {
            Text(
                text = "Actualización disponible",
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        UpdateVersion("Instalada", BuildConfig.VERSION_NAME, false, Modifier.weight(1f))
                        UpdateVersion("Nueva", updateInfo.versionName, true, Modifier.weight(1f))
                    }
                }
                if (updateInfo.required) {
                    Text(
                        text = "Esta actualización es necesaria para continuar.",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                if (releaseNotes != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Novedades",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = releaseNotes,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onUpdate, shape = RoundedCornerShape(12.dp)) {
                Icon(imageVector = MusicIcons.Reload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Actualizar", fontSize = 13.sp)
            }
        },
        dismissButton = if (updateInfo.required) null else {
            { TextButton(onClick = onDismiss) { Text("Omitir versión", fontSize = 13.sp) } }
        },
    )
}

@Composable
private fun UpdateVersion(label: String, version: String, highlighted: Boolean, modifier: Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 14.sp,
        )
        Text(
            text = version,
            color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
