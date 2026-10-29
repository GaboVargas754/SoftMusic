package com.softmusic.app.player

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.softmusic.app.backup.BackupDocument
import com.softmusic.app.backup.BackupPreferences
import com.softmusic.app.backup.BackupSong
import com.softmusic.app.backup.BACKUP_PREFERENCE_KEYS
import com.softmusic.app.backup.MAX_BACKUP_SONGS
import com.softmusic.app.backup.putBackupPreferences
import com.softmusic.app.backup.validateBackupDocument
import com.softmusic.app.data.MusicPlaylist
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

internal class MusicPersistenceStore(
    private val favoritesPreferences: SharedPreferences,
    private val playlistsPreferences: SharedPreferences,
    private val playbackSessionPreferences: SharedPreferences,
    private val collectionMixPreferences: SharedPreferences,
) : PlaybackSessionStore {
    constructor(context: Context) : this(
        context.getSharedPreferences(FAVORITES_PREFS_NAME, Context.MODE_PRIVATE),
        context.getSharedPreferences(PLAYLISTS_PREFS_NAME, Context.MODE_PRIVATE),
        context.getSharedPreferences(PLAYBACK_SESSION_PREFS_NAME, Context.MODE_PRIVATE),
        context.getSharedPreferences(COLLECTION_MIX_PREFS_NAME, Context.MODE_PRIVATE),
    )

    fun loadFavoriteSongIds(): Set<Long> = (if (playlistsPreferences.contains(KEY_COLLECTION_FAVORITES)) {
        playlistsPreferences.getStringSet(KEY_COLLECTION_FAVORITES, emptySet())
    } else favoritesPreferences
        .getStringSet(KEY_FAVORITE_SONG_IDS, emptySet())
    ).orEmpty()
        .mapNotNull { it.toLongOrNull() }
        .toSet()

    fun saveFavoriteSongIds(ids: Set<Long>, pendingSongs: Map<Long, BackupSong>) {
        playlistsPreferences.edit()
            .putStringSet(KEY_COLLECTION_FAVORITES, ids.map { it.toString() }.toSet())
            .putString(KEY_PENDING_BACKUP_SONGS, pendingSongsJson(pendingSongs))
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
                    coverSongId = item.optLong("coverSongId").takeIf { it != 0L },
                )
            }.filter { it.id.isNotBlank() && it.name.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    fun savePlaylists(playlists: List<MusicPlaylist>, pendingSongs: Map<Long, BackupSong>) {
        playlistsPreferences.edit()
            .putString(KEY_PLAYLISTS, playlistsJson(playlists))
            .putString(KEY_PENDING_BACKUP_SONGS, pendingSongsJson(pendingSongs))
            .apply()
    }

    fun loadPendingBackupSongs(): Map<Long, BackupSong> = runCatching {
        val raw = playlistsPreferences.getString(KEY_PENDING_BACKUP_SONGS, null)
            ?: return@runCatching emptyMap()
        val array = JSONArray(raw)
        require(array.length() <= MAX_BACKUP_SONGS)
        val pending = buildMap {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                val id = item.getLong("id")
                require(id < 0L && id !in this)
                put(id, BackupSong(
                    key = item.getString("key"),
                    fileName = item.nullableString("fileName"),
                    title = item.getString("title"),
                    artist = item.getString("artist"),
                    album = item.getString("album"),
                    durationMs = item.nullableLong("durationMs"),
                    fileSizeBytes = item.nullableLong("fileSizeBytes"),
                    folderPath = item.nullableString("folderPath"),
                ))
            }
        }
        validateBackupDocument(BackupDocument(createdAtEpochMs = 0L, appVersion = "pending",
            songs = pending.values.toList(), playlists = emptyList(), favoriteKeys = emptyList()))
        pending
    }.getOrDefault(emptyMap())

    /** One preferences-file commit is the durability boundary for a restore. */
    fun commitBackupCollections(
        state: BackupCollectionState,
        preferences: BackupPreferences?,
        validFolderPaths: Set<String>,
    ): Boolean {
        val keys = setOf(KEY_PLAYLISTS, KEY_COLLECTION_FAVORITES, KEY_PENDING_BACKUP_SONGS) +
            if (preferences != null) BACKUP_PREFERENCE_KEYS else emptySet()
        val previousValues = playlistsPreferences.all.filterKeys { it in keys }
            .mapValues { (_, value) -> if (value is Set<*>) value.toSet() else value }
        val editor = playlistsPreferences.edit()
            .putString(KEY_PLAYLISTS, playlistsJson(state.playlists))
            .putStringSet(KEY_COLLECTION_FAVORITES, state.favoriteSongIds.map { it.toString() }.toSet())
            .putString(KEY_PENDING_BACKUP_SONGS, pendingSongsJson(state.pendingSongs))
        if (preferences != null) editor.putBackupPreferences(preferences, validFolderPaths)
        val saved = runCatching { editor.commit() }.getOrDefault(false)
        if (!saved) {
            // SharedPreferences updates its in-memory map even when the disk write fails.
            // Restore that map too, so a later ordinary write cannot persist a failed import.
            val rollback = playlistsPreferences.edit()
            keys.forEach { key -> rollback.restoreValue(key, previousValues[key]) }
            runCatching { rollback.commit() }.onFailure {
                Log.w("SoftMusicPersistence", "No se pudo volver a guardar el estado anterior del respaldo", it)
            }
        }
        return saved
    }

    private fun SharedPreferences.Editor.restoreValue(key: String, value: Any?) {
        when (value) {
            null -> remove(key)
            is String -> putString(key, value)
            is Boolean -> putBoolean(key, value)
            is Float -> putFloat(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }

    private fun playlistsJson(playlists: List<MusicPlaylist>): String {
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
        return array.toString()
    }

    private fun pendingSongsJson(pendingSongs: Map<Long, BackupSong>): String = JSONArray().apply {
        pendingSongs.forEach { (id, song) ->
            put(JSONObject().put("id", id).put("key", song.key)
                .put("fileName", song.fileName ?: JSONObject.NULL)
                .put("title", song.title).put("artist", song.artist).put("album", song.album)
                .put("durationMs", song.durationMs ?: JSONObject.NULL)
                .put("fileSizeBytes", song.fileSizeBytes ?: JSONObject.NULL)
                .put("folderPath", song.folderPath ?: JSONObject.NULL))
        }
    }.toString()

    private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else getString(key)
    private fun JSONObject.nullableLong(key: String): Long? = if (isNull(key)) null else getLong(key)

    fun loadCollectionMixes(): Map<String, CollectionMixConfig> = runCatching {
        val raw = collectionMixPreferences.getString(KEY_COLLECTION_MIXES, null)
            ?.takeIf { it.isNotBlank() } ?: return@runCatching emptyMap()
        val document = JSONObject(raw)
        if (document.optInt("version", 0) != COLLECTION_MIX_VERSION) return@runCatching emptyMap()
        val collections = document.optJSONObject("collections") ?: return@runCatching emptyMap()
        buildMap {
            for (key in collections.keys()) {
                if (!key.isCollectionMixKey()) continue
                val item = collections.optJSONObject(key) ?: continue
                // Consent must be a stored boolean, never inferred from old global settings.
                val accepted = item.opt("accepted") == true
                val settings = item.optJSONObject("settings")
                val order = item.optJSONArray("orderedSongIds")
                val transitions = item.optJSONObject("transitions")
                put(key, CollectionMixConfig(
                    accepted = accepted,
                    enabled = accepted && item.opt("enabled") == true,
                    settings = AutoDjSettings(
                        beats = settings?.optInt("beats", 16) ?: 16,
                        effect = enumValueOrNull<AutoDjEffect>(settings?.optString("effect")) ?: AutoDjEffect.BassSwap,
                        intensity = settings.floatOrDefault("intensity", .5f),
                        syncEnabled = settings?.optBoolean("syncEnabled", true) ?: true,
                    ),
                    orderedSongIds = if (order == null) emptyList() else List(order.length()) { order.opt(it).longOrNull() }
                        .filterNotNull().filter { it > 0L },
                    transitions = buildMap {
                        if (transitions != null) for (pair in transitions.keys()) {
                            val normalizedPair = normalizedPairKey(pair) ?: continue
                            val transition = transitions.optJSONObject(pair) ?: continue
                            put(normalizedPair, transition.toMixTransition())
                        }
                    },
                ).sanitized())
            }
        }
    }.getOrDefault(emptyMap())

    fun saveCollectionMixes(configs: Map<String, CollectionMixConfig>) {
        val collections = JSONObject()
        for ((key, original) in configs) {
            if (!key.isCollectionMixKey()) continue
            val config = original.sanitized()
            val settings = JSONObject()
                .put("beats", config.settings.beats)
                .put("effect", config.settings.effect.name)
                .put("intensity", config.settings.intensity.toDouble())
                .put("syncEnabled", config.settings.syncEnabled)
            val order = JSONArray().apply { config.orderedSongIds.forEach { put(it) } }
            val transitions = JSONObject()
            for ((pair, transition) in config.transitions) {
                val normalizedPair = normalizedPairKey(pair) ?: continue
                transitions.put(normalizedPair, transition.toJson())
            }
            collections.put(key, JSONObject()
                .put("accepted", config.accepted)
                .put("enabled", config.isEnabled)
                .put("settings", settings)
                .put("orderedSongIds", order)
                .put("transitions", transitions))
        }
        val document = JSONObject().put("version", COLLECTION_MIX_VERSION).put("collections", collections)
        collectionMixPreferences.edit().putString(KEY_COLLECTION_MIXES, document.toString()).apply()
    }

    override fun loadPlaybackSession(): PlaybackSessionSnapshot? {
        synchronized(playbackSessionLock) {
            if (playbackSessionKnown) return requestedPlaybackSession
        }
        val raw = playbackSessionPreferences.getString(KEY_PLAYBACK_SESSION, null).orEmpty()
        val storedSession = runCatching {
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
                source = enumValueOrNull<PlaybackQueueSource>(json.optString("source")) ?: PlaybackQueueSource.Songs,
                sourceKey = (json.opt("sourceKey") as? String)?.takeIf { it.isNotBlank() },
            )
        }.getOrNull()
        return synchronized(playbackSessionLock) {
            // A request made while preferences were being read is newer than their stored value.
            if (playbackSessionKnown) requestedPlaybackSession else storedSession
        }
    }

    override fun savePlaybackSession(session: PlaybackSessionSnapshot) {
        synchronized(playbackSessionLock) {
            requestedPlaybackSession = session
            playbackSessionKnown = true
            playbackSessionWrites.submit(PlaybackSessionWrite.Save(playbackSessionPreferences, session))
        }
    }

    override fun clearPlaybackSession() {
        synchronized(playbackSessionLock) {
            requestedPlaybackSession = null
            playbackSessionKnown = true
            playbackSessionWrites.submit(PlaybackSessionWrite.Clear(playbackSessionPreferences))
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOrNull(value: String?): T? {
        if (value.isNullOrBlank()) return null
        return enumValues<T>().firstOrNull { it.name == value }
    }

    private fun String.isCollectionMixKey(): Boolean = this == "favorites" ||
        (startsWith("playlist:") && removePrefix("playlist:").isNotBlank())

    private fun normalizedPairKey(value: String): String? {
        val parts = value.split('>')
        if (parts.size != 2) return null
        val outgoing = parts[0].toLongOrNull()?.takeIf { it > 0L } ?: return null
        val incoming = parts[1].toLongOrNull()?.takeIf { it > 0L && it != outgoing } ?: return null
        return mixPairKey(outgoing, incoming)
    }

    private fun Any?.longOrNull(): Long? = this?.toString()?.toLongOrNull()

    private fun JSONObject?.floatOrDefault(key: String, fallback: Float): Float =
        this?.optDouble(key, fallback.toDouble())?.toFloat() ?: fallback

    private fun JSONObject.toMixEq() = MixEq(
        floatOrDefault("lowDb", 0f), floatOrDefault("midDb", 0f), floatOrDefault("highDb", 0f),
    ).sanitized()

    private fun JSONObject.toMixTransition() = MixTransitionSettings(
        mode = enumValueOrNull<MixTransitionMode>(optString("mode")) ?: MixTransitionMode.Automatic,
        durationMs = opt("durationMs").longOrNull() ?: 8_000L,
        outgoingStartMs = opt("outgoingStartMs").longOrNull(),
        incomingStartMs = opt("incomingStartMs").longOrNull(),
        syncEnabled = optBoolean("syncEnabled", true),
        effect = enumValueOrNull<AutoDjEffect>(optString("effect")) ?: AutoDjEffect.BassSwap,
        intensity = floatOrDefault("intensity", .5f),
        outgoingVolume = floatOrDefault("outgoingVolume", 1f),
        incomingVolume = floatOrDefault("incomingVolume", 1f),
        outgoingEq = optJSONObject("outgoingEq")?.toMixEq() ?: MixEq(),
        incomingEq = optJSONObject("incomingEq")?.toMixEq() ?: MixEq(),
    ).sanitized()

    private fun MixEq.toJson() = JSONObject()
        .put("lowDb", lowDb.toDouble()).put("midDb", midDb.toDouble()).put("highDb", highDb.toDouble())

    private fun MixTransitionSettings.toJson(): JSONObject {
        val transition = sanitized()
        return JSONObject().put("mode", transition.mode.name).put("durationMs", transition.durationMs)
            .put("outgoingStartMs", transition.outgoingStartMs ?: JSONObject.NULL)
            .put("incomingStartMs", transition.incomingStartMs ?: JSONObject.NULL)
            .put("syncEnabled", transition.syncEnabled).put("effect", transition.effect.name)
            .put("intensity", transition.intensity.toDouble())
            .put("outgoingVolume", transition.outgoingVolume.toDouble()).put("incomingVolume", transition.incomingVolume.toDouble())
            .put("outgoingEq", transition.outgoingEq.toJson()).put("incomingEq", transition.incomingEq.toJson())
    }

    private sealed interface PlaybackSessionWrite {
        data class Save(val preferences: SharedPreferences, val session: PlaybackSessionSnapshot) : PlaybackSessionWrite
        data class Clear(val preferences: SharedPreferences) : PlaybackSessionWrite
    }

    private companion object {
        private val playbackSessionLock = Any()
        private var playbackSessionKnown = false
        private var requestedPlaybackSession: PlaybackSessionSnapshot? = null
        private val playbackSessionWrites = CoalescingWriteQueue<PlaybackSessionWrite>(
            executor = Executors.newSingleThreadExecutor { task ->
                Thread(task, "SoftMusic-session-save").apply { isDaemon = true }
            },
            write = ::writePlaybackSession,
            onFailure = { failure -> Log.w("SoftMusicPersistence", "No se pudo guardar la sesión de reproducción", failure) },
        )

        private fun writePlaybackSession(request: PlaybackSessionWrite) {
            when (request) {
                is PlaybackSessionWrite.Clear -> request.preferences.edit().remove(KEY_PLAYBACK_SESSION).apply()
                is PlaybackSessionWrite.Save -> {
                    val session = request.session
                    val queueIds = JSONArray().apply { session.queueIds.forEach(::put) }
                    val json = JSONObject()
                        .put("queueIds", queueIds)
                        .put("currentSongId", session.currentSongId)
                        .put("positionMs", session.positionMs)
                        .put("playbackMode", (session.playbackMode ?: PlaybackMode.Ordered).name)
                        .put("source", session.source.name)
                        .put("sourceKey", session.sourceKey?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                        .put("savedAt", System.currentTimeMillis())
                    request.preferences.edit().putString(KEY_PLAYBACK_SESSION, json.toString()).apply()
                }
            }
        }

        const val FAVORITES_PREFS_NAME = "favorite_settings"
        const val KEY_FAVORITE_SONG_IDS = "favorite_song_ids"
        const val PLAYLISTS_PREFS_NAME = "playlist_settings"
        const val KEY_PLAYLISTS = "playlists"
        const val KEY_COLLECTION_FAVORITES = "collection_favorite_song_ids"
        const val KEY_PENDING_BACKUP_SONGS = "pending_backup_songs"
        const val PLAYBACK_SESSION_PREFS_NAME = "playback_session_settings"
        const val KEY_PLAYBACK_SESSION = "playback_session"
        const val COLLECTION_MIX_PREFS_NAME = "collection_mix_settings"
        const val KEY_COLLECTION_MIXES = "collection_mixes"
        const val COLLECTION_MIX_VERSION = 1
    }
}
