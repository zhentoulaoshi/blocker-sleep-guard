package com.cylldby.blocker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cylldby.blocker.data.model.AppRule
import com.cylldby.blocker.data.model.SiteRule
import com.cylldby.blocker.data.model.SiteUsage

@Database(entities = [AppRule::class, SiteRule::class, SiteUsage::class], version = 2, exportSchema = false)
abstract class BlockerDatabase : RoomDatabase() {
    abstract fun appRuleDao(): AppRuleDao
    abstract fun siteRuleDao(): SiteRuleDao
    abstract fun siteUsageDao(): SiteUsageDao

    companion object {
        @Volatile private var INSTANCE: BlockerDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE site_rules ADD COLUMN dailyLimitMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS site_usage (domain TEXT NOT NULL, date TEXT NOT NULL, usedMinutes INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(domain, date))")
            }
        }

        fun getInstance(context: Context): BlockerDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BlockerDatabase::class.java,
                    "blocker_db"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}
