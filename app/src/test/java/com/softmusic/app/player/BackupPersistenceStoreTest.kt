package com.softmusic.app.player

import com.softmusic.app.backup.BackupPreferences
import com.softmusic.app.backup.BackupSong
import com.softmusic.app.backup.InMemorySharedPreferences
import com.softmusic.app.data.MusicPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPersistenceStoreTest {
    @Test
    fun successfulRestoreCommitsCollectionsPendingAndPreferencesTogetherAndSurvivesRestart() {
        val legacy = InMemorySharedPreferences(mapOf("favorite_song_ids" to setOf("42")))
        val target = InMemorySharedPreferences(mapOf("listening_history_enabled" to false))
        val session = InMemorySharedPreferences(mapOf("playback_session" to "unchanged"))
        val mixes = InMemorySharedPreferences(mapOf("collection_mixes" to "unchanged"))
        val store = MusicPersistenceStore(legacy, target, session, mixes)
        val pending = BackupSong("pending-1", "missing.mp3", "Missing", "Artist", "Album", 90_000L, 3_000_000L, "/old")
        val state = BackupCollectionState(listOf(MusicPlaylist("new", "Restaurada", listOf(42L, -1L), -1L)),
            setOf(42L, -1L), mapOf(-1L to pending))
        var observedAtomicState = false
        target.afterNextMemoryUpdate = {
            observedAtomicState = store.loadPlaylists() == state.playlists &&
                store.loadFavoriteSongIds() == state.favoriteSongIds &&
                store.loadPendingBackupSongs() == state.pendingSongs &&
                target.getBoolean("high_performance_mode", false)
        }
        assertTrue(store.commitBackupCollections(state, BackupPreferences(highPerformanceMode = true), emptySet()))
        assertTrue(observedAtomicState)
        assertEquals(1, target.commitCount)
        val restarted = MusicPersistenceStore(legacy.reopened(), target.reopened(), session.reopened(), mixes.reopened())
        assertEquals(state.playlists, restarted.loadPlaylists())
        assertEquals(state.favoriteSongIds, restarted.loadFavoriteSongIds())
        assertEquals(state.pendingSongs, restarted.loadPendingBackupSongs())
        assertFalse(target.getBoolean("listening_history_enabled", true))
        assertEquals(setOf("42"), legacy.getStringSet("favorite_song_ids", null))
        assertEquals("unchanged", session.getString("playback_session", null))
        assertEquals("unchanged", mixes.getString("collection_mixes", null))
        assertEquals(0, session.commitCount)
        assertEquals(0, mixes.commitCount)
    }

    @Test
    fun failedCommitRestoresOldTypedMemoryAndDiskWithoutLeavingNewKeysOrChangingOtherSettings() {
        val before = mapOf("playlists" to "[{\"id\":\"old\",\"name\":\"Local\",\"songIds\":[42],\"coverSongId\":42}]",
            "high_performance_mode" to false, "font_scale" to .9f, "auto_dj_beats" to 8,
            "backup_preferences_revision" to 123L, "hidden_folder_paths" to setOf("/local/hidden"),
            "default_folder_path" to "/local/default", "listening_history_enabled" to true,
            "backup_settings_migrated_v1" to true)
        val legacy = InMemorySharedPreferences(mapOf("favorite_song_ids" to setOf("42")))
        val target = InMemorySharedPreferences(before)
        target.commitResults.addAll(listOf(false, true))
        val store = MusicPersistenceStore(legacy, target, InMemorySharedPreferences(), InMemorySharedPreferences())
        val failedState = BackupCollectionState(listOf(MusicPlaylist("failed", "No guardada", listOf(9L))),
            setOf(9L), emptyMap())
        assertFalse(store.commitBackupCollections(failedState, BackupPreferences(highPerformanceMode = true,
            fontScale = 1.2f, hiddenFolderPaths = setOf("/new")), setOf("/new")))
        assertEquals(2, target.commitCount)
        assertEquals(before, target.all)
        assertEquals(before, target.reopened().all)
        assertEquals(setOf(42L), store.loadFavoriteSongIds())
        assertEquals("old", store.loadPlaylists().single().id)
        assertTrue(store.loadPendingBackupSongs().isEmpty())
        // A later normal write must not accidentally make any failed import durable.
        store.saveFavoriteSongIds(setOf(7L), emptyMap())
        val restarted = MusicPersistenceStore(legacy, target.reopened(), InMemorySharedPreferences(), InMemorySharedPreferences())
        assertEquals("old", restarted.loadPlaylists().single().id)
        assertEquals(setOf(7L), restarted.loadFavoriteSongIds())
        assertEquals(.9f, target.reopened().getFloat("font_scale", 1f))
    }

    @Test
    fun ordinaryFavoriteWritesUseTheNewFileAndTheLegacyFileBecomesOnlyAFallback() {
        val legacy = InMemorySharedPreferences(mapOf("favorite_song_ids" to setOf("42")))
        val target = InMemorySharedPreferences()
        val store = MusicPersistenceStore(legacy, target, InMemorySharedPreferences(), InMemorySharedPreferences())
        assertEquals(setOf(42L), store.loadFavoriteSongIds())
        store.saveFavoriteSongIds(setOf(8L), emptyMap())
        legacy.edit().putStringSet("favorite_song_ids", setOf("99")).commit()
        assertEquals(setOf(8L), store.loadFavoriteSongIds())
        assertEquals(setOf("8"), target.reopened().getStringSet("collection_favorite_song_ids", null))
    }
}
