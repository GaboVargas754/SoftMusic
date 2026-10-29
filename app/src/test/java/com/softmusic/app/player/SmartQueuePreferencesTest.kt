package com.softmusic.app.player

import com.softmusic.app.backup.InMemorySharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartQueuePreferencesTest {
    @Test
    fun eachPlaylistCyclesAndPersistsIndependentlyWithOffAsTheDefault() {
        val storage = InMemorySharedPreferences(mapOf("smart_shuffle_enabled" to true))
        val preferences = SmartQueuePreferences(storage)
        assertTrue(preferences.loadPlaylistModes().isEmpty())
        preferences.setPlaylistMode("first", PlaylistShuffleMode.Shuffle)
        preferences.setPlaylistMode("second", PlaylistShuffleMode.SmartShuffle)
        assertEquals(mapOf("first" to PlaylistShuffleMode.Shuffle, "second" to PlaylistShuffleMode.SmartShuffle),
            SmartQueuePreferences(storage.reopened()).loadPlaylistModes())
        preferences.setPlaylistMode("first", PlaylistShuffleMode.SmartShuffle)
        preferences.setPlaylistMode("first", PlaylistShuffleMode.Off)
        assertEquals(mapOf("second" to PlaylistShuffleMode.SmartShuffle),
            SmartQueuePreferences(storage.reopened()).loadPlaylistModes())
        assertFalse(storage.contains("smart_shuffle_enabled"))
    }

    @Test
    fun deletionPrunesOnlyRemovedPlaylistModesAndMalformedValuesDefaultToOff() {
        val storage = InMemorySharedPreferences(mapOf("playlist_shuffle_mode:bad" to "not-a-mode",
            "playlist_shuffle_mode:wrong-type" to true, "other_preference" to "keep"))
        val preferences = SmartQueuePreferences(storage)
        preferences.setPlaylistMode("keep", PlaylistShuffleMode.SmartShuffle)
        preferences.setPlaylistMode("deleted", PlaylistShuffleMode.Shuffle)
        preferences.retainPlaylists(setOf("keep", "bad", "wrong-type"))
        assertEquals(mapOf("keep" to PlaylistShuffleMode.SmartShuffle),
            SmartQueuePreferences(storage.reopened()).loadPlaylistModes())
        assertEquals("keep", storage.getString("other_preference", null))
        assertFalse(storage.contains("playlist_shuffle_mode:deleted"))
    }

    @Test
    fun choosingAnotherPlaylistChangesOnlyItsSavedValueAndDoesNotEnableTheCurrentSource() {
        val preferences = SmartQueuePreferences(InMemorySharedPreferences())
        preferences.setPlaylistMode("current", PlaylistShuffleMode.Shuffle)
        val before = playbackSettingsForSource(PlaybackQueueSource.Playlist, "current", preferences.loadPlaylistModes(), PlaybackMode.RepeatList)
        preferences.setPlaylistMode("browsed", PlaylistShuffleMode.SmartShuffle)
        val after = playbackSettingsForSource(PlaybackQueueSource.Playlist, "current", preferences.loadPlaylistModes(), PlaybackMode.RepeatList)
        assertEquals(before, after)
        assertEquals(PlaylistPlaybackSettings(PlaybackMode.Shuffle, false), after)
    }
}
