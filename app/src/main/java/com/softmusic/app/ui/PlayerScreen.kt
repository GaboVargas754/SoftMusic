package com.softmusic.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.softmusic.app.data.MusicPlaylist
import com.softmusic.app.data.Song
import com.softmusic.app.player.DjMixMode
import com.softmusic.app.player.PlaybackMode
import com.softmusic.app.player.PlaybackProgressState
import com.softmusic.app.player.PlaybackQueueSource
import com.softmusic.app.player.PlayerUiState
import com.softmusic.app.player.playbackQueueOriginLabel
import kotlin.math.max
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FullPlayer(
    uiState: PlayerUiState,
    playbackProgressState: StateFlow<PlaybackProgressState>,
    isFavorite: Boolean,
    playlists: List<MusicPlaylist>,
    onPlayQueueSong: (Song) -> Unit,
    onRemoveFromQueue: (Song) -> Unit,
    onMoveQueuedSong: (Song, Int) -> Unit,
    onClearUpcomingQueue: () -> Unit,
    onSmartOrderQueue: () -> Unit,
    onCreatePlaylistFromQueue: (String) -> Boolean,
    onAddQueueToPlaylist: (String) -> Int,
    onStartSleepTimer: (Long) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
    onClose: () -> Unit,
) {
    val song = uiState.currentSong ?: return
    val highPerformanceMode = LocalHighPerformanceMode.current
    var showVinylArtwork by remember(uiState.currentSong?.id) { mutableStateOf(false) }
    var showSongDetails by rememberSaveable(uiState.currentSong?.id) { mutableStateOf(false) }
    var showPlaybackQueue by rememberSaveable { mutableStateOf(false) }
    var showSleepTimerDialog by rememberSaveable { mutableStateOf(false) }
    val queueSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val queueHeightFraction = if (LocalConfiguration.current.screenHeightDp < 500) 1f else 0.72f
    val artwork = rememberArtworkAppearance(song.artworkUri.takeUnless { highPerformanceMode })
    val artworkColor = artwork?.color
    val playerBackground by animateColorAsState(
        targetValue = whitePlaybackColor(artwork?.playbackColor?.let {
            lerp(it, Color.Black, 0.12f)
        } ?: MaterialTheme.colorScheme.background),
        animationSpec = softMusicMotionSpec(360),
        label = "fullPlayerArtworkColor",
    )
    val primaryText = Color.White
    val playButtonColor = whitePlaybackColor(
        artworkColor ?: MaterialTheme.colorScheme.primary,
        minimumContrast = 4.5,
    )
    val mutedText = if (artworkColor != null) primaryText.copy(alpha = 0.85f)
        else MaterialTheme.colorScheme.onSurfaceVariant
    val timelineInactive = if (artworkColor != null) primaryText.copy(alpha = 0.24f)
        else MaterialTheme.colorScheme.surfaceVariant
    val sourceCaption = when (uiState.playbackQueueSource) {
        PlaybackQueueSource.Favorites -> "Reproduciendo desde favoritos"
        PlaybackQueueSource.Playlist -> "Reproduciendo desde playlist"
        PlaybackQueueSource.Artist -> "Reproduciendo desde artista"
        PlaybackQueueSource.Album -> "Reproduciendo desde álbum"
        PlaybackQueueSource.History -> "Reproduciendo desde historial"
        PlaybackQueueSource.Songs -> if (uiState.playbackQueueSourceKey.isNullOrBlank()) {
            "Reproduciendo desde biblioteca"
        } else "Reproduciendo desde carpeta"
    }
    val djStatusLabel = if (uiState.djModeEnabled &&
        (uiState.djMixMode == DjMixMode.Classic || uiState.djMixMode == DjMixMode.Expert)
    ) "DJ activo" else null
    val shuffleActive = uiState.playbackMode == PlaybackMode.Shuffle
    val nextShuffleMode = if (shuffleActive) PlaybackMode.Ordered else PlaybackMode.Shuffle
    val nextRepeatMode = when (uiState.playbackMode) {
        PlaybackMode.RepeatList -> PlaybackMode.RepeatCurrent
        PlaybackMode.RepeatCurrent -> PlaybackMode.Ordered
        PlaybackMode.Ordered,
        PlaybackMode.Shuffle -> PlaybackMode.RepeatList
    }
    val favoriteTint by animateColorAsState(
        targetValue = if (isFavorite) MaterialTheme.colorScheme.primary else primaryText,
        animationSpec = softMusicMotionSpec(),
        label = "fullPlayerFavoriteTint",
    )
    val favoriteScale by animateFloatAsState(
        targetValue = if (isFavorite) 1.18f else 1f,
        animationSpec = if (highPerformanceMode) snap() else spring(),
        label = "fullPlayerFavoriteScale",
    )
    val playPauseScale by animateFloatAsState(
        targetValue = if (uiState.isPlaying) 1.06f else 1f,
        animationSpec = if (highPerformanceMode) snap() else spring(),
        label = "fullPlayerPlayPauseScale",
    )

    if (showSongDetails) {
        SongDetailsDialog(
            song = song,
            onDismiss = { showSongDetails = false },
        )
    }

    if (showPlaybackQueue) {
        ModalBottomSheet(
            onDismissRequest = { showPlaybackQueue = false },
            sheetState = queueSheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(queueHeightFraction)) {
            PlaybackQueueSheet(
                queue = uiState.playbackQueue,
                currentSong = song,
                playlists = playlists,
                songs = uiState.songs,
                sourceLabel = uiState.playbackQueueOriginLabel(),
                isPlaying = uiState.isPlaying,
                playbackMode = uiState.playbackMode,
                smartShuffleEnabled = uiState.smartShuffleAvailable,
                smartQueueBusy = uiState.smartQueueBusy,
                smartQueueMessage = uiState.smartQueueMessage,
                sleepTimerRemainingMs = uiState.sleepTimerRemainingMs,
                onPlaySong = onPlayQueueSong,
                onTogglePlayPause = onTogglePlayPause,
                onRemoveSong = onRemoveFromQueue,
                onMoveSong = onMoveQueuedSong,
                onClearUpcomingQueue = onClearUpcomingQueue,
                onSmartOrderQueue = onSmartOrderQueue,
                onCreatePlaylistFromQueue = onCreatePlaylistFromQueue,
                onAddQueueToPlaylist = onAddQueueToPlaylist,
                onToggleShuffle = { onPlaybackModeChange(nextShuffleMode) },
                onOpenSleepTimer = { showSleepTimerDialog = true },
                onDismiss = { showPlaybackQueue = false },
            )
            }
        }
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            remainingMs = uiState.sleepTimerRemainingMs,
            onStartTimer = { durationMs ->
                onStartSleepTimer(durationMs)
                showSleepTimerDialog = false
            },
            onCancelTimer = {
                onCancelSleepTimer()
                showSleepTimerDialog = false
            },
            onDismiss = { showSleepTimerDialog = false },
        )
    }

    AnimatedArtworkBackdrop(
        color = playerBackground,
        artwork = artwork,
        modifier = Modifier.fillMaxSize(),
    ) {
        FullPlayerContent(
            song = song,
            sourceCaption = sourceCaption,
            sourceName = uiState.playbackQueueOriginLabel(),
            isFavorite = isFavorite,
            isPlaying = uiState.isPlaying,
            playbackMode = uiState.playbackMode,
            smartShuffleEnabled = uiState.smartShuffleAvailable,
            playButtonColor = playButtonColor,
            mutedText = mutedText,
            favoriteTint = favoriteTint,
            favoriteScale = favoriteScale,
            playPauseScale = playPauseScale,
            djStatusLabel = djStatusLabel,
            timerRemainingMs = uiState.sleepTimerRemainingMs,
            onClose = onClose,
            onShowSongDetails = { showSongDetails = true },
            onToggleFavorite = onToggleFavorite,
            onTogglePlayPause = onTogglePlayPause,
            onPrevious = onPrevious,
            onNext = onNext,
            onToggleShuffle = { onPlaybackModeChange(nextShuffleMode) },
            onCycleRepeat = { onPlaybackModeChange(nextRepeatMode) },
            onShowSleepTimer = { showSleepTimerDialog = true },
            onShowQueue = { showPlaybackQueue = true },
            artwork = { layoutModifier ->
                val interactiveArtwork = layoutModifier.clickable(
                    enabled = !highPerformanceMode,
                    role = Role.Button,
                    onClickLabel = if (showVinylArtwork) "Mostrar portada" else "Mostrar vinilo",
                    onClick = { showVinylArtwork = !showVinylArtwork },
                )
                if (!highPerformanceMode && showVinylArtwork) {
                    SpinningDiscArtwork(
                        song = song,
                        isPlaying = uiState.isPlaying,
                        modifier = interactiveArtwork,
                    )
                } else {
                    AnimatedSongArtwork(song = song, modifier = interactiveArtwork, rounded = 12.dp)
                }
            },
            timeline = {
                PlayerTimeline(
                    songId = song.id,
                    playbackProgressState = playbackProgressState,
                    onSeek = onSeek,
                    activeColor = primaryText,
                    inactiveColor = timelineInactive,
                    timeColor = mutedText,
                )
            },
            addToPlaylist = {
                AddToPlaylistButton(
                    playlists = playlists,
                    songs = uiState.songs,
                    song = song,
                    onAddToPlaylist = onAddToPlaylist,
                )
            },
        )
    }
}

@Composable
private fun PlayerTimeline(
    songId: Long,
    playbackProgressState: StateFlow<PlaybackProgressState>,
    onSeek: (Long) -> Unit,
    activeColor: Color,
    inactiveColor: Color,
    timeColor: Color,
) {
    val playbackProgress by playbackProgressState.collectAsStateWithLifecycle()
    var scrubbedProgress by remember(songId) { mutableFloatStateOf(0f) }
    var isScrubbing by remember(songId) { mutableStateOf(false) }
    val duration = max(playbackProgress.durationMs, 1L)
    val actualProgress = (playbackProgress.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    val displayedProgress = if (isScrubbing) scrubbedProgress else actualProgress

    Column(modifier = Modifier.fillMaxWidth()) {
        ThinTimeline(
            value = displayedProgress,
            onValueChange = {
                isScrubbing = true
                scrubbedProgress = it
            },
            onValueChangeFinished = {
                onSeek((scrubbedProgress * duration).toLong())
                isScrubbing = false
            },
            activeColor = activeColor,
            inactiveColor = inactiveColor,
            thumbColor = activeColor,
            modifier = Modifier.semantics {
                stateDescription = "${formatDuration((displayedProgress * duration).toLong())} de ${formatDuration(playbackProgress.durationMs)}"
            },
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatDuration(playbackProgress.positionMs),
                color = timeColor,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatDuration(playbackProgress.durationMs),
                color = timeColor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ThinTimeline(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    activeColor: Color,
    inactiveColor: Color,
    thumbColor: Color,
    modifier: Modifier = Modifier,
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    fun updateValue(x: Float, width: Int) {
        val safeWidth = width.coerceAtLeast(1)
        currentOnValueChange((x / safeWidth.toFloat()).coerceIn(0f, 1f))
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    down.consume()
                    updateValue(down.position.x, size.width)

                    drag(down.id) { change ->
                        change.consume()
                        updateValue(change.position.x, size.width)
                    }
                    currentOnValueChangeFinished()
                }
            },
    ) {
        val safeValue = value.coerceIn(0f, 1f)
        val centerY = size.height / 2f
        val progressX = size.width * safeValue
        val lineWidth = 2.dp.toPx()
        val thumbRadius = 5.dp.toPx()

        drawLine(
            color = inactiveColor,
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = lineWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = activeColor,
            start = Offset(0f, centerY),
            end = Offset(progressX, centerY),
            strokeWidth = lineWidth,
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = thumbColor,
            radius = thumbRadius,
            center = Offset(progressX, centerY),
        )
    }
}

@Composable
private fun PlaybackModeButton(
    playbackMode: PlaybackMode,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedIconButton(
        onClick = { onPlaybackModeChange(playbackMode.next()) },
        modifier = modifier,
    ) {
        Icon(
            imageVector = playbackMode.icon(),
            contentDescription = "Modo actual: ${playbackMode.label}. Toca para cambiar.",
        )
    }
}

private fun PlaybackMode.next(): PlaybackMode {
    val modes = PlaybackMode.entries
    return modes[(ordinal + 1) % modes.size]
}

@Composable
private fun PlaybackMode.icon(): ImageVector = when (this) {
    PlaybackMode.Ordered -> MusicIcons.ListView
    PlaybackMode.RepeatList -> MusicIcons.RepeatAllSelected
    PlaybackMode.RepeatCurrent -> MusicIcons.RepeatOneSelected
    PlaybackMode.Shuffle -> MusicIcons.ShuffleSelected
}
