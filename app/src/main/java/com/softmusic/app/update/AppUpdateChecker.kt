package com.softmusic.app.update

import com.softmusic.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

data class AppUpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val releaseNotes: String,
    val updateUrl: String,
    val required: Boolean,
)

sealed interface AppUpdateCheckResult {
    data class Available(val info: AppUpdateInfo) : AppUpdateCheckResult
    data object UpToDate : AppUpdateCheckResult
    data class Failure(val reason: AppUpdateFailureReason) : AppUpdateCheckResult
}

sealed interface AppUpdateFailureReason {
    data object Timeout : AppUpdateFailureReason
    data object Network : AppUpdateFailureReason
    data class Http(val statusCode: Int) : AppUpdateFailureReason
    data object InvalidResponse : AppUpdateFailureReason
    data object Unexpected : AppUpdateFailureReason
}

object AppUpdateChecker {
    private const val UPDATE_MANIFEST_URL = "https://softmusic.cgvslab.online/update.json"
    internal const val DEFAULT_UPDATE_URL = "https://softmusic.cgvslab.online/downloads/SoftMusic.apk"

    suspend fun checkForUpdate(): AppUpdateCheckResult = AppUpdateCheckClient(
        installedVersionCode = BuildConfig.VERSION_CODE.toLong(),
        openConnection = { URL(UPDATE_MANIFEST_URL).openConnection() as HttpURLConnection },
    ).checkForUpdate()
}

/** Transport and installed version are explicit so HTTP failures and real manifest parsing can be tested together. */
internal class AppUpdateCheckClient(
    private val installedVersionCode: Long,
    private val openConnection: () -> HttpURLConnection,
) {
    suspend fun checkForUpdate(): AppUpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val connection = openConnection()
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.useCaches = false
                connection.setRequestProperty("Accept", "application/json")
                val statusCode = connection.responseCode
                if (statusCode !in 200..299) {
                    return@withContext AppUpdateCheckResult.Failure(AppUpdateFailureReason.Http(statusCode))
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                parseManifest(body)
            } finally {
                connection.disconnect()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SocketTimeoutException) {
            AppUpdateCheckResult.Failure(AppUpdateFailureReason.Timeout)
        } catch (_: IOException) {
            AppUpdateCheckResult.Failure(AppUpdateFailureReason.Network)
        } catch (_: JSONException) {
            AppUpdateCheckResult.Failure(AppUpdateFailureReason.InvalidResponse)
        } catch (_: Exception) {
            AppUpdateCheckResult.Failure(AppUpdateFailureReason.Unexpected)
        }
    }

    private fun parseManifest(body: String): AppUpdateCheckResult {
        val tokens = JSONTokener(body)
        val json = tokens.nextValue() as? JSONObject
            ?: return AppUpdateCheckResult.Failure(AppUpdateFailureReason.InvalidResponse)
        if (tokens.nextClean() != '\u0000') return AppUpdateCheckResult.Failure(AppUpdateFailureReason.InvalidResponse)
        // optLong coerces strings and truncates fractions; a manifest needs an actual positive integer.
        val version = when (val value = json.opt("versionCode")) {
            is Int -> value.toLong()
            is Long -> value
            else -> null
        }?.takeIf { it > 0L }
            ?: return AppUpdateCheckResult.Failure(AppUpdateFailureReason.InvalidResponse)
        if (version <= installedVersionCode) return AppUpdateCheckResult.UpToDate

        return AppUpdateCheckResult.Available(
            AppUpdateInfo(
                versionCode = version,
                versionName = json.optString("versionName").takeIf { it.isNotBlank() } ?: version.toString(),
                releaseNotes = json.optString("releaseNotes"),
                updateUrl = json.optString("updateUrl").takeIf { it.isNotBlank() } ?: AppUpdateChecker.DEFAULT_UPDATE_URL,
                required = json.optBoolean("required", false),
            ),
        )
    }

    private companion object {
        const val TIMEOUT_MS = 5_000
    }
}
