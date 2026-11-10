package com.cylldby.blocker.data.model

import androidx.room.Entity

@Entity(tableName = "site_usage", primaryKeys = ["domain", "date"])
data class SiteUsage(
    val domain: String,
    val date: String,
    val usedMinutes: Int = 0
)
