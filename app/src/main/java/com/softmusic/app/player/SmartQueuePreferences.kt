package com.softmusic.app.player

import android.content.Context
import android.content.SharedPreferences

/** Kept separate from backup-managed defaults so opening settings never resets a live session. */
internal class SmartQueuePreferences(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.applicationContext.getSharedPreferences("smart_queue", Context.MODE_PRIVATE))

    fun loadPlaylistModes(): Map<String, PlaylistShuffleMode> = preferences.all.mapNotNull { (key, value) ->
        if (!key.startsWith(PLAYLIST_MODE_PREFIX)) return@mapNotNull null
        val id = key.removePrefix(PLAYLIST_MODE_PREFIX).takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val mode = PlaylistShuffleMode.entries.firstOrNull { it.name == value } ?: return@mapNotNull null
        (id to mode).takeIf { mode != PlaylistShuffleMode.Off }
    }.toMap()

    fun setPlaylistMode(playlistId: String, mode: PlaylistShuffleMode) {
        if (playlistId.isBlank()) return
        preferences.edit().apply {
            if (mode == PlaylistShuffleMode.Off) remove(PLAYLIST_MODE_PREFIX + playlistId)
            else putString(PLAYLIST_MODE_PREFIX + playlistId, mode.name)
            // The removed global preference must never enable smart playback on another list.
            remove("smart_shuffle_enabled")
        }.apply()
    }

    fun retainPlaylists(playlistIds: Set<String>) {
        preferences.edit().apply {
            preferences.all.keys.filter { it.startsWith(PLAYLIST_MODE_PREFIX) }
                .filterNot { it.removePrefix(PLAYLIST_MODE_PREFIX) in playlistIds }.forEach(::remove)
            remove("smart_shuffle_enabled")
        }.apply()
    }

    private companion object {
        const val PLAYLIST_MODE_PREFIX = "playlist_shuffle_mode:"
    }
}
