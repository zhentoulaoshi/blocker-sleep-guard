package com.cylldby.blocker.data.db

import androidx.room.*
import com.cylldby.blocker.data.model.AppRule
import kotlinx.coroutines.flow.Flow

@Dao
interface AppRuleDao {
    @Query("SELECT * FROM app_rules ORDER BY appName ASC")
    fun getAllRules(): Flow<List<AppRule>>

    @Query("SELECT * FROM app_rules WHERE isActive = 1")
    suspend fun getActiveRules(): List<AppRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: AppRule)

    @Update
    suspend fun update(rule: AppRule)

    @Delete
    suspend fun delete(rule: AppRule)

    @Query("SELECT * FROM app_rules WHERE packageName = :packageName")
    suspend fun getRule(packageName: String): AppRule?
}
