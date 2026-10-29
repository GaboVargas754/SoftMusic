package com.softmusic.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Android-free observer lifecycle and debounce policy. */
internal class LibraryRefreshMonitor(
    private val scope: CoroutineScope,
    private val register: (() -> Unit) -> AutoCloseable,
    private val onRefreshRequested: () -> Unit,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) : AutoCloseable {
    private var permissionGranted = false
    private var closed = false
    private var registration: AutoCloseable? = null
    private var pendingRefresh: Job? = null
    private var revision = 0L

    fun setPermissionGranted(granted: Boolean) {
        if (closed || permissionGranted == granted) return
        permissionGranted = granted
        cancelPendingRefresh()
        if (granted) {
            try {
                registration = register(::onChanged)
            } catch (_: SecurityException) {
                // Permission may disappear between the UI check and observer registration.
                permissionGranted = false
                return
            }
            onRefreshRequested()
        } else {
            registration?.close()
            registration = null
        }
    }

    fun onResume() {
        if (!permissionGranted || closed) return
        cancelPendingRefresh()
        onRefreshRequested()
    }

    private fun onChanged() {
        if (!permissionGranted || closed) return
        cancelPendingRefresh()
        val requestRevision = revision
        pendingRefresh = scope.launch {
            pause(DEBOUNCE_MS)
            if (permissionGranted && !closed && revision == requestRevision) onRefreshRequested()
        }
    }

    private fun cancelPendingRefresh() {
        revision++
        pendingRefresh?.cancel()
        pendingRefresh = null
    }

    override fun close() {
        if (closed) return
        closed = true
        permissionGranted = false
        cancelPendingRefresh()
        registration?.close()
        registration = null
    }

    private companion object {
        const val DEBOUNCE_MS = 500L
    }
}
