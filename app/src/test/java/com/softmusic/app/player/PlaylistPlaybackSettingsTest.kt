package com.softmusic.app.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistPlaybackSettingsTest {
    private val modes = mapOf("regular" to PlaylistShuffleMode.Shuffle, "smart" to PlaylistShuffleMode.SmartShuffle)

    @Test
    fun preferenceRefreshPreservesLivePlaylistModeOverAnOldGlobalSelection() {
        assertEquals(PlaybackMode.Shuffle, playbackModeAfterPreferenceRefresh(
            PlaybackQueueSource.Playlist, PlaybackMode.Ordered, PlaybackMode.Shuffle,
            PlaybackMode.Ordered, false, PlaybackMode.Ordered))
        assertEquals(PlaybackMode.RepeatCurrent, playbackModeAfterPreferenceRefresh(
            PlaybackQueueSource.Playlist, PlaybackMode.Shuffle, PlaybackMode.RepeatCurrent,
            PlaybackMode.Shuffle, true, PlaybackMode.Ordered))
    }

    @Test
    fun preferenceRefreshRetainsPlaylistStateUntilServiceReconnects() {
        assertEquals(PlaybackMode.Shuffle, playbackModeAfterPreferenceRefresh(
            PlaybackQueueSource.Playlist, PlaybackMode.Ordered, null,
            PlaybackMode.Shuffle, true, PlaybackMode.Ordered))
    }

    @Test
    fun preferenceRefreshKeepsGlobalSelectionPrecedenceOutsidePlaylists() {
        assertEquals(PlaybackMode.Ordered, playbackModeAfterPreferenceRefresh(
            PlaybackQueueSource.Songs, PlaybackMode.Ordered, PlaybackMode.Shuffle,
            PlaybackMode.Shuffle, true, PlaybackMode.RepeatList))
    }

    @Test
    fun startingEachPlaylistAdoptsItsSavedChoiceAndAnUnknownPlaylistStartsOrdered() {
        assertEquals(PlaylistPlaybackSettings(PlaybackMode.Ordered, false), settings(PlaybackQueueSource.Playlist, "new"))
        assertEquals(PlaylistPlaybackSettings(PlaybackMode.Shuffle, false), settings(PlaybackQueueSource.Playlist, "regular"))
        assertEquals(PlaylistPlaybackSettings(PlaybackMode.Shuffle, true), settings(PlaybackQueueSource.Playlist, "smart"))
    }

    @Test
    fun leavingAPlaylistUsesTheGlobalDefaultAndClearsSmartForEveryOtherCollection() {
        for (source in PlaybackQueueSource.entries.filterNot { it == PlaybackQueueSource.Playlist }) {
            assertEquals(PlaylistPlaybackSettings(PlaybackMode.RepeatList, false), settings(source, "smart"))
        }
    }

    private fun settings(source: PlaybackQueueSource, key: String) =
        playbackSettingsForSource(source, key, modes, PlaybackMode.RepeatList)
}
