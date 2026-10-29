package com.softmusic.app.update

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** Starts the busy state synchronously and releases it even when the request is cancelled. */
internal class ManualUpdateCheckRunner {
    private var checking = false

    fun launch(
        scope: CoroutineScope,
        check: suspend () -> AppUpdateCheckResult,
        onCheckingChanged: (Boolean) -> Unit,
        onResult: (AppUpdateCheckResult) -> Unit,
    ) {
        if (checking) return
        checking = true
        onCheckingChanged(true)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                onResult(check())
            } finally {
                checking = false
                onCheckingChanged(false)
            }
        }
    }
}
