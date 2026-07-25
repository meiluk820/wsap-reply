package com.clinic.wanotifybridge.notify

/**
 * Suppresses WhatsApp's repeated notification updates for a message we already forwarded.
 *
 * WhatsApp re-posts a conversation's notification on nearly every state change — a second
 * message in the same chat, the summary/rollup notification, read-state updates, group
 * re-renders. All of those arrive as `onNotificationPosted` with the same content, so
 * without this every message would be forwarded several times.
 *
 * Keys are content-derived (see [com.clinic.wanotifybridge.data.MessageEvent.dedupeKey]) and
 * kept for [ttlMillis]; the map is capped at [maxEntries] so it cannot grow unbounded on a
 * busy day. Nothing here is persisted — a restart legitimately re-forwards at most whatever
 * notification is still live in the shade.
 */
class Deduper(
    private val ttlMillis: Long = 10 * 60 * 1000L,
    private val maxEntries: Int = 300,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val seen = LinkedHashMap<String, Long>()

    /** @return true if this key is new (caller should forward), false if it's a repeat. */
    @Synchronized
    fun markIfNew(key: String): Boolean {
        val now = clock()
        prune(now)
        val previous = seen[key]
        if (previous != null && now - previous < ttlMillis) {
            // Refresh so a chat that keeps re-posting stays suppressed for the full TTL.
            seen[key] = now
            return false
        }
        seen[key] = now
        return true
    }

    private fun prune(now: Long) {
        val iterator = seen.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value >= ttlMillis) iterator.remove()
        }
        while (seen.size > maxEntries) {
            val oldest = seen.keys.firstOrNull() ?: break
            seen.remove(oldest)
        }
    }

    @Synchronized
    fun clear() = seen.clear()
}
