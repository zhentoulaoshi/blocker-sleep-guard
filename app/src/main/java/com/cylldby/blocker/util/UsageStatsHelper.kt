package com.cylldby.blocker.util

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

object UsageStatsHelper {

    fun getTodayUsageMinutes(context: Context, packageName: String): Long {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            cal.timeInMillis,
            System.currentTimeMillis()
        )
        val totalMs = stats.filter { it.packageName == packageName }
            .sumOf { it.totalTimeInForeground }
        return totalMs / 60_000L
    }
}
