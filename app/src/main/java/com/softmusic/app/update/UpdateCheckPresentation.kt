package com.softmusic.app.update

internal data class UpdateCheckPresentation(
    val availableUpdate: AppUpdateInfo? = null,
    val status: String? = null,
)

internal fun AppUpdateCheckResult.presentation(manual: Boolean, skippedVersionCode: Long): UpdateCheckPresentation = when (this) {
    is AppUpdateCheckResult.Available -> UpdateCheckPresentation(
        availableUpdate = info.takeIf { manual || it.required || it.versionCode != skippedVersionCode },
    )
    AppUpdateCheckResult.UpToDate -> UpdateCheckPresentation(
        status = "No hay actualizaciones disponibles.".takeIf { manual },
    )
    is AppUpdateCheckResult.Failure -> UpdateCheckPresentation(
        status = if (manual) reason.statusMessage() else null,
    )
}

private fun AppUpdateFailureReason.statusMessage(): String = when (this) {
    AppUpdateFailureReason.Timeout -> "La búsqueda de actualizaciones tardó demasiado. Inténtalo de nuevo."
    AppUpdateFailureReason.Network -> "No se pudo buscar actualizaciones. Revisa tu conexión e inténtalo de nuevo."
    is AppUpdateFailureReason.Http -> "El servidor de actualizaciones no pudo completar la consulta. Inténtalo más tarde."
    AppUpdateFailureReason.InvalidResponse -> "La información de actualizaciones no es válida. Inténtalo más tarde."
    AppUpdateFailureReason.Unexpected -> "No se pudo comprobar si hay actualizaciones. Inténtalo de nuevo."
}
