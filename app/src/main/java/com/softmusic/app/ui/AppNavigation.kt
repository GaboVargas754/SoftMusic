package com.softmusic.app.ui

internal enum class LibraryDestination {
    Music,
    Browse,
    Search,
    Library,
    Favorites,
    Playlists,
    History,
    Settings,
}

/** Pure navigation decisions; the composition owns the existing saveable state slots. */
internal data class AppNavigationState(
    val destination: LibraryDestination = LibraryDestination.Music,
    val requestedLibraryFilter: LibraryCollectionFilter? = null,
    val libraryFilterRequestId: Long = 0L,
    val browseView: MusicLibraryView = MusicLibraryView.Songs,
    val browseGroupTitle: String? = null,
    val browseReturnDestination: LibraryDestination = LibraryDestination.Music,
    val browseDirectGroup: Boolean = false,
    val browseWholeLibrary: Boolean = false,
    val collectionReturnDestination: LibraryDestination = LibraryDestination.Library,
    val selectedPlaylistId: String? = null,
    val historyView: ListeningHistoryView = ListeningHistoryView.Recent,
    val historyReturnDestination: LibraryDestination = LibraryDestination.Music,
) {
    val screenKey: String
        get() = when (destination) {
            LibraryDestination.Browse -> "browse:${browseView.name}:${browseGroupTitle ?: "list"}"
            LibraryDestination.Playlists -> "playlist:${selectedPlaylistId ?: "list"}"
            LibraryDestination.History -> "history:${historyView.key}"
            else -> destination.name
        }

    val selectedNavigationDestination: LibraryDestination
        get() = when (destination) {
            LibraryDestination.Browse -> when (browseReturnDestination) {
                LibraryDestination.Favorites, LibraryDestination.Playlists -> collectionReturnDestination
                else -> browseReturnDestination
            }
            LibraryDestination.Favorites, LibraryDestination.Playlists -> collectionReturnDestination
            LibraryDestination.History -> historyReturnDestination
            else -> destination
        }

    fun openMusicBrowse(
        view: MusicLibraryView = MusicLibraryView.Songs,
        groupTitle: String? = null,
    ): AppNavigationState = copy(
        browseReturnDestination = destination,
        browseView = view,
        browseGroupTitle = groupTitle,
        browseDirectGroup = groupTitle != null,
        browseWholeLibrary = view != MusicLibraryView.Songs,
        destination = LibraryDestination.Browse,
    )

    fun returnFromBrowse(): AppNavigationState = if (browseGroupTitle != null && !browseDirectGroup) {
        copy(browseGroupTitle = null)
    } else {
        copy(destination = browseReturnDestination)
    }

    fun selectBrowseView(view: MusicLibraryView): AppNavigationState = copy(
        browseView = view,
        browseGroupTitle = null,
        browseDirectGroup = false,
    )

    fun selectBrowseGroup(title: String?): AppNavigationState = copy(
        browseGroupTitle = title,
        browseDirectGroup = false,
    )

    fun useSelectedFolder(): AppNavigationState = copy(browseWholeLibrary = false)

    fun openLibraryAlbums(): AppNavigationState = copy(
        requestedLibraryFilter = LibraryCollectionFilter.Albums,
        libraryFilterRequestId = libraryFilterRequestId + 1L,
        destination = LibraryDestination.Library,
    )

    fun openFavorites(): AppNavigationState = copy(
        collectionReturnDestination = destination,
        destination = LibraryDestination.Favorites,
    )

    fun openPlaylist(playlistId: String): AppNavigationState = copy(
        collectionReturnDestination = destination,
        selectedPlaylistId = playlistId,
        destination = LibraryDestination.Playlists,
    )

    fun returnFromCollection(): AppNavigationState = copy(destination = collectionReturnDestination)

    fun openHistory(view: ListeningHistoryView): AppNavigationState = copy(
        historyView = view,
        historyReturnDestination = destination,
        destination = LibraryDestination.History,
    )

    fun selectHistoryView(view: ListeningHistoryView): AppNavigationState = copy(historyView = view)

    fun returnFromHistory(): AppNavigationState = copy(destination = historyReturnDestination)

    fun returnFromPlaylist(): AppNavigationState = copy(
        selectedPlaylistId = null,
        destination = collectionReturnDestination,
    )

    fun selectDestination(selectedDestination: LibraryDestination): AppNavigationState = copy(
        selectedPlaylistId = null,
        destination = selectedDestination,
    )

    fun onBack(): AppNavigationState = when (destination) {
        LibraryDestination.Browse -> returnFromBrowse()
        LibraryDestination.Favorites, LibraryDestination.Playlists -> returnFromCollection()
        LibraryDestination.History -> returnFromHistory()
        else -> copy(destination = LibraryDestination.Music)
    }
}
