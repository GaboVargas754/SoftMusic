package com.softmusic.app.player

import com.softmusic.app.data.MusicFolder
import com.softmusic.app.data.MusicPlaylist
import com.softmusic.app.data.SMALL_AUDIO_MIN_DURATION_MS
import com.softmusic.app.data.SMALL_AUDIO_MIN_FILE_SIZE_BYTES
import com.softmusic.app.data.Song
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryRulesTest {
    @Test
    fun `filtered and sorted applies hidden folder selected folder small audio and recent sort`() {
        val songs = listOf(
            song(id = 1, title = "Visible old", folderPath = "/music/a", dateAddedSeconds = 1),
            song(id = 2, title = "Visible new", folderPath = "/music/a", dateAddedSeconds = 3),
            song(id = 3, title = "Hidden", folderPath = "/music/hidden", dateAddedSeconds = 9),
            song(id = 4, title = "Other folder", folderPath = "/music/b", dateAddedSeconds = 8),
            song(
                id = 5,
                title = "Small",
                folderPath = "/music/a",
                durationMs = SMALL_AUDIO_MIN_DURATION_MS - 1,
                fileSizeBytes = SMALL_AUDIO_MIN_FILE_SIZE_BYTES - 1,
                dateAddedSeconds = 10,
            ),
        )

        val result = songs.filteredAndSorted(
            selectedFolderPath = "/music/a",
            sortMode = SortMode.Recent,
            hiddenFolderPaths = setOf("/music/hidden"),
            excludeSmallAudios = true,
        )

        assertEquals(listOf(2L, 1L), result.map { it.id })
    }

    @Test
    fun `title sort uses title then artist`() {
        val songs = listOf(
            song(id = 1, title = "Beta", artist = "Artist A"),
            song(id = 2, title = "Alpha", artist = "Artist B"),
            song(id = 3, title = "Alpha", artist = "Artist A"),
        )

        val result = songs.filteredAndSorted(
            selectedFolderPath = null,
            sortMode = SortMode.Title,
            hiddenFolderPaths = emptySet(),
            excludeSmallAudios = true,
        )

        assertEquals(listOf(3L, 2L, 1L), result.map { it.id })
    }

    @Test
    fun `folders group songs by path and hide selected paths`() {
        val songs = listOf(
            song(id = 1, folderPath = "/music/b", folderName = "Beta"),
            song(id = 2, folderPath = "/music/a", folderName = "Alpha"),
            song(id = 3, folderPath = "/music/a", folderName = "Alpha"),
        )

        val folders = songs.toFolders()
        val visible = folders.visibleFolders(setOf("/music/b"))

        assertEquals(
            listOf(
                MusicFolder(path = "/music/a", name = "Alpha", songCount = 2),
                MusicFolder(path = "/music/b", name = "Beta", songCount = 1),
            ),
            folders,
        )
        assertEquals(listOf("/music/a"), visible.map { it.path })
    }

    @Test
    fun `playable songs prefer visible songs and fallback to filtered library`() {
        val visible = song(id = 1, folderPath = "/music/visible")
        val fallbackVisible = song(id = 2, folderPath = "/music/fallback")
        val fallbackHidden = song(id = 3, folderPath = "/music/hidden")
        val stateWithVisible = PlayerUiState(
            songs = listOf(fallbackVisible, fallbackHidden),
            visibleSongs = listOf(visible),
            hiddenFolderPaths = setOf("/music/hidden"),
        )
        val stateWithFallback = stateWithVisible.copy(visibleSongs = emptyList())

        assertEquals(listOf(1L), stateWithVisible.playableSongs().map { it.id })
        assertEquals(listOf(2L), stateWithFallback.playableSongs().map { it.id })
    }

    @Test
    fun `an unavailable library retains saved hidden folders selected folder and collections`() {
        val current = song(1L, folderPath = "/missing")
        val state = PlayerUiState(
            songs = listOf(current),
            selectedFolderPath = "/missing",
            hiddenFolderPaths = setOf("/hidden-missing", "/hidden-present"),
            favoriteSongIds = setOf(1L, 99L),
            playlists = listOf(MusicPlaylist("list", "List", listOf(1L, 99L))),
            playbackQueue = listOf(current),
            currentSong = current,
            isPlaying = true,
            isLoading = true,
            errorMessage = "previous error",
        )

        val empty = state.withRefreshedLibrary(emptyList())
        assertEquals("/missing", empty.selectedFolderPath)
        assertEquals(state.hiddenFolderPaths, empty.hiddenFolderPaths)
        assertEquals(state.favoriteSongIds, empty.favoriteSongIds)
        assertEquals(state.playlists, empty.playlists)
        assertEquals(state.playbackQueue, empty.playbackQueue)
        assertEquals(current, empty.currentSong)
        assertEquals(true, empty.isPlaying)
        assertEquals(false, empty.isLoading)
        assertEquals(null, empty.errorMessage)
        assertEquals(emptyList(), empty.visibleSongs)
        assertEquals(listOf(current), empty.withRefreshedLibrary(listOf(current)).visibleSongs)
    }

    @Test
    fun `refresh derives filters sorting and folders without pruning hidden preferences`() {
        val songs = listOf(
            song(1L, title = "Beta", folderPath = "/music/a"),
            song(2L, title = "Alpha", folderPath = "/music/a"),
            song(3L, folderPath = "/hidden"),
            song(4L, folderPath = "/small", durationMs = 1L),
            song(5L, folderPath = "/music/b"),
        )
        val state = PlayerUiState(
            selectedFolderPath = "/music/a",
            hiddenFolderPaths = setOf("/hidden", "/unmounted-hidden"),
            excludeSmallAudios = true,
            sortMode = SortMode.Title,
        ).withRefreshedLibrary(songs)

        assertEquals(listOf(2L, 1L), state.visibleSongs.map { it.id })
        assertEquals(setOf("/music/a", "/music/b"), state.folders.map { it.path }.toSet())
        assertEquals(setOf("/music/a", "/music/b", "/hidden", "/small"), state.allFolders.map { it.path }.toSet())
        assertEquals(setOf("/hidden", "/unmounted-hidden"), state.hiddenFolderPaths)
    }

    @Test
    fun `only explicitly hiding the selected folder clears selection on refresh`() {
        val state = PlayerUiState(selectedFolderPath = "/missing", hiddenFolderPaths = setOf("/missing"))
        assertEquals(null, state.withRefreshedLibrary(emptyList()).selectedFolderPath)
        assertEquals("/missing", state.copy(hiddenFolderPaths = emptySet()).withRefreshedLibrary(emptyList()).selectedFolderPath)
    }

    @Test
    fun `queue refresh retains missing playing item updates metadata and preserves existing order`() {
        val oldCurrent = song(2L, title = "Current removed from storage")
        val oldQueue = listOf(song(3L), oldCurrent, song(1L), song(99L))
        val refreshed = song(3L, title = "Updated metadata")
        val available = listOf(song(1L), song(4L), refreshed)

        val queue = oldQueue.refreshedQueuePreservingCurrent(available, currentSongId = 2L)
        assertEquals(listOf(3L, 2L, 1L), queue.map { it.id })
        assertEquals(refreshed, queue[0])
        assertEquals(oldCurrent, queue[1])
        assertEquals(listOf(3L, 1L), oldQueue.refreshedQueuePreservingCurrent(available, null).map { it.id })
        assertEquals(listOf(oldCurrent), oldQueue.refreshedQueuePreservingCurrent(emptyList(), 2L))
    }

    private fun song(
        id: Long,
        title: String = "Song $id",
        artist: String = "Artist",
        album: String = "Album",
        durationMs: Long = SMALL_AUDIO_MIN_DURATION_MS,
        fileSizeBytes: Long = SMALL_AUDIO_MIN_FILE_SIZE_BYTES,
        dateAddedSeconds: Long = id,
        folderPath: String = "/music",
        folderName: String = folderPath.substringAfterLast('/'),
    ): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        fileSizeBytes = fileSizeBytes,
        dateAddedSeconds = dateAddedSeconds,
        folderPath = folderPath,
        folderName = folderName,
        uri = "file://$folderPath/song$id.mp3",
        artworkUri = null,
    )
}
