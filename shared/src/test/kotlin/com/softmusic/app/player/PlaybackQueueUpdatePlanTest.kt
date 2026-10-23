package com.softmusic.app.player

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PlaybackQueueUpdatePlanTest {
    @Test
    fun `shuffle to normal replaces suffix before prefix`() {
        val patches = assertUpdatesQueue(
            currentIds = listOf(4L, 1L, 3L, 5L, 2L),
            desiredIds = listOf(1L, 2L, 3L, 4L, 5L),
            currentSongId = 3L,
        )

        assertEquals(
            listOf(
                PlaybackQueuePatch(3, 5, listOf(4L, 5L)),
                PlaybackQueuePatch(0, 2, listOf(1L, 2L)),
            ),
            patches,
        )
    }

    @Test
    fun `normal to shuffle puts current first without replacing it`() {
        assertUpdatesQueue(
            currentIds = listOf(1L, 2L, 3L, 4L, 5L),
            desiredIds = listOf(3L, 5L, 2L, 1L, 4L),
            currentSongId = 3L,
        )
    }

    @Test
    fun `inserts and removes songs on both sides of current`() {
        assertUpdatesQueue(
            currentIds = listOf(1L, 2L, 3L, 4L, 5L),
            desiredIds = listOf(7L, 3L, 8L, 9L),
            currentSongId = 3L,
        )
    }

    @Test
    fun `unchanged suffix needs only a prefix replacement`() {
        val patches = assertUpdatesQueue(
            currentIds = listOf(1L, 2L, 3L, 4L),
            desiredIds = listOf(8L, 3L, 4L),
            currentSongId = 3L,
        )

        assertEquals(listOf(PlaybackQueuePatch(0, 2, listOf(8L))), patches)
    }

    @Test
    fun `unchanged prefix needs only a suffix replacement`() {
        val patches = assertUpdatesQueue(
            currentIds = listOf(1L, 2L, 3L, 4L),
            desiredIds = listOf(1L, 2L, 5L),
            currentSongId = 2L,
        )

        assertEquals(listOf(PlaybackQueuePatch(2, 4, listOf(5L))), patches)
    }

    @Test
    fun `current at first or last position can stay there`() {
        assertUpdatesQueue(
            currentIds = listOf(3L, 1L, 2L, 4L),
            desiredIds = listOf(3L, 4L, 2L, 5L),
            currentSongId = 3L,
        )
        assertUpdatesQueue(
            currentIds = listOf(1L, 2L, 3L),
            desiredIds = listOf(4L, 2L, 3L),
            currentSongId = 3L,
        )
    }

    @Test
    fun `current can move between first and last position`() {
        assertUpdatesQueue(
            currentIds = listOf(1L, 2L, 3L),
            desiredIds = listOf(3L, 4L, 1L, 2L),
            currentSongId = 3L,
        )
        assertUpdatesQueue(
            currentIds = listOf(3L, 1L, 2L),
            desiredIds = listOf(4L, 1L, 2L, 3L),
            currentSongId = 3L,
        )
    }

    @Test
    fun `desired queue can contain only the current song`() {
        for (currentIds in listOf(
            listOf(1L, 2L, 3L, 4L, 5L),
            listOf(3L, 1L, 2L),
            listOf(1L, 2L, 3L),
        )) {
            assertUpdatesQueue(currentIds, listOf(3L), currentSongId = 3L)
        }
    }

    @Test
    fun `singleton queue can grow on both sides of current`() {
        assertUpdatesQueue(
            currentIds = listOf(3L),
            desiredIds = listOf(1L, 3L, 2L),
            currentSongId = 3L,
        )
    }

    @Test
    fun `unchanged queues need no patches`() {
        assertEquals(emptyList(), assertUpdatesQueue(listOf(1L), listOf(1L), currentSongId = 1L))
        assertEquals(
            emptyList(),
            assertUpdatesQueue(listOf(1L, 2L, 3L), listOf(1L, 2L, 3L), currentSongId = 2L),
        )
    }

    @Test
    fun `old duplicates away from current can be repaired`() {
        assertUpdatesQueue(
            currentIds = listOf(1L, 1L, 3L, 2L, 2L),
            desiredIds = listOf(2L, 3L, 1L),
            currentSongId = 3L,
        )
    }

    @Test
    fun `empty queues and missing current song are invalid`() {
        assertNull(planPlaybackQueueUpdate(emptyList(), listOf(1L), currentSongId = 1L))
        assertNull(planPlaybackQueueUpdate(listOf(1L), emptyList(), currentSongId = 1L))
        assertNull(planPlaybackQueueUpdate(emptyList(), emptyList(), currentSongId = 1L))
        assertNull(planPlaybackQueueUpdate(listOf(1L, 3L), listOf(1L, 2L), currentSongId = 2L))
        assertNull(planPlaybackQueueUpdate(listOf(1L, 2L), listOf(1L, 3L), currentSongId = 2L))
        assertNull(planPlaybackQueueUpdate(listOf(1L), listOf(1L), currentSongId = 2L))
    }

    @Test
    fun `ambiguous current and duplicate desired ids are invalid even when queues match`() {
        assertNull(planPlaybackQueueUpdate(listOf(1L, 2L, 1L), listOf(1L, 2L), currentSongId = 1L))
        assertNull(planPlaybackQueueUpdate(listOf(1L, 2L), listOf(1L, 2L, 1L), currentSongId = 1L))
        assertNull(planPlaybackQueueUpdate(listOf(1L, 2L), listOf(1L, 2L, 2L), currentSongId = 1L))
        assertNull(planPlaybackQueueUpdate(listOf(1L, 1L), listOf(1L, 1L), currentSongId = 1L))
        assertNull(planPlaybackQueueUpdate(listOf(1L, 2L, 2L), listOf(1L, 2L, 2L), currentSongId = 1L))
    }

    @Test
    fun `nonpositive ids in either queue are invalid`() {
        for (invalidId in listOf(0L, -1L, Long.MIN_VALUE)) {
            assertNull(planPlaybackQueueUpdate(listOf(invalidId, 2L), listOf(2L), currentSongId = 2L))
            assertNull(planPlaybackQueueUpdate(listOf(2L), listOf(2L, invalidId), currentSongId = 2L))
            assertNull(planPlaybackQueueUpdate(listOf(1L), listOf(1L), currentSongId = invalidId))
        }
    }

    @Test
    fun `largest positive id is a valid current song`() {
        assertUpdatesQueue(
            currentIds = listOf(Long.MAX_VALUE, 1L),
            desiredIds = listOf(2L, Long.MAX_VALUE),
            currentSongId = Long.MAX_VALUE,
        )
    }

    @Test
    fun `replacement ids are snapshots of mutable input`() {
        val currentIds = mutableListOf(1L, 2L, 3L)
        val desiredIds = mutableListOf(4L, 2L, 5L)
        val patches = assertUpdatesQueue(currentIds, desiredIds, currentSongId = 2L)

        currentIds.clear()
        desiredIds.clear()

        assertEquals(listOf(5L), patches[0].songIds)
        assertEquals(listOf(4L), patches[1].songIds)
    }

    @Test
    fun `seeded permutations preserve current across 540 queue updates`() {
        val random = Random(0x50F7)
        for (size in 5..40) {
            val songIds = (1..size).map { it.toLong() }
            repeat(15) {
                assertUpdatesQueue(
                    currentIds = songIds.shuffled(random),
                    desiredIds = songIds.shuffled(random),
                    currentSongId = songIds[random.nextInt(size)],
                )
            }
        }
    }

    @Test
    fun `540 song queues retain current at boundary and middle positions across seeds`() {
        val songIds = (1L..540L).toList()
        val currentSongId = 271L
        val otherSongIds = songIds.filterNot { it == currentSongId }
        val positions = listOf(0, songIds.size / 2, songIds.lastIndex)
        for (seed in listOf(0x215, 0x216)) {
            val random = Random(seed)
            for (currentIndex in positions) {
                for (desiredIndex in positions) {
                    val currentIds = otherSongIds.shuffled(random).toMutableList().apply {
                        add(currentIndex, currentSongId)
                    }
                    val desiredIds = otherSongIds.shuffled(random).toMutableList().apply {
                        add(desiredIndex, currentSongId)
                    }

                    assertUpdatesQueue(currentIds, desiredIds, currentSongId)
                }
            }
        }
    }

    private fun assertUpdatesQueue(
        currentIds: List<Long>,
        desiredIds: List<Long>,
        currentSongId: Long,
    ): List<PlaybackQueuePatch> {
        val originalIds = currentIds.toList()
        val expectedIds = desiredIds.toList()
        val patches = assertNotNull(planPlaybackQueueUpdate(currentIds, desiredIds, currentSongId))
        assertTrue(patches.size <= 2, "Too many queue replacements: $patches")
        val queue = currentIds.map { QueueItem(it) }.toMutableList()
        val currentItem = queue.single { it.songId == currentSongId }

        patches.forEachIndexed { index, patch ->
            val context = "Patch $index of $patches, queue=${queue.map { it.songId }}"
            assertTrue(patch.fromIndex in 0..queue.size, context)
            assertTrue(patch.toIndex in patch.fromIndex..queue.size, context)
            val replacedItems = queue.subList(patch.fromIndex, patch.toIndex)
            assertFalse(replacedItems.any { it === currentItem }, "Current item would be removed. $context")
            assertFalse(currentSongId in patch.songIds, "Current item would be recreated. $context")

            replacedItems.clear()
            queue.addAll(patch.fromIndex, patch.songIds.map { QueueItem(it) })

            assertSame(currentItem, queue.single { it.songId == currentSongId }, context)
        }

        assertEquals(expectedIds, queue.map { it.songId }, "Queue after applying $patches")
        assertEquals(originalIds, currentIds, "Planner changed the original queue")
        assertEquals(expectedIds, desiredIds, "Planner changed the desired queue")
        return patches
    }

    private class QueueItem(val songId: Long)
}
