package com.softmusic.app.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CollectionMixModelsTest {
    @Test
    fun `only favorites and a specific playlist produce collection keys`() {
        assertEquals("favorites", collectionMixKey(PlaybackQueueSource.Favorites))
        assertEquals("playlist:one", collectionMixKey(PlaybackQueueSource.Playlist, "one"))
        assertNotEquals(collectionMixKey(PlaybackQueueSource.Playlist, "one"), collectionMixKey(PlaybackQueueSource.Playlist, "two"))
        assertNull(collectionMixKey(PlaybackQueueSource.Playlist))
        assertNull(collectionMixKey(PlaybackQueueSource.Playlist, " "))
        for (source in listOf(PlaybackQueueSource.Songs, PlaybackQueueSource.Album, PlaybackQueueSource.Artist)) {
            assertNull(collectionMixKey(source, "same"))
        }
    }

    @Test
    fun `consent and enablement are both necessary for collection mixing`() {
        assertFalse(CollectionMixConfig().isEnabled)
        assertFalse(CollectionMixConfig(enabled = true).isEnabled)
        assertFalse(CollectionMixConfig(accepted = true).isEnabled)
        assertTrue(CollectionMixConfig(accepted = true, enabled = true).isEnabled)
        assertFalse(CollectionMixConfig(enabled = true).sanitized().enabled)
    }

    @Test
    fun `collection preference is independent from other collections`() {
        val favorites = CollectionMixConfig(accepted = true, enabled = true)
        val playlist = CollectionMixConfig()
        assertTrue(context(config = favorites).permits(PlaybackMode.Ordered))
        assertFalse(context(source = PlaybackQueueSource.Playlist, key = "one", config = playlist).permits(PlaybackMode.Ordered))
    }

    @Test
    fun `manual general library artist album and unknown playlist sources never permit mixing`() {
        val enabled = CollectionMixConfig(accepted = true, enabled = true)
        for (source in listOf(PlaybackQueueSource.Songs, PlaybackQueueSource.Artist, PlaybackQueueSource.Album)) {
            assertFalse(context(source, "one", enabled).permits(PlaybackMode.Ordered))
        }
        assertFalse(context(PlaybackQueueSource.Playlist, null, enabled).permits(PlaybackMode.Ordered))
    }

    @Test
    fun `shuffle and repeat current disable mixing for accepted collections`() {
        val context = context()
        assertTrue(context.permits(PlaybackMode.Ordered))
        assertTrue(context.permits(PlaybackMode.RepeatList))
        assertFalse(context.permits(PlaybackMode.Shuffle))
        assertFalse(context.permits(PlaybackMode.RepeatCurrent))
    }

    @Test
    fun `only distinct pair members inside the active source can mix`() {
        val context = context()
        assertTrue(context.permitsPair(1, 2, PlaybackMode.Ordered))
        assertTrue(context.permitsPair(2, 1, PlaybackMode.RepeatList))
        assertFalse(context.permitsPair(1, 1, PlaybackMode.Ordered))
        assertFalse(context.permitsPair(1, 3, PlaybackMode.Ordered))
        assertFalse(context.permitsPair(3, 2, PlaybackMode.Ordered))
        assertFalse(context.permitsPair(1, 2, PlaybackMode.Shuffle))
        assertFalse(context(config = CollectionMixConfig(enabled = true)).permitsPair(1, 2, PlaybackMode.Ordered))
    }

    @Test
    fun `saved transition settings are directional and default to automatic`() {
        val custom = MixTransitionSettings(mode = MixTransitionMode.Custom, durationMs = 6_000)
        val off = MixTransitionSettings(mode = MixTransitionMode.Off)
        val config = CollectionMixConfig(transitions = mapOf(mixPairKey(1, 2) to custom, mixPairKey(2, 3) to off))

        assertEquals(custom, config.transition(1, 2))
        assertEquals(MixTransitionMode.Off, config.transition(2, 3).mode)
        assertEquals(MixTransitionMode.Automatic, config.transition(2, 1).mode)
        assertEquals(MixTransitionMode.Automatic, config.transition(1, 3).mode)
    }

    @Test
    fun `sanitizing settings contains invalid ranges and non finite numbers`() {
        val config = CollectionMixConfig(
            accepted = true,
            enabled = true,
            settings = AutoDjSettings(beats = 7, intensity = Float.NaN),
            orderedSongIds = listOf(3, -1, 2, 3, 0),
            transitions = mapOf(mixPairKey(1, 2) to MixTransitionSettings(
                mode = MixTransitionMode.Custom,
                durationMs = Long.MAX_VALUE,
                outgoingStartMs = -10,
                incomingStartMs = -20,
                intensity = Float.POSITIVE_INFINITY,
                outgoingVolume = -2f,
                incomingVolume = Float.NaN,
                outgoingEq = MixEq(-50f, 50f, Float.NaN),
                incomingEq = MixEq(Float.NEGATIVE_INFINITY, -2f, 3f),
            )),
        ).sanitized()

        assertEquals(listOf(3L, 2L), config.orderedSongIds)
        assertEquals(16, config.settings.beats)
        assertEquals(.5f, config.settings.intensity)
        val transition = config.transition(1, 2)
        assertEquals(24_000L, transition.durationMs)
        assertEquals(0L, transition.outgoingStartMs)
        assertEquals(0L, transition.incomingStartMs)
        assertEquals(.5f, transition.intensity)
        assertEquals(0f, transition.outgoingVolume)
        assertEquals(1f, transition.incomingVolume)
        assertEquals(MixEq(-12f, 6f, 0f), transition.outgoingEq)
        assertEquals(MixEq(0f, -2f, 3f), transition.incomingEq)
        assertTrue(config.isEnabled)
    }

    private fun context(
        source: PlaybackQueueSource = PlaybackQueueSource.Favorites,
        key: String? = null,
        config: CollectionMixConfig = CollectionMixConfig(accepted = true, enabled = true),
    ) = CollectionMixContext(source, key, setOf(1, 2), config)
}
