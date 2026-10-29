package com.softmusic.app.player

import com.softmusic.app.data.MusicFolder
import com.softmusic.app.data.MusicPlaylist
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackQueueOriginTest {
    @Test
    fun `history playback keeps its actual source label across saved keys`() {
        val history = PlayerUiState(playbackQueueSource = PlaybackQueueSource.History)
        assertEquals("Historial", history.playbackQueueOriginLabel())
        assertEquals("Escuchadas recientemente", history.copy(playbackQueueSourceKey = "recent").playbackQueueOriginLabel())
        assertEquals("Más reproducidas", history.copy(playbackQueueSourceKey = "most_played").playbackQueueOriginLabel())
        assertEquals(PlaybackQueueSource.History, enumValueOf<PlaybackQueueSource>("History"))
    }

    @Test
    fun `saved queue origin can be recovered for the exact running timeline and current song`() {
        assertTrue(
            playbackQueueOriginMatchesTimeline(listOf(8L, 3L, 5L), 3L, listOf("8", "3", "5"), "3"),
        )
    }

    @Test
    fun `matching current song does not authorize origin for reordered or different queues`() {
        for (timeline in listOf(
            listOf("3", "8", "5"),
            listOf("8", "3", "9"),
            listOf("8", "3"),
            listOf("8", "3", "5", "9"),
        )) {
            assertFalse(playbackQueueOriginMatchesTimeline(listOf(8L, 3L, 5L), 3L, timeline, "3"))
        }
    }

    @Test
    fun `origin recovery rejects absent or changed current media`() {
        for (currentMediaId in listOf(null, "5", "unknown")) {
            assertFalse(
                playbackQueueOriginMatchesTimeline(listOf(8L, 3L, 5L), 3L, listOf("8", "3", "5"), currentMediaId),
            )
        }
    }

    @Test
    fun `origin recovery rejects an empty queue and a saved current song outside the queue`() {
        assertFalse(playbackQueueOriginMatchesTimeline(emptyList(), 3L, emptyList(), "3"))
        assertFalse(playbackQueueOriginMatchesTimeline(listOf(8L, 5L), 3L, listOf("8", "5"), "3"))
    }

    @Test
    fun `origin recovery requires exact media identifiers`() {
        for (timeline in listOf(listOf("8", "03", "5"), listOf("8", "unknown", "5"))) {
            assertFalse(playbackQueueOriginMatchesTimeline(listOf(8L, 3L, 5L), 3L, timeline, "3"))
        }
    }

    @Test
    fun `songs without a source key keep the local library label while browsing a folder`() {
        val state = PlayerUiState(
            selectedFolderPath = "/music/browsing",
            folders = listOf(MusicFolder("/music/browsing", "Explorando", 3)),
        )

        assertEquals("Biblioteca local", state.playbackQueueOriginLabel())
        assertEquals("Biblioteca local", state.copy(playbackQueueSourceKey = "  ").playbackQueueOriginLabel())
    }

    @Test
    fun `favorites use the collection label regardless of key`() {
        assertEquals(
            "Favoritos",
            PlayerUiState(
                playbackQueueSource = PlaybackQueueSource.Favorites,
                playbackQueueSourceKey = "unused",
            ).playbackQueueOriginLabel(),
        )
    }

    @Test
    fun `playlist origin follows its id through a rename`() {
        val playlist = MusicPlaylist("playing", "Para viajar", emptyList())
        val state = PlayerUiState(
            playbackQueueSource = PlaybackQueueSource.Playlist,
            playbackQueueSourceKey = playlist.id,
            playlists = listOf(MusicPlaylist("other", "Otra playlist", emptyList()), playlist),
        )

        assertEquals("Para viajar", state.playbackQueueOriginLabel())
        assertEquals(
            "Viaje de noche",
            state.copy(playlists = listOf(playlist.copy(name = "Viaje de noche"))).playbackQueueOriginLabel(),
        )
    }

    @Test
    fun `deleted playlist retains a generic origin instead of another playlist name`() {
        val state = PlayerUiState(
            playbackQueueSource = PlaybackQueueSource.Playlist,
            playbackQueueSourceKey = "deleted",
            playlists = listOf(MusicPlaylist("other", "Otra playlist", emptyList())),
        )

        assertEquals("Playlist", state.playbackQueueOriginLabel())
    }

    @Test
    fun `playlist with missing key or empty name has a usable fallback`() {
        val state = PlayerUiState(playbackQueueSource = PlaybackQueueSource.Playlist)

        assertEquals("Playlist", state.playbackQueueOriginLabel())
        assertEquals("Playlist", state.copy(playbackQueueSourceKey = " ").playbackQueueOriginLabel())
        assertEquals(
            "Playlist",
            state.copy(
                playbackQueueSourceKey = "empty",
                playlists = listOf(MusicPlaylist("empty", "  ", emptyList())),
            ).playbackQueueOriginLabel(),
        )
    }

    @Test
    fun `artist and album use their source key with fallbacks for absent metadata`() {
        for ((source, name, fallback) in listOf(
            Triple(PlaybackQueueSource.Artist, "Café Tacvba", "Artista"),
            Triple(PlaybackQueueSource.Album, "Re", "Álbum"),
        )) {
            val state = PlayerUiState(playbackQueueSource = source, playbackQueueSourceKey = name)

            assertEquals(name, state.playbackQueueOriginLabel())
            assertEquals(fallback, state.copy(playbackQueueSourceKey = null).playbackQueueOriginLabel())
            assertEquals(fallback, state.copy(playbackQueueSourceKey = " \t").playbackQueueOriginLabel())
        }
    }

    @Test
    fun `folder origin describes the playing folder even after browsing elsewhere`() {
        val state = PlayerUiState(
            playbackQueueSourceKey = "/music/playing",
            selectedFolderPath = "/music/browsing",
            allFolders = listOf(MusicFolder("/music/playing", "Música del viaje", 8)),
            folders = listOf(MusicFolder("/music/browsing", "Explorando", 3)),
        )

        assertEquals("Música del viaje", state.playbackQueueOriginLabel())
    }

    @Test
    fun `folder origin can resolve a name from visible folder metadata`() {
        val state = PlayerUiState(
            playbackQueueSourceKey = "/music/playing",
            folders = listOf(MusicFolder("/music/playing", "Del teléfono", 8)),
        )

        assertEquals("Del teléfono", state.playbackQueueOriginLabel())
    }

    @Test
    fun `folder without metadata uses the final path component including after a trailing slash`() {
        val state = PlayerUiState(playbackQueueSourceKey = "/storage/emulated/0/Music/En vivo/")

        assertEquals("En vivo", state.playbackQueueOriginLabel())
    }

    @Test
    fun `blank folder names fall back to other metadata then to the path`() {
        val state = PlayerUiState(
            playbackQueueSourceKey = "/music/playing",
            allFolders = listOf(MusicFolder("/music/playing", "", 8)),
            folders = listOf(MusicFolder("/music/playing", "Música local", 8)),
        )

        assertEquals("Música local", state.playbackQueueOriginLabel())
        assertEquals("playing", state.copy(folders = emptyList()).playbackQueueOriginLabel())
        assertEquals("Carpeta", PlayerUiState(playbackQueueSourceKey = "/").playbackQueueOriginLabel())
    }
}
