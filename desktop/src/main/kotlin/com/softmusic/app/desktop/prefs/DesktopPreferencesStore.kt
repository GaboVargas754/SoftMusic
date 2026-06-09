package com.softmusic.app.desktop.prefs

import com.softmusic.app.data.MusicPlaylist
import com.softmusic.app.player.PlaybackMode
import com.softmusic.app.player.SortMode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties

class DesktopPreferencesStore(
    private val configFile: Path = defaultConfigFile(),
) {
    fun load(): DesktopPreferences {
        if (!Files.exists(configFile)) return DesktopPreferences()

        val properties = Properties()
        runCatching {
            Files.newInputStream(configFile).use(properties::load)
        }.onFailure {
            return DesktopPreferences()
        }

        return DesktopPreferences(
            selectedFolderPath = properties.getProperty(KEY_SELECTED_FOLDER)?.takeIf { it.isNotBlank() },
            favoriteSongIds = properties.getProperty(KEY_FAVORITE_SONG_IDS)
                ?.split(',')
                ?.mapNotNull { it.toLongOrNull() }
                ?.toSet()
                ?: emptySet(),
            playlists = properties.readPlaylists(),
            settings = DesktopSettings(
                themeMode = properties.getProperty(KEY_THEME_MODE)
                    ?.let { value -> runCatching { DesktopThemeMode.valueOf(value) }.getOrNull() }
                    ?: DesktopThemeMode.Dark,
                showArtwork = properties.getProperty(KEY_SHOW_ARTWORK)?.toBooleanStrictOrNull() ?: true,
                scanOnStartup = properties.getProperty(KEY_SCAN_ON_STARTUP)?.toBooleanStrictOrNull() ?: true,
                volumePercent = properties.getProperty(KEY_VOLUME_PERCENT)
                    ?.toIntOrNull()
                    ?.coerceIn(MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT)
                    ?: DEFAULT_VOLUME_PERCENT,
                playbackMode = properties.getProperty(KEY_PLAYBACK_MODE)
                    ?.let { value -> runCatching { PlaybackMode.valueOf(value) }.getOrNull() }
                    ?: PlaybackMode.Ordered,
                sortMode = properties.getProperty(KEY_SORT_MODE)
                    ?.let { value -> runCatching { SortMode.valueOf(value) }.getOrNull() }
                    ?: SortMode.Recent,
                libraryView = properties.getProperty(KEY_LIBRARY_VIEW)
                    ?.let { value -> runCatching { DesktopLibraryView.valueOf(value) }.getOrNull() }
                    ?: DesktopLibraryView.Songs,
                folderFilterPath = properties.getProperty(KEY_FOLDER_FILTER_PATH)?.takeIf { it.isNotBlank() },
                searchQuery = properties.getProperty(KEY_SEARCH_QUERY).orEmpty(),
                djModeEnabled = properties.getProperty(KEY_DJ_MODE_ENABLED)?.toBooleanStrictOrNull() ?: false,
                djMixDurationSeconds = properties.getProperty(KEY_DJ_MIX_DURATION_SECONDS)
                    ?.toIntOrNull()
                    ?.coerceIn(MIN_DJ_MIX_SECONDS, MAX_DJ_MIX_SECONDS)
                    ?: MAX_DJ_MIX_SECONDS,
            ),
        )
    }

    fun save(preferences: DesktopPreferences): Result<Unit> = runCatching {
        Files.createDirectories(configFile.parent)
        val properties = Properties().apply {
            preferences.selectedFolderPath?.let { setProperty(KEY_SELECTED_FOLDER, it) }
            setProperty(KEY_FAVORITE_SONG_IDS, preferences.favoriteSongIds.joinToString(","))
            setProperty(KEY_THEME_MODE, preferences.settings.themeMode.name)
            setProperty(KEY_SHOW_ARTWORK, preferences.settings.showArtwork.toString())
            setProperty(KEY_SCAN_ON_STARTUP, preferences.settings.scanOnStartup.toString())
            setProperty(KEY_VOLUME_PERCENT, preferences.settings.volumePercent.coerceIn(MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT).toString())
            setProperty(KEY_PLAYBACK_MODE, preferences.settings.playbackMode.name)
            setProperty(KEY_SORT_MODE, preferences.settings.sortMode.name)
            setProperty(KEY_LIBRARY_VIEW, preferences.settings.libraryView.name)
            preferences.settings.folderFilterPath?.let { setProperty(KEY_FOLDER_FILTER_PATH, it) }
            setProperty(KEY_SEARCH_QUERY, preferences.settings.searchQuery)
            setProperty(KEY_DJ_MODE_ENABLED, preferences.settings.djModeEnabled.toString())
            setProperty(KEY_DJ_MIX_DURATION_SECONDS, preferences.settings.djMixDurationSeconds.coerceIn(MIN_DJ_MIX_SECONDS, MAX_DJ_MIX_SECONDS).toString())
            setProperty(KEY_PLAYLIST_COUNT, preferences.playlists.size.toString())
            preferences.playlists.forEachIndexed { index, playlist ->
                setProperty("$KEY_PLAYLIST_PREFIX.$index.id", playlist.id)
                setProperty("$KEY_PLAYLIST_PREFIX.$index.name", playlist.name)
                setProperty("$KEY_PLAYLIST_PREFIX.$index.song_ids", playlist.songIds.joinToString(","))
            }
        }
        Files.newOutputStream(configFile).use { output ->
            properties.store(output, "SoftMusic desktop preferences")
        }
    }

    private fun Properties.readPlaylists(): List<MusicPlaylist> {
        val count = getProperty(KEY_PLAYLIST_COUNT)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        return List(count) { index ->
            val id = getProperty("$KEY_PLAYLIST_PREFIX.$index.id").orEmpty()
            val name = getProperty("$KEY_PLAYLIST_PREFIX.$index.name").orEmpty()
            val songIds = getProperty("$KEY_PLAYLIST_PREFIX.$index.song_ids")
                ?.split(',')
                ?.mapNotNull { it.toLongOrNull() }
                ?: emptyList()
            MusicPlaylist(
                id = id,
                name = name,
                songIds = songIds,
            )
        }.filter { playlist -> playlist.id.isNotBlank() && playlist.name.isNotBlank() }
    }

    private companion object {
        const val KEY_SELECTED_FOLDER = "selected_folder"
        const val KEY_FAVORITE_SONG_IDS = "favorite_song_ids"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_SHOW_ARTWORK = "show_artwork"
        const val KEY_SCAN_ON_STARTUP = "scan_on_startup"
        const val KEY_VOLUME_PERCENT = "volume_percent"
        const val KEY_PLAYBACK_MODE = "playback_mode"
        const val KEY_SORT_MODE = "sort_mode"
        const val KEY_LIBRARY_VIEW = "library_view"
        const val KEY_FOLDER_FILTER_PATH = "folder_filter_path"
        const val KEY_SEARCH_QUERY = "search_query"
        const val KEY_DJ_MODE_ENABLED = "dj_mode_enabled"
        const val KEY_DJ_MIX_DURATION_SECONDS = "dj_mix_duration_seconds"
        const val KEY_PLAYLIST_COUNT = "playlist_count"
        const val KEY_PLAYLIST_PREFIX = "playlist"
        const val DEFAULT_VOLUME_PERCENT = 80
        const val MIN_VOLUME_PERCENT = 0
        const val MAX_VOLUME_PERCENT = 100
        const val MIN_DJ_MIX_SECONDS = 5
        const val MAX_DJ_MIX_SECONDS = 8

        fun defaultConfigFile(): Path {
            val xdgConfigHome = System.getenv("XDG_CONFIG_HOME")
                ?.takeIf { it.isNotBlank() }
                ?.let(Paths::get)
            val baseDir = xdgConfigHome ?: Paths.get(System.getProperty("user.home"), ".config")
            return baseDir.resolve("SoftMusic").resolve("desktop.properties")
        }
    }
}

data class DesktopPreferences(
    val selectedFolderPath: String? = null,
    val favoriteSongIds: Set<Long> = emptySet(),
    val playlists: List<MusicPlaylist> = emptyList(),
    val settings: DesktopSettings = DesktopSettings(),
)

data class DesktopSettings(
    val themeMode: DesktopThemeMode = DesktopThemeMode.Dark,
    val showArtwork: Boolean = true,
    val scanOnStartup: Boolean = true,
    val volumePercent: Int = 80,
    val playbackMode: PlaybackMode = PlaybackMode.Ordered,
    val sortMode: SortMode = SortMode.Recent,
    val libraryView: DesktopLibraryView = DesktopLibraryView.Songs,
    val folderFilterPath: String? = null,
    val searchQuery: String = "",
    val djModeEnabled: Boolean = false,
    val djMixDurationSeconds: Int = 8,
)

enum class DesktopThemeMode(val label: String) {
    System("Sistema"),
    Light("Claro"),
    Dark("Oscuro"),
    Midnight("Noche azul"),
    Forest("Bosque"),
    Sunset("Atardecer"),
    Lavender("Lavanda"),
    Graphite("Grafito"),
}

enum class DesktopLibraryView(val label: String) {
    Songs("Canciones"),
    Artists("Artistas"),
    Albums("Álbumes"),
    Folders("Carpetas"),
}
