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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.softmusic.app.backup.BackupPreferences
import com.softmusic.app.backup.openAppPreferences
import com.softmusic.app.player.AutoDjSettings
import com.softmusic.app.player.DjMixMode
import com.softmusic.app.player.MusicService
import com.softmusic.app.player.MusicViewModel
import com.softmusic.app.player.PlaybackMode
import com.softmusic.app.player.SortMode
import com.softmusic.app.icons.SeasonalIconManager
import com.softmusic.app.icons.drawableRes
import com.softmusic.app.icons.logoDrawableRes
import com.softmusic.app.ui.LocalSeasonalLogo
import com.softmusic.app.ui.LocalSeasonalIcon
import com.softmusic.app.ui.AppUpdateDialog
import com.softmusic.app.ui.BackupUiActions
import com.softmusic.app.ui.LocalBackupActions
import com.softmusic.app.ui.SoftMusicApp
import com.softmusic.app.ui.theme.SoftMusicTheme
import com.softmusic.app.update.AppUpdateChecker
import com.softmusic.app.update.AppUpdateCheckResult
import com.softmusic.app.update.AppUpdateInfo
import com.softmusic.app.update.ManualUpdateCheckRunner
import com.softmusic.app.update.presentation

class MainActivity : ComponentActivity() {
    private val seasonalIconVisibilityToken = Any()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SeasonalIconManager.get(this).setActivityVisible(seasonalIconVisibilityToken, true)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        val preferences = openAppPreferences(this)
        preferences.edit()
            .remove(KEY_LEGACY_THEME_MODE)
            .remove(KEY_LEGACY_COLOR_PALETTE)
            .apply()
        setContent {
            val seasonalIcon by SeasonalIconManager.get(this).icon.collectAsStateWithLifecycle()
            var highPerformanceMode by remember { mutableStateOf(preferences.getBoolean(KEY_HIGH_PERFORMANCE_MODE, false)) }
            var defaultFolderPath by remember { mutableStateOf(preferences.getString(KEY_DEFAULT_FOLDER_PATH, null)) }
            var defaultPlaybackMode by remember { mutableStateOf(preferences.readDefaultPlaybackMode()) }
            var defaultSortMode by remember { mutableStateOf(preferences.readDefaultSortMode()) }
            var fontScale by remember { mutableFloatStateOf(preferences.readFontScale()) }
            var hiddenFolderPaths by remember { mutableStateOf(preferences.readHiddenFolderPaths()) }
            var excludeSmallAudios by remember { mutableStateOf(preferences.getBoolean(KEY_EXCLUDE_SMALL_AUDIOS, true)) }
            var djModeEnabled by remember {
                mutableStateOf(runCatching { preferences.getBoolean(KEY_DJ_MODE_ENABLED, false) }.getOrDefault(false))
            }
            var djMixMode by remember { mutableStateOf(preferences.readDjMixMode()) }
            var djMixDurationSeconds by remember { mutableIntStateOf(preferences.readDjMixDurationSeconds()) }
            var autoDjSettings by remember { mutableStateOf(preferences.readAutoDjSettings()) }
            var audioPermissionRequested by remember { mutableStateOf(preferences.getBoolean(KEY_AUDIO_PERMISSION_REQUESTED, false)) }
            var availableUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }
            var isCheckingForUpdates by remember { mutableStateOf(false) }
            var updateCheckStatus by remember { mutableStateOf<String?>(null) }
            val updateCheckScope = rememberCoroutineScope()
            val manualUpdateCheck = remember { ManualUpdateCheckRunner() }

            fun refreshPortablePreferences() {
                highPerformanceMode = preferences.getBoolean(KEY_HIGH_PERFORMANCE_MODE, false)
                defaultFolderPath = preferences.getString(KEY_DEFAULT_FOLDER_PATH, null)
                defaultPlaybackMode = preferences.readDefaultPlaybackMode()
                defaultSortMode = preferences.readDefaultSortMode()
                fontScale = preferences.readFontScale()
                hiddenFolderPaths = preferences.readHiddenFolderPaths()
                excludeSmallAudios = preferences.getBoolean(KEY_EXCLUDE_SMALL_AUDIOS, true)
                djModeEnabled = runCatching { preferences.getBoolean(KEY_DJ_MODE_ENABLED, false) }.getOrDefault(false)
                djMixMode = preferences.readDjMixMode()
                djMixDurationSeconds = preferences.readDjMixDurationSeconds()
                autoDjSettings = preferences.readAutoDjSettings()
            }

            DisposableEffect(preferences) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == KEY_BACKUP_PREFERENCES_REVISION) refreshPortablePreferences()
                }
                preferences.registerOnSharedPreferenceChangeListener(listener)
                // A restore may finish while an activity is being recreated.
                refreshPortablePreferences()
                onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
            }

            fun applyUpdateCheckResult(result: AppUpdateCheckResult, manual: Boolean) {
                val presentation = result.presentation(
                    manual = manual,
                    skippedVersionCode = preferences.getLong(KEY_SKIPPED_UPDATE_VERSION_CODE, -1L),
                )
                presentation.availableUpdate?.let {
                    availableUpdate = it
                    updateCheckStatus = null
                }
                if (manual && presentation.availableUpdate == null) {
                    updateCheckStatus = presentation.status
                }
            }

            fun checkForUpdatesManually() {
                manualUpdateCheck.launch(
                    scope = updateCheckScope,
                    check = AppUpdateChecker::checkForUpdate,
                    onCheckingChanged = { checking ->
                        isCheckingForUpdates = checking
                        if (checking) updateCheckStatus = null
                    },
                    onResult = { result -> applyUpdateCheckResult(result, manual = true) },
                )
            }

            LaunchedEffect(Unit) {
                applyUpdateCheckResult(AppUpdateChecker.checkForUpdate(), manual = false)
            }

            SoftMusicTheme {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalSeasonalIcon provides seasonalIcon.drawableRes,
                    LocalSeasonalLogo provides seasonalIcon.logoDrawableRes,
                    LocalDensity provides Density(
                        density = baseDensity.density,
                        fontScale = baseDensity.fontScale * fontScale,
                    ),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
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
                                autoDjSettings = autoDjSettings,
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
                                onAutoDjSettingsChange = { settings ->
                                    autoDjSettings = settings.sanitized()
                                    preferences.writeAutoDjSettings(autoDjSettings)
                                },
                                onOpenNotificationSettings = ::openNotificationSettings,
                                onOpenAppSettings = ::openAppSettings,
                                onOpenDonation = { openExternalUrl(PAYPAL_DONATION_URL) },
                                isCheckingForUpdates = isCheckingForUpdates,
                                updateCheckStatus = updateCheckStatus,
                                onCheckForUpdates = ::checkForUpdatesManually,
                                onCloseApp = { closeApplication() },
                            )
                            availableUpdate?.let { updateInfo ->
                                AppUpdateDialog(
                                    updateInfo = updateInfo,
                                    onUpdate = {
                                        availableUpdate = null
                                        openExternalUrl(updateInfo.updateUrl)
                                    },
                                    onDismiss = {
                                        availableUpdate = null
                                        preferences.edit()
                                            .putLong(KEY_SKIPPED_UPDATE_VERSION_CODE, updateInfo.versionCode)
                                            .apply()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SeasonalIconManager.get(this).setActivityVisible(seasonalIconVisibilityToken, true)
    }

    override fun onStop() {
        super.onStop()
        SeasonalIconManager.get(this).setActivityVisible(seasonalIconVisibilityToken, false)
    }

    override fun onDestroy() {
        SeasonalIconManager.get(this).setActivityVisible(seasonalIconVisibilityToken, false)
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

    private fun shouldShowAudioPermissionRationale(): Boolean = shouldShowRequestPermissionRationale(audioPermissionName())

    private fun hasNotificationPermission(): Boolean {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return false
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

    private fun openExternalUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        runCatching { startActivity(intent) }
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
    autoDjSettings: AutoDjSettings,
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
    onAutoDjSettingsChange: (AutoDjSettings) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenDonation: () -> Unit,
    isCheckingForUpdates: Boolean,
    updateCheckStatus: String?,
    onCheckForUpdates: () -> Unit,
    onCloseApp: () -> Unit,
) {
    val viewModel: MusicViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingBackupCount by viewModel.pendingBackupCount.collectAsStateWithLifecycle()
    val backupBusy by viewModel.backupBusy.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionGranted by remember { mutableStateOf(checkAudioPermission()) }
    var showAudioPermissionRationale by remember { mutableStateOf(shouldShowAudioPermissionRationale()) }
    var notificationPermissionGranted by remember { mutableStateOf(checkNotificationPermission()) }
    var defaultsApplied by remember { mutableStateOf(false) }
    var showNotificationPermissionPrompt by remember { mutableStateOf(false) }
    var notificationPermissionPromptDismissed by remember { mutableStateOf(false) }

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

    fun requestNotificationPermission() {
        val notificationPermission = notificationPermissionName()
        if (notificationPermission == null) {
            onOpenNotificationSettings()
        } else {
            notificationPermissionLauncher.launch(notificationPermission)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissionState()
                if (defaultsApplied) {
                    viewModel.setLibraryAccess(permissionGranted)
                    viewModel.onLibraryResume()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(defaultsApplied, backupBusy) {
        if (defaultsApplied || viewModel.backupBusy.value) return@LaunchedEffect
        viewModel.applyDefaultPreferences(
            defaultFolderPath = defaultFolderPath,
            defaultPlaybackMode = defaultPlaybackMode,
            defaultSortMode = defaultSortMode,
            hiddenFolderPaths = hiddenFolderPaths,
            excludeSmallAudios = excludeSmallAudios,
            djModeEnabled = djModeEnabled,
            djMixMode = djMixMode,
            djMixDurationSeconds = djMixDurationSeconds,
            autoDjSettings = autoDjSettings,
        )
        defaultsApplied = true
    }

    LaunchedEffect(permissionGranted, defaultsApplied) {
        if (defaultsApplied) viewModel.setLibraryAccess(permissionGranted)
    }

    LaunchedEffect(uiState.currentSong?.id, notificationPermissionGranted) {
        if (uiState.currentSong != null && !notificationPermissionGranted && !notificationPermissionPromptDismissed) {
            showNotificationPermissionPrompt = true
        }
    }

    LaunchedEffect(defaultsApplied, uiState.hiddenFolderPaths, hiddenFolderPaths, backupBusy) {
        if (viewModel.backupBusy.value) return@LaunchedEffect
        val currentState = viewModel.uiState.value
        if (defaultsApplied && currentState.allFolders.isNotEmpty() && currentState.hiddenFolderPaths != hiddenFolderPaths) {
            onHiddenFolderPathsChange(currentState.hiddenFolderPaths)
        }
    }

    fun changePlaybackMode(mode: PlaybackMode) {
        onDefaultPlaybackModeChange(mode)
        viewModel.setPlaybackMode(mode)
    }

    val backupPreferences = BackupPreferences(
        highPerformanceMode = highPerformanceMode,
        fontScale = fontScale,
        playbackMode = defaultPlaybackMode,
        sortMode = defaultSortMode,
        excludeSmallAudios = excludeSmallAudios,
        defaultFolderPath = defaultFolderPath,
        hiddenFolderPaths = hiddenFolderPaths,
        djModeEnabled = djModeEnabled,
        djMixMode = djMixMode,
        djMixDurationSeconds = djMixDurationSeconds,
        autoDjSettings = autoDjSettings,
    )
    val backupActions = BackupUiActions(
        pendingCount = pendingBackupCount,
        libraryReady = permissionGranted && defaultsApplied && !uiState.isLoading && !backupBusy,
        createDocument = { viewModel.createBackupDocument(backupPreferences) },
        preview = viewModel::previewBackup,
        restore = viewModel::restoreBackup,
        previewPending = viewModel::previewPendingBackup,
        resolvePending = viewModel::resolvePendingBackup,
    )

    CompositionLocalProvider(LocalBackupActions provides backupActions) {
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
                onRequestNotificationPermission = ::requestNotificationPermission,
                onRefreshLibrary = viewModel::loadSongs,
                onPlaySong = viewModel::playSong,
                onPlayList = viewModel::playVisibleList,
                onPlayQueuedSong = viewModel::playQueuedSong,
                onPlayQueue = viewModel::playQueue,
                onPlayQueueSong = viewModel::playQueueSong,
                onRemoveFromQueue = viewModel::removeQueuedSong,
                onMoveQueuedSong = viewModel::moveQueuedSong,
                onClearUpcomingQueue = viewModel::clearUpcomingQueue,
                onSmartOrderQueue = viewModel::smartOrderQueue,
                onCreatePlaylistFromQueue = viewModel::createPlaylistFromQueue,
                onAddQueueToPlaylist = viewModel::addQueueToPlaylist,
                onResumeRestoredSession = viewModel::resumeRestoredSession,
                onDismissRestoredSession = viewModel::dismissRestoredSession,
                onStartSleepTimer = viewModel::startSleepTimer,
                onCancelSleepTimer = viewModel::cancelSleepTimer,
                onToggleFavorite = viewModel::toggleFavorite,
                onPlayNext = viewModel::playNext,
                onPlayAtEnd = viewModel::playAtEnd,
                onCreatePlaylist = viewModel::createPlaylist,
                onDeletePlaylist = viewModel::deletePlaylist,
                onSetPlaylistCover = viewModel::setPlaylistCover,
                onAddSongToPlaylist = viewModel::addSongToPlaylist,
                onRemoveSongFromPlaylist = viewModel::removeSongFromPlaylist,
                onRenamePlaylist = viewModel::renamePlaylist,
                onReorderPlaylist = viewModel::reorderPlaylist,
                onAddSongsToPlaylist = viewModel::addSongsToPlaylist,
                onRemoveSongsFromPlaylist = viewModel::removeSongsFromPlaylist,
                onCyclePlaylistShuffleMode = viewModel::cyclePlaylistShuffleMode,
                onHistoryEnabledChange = viewModel::setListeningHistoryEnabled,
                onClearListeningHistory = viewModel::clearListeningHistory,
                onTogglePlayPause = viewModel::togglePlayPause,
                onNext = viewModel::next,
                onPrevious = viewModel::previous,
                onSeek = viewModel::seekTo,
                onPlaybackModeChange = ::changePlaybackMode,
                onSortModeChange = viewModel::setSortMode,
                onFolderChange = viewModel::setSelectedFolder,
                highPerformanceMode = highPerformanceMode,
                defaultFolderPath = defaultFolderPath,
                defaultPlaybackMode = uiState.playbackMode,
                defaultSortMode = defaultSortMode,
                fontScale = fontScale,
                djModeEnabled = djModeEnabled,
                djMixMode = djMixMode,
                djMixDurationSeconds = djMixDurationSeconds,
                autoDjSettings = autoDjSettings,
                onHighPerformanceModeChange = onHighPerformanceModeChange,
                onDefaultFolderChange = onDefaultFolderChange,
                onDefaultPlaybackModeChange = ::changePlaybackMode,
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
                onAutoDjSettingsChange = { settings ->
                    val safeSettings = settings.sanitized()
                    onAutoDjSettingsChange(safeSettings)
                    viewModel.setAutoDjSettings(safeSettings)
                },
                onOpenNotificationSettings = onOpenNotificationSettings,
                onOpenAppSettings = onOpenAppSettings,
                onOpenDonation = onOpenDonation,
                isCheckingForUpdates = isCheckingForUpdates,
                updateCheckStatus = updateCheckStatus,
                onCheckForUpdates = onCheckForUpdates,
                onCloseApp = {
                    viewModel.shutdownPlayback()
                    onCloseApp()
                },
            )

            if (showNotificationPermissionPrompt) {
                AlertDialog(
                    onDismissRequest = {
                        notificationPermissionPromptDismissed = true
                        showNotificationPermissionPrompt = false
                    },
                    title = { Text("Activa los controles del sistema") },
                    text = {
                        Text("Permite las notificaciones de SoftMusic para mostrar controles de reproducción en el panel, pantalla de bloqueo y accesorios compatibles.")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showNotificationPermissionPrompt = false
                                requestNotificationPermission()
                            },
                        ) {
                            Text("Activar")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = {
                                notificationPermissionPromptDismissed = true
                                showNotificationPermissionPrompt = false
                            },
                        ) {
                            Text("Luego")
                        }
                    },
                )
            }
        }
    }
}

private const val KEY_BACKUP_PREFERENCES_REVISION = "backup_preferences_revision"
private const val KEY_LEGACY_THEME_MODE = "theme_mode"
private const val KEY_LEGACY_COLOR_PALETTE = "color_palette"
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
private const val KEY_AUTO_DJ_BEATS = "auto_dj_beats"
private const val KEY_AUTO_DJ_EFFECT = "auto_dj_effect"
private const val KEY_AUTO_DJ_INTENSITY = "auto_dj_intensity"
private const val KEY_AUTO_DJ_SYNC_ENABLED = "auto_dj_sync_enabled"
private const val KEY_AUDIO_PERMISSION_REQUESTED = "audio_permission_requested"
private const val KEY_SKIPPED_UPDATE_VERSION_CODE = "skipped_update_version_code"
private const val PAYPAL_DONATION_URL = "https://paypal.me/ChristianGabriel754"
private const val MIN_FONT_SCALE = 0.80f
private const val MAX_FONT_SCALE = 1.20f
private const val MIN_DJ_MIX_SECONDS = 5
private const val MAX_DJ_MIX_SECONDS = 8

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
    value = runCatching { getString(KEY_DJ_MIX_MODE, null) }.getOrNull(),
    default = DjMixMode.Classic,
).takeUnless { it == DjMixMode.AutoDj } ?: DjMixMode.Classic

private fun SharedPreferences.readDjMixDurationSeconds(): Int = runCatching { getInt(KEY_DJ_MIX_DURATION_SECONDS, 8) }
    .getOrDefault(8)
    .coerceIn(MIN_DJ_MIX_SECONDS, MAX_DJ_MIX_SECONDS)

private fun SharedPreferences.readAutoDjSettings(): AutoDjSettings {
    val defaults = AutoDjSettings()
    return AutoDjSettings(
        beats = runCatching { getInt(KEY_AUTO_DJ_BEATS, defaults.beats) }.getOrDefault(defaults.beats),
        effect = enumValueOrDefault(
            value = runCatching { getString(KEY_AUTO_DJ_EFFECT, null) }.getOrNull(),
            default = defaults.effect,
        ),
        intensity = runCatching { getFloat(KEY_AUTO_DJ_INTENSITY, defaults.intensity) }
            .getOrDefault(defaults.intensity),
        syncEnabled = runCatching { getBoolean(KEY_AUTO_DJ_SYNC_ENABLED, defaults.syncEnabled) }
            .getOrDefault(defaults.syncEnabled),
    ).sanitized()
}

private fun SharedPreferences.writeAutoDjSettings(settings: AutoDjSettings) {
    val safeSettings = settings.sanitized()
    edit()
        .putInt(KEY_AUTO_DJ_BEATS, safeSettings.beats)
        .putString(KEY_AUTO_DJ_EFFECT, safeSettings.effect.name)
        .putFloat(KEY_AUTO_DJ_INTENSITY, safeSettings.intensity)
        .putBoolean(KEY_AUTO_DJ_SYNC_ENABLED, safeSettings.syncEnabled)
        .apply()
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(
    value: String?,
    default: T,
): T = runCatching {
    enumValueOf<T>(value ?: default.name)
}.getOrDefault(default)
