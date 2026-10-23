package com.softmusic.app.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.softmusic.app.MainActivity
import com.softmusic.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class WebPanelService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private var server: WebPanelServer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        server = WebPanelServer(
            context = applicationContext,
            scope = serviceScope,
            snapshotProvider = WebPanelBridge::snapshot,
            commandHandler = WebPanelBridge::handleCommand,
        )
        WebPanelBridge.updateServerState(WebPanelServerState())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> {
                stopPanel()
                START_NOT_STICKY
            }
            else -> {
                startPanel()
                START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        server?.stop()
        WebPanelBridge.updateServerState(WebPanelServerState())
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun startPanel() {
        val webPanelServer = server ?: return
        runCatching { webPanelServer.start() }
            .onSuccess {
                val state = webPanelServer.state.value
                WebPanelBridge.updateServerState(state)
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification(state),
                    foregroundServiceType(),
                )
            }
            .onFailure {
                WebPanelBridge.updateServerState(WebPanelServerState())
                stopSelf()
            }
    }

    private fun stopPanel() {
        server?.stop()
        WebPanelBridge.updateServerState(WebPanelServerState())
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(state: WebPanelServerState): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            REQUEST_OPEN_APP,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_STOP,
            stopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val contentText = state.url?.let { getString(R.string.notification_web_panel_text, it) }
            ?: getString(R.string.notification_web_panel_running)

        return NotificationCompat.Builder(this, WEB_PANEL_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_web_panel_notification)
            .setContentTitle(getString(R.string.notification_web_panel_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                R.drawable.ic_web_panel_notification,
                getString(R.string.notification_web_panel_stop),
                stopIntent,
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (notificationManager.getNotificationChannel(WEB_PANEL_NOTIFICATION_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            WEB_PANEL_NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_web_panel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_web_panel)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun foregroundServiceType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
    }

    companion object {
        private const val ACTION_START = "com.softmusic.app.player.action.START_WEB_PANEL"
        private const val ACTION_STOP = "com.softmusic.app.player.action.STOP_WEB_PANEL"
        private const val WEB_PANEL_NOTIFICATION_CHANNEL_ID = "web_panel"
        private const val NOTIFICATION_ID = 2402
        private const val REQUEST_OPEN_APP = 10
        private const val REQUEST_STOP = 11

        fun startIntent(context: Context): Intent {
            return Intent(context, WebPanelService::class.java).setAction(ACTION_START)
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, WebPanelService::class.java).setAction(ACTION_STOP)
        }
    }
}
