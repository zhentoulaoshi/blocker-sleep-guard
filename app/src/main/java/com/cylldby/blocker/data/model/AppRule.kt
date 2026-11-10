package com.cylldby.blocker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_rules")
data class AppRule(
    @PrimaryKey val packageName: String,
    val appName: String,
    val dailyLimitMinutes: Int,  // 0 = no quota limit
    val isActive: Boolean = true,
    // Schedule fields: block from startHour to endHour on specified days
    val scheduleStartHour: Int = -1,    // -1 = no schedule
    val scheduleEndHour: Int = -1,
    val scheduleDays: String = "",       // comma-separated day numbers 1-7 (Mon=1)
    val scheduleActive: Boolean = false
)
