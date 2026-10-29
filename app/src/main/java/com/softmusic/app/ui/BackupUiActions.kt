package com.softmusic.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.softmusic.app.backup.BackupDocument
import com.softmusic.app.backup.BackupSongMatch
import com.softmusic.app.player.BackupRestoreResult

/** The settings UI never changes collections until the user confirms its exact preview. */
internal data class BackupUiActions(
    val pendingCount: Int,
    val libraryReady: Boolean,
    val createDocument: suspend () -> BackupDocument,
    val preview: suspend (BackupDocument) -> List<BackupSongMatch>,
    val restore: suspend (BackupDocument, List<BackupSongMatch>, Boolean) -> BackupRestoreResult,
    val previewPending: suspend () -> List<BackupSongMatch>,
    val resolvePending: suspend (List<BackupSongMatch>) -> BackupRestoreResult,
)

internal val LocalBackupActions = staticCompositionLocalOf<BackupUiActions?> { null }
