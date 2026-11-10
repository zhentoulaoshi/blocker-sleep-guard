package com.cylldby.blocker.util

import com.cylldby.blocker.data.model.AppRule
import java.util.Calendar

/**
 * Pure schedule-checking logic — no Android dependencies, fully unit-testable.
 * Callers can inject a Calendar for deterministic testing.
 */
object ScheduleChecker {

    fun isScheduleBlocked(rule: AppRule, now: Calendar = Calendar.getInstance()): Boolean {
        if (!rule.scheduleActive) return false
        if (rule.scheduleStartHour < 0 || rule.scheduleEndHour < 0) return false

        val hour = now.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)
        // Map Calendar Sunday=1..Saturday=7 → Mon=1..Sun=7
        val day = if (dayOfWeek == Calendar.SUNDAY) 7 else dayOfWeek - 1

        val days = rule.scheduleDays.split(",").mapNotNull { it.trim().toIntOrNull() }
        val inDay = days.isEmpty() || day in days

        val inTime = if (rule.scheduleStartHour <= rule.scheduleEndHour)
            hour in rule.scheduleStartHour until rule.scheduleEndHour
        else  // overnight window e.g. 22:00–06:00
            hour >= rule.scheduleStartHour || hour < rule.scheduleEndHour

        return inDay && inTime
    }

    fun isQuotaExceeded(rule: AppRule, usedMinutes: Long): Boolean =
        rule.dailyLimitMinutes > 0 && usedMinutes >= rule.dailyLimitMinutes
}
