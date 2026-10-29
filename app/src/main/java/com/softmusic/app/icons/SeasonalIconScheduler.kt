package com.softmusic.app.icons

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.Instant

internal class SeasonalIconScheduler(private val context: Context) {
    fun schedule(at: Instant) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, SeasonalIconReceiver::class.java).setAction(ACTION_REFRESH)
        val pending = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Cosmetic changes can wait for an awake device; no exact-alarm access or wakeup is needed.
        alarmManager.setWindow(AlarmManager.RTC, at.toEpochMilli(), REFRESH_WINDOW_MS, pending)
    }

    companion object {
        const val ACTION_REFRESH = "com.softmusic.app.action.REFRESH_SEASONAL_ICON"
        private const val REFRESH_WINDOW_MS = 60L * 60L * 1_000L
    }
}
