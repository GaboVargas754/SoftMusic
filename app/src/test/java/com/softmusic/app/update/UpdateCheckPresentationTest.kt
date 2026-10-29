package com.softmusic.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckPresentationTest {
    @Test
    fun startupSilentlyIgnoresCurrentVersionsAndAllFailures() {
        assertEquals(UpdateCheckPresentation(), AppUpdateCheckResult.UpToDate.presentation(manual = false, skippedVersionCode = -1L))
        for (reason in reasons) {
            assertEquals(UpdateCheckPresentation(), AppUpdateCheckResult.Failure(reason).presentation(manual = false, skippedVersionCode = -1L))
        }
    }

    @Test
    fun startupRespectsSkippedVersionsButAlwaysShowsRequiredUpdates() {
        assertNull(AppUpdateCheckResult.Available(info).presentation(manual = false, skippedVersionCode = 30L).availableUpdate)
        assertEquals(info, AppUpdateCheckResult.Available(info).presentation(manual = false, skippedVersionCode = 29L).availableUpdate)
        val required = info.copy(required = true)
        assertEquals(required, AppUpdateCheckResult.Available(required).presentation(manual = false, skippedVersionCode = 30L).availableUpdate)
    }

    @Test
    fun manualChecksShowSkippedUpdatesAndKeepFailureMessagesSeparateFromUpToDate() {
        assertEquals(info, AppUpdateCheckResult.Available(info).presentation(manual = true, skippedVersionCode = 30L).availableUpdate)
        val upToDate = AppUpdateCheckResult.UpToDate.presentation(manual = true, skippedVersionCode = -1L)
        assertEquals("No hay actualizaciones disponibles.", upToDate.status)
        for (reason in reasons) {
            val failure = AppUpdateCheckResult.Failure(reason).presentation(manual = true, skippedVersionCode = -1L)
            assertNotNull(failure.status)
            assertNull(failure.availableUpdate)
            assertTrue(failure.status != upToDate.status)
        }
    }

    private val info = AppUpdateInfo(30L, "3.1", "", "https://example.invalid/update.apk", false)
    private val reasons = listOf(AppUpdateFailureReason.Timeout, AppUpdateFailureReason.Network, AppUpdateFailureReason.Http(503), AppUpdateFailureReason.InvalidResponse, AppUpdateFailureReason.Unexpected)
}
