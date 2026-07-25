package com.clinic.wanotifybridge.notify

/**
 * Decides whether a notification body is an actual message worth forwarding.
 *
 * Kept free of Android types so it can be unit-tested directly.
 */
object BodyFilter {

    /** Rollup text WhatsApp posts instead of real content; never a message. */
    private val ROLLUP =
        Regex("""^\d+\s+(new\s+)?messages?(\s+from\s+\d+\s+chats?)?$""", RegexOption.IGNORE_CASE)

    /** Missed-call and media-only bodies with no caption to reply to. */
    private val NON_TEXT_BODIES = setOf(
        "missed voice call", "missed video call", "missed group voice call",
        "missed group video call", "photo", "video", "audio", "voice message",
        "document", "sticker", "gif", "contact", "location", "live location",
        "view once photo", "view once video", "📷 photo", "🎥 video", "🎤 voice message",
    )

    fun isForwardable(body: String): Boolean {
        val trimmed = body.trim()
        if (trimmed.isBlank()) return false
        if (ROLLUP.matches(trimmed)) return false
        // A media notification *with* a caption reads "📷 Photo\ncaption" — that's a real
        // message, so only drop bodies that are nothing but the label.
        return trimmed.lowercase() !in NON_TEXT_BODIES
    }
}
