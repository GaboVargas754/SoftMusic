package com.softmusic.app.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupLibraryReadinessTest {
    @Test
    fun preparationWaitsForTheScanStartedWhenReturningFromSaf() = runBlocking {
        val readiness = MutableStateFlow(BackupLibraryReadiness.Loading)
        var prepared = false
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            prepareWhenBackupLibraryReady(readiness) { prepared = true; "document" }
        }
        assertFalse(result.isCompleted)
        assertFalse(prepared)
        readiness.value = BackupLibraryReadiness.Ready
        assertEquals("document", result.await())
        assertTrue(prepared)
    }

    @Test
    fun lostPermissionStopsTheWaitWithoutPreparingAnyData() = runBlocking {
        supervisorScope {
            val readiness = MutableStateFlow(BackupLibraryReadiness.Loading)
            var prepared = false
            val result = async(start = CoroutineStart.UNDISPATCHED) {
                prepareWhenBackupLibraryReady(readiness) { prepared = true; "document" }
            }
            readiness.value = BackupLibraryReadiness.PermissionRequired
            val failure = runCatching { result.await() }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
            assertEquals(BACKUP_LIBRARY_PERMISSION_MESSAGE, failure?.message)
            assertFalse(prepared)
        }
    }

    @Test
    fun anIncompleteScanTimesOutWithARetryMessageInsteadOfCancellation() = runBlocking {
        val failure = runCatching {
            prepareWhenBackupLibraryReady(MutableStateFlow(BackupLibraryReadiness.Loading), timeoutMs = 1L) { "document" }
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals(BACKUP_LIBRARY_TIMEOUT_MESSAGE, failure?.message)
    }

    @Test
    fun callerCancellationStillCancelsTheWait() = runBlocking {
        val readiness = MutableStateFlow(BackupLibraryReadiness.Loading)
        var prepared = false
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            prepareWhenBackupLibraryReady(readiness) { prepared = true; "document" }
        }
        result.cancel()
        val failure = runCatching { result.await() }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertFalse(prepared)
    }

    @Test
    fun theSameDeadlineCoversRepeatedLibrarySnapshotInvalidation() = runBlocking {
        var attempts = 0
        val failure = runCatching {
            prepareWhenBackupLibraryReady<String>(MutableStateFlow(BackupLibraryReadiness.Ready), timeoutMs = 25L) {
                attempts++
                delay(2L)
                null // A refresh invalidated the captured library or collection revision.
            }
        }.exceptionOrNull()
        assertTrue(attempts > 0)
        assertTrue(failure is IllegalStateException)
        assertEquals(BACKUP_LIBRARY_TIMEOUT_MESSAGE, failure?.message)
    }

    @Test
    fun failedScanReturnsAnActionableErrorWithoutPreparingStaleLibraryData() = runBlocking {
        var prepared = false
        val failure = runCatching {
            prepareWhenBackupLibraryReady(MutableStateFlow(BackupLibraryReadiness.Failed)) { prepared = true; "document" }
        }.exceptionOrNull()
        assertEquals(BACKUP_LIBRARY_FAILED_MESSAGE, failure?.message)
        assertFalse(prepared)
    }
}
