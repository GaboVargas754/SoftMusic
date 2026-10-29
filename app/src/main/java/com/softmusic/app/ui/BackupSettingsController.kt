package com.softmusic.app.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.softmusic.app.backup.BackupDocument
import com.softmusic.app.backup.BackupJsonCodec
import com.softmusic.app.backup.BackupSongMatch
import com.softmusic.app.backup.MAX_BACKUP_BYTES
import com.softmusic.app.player.BackupRestoreResult
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class BackupPreviewState(
    val document: BackupDocument?,
    val matches: List<BackupSongMatch>,
    val applyPreferences: Boolean = false,
    val error: String? = null,
    val needsReview: Boolean = false,
)

internal data class BackupSettingsUi(
    val controller: BackupSettingsController,
    val onCreate: () -> Unit,
    val onOpen: () -> Unit,
)

@Composable
internal fun rememberBackupSettingsUi(): BackupSettingsUi {
    val latestActions = rememberUpdatedState(LocalBackupActions.current)
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    val controller = remember(resolver, scope) {
        BackupSettingsController(resolver, scope) { latestActions.value }
    }
    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
        controller::exportTo,
    )
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
        controller::importFrom,
    )
    return BackupSettingsUi(
        controller = controller,
        onCreate = {
            controller.chooseDocument {
                createLauncher.launch("SoftMusic-${LocalDate.now()}.softmusic.json")
            }
        },
        onOpen = {
            controller.chooseDocument {
                openLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
            }
        },
    )
}

internal class BackupSettingsController(
    private val resolver: ContentResolver,
    private val scope: CoroutineScope,
    private val latestActions: () -> BackupUiActions?,
) {
    val actions: BackupUiActions? get() = latestActions()
    var awaitingDocument by mutableStateOf(false)
        private set
    var operationLabel by mutableStateOf<String?>(null)
        private set
    var isCommitting by mutableStateOf(false)
        private set
    // Equal-looking matches can belong to a newer library revision. Retain the new list itself.
    var preview by mutableStateOf<BackupPreviewState?>(null, referentialEqualityPolicy())
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    private var operationJob: Job? = null
    private var operationGeneration = 0

    val canStart: Boolean
        get() = actions?.libraryReady == true && !awaitingDocument && operationLabel == null && preview == null

    fun chooseDocument(launch: () -> Unit) {
        if (!canStart) return
        notice = null
        awaitingDocument = true
        try {
            launch()
        } catch (_: RuntimeException) {
            awaitingDocument = false
            notice = "No se pudo abrir el selector de archivos. Inténtalo de nuevo."
        }
    }

    fun exportTo(uri: Uri?) {
        awaitingDocument = false
        if (uri == null) return
        val action = readyActions(waitForLibrary = true) ?: return
        runOperation("Creando respaldo…", "No se pudo guardar el respaldo. Revisa el destino y vuelve a intentarlo.") {
            val document = action.createDocument()
            withContext(Dispatchers.IO) {
                val bytes = BackupJsonCodec.encode(document).toByteArray(Charsets.UTF_8)
                currentCoroutineContext().ensureActive()
                val output = resolver.openOutputStream(uri, "wt")
                    ?: throw IOException("No output stream")
                output.use { stream ->
                    var offset = 0
                    while (offset < bytes.size) {
                        currentCoroutineContext().ensureActive()
                        val count = minOf(8_192, bytes.size - offset)
                        stream.write(bytes, offset, count)
                        offset += count
                    }
                    stream.flush()
                }
            }
            // Success is published only after the provider has flushed and closed its stream.
            notice = "Respaldo guardado. Incluye playlists, favoritos y preferencias."
        }
    }

    fun importFrom(uri: Uri?) {
        awaitingDocument = false
        if (uri == null) return
        val action = readyActions(waitForLibrary = true) ?: return
        runOperation("Leyendo y revisando respaldo…", "No se pudo leer el respaldo. Elige un archivo de SoftMusic válido y compatible.") {
            val document = withContext(Dispatchers.IO) { readDocument(uri) }
            val matches = action.preview(document)
            currentCoroutineContext().ensureActive()
            preview = BackupPreviewState(document, matches)
        }
    }

    fun showPending() {
        if (!canStart) return
        val action = readyActions() ?: return
        runOperation("Revisando canciones pendientes…", "No se pudieron revisar las canciones pendientes. Vuelve a intentarlo.") {
            val matches = action.previewPending()
            currentCoroutineContext().ensureActive()
            preview = BackupPreviewState(document = null, matches = matches)
        }
    }

    fun reviewAgain() {
        val previous = preview ?: return
        val action = readyActions() ?: return
        runOperation("Revisando canciones…", "No se pudo actualizar la revisión. Vuelve a intentarlo.") {
            val matches = previous.document?.let { action.preview(it) } ?: action.previewPending()
            currentCoroutineContext().ensureActive()
            preview = previous.copy(matches = matches, error = null, needsReview = false)
        }
    }

    fun setApplyPreferences(value: Boolean) {
        preview = preview?.copy(applyPreferences = value)
    }

    fun confirmRestore() {
        val selected = preview ?: return
        if (selected.needsReview || operationLabel != null) return
        val action = readyActions() ?: return
        runOperation(
            label = if (selected.document == null) "Vinculando canciones…" else "Restaurando copias…",
            fallbackError = "No se pudo completar la restauración. Revisa las canciones de nuevo antes de reintentar.",
            committing = true,
        ) {
            // Keep this exact list: the collection owner rejects a stale library revision.
            val result = selected.document?.let {
                action.restore(it, selected.matches, selected.applyPreferences)
            } ?: action.resolvePending(selected.matches)
            preview = null
            notice = restoreMessage(result, pendingOnly = selected.document == null)
        }
    }

    fun cancelOperation() {
        if (isCommitting) return
        operationGeneration++
        operationJob?.cancel()
        operationJob = null
        operationLabel = null
    }

    fun closePreview() {
        if (operationLabel == null) preview = null
    }

    fun dismissNotice() { notice = null }

    private fun readyActions(waitForLibrary: Boolean = false): BackupUiActions? {
        val action = actions
        // Returning from SAF resumes the library monitor before Compose necessarily observes
        // its refresh. The VM waits for that live load; checking this snapshot would race it.
        if (action == null || (!waitForLibrary && !action.libraryReady)) {
            notice = "Espera a que termine de cargar la biblioteca para usar los respaldos."
            return null
        }
        return action
    }

    private fun runOperation(
        label: String,
        fallbackError: String,
        committing: Boolean = false,
        work: suspend () -> Unit,
    ) {
        if (operationLabel != null) return
        val generation = ++operationGeneration
        notice = null
        operationLabel = label
        isCommitting = committing
        operationJob = scope.launch {
            try {
                work()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == operationGeneration) {
                    val message = backupErrorMessage(error, fallbackError)
                    preview = preview?.copy(error = message, needsReview = true)
                    if (preview == null) notice = message
                }
            } finally {
                if (generation == operationGeneration) {
                    operationLabel = null
                    isCommitting = false
                }
            }
        }
    }

    private suspend fun readDocument(uri: Uri): BackupDocument {
        val input = resolver.openInputStream(uri) ?: throw IOException("No input stream")
        val bytes = input.use { stream ->
            val result = ByteArrayOutputStream(8_192)
            val buffer = ByteArray(8_192)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = stream.read(buffer)
                if (count < 0) break
                if (result.size() + count > MAX_BACKUP_BYTES) throw BackupFileTooLarge()
                result.write(buffer, 0, count)
            }
            result.toByteArray()
        }
        val json = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
        return BackupJsonCodec.decode(json)
    }
}

private class BackupFileTooLarge : IOException()

private fun backupErrorMessage(error: Exception, fallback: String): String = when (error) {
    is BackupFileTooLarge -> "El respaldo supera el límite de 10 MiB. Elige un archivo más pequeño."
    is CharacterCodingException -> "El archivo no contiene texto UTF-8 válido. Elige un respaldo de SoftMusic."
    is SecurityException -> "No se pudo acceder al archivo. Selecciónalo de nuevo y revisa los permisos del proveedor."
    // These messages come from our bounded model/JSON validation, never from a provider exception.
    is IllegalArgumentException -> error.message?.takeIf { message ->
        message.length <= 240 && listOf("El ", "La ", "Una ", "Un ").any(message::startsWith)
    } ?: fallback
    is IllegalStateException -> when {
        error.message?.startsWith("La biblioteca o tus listas cambiaron.") == true ->
            "La biblioteca o tus colecciones cambiaron. Toca «Revisar de nuevo» antes de continuar."
        error.message?.startsWith("No se pudo guardar la restauración.") == true ->
            "No se pudo guardar la restauración. No se cambiaron tus listas ni favoritos."
        error.message?.startsWith("Concede acceso") == true ->
            "Concede acceso a la música y vuelve a revisar el respaldo."
        error.message?.startsWith("Espera a que") == true ->
            "Espera a que termine de cargar la biblioteca o la restauración actual y vuelve a intentarlo."
        else -> fallback
    }
    else -> fallback
}

private fun restoreMessage(result: BackupRestoreResult, pendingOnly: Boolean): String {
    val summary = if (pendingOnly) {
        "Se vincularon ${result.resolvedSongCount} canciones encontradas."
    } else {
        "Se crearon ${result.importedPlaylistCount} copias de playlists y se añadieron ${result.addedFavoriteCount} favoritos."
    }
    return "$summary Quedan ${result.pendingSongCount} canciones pendientes."
}
