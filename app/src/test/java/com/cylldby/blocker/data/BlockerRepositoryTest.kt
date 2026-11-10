package com.cylldby.blocker.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cylldby.blocker.data.db.BlockerDatabase
import com.cylldby.blocker.data.model.AppRule
import com.cylldby.blocker.data.model.SiteRule
import com.cylldby.blocker.data.repository.BlockerRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BlockerRepositoryTest {

    private lateinit var db: BlockerDatabase
    private lateinit var repo: BlockerRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BlockerDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = BlockerRepository(db.appRuleDao(), db.siteRuleDao(), db.siteUsageDao())
    }

    @After
    fun teardown() = db.close()

    // --- AppRule CRUD ---

    @Test
    fun `insert app rule and retrieve it`() = runTest {
        val rule = AppRule(packageName = "com.example", appName = "Example", dailyLimitMinutes = 30)
        repo.upsertAppRule(rule)
        val rules = repo.allAppRules.first()
        assertThat(rules).hasSize(1)
        assertThat(rules[0].packageName).isEqualTo("com.example")
        assertThat(rules[0].dailyLimitMinutes).isEqualTo(30)
    }

    @Test
    fun `upsert updates existing rule`() = runTest {
        val rule = AppRule(packageName = "com.example", appName = "Example", dailyLimitMinutes = 30)
        repo.upsertAppRule(rule)
        repo.upsertAppRule(rule.copy(dailyLimitMinutes = 60))
        val rules = repo.allAppRules.first()
        assertThat(rules).hasSize(1)
        assertThat(rules[0].dailyLimitMinutes).isEqualTo(60)
    }

    @Test
    fun `delete removes app rule`() = runTest {
        val rule = AppRule(packageName = "com.example", appName = "Example", dailyLimitMinutes = 0)
        repo.upsertAppRule(rule)
        repo.deleteAppRule(rule)
        assertThat(repo.allAppRules.first()).isEmpty()
    }

    @Test
    fun `getActiveRules returns only active rules`() = runTest {
        repo.upsertAppRule(AppRule("com.a", "A", 30, isActive = true))
        repo.upsertAppRule(AppRule("com.b", "B", 30, isActive = false))
        val active = repo.getActiveRules()
        assertThat(active).hasSize(1)
        assertThat(active[0].packageName).isEqualTo("com.a")
    }

    @Test
    fun `update toggles isActive`() = runTest {
        val rule = AppRule(packageName = "com.example", appName = "Example", dailyLimitMinutes = 0)
        repo.upsertAppRule(rule)
        repo.updateAppRule(rule.copy(isActive = false))
        assertThat(repo.getActiveRules()).isEmpty()
    }

    // --- SiteRule CRUD ---

    @Test
    fun `add site rule and retrieve it`() = runTest {
        repo.addSiteRule(SiteRule("reddit.com"))
        val sites = repo.allSiteRules.first()
        assertThat(sites).hasSize(1)
        assertThat(sites[0].domain).isEqualTo("reddit.com")
    }

    @Test
    fun `delete site rule removes it`() = runTest {
        val site = SiteRule("reddit.com")
        repo.addSiteRule(site)
        repo.deleteSiteRule(site)
        assertThat(repo.allSiteRules.first()).isEmpty()
    }

    @Test
    fun `getAllDomains returns all domain strings`() = runTest {
        repo.addSiteRule(SiteRule("reddit.com"))
        repo.addSiteRule(SiteRule("twitter.com"))
        val domains = repo.getAllDomains()
        assertThat(domains).containsExactly("reddit.com", "twitter.com")
    }

    @Test
    fun `duplicate site rule is silently replaced`() = runTest {
        repo.addSiteRule(SiteRule("reddit.com"))
        repo.addSiteRule(SiteRule("reddit.com"))
        assertThat(repo.allSiteRules.first()).hasSize(1)
    }
}
