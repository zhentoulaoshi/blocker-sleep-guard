package com.cylldby.blocker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "site_rules")
data class SiteRule(
    @PrimaryKey val domain: String,
    val dailyLimitMinutes: Int = 0   // 0 = always block
)
