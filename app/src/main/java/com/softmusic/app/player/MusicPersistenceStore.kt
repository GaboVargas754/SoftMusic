package com.softmusic.app.player

import android.content.Context
import com.softmusic.app.data.MusicPlaylist
import org.json.JSONArray
import org.json.JSONObject

internal class MusicPersistenceStore(context: Context) {
    private val favoritesPreferences = context.getSharedPreferences(FAVORITES_PREFS_NAME, Context.MODE_PRIVATE)
    private val playlistsPreferences = context.getSharedPreferences(PLAYLISTS_PREFS_NAME, Context.MODE_PRIVATE)
    private val playbackSessionPreferences = context.getSharedPreferences(PLAYBACK_SESSION_PREFS_NAME, Context.MODE_PRIVATE)

    fun loadFavoriteSongIds(): Set<Long> = favoritesPreferences
        .getStringSet(KEY_FAVORITE_SONG_IDS, emptySet())
        .orEmpty()
        .mapNotNull { it.toLongOrNull() }
        .toSet()

    fun saveFavoriteSongIds(ids: Set<Long>) {
        favoritesPreferences.edit()
            .putStringSet(KEY_FAVORITE_SONG_IDS, ids.map { it.toString() }.toSet())
            .apply()
    }

    fun loadPlaylists(): List<MusicPlaylist> {
        val raw = playlistsPreferences.getString(KEY_PLAYLISTS, null).orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val songsArray = item.optJSONArray("songIds") ?: JSONArray()
                MusicPlaylist(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    songIds = List(songsArray.length()) { songIndex -> songsArray.optLong(songIndex) },
                    coverSongId = item.optLong("coverSongId").takeIf { it > 0L },
                )
            }.filter { it.id.isNotBlank() && it.name.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    fun savePlaylists(playlists: List<MusicPlaylist>) {
        val array = JSONArray()
        playlists.forEach { playlist ->
            val songIds = JSONArray()
            playlist.songIds.forEach(songIds::put)
            array.put(
                JSONObject()
                    .put("id", playlist.id)
                    .put("name", playlist.name)
                    .put("songIds", songIds)
                    .put("coverSongId", playlist.coverSongId ?: 0L),
            )
        }
        playlistsPreferences.edit()
            .putString(KEY_PLAYLISTS, array.toString())
            .apply()
    }

    fun loadPlaybackSession(): PlaybackSessionSnapshot? {
        val raw = playbackSessionPreferences.getString(KEY_PLAYBACK_SESSION, null).orEmpty()
        return runCatching {
            val json = JSONObject(raw)
            val queueArray = json.optJSONArray("queueIds") ?: JSONArray()
            val queueIds = List(queueArray.length()) { index -> queueArray.optLong(index) }
                .filter { it > 0L }
            val currentSongId = json.optLong("currentSongId").takeIf { it > 0L } ?: return@runCatching null
            if (queueIds.isEmpty()) return@runCatching null
            PlaybackSessionSnapshot(
                queueIds = queueIds,
                currentSongId = currentSongId,
                positionMs = json.optLong("positionMs", 0L),
                playbackMode = enumValueOrNull<PlaybackMode>(json.optString("playbackMode")),
            )
        }.getOrNull()
    }

    fun savePlaybackSession(session: PlaybackSessionSnapshot) {
        val queueIds = JSONArray().apply {
            session.queueIds.forEach(::put)
        }
        val json = JSONObject()
            .put("queueIds", queueIds)
            .put("currentSongId", session.currentSongId)
            .put("positionMs", session.positionMs)
            .put("playbackMode", (session.playbackMode ?: PlaybackMode.Ordered).name)
            .put("savedAt", System.currentTimeMillis())

        playbackSessionPreferences.edit()
            .putString(KEY_PLAYBACK_SESSION, json.toString())
            .apply()
    }

    fun clearPlaybackSession() {
        playbackSessionPreferences.edit().remove(KEY_PLAYBACK_SESSION).apply()
    }

    private inline fun <reified T : Enum<T>> enumValueOrNull(value: String?): T? {
        if (value.isNullOrBlank()) return null
        return enumValues<T>().firstOrNull { it.name == value }
    }

    private companion object {
        const val FAVORITES_PREFS_NAME = "favorite_settings"
        const val KEY_FAVORITE_SONG_IDS = "favorite_song_ids"
        const val PLAYLISTS_PREFS_NAME = "playlist_settings"
        const val KEY_PLAYLISTS = "playlists"
        const val PLAYBACK_SESSION_PREFS_NAME = "playback_session_settings"
        const val KEY_PLAYBACK_SESSION = "playback_session"
    }
}

internal data class PlaybackSessionSnapshot(
    val queueIds: List<Long>,
    val currentSongId: Long,
    val positionMs: Long,
    val playbackMode: PlaybackMode?,
)
