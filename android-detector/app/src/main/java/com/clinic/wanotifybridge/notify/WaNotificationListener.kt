package com.clinic.wanotifybridge.notify

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.clinic.wanotifybridge.data.BridgeSettings
import com.clinic.wanotifybridge.data.ChatType
import com.clinic.wanotifybridge.data.SourceApp
import com.clinic.wanotifybridge.net.WebhookClient
import com.clinic.wanotifybridge.util.BusinessHours
import com.clinic.wanotifybridge.util.SenderFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The detector. Watches WhatsApp / WhatsApp Business notifications and forwards genuinely
 * new messages to the webhook.
 *
 * This service reads notifications only. It never replies, never marks anything read, and
 * holds no reply actions — sending is entirely the Routine's job via WhatsApp Web.
 */
class WaNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob())
    private val deduper = Deduper()

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "listener connected")
        // A reconnect means we may have missed posts while disconnected; old dedupe keys
        // are worthless and could suppress a legitimately new message.
        deduper.clear()
    }

    override fun onListenerDisconnected() {
        Log.w(TAG, "listener disconnected")
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        if (SourceApp.fromPackage(sbn.packageName) == null) return

        val settings = BridgeSettings.get(this)
        if (!settings.enabled) return
        if (!settings.isConfigured()) {
            Log.w(TAG, "message detected but webhook is not configured; ignoring")
            return
        }

        val event = NotificationParser.parse(sbn.packageName, notification, sbn.postTime) ?: return

        if (event.chatType == ChatType.GROUP && !settings.forwardGroups) return
        if (!SenderFilter.isAllowed(settings, event)) return
        if (!BusinessHours.isOpen(settings)) return
        if (!deduper.markIfNew(event.dedupeKey())) return

        scope.launch {
            val delivered = WebhookClient.send(applicationContext, event)
            Log.i(TAG, "forwarded=${delivered} app=${event.app.wireName}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Nothing to do: dismissing a notification tells us nothing about the message.
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WaNotifyListener"

        /**
         * Notification access cannot be granted programmatically — the user must toggle it
         * in system settings. This is how we tell whether they have.
         */
        fun hasNotificationAccess(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ) ?: return false
            val expected = ComponentName(context, WaNotificationListener::class.java)
            return enabled.split(':').any {
                ComponentName.unflattenFromString(it) == expected
            }
        }
    }
}
