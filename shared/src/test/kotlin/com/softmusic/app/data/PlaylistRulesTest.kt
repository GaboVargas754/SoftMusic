package com.softmusic.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaylistRulesTest {
    @Test
    fun `new playlist cleans name and skips duplicate names`() {
        val playlists = listOf(playlist(id = "existing", name = "Favoritos"))

        val duplicate = playlists.withNewPlaylist(name = " favoritos ", id = "duplicate")
        val created = playlists.withNewPlaylist(name = "  Ruta nueva  ", id = "new")

        assertFalse(duplicate.changed)
        assertEquals(playlists, duplicate.playlists)
        assertTrue(created.changed)
        assertEquals("Ruta nueva", created.playlists.last().name)
        assertEquals("new", created.playlists.last().id)
    }

    @Test
    fun `queue add appends missing ids once and reports count`() {
        val playlists = listOf(playlist(songIds = listOf(1, 3)))

        val result = playlists.withQueueAddedToPlaylist(
            playlistId = "playlist",
            queueSongIds = listOf(1, 2, 2, 3, 4),
        )

        assertTrue(result.changed)
        assertEquals(2, result.addedCount)
        assertEquals(listOf(1L, 3L, 2L, 4L), result.playlists.single().songIds)
    }

    @Test
    fun `queue add skips save when playlist already contains queue`() {
        val playlists = listOf(playlist(songIds = listOf(1, 2)))

        val result = playlists.withQueueAddedToPlaylist(
            playlistId = "playlist",
            queueSongIds = listOf(2, 1, 1),
        )

        assertFalse(result.changed)
        assertEquals(0, result.addedCount)
        assertEquals(playlists, result.playlists)
    }

    @Test
    fun `playlist cover only accepts songs in playlist`() {
        val playlists = listOf(playlist(songIds = listOf(1, 2)))

        val valid = playlists.withPlaylistCover(playlistId = "playlist", songId = 2)
        val invalid = valid.playlists.withPlaylistCover(playlistId = "playlist", songId = 99)

        assertTrue(valid.changed)
        assertEquals(2L, valid.playlists.single().coverSongId)
        assertTrue(invalid.changed)
        assertNull(invalid.playlists.single().coverSongId)
    }

    @Test
    fun `removing covered song clears cover`() {
        val playlists = listOf(playlist(songIds = listOf(1, 2, 3), coverSongId = 2))

        val result = playlists.withSongRemovedFromPlaylist(playlistId = "playlist", songId = 2)

        assertTrue(result.changed)
        assertEquals(listOf(1L, 3L), result.playlists.single().songIds)
        assertNull(result.playlists.single().coverSongId)
    }

    @Test
    fun `adding duplicate song does not change playlist`() {
        val playlists = listOf(playlist(songIds = listOf(1, 2)))

        val result = playlists.withSongAddedToPlaylist(playlistId = "playlist", songId = 2)

        assertFalse(result.changed)
        assertEquals(playlists, result.playlists)
    }

    private fun playlist(
        id: String = "playlist",
        name: String = "Playlist",
        songIds: List<Long> = emptyList(),
        coverSongId: Long? = null,
    ): MusicPlaylist = MusicPlaylist(
        id = id,
        name = name,
        songIds = songIds,
        coverSongId = coverSongId,
    )
}
