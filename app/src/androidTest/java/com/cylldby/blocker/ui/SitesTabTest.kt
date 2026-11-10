package com.cylldby.blocker.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cylldby.blocker.MainActivity
import com.cylldby.blocker.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SitesTabTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private fun navigateToSites() {
        onView(withId(R.id.sitesFragment)).perform(click())
    }

    @Test
    fun fabIsVisible() {
        navigateToSites()
        onView(withId(R.id.fab_add_site)).check(matches(isDisplayed()))
    }

    @Test
    fun fabClickOpensDomainDialog() {
        navigateToSites()
        onView(withId(R.id.fab_add_site)).perform(click())
        onView(withText("Block a website")).check(matches(isDisplayed()))
    }

    @Test
    fun addSiteShowsLimitDialog() {
        navigateToSites()
        onView(withId(R.id.fab_add_site)).perform(click())
        onView(withHint("e.g. reddit.com")).perform(typeText("test.com"), closeSoftKeyboard())
        onView(withText("Next")).perform(click())
        onView(withText("Daily limit in minutes")).check(matches(isDisplayed()))
    }
}
