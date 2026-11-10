package com.cylldby.blocker.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pure time-window math. No Android dependency so it can be unit-tested on the JVM.
 * A "night" window is allowed to cross midnight (e.g. 23:00 -> 07:00).
 */
object NightWindow {

    const val MINUTES_PER_DAY = 24 * 60

    /** True when [minuteOfDay] falls inside [startMinute]..[endMinute] (exclusive end). */
    fun isInWindow(minuteOfDay: Int, startMinute: Int, endMinute: Int): Boolean {
        val s = startMinute.coerceIn(0, MINUTES_PER_DAY - 1)
        val e = endMinute.coerceIn(0, MINUTES_PER_DAY - 1)
        if (s == e) return false // zero-length window is never active
        val m = ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return if (s < e) m in s until e else m >= s || m < e
    }

    /** Minutes left before the current night window ends; 0 when outside the window. */
    fun minutesUntilEnd(minuteOfDay: Int, startMinute: Int, endMinute: Int): Int {
        if (!isInWindow(minuteOfDay, startMinute, endMinute)) return 0
        val e = endMinute.coerceIn(0, MINUTES_PER_DAY - 1)
        val m = ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return (e - m + MINUTES_PER_DAY) % MINUTES_PER_DAY
    }

    /**
     * A "sleep night" is one session from noon to noon, not a calendar day.
     * 23:50 and 00:10 the next morning must share the same key, otherwise the
     * once-per-night grace period could be claimed twice in one night.
     * Before noon we attribute the timestamp to the previous calendar day.
     */
    fun nightSessionKey(timestampMillis: Long, hourOfDay: Int): String {
        val shifted = if (hourOfDay < 12) timestampMillis - 24L * 60 * 60 * 1000 else timestampMillis
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(shifted))
    }

    fun format(minuteOfDay: Int): String {
        val m = ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return "%02d:%02d".format(m / 60, m % 60)
    }
}
