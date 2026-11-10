package com.cylldby.blocker.data.db

import androidx.room.*
import com.cylldby.blocker.data.model.SiteRule
import kotlinx.coroutines.flow.Flow

@Dao
interface SiteRuleDao {
    @Query("SELECT * FROM site_rules ORDER BY domain ASC")
    fun getAllSites(): Flow<List<SiteRule>>

    @Query("SELECT domain FROM site_rules")
    suspend fun getAllDomains(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: SiteRule)

    @Delete
    suspend fun delete(rule: SiteRule)
}
