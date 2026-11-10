package com.cylldby.blocker.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cylldby.blocker.data.db.BlockerDatabase
import com.cylldby.blocker.data.model.AppRule
import com.cylldby.blocker.data.repository.BlockerRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests repository behaviour as exercised by the ViewModel layer.
 * ViewModels themselves require an Application instance — testing the repository
 * directly gives equivalent coverage without the Application dependency.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class AppsViewModelTest {

    @get:Rule val instantTaskRule = InstantTaskExecutorRule()

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

    @Test
    fun `inserting rule makes it appear in flow`() = runTest {
        repo.upsertAppRule(AppRule("com.instagram", "Instagram", 45))
        val rules = repo.allAppRules.first()
        assertThat(rules.map { it.packageName }).contains("com.instagram")
    }

    @Test
    fun `toggling isActive flips the flag`() = runTest {
        val rule = AppRule("com.tiktok", "TikTok", 20, isActive = true)
        repo.upsertAppRule(rule)
        repo.updateAppRule(rule.copy(isActive = false))
        val updated = repo.allAppRules.first().first()
        assertThat(updated.isActive).isFalse()
    }

    @Test
    fun `deleting rule removes it from flow`() = runTest {
        val rule = AppRule("com.youtube", "YouTube", 60)
        repo.upsertAppRule(rule)
        repo.deleteAppRule(rule)
        assertThat(repo.allAppRules.first()).isEmpty()
    }

    @Test
    fun `rules are ordered by app name ascending`() = runTest {
        repo.upsertAppRule(AppRule("com.z", "Zoom", 0))
        repo.upsertAppRule(AppRule("com.a", "AnyDesk", 0))
        repo.upsertAppRule(AppRule("com.m", "Maps", 0))
        val names = repo.allAppRules.first().map { it.appName }
        assertThat(names).isInOrder()
    }
}
