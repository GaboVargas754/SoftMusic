package com.softmusic.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.softmusic.app.player.ListeningHistoryEntry
import com.softmusic.app.player.ListeningHistoryPlay
import com.softmusic.app.player.ListeningHistoryState
import com.softmusic.app.player.ListeningHistoryWriteFence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A local aggregate store. Only qualified visits enqueue writes; playback ticks never touch SQLite. */
class ListeningHistoryRepository private constructor(context: Context) {
    private sealed interface Command {
        data class Refresh(val revision: Long, val restoreSettings: Boolean = false) : Command
        data class Enabled(val value: Boolean, val revision: Long) : Command
        data class Clear(val revision: Long) : Command
        data class Record(val play: ListeningHistoryPlay, val revision: Long) : Command
    }

    private val lock = Any()
    private val fence = ListeningHistoryWriteFence()
    private var settingsLoaded = false
    private val helper = HistoryDatabase(context.applicationContext)
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val _state = MutableStateFlow(ListeningHistoryState())
    val state: StateFlow<ListeningHistoryState> = _state.asStateFlow()

    init {
        commands.trySend(Command.Refresh(0L, restoreSettings = true))
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            for (command in commands) process(command)
        }
    }

    fun setEnabled(enabled: Boolean) = synchronized(lock) {
        if (fence.snapshot.enabled == enabled && !_state.value.isLoading) return@synchronized
        if (_state.value.isLoading && fence.snapshot.enabled == enabled) fence.clear()
        val snapshot = fence.setEnabled(enabled)
        _state.value = _state.value.copy(enabled = enabled, trackingRevision = snapshot.revision, errorMessage = null)
        commands.trySend(Command.Enabled(enabled, snapshot.revision))
        Unit
    }

    fun clear() = synchronized(lock) {
        val snapshot = fence.clear()
        _state.value = _state.value.copy(entries = emptyList(), isLoading = !settingsLoaded,
            trackingRevision = snapshot.revision, errorMessage = null)
        commands.trySend(Command.Clear(snapshot.revision))
        Unit
    }

    fun refresh() = synchronized(lock) {
        commands.trySend(Command.Refresh(fence.snapshot.revision, restoreSettings = true))
        Unit
    }

    internal fun record(play: ListeningHistoryPlay, trackingRevision: Long) = synchronized(lock) {
        if (!_state.value.isLoading && fence.accepts(trackingRevision)) {
            commands.trySend(Command.Record(play, trackingRevision))
        }
        Unit
    }

    private fun process(command: Command) {
        val revision = when (command) {
            is Command.Refresh -> command.revision
            is Command.Enabled -> command.revision
            is Command.Clear -> command.revision
            is Command.Record -> command.revision
        }
        try {
            val db = helper.writableDatabase
            if (command !is Command.Enabled && synchronized(lock) { !settingsLoaded }) {
                restoreSettings(db, revision)
            }
            when (command) {
                is Command.Enabled -> {
                    db.execSQL("UPDATE history_settings SET enabled = ? WHERE id = 1", arrayOf(if (command.value) 1 else 0))
                    synchronized(lock) { settingsLoaded = true }
                }
                is Command.Clear -> db.delete("listening_history", null, null)
                is Command.Record -> {
                    if (!synchronized(lock) { fence.accepts(revision) }) return
                    db.beginTransaction()
                    try {
                        db.execSQL("INSERT OR IGNORE INTO listening_history(song_id, play_count, last_played_at_ms) VALUES (?, 0, 0)",
                            arrayOf(command.play.songId))
                        db.execSQL("UPDATE listening_history SET play_count = play_count + 1, last_played_at_ms = MAX(last_played_at_ms, ?) WHERE song_id = ?",
                            arrayOf(command.play.playedAtMs.coerceAtLeast(0L), command.play.songId))
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                }
                is Command.Refresh -> if (command.restoreSettings) {
                    restoreSettings(db, revision)
                }
            }
            val entries = db.rawQuery("SELECT song_id, play_count, last_played_at_ms FROM listening_history WHERE play_count > 0 ORDER BY last_played_at_ms DESC, song_id", null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(ListeningHistoryEntry(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2)))
                }
            }
            synchronized(lock) {
                val current = fence.snapshot
                if (current.revision == revision) {
                    _state.value = ListeningHistoryState(current.enabled, entries, isLoading = false, trackingRevision = current.revision)
                }
            }
        } catch (_: Exception) {
            synchronized(lock) {
                if (fence.snapshot.revision == revision) {
                    _state.value = _state.value.copy(enabled = fence.snapshot.enabled, isLoading = false,
                        errorMessage = "No se pudo guardar o cargar el historial local. Inténtalo de nuevo.")
                }
            }
        }
    }

    private fun restoreSettings(db: SQLiteDatabase, revision: Long) {
        val storedEnabled = db.rawQuery("SELECT enabled FROM history_settings WHERE id = 1", null).use {
            it.moveToFirst() && it.getInt(0) != 0
        }
        synchronized(lock) {
            if (fence.restoreEnabled(revision, storedEnabled) != null) settingsLoaded = true
        }
    }

    private class HistoryDatabase(context: Context) : SQLiteOpenHelper(context, "listening_history.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE listening_history (song_id INTEGER PRIMARY KEY, play_count INTEGER NOT NULL, last_played_at_ms INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX history_recent ON listening_history(last_played_at_ms DESC)")
            db.execSQL("CREATE TABLE history_settings (id INTEGER PRIMARY KEY, enabled INTEGER NOT NULL)")
            db.execSQL("INSERT INTO history_settings(id, enabled) VALUES (1, 1)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    companion object {
        @Volatile private var instance: ListeningHistoryRepository? = null

        fun get(context: Context): ListeningHistoryRepository = instance ?: synchronized(this) {
            instance ?: ListeningHistoryRepository(context).also { instance = it }
        }
    }
}
