package com.softmusic.app.ui

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive

@Stable
internal class PlaylistDragState(
    private val fullIds: List<Long>,
    private val sourceIds: List<Long>,
) {
    var orderedIds by mutableStateOf(sourceIds)
        private set
    var draggedSongId by mutableStateOf<Long?>(null)
        private set
    var draggedTop by mutableFloatStateOf(0f)
        private set
    var draggedHeight by mutableIntStateOf(0)
        private set
    var awaitingCommit by mutableStateOf(false)
        private set
    private var session: PlaylistReorderSession? = null

    fun start(songId: Long, listState: LazyListState): Boolean {
        if (awaitingCommit || draggedSongId != null || songId !in sourceIds) return false
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == songId } ?: return false
        session = PlaylistReorderSession(fullIds, sourceIds, songId)
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
        val from = orderedIds.indexOf(drag.songId)
        val center = draggedTop + draggedHeight / 2f
        val items = listState.layoutInfo.visibleItemsInfo
        val crossed = items.mapNotNull { item ->
            val id = item.key as? Long ?: return@mapNotNull null
            if (id == drag.songId) return@mapNotNull null
            val index = orderedIds.indexOf(id)
            val reached = if (direction > 0f) {
                index > from && center >= item.offset + item.size / 2f
            } else {
                index in 0 until from && center <= item.offset + item.size / 2f
            }
            if (reached) index else null
        }
        val target = if (direction > 0f) crossed.maxOrNull() else crossed.minOrNull()
        if (target != null && drag.moveTo(target)) {
            val firstIndex = listState.firstVisibleItemIndex
            val firstOffset = listState.firstVisibleItemScrollOffset
            val firstId = items.firstOrNull { it.index == firstIndex }?.key
            val targetId = orderedIds[target]
            orderedIds = drag.orderedIds
            if (firstId == drag.songId || firstId == targetId) {
                listState.requestScrollToItem(firstIndex, firstOffset)
            }
        }
    }

    fun finish(latestFullIds: List<Long>, latestVisibleIds: List<Long>): PlaylistReorderCommit? {
        val commit = session?.drop(latestFullIds, latestVisibleIds)
        draggedSongId = null
        session = null
        awaitingCommit = commit != null
        if (commit == null) orderedIds = sourceIds
        return commit
    }

    fun cancel() {
        draggedSongId = null
        session = null
        awaitingCommit = false
        orderedIds = sourceIds
    }
}

@Composable
internal fun PlaylistDragEffects(state: PlaylistDragState, listState: LazyListState) {
    val density = LocalDensity.current
    val edgeSize = with(density) { 48.dp.toPx() }
    val maximumScroll = with(density) { 9.dp.toPx() }
    LaunchedEffect(state, state.awaitingCommit) {
        if (state.awaitingCommit) {
            // A synchronous mutation will replace this state; rejection restores the source order.
            withFrameNanos { }
            withFrameNanos { }
            state.cancel()
        }
    }
    LaunchedEffect(state, state.draggedSongId) {
        while (isActive && state.draggedSongId != null) {
            withFrameNanos { }
            val layout = listState.layoutInfo
            val topDistance = state.draggedTop - layout.viewportStartOffset
            val bottomDistance = layout.viewportEndOffset - (state.draggedTop + state.draggedHeight)
            val scroll = when {
                topDistance < edgeSize -> -maximumScroll * (1f - topDistance / edgeSize).coerceIn(0f, 1f)
                bottomDistance < edgeSize -> maximumScroll * (1f - bottomDistance / edgeSize).coerceIn(0f, 1f)
                else -> 0f
            }
            if (scroll != 0f) {
                val consumed = listState.scrollBy(scroll)
                if (consumed != 0f) state.findTarget(listState, consumed)
            }
        }
    }
}
