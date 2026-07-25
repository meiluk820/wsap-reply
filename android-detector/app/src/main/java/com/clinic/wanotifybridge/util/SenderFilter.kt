package com.clinic.wanotifybridge.util

import com.clinic.wanotifybridge.data.BridgeSettings
import com.clinic.wanotifybridge.data.MessageEvent

object SenderFilter {

    /**
     * Decides whether an event may be forwarded.
     *
     * Policy, per the operator's instruction that unknown numbers should still get a reply:
     *  - the block-list always wins;
     *  - an **empty allow-list means allow everyone**, including first-time numbers;
     *  - a non-empty allow-list narrows forwarding to those entries only.
     *
     * Matching is case-insensitive substring, against both the sender and the group name,
     * with non-digits stripped for number comparisons so "+60 12-345 6789", "0123456789"
     * and "60123456789" all match the same list entry.
     */
    fun isAllowed(settings: BridgeSettings, event: MessageEvent): Boolean {
        val candidates = listOfNotNull(event.sender, event.groupName)
        if (settings.blockList.any { entry -> candidates.any { matches(entry, it) } }) return false

        val allow = settings.allowList
        if (allow.isEmpty()) return true
        return allow.any { entry -> candidates.any { matches(entry, it) } }
    }

    private fun matches(entry: String, value: String): Boolean {
        if (entry.isBlank()) return false
        if (value.contains(entry, ignoreCase = true)) return true

        val entryDigits = entry.filter(Char::isDigit)
        val valueDigits = value.filter(Char::isDigit)
        // Compare on the last 8 digits so country-code and trunk-zero variants line up.
        return entryDigits.length >= 7 &&
            valueDigits.length >= 7 &&
            entryDigits.takeLast(8) == valueDigits.takeLast(8)
    }
}
