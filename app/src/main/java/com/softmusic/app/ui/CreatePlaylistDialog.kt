package com.softmusic.app.ui

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.softmusic.app.data.MAX_PLAYLIST_NAME_LENGTH

@Composable
internal fun CreatePlaylistDialog(
    existingPlaylistNames: List<String>,
    onCreatePlaylist: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var playlistName by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val suggestion = suggestedPlaylistName(existingPlaylistNames)
        mutableStateOf(TextFieldValue(suggestion, selection = TextRange(0, suggestion.length)))
    }
    var submissionError by rememberSaveable { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val validationError = remember(playlistName.text, existingPlaylistNames) {
        playlistNameValidationError(playlistName.text, existingPlaylistNames)
    }
    val errorMessage = validationError ?: submissionError
    val focusRequester = remember { FocusRequester() }
    val colors = MaterialTheme.colorScheme
    val background = remember {
        Brush.verticalGradient(
            0f to Color(0xFF343C3D),
            0.32f to Color(0xFF202829),
            0.68f to Color(0xFF0B1112),
            1f to Color(0xFF000000),
        )
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            // Compose 1.7 respects resized constraints here; our own window sets MATCH_PARENT.
            usePlatformDefaultWidth = true,
            decorFitsSystemWindows = true,
            dismissOnClickOutside = false,
        ),
    ) {
        val keyboardController = LocalSoftwareKeyboardController.current
        val createPlaylist = {
            if (!submitting && validationError == null) {
                submitting = true
                if (onCreatePlaylist(playlistName.text.trim())) {
                    keyboardController?.hide()
                    onDismiss()
                } else {
                    submitting = false
                    submissionError = "No se pudo crear la playlist. Inténtalo de nuevo."
                }
            }
        }
        val dismiss = {
            keyboardController?.hide()
            onDismiss()
        }
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(dialogWindow) {
            // This is the dialog's own window; the player's window remains untouched.
            dialogWindow?.apply {
                setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                @Suppress("DEPRECATION")
                run {
                    statusBarColor = android.graphics.Color.TRANSPARENT
                    navigationBarColor = android.graphics.Color.TRANSPARENT
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    isStatusBarContrastEnforced = false
                    isNavigationBarContrastEnforced = false
                }
                WindowCompat.getInsetsController(this, decorView).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
            withFrameNanos { }
            focusRequester.requestFocus()
            withFrameNanos { }
            keyboardController?.show()
        }

        // Keep the backdrop opaque even while the content enters.
        Box(Modifier.fillMaxSize().background(background)) {
            ScreenEntrance(screenKey = "create-playlist", modifier = Modifier.fillMaxSize()) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        // Union uses the larger bottom inset, never IME + navigation bar.
                        .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
                        .semantics { paneTitle = "Crear playlist" },
                    contentAlignment = Alignment.Center,
                ) {
                    val compact = maxHeight < 500.dp
                    val veryCompact = maxHeight < 240.dp
                    val horizontalPadding = if (maxWidth < 360.dp) 24.dp else 32.dp
                    Column(
                        modifier = Modifier
                            .widthIn(max = 480.dp)
                            .fillMaxSize()
                            .padding(
                                horizontal = horizontalPadding,
                                vertical = if (veryCompact) 4.dp else if (compact) 16.dp else 28.dp,
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(
                            space = 0.dp,
                            alignment = BiasAlignment.Vertical(if (compact) 0f else -0.14f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                // The form receives all space left after error and actions are measured.
                                .weight(1f, fill = false)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "Ponle nombre a tu playlist",
                                modifier = Modifier.fillMaxWidth().semantics { heading() },
                                color = Color.White,
                                fontSize = 22.sp,
                                lineHeight = 29.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(if (veryCompact) 16.dp else if (compact) 24.dp else 52.dp))
                            val underlineColor = if (errorMessage == null) {
                                Color.White.copy(alpha = 0.65f)
                            } else {
                                colors.error
                            }
                            BasicTextField(
                                value = playlistName,
                                onValueChange = { value ->
                                    playlistName = value.copy(
                                        text = value.text
                                            .replace('\n', ' ')
                                            .replace('\r', ' ')
                                            .take(MAX_PLAYLIST_NAME_LENGTH),
                                    )
                                    submissionError = null
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = if (veryCompact) 40.dp else 58.dp)
                                    .focusRequester(focusRequester)
                                    .semantics {
                                        contentDescription = "Nombre de la playlist"
                                        errorMessage?.let { error(it) }
                                    }
                                    .drawBehind {
                                        val strokeWidth = 1.dp.toPx()
                                        drawLine(
                                            color = underlineColor,
                                            start = Offset(0f, size.height - strokeWidth / 2),
                                            end = Offset(size.width, size.height - strokeWidth / 2),
                                            strokeWidth = strokeWidth,
                                        )
                                    }
                                    .padding(vertical = if (veryCompact) 4.dp else 10.dp),
                                enabled = !submitting,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.headlineMedium.copy(
                                    color = Color.White,
                                    fontSize = if (veryCompact) 24.sp else 30.sp,
                                    lineHeight = if (veryCompact) 30.sp else 38.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                ),
                                cursorBrush = SolidColor(colors.primary),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(onDone = { createPlaylist() }),
                            )
                        }
                        // Validation must stay visible even when the form needs to scroll.
                        if (!veryCompact || errorMessage != null) {
                            Box(
                                Modifier.fillMaxWidth().heightIn(min = 30.dp).padding(top = 8.dp),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                if (errorMessage != null) {
                                    Text(
                                        text = errorMessage,
                                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                        color = colors.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(if (veryCompact) 8.dp else if (compact) 24.dp else 40.dp))
                        // Actions stay outside the scroll viewport when IME or large text reduces space.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            OutlinedButton(
                                onClick = dismiss,
                                modifier = Modifier.weight(1f).heightIn(min = if (veryCompact) 48.dp else 52.dp),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                contentPadding = PaddingValues(
                                    horizontal = 12.dp,
                                    vertical = if (veryCompact) 10.dp else 14.dp,
                                ),
                            ) {
                                Text("Cancelar", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                            Button(
                                onClick = { createPlaylist() },
                                modifier = Modifier.weight(1f).heightIn(min = if (veryCompact) 48.dp else 52.dp),
                                enabled = validationError == null && !submitting,
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primary,
                                    contentColor = Color.Black,
                                    disabledContainerColor = colors.primary.copy(alpha = 0.28f),
                                    disabledContentColor = Color.White.copy(alpha = 0.6f),
                                ),
                                contentPadding = PaddingValues(
                                    horizontal = 12.dp,
                                    vertical = if (veryCompact) 10.dp else 14.dp,
                                ),
                            ) {
                                Text("Crear", fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun suggestedPlaylistName(existingPlaylistNames: List<String>): String {
    val existingNames = existingPlaylistNames.map { it.normalizedSearchText() }.toSet()
    var number = existingPlaylistNames.size + 1
    while ("Mi playlist #$number".normalizedSearchText() in existingNames) number++
    return "Mi playlist #$number"
}

internal fun playlistNameValidationError(name: String, existingPlaylistNames: List<String>): String? {
    val cleanName = name.trim()
    return when {
        cleanName.isBlank() -> "Escribe un nombre para la playlist"
        cleanName.length > MAX_PLAYLIST_NAME_LENGTH -> "El nombre es demasiado largo"
        existingPlaylistNames.any { it.normalizedSearchText() == cleanName.normalizedSearchText() } ->
            "Ya existe una playlist con ese nombre"
        else -> null
    }
}
