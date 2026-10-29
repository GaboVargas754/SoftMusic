package com.softmusic.app.icons

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SeasonalIconReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS) return
        val result = goAsync()
        SeasonalIconManager.get(context).refresh { result.finish() }
    }

    private companion object {
        val SUPPORTED_ACTIONS = setOf(
            SeasonalIconScheduler.ACTION_REFRESH,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
