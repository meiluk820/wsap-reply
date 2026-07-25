package com.clinic.wanotifybridge.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.clinic.wanotifybridge.data.BridgeSettings

/** Brings the foreground service back after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (BridgeSettings.get(context).enabled) {
                    BridgeForegroundService.start(context)
                }
            }
        }
    }
}
