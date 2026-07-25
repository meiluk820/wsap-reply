package com.clinic.wanotifybridge.util

/** A half-open range of minutes past midnight, local time. Wraps past midnight if end < start. */
data class TimeWindow(val startMinute: Int, val endMinute: Int) {

    fun contains(minuteOfDay: Int): Boolean =
        if (startMinute <= endMinute) {
            minuteOfDay >= startMinute && minuteOfDay < endMinute
        } else {
            minuteOfDay >= startMinute || minuteOfDay < endMinute
        }

    override fun toString(): String = "${format(startMinute)}-${format(endMinute)}"

    companion object {
        /**
         * End-of-day renders as "24:00", not "00:00" — a window shown as "20:00-00:00" reads
         * as zero-length or wrapping when it means "until midnight".
         */
        fun format(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
    }
}

/**
 * When the bridge should be forwarding.
 *
 * The rule is not "during business hours" — it's the opposite. Forwarding matters exactly when
 * nobody at the clinic is free to answer WhatsApp:
 *
 *  - **outside opening hours**, because nobody is there at all;
 *  - **on days the clinic is closed**, all day, for the same reason;
 *  - **during peak windows inside opening hours**, because the team is with patients.
 *
 * The calm stretches of the working day are the only times forwarding is off: staff can see and
 * answer WhatsApp themselves, and a bot replying over them is worse than a slightly slower human.
 *
 * Pure data with no Android dependencies, so the schedule logic is unit-testable.
 */
data class ActiveSchedule(
    /** When false the bridge is always active — no time-of-day gating at all. */
    val enabled: Boolean,
    /** Clinic opening time, minutes past midnight. */
    val openMinute: Int,
    /** Clinic closing time, minutes past midnight. */
    val closeMinute: Int,
    /** Days the clinic is open, as [java.time.DayOfWeek.getValue] (1 = Monday). */
    val openDays: Set<Int>,
    /** Busy stretches *within* opening hours when the team can't get to WhatsApp. */
    val peakWindows: List<TimeWindow>,
) {

    fun isActiveAt(dayOfWeek: Int, minuteOfDay: Int): Boolean {
        if (!enabled) return true
        // Clinic shut for the day: nobody is watching WhatsApp, so forward everything.
        if (dayOfWeek !in openDays) return true

        val open = TimeWindow(openMinute, closeMinute)
        if (!open.contains(minuteOfDay)) return true

        // Inside opening hours, only the peak windows are active.
        return peakWindows.any { it.contains(minuteOfDay) }
    }

    /** The stretches of an open day when forwarding is on. For the settings-screen preview. */
    fun activeWindows(dayOfWeek: Int): List<TimeWindow> =
        runsWhere(dayOfWeek) { active -> active }

    /** The calm stretches, when the team answers WhatsApp themselves. */
    fun quietWindows(dayOfWeek: Int): List<TimeWindow> =
        runsWhere(dayOfWeek) { active -> !active }

    /**
     * Collapses the day into contiguous runs matching [wanted].
     *
     * Walked minute by minute rather than with interval arithmetic: 1440 steps costs nothing,
     * and it handles overlapping and midnight-wrapping windows without special cases.
     */
    private fun runsWhere(dayOfWeek: Int, wanted: (Boolean) -> Boolean): List<TimeWindow> {
        val runs = mutableListOf<TimeWindow>()
        var runStart: Int? = null
        for (minute in 0 until MINUTES_PER_DAY) {
            val match = wanted(isActiveAt(dayOfWeek, minute))
            if (match && runStart == null) {
                runStart = minute
            } else if (!match && runStart != null) {
                runs += TimeWindow(runStart, minute)
                runStart = null
            }
        }
        runStart?.let { runs += TimeWindow(it, MINUTES_PER_DAY) }
        return runs
    }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60

        /**
         * The clinic's real schedule: open 9:00–21:00, and busiest at opening, over lunch, at
         * evening changeover, and late evening.
         */
        val DEFAULT = ActiveSchedule(
            enabled = true,
            openMinute = 9 * 60,
            closeMinute = 21 * 60,
            openDays = setOf(1, 2, 3, 4, 5, 6),
            peakWindows = listOf(
                TimeWindow(8 * 60, 9 * 60 + 30),
                TimeWindow(12 * 60 + 45, 14 * 60 + 15),
                TimeWindow(17 * 60, 18 * 60 + 30),
                TimeWindow(20 * 60, 22 * 60),
            ),
        )

        /** Parses "08:00-09:30" lines, ignoring blanks and anything malformed. */
        fun parseWindows(text: String): List<TimeWindow> =
            text.lines().mapNotNull { parseWindow(it) }

        fun parseWindow(line: String): TimeWindow? {
            val parts = line.trim().split('-', '–', '—')
            if (parts.size != 2) return null
            val start = parseTime(parts[0]) ?: return null
            val end = parseTime(parts[1]) ?: return null
            if (start == end) return null
            return TimeWindow(start, end)
        }

        fun parseTime(text: String): Int? {
            val parts = text.trim().split(':')
            if (parts.size != 2) return null
            val hour = parts[0].trim().toIntOrNull() ?: return null
            val minute = parts[1].trim().toIntOrNull() ?: return null
            if (hour !in 0..24 || minute !in 0..59) return null
            return (hour * 60 + minute).coerceAtMost(MINUTES_PER_DAY)
        }

        fun formatWindows(windows: List<TimeWindow>): String =
            windows.joinToString("\n") { it.toString() }
    }
}
