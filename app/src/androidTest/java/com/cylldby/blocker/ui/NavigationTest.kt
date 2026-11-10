package com.cylldby.blocker.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cylldby.blocker.MainActivity
import com.cylldby.blocker.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun navigateToSites() {
        onView(withId(R.id.sitesFragment)).perform(click())
    }

    @Test
    fun navigateToSchedule() {
        onView(withId(R.id.scheduleFragment)).perform(click())
    }

    @Test
    fun navigateToFocus() {
        onView(withId(R.id.quickBlockFragment)).perform(click())
    }

    @Test
    fun navigateBackToApps() {
        onView(withId(R.id.sitesFragment)).perform(click())
        onView(withId(R.id.appsFragment)).perform(click())
    }
}
