package com.softmusic.app.data

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicLibraryLoaderTest {
    @Test
    fun requestsConflateAndCancelledScansCannotPublishOrOverlapTheLatestScan() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val loaded = Channel<List<Song>>(Channel.UNLIMITED)
        val failures = Channel<Throwable>(Channel.UNLIMITED)
        val calls = AtomicInteger()
        val active = AtomicInteger()
        val maximumActive = AtomicInteger()
        val loader = MusicLibraryLoader(scope, load = {
            val currentActive = active.incrementAndGet()
            maximumActive.updateAndGet { maxOf(it, currentActive) }
            val call = calls.incrementAndGet()
            try {
                if (call == 1) {
                    firstStarted.complete(Unit)
                    // Some storage/decoder implementations return despite cancellation.
                    withContext(NonCancellable) { releaseFirst.await() }
                }
                listOf(song(call.toLong()))
            } finally {
                active.decrementAndGet()
            }
        }, onStarted = {}, onLoaded = { loaded.trySend(it) }, onFailed = { failures.trySend(it) })
        try {
            loader.setEnabled(true)
            loader.refresh()
            withTimeout(3_000L) { firstStarted.await() }
            repeat(4) { loader.refresh() }
            assertEquals(1, calls.get())
            releaseFirst.complete(Unit)
            assertEquals(listOf(2L), withTimeout(3_000L) { loaded.receive() }.map { it.id })
            assertEquals(2, calls.get())
            assertEquals(1, maximumActive.get())
            assertTrue(loaded.tryReceive().isFailure)
            assertTrue(failures.tryReceive().isFailure)
        } finally {
            releaseFirst.complete(Unit)
            loader.close()
            scope.cancel()
        }
    }

    @Test
    fun revokingPermissionSuppressesLateResultsAndEnablingAllowsAFreshRequest() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val loaded = Channel<List<Song>>(Channel.UNLIMITED)
        val failures = Channel<Throwable>(Channel.UNLIMITED)
        val calls = AtomicInteger()
        val loader = MusicLibraryLoader(scope, load = {
            if (calls.incrementAndGet() == 1) {
                firstStarted.complete(Unit)
                withContext(NonCancellable) { releaseFirst.await() }
                throw IOException("late failure from revoked storage")
            }
            listOf(song(2L))
        }, onStarted = {}, onLoaded = { loaded.trySend(it) }, onFailed = { failures.trySend(it) })
        try {
            loader.refresh()
            assertEquals(0, calls.get())
            loader.setEnabled(true)
            loader.refresh()
            withTimeout(3_000L) { firstStarted.await() }
            loader.setEnabled(false)
            loader.refresh()
            loader.setEnabled(true)
            loader.refresh()
            releaseFirst.complete(Unit)
            assertEquals(listOf(2L), withTimeout(3_000L) { loaded.receive() }.map { it.id })
            assertTrue(failures.tryReceive().isFailure)
            assertTrue(loaded.tryReceive().isFailure)
        } finally {
            releaseFirst.complete(Unit)
            loader.close()
            scope.cancel()
        }
    }

    @Test
    fun refreshCancelsCooperativeWorkAndCurrentFailuresDoNotBlockRetries() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val firstStarted = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val loaded = Channel<List<Song>>(Channel.UNLIMITED)
        val failures = Channel<Throwable>(Channel.UNLIMITED)
        val calls = AtomicInteger()
        val expectedFailure = IOException("storage unavailable")
        val loader = MusicLibraryLoader(scope, load = {
            when (calls.incrementAndGet()) {
                1 -> try { firstStarted.complete(Unit); awaitCancellation() } finally { cancelled.complete(Unit) }
                2 -> throw expectedFailure
                else -> listOf(song(3L))
            }
        }, onStarted = {}, onLoaded = { loaded.trySend(it) }, onFailed = { failures.trySend(it) })
        try {
            loader.setEnabled(true)
            loader.refresh()
            withTimeout(3_000L) { firstStarted.await() }
            loader.refresh()
            withTimeout(3_000L) { cancelled.await() }
            val failure = withTimeout(3_000L) { failures.receive() }
            assertEquals(IOException::class.java, failure.javaClass)
            assertEquals(expectedFailure.message, failure.message)
            // Coroutine stacktrace recovery may wrap a copy around the original exception.
            val originalFailure = generateSequence(failure) { it.cause }.last()
            assertEquals(IOException::class.java, originalFailure.javaClass)
            assertEquals(expectedFailure.message, originalFailure.message)
            loader.refresh()
            assertEquals(listOf(3L), withTimeout(3_000L) { loaded.receive() }.map { it.id })
        } finally {
            loader.close()
            scope.cancel()
        }
    }

    @Test
    fun closingCancelsTheScanAndDisallowsFurtherRequestsOrPublications() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val loaded = Channel<List<Song>>(Channel.UNLIMITED)
        val failures = Channel<Throwable>(Channel.UNLIMITED)
        val calls = AtomicInteger()
        val loader = MusicLibraryLoader(scope, load = {
            calls.incrementAndGet()
            try { started.complete(Unit); awaitCancellation() } finally { cancelled.complete(Unit) }
        }, onStarted = {}, onLoaded = { loaded.trySend(it) }, onFailed = { failures.trySend(it) })
        try {
            loader.setEnabled(true)
            loader.refresh()
            withTimeout(3_000L) { started.await() }
            loader.close()
            loader.close()
            withTimeout(3_000L) { cancelled.await() }
            loader.setEnabled(true)
            loader.refresh()
            assertEquals(1, calls.get())
            assertTrue(loaded.tryReceive().isFailure)
            assertTrue(failures.tryReceive().isFailure)
        } finally {
            loader.close()
            scope.cancel()
        }
    }

    private fun song(id: Long) = Song(id, "Song $id", "Artist", "Album", 60_000L, 2_000_000L, id, "/music", "music", "content://music/$id", null)
}
