package com.clinic.wanotifybridge.data

import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Which WhatsApp flavour a notification came from. */
enum class SourceApp(val packageName: String, val wireName: String) {
    WHATSAPP("com.whatsapp", "whatsapp"),
    WHATSAPP_BUSINESS("com.whatsapp.w4b", "whatsapp_business"),
    ;

    companion object {
        fun fromPackage(pkg: String?): SourceApp? = entries.firstOrNull { it.packageName == pkg }
    }
}

enum class ChatType(val wireName: String) {
    INDIVIDUAL("individual"),
    GROUP("group"),
}

/**
 * One detected WhatsApp message, in the exact shape the webhook expects.
 *
 * Instances are short-lived: they exist in memory while a POST is being attempted and
 * are dropped afterwards. Nothing here is written to disk — see [com.clinic.wanotifybridge.util.FailureLog].
 */
data class MessageEvent(
    val sender: String,
    val chatType: ChatType,
    val groupName: String?,
    val message: String,
    /** Epoch millis of the message itself (not of the notification post). */
    val timestampMillis: Long,
    val app: SourceApp,
) {
    fun toJson(): String = JSONObject().apply {
        put("sender", sender)
        put("chat_type", chatType.wireName)
        if (groupName == null) put("group_name", JSONObject.NULL) else put("group_name", groupName)
        put("message", message)
        put("timestamp", isoTimestamp())
        put("app", app.wireName)
    }.toString()

    fun isoTimestamp(): String = ISO.format(Instant.ofEpochMilli(timestampMillis))

    /**
     * Stable identity of this message for de-duplication. WhatsApp re-posts the same
     * notification many times (read-state changes, "N new messages" rollups, group
     * re-renders), so identity has to come from the message content, not the post.
     */
    fun dedupeKey(): String =
        "${app.wireName}|${groupName ?: ""}|$sender|$timestampMillis|${message.hashCode()}"

    private companion object {
        val ISO: DateTimeFormatter =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault())
    }
}
