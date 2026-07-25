package com.clinic.wanotifybridge.util

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * A bounded, on-device record of webhook deliveries that failed, so the operator can see
 * *that* something was dropped and why.
 *
 * Deliberately stores no message bodies. This app is a bridge, not a message store: the
 * only durable trace of a message is the fact that a delivery attempt for it failed.
 */
object FailureLog {

    private const val FILE_NAME = "webhook_failures.log"
    private const val MAX_LINES = 100

    @Synchronized
    fun record(context: Context, sender: String, reason: String) {
        val file = File(context.filesDir, FILE_NAME)
        val stamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        val line = "$stamp\t${redact(sender)}\t${reason.take(160).replace('\n', ' ')}"

        val lines = if (file.exists()) file.readLines() else emptyList()
        val trimmed = (lines + line).takeLast(MAX_LINES)
        file.writeText(trimmed.joinToString("\n"))
    }

    @Synchronized
    fun read(context: Context): List<String> {
        val file = File(context.filesDir, FILE_NAME)
        return if (file.exists()) file.readLines().asReversed() else emptyList()
    }

    @Synchronized
    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }

    /** Keep enough of the sender to identify the chat, not enough to rebuild a contact list. */
    private fun redact(sender: String): String =
        if (sender.length <= 4) sender else sender.take(2) + "…" + sender.takeLast(2)
}
