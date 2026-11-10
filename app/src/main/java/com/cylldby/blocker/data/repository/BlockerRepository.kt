package com.cylldby.blocker.data.repository

import com.cylldby.blocker.data.db.AppRuleDao
import com.cylldby.blocker.data.db.SiteRuleDao
import com.cylldby.blocker.data.db.SiteUsageDao
import com.cylldby.blocker.data.model.AppRule
import com.cylldby.blocker.data.model.SiteRule
import com.cylldby.blocker.data.model.SiteUsage
import kotlinx.coroutines.flow.Flow

class BlockerRepository(
    private val appRuleDao: AppRuleDao,
    private val siteRuleDao: SiteRuleDao,
    private val siteUsageDao: SiteUsageDao
) {
    val allAppRules: Flow<List<AppRule>> = appRuleDao.getAllRules()
    val allSiteRules: Flow<List<SiteRule>> = siteRuleDao.getAllSites()

    suspend fun upsertAppRule(rule: AppRule) = appRuleDao.insert(rule)
    suspend fun updateAppRule(rule: AppRule) = appRuleDao.update(rule)
    suspend fun deleteAppRule(rule: AppRule) = appRuleDao.delete(rule)
    suspend fun getAppRule(pkg: String) = appRuleDao.getRule(pkg)
    suspend fun getActiveRules() = appRuleDao.getActiveRules()

    suspend fun addSiteRule(rule: SiteRule) = siteRuleDao.insert(rule)
    suspend fun deleteSiteRule(rule: SiteRule) = siteRuleDao.delete(rule)
    suspend fun getAllDomains() = siteRuleDao.getAllDomains()
    suspend fun updateSiteRule(rule: SiteRule) = siteRuleDao.insert(rule)

    suspend fun getSiteUsage(domain: String, date: String): SiteUsage? =
        siteUsageDao.getUsage(domain, date)

    suspend fun upsertSiteUsage(usage: SiteUsage) = siteUsageDao.upsert(usage)
}
