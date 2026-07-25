package com.clinic.wanotifybridge.notify

import android.app.Notification
import android.os.Bundle
import android.os.Parcelable
import com.clinic.wanotifybridge.data.ChatType
import com.clinic.wanotifybridge.data.MessageEvent
import com.clinic.wanotifybridge.data.SourceApp

/**
 * Turns a WhatsApp notification into a [MessageEvent], or returns null when there is
 * nothing worth forwarding.
 *
 * Pure and free of Android services so it can be unit-tested with plain Bundles.
 */
object NotificationParser {

    fun parse(
        packageName: String?,
        notification: Notification,
        postTimeMillis: Long,
    ): MessageEvent? {
        val app = SourceApp.fromPackage(packageName) ?: return null

        // Group-summary notifications duplicate their children; calls are ongoing, not messages.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return null

        val extras = notification.extras ?: return null

        // Silent "checking for new messages" / backup notifications carry no title.
        val title = extras.string(Notification.EXTRA_TITLE) ?: return null
        val conversationTitle = extras.string(Notification.EXTRA_CONVERSATION_TITLE)
        val isGroupExtra = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)

        val latest = latestStyledMessage(extras)

        // MessagingStyle is the reliable path: it carries the individual message, its own
        // timestamp, and the per-message sender inside a group. EXTRA_TEXT is the fallback
        // for notifications posted without it.
        var sender: String
        var body: String
        var timestamp: Long
        var groupName: String?

        if (latest != null) {
            body = latest.text
            timestamp = if (latest.timeMillis > 0) latest.timeMillis else postTimeMillis
            val isGroup = isGroupExtra || conversationTitle != null
            groupName = if (isGroup) (conversationTitle ?: title) else null
            sender = latest.sender ?: title
        } else {
            val rawText = extras.string(Notification.EXTRA_TEXT) ?: return null
            timestamp = postTimeMillis
            val isGroup = isGroupExtra || conversationTitle != null
            if (isGroup) {
                groupName = conversationTitle ?: title
                // Group bodies arrive as "Sender: message".
                val split = rawText.split(": ", limit = 2)
                if (split.size == 2 && split[0].isNotBlank()) {
                    sender = split[0].trim()
                    body = split[1]
                } else {
                    sender = title
                    body = rawText
                }
            } else {
                groupName = null
                sender = title
                body = rawText
            }
        }

        body = body.trim()
        sender = sender.trim()
        if (sender.isEmpty()) return null
        if (!BodyFilter.isForwardable(body)) return null

        return MessageEvent(
            sender = sender,
            chatType = if (groupName != null) ChatType.GROUP else ChatType.INDIVIDUAL,
            groupName = groupName,
            message = body,
            timestampMillis = timestamp,
            app = app,
        )
    }

    private data class StyledMessage(val text: String, val sender: String?, val timeMillis: Long)

    /**
     * Pulls the newest entry out of `EXTRA_MESSAGES`. WhatsApp includes the whole recent
     * history of the chat on every re-post, so taking the last entry (rather than all of
     * them) is what keeps re-posts from re-forwarding old messages.
     */
    private fun latestStyledMessage(extras: Bundle): StyledMessage? {
        val messages = extras.parcelableArray(Notification.EXTRA_MESSAGES) ?: return null
        val bundles = messages.filterIsInstance<Bundle>()
        if (bundles.isEmpty()) return null

        val newest = bundles.maxByOrNull { it.getLong("time", 0L) } ?: return null
        val text = newest.string("text") ?: return null
        val sender = newest.string("sender") ?: senderFromPerson(newest)
        return StyledMessage(text, sender, newest.getLong("time", 0L))
    }

    /**
     * On API 28+ the per-message sender is a [android.app.Person] rather than a plain
     * "sender" CharSequence. Newer WhatsApp builds populate only this field for group
     * messages, so without it group senders fall back to the group name.
     */
    @Suppress("DEPRECATION")
    private fun senderFromPerson(bundle: Bundle): String? {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) return null
        val person = bundle.getParcelable("sender_person") as? android.app.Person ?: return null
        return person.name?.toString()?.takeIf { it.isNotBlank() }
    }

    private fun Bundle.string(key: String): String? =
        getCharSequence(key)?.toString()?.takeIf { it.isNotBlank() }

    @Suppress("DEPRECATION")
    private fun Bundle.parcelableArray(key: String): Array<Parcelable>? = getParcelableArray(key)
}
