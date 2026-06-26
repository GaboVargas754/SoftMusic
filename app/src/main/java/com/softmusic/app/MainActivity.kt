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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.softmusic.app.player.DjMixMode
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
            var excludeSmallAudios by remember { mutableStateOf(preferences.getBoolean(KEY_EXCLUDE_SMALL_AUDIOS, true)) }
            var djModeEnabled by remember { mutableStateOf(preferences.getBoolean(KEY_DJ_MODE_ENABLED, false)) }
            var djMixMode by remember { mutableStateOf(preferences.readDjMixMode()) }
            var djMixDurationSeconds by remember { mutableStateOf(preferences.readDjMixDurationSeconds()) }
            var audioPermissionRequested by remember { mutableStateOf(preferences.getBoolean(KEY_AUDIO_PERMISSION_REQUESTED, false)) }

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
                        checkAudioPermission = ::hasAudioPermission,
                        audioPermissionName = ::audioPermissionName,
                        shouldShowAudioPermissionRationale = ::shouldShowAudioPermissionRationale,
                        audioPermissionRequested = audioPermissionRequested,
                        onAudioPermissionRequested = {
                            audioPermissionRequested = true
                            preferences.edit().putBoolean(KEY_AUDIO_PERMISSION_REQUESTED, true).apply()
                        },
                        checkNotificationPermission = ::hasNotificationPermission,
                        notificationPermissionName = ::notificationPermissionName,
                        themeMode = themeMode,
                            colorPalette = colorPalette,
                            highPerformanceMode = highPerformanceMode,
                            defaultFolderPath = defaultFolderPath,
                            defaultPlaybackMode = defaultPlaybackMode,
                            defaultSortMode = defaultSortMode,
                            fontScale = fontScale,
                            hiddenFolderPaths = hiddenFolderPaths,
                            excludeSmallAudios = excludeSmallAudios,
                            djModeEnabled = djModeEnabled,
                            djMixMode = djMixMode,
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
                            onExcludeSmallAudiosChange = { enabled ->
                                excludeSmallAudios = enabled
                                preferences.edit().putBoolean(KEY_EXCLUDE_SMALL_AUDIOS, enabled).apply()
                            },
                            onDjModeChange = { enabled ->
                                djModeEnabled = enabled
                                preferences.edit().putBoolean(KEY_DJ_MODE_ENABLED, enabled).apply()
                            },
                            onDjMixModeChange = { mode ->
                                djMixMode = mode
                                preferences.edit().putString(KEY_DJ_MIX_MODE, mode.name).apply()
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

    private fun shouldShowAudioPermissionRationale(): Boolean = shouldShowRequestPermissionRationale(audioPermissionName())

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
    checkAudioPermission: () -> Boolean,
    audioPermissionName: () -> String,
    shouldShowAudioPermissionRationale: () -> Boolean,
    audioPermissionRequested: Boolean,
    onAudioPermissionRequested: () -> Unit,
    checkNotificationPermission: () -> Boolean,
    notificationPermissionName: () -> String?,
    themeMode: AppThemeMode,
    colorPalette: AppColorPalette,
    highPerformanceMode: Boolean,
    defaultFolderPath: String?,
    defaultPlaybackMode: PlaybackMode,
    defaultSortMode: SortMode,
    fontScale: Float,
    hiddenFolderPaths: Set<String>,
    excludeSmallAudios: Boolean,
    djModeEnabled: Boolean,
    djMixMode: DjMixMode,
    djMixDurationSeconds: Int,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onColorPaletteChange: (AppColorPalette) -> Unit,
    onHighPerformanceModeChange: (Boolean) -> Unit,
    onDefaultFolderChange: (String?) -> Unit,
    onDefaultPlaybackModeChange: (PlaybackMode) -> Unit,
    onDefaultSortModeChange: (SortMode) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onHiddenFolderPathsChange: (Set<String>) -> Unit,
    onExcludeSmallAudiosChange: (Boolean) -> Unit,
    onDjModeChange: (Boolean) -> Unit,
    onDjMixModeChange: (DjMixMode) -> Unit,
    onDjMixDurationChange: (Int) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onCloseApp: () -> Unit,
) {
    val viewModel: MusicViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionGranted by remember { mutableStateOf(checkAudioPermission()) }
    var showAudioPermissionRationale by remember { mutableStateOf(shouldShowAudioPermissionRationale()) }
    var notificationPermissionGranted by remember { mutableStateOf(checkNotificationPermission()) }
    var defaultsApplied by remember { mutableStateOf(false) }

    fun refreshPermissionState() {
        permissionGranted = checkAudioPermission()
        showAudioPermissionRationale = shouldShowAudioPermissionRationale()
        notificationPermissionGranted = checkNotificationPermission()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        refreshPermissionState()
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        refreshPermissionState()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissionState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.applyDefaultPreferences(
            defaultFolderPath = defaultFolderPath,
            defaultPlaybackMode = defaultPlaybackMode,
            defaultSortMode = defaultSortMode,
            hiddenFolderPaths = hiddenFolderPaths,
            excludeSmallAudios = excludeSmallAudios,
            djModeEnabled = djModeEnabled,
            djMixMode = djMixMode,
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

    Box(modifier = Modifier.fillMaxSize()) {
        SoftMusicApp(
            uiState = uiState,
            playbackProgressState = viewModel.progressState,
            hasAudioPermission = permissionGranted,
            showAudioPermissionRationale = showAudioPermissionRationale,
            audioPermissionPermanentlyDenied = !permissionGranted && audioPermissionRequested && !showAudioPermissionRationale,
            hasNotificationPermission = notificationPermissionGranted,
            onRequestPermission = {
                onAudioPermissionRequested()
                permissionLauncher.launch(audioPermissionName())
            },
            onRequestNotificationPermission = {
                val notificationPermission = notificationPermissionName()
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
            djMixMode = djMixMode,
            djMixDurationSeconds = djMixDurationSeconds,
            onThemeModeChange = onThemeModeChange,
            onColorPaletteChange = onColorPaletteChange,
            onHighPerformanceModeChange = onHighPerformanceModeChange,
            onDefaultFolderChange = onDefaultFolderChange,
            onDefaultPlaybackModeChange = onDefaultPlaybackModeChange,
            onDefaultSortModeChange = onDefaultSortModeChange,
            onFontScaleChange = onFontScaleChange,
            onExcludeSmallAudiosChange = { enabled ->
                onExcludeSmallAudiosChange(enabled)
                viewModel.setExcludeSmallAudios(enabled)
            },
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
                viewModel.setDjModeConfig(enabled, djMixMode, djMixDurationSeconds)
            },
            onDjMixModeChange = { mode ->
                onDjMixModeChange(mode)
                viewModel.setDjModeConfig(djModeEnabled, mode, djMixDurationSeconds)
            },
            onDjMixDurationChange = { seconds ->
                val safeSeconds = seconds.coerceIn(MIN_DJ_MIX_SECONDS, MAX_DJ_MIX_SECONDS)
                onDjMixDurationChange(safeSeconds)
                viewModel.setDjModeConfig(djModeEnabled, djMixMode, safeSeconds)
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
private const val KEY_EXCLUDE_SMALL_AUDIOS = "exclude_small_audios"
private const val KEY_DJ_MODE_ENABLED = "dj_mode_enabled"
private const val KEY_DJ_MIX_MODE = "dj_mix_mode"
private const val KEY_DJ_MIX_DURATION_SECONDS = "dj_mix_duration_seconds"
private const val KEY_AUDIO_PERMISSION_REQUESTED = "audio_permission_requested"
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

private fun SharedPreferences.readDjMixMode(): DjMixMode = enumValueOrDefault(
    value = getString(KEY_DJ_MIX_MODE, null),
    default = DjMixMode.Classic,
)

private fun SharedPreferences.readDjMixDurationSeconds(): Int = getInt(KEY_DJ_MIX_DURATION_SECONDS, 8)
    .coerceIn(MIN_DJ_MIX_SECONDS, MAX_DJ_MIX_SECONDS)

private inline fun <reified T : Enum<T>> enumValueOrDefault(
    value: String?,
    default: T,
): T = runCatching {
    enumValueOf<T>(value ?: default.name)
}.getOrDefault(default)
