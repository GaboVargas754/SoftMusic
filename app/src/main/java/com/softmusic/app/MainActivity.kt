package com.softmusic.app

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.softmusic.app.player.MusicService
import com.softmusic.app.player.MusicViewModel
import com.softmusic.app.player.PlaybackMode
import com.softmusic.app.player.SortMode
import com.softmusic.app.ui.SoftMusicApp
import com.softmusic.app.ui.theme.AppColorPalette
import com.softmusic.app.ui.theme.AppThemeMode
import com.softmusic.app.ui.theme.SoftMusicTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val preferences = remember { getSharedPreferences(THEME_PREFS_NAME, MODE_PRIVATE) }
            var themeMode by remember { mutableStateOf(preferences.readThemeMode()) }
            var colorPalette by remember { mutableStateOf(preferences.readColorPalette()) }
            var highPerformanceMode by remember { mutableStateOf(preferences.getBoolean(KEY_HIGH_PERFORMANCE_MODE, false)) }
            var defaultFolderPath by remember { mutableStateOf(preferences.getString(KEY_DEFAULT_FOLDER_PATH, null)) }
            var defaultPlaybackMode by remember { mutableStateOf(preferences.readDefaultPlaybackMode()) }
            var defaultSortMode by remember { mutableStateOf(preferences.readDefaultSortMode()) }
            var fontScale by remember { mutableStateOf(preferences.readFontScale()) }
            var hiddenFolderPaths by remember { mutableStateOf(preferences.readHiddenFolderPaths()) }
            var djModeEnabled by remember { mutableStateOf(preferences.getBoolean(KEY_DJ_MODE_ENABLED, false)) }
            var djMixDurationSeconds by remember { mutableStateOf(preferences.readDjMixDurationSeconds()) }

            SoftMusicTheme(
                themeMode = themeMode,
                colorPalette = colorPalette,
            ) {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        density = baseDensity.density,
                        fontScale = baseDensity.fontScale * fontScale,
                    ),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                    SoftMusicRoot(
                        hasAudioPermission = hasAudioPermission(),
                        requestAudioPermission = ::audioPermissionName,
                        hasNotificationPermission = hasNotificationPermission(),
                        requestNotificationPermission = ::notificationPermissionName,
                        themeMode = themeMode,
                            colorPalette = colorPalette,
                            highPerformanceMode = highPerformanceMode,
                            defaultFolderPath = defaultFolderPath,
                            defaultPlaybackMode = defaultPlaybackMode,
                            defaultSortMode = defaultSortMode,
                            fontScale = fontScale,
                            hiddenFolderPaths = hiddenFolderPaths,
                            djModeEnabled = djModeEnabled,
                            djMixDurationSeconds = djMixDurationSeconds,
                            onThemeModeChange = { mode ->
                                themeMode = mode
                                preferences.edit().putString(KEY_THEME_MODE, mode.name).apply()
                            },
                            onColorPaletteChange = { palette ->
                                colorPalette = palette
                                preferences.edit().putString(KEY_COLOR_PALETTE, palette.name).apply()
                            },
                            onHighPerformanceModeChange = { enabled ->
                                highPerformanceMode = enabled
                                preferences.edit().putBoolean(KEY_HIGH_PERFORMANCE_MODE, enabled).apply()
                            },
                            onDefaultFolderChange = { folderPath ->
                                defaultFolderPath = folderPath
                                preferences.edit().apply {
                                    if (folderPath == null) {
                                        remove(KEY_DEFAULT_FOLDER_PATH)
                                    } else {
                                        putString(KEY_DEFAULT_FOLDER_PATH, folderPath)
                                    }
                                }.apply()
                            },
                            onDefaultPlaybackModeChange = { playbackMode ->
                                defaultPlaybackMode = playbackMode
                                preferences.edit().putString(KEY_DEFAULT_PLAYBACK_MODE, playbackMode.name).apply()
                            },
                            onDefaultSortModeChange = { sortMode ->
                                defaultSortMode = sortMode
                                preferences.edit().putString(KEY_DEFAULT_SORT_MODE, sortMode.name).apply()
                            },
                            onFontScaleChange = { scale ->
                                fontScale = scale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
                                preferences.edit().putFloat(KEY_FONT_SCALE, fontScale).apply()
                            },
                            onHiddenFolderPathsChange = { folderPaths ->
                                hiddenFolderPaths = folderPaths
                                preferences.edit().apply {
                                    putStringSet(KEY_HIDDEN_FOLDER_PATHS, folderPaths)
                                    if (defaultFolderPath in folderPaths) {
                                        defaultFolderPath = null
                                        remove(KEY_DEFAULT_FOLDER_PATH)
                                    }
                                }.apply()
                            },
                            onDjModeChange = { enabled ->
                                djModeEnabled = enabled
                                preferences.edit().putBoolean(KEY_DJ_MODE_ENABLED, enabled).apply()
                            },
                            onDjMixDurationChange = { seconds ->
                                djMixDurationSeconds = seconds.coerceIn(MIN_DJ_MIX_SECONDS, MAX_DJ_MIX_SECONDS)
                                preferences.edit().putInt(KEY_DJ_MIX_DURATION_SECONDS, djMixDurationSeconds).apply()
                            },
                            onOpenNotificationSettings = ::openNotificationSettings,
                            onOpenAppSettings = ::openAppSettings,
                            onCloseApp = { closeApplication() },
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing && !isChangingConfigurations) {
            stopMusicService()
        }
        super.onDestroy()
    }

    private fun closeApplication() {
        stopMusicService()
        finishAndRemoveTask()
    }

    private fun stopMusicService() {
        stopService(Intent(this, MusicService::class.java))
    }

    private fun hasAudioPermission(): Boolean {
        val permission = audioPermissionName()
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun audioPermissionName(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    private fun hasNotificationPermission(): Boolean {
        val permission = notificationPermissionName() ?: return true
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun notificationPermissionName(): String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.POST_NOTIFICATIONS
    } else {
        null
    }

    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        runCatching { startActivity(intent) }
            .onFailure { openAppSettings() }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:$packageName"))
        startActivity(intent)
    }
}

@Composable
private fun SoftMusicRoot(
    hasAudioPermission: Boolean,
    requestAudioPermission: () -> String,
    hasNotificationPermission: Boolean,
    requestNotificationPermission: () -> String?,
    themeMode: AppThemeMode,
    colorPalette: AppColorPalette,
    highPerformanceMode: Boolean,
    defaultFolderPath: String?,
    defaultPlaybackMode: PlaybackMode,
    defaultSortMode: SortMode,
    fontScale: Float,
    hiddenFolderPaths: Set<String>,
    djModeEnabled: Boolean,
    djMixDurationSeconds: Int,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onColorPaletteChange: (AppColorPalette) -> Unit,
    onHighPerformanceModeChange: (Boolean) -> Unit,
    onDefaultFolderChange: (String?) -> Unit,
    onDefaultPlaybackModeChange: (PlaybackMode) -> Unit,
    onDefaultSortModeChange: (SortMode) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onHiddenFolderPathsChange: (Set<String>) -> Unit,
    onDjModeChange: (Boolean) -> Unit,
    onDjMixDurationChange: (Int) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onCloseApp: () -> Unit,
) {
    val viewModel: MusicViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var permissionGranted by remember { mutableStateOf(hasAudioPermission) }
    var notificationPermissionGranted by remember { mutableStateOf(hasNotificationPermission) }
    var defaultsApplied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationPermissionGranted = granted
    }

    LaunchedEffect(Unit) {
        viewModel.applyDefaultPreferences(
            defaultFolderPath = defaultFolderPath,
            defaultPlaybackMode = defaultPlaybackMode,
            defaultSortMode = defaultSortMode,
            hiddenFolderPaths = hiddenFolderPaths,
            djModeEnabled = djModeEnabled,
            djMixDurationSeconds = djMixDurationSeconds,
        )
        defaultsApplied = true
    }

    LaunchedEffect(permissionGranted, defaultsApplied) {
        if (permissionGranted && defaultsApplied) {
            viewModel.loadSongs()
        }
    }

    LaunchedEffect(defaultsApplied, uiState.hiddenFolderPaths, hiddenFolderPaths) {
        if (defaultsApplied && uiState.allFolders.isNotEmpty() && uiState.hiddenFolderPaths != hiddenFolderPaths) {
            onHiddenFolderPathsChange(uiState.hiddenFolderPaths)
        }
    }

    LaunchedEffect(permissionGranted, notificationPermissionGranted) {
        val notificationPermission = requestNotificationPermission()
        if (permissionGranted && !notificationPermissionGranted && notificationPermission != null) {
            notificationPermissionLauncher.launch(notificationPermission)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SoftMusicApp(
            uiState = uiState,
            playbackProgressState = viewModel.progressState,
            hasAudioPermission = permissionGranted,
            hasNotificationPermission = notificationPermissionGranted,
            onRequestPermission = { permissionLauncher.launch(requestAudioPermission()) },
            onRequestNotificationPermission = {
                val notificationPermission = requestNotificationPermission()
                if (notificationPermission == null) {
                    notificationPermissionGranted = true
                } else {
                    notificationPermissionLauncher.launch(notificationPermission)
                }
            },
            onRefreshLibrary = viewModel::loadSongs,
            onPlaySong = viewModel::playSong,
            onPlayList = viewModel::playVisibleList,
            onPlayQueuedSong = viewModel::playQueuedSong,
            onPlayQueue = viewModel::playQueue,
            onToggleFavorite = viewModel::toggleFavorite,
            onPlayNext = viewModel::playNext,
            onPlayAtEnd = viewModel::playAtEnd,
            onCreatePlaylist = viewModel::createPlaylist,
            onDeletePlaylist = viewModel::deletePlaylist,
            onAddSongToPlaylist = viewModel::addSongToPlaylist,
            onRemoveSongFromPlaylist = viewModel::removeSongFromPlaylist,
            onTogglePlayPause = viewModel::togglePlayPause,
            onNext = viewModel::next,
            onPrevious = viewModel::previous,
            onSeek = viewModel::seekTo,
            onPlaybackModeChange = viewModel::setPlaybackMode,
            onSortModeChange = viewModel::setSortMode,
            onFolderChange = viewModel::setSelectedFolder,
            themeMode = themeMode,
            colorPalette = colorPalette,
            highPerformanceMode = highPerformanceMode,
            defaultFolderPath = defaultFolderPath,
            defaultPlaybackMode = defaultPlaybackMode,
            defaultSortMode = defaultSortMode,
            fontScale = fontScale,
            djModeEnabled = djModeEnabled,
            djMixDurationSeconds = djMixDurationSeconds,
            onThemeModeChange = onThemeModeChange,
            onColorPaletteChange = onColorPaletteChange,
            onHighPerformanceModeChange = onHighPerformanceModeChange,
            onDefaultFolderChange = onDefaultFolderChange,
            onDefaultPlaybackModeChange = onDefaultPlaybackModeChange,
            onDefaultSortModeChange = onDefaultSortModeChange,
            onFontScaleChange = onFontScaleChange,
            onHiddenFolderChange = { folderPath, hidden ->
                val updated = if (hidden) {
                    hiddenFolderPaths + folderPath
                } else {
                    hiddenFolderPaths - folderPath
                }
                onHiddenFolderPathsChange(updated)
                viewModel.setHiddenFolderPaths(updated)
            },
            onDjModeChange = { enabled ->
                onDjModeChange(enabled)
                viewModel.setDjModeConfig(enabled, djMixDurationSeconds)
            },
            onDjMixDurationChange = { seconds ->
                val safeSeconds = seconds.coerceIn(MIN_DJ_MIX_SECONDS, MAX_DJ_MIX_SECONDS)
                onDjMixDurationChange(safeSeconds)
                viewModel.setDjModeConfig(djModeEnabled, safeSeconds)
            },
            onOpenNotificationSettings = onOpenNotificationSettings,
            onOpenAppSettings = onOpenAppSettings,
            onCloseApp = {
                viewModel.shutdownPlayback()
                onCloseApp()
            },
        )
    }
}

private const val THEME_PREFS_NAME = "theme_settings"
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_COLOR_PALETTE = "color_palette"
private const val KEY_HIGH_PERFORMANCE_MODE = "high_performance_mode"
private const val KEY_DEFAULT_FOLDER_PATH = "default_folder_path"
private const val KEY_DEFAULT_PLAYBACK_MODE = "default_playback_mode"
private const val KEY_DEFAULT_SORT_MODE = "default_sort_mode"
private const val KEY_FONT_SCALE = "font_scale"
private const val KEY_HIDDEN_FOLDER_PATHS = "hidden_folder_paths"
private const val KEY_DJ_MODE_ENABLED = "dj_mode_enabled"
private const val KEY_DJ_MIX_DURATION_SECONDS = "dj_mix_duration_seconds"
private const val MIN_FONT_SCALE = 0.80f
private const val MAX_FONT_SCALE = 1.20f
private const val MIN_DJ_MIX_SECONDS = 5
private const val MAX_DJ_MIX_SECONDS = 8

private fun SharedPreferences.readThemeMode(): AppThemeMode = enumValueOrDefault(
    value = getString(KEY_THEME_MODE, null),
    default = AppThemeMode.System,
)

private fun SharedPreferences.readColorPalette(): AppColorPalette = enumValueOrDefault(
    value = getString(KEY_COLOR_PALETTE, null),
    default = AppColorPalette.Green,
)

private fun SharedPreferences.readDefaultPlaybackMode(): PlaybackMode {
    val value = getString(KEY_DEFAULT_PLAYBACK_MODE, null)
    if (value == "AbsoluteShuffle") return PlaybackMode.Shuffle
    return enumValueOrDefault(
        value = value,
        default = PlaybackMode.Ordered,
    )
}

private fun SharedPreferences.readDefaultSortMode(): SortMode = enumValueOrDefault(
    value = getString(KEY_DEFAULT_SORT_MODE, null),
    default = SortMode.Recent,
)

private fun SharedPreferences.readFontScale(): Float = getFloat(KEY_FONT_SCALE, 1f)
    .coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)

private fun SharedPreferences.readHiddenFolderPaths(): Set<String> = getStringSet(KEY_HIDDEN_FOLDER_PATHS, emptySet())
    .orEmpty()
    .toSet()

private fun SharedPreferences.readDjMixDurationSeconds(): Int = getInt(KEY_DJ_MIX_DURATION_SECONDS, 8)
    .coerceIn(MIN_DJ_MIX_SECONDS, MAX_DJ_MIX_SECONDS)

private inline fun <reified T : Enum<T>> enumValueOrDefault(
    value: String?,
    default: T,
): T = runCatching {
    enumValueOf<T>(value ?: default.name)
}.getOrDefault(default)
