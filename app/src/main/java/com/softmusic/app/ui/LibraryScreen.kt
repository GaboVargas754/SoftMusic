@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.softmusic.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softmusic.app.data.Song
import com.softmusic.app.player.PlayerUiState

internal enum class LibraryCollectionFilter(val label: String) {
    Playlists("Playlists"),
    Albums("Álbumes"),
    Artists("Artistas"),
}

private enum class LibraryCollectionKind(val searchLabel: String) {
    Favorites("playlist favoritos favoritas"),
    Playlist("playlist"),
    Album("álbum álbumes"),
    Artist("artista artistas"),
}

private data class LibraryLocalCollection(
    val key: String,
    val title: String,
    val subtitle: String,
    val kind: LibraryCollectionKind,
    val songs: List<Song>,
    val coverSongId: Long? = null,
    val playlistId: String? = null,
) {
    val searchText = buildString {
        append(title)
        append(' ')
        append(kind.searchLabel)
        songs.asSequence().map { it.artist }.distinct().forEach { artist ->
            append(' ')
            append(artist)
        }
    }.normalizedSearchText()

    fun isIncludedIn(filter: LibraryCollectionFilter?): Boolean = when (filter) {
        null -> true
        LibraryCollectionFilter.Playlists -> kind == LibraryCollectionKind.Favorites ||
            kind == LibraryCollectionKind.Playlist
        LibraryCollectionFilter.Albums -> kind == LibraryCollectionKind.Album
        LibraryCollectionFilter.Artists -> kind == LibraryCollectionKind.Artist
    }
}

@Composable
internal fun LocalLibraryScreen(
    uiState: PlayerUiState,
    modifier: Modifier = Modifier,
    contentBottomPadding: Dp = 144.dp,
    onOpenMenu: () -> Unit,
    onCreatePlaylist: (String) -> Boolean,
    onOpenFavorites: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenSongs: () -> Unit,
    requestedFilter: LibraryCollectionFilter? = null,
    filterRequestId: Long = 0L,
) {
    val highPerformanceMode = LocalHighPerformanceMode.current
    val fadeSpec = softMusicMotionSpec<Float>(durationMillis = 180)
    val searchSizeSpec = softMusicMotionSpec<IntSize>(durationMillis = 180)
    val placementSpec = softMusicMotionSpec<IntOffset>(durationMillis = 220)
    var selectedFilterName by rememberSaveable { mutableStateOf<String?>(null) }
    var sortByName by rememberSaveable { mutableStateOf(false) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var showCreatePlaylist by rememberSaveable { mutableStateOf(false) }
    var lastAppliedRequestId by rememberSaveable { mutableStateOf(0L) }
    val listState = rememberLazyListState()
    val selectedFilter = LibraryCollectionFilter.entries.firstOrNull {
        it.name == selectedFilterName
    }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dismissKeyboard = {
        focusManager.clearFocus()
        keyboardController?.hide()
        Unit
    }
    val closeSearch = {
        searchVisible = false
        query = ""
        dismissKeyboard()
    }

    LaunchedEffect(filterRequestId) {
        if (filterRequestId != lastAppliedRequestId) {
            selectedFilterName = requestedFilter?.name
            searchVisible = false
            query = ""
            dismissKeyboard()
            lastAppliedRequestId = filterRequestId
            listState.scrollToItem(0)
        }
    }

    BackHandler(enabled = searchVisible && LocalNavigationBackEnabled.current, onBack = closeSearch)

    val librarySongs = remember(
        uiState.songs,
        uiState.hiddenFolderPaths,
        uiState.excludeSmallAudios,
    ) { uiState.visibleLibrarySongs() }
    val favoriteSongs = remember(librarySongs, uiState.favoriteSongIds, uiState.sortMode) {
        uiState.favoriteSongs()
    }
    val collections = remember(
        librarySongs,
        favoriteSongs,
        uiState.playlists,
        uiState.sortMode,
        uiState.hiddenFolderPaths,
        uiState.excludeSmallAudios,
    ) {
        buildList {
            add(
                LibraryLocalCollection(
                    key = "library:favorites",
                    title = "Tus favoritos",
                    subtitle = "Playlist · ${favoriteSongs.size.songCountLabel()}",
                    kind = LibraryCollectionKind.Favorites,
                    songs = favoriteSongs,
                ),
            )
            uiState.playlists.forEach { playlist ->
                val songs = playlist.songsFrom(
                    songs = librarySongs,
                    hiddenFolderPaths = uiState.hiddenFolderPaths,
                    excludeSmallAudios = uiState.excludeSmallAudios,
                )
                add(
                    LibraryLocalCollection(
                        key = "library:playlist:${playlist.id}",
                        title = playlist.name,
                        subtitle = "Playlist · ${songs.size.songCountLabel()}",
                        kind = LibraryCollectionKind.Playlist,
                        songs = songs,
                        coverSongId = playlist.coverSongId,
                        playlistId = playlist.id,
                    ),
                )
            }
            librarySongs.toMusicGroups(MusicLibraryView.Albums, uiState.sortMode).forEach { album ->
                add(
                    LibraryLocalCollection(
                        key = "library:album:${album.title}",
                        title = album.title,
                        subtitle = "Álbum · ${album.subtitle}",
                        kind = LibraryCollectionKind.Album,
                        songs = album.songs,
                    ),
                )
            }
            librarySongs.toMusicGroups(MusicLibraryView.Artists, uiState.sortMode).forEach { artist ->
                add(
                    LibraryLocalCollection(
                        key = "library:artist:${artist.title}",
                        title = artist.title,
                        subtitle = "Artista · ${artist.songs.size.songCountLabel()}",
                        kind = LibraryCollectionKind.Artist,
                        songs = artist.songs,
                    ),
                )
            }
        }
    }
    val searchTerms = remember(query) {
        query.normalizedSearchText().split(Regex("\\s+")).filter { it.isNotBlank() }
    }
    val visibleCollections = remember(collections, selectedFilter, sortByName, searchTerms) {
        val filtered = collections.filter { collection ->
            collection.isIncludedIn(selectedFilter) &&
                searchTerms.all { term -> collection.searchText.contains(term) }
        }
        if (sortByName) {
            filtered.sortedWith(
                compareBy<LibraryLocalCollection> { it.kind != LibraryCollectionKind.Favorites }
                    .thenBy { it.title.normalizedSearchText() },
            )
        } else {
            filtered
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeMenuButton(onClick = onOpenMenu)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Tu biblioteca",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(
                onClick = {
                    if (searchVisible) closeSearch() else searchVisible = true
                },
            ) {
                Icon(
                    imageVector = if (searchVisible) MusicIcons.Close else MusicIcons.Search,
                    contentDescription = if (searchVisible) "Cerrar búsqueda" else "Buscar en tu biblioteca",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = {
                    dismissKeyboard()
                    showCreatePlaylist = true
                },
            ) {
                Icon(
                    imageVector = MusicIcons.Add,
                    contentDescription = "Crear playlist",
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AnimatedVisibility(
            visible = searchVisible,
            enter = if (highPerformanceMode) EnterTransition.None else {
                fadeIn(animationSpec = fadeSpec) +
                    expandVertically(animationSpec = searchSizeSpec, expandFrom = Alignment.Top)
            },
            exit = if (highPerformanceMode) ExitTransition.None else {
                fadeOut(animationSpec = fadeSpec) +
                    shrinkVertically(animationSpec = searchSizeSpec, shrinkTowards = Alignment.Top)
            },
        ) {
            LaunchedEffect(searchVisible) {
                if (searchVisible) {
                    withFrameNanos { }
                    focusRequester.requestFocus()
                }
            }
            LibraryLocalSearchField(
                query = query,
                onQueryChange = { query = it },
                onSearch = dismissKeyboard,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .focusRequester(focusRequester),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibraryCollectionFilter.entries.forEach { filter ->
                LibraryLocalFilterChip(
                    label = filter.label,
                    selected = selectedFilter == filter,
                    onClick = {
                        selectedFilterName = if (selectedFilter == filter) null else filter.name
                    },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LibraryLocalSortMenu(
                sortByName = sortByName,
                onSortChange = { sortByName = it },
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = when (selectedFilter) {
                    LibraryCollectionFilter.Playlists -> visibleCollections.size.playlistCountLabel()
                    LibraryCollectionFilter.Albums -> visibleCollections.size.albumCountLabel()
                    LibraryCollectionFilter.Artists -> visibleCollections.size.artistCountLabel()
                    null -> if (visibleCollections.size == 1) "1 colección" else "${visibleCollections.size} colecciones"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = contentBottomPadding + 24.dp),
        ) {
            items(items = visibleCollections, key = { it.key }, contentType = { "library-collection" }) { collection ->
                LibraryLocalCollectionRow(
                    collection = collection,
                    modifier = if (highPerformanceMode) Modifier else {
                        Modifier.animateItem(
                            fadeInSpec = fadeSpec,
                            placementSpec = placementSpec,
                            fadeOutSpec = fadeSpec,
                        )
                    },
                    onClick = {
                        dismissKeyboard()
                        when (collection.kind) {
                            LibraryCollectionKind.Favorites -> onOpenFavorites()
                            LibraryCollectionKind.Playlist -> collection.playlistId?.let(onOpenPlaylist)
                            LibraryCollectionKind.Album -> onOpenAlbum(collection.title)
                            LibraryCollectionKind.Artist -> onOpenArtist(collection.title)
                        }
                    },
                )
            }
            when {
                searchTerms.isNotEmpty() && visibleCollections.isEmpty() -> item(key = "library:no-results") {
                    LibraryLocalEmptyState(
                        title = "No hay resultados",
                        description = "Prueba con otro nombre o artista.",
                        actionLabel = "Borrar búsqueda",
                        onAction = { query = "" },
                        searching = true,
                    )
                }
                librarySongs.isEmpty() && uiState.isLoading -> item(key = "library:loading") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Buscando tu música…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                        )
                    }
                }
                librarySongs.isEmpty() && searchTerms.isEmpty() -> item(key = "library:empty") {
                    LibraryLocalEmptyState(
                        title = "Tu música, en un solo lugar",
                        description = "Abre Canciones para ver la música de tu dispositivo y organizar tu biblioteca.",
                        actionLabel = "Ver canciones",
                        onAction = onOpenSongs,
                    )
                }
            }
        }
    }

    if (showCreatePlaylist) {
        CreatePlaylistDialog(
            existingPlaylistNames = uiState.playlists.map { it.name },
            onCreatePlaylist = onCreatePlaylist,
            onDismiss = { showCreatePlaylist = false },
        )
    }
}

@Composable
private fun LibraryLocalSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        placeholder = { Text("Buscar por nombre o artista", fontSize = 14.sp) },
        leadingIcon = { Icon(MusicIcons.Search, contentDescription = null, modifier = Modifier.size(21.dp)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(MusicIcons.Close, contentDescription = "Borrar búsqueda", modifier = Modifier.size(20.dp))
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
    )
}

@Composable
private fun LibraryLocalFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.075f)
        },
        animationSpec = softMusicMotionSpec(durationMillis = 180),
        label = "Library filter container",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        animationSpec = softMusicMotionSpec(durationMillis = 180),
        label = "Library filter content",
    )
    Surface(
        modifier = Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() }),
        color = containerColor,
        shape = CircleShape,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun LibraryLocalSortMenu(
    sortByName: Boolean,
    onSortChange: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Icon(
                imageVector = MusicIcons.Sort,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = if (sortByName) "Nombre" else "Orden original",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = MusicIcons.ChevronDown,
                contentDescription = "Cambiar orden",
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(false to "Orden original", true to "Nombre").forEach { (byName, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSortChange(byName)
                        expanded = false
                    },
                    trailingIcon = {
                        if (sortByName == byName) {
                            Icon(MusicIcons.CheckSelected, contentDescription = "Seleccionado", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun LibraryLocalCollectionRow(
    collection: LibraryLocalCollection,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Abrir ${collection.title}", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CollectionArtwork(
            title = collection.title,
            songs = collection.songs,
            modifier = Modifier.size(64.dp),
            coverSongId = collection.coverSongId,
            favorite = collection.kind == LibraryCollectionKind.Favorites,
            artist = collection.kind == LibraryCollectionKind.Artist,
            cornerRadius = 5.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = collection.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = collection.subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LibraryLocalEmptyState(
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
    searching: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = if (searching) MusicIcons.Search else MusicIcons.Library,
            contentDescription = null,
            modifier = Modifier.size(34.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}
