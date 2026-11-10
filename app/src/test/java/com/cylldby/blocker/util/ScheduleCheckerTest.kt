package com.cylldby.blocker.util

import com.cylldby.blocker.data.model.AppRule
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar

class ScheduleCheckerTest {

    private fun rule(
        startHour: Int = 22, endHour: Int = 6,
        days: String = "", active: Boolean = true
    ) = AppRule(
        packageName = "com.example.app",
        appName = "Test App",
        dailyLimitMinutes = 0,
        scheduleStartHour = startHour,
        scheduleEndHour = endHour,
        scheduleDays = days,
        scheduleActive = active
    )

    private fun calAt(hour: Int, dayOfWeek: Int = Calendar.MONDAY): Calendar =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
            set(Calendar.MINUTE, 0)
        }

    // --- isScheduleBlocked ---

    @Test
    fun `inactive schedule never blocks`() {
        val r = rule(startHour = 0, endHour = 23, active = false)
        assertThat(ScheduleChecker.isScheduleBlocked(r, calAt(12))).isFalse()
    }

    @Test
    fun `no schedule configured never blocks`() {
        val r = rule(startHour = -1, endHour = -1, active = true)
        assertThat(ScheduleChecker.isScheduleBlocked(r, calAt(12))).isFalse()
    }

    @Test
    fun `hour inside daytime window is blocked`() {
        // Block 09:00–17:00
        val r = rule(startHour = 9, endHour = 17)
        assertThat(ScheduleChecker.isScheduleBlocked(r, calAt(10))).isTrue()
        assertThat(ScheduleChecker.isScheduleBlocked(r, calAt(16))).isTrue()
    }

    @Test
    fun `hour at start of window is blocked`() {
        val r = rule(startHour = 9, endHour = 17)
        assertThat(ScheduleChecker.isScheduleBlocked(r, calAt(9))).isTrue()
    }

    @Test
    fun `hour at end of window is not blocked (exclusive end)`() {
        val r = rule(startHour = 9, endHour = 17)
        assertThat(ScheduleChecker.isScheduleBlocked(r, calAt(17))).isFalse()
    }

    @Test
    fun `hour outside daytime window is not blocked`() {
        val r = rule(startHour = 9, endHour = 17)
        assertThat(ScheduleChecker.isScheduleBlocked(r, calAt(8))).isFalse()
        assertThat(ScheduleChecker.isScheduleBlocked(r, calAt(20))).isFalse()
    }

    @Test
    fun `overnight window blocks after start hour`() {
        // Block 22:00–06:00 (overnight)
        val r = rule(startHour = 22, endHour = 6)
        assertThat(ScheduleChecker.isScheduleBlocked(r, calAt(23))).isTrue()
        assertThat(ScheduleChecker.isScheduleBlocked(r, calAt(22))).isTrue()
    }

    @Test
    fun `overnight window blocks before end hour`() {
        val r = rule(startHour = 22, endHour = 6)
        assertThat(ScheduleChecker.isScheduleBlocked(r, calAt(3))).isTrue()
        assertThat(ScheduleChecker.isScheduleBlocked(r, calAt(5))).isTrue()
    }

    @Test
    fun `overnight window does not block midday`() {
        val r = rule(startHour = 22, endHour = 6)
        assertThat(ScheduleChecker.isScheduleBlocked(r, calAt(12))).isFalse()
        assertThat(ScheduleChecker.isScheduleBlocked(r, calAt(21))).isFalse()
    }

    @Test
    fun `specific days schedule blocks on matching day`() {
        // Block Mon=1 and Fri=5 only
        val r = rule(startHour = 9, endHour = 17, days = "1,5")
        // Monday (Calendar.MONDAY = 2 in Calendar, maps to day=1)
        assertThat(ScheduleChecker.isScheduleBlocked(r, calAt(10, Calendar.MONDAY))).isTrue()
    }

    @Test
    fun `specific days schedule does not block on non-matching day`() {
        val r = rule(startHour = 9, endHour = 17, days = "1,5")
        // Wednesday (Calendar.WEDNESDAY = 4, maps to day=3)
        assertThat(ScheduleChecker.isScheduleBlocked(r, calAt(10, Calendar.WEDNESDAY))).isFalse()
    }

    @Test
    fun `Sunday maps to day 7`() {
        // Block on Sunday (day=7) only
        val r = rule(startHour = 9, endHour = 17, days = "7")
        assertThat(ScheduleChecker.isScheduleBlocked(r, calAt(10, Calendar.SUNDAY))).isTrue()
        assertThat(ScheduleChecker.isScheduleBlocked(r, calAt(10, Calendar.MONDAY))).isFalse()
    }

    // --- isQuotaExceeded ---

    @Test
    fun `quota not exceeded when used is below limit`() {
        val r = rule().copy(dailyLimitMinutes = 30)
        assertThat(ScheduleChecker.isQuotaExceeded(r, 29)).isFalse()
    }

    @Test
    fun `quota exceeded when used equals limit`() {
        val r = rule().copy(dailyLimitMinutes = 30)
        assertThat(ScheduleChecker.isQuotaExceeded(r, 30)).isTrue()
    }

    @Test
    fun `quota exceeded when used is above limit`() {
        val r = rule().copy(dailyLimitMinutes = 30)
        assertThat(ScheduleChecker.isQuotaExceeded(r, 60)).isTrue()
    }

    @Test
    fun `zero limit means no quota enforcement`() {
        val r = rule().copy(dailyLimitMinutes = 0)
        assertThat(ScheduleChecker.isQuotaExceeded(r, 999)).isFalse()
    }
}
