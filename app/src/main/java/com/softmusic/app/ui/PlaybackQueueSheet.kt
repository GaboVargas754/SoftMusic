package com.softmusic.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.softmusic.app.data.MusicPlaylist
import com.softmusic.app.data.Song
import com.softmusic.app.player.PlaybackMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
internal fun PlaybackQueueSheet(
    queue: List<Song>,
    currentSong: Song,
    playlists: List<MusicPlaylist>,
    songs: List<Song>,
    sourceLabel: String,
    isPlaying: Boolean,
    playbackMode: PlaybackMode,
    sleepTimerRemainingMs: Long,
    onPlaySong: (Song) -> Unit,
    onTogglePlayPause: () -> Unit,
    onRemoveSong: (Song) -> Unit,
    onMoveSong: (Song, Int) -> Unit,
    onClearUpcomingQueue: () -> Unit,
    onCreatePlaylistFromQueue: (String) -> Boolean,
    onAddQueueToPlaylist: (String) -> Int,
    onToggleShuffle: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    val currentIndex = queue.indexOfFirst { it.id == currentSong.id }
    // A missing current item cannot grant permission to move queue history.
    val upcomingSongs = if (currentIndex >= 0) queue.drop(currentIndex + 1) else emptyList()
    val queueIds = remember(queue) { queue.map { it.id } }
    val upcomingIds = remember(upcomingSongs) { upcomingSongs.map { it.id } }
    val songsById = remember(upcomingSongs) { upcomingSongs.associateBy { it.id } }
    val reorderState = remember(currentSong.id, queueIds) { QueueDragState(upcomingIds) }
    val orderedSongs = remember(reorderState.orderedIds, songsById) {
        reorderState.orderedIds.mapNotNull(songsById::get)
    }
    val listState = rememberLazyListState()
    val placementSpec = softMusicMotionSpec<IntOffset>(160)
    val latestUpcomingIds by rememberUpdatedState(upcomingIds)
    val latestMoveSong by rememberUpdatedState(onMoveSong)
    val latestSongsById by rememberUpdatedState(songsById)
    val latestFinishDrag by rememberUpdatedState(newValue = {
        val drop = reorderState.finish(latestUpcomingIds)
        if (drop != null) latestSongsById[drop.songId]?.let { latestMoveSong(it, drop.offset) }
    })
    var showActions by remember { mutableStateOf(false) }
    var showSaveQueueDialog by rememberSaveable { mutableStateOf(false) }
    var showAddQueueToPlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var pendingRemovalId by rememberSaveable { mutableStateOf<Long?>(null) }
    val pendingRemovalSong = upcomingSongs.firstOrNull { it.id == pendingRemovalId }
    val latestUpcomingSongs by rememberUpdatedState(upcomingSongs)
    var isClearingUpcoming by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var feedbackGeneration by remember { mutableIntStateOf(0) }
    fun showFeedback(message: String) {
        feedbackMessage = message
        feedbackGeneration += 1
    }
    val queueActionScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val edgeSizePx = with(density) { 48.dp.toPx() }
    val maxScrollPerFramePx = with(density) { 9.dp.toPx() }

    LaunchedEffect(upcomingSongs.size) {
        if (upcomingSongs.isEmpty()) isClearingUpcoming = false
    }
    LaunchedEffect(pendingRemovalId, upcomingIds) {
        if (pendingRemovalId != null && pendingRemovalSong == null) pendingRemovalId = null
    }
    LaunchedEffect(feedbackGeneration) {
        if (feedbackMessage != null) {
            delay(2_300L)
            feedbackMessage = null
        }
    }
    LaunchedEffect(reorderState, reorderState.awaitingMove) {
        if (reorderState.awaitingMove) {
            // Keep the drop optimistic while the synchronous queue callback reaches composition.
            withFrameNanos { }
            withFrameNanos { }
            reorderState.reconcilePendingMove()
        }
    }
    LaunchedEffect(reorderState, reorderState.draggedSongId) {
        if (reorderState.draggedSongId != null) {
            while (isActive && reorderState.draggedSongId != null) {
                withFrameNanos { }
                val layout = listState.layoutInfo
                val topDistance = reorderState.draggedTop - layout.viewportStartOffset
                val bottomDistance = layout.viewportEndOffset -
                    (reorderState.draggedTop + reorderState.draggedHeight)
                val scroll = when {
                    topDistance < edgeSizePx -> -maxScrollPerFramePx *
                        (1f - topDistance / edgeSizePx).coerceIn(0f, 1f)
                    bottomDistance < edgeSizePx -> maxScrollPerFramePx *
                        (1f - bottomDistance / edgeSizePx).coerceIn(0f, 1f)
                    else -> 0f
                }
                if (scroll != 0f) {
                    val consumed = listState.scrollBy(scroll)
                    if (consumed != 0f) reorderState.findTarget(listState, consumed)
                }
            }
        }
    }

    if (pendingRemovalSong != null) {
        AlertDialog(
            onDismissRequest = { pendingRemovalId = null },
            title = { Text("¿Quitar de la fila?") },
            text = {
                Text(
                    text = "«${pendingRemovalSong.title}» se quitará de la fila de reproducción. Seguirá en tu biblioteca.",
                    modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(onClick = {
                    val song = latestUpcomingSongs.firstOrNull { it.id == pendingRemovalId }
                    pendingRemovalId = null
                    if (song != null) {
                        onRemoveSong(song)
                        showFeedback("Canción quitada de la fila")
                    }
                }) { Text("Quitar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemovalId = null }) { Text("Cancelar") }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        )
    }

    if (showSaveQueueDialog) {
        SaveQueueAsPlaylistDialog(
            existingPlaylistNames = playlists.map { it.name },
            onSave = { name ->
                if (onCreatePlaylistFromQueue(name)) {
                    showSaveQueueDialog = false
                    showFeedback("Playlist guardada")
                }
            },
            onDismiss = { showSaveQueueDialog = false },
        )
    }
    if (showAddQueueToPlaylistDialog) {
        AddQueueToPlaylistDialog(
            playlists = playlists,
            songs = songs,
            queue = queue,
            onAddToPlaylist = { playlistId ->
                val addedCount = onAddQueueToPlaylist(playlistId)
                if (addedCount > 0) {
                    showAddQueueToPlaylistDialog = false
                    showFeedback(
                        if (addedCount == 1) "Se agregó 1 canción a la playlist"
                        else "Se agregaron $addedCount canciones a la playlist",
                    )
                }
            },
            onDismiss = { showAddQueueToPlaylistDialog = false },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Fila de reproducción",
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = sourceLabel,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { showActions = true }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = MusicIcons.Playlist,
                        contentDescription = "Opciones de la fila de reproducción",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                    DropdownMenuItem(
                        text = { Text("Guardar playlist") },
                        enabled = queue.isNotEmpty() && !isClearingUpcoming,
                        onClick = {
                            showActions = false
                            showSaveQueueDialog = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Agregar cola a playlist") },
                        enabled = queue.isNotEmpty() && !isClearingUpcoming,
                        onClick = {
                            showActions = false
                            showAddQueueToPlaylistDialog = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (isClearingUpcoming) "Limpiando..." else "Limpiar siguientes") },
                        enabled = upcomingSongs.isNotEmpty() && !isClearingUpcoming,
                        onClick = {
                            showActions = false
                            isClearingUpcoming = true
                            queueActionScope.launch {
                                withFrameNanos { }
                                onClearUpcomingQueue()
                                isClearingUpcoming = false
                                showFeedback("Se limpiaron las canciones siguientes")
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Cerrar fila") },
                        onClick = {
                            showActions = false
                            onDismiss()
                        },
                    )
                }
            }
        }

        if (isClearingUpcoming) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
        }
        QueueSectionTitle("Reproduciendo ahora")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(song = currentSong, modifier = Modifier.size(40.dp), rounded = 6.dp)
            Spacer(modifier = Modifier.width(10.dp))
            QueueSongText(song = currentSong, current = true, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier.size(48.dp),
            ) {
                Box(
                    modifier = Modifier.size(34.dp).background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        CircleShape,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPlaying) MusicIcons.Pause else MusicIcons.Play,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        QueueSectionTitle("Siguiente")

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            if (orderedSongs.isEmpty()) {
                item(key = "emptyQueue") {
                    Text(
                        text = "No hay más canciones después de esta.",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            itemsIndexed(
                items = orderedSongs,
                key = { _, song -> song.id },
                contentType = { _, _ -> "queueSong" },
            ) { index, song ->
                val dragging = reorderState.draggedSongId == song.id
                val placementModifier = if (dragging) Modifier else Modifier.animateItem(
                    fadeInSpec = null,
                    placementSpec = placementSpec,
                    fadeOutSpec = null,
                )
                QueueUpcomingSong(
                    song = song,
                    index = index,
                    count = orderedSongs.size,
                    dragging = dragging,
                    modifier = placementModifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) {
                                val itemTop = listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.key == song.id }?.offset
                                if (itemTop != null) reorderState.draggedTop - itemTop else 0f
                            } else 0f
                        },
                    dragModifier = Modifier.pointerInput(reorderState, song.id) {
                        awaitEachGesture {
                            // Claim the grip at the initial pass before the sheet's vertical drag.
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            down.consume()
                            if (reorderState.start(song.id, listState)) {
                                try {
                                    val finished = drag(down.id) { change ->
                                        val distance = change.positionChange().y
                                        change.consume()
                                        reorderState.dragBy(distance, listState)
                                    }
                                    if (finished) latestFinishDrag() else reorderState.cancel()
                                } finally {
                                    // A disposed/replaced row must never leave edge scrolling running.
                                    if (reorderState.draggedSongId == song.id) reorderState.cancel()
                                }
                            }
                        }
                    },
                    onPlay = { onPlaySong(song) },
                    onRemove = { pendingRemovalId = song.id },
                    onMoveUp = if (index > 0) ({ onMoveSong(song, -1) }) else null,
                    onMoveDown = if (index < orderedSongs.lastIndex) ({ onMoveSong(song, 1) }) else null,
                )
            }
        }

        feedbackMessage?.let { message ->
            Text(
                text = message,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val shuffleActive = playbackMode == PlaybackMode.Shuffle
            QueueFooterCard(
                title = "Aleatorio",
                imageVector = if (shuffleActive) MusicIcons.ShuffleSelected else MusicIcons.Shuffle,
                active = shuffleActive,
                description = if (shuffleActive) "Desactivar aleatorio" else "Activar aleatorio",
                status = if (shuffleActive) "Activado" else "Desactivado",
                modifier = Modifier.weight(1f),
                onClick = onToggleShuffle,
            )
            val timerActive = sleepTimerRemainingMs > 0L
            QueueFooterCard(
                title = "Temporizador",
                imageVector = if (timerActive) MusicIcons.SleepTimerSelected else MusicIcons.SleepTimer,
                active = timerActive,
                description = "Temporizador de apagado",
                status = if (timerActive) formatDuration(sleepTimerRemainingMs) else "Desactivado",
                modifier = Modifier.weight(1f),
                onClick = onOpenSleepTimer,
            )
        }
    }
}

@Composable
private fun QueueSectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun QueueSongText(song: Song, current: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = song.title,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = song.artist,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun QueueUpcomingSong(
    song: Song,
    index: Int,
    count: Int,
    dragging: Boolean,
    modifier: Modifier,
    dragModifier: Modifier,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
            )
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 52.dp)
                .clickable(onClick = onPlay)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(song = song, modifier = Modifier.size(36.dp), rounded = 6.dp)
            Spacer(modifier = Modifier.width(10.dp))
            QueueSongText(song = song, current = false, modifier = Modifier.weight(1f))
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .then(dragModifier)
                .semantics(mergeDescendants = true) {
                    contentDescription = "Reordenar ${song.title}"
                    stateDescription = "Posición ${index + 1} de $count"
                    role = Role.Button
                    customActions = buildList {
                        if (onMoveUp != null) add(CustomAccessibilityAction("Subir") { onMoveUp(); true })
                        if (onMoveDown != null) add(CustomAccessibilityAction("Bajar") { onMoveDown(); true })
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MusicIcons.ListView,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = MusicIcons.Close,
                contentDescription = "Quitar ${song.title} de la fila",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun QueueFooterCard(
    title: String,
    imageVector: ImageVector,
    active: Boolean,
    description: String,
    status: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics {
            contentDescription = description
            selected = active
            stateDescription = status
        },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = title,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Stable
private class QueueDragState(private val sourceIds: List<Long>) {
    var orderedIds by mutableStateOf(sourceIds)
        private set
    var draggedSongId by mutableStateOf<Long?>(null)
        private set
    var draggedTop by mutableFloatStateOf(0f)
        private set
    var draggedHeight by mutableIntStateOf(0)
        private set
    var awaitingMove by mutableStateOf(false)
        private set
    private var session: QueueReorderSession? = null

    fun start(songId: Long, listState: LazyListState): Boolean {
        if (awaitingMove || draggedSongId != null || songId !in sourceIds) return false
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == songId } ?: return false
        session = QueueReorderSession(sourceIds, songId)
        draggedSongId = songId
        draggedTop = item.offset.toFloat()
        draggedHeight = item.size
        return true
    }

    fun dragBy(distance: Float, listState: LazyListState) {
        if (draggedSongId == null) return
        val layout = listState.layoutInfo
        draggedTop = (draggedTop + distance).coerceIn(
            layout.viewportStartOffset.toFloat(),
            (layout.viewportEndOffset - draggedHeight).coerceAtLeast(layout.viewportStartOffset).toFloat(),
        )
        findTarget(listState, distance)
    }

    fun findTarget(listState: LazyListState, direction: Float) {
        val drag = session ?: return
        val fromIndex = orderedIds.indexOf(drag.songId)
        val center = draggedTop + draggedHeight / 2f
        val candidates = listState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
            val id = item.key as? Long ?: return@mapNotNull null
            if (id == drag.songId) return@mapNotNull null
            val index = orderedIds.indexOf(id)
            val crossed = if (direction > 0f) {
                index > fromIndex && center >= item.offset + item.size / 2f
            } else {
                // At the first row, the viewport clamp can align both centers exactly.
                index in 0 until fromIndex && center <= item.offset + item.size / 2f
            }
            if (crossed) index else null
        }
        val target = if (direction > 0f) candidates.maxOrNull() else candidates.minOrNull()
        if (target != null && drag.moveTo(target)) {
            val firstIndex = listState.firstVisibleItemIndex
            val firstOffset = listState.firstVisibleItemScrollOffset
            orderedIds = drag.orderedIds
            // Preserve the viewport, rather than letting the dragged first key anchor the list.
            if (fromIndex == firstIndex || target == firstIndex) {
                listState.requestScrollToItem(firstIndex, firstOffset)
            }
        }
    }

    fun finish(latestUpcomingIds: List<Long>): QueueDrop? {
        val drop = session?.drop(latestUpcomingIds)
        draggedSongId = null
        session = null
        awaitingMove = drop != null
        if (drop == null) orderedIds = sourceIds
        return drop
    }

    fun cancel() {
        draggedSongId = null
        session = null
        orderedIds = sourceIds
    }

    fun reconcilePendingMove() {
        awaitingMove = false
        orderedIds = sourceIds
    }
}
