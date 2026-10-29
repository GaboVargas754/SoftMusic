package com.softmusic.app.player

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SmartQueuePlannerTest {
    @Test
    fun `deterministic order follows reliable tempo from each selected song`() {
        val profiles = mapOf(1L to profile(120f), 2L to profile(170f), 3L to profile(122f), 4L to profile(125f))

        assertEquals(listOf(3L, 4L, 2L), SmartQueuePlanner.order(1, listOf(2, 3, 4), profiles, randomized = false))
        assertEquals(
            SmartQueuePlanner.order(1, listOf(2, 3, 4), profiles, randomized = false, Random(1)),
            SmartQueuePlanner.order(1, listOf(2, 3, 4), profiles, randomized = false, Random(999)),
        )
    }

    @Test
    fun `measured opening level chooses the gentler change at equal tempo`() {
        val profiles = mapOf(
            1L to profile(120f, rms = 0.1f),
            2L to profile(120f, rms = 0.8f),
            3L to profile(120f, rms = 0.11f),
        )

        assertEquals(listOf(3L, 2L), SmartQueuePlanner.order(1, listOf(2, 3), profiles, randomized = false))
    }

    @Test
    fun `missing and invalid evidence retain fallback order even in smart shuffle`() {
        val pending = listOf(2L, 3L, 4L, 5L)
        val profiles = mapOf(
            1L to SmartSongProfile(Float.NaN, 1f, Float.NaN),
            2L to SmartSongProfile(120f, 0.2f, -1f),
            3L to SmartSongProfile(120f, Float.NaN, Float.POSITIVE_INFINITY),
            4L to SmartSongProfile(0f, 1f, 2f),
        )

        assertEquals(pending, SmartQueuePlanner.order(1, pending, profiles, randomized = true, Random(4)))
        assertEquals(pending, SmartQueuePlanner.order(1, pending, emptyMap(), randomized = false))
    }

    @Test
    fun `unreliable pulse does not beat a measured reliable match`() {
        val profiles = mapOf(1L to profile(120f), 2L to SmartSongProfile(120f, 0.71f), 3L to profile(122f))

        assertEquals(listOf(3L, 2L), SmartQueuePlanner.order(1, listOf(2, 3), profiles, randomized = false))
    }

    @Test
    fun `shuffle varies among close measured matches and excludes unknown or far tempo`() {
        val pending = listOf(2L, 3L, 4L, 5L, 6L, 7L)
        val profiles = mapOf(
            1L to profile(120f), 3L to profile(120f), 4L to profile(121f),
            5L to profile(119f), 6L to profile(122f), 7L to profile(180f),
        )
        val openings = (0..31).map { seed ->
            val ordered = SmartQueuePlanner.order(1, pending, profiles, randomized = true, Random(seed))
            assertEquals(pending.toSet(), ordered.toSet())
            assertEquals(pending.size, ordered.size)
            ordered.first()
        }.toSet()

        assertTrue(openings.size > 1)
        assertTrue(openings.all { it in setOf(3L, 4L, 5L, 6L) })
        assertEquals(
            SmartQueuePlanner.order(1, pending, profiles, randomized = true, Random(123)),
            SmartQueuePlanner.order(1, pending, profiles, randomized = true, Random(123)),
        )
    }

    @Test
    fun `unknown oldest entry gets a bounded turn among compatible songs`() {
        val pending = (2L..100L).toList()
        val profiles = (1L..100L).filterNot { it == 2L }.associateWith { profile(120f) }

        for (randomized in listOf(false, true)) {
            val result = SmartQueuePlanner.order(1, pending, profiles, randomized, Random(22))
            assertTrue(result.indexOf(2L) <= 8)
            assertEquals(pending.toSet(), result.toSet())
            assertEquals(pending.size, result.size)
        }
    }

    @Test
    fun `lookahead stays bounded instead of searching the whole queue`() {
        val pending = (2L..20_000L).toList()
        val profiles = pending.associateWith { profile(if (it == 14L) 120f else 180f) } + (1L to profile(120f))

        val result = SmartQueuePlanner.order(1, pending, profiles, randomized = false)

        assertEquals(2L, result.first())
        assertEquals(pending.toSet(), result.toSet())
        assertEquals(pending.size, result.size)
    }

    @Test
    fun `empty and single pending queues preserve membership`() {
        assertEquals(emptyList(), SmartQueuePlanner.order(1, emptyList(), emptyMap(), randomized = true))
        assertEquals(listOf(2L), SmartQueuePlanner.order(1, listOf(2), emptyMap(), randomized = true))
    }

    private fun profile(bpm: Float, rms: Float? = null) = SmartSongProfile(bpm, confidence = 0.95f, rms)
}
