package com.softmusic.app.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CollectionMixOrderingTest {
    @Test
    fun `suggestion follows nearest BPM from the original opening song`() {
        val songs = listOf(1L, 2L, 3L, 4L)
        val profiles = tempos(1L to 100f, 2L to 140f, 3L to 105f, 4L to 125f)

        assertEquals(listOf(1L, 3L, 4L, 2L), suggestMixOrder(songs, profiles, emptyMap()))
        assertEquals(songs, listOf(1L, 2L, 3L, 4L))
    }

    @Test
    fun `equal distance keeps original order deterministically`() {
        val songs = listOf(1L, 2L, 3L)
        val profiles = tempos(1L to 100f, 2L to 110f, 3L to 90f)

        repeat(3) { assertEquals(songs, suggestMixOrder(songs, profiles, emptyMap())) }
    }

    @Test
    fun `unknown songs remain at exact indices and split independent segments`() {
        val songs = listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L)
        val profiles = tempos(1L to 100f, 2L to 150f, 3L to 105f, 5L to 120f, 6L to 180f, 7L to 125f)

        assertEquals(listOf(1L, 3L, 2L, 4L, 5L, 7L, 6L), suggestMixOrder(songs, profiles, emptyMap()))
    }

    @Test
    fun `low confidence non finite and invalid tempos remain unknown`() {
        val songs = listOf(1L, 2L, 3L, 4L, 5L)
        val profiles = tempos(1L to 100f, 2L to 101f, 3L to Float.NaN, 4L to -2f, 5L to 120f)
            .toMutableMap().apply { this[2L] = getValue(2L).copy(confidence = .719f) }

        assertEquals(songs, suggestMixOrder(songs, profiles, emptyMap()))
    }

    @Test
    fun `custom and disabled adjacent pairs move as complete blocks`() {
        val songs = listOf(1L, 2L, 3L, 4L, 5L)
        val profiles = tempos(1L to 100f, 2L to 160f, 3L to 110f, 4L to 105f, 5L to 150f)
        for (mode in listOf(MixTransitionMode.Custom, MixTransitionMode.Off)) {
            val transitions = mapOf(mixPairKey(2L, 3L) to MixTransitionSettings(mode = mode))
            val result = suggestMixOrder(songs, profiles, transitions)
            assertEquals(listOf(1L, 4L, 5L, 2L, 3L), result)
            assertEquals(result.indexOf(2L) + 1, result.indexOf(3L))
        }
    }

    @Test
    fun `custom pair chains retain all pair relationships`() {
        val songs = listOf(1L, 2L, 3L, 4L, 5L, 6L)
        val profiles = tempos(1L to 100f, 2L to 150f, 3L to 110f, 4L to 180f, 5L to 105f, 6L to 120f)
        val transitions = mapOf(
            mixPairKey(2, 3) to MixTransitionSettings(mode = MixTransitionMode.Custom),
            mixPairKey(3, 4) to MixTransitionSettings(mode = MixTransitionMode.Off),
        )

        val result = suggestMixOrder(songs, profiles, transitions)

        assertEquals(listOf(1L, 5L, 6L, 2L, 3L, 4L), result)
    }

    @Test
    fun `a block containing an unknown song remains entirely immobile`() {
        val songs = listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L)
        val profiles = tempos(1L to 100f, 2L to 160f, 3L to 105f, 5L to 120f, 6L to 130f, 7L to 180f, 8L to 135f)
        val transitions = mapOf(
            mixPairKey(3, 4) to MixTransitionSettings(mode = MixTransitionMode.Custom),
            mixPairKey(4, 5) to MixTransitionSettings(mode = MixTransitionMode.Custom),
        )

        val result = suggestMixOrder(songs, profiles, transitions)

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 6L, 8L, 7L), result)
        assertEquals(songs.subList(2, 5), result.subList(2, 5))
    }

    @Test
    fun `non adjacent saved pairs and automatic transitions do not constrain suggestion`() {
        val songs = listOf(1L, 2L, 3L, 4L)
        val profiles = tempos(1L to 100f, 2L to 150f, 3L to 105f, 4L to 110f)
        val transitions = mapOf(
            mixPairKey(2, 4) to MixTransitionSettings(mode = MixTransitionMode.Custom),
            mixPairKey(2, 3) to MixTransitionSettings(mode = MixTransitionMode.Automatic),
        )

        assertEquals(listOf(1L, 3L, 4L, 2L), suggestMixOrder(songs, profiles, transitions))
    }

    @Test
    fun `enabled alternate order normalizes saved ids and appends new members`() {
        val config = CollectionMixConfig(accepted = true, enabled = true, orderedSongIds = listOf(3L, 99L, 1L, 3L))

        assertEquals(listOf(3L, 1L, 2L, 4L), collectionMixOrder(listOf(1L, 2L, 3L, 4L), config))
        assertEquals(listOf(3L, 99L, 1L, 3L), config.orderedSongIds)
    }

    @Test
    fun `unaccepted disabled and restored configs preserve collection order`() {
        val songs = listOf(1L, 2L, 3L)
        val ordered = CollectionMixConfig(orderedSongIds = listOf(3L, 2L, 1L))

        assertEquals(songs, collectionMixOrder(songs, ordered.copy(enabled = true)))
        assertEquals(songs, collectionMixOrder(songs, ordered.copy(accepted = true)))
        assertEquals(songs, collectionMixOrder(songs, ordered.copy(accepted = true, enabled = true, orderedSongIds = emptyList())))
    }

    @Test
    fun `empty singleton and all unknown inputs retain every song`() {
        assertEquals(emptyList(), suggestMixOrder(emptyList(), emptyMap(), emptyMap()))
        assertEquals(listOf(1L), suggestMixOrder(listOf(1L), tempos(1L to 120f), emptyMap()))
        val songs = (1L..10L).toList()
        assertEquals(songs, suggestMixOrder(songs, emptyMap(), emptyMap()))
        assertTrue(collectionMixOrder(songs, CollectionMixConfig()).containsAll(songs))
    }

    private fun tempos(vararg values: Pair<Long, Float>) = values.associate { (id, bpm) ->
        id to DjBeatProfile(bpm, .95f, 100.0, 0, 32_000)
    }
}
