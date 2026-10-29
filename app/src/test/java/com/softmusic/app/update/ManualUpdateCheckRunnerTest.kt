package com.softmusic.app.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Test

class ManualUpdateCheckRunnerTest {
    @Test
    fun aPendingManualRequestIgnoresDuplicateTapsAndAllowsRetryAfterFailure() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val runner = ManualUpdateCheckRunner()
            val first = CompletableDeferred<AppUpdateCheckResult>()
            val checking = mutableListOf<Boolean>()
            val results = mutableListOf<AppUpdateCheckResult>()
            var calls = 0
            val check: suspend () -> AppUpdateCheckResult = {
                calls++
                if (calls == 1) first.await() else AppUpdateCheckResult.UpToDate
            }
            runner.launch(scope, check, checking::add, results::add)
            runner.launch(scope, check, checking::add, results::add)
            assertEquals(1, calls)
            assertEquals(listOf(true), checking)

            val failure = AppUpdateCheckResult.Failure(AppUpdateFailureReason.Network)
            first.complete(failure)
            assertEquals(listOf(true, false), checking)
            runner.launch(scope, check, checking::add, results::add)
            assertEquals(2, calls)
            assertEquals(listOf(failure, AppUpdateCheckResult.UpToDate), results)
            assertEquals(listOf(true, false, true, false), checking)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun cancellingTheScopeResetsTheBusyStateWithoutPresentingAnError() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val runner = ManualUpdateCheckRunner()
        val checking = mutableListOf<Boolean>()
        val results = mutableListOf<AppUpdateCheckResult>()
        runner.launch(scope, { awaitCancellation() }, checking::add, results::add)
        assertEquals(listOf(true), checking)
        scope.cancel()
        assertEquals(listOf(true, false), checking)
        assertEquals(emptyList<AppUpdateCheckResult>(), results)
    }

    @Test
    fun aCancelledRequestReleasesTheGuardForAnotherManualCheck() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val runner = ManualUpdateCheckRunner()
            val checking = mutableListOf<Boolean>()
            val results = mutableListOf<AppUpdateCheckResult>()
            runner.launch(scope, { throw CancellationException("cancelled") }, checking::add, results::add)
            runner.launch(scope, { AppUpdateCheckResult.UpToDate }, checking::add, results::add)
            assertEquals(listOf(true, false, true, false), checking)
            assertEquals(listOf(AppUpdateCheckResult.UpToDate), results)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun anUnexpectedExceptionStillResetsBusyAndAllowsRetry() {
        val errors = mutableListOf<Throwable>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + CoroutineExceptionHandler { _, error -> errors += error })
        try {
            val runner = ManualUpdateCheckRunner()
            val checking = mutableListOf<Boolean>()
            val results = mutableListOf<AppUpdateCheckResult>()
            val error = IllegalStateException("unexpected failure outside checker")
            runner.launch(scope, { throw error }, checking::add, results::add)
            runner.launch(scope, { AppUpdateCheckResult.UpToDate }, checking::add, results::add)
            assertEquals(listOf(error), errors)
            assertEquals(listOf(true, false, true, false), checking)
            assertEquals(listOf(AppUpdateCheckResult.UpToDate), results)
        } finally {
            scope.cancel()
        }
    }
}
