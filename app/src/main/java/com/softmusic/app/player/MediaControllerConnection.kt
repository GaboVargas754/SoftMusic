package com.softmusic.app.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

/** The pending handle owns release both before and after its asynchronous result arrives. */
internal interface PendingPlaybackController<C> {
    fun get(): C
    fun whenReady(callback: () -> Unit)
    fun release()
}

internal class PlaybackControllerConnection<C>(
    private val pending: PendingPlaybackController<C>,
    private val attach: (C) -> Unit,
    private val detach: (C) -> Unit,
) {
    var controller: C? = null
        private set
    private var closed = false
    private var started = false

    fun connect(onConnected: (C) -> Unit, onFailure: (Throwable) -> Unit) {
        if (closed || started) return
        started = true
        pending.whenReady {
            // release() also owns a result arriving after close; do not acquire it again.
            if (closed) return@whenReady
            val result = runCatching(pending::get).getOrElse {
                if (!closed) onFailure(it)
                return@whenReady
            }
            controller = result
            attach(result)
            onConnected(result)
        }
    }

    fun close() {
        if (closed) return
        closed = true
        controller?.let(detach)
        controller = null
        pending.release()
    }
}

internal class MediaControllerConnection(context: Context, listener: Player.Listener) {
    private val connection = PlaybackControllerConnection(
        pending = object : PendingPlaybackController<MediaController> {
            private val future = MediaController.Builder(
                context,
                SessionToken(context, ComponentName(context, MusicService::class.java)),
            ).buildAsync()

            override fun get(): MediaController = future.get()
            override fun whenReady(callback: () -> Unit) {
                future.addListener(callback, ContextCompat.getMainExecutor(context))
            }
            override fun release() = MediaController.releaseFuture(future)
        },
        attach = { it.addListener(listener) },
        detach = { it.removeListener(listener) },
    )

    val controller: MediaController? get() = connection.controller

    fun connect(onConnected: (MediaController) -> Unit, onFailure: (Throwable) -> Unit) =
        connection.connect(onConnected, onFailure)

    fun close() = connection.close()
}
