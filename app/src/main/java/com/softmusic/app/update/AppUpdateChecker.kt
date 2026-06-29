package com.softmusic.app.update

import com.softmusic.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val releaseNotes: String,
    val updateUrl: String,
    val required: Boolean,
)

object AppUpdateChecker {
    private const val UPDATE_MANIFEST_URL = "https://softmusic.mi-portafolio.online/update.json"
    private const val DEFAULT_UPDATE_URL = "https://softmusic.mi-portafolio.online/downloads/SoftMusic.apk"
    private const val TIMEOUT_MS = 5_000

    suspend fun checkForUpdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(UPDATE_MANIFEST_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                useCaches = false
                setRequestProperty("Accept", "application/json")
            }

            try {
                if (connection.responseCode !in 200..299) return@runCatching null

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val remoteVersionCode = json.optLong("versionCode", -1L)
                if (remoteVersionCode <= BuildConfig.VERSION_CODE.toLong()) return@runCatching null

                val remoteVersionName = json.optString("versionName").takeIf { it.isNotBlank() }
                    ?: remoteVersionCode.toString()
                val updateUrl = json.optString("updateUrl").takeIf { it.isNotBlank() }
                    ?: DEFAULT_UPDATE_URL

                AppUpdateInfo(
                    versionCode = remoteVersionCode,
                    versionName = remoteVersionName,
                    releaseNotes = json.optString("releaseNotes"),
                    updateUrl = updateUrl,
                    required = json.optBoolean("required", false),
                )
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}
