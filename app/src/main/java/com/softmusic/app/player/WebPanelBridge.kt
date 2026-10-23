package com.softmusic.app.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface WebPanelController {
    fun snapshot(): WebPanelSnapshot

    suspend fun handleCommand(command: String): Boolean
}

internal object WebPanelBridge {
    private val lock = Any()
    private val _serverState = MutableStateFlow(WebPanelServerState())
    private var controller: WebPanelController? = null

    val serverState: StateFlow<WebPanelServerState> = _serverState.asStateFlow()

    fun registerController(controller: WebPanelController) {
        synchronized(lock) {
            this.controller = controller
        }
    }

    fun unregisterController(controller: WebPanelController) {
        synchronized(lock) {
            if (this.controller === controller) {
                this.controller = null
            }
        }
    }

    fun updateServerState(state: WebPanelServerState) {
        _serverState.value = state
    }

    fun snapshot(): WebPanelSnapshot {
        return currentController()?.snapshot() ?: WebPanelSnapshot(
            isPlaying = false,
            currentSong = null,
            positionMs = 0L,
            durationMs = 0L,
            playbackMode = PlaybackMode.Ordered,
            queue = emptyList(),
            playlists = emptyList(),
        )
    }

    suspend fun handleCommand(command: String): Boolean {
        return currentController()?.handleCommand(command) ?: false
    }

    private fun currentController(): WebPanelController? {
        return synchronized(lock) { controller }
    }
}
