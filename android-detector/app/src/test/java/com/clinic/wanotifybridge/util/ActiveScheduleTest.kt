package com.clinic.wanotifybridge.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveScheduleTest {

    private val schedule = ActiveSchedule.DEFAULT

    private fun at(hour: Int, minute: Int = 0) = hour * 60 + minute

    @Test
    fun `the clinic's real schedule is active exactly when nobody is free`() {
        // Overnight and early morning: line unstaffed.
        assertTrue(schedule.isActiveAt(at(2)))
        assertTrue(schedule.isActiveAt(at(8, 30)))

        // 08:00-09:30 peak straddles the 09:00 opening; still active up to 09:29.
        assertTrue(schedule.isActiveAt(at(9, 29)))

        // First calm stretch: team can answer WhatsApp themselves.
        assertFalse(schedule.isActiveAt(at(9, 30)))
        assertFalse(schedule.isActiveAt(at(11)))
        assertFalse(schedule.isActiveAt(at(12, 44)))

        // Lunch peak.
        assertTrue(schedule.isActiveAt(at(12, 45)))
        assertTrue(schedule.isActiveAt(at(14, 14)))
        assertFalse(schedule.isActiveAt(at(14, 15)))

        // Afternoon calm, then the evening changeover peak.
        assertFalse(schedule.isActiveAt(at(16, 59)))
        assertTrue(schedule.isActiveAt(at(17)))
        assertTrue(schedule.isActiveAt(at(18, 29)))

        // Early-evening calm, then the late peak.
        assertFalse(schedule.isActiveAt(at(18, 30)))
        assertFalse(schedule.isActiveAt(at(19, 59)))
        assertTrue(schedule.isActiveAt(at(20)))

        // After 21:00 close everything is off-hours, so active regardless of peaks.
        assertTrue(schedule.isActiveAt(at(21, 30)))
        assertTrue(schedule.isActiveAt(at(23, 59)))
    }

    @Test
    fun `disabling the schedule forwards everything`() {
        val always = schedule.copy(enabled = false)
        assertTrue(always.isActiveAt(at(11)))
        assertTrue(always.isActiveAt(at(15, 30)))
    }

    @Test
    fun `derived windows match the operator's intent`() {
        assertEquals(
            listOf("00:00-09:30", "12:45-14:15", "17:00-18:30", "20:00-24:00"),
            schedule.activeWindows().map(TimeWindow::toString),
        )
        assertEquals(
            listOf("09:30-12:45", "14:15-17:00", "18:30-20:00"),
            schedule.quietWindows().map(TimeWindow::toString),
        )
    }

    @Test
    fun `active and quiet windows partition the day`() {
        val covered = schedule.activeWindows().sumOf { it.endMinute - it.startMinute } +
            schedule.quietWindows().sumOf { it.endMinute - it.startMinute }
        assertEquals(ActiveSchedule.MINUTES_PER_DAY, covered)
    }

    @Test
    fun `no peak windows means the whole open day is quiet`() {
        val noPeaks = schedule.copy(peakWindows = emptyList())
        assertFalse(noPeaks.isActiveAt(at(12)))
        assertTrue(noPeaks.isActiveAt(at(23)))
        assertEquals(listOf("09:00-21:00"), noPeaks.quietWindows().map(TimeWindow::toString))
    }

    @Test
    fun `a window wrapping midnight is handled`() {
        val lateNight = TimeWindow(23 * 60, 1 * 60)
        assertTrue(lateNight.contains(at(23, 30)))
        assertTrue(lateNight.contains(at(0, 30)))
        assertFalse(lateNight.contains(at(12)))
    }

    @Test
    fun `window text parses and round-trips`() {
        val parsed = ActiveSchedule.parseWindows(
            """
            08:00-09:30
            12:45-14:15
            """.trimIndent(),
        )
        assertEquals(2, parsed.size)
        assertEquals(at(8), parsed[0].startMinute)
        assertEquals(at(14, 15), parsed[1].endMinute)
        assertEquals("08:00-09:30\n12:45-14:15", ActiveSchedule.formatWindows(parsed))
    }

    @Test
    fun `an en dash separator is accepted`() {
        assertEquals(TimeWindow(at(17), at(18, 30)), ActiveSchedule.parseWindow("17:00–18:30"))
    }

    @Test
    fun `malformed window lines are dropped rather than crashing`() {
        assertNull(ActiveSchedule.parseWindow("nonsense"))
        assertNull(ActiveSchedule.parseWindow("25:00-26:00"))
        assertNull(ActiveSchedule.parseWindow("9am to 5pm"))
        // A zero-length window would silently never match; treat it as malformed.
        assertNull(ActiveSchedule.parseWindow("10:00-10:00"))

        val parsed = ActiveSchedule.parseWindows("08:00-09:30\nrubbish\n\n12:45-14:15")
        assertEquals(2, parsed.size)
    }

    @Test
    fun `a typo that drops every peak fails safe by staying quiet, not by spamming`() {
        // Worth pinning down: a bad peak list means the bridge goes quiet during opening
        // hours rather than replying over staff all day. Off-hours forwarding still works.
        val broken = schedule.copy(peakWindows = ActiveSchedule.parseWindows("garbage"))
        assertFalse(broken.isActiveAt(at(10)))
        assertTrue(broken.isActiveAt(at(3)))
    }
}
