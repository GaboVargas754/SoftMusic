package com.softmusic.app.player

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield

internal enum class BackupLibraryReadiness { PermissionRequired, Loading, Ready, Failed }

internal const val BACKUP_LIBRARY_PERMISSION_MESSAGE = "Concede acceso a la música antes de preparar el respaldo."
internal const val BACKUP_LIBRARY_TIMEOUT_MESSAGE = "La biblioteca tarda demasiado en cargar. Vuelve a intentarlo cuando termine."
internal const val BACKUP_LIBRARY_FAILED_MESSAGE = "No se pudo cargar la biblioteca. Vuelve a cargar la música e inténtalo de nuevo."

/** One deadline covers waiting for SAF's onResume scan and any subsequent snapshot retries. */
internal suspend fun <T : Any> prepareWhenBackupLibraryReady(
    readiness: StateFlow<BackupLibraryReadiness>,
    timeoutMs: Long = 30_000L,
    prepare: suspend () -> T?,
): T = withTimeoutOrNull(timeoutMs) {
    var prepared: T? = null
    while (prepared == null) {
        val state = readiness.first { it != BackupLibraryReadiness.Loading }
        check(state != BackupLibraryReadiness.PermissionRequired) { BACKUP_LIBRARY_PERMISSION_MESSAGE }
        check(state != BackupLibraryReadiness.Failed) { BACKUP_LIBRARY_FAILED_MESSAGE }
        prepared = prepare()
        if (prepared == null) yield()
    }
    prepared
} ?: throw IllegalStateException(BACKUP_LIBRARY_TIMEOUT_MESSAGE)
