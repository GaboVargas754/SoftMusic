package com.softmusic.app.player

import com.softmusic.app.data.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivePlaybackQueueTest {
    @Test
    fun manualEditsKeepTheSourceLibraryAndCollectionOriginTogether() {
        val queue = ActivePlaybackQueue()
        queue.replace(songs, songs, PlaybackQueueSource.Playlist, "playlist-id", PlaybackMode.Ordered)
        val edited = queue.moving(4L, -1, currentSongId = 2L)!!
        queue.replace(edited, queue.snapshot.sourceSongs, mode = PlaybackMode.Ordered, hasManualEdits = true)

        assertEquals(listOf(1L, 2L, 4L, 3L), queue.snapshot.ids)
        assertEquals(songs, queue.snapshot.sourceSongs)
        assertEquals(PlaybackQueueSource.Playlist, queue.snapshot.source)
        assertEquals("playlist-id", queue.snapshot.sourceKey)
        assertTrue(queue.snapshot.hasManualEdits)
        assertTrue(queue.shouldGenerate(songs, null, PlaybackQueueSource.Playlist, "playlist-id", PlaybackMode.Ordered))
    }

    @Test
    fun editsProtectTheCurrentSongAndNeverMoveUpcomingSongsIntoPlayedHistory() {
        val queue = ActivePlaybackQueue()
        queue.replace(songs, songs, mode = PlaybackMode.Ordered)
        assertNull(queue.removing(2L, currentSongId = 2L))
        assertNull(queue.removing(99L, currentSongId = 2L))
        assertNull(queue.moving(1L, 1, currentSongId = 2L))
        assertNull(queue.moving(2L, 1, currentSongId = 2L))
        assertNull(queue.moving(3L, 0, currentSongId = 2L))
        assertEquals(listOf(1L, 2L, 4L, 3L), queue.moving(4L, -99, currentSongId = 2L)!!.map { it.id })
        assertEquals(listOf(2L), queue.keepingCurrent(2L)!!.map { it.id })
        assertEquals(listOf(1L, 2L, 4L), queue.removing(3L, currentSongId = 2L)!!.map { it.id })
        assertEquals(songs, queue.snapshot.songs)
    }

    @Test
    fun queueGenerationChecksTheOriginModeAndSourceOrder() {
        val queue = ActivePlaybackQueue()
        queue.replace(songs, songs, PlaybackQueueSource.Favorites, null, PlaybackMode.RepeatList)
        assertFalse(queue.shouldGenerate(songs, songs[1], PlaybackQueueSource.Favorites, null, PlaybackMode.RepeatList))
        assertTrue(queue.shouldGenerate(songs, null, PlaybackQueueSource.Songs, null, PlaybackMode.RepeatList))
        assertTrue(queue.shouldGenerate(songs, null, PlaybackQueueSource.Favorites, "changed", PlaybackMode.RepeatList))
        assertTrue(queue.shouldGenerate(songs, null, PlaybackQueueSource.Favorites, null, PlaybackMode.Shuffle))
        assertTrue(queue.shouldGenerate(songs.reversed(), null, PlaybackQueueSource.Favorites, null, PlaybackMode.RepeatList))
    }

    @Test
    fun snapshotsDoNotFollowMutableInputListsAndClearResetsAllQueueMetadata() {
        val queue = ActivePlaybackQueue()
        val input = songs.toMutableList()
        queue.replace(input, input, PlaybackQueueSource.Album, "album", PlaybackMode.Shuffle, hasManualEdits = true)
        val previous = queue.snapshot
        input.clear()
        assertEquals(songs, queue.snapshot.songs)
        assertEquals(songs, queue.snapshot.sourceSongs)
        assertEquals(listOf("1", "2", "3", "4"), queue.snapshot.mediaIds)

        queue.clear()
        assertEquals(ActivePlaybackQueueSnapshot(), queue.snapshot)
        assertEquals(songs, previous.songs)
        assertEquals("album", previous.sourceKey)
        assertTrue(previous.hasManualEdits)
    }

    private val songs = (1L..4L).map { id -> Song(id, "Song $id", "Artist", "Album", 5_000L, 100L, 0L, "/music", "music", "content://music/$id", null) }
}
