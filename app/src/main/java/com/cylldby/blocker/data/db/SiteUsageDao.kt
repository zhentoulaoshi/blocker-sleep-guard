package com.cylldby.blocker.data.db

import androidx.room.*
import com.cylldby.blocker.data.model.SiteUsage

@Dao
interface SiteUsageDao {
    @Query("SELECT * FROM site_usage WHERE domain = :domain AND date = :date LIMIT 1")
    suspend fun getUsage(domain: String, date: String): SiteUsage?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(usage: SiteUsage)

    @Query("DELETE FROM site_usage WHERE date < :date")
    suspend fun deleteOlderThan(date: String)
}
