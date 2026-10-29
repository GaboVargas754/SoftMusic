package com.softmusic.app.backup

import com.softmusic.app.player.AutoDjEffect
import com.softmusic.app.player.AutoDjSettings
import com.softmusic.app.player.DjMixMode
import com.softmusic.app.player.PlaybackMode
import com.softmusic.app.player.SortMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPreferencesStoreTest {
    @Test
    fun migrationKeepsExistingDestinationValuesAndPreservesEverySupportedLegacyType() {
        val sourceValues = mapOf("font_scale" to 1.15f, "old_boolean" to true, "old_int" to 7,
            "old_long" to 99L, "old_string" to "valor", "hidden_folder_paths" to setOf("/music"),
            "listening_history_enabled" to true)
        val source = InMemorySharedPreferences(sourceValues)
        val target = InMemorySharedPreferences(mapOf("font_scale" to 0.9f, "playlists" to "existing"))
        assertSame(target, migrateAppPreferences(source, target))
        assertEquals(sourceValues, source.all)
        assertEquals(sourceValues + mapOf("font_scale" to 0.9f, "playlists" to "existing",
            "backup_settings_migrated_v1" to true), target.all)
        assertEquals(1, target.commitCount)
        assertTrue(target.reopened().getBoolean("backup_settings_migrated_v1", false))
        source.edit().putString("new_legacy_key", "ignored after migration").commit()
        migrateAppPreferences(source, target)
        assertEquals(1, target.commitCount)
        assertFalse(target.contains("new_legacy_key"))
    }

    @Test
    fun failedMigrationKeepsSettingsUsableWithoutCompletingTheMarkerAndRetriesSafely() {
        val source = InMemorySharedPreferences(mapOf("font_scale" to 1.1f, "old_string" to "legacy"))
        val target = InMemorySharedPreferences(mapOf("playlists" to "existing"))
        target.commitResults.addAll(listOf(false, false))
        assertSame(target, migrateAppPreferences(source, target))
        assertEquals(1.1f, target.getFloat("font_scale", 1f))
        assertFalse(target.getBoolean("backup_settings_migrated_v1", false))
        assertEquals(mapOf("playlists" to "existing"), target.reopened().all)
        assertEquals("legacy", source.getString("old_string", null))
        target.edit().putFloat("font_scale", 0.95f).commit()
        migrateAppPreferences(source, target)
        assertEquals(0.95f, target.getFloat("font_scale", 1f))
        assertTrue(target.getBoolean("backup_settings_migrated_v1", false))
        assertTrue(target.reopened().getBoolean("backup_settings_migrated_v1", false))
    }

    @Test
    fun backupWritesOnlyTheWhitelistAndFiltersUnavailableFolders() {
        val unrelated = mapOf("listening_history_enabled" to false, "history_consent" to "local",
            "playlists" to "existing", "playback_session" to "queue")
        val target = InMemorySharedPreferences(unrelated)
        val preferences = BackupPreferences(highPerformanceMode = true, fontScale = 1.2f,
            playbackMode = PlaybackMode.Shuffle, sortMode = SortMode.Title, excludeSmallAudios = false,
            defaultFolderPath = "/music/default", hiddenFolderPaths = setOf("/music/hidden", "/missing"),
            djModeEnabled = true, djMixMode = DjMixMode.AutoDj, djMixDurationSeconds = 6,
            autoDjSettings = AutoDjSettings(32, AutoDjEffect.EchoOut, .75f, false))
        target.edit().putBackupPreferences(preferences, setOf("/music/default", "/music/hidden")).commit()
        assertEquals(BACKUP_PREFERENCE_KEYS, target.all.keys - unrelated.keys)
        assertEquals(unrelated, target.all.filterKeys { it in unrelated })
        assertEquals(true, target.getBoolean("high_performance_mode", false))
        assertEquals(1.2f, target.getFloat("font_scale", 1f))
        assertEquals("Shuffle", target.getString("default_playback_mode", null))
        assertEquals("Title", target.getString("default_sort_mode", null))
        assertFalse(target.getBoolean("exclude_small_audios", true))
        assertEquals("/music/default", target.getString("default_folder_path", null))
        assertEquals(setOf("/music/hidden"), target.getStringSet("hidden_folder_paths", null))
        assertTrue(target.getBoolean("dj_mode_enabled", false))
        assertEquals("AutoDj", target.getString("dj_mix_mode", null))
        assertEquals(6, target.getInt("dj_mix_duration_seconds", 0))
        assertEquals(32, target.getInt("auto_dj_beats", 0))
        assertEquals("EchoOut", target.getString("auto_dj_effect", null))
        assertEquals(.75f, target.getFloat("auto_dj_intensity", 0f))
        assertFalse(target.getBoolean("auto_dj_sync_enabled", true))
        assertTrue(target.all["backup_preferences_revision"] is Long)
    }

    @Test
    fun backupClearsAnUnavailableOrHiddenDefaultFolder() {
        val target = InMemorySharedPreferences(mapOf("default_folder_path" to "/music/old"))
        target.edit().putBackupPreferences(BackupPreferences(defaultFolderPath = "/missing"), setOf("/music")).commit()
        assertNull(target.getString("default_folder_path", null))
        target.edit().putBackupPreferences(BackupPreferences(defaultFolderPath = "/music",
            hiddenFolderPaths = setOf("/music")), setOf("/music")).commit()
        assertNull(target.getString("default_folder_path", null))
        assertEquals(setOf("/music"), target.getStringSet("hidden_folder_paths", null))
    }
}
