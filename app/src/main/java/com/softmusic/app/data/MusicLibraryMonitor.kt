package com.softmusic.app.data

import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope

/** Observes the same external audio collection queried by MusicRepository, only with permission. */
class MusicLibraryMonitor(context: Context, scope: CoroutineScope, onRefreshRequested: () -> Unit) : AutoCloseable {
    private val resolver = context.applicationContext.contentResolver
    private val monitor = LibraryRefreshMonitor(
        scope = scope,
        register = { onChanged ->
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = onChanged()
            }
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            resolver.registerContentObserver(collection, true, observer)
            AutoCloseable { resolver.unregisterContentObserver(observer) }
        },
        onRefreshRequested = onRefreshRequested,
    )

    fun setPermissionGranted(granted: Boolean) = monitor.setPermissionGranted(granted)
    fun onResume() = monitor.onResume()
    override fun close() = monitor.close()
}
