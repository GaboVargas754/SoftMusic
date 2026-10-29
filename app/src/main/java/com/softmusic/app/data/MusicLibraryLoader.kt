package com.softmusic.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Serial, conflated scans. Control methods and callbacks belong to the supplied UI scope. */
class MusicLibraryLoader(
    scope: CoroutineScope,
    private val load: suspend () -> List<Song>,
    private val onStarted: () -> Unit,
    private val onLoaded: (List<Song>) -> Unit,
    private val onFailed: (Throwable) -> Unit,
) : AutoCloseable {
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private var enabled = false
    private var closed = false
    private var revision = 0L
    private var activeScan: Job? = null
    private val worker = scope.launch {
        for (request in requests) {
            if (!enabled || closed) continue
            val scanRevision = revision
            val scan = launch(start = CoroutineStart.LAZY) scan@{
                if (!isCurrent(scanRevision)) return@scan
                onStarted()
                if (!isCurrent(scanRevision)) return@scan
                try {
                    val songs = withContext(Dispatchers.IO) { load() }
                    if (isActive && isCurrent(scanRevision)) onLoaded(songs)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (isActive && isCurrent(scanRevision)) onFailed(failure)
                }
            }
            activeScan = scan
            scan.start()
            scan.join()
            if (activeScan === scan) activeScan = null
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (closed || this.enabled == enabled) return
        this.enabled = enabled
        if (!enabled) {
            revision++
            activeScan?.cancel()
        }
    }

    fun refresh() {
        if (!enabled || closed) return
        revision++
        activeScan?.cancel()
        requests.trySend(Unit)
    }

    override fun close() {
        if (closed) return
        closed = true
        enabled = false
        revision++
        activeScan?.cancel()
        requests.close()
        worker.cancel()
    }

    private fun isCurrent(scanRevision: Long): Boolean = enabled && !closed && revision == scanRevision
}
