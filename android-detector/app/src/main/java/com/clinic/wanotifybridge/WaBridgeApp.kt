package com.clinic.wanotifybridge

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class WaBridgeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_BRIDGE,
            getString(R.string.channel_bridge_name),
            // MIN keeps the persistent notification silent and collapsed in the shade.
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = getString(R.string.channel_bridge_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_BRIDGE = "bridge_status"
    }
}
