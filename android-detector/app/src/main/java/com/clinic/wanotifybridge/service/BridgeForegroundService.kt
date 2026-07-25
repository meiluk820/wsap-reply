package com.clinic.wanotifybridge.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.clinic.wanotifybridge.R
import com.clinic.wanotifybridge.WaBridgeApp
import com.clinic.wanotifybridge.data.BridgeSettings
import com.clinic.wanotifybridge.ui.MainActivity

/**
 * Keeps the process alive so the notification listener isn't reclaimed under Doze or
 * aggressive OEM battery management.
 *
 * It does no work of its own — the listener does the detecting. Its only job is being a
 * foreground component, which is what buys the process its priority.
 */
class BridgeForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val settings = BridgeSettings.get(this)
        val text = if (settings.enabled && settings.isConfigured()) {
            getString(R.string.service_active)
        } else {
            getString(R.string.service_paused)
        }

        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, WaBridgeApp.CHANNEL_BRIDGE)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_bridge)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.clinic.wanotifybridge.STOP"

        fun start(context: Context) {
            val intent = Intent(context, BridgeForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BridgeForegroundService::class.java)
                .setAction(ACTION_STOP)
            context.startService(intent)
        }

        /** Refresh the persistent notification's text after a settings change. */
        fun refresh(context: Context) {
            if (BridgeSettings.get(context).enabled) start(context) else stop(context)
        }
    }
}
