package com.softmusic.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaylistEditingRulesTest {
    @Test
    fun `rename trims while preserving identity membership cover and list order`() {
        val source = listOf(playlist(), playlist(id = "other", name = "Otra"))

        val result = source.withPlaylistRenamed("mix", "  Mi nueva música  ")

        assertTrue(result.changed)
        assertEquals(source[0].copy(name = "Mi nueva música"), result.playlists[0])
        assertEquals(source[1], result.playlists[1])
    }

    @Test
    fun `rename excludes itself and compares other names without accents or case`() {
        val source = listOf(playlist(name = "Canciones"), playlist(id = "other", name = "  Música para mí "))

        assertTrue(source.withPlaylistRenamed("mix", "CANCIONES").changed)
        assertFalse(source.withPlaylistRenamed("mix", " MUSICA PARA MI ").changed)
        assertFalse(source.withPlaylistRenamed("mix", "Mu\u0301sica para mi\u0301").changed)
        assertEquals(source, source.withPlaylistRenamed("mix", "Música para mí").playlists)
    }

    @Test
    fun `rename rejects blank and excessive names without silently truncating`() {
        val source = listOf(playlist())
        val maximum = "a".repeat(MAX_PLAYLIST_NAME_LENGTH)

        assertEquals(maximum, source.withPlaylistRenamed("mix", " $maximum ").playlists.single().name)
        assertFalse(source.withPlaylistRenamed("mix", maximum + "b").changed)
        assertFalse(source.withPlaylistRenamed("mix", " \t\n ").changed)
        assertFalse(source.withPlaylistRenamed("mix", source.single().name).changed)
    }

    @Test
    fun `reorder changes only visible slots and preserves hidden missing members and cover`() {
        val full = listOf(8L, 1L, 99L, 2L, 3L, 7L)
        val source = listOf(playlist(ids = full, cover = 99))

        val result = source.withPlaylistSongsReordered("mix", full, listOf(1, 2, 3), listOf(3, 1, 2))

        assertTrue(result.changed)
        assertEquals(listOf(8L, 3L, 99L, 1L, 2L, 7L), result.playlists.single().songIds)
        assertEquals(99L, result.playlists.single().coverSongId)
        assertEquals("mix", result.playlists.single().id)
        assertEquals("Música", result.playlists.single().name)
    }

    @Test
    fun `stale full snapshot rejects reorder even when only a hidden member changed`() {
        val original = listOf(1L, 99L, 2L)
        val source = listOf(playlist(ids = original + 100L))

        val result = source.withPlaylistSongsReordered("mix", original, listOf(1, 2), listOf(2, 1))

        assertFalse(result.changed)
        assertEquals(source, result.playlists)
    }

    @Test
    fun `reorder rejects a changed visible set invalid baseline and nonpermutations`() {
        val full = listOf(1L, 99L, 2L, 3L)
        val source = listOf(playlist(ids = full))
        val invalid = listOf(
            listOf(1L, 2L) to listOf(3L, 2L, 1L),
            listOf(2L, 1L, 3L) to listOf(1L, 2L, 3L),
            listOf(1L, 2L, 3L) to listOf(2L, 2L, 1L),
            listOf(1L, 2L, 3L) to listOf(3L, 1L),
            listOf(1L, 2L, 4L) to listOf(4L, 2L, 1L),
            listOf(1L, 2L, 2L) to listOf(2L, 1L, 2L),
        )
        for ((visible, ordered) in invalid) {
            val result = source.withPlaylistSongsReordered("mix", full, visible, ordered)
            assertFalse(result.changed, "$visible -> $ordered")
            assertEquals(source, result.playlists)
        }
    }

    @Test
    fun `reordering to the same order and an empty visible subset does not write`() {
        val full = listOf(1L, 2L)
        val source = listOf(playlist(ids = full))

        assertFalse(source.withPlaylistSongsReordered("mix", full, full, full).changed)
        assertFalse(source.withPlaylistSongsReordered("mix", full, emptyList(), emptyList()).changed)
    }

    @Test
    fun `batch add preserves order and cover appending only new unique ids`() {
        val source = listOf(playlist(ids = listOf(2, 1), cover = 2))

        val result = source.withSongsAddedToPlaylist("mix", listOf(1, 3, 3, 4, 2))

        assertEquals(2, result.addedCount)
        assertEquals(listOf(2L, 1L, 3L, 4L), result.playlists.single().songIds)
        assertEquals(2L, result.playlists.single().coverSongId)
        assertEquals(0, result.playlists.withSongsAddedToPlaylist("mix", listOf(3, 4, 3)).addedCount)
    }

    @Test
    fun `batch remove counts existing unique ids retaining order and an unremoved cover`() {
        val source = listOf(playlist(ids = listOf(8, 1, 2, 3, 9), cover = 8))

        val result = source.withoutSongsFromPlaylist("mix", setOf(1, 3, 999))

        assertEquals(2, result.removedCount)
        assertEquals(listOf(8L, 2L, 9L), result.playlists.single().songIds)
        assertEquals(8L, result.playlists.single().coverSongId)
    }

    @Test
    fun `batch remove clears the cover exactly when its song is removed`() {
        val source = listOf(playlist(ids = listOf(1, 2, 3), cover = 2))

        val result = source.withoutSongsFromPlaylist("mix", setOf(2, 3))

        assertEquals(2, result.removedCount)
        assertEquals(listOf(1L), result.playlists.single().songIds)
        assertNull(result.playlists.single().coverSongId)
        assertFalse(source.withoutSongsFromPlaylist("mix", setOf(99)).changed)
        assertFalse(source.withoutSongsFromPlaylist("mix", emptySet()).changed)
    }

    @Test
    fun `unknown playlists and missing song ids are noops`() {
        val source = listOf(playlist())

        assertFalse(source.withPlaylistRenamed("missing", "Nuevo nombre").changed)
        assertFalse(source.withPlaylistSongsReordered("missing", emptyList(), listOf(1, 2), listOf(2, 1)).changed)
        assertEquals(0, source.withSongsAddedToPlaylist("missing", listOf(1, 2)).addedCount)
        assertEquals(0, source.withoutSongsFromPlaylist("missing", setOf(1, 2)).removedCount)
        assertEquals(source, source.withoutSongsFromPlaylist("mix", setOf(999)).playlists)
    }

    private fun playlist(
        id: String = "mix",
        name: String = "Música",
        ids: List<Long> = listOf(1, 2, 3),
        cover: Long? = 2,
    ) = MusicPlaylist(id, name, ids, cover)
}
