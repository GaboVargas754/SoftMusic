package com.softmusic.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationStateTest {
    @Test
    fun nestedArtistBrowseReturnsToTheArtistListBeforeItsOrigin() {
        val artists = AppNavigationState(destination = LibraryDestination.Library)
            .openMusicBrowse(MusicLibraryView.Artists)
        val detail = artists.selectBrowseGroup("Artista")

        val list = detail.onBack()
        assertEquals(LibraryDestination.Browse, list.destination)
        assertNull(list.browseGroupTitle)
        assertEquals(MusicLibraryView.Artists, list.browseView)
        assertTrue(list.browseWholeLibrary)
        assertEquals("browse:Artists:list", list.screenKey)
        assertEquals(LibraryDestination.Library, list.onBack().destination)
    }

    @Test
    fun directlyOpenedAlbumReturnsToItsOriginInOneStep() {
        val detail = AppNavigationState(destination = LibraryDestination.Music)
            .openMusicBrowse(MusicLibraryView.Albums, "Álbum")

        val returned = detail.onBack()
        assertEquals(LibraryDestination.Music, returned.destination)
        assertEquals("Álbum", returned.browseGroupTitle)
        assertTrue(returned.browseDirectGroup)
    }

    @Test
    fun changingBrowseViewResetsDirectEntryBeforeOpeningAnotherGroup() {
        val detail = AppNavigationState(destination = LibraryDestination.Library)
            .openMusicBrowse(MusicLibraryView.Albums, "Álbum")
            .selectBrowseView(MusicLibraryView.Artists)

        assertNull(detail.browseGroupTitle)
        assertFalse(detail.browseDirectGroup)
        val returned = detail.selectBrowseGroup("Artista").onBack()
        assertEquals(LibraryDestination.Browse, returned.destination)
        assertNull(returned.browseGroupTitle)
    }

    @Test
    fun browsingFromAPlaylistPreservesThePlaylistSelectionAndCollectionOrigin() {
        val playlist = AppNavigationState(destination = LibraryDestination.Music).openPlaylist("road-trip")
        val browse = playlist.openMusicBrowse()

        assertEquals(LibraryDestination.Music, browse.selectedNavigationDestination)
        val returned = browse.onBack()
        assertEquals(LibraryDestination.Playlists, returned.destination)
        assertEquals("road-trip", returned.selectedPlaylistId)
        assertEquals("playlist:road-trip", returned.screenKey)
        assertEquals(LibraryDestination.Music, returned.collectionReturnDestination)
    }

    @Test
    fun favoritesReturnToTheirOriginAfterReturningFromBrowse() {
        val favorites = AppNavigationState(destination = LibraryDestination.Library).openFavorites()
        val browse = favorites.openMusicBrowse()

        assertEquals(LibraryDestination.Library, browse.selectedNavigationDestination)
        val returned = browse.onBack()
        assertEquals(LibraryDestination.Favorites, returned.destination)
        assertEquals(LibraryDestination.Library, returned.selectedNavigationDestination)
        assertEquals(LibraryDestination.Library, returned.onBack().destination)
    }

    @Test
    fun playlistDetailBackClearsSelectionAndReturnsDirectlyToTheCollectionOrigin() {
        val playlist = AppNavigationState(destination = LibraryDestination.Music).openPlaylist("mix")

        val returned = playlist.returnFromPlaylist()
        assertEquals(LibraryDestination.Music, returned.destination)
        assertNull(returned.selectedPlaylistId)
    }

    @Test
    fun collectionBackRetainsTheSelectionUnlessTheDetailHandlerClearsIt() {
        val playlist = AppNavigationState(destination = LibraryDestination.Library).openPlaylist("mix")

        val returned = playlist.onBack()
        assertEquals(LibraryDestination.Library, returned.destination)
        assertEquals("mix", returned.selectedPlaylistId)
    }

    @Test
    fun selectingABottomTabClearsOnlyThePlaylistSelection() {
        val playlist = AppNavigationState(destination = LibraryDestination.Library)
            .openPlaylist("mix")
            .openMusicBrowse(MusicLibraryView.Albums, "Album")

        val selected = playlist.selectDestination(LibraryDestination.Search)
        assertEquals(LibraryDestination.Search, selected.destination)
        assertNull(selected.selectedPlaylistId)
        assertEquals("Album", selected.browseGroupTitle)
        assertEquals(MusicLibraryView.Albums, selected.browseView)
        assertEquals(LibraryDestination.Playlists, selected.browseReturnDestination)
        assertEquals(LibraryDestination.Library, selected.collectionReturnDestination)
    }

    @Test
    fun eachAlbumsShortcutRequestsTheFilterEvenWhenAlreadyInTheLibrary() {
        val first = AppNavigationState().openLibraryAlbums()
        val second = first.openLibraryAlbums()

        assertEquals(LibraryDestination.Library, first.destination)
        assertEquals(LibraryCollectionFilter.Albums, first.requestedLibraryFilter)
        assertEquals(1L, first.libraryFilterRequestId)
        assertEquals(2L, second.libraryFilterRequestId)
    }

    @Test
    fun choosingAFolderStopsWholeLibraryBrowseWithoutLosingTheSelectedGroup() {
        val detail = AppNavigationState().openMusicBrowse(MusicLibraryView.Albums, "Album")

        val selected = detail.useSelectedFolder()
        assertFalse(selected.browseWholeLibrary)
        assertEquals("Album", selected.browseGroupTitle)
        assertTrue(selected.browseDirectGroup)
    }

    @Test
    fun rootDestinationBackReturnsHomeAndPreservesCollectionSelection() {
        for (destination in listOf(LibraryDestination.Search, LibraryDestination.Library, LibraryDestination.Settings)) {
            val returned = AppNavigationState(destination = destination, selectedPlaylistId = "mix").onBack()
            assertEquals(LibraryDestination.Music, returned.destination)
            assertEquals("mix", returned.selectedPlaylistId)
        }
    }

    @Test
    fun saveableScreenKeysDistinguishBrowseGroupsAndPlaylistSelection() {
        assertEquals("Music", AppNavigationState().screenKey)
        assertEquals("playlist:list", AppNavigationState(destination = LibraryDestination.Playlists).screenKey)
        assertEquals("playlist:mix", AppNavigationState().openPlaylist("mix").screenKey)
        assertEquals("browse:Songs:list", AppNavigationState().openMusicBrowse().screenKey)
        assertEquals("browse:Albums:Album", AppNavigationState().openMusicBrowse(MusicLibraryView.Albums, "Album").screenKey)
    }
}
