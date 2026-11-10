package com.cylldby.blocker.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class NightWindowTest {

    private fun ts(value: String): Long =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(value)!!.time

    private val start = 23 * 60   // 23:00
    private val end = 7 * 60      // 07:00

    @Test
    fun overnightWindowCoversLateNightAndEarlyMorning() {
        assertThat(NightWindow.isInWindow(23 * 60, start, end)).isTrue()
        assertThat(NightWindow.isInWindow(0, start, end)).isTrue()
        assertThat(NightWindow.isInWindow(3 * 60 + 30, start, end)).isTrue()
        assertThat(NightWindow.isInWindow(6 * 60 + 59, start, end)).isTrue()
    }

    @Test
    fun overnightWindowExcludesDaytime() {
        assertThat(NightWindow.isInWindow(7 * 60, start, end)).isFalse()
        assertThat(NightWindow.isInWindow(12 * 60, start, end)).isFalse()
        assertThat(NightWindow.isInWindow(22 * 60 + 59, start, end)).isFalse()
    }

    @Test
    fun sameDayWindowWorksToo() {
        assertThat(NightWindow.isInWindow(10 * 60, 9 * 60, 12 * 60)).isTrue()
        assertThat(NightWindow.isInWindow(12 * 60, 9 * 60, 12 * 60)).isFalse()
    }

    @Test
    fun zeroLengthWindowIsNeverActive() {
        assertThat(NightWindow.isInWindow(12 * 60, 9 * 60, 9 * 60)).isFalse()
    }

    @Test
    fun minutesUntilEndHandlesMidnight() {
        assertThat(NightWindow.minutesUntilEnd(23 * 60, start, end)).isEqualTo(8 * 60)
        assertThat(NightWindow.minutesUntilEnd(6 * 60, start, end)).isEqualTo(60)
        assertThat(NightWindow.minutesUntilEnd(12 * 60, start, end)).isEqualTo(0)
    }

    @Test
    fun formatPadsHoursAndMinutes() {
        assertThat(NightWindow.format(23 * 60)).isEqualTo("23:00")
        assertThat(NightWindow.format(7 * 60 + 5)).isEqualTo("07:05")
    }

    @Test
    fun oneNightSpansMidnightForTheOncePerNightGrace() {
        val lateNight = ts("2026-11-10 23:50")
        val nextMorning = ts("2026-11-11 00:10")
        assertThat(NightWindow.nightSessionKey(lateNight, 23))
            .isEqualTo(NightWindow.nightSessionKey(nextMorning, 0))
        // The following evening is a new session.
        assertThat(NightWindow.nightSessionKey(ts("2026-11-11 23:10"), 23))
            .isNotEqualTo(NightWindow.nightSessionKey(lateNight, 23))
    }
}
