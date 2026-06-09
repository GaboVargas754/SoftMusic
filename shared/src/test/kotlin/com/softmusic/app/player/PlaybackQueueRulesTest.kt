package com.softmusic.app.player

import com.softmusic.app.data.MusicPlaylist
import com.softmusic.app.data.Song
import com.softmusic.app.data.orderedSongsFrom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PlaybackQueueRulesTest {
    @Test
    fun `shuffle queue keeps requested song first`() {
        val queue = songs(1, 2, 3)

        val result = queue.generatePlaybackQueue(PlaybackMode.Shuffle, requestedSong = queue[1])

        assertEquals(2, result.first().id)
        assertEquals(queue.map { it.id }.toSet(), result.map { it.id }.toSet())
        assertEquals(queue.size, result.size)
    }

    @Test
    fun `insert song after current removes duplicate`() {
        val queue = songs(1, 2, 3)

        val result = queue.insertSongNearCurrent(song = queue[2], currentSongId = 1, afterCurrent = true)

        assertNotNull(result)
        assertEquals(listOf(1L, 3L, 2L), result.map { it.id })
    }

    @Test
    fun `insert song at end removes duplicate`() {
        val queue = songs(1, 2, 3)

        val result = queue.insertSongNearCurrent(song = queue[0], currentSongId = 2, afterCurrent = false)

        assertNotNull(result)
        assertEquals(listOf(2L, 3L, 1L), result.map { it.id })
    }

    @Test
    fun `ordered next stops at end and repeat list wraps`() {
        val queue = songs(1, 2, 3)

        assertNull(
            queue.nextQueueMove(
                sourceSongs = queue,
                currentSong = queue[2],
                playbackMode = PlaybackMode.Ordered,
                manual = true,
            ),
        )

        val repeatMove = queue.nextQueueMove(
            sourceSongs = queue,
            currentSong = queue[2],
            playbackMode = PlaybackMode.RepeatList,
            manual = true,
        )

        assertNotNull(repeatMove)
        assertEquals(0, repeatMove.index)
    }

    @Test
    fun `repeat current stays on auto advance and moves on manual next`() {
        val queue = songs(1, 2, 3)

        val autoMove = queue.nextQueueMove(
            sourceSongs = queue,
            currentSong = queue[1],
            playbackMode = PlaybackMode.RepeatCurrent,
            manual = false,
        )
        val manualMove = queue.nextQueueMove(
            sourceSongs = queue,
            currentSong = queue[1],
            playbackMode = PlaybackMode.RepeatCurrent,
            manual = true,
        )

        assertNotNull(autoMove)
        assertNotNull(manualMove)
        assertEquals(1, autoMove.index)
        assertEquals(2, manualMove.index)
    }

    @Test
    fun `previous restarts current after threshold and wraps repeat list`() {
        val queue = songs(1, 2, 3)

        val restartMove = queue.previousQueueMove(
            currentSong = queue[1],
            playbackMode = PlaybackMode.Ordered,
            currentPositionMs = 3_001L,
        )
        val wrapMove = queue.previousQueueMove(
            currentSong = queue[0],
            playbackMode = PlaybackMode.RepeatList,
            currentPositionMs = 0L,
        )

        assertNotNull(restartMove)
        assertNotNull(wrapMove)
        assertEquals(1, restartMove.index)
        assertEquals(2, wrapMove.index)
    }

    @Test
    fun `shuffle at queue end rebuilds with current first and moves to next`() {
        val source = songs(1, 2, 3)
        val queue = listOf(source[1], source[2], source[0])

        val move = queue.nextQueueMove(
            sourceSongs = source,
            currentSong = source[0],
            playbackMode = PlaybackMode.Shuffle,
            manual = false,
        )

        assertNotNull(move)
        assertEquals(1L, move.queue.first().id)
        assertEquals(1, move.index)
        assertEquals(source.map { it.id }.toSet(), move.queue.map { it.id }.toSet())
    }

    @Test
    fun `playlist songs preserve playlist order and skip missing ids`() {
        val library = songs(1, 2, 3)
        val playlist = MusicPlaylist(
            id = "playlist",
            name = "Playlist",
            songIds = listOf(3, 1, 99, 2),
        )

        val result = playlist.orderedSongsFrom(library)

        assertEquals(listOf(3L, 1L, 2L), result.map { it.id })
    }

    private fun songs(vararg ids: Long): List<Song> = ids.map { id ->
        Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
            album = "Album",
            durationMs = 180_000L,
            dateAddedSeconds = id,
            folderPath = "/music",
            folderName = "music",
            uri = "file:///music/song$id.mp3",
            artworkUri = null,
        )
    }
}
