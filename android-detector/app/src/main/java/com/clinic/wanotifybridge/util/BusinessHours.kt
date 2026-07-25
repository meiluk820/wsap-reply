package com.clinic.wanotifybridge.util

import com.clinic.wanotifybridge.data.BridgeSettings
import java.time.LocalDateTime

object BusinessHours {

    /**
     * True when [at] falls inside the configured window, or when the window is disabled.
     *
     * Windows that wrap past midnight (e.g. 20:00 → 02:00) are supported: the day check
     * applies to the day the window *opens*.
     */
    fun isOpen(settings: BridgeSettings, at: LocalDateTime = LocalDateTime.now()): Boolean {
        if (!settings.businessHoursEnabled) return true

        val start = settings.businessStartMinute
        val end = settings.businessEndMinute
        val minuteOfDay = at.hour * 60 + at.minute
        val today = at.dayOfWeek.value
        val yesterday = at.minusDays(1).dayOfWeek.value
        val days = settings.businessDays

        return if (start <= end) {
            today in days && minuteOfDay in start until end
        } else {
            // Wrapped window: either late on an open day, or early the morning after one.
            (today in days && minuteOfDay >= start) ||
                (yesterday in days && minuteOfDay < end)
        }
    }
}
