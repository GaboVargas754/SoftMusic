package com.softmusic.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.softmusic.app.ui.theme.SoftMusicTheme
import com.softmusic.app.update.AppUpdateInfo

@Preview
@Composable
fun OptionalAppUpdatePreview() {
    AppUpdatePreview(required = false, longNotes = false)
}

@Preview
@Composable
fun RequiredAppUpdatePreview() {
    AppUpdatePreview(required = true, longNotes = true)
}

@Preview
@Composable
fun EmptyNotesAppUpdatePreview() {
    AppUpdatePreview(required = false, longNotes = false, emptyNotes = true)
}

@Composable
private fun AppUpdatePreview(required: Boolean, longNotes: Boolean, emptyNotes: Boolean = false) {
    var visible by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf("Vista previa") }
    val notes = when {
        emptyNotes -> ""
        longNotes -> (1..24).joinToString("\n\n") { "Mejora $it: controles más compactos y una experiencia más clara para tu música." }
        else -> "• Fila de reproducción más compacta.\n• Confirmación al quitar canciones.\n• Nuevo aviso de actualizaciones."
    }
    SoftMusicTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center) {
                Text(result)
                if (visible) {
                    AppUpdateDialog(
                        updateInfo = AppUpdateInfo(30, "3.1.0", notes, "", required),
                        onUpdate = { result = "Descarga solicitada"; visible = false },
                        onDismiss = { result = "Versión omitida"; visible = false },
                    )
                }
            }
        }
    }
}
