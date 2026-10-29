package com.softmusic.app.backup

import android.content.Context
import android.content.SharedPreferences

internal val BACKUP_PREFERENCE_KEYS = setOf(
    "high_performance_mode", "font_scale", "default_playback_mode", "default_sort_mode",
    "exclude_small_audios", "default_folder_path", "hidden_folder_paths", "dj_mode_enabled",
    "dj_mix_mode", "dj_mix_duration_seconds", "auto_dj_beats", "auto_dj_effect",
    "auto_dj_intensity", "auto_dj_sync_enabled", "backup_preferences_revision",
)

/** Settings and collections share the same atomic restore transaction. */
internal fun openAppPreferences(context: Context): SharedPreferences {
    val target = context.getSharedPreferences("playlist_settings", Context.MODE_PRIVATE)
    val source = context.getSharedPreferences("theme_settings", Context.MODE_PRIVATE)
    return migrateAppPreferences(source, target)
}

internal fun migrateAppPreferences(source: SharedPreferences, target: SharedPreferences): SharedPreferences {
    if (!target.getBoolean("backup_settings_migrated_v1", false)) {
        val editor = target.edit()
        source.all.filterKeys { !target.contains(it) }.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        if (!runCatching { editor.putBoolean("backup_settings_migrated_v1", true).commit() }.getOrDefault(false)) {
            // A failed commit still updates SharedPreferences memory. Keep those copied
            // settings available without crashing startup, but retry their migration later.
            // The untouched source remains a durable fallback for the next process.
            runCatching { target.edit().remove("backup_settings_migrated_v1").apply() }
        }
    }
    return target
}

internal fun SharedPreferences.Editor.putBackupPreferences(
    preferences: BackupPreferences,
    validFolderPaths: Set<String>,
): SharedPreferences.Editor = apply {
    val hidden = preferences.hiddenFolderPaths.intersect(validFolderPaths)
    val folder = preferences.defaultFolderPath?.takeIf { it in validFolderPaths && it !in hidden }
    putBoolean("high_performance_mode", preferences.highPerformanceMode)
    putFloat("font_scale", preferences.fontScale)
    putString("default_playback_mode", preferences.playbackMode.name)
    putString("default_sort_mode", preferences.sortMode.name)
    putBoolean("exclude_small_audios", preferences.excludeSmallAudios)
    putString("default_folder_path", folder)
    putStringSet("hidden_folder_paths", hidden)
    putBoolean("dj_mode_enabled", preferences.djModeEnabled)
    putString("dj_mix_mode", preferences.djMixMode.name)
    putInt("dj_mix_duration_seconds", preferences.djMixDurationSeconds)
    putInt("auto_dj_beats", preferences.autoDjSettings.beats)
    putString("auto_dj_effect", preferences.autoDjSettings.effect.name)
    putFloat("auto_dj_intensity", preferences.autoDjSettings.intensity)
    putBoolean("auto_dj_sync_enabled", preferences.autoDjSettings.syncEnabled)
    putLong("backup_preferences_revision", System.nanoTime())
}
