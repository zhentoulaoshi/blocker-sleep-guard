package com.cylldby.blocker.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cylldby.blocker.MainActivity
import com.cylldby.blocker.R
import com.cylldby.blocker.util.QuickBlockManager
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickBlockTabTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @After
    fun cleanup() {
        activityRule.scenario.onActivity { activity ->
            QuickBlockManager.stop(activity)
        }
    }

    private fun navigateToFocus() {
        onView(withId(R.id.quickBlockFragment)).perform(click())
    }

    @Test
    fun focusTabShowsQuickBlockTitle() {
        navigateToFocus()
        onView(withText("Quick Block")).check(matches(isDisplayed()))
    }

    @Test
    fun startBlockingButtonVisible() {
        navigateToFocus()
        onView(withId(R.id.btnStartBlock)).check(matches(isDisplayed()))
    }

    @Test
    fun durationChipsAreVisible() {
        navigateToFocus()
        onView(withId(R.id.chip15m)).check(matches(isDisplayed()))
        onView(withId(R.id.chip30m)).check(matches(isDisplayed()))
        onView(withId(R.id.chip1h)).check(matches(isDisplayed()))
        onView(withId(R.id.chip2h)).check(matches(isDisplayed()))
    }

    @Test
    fun selectingChipUpdatesSelection() {
        navigateToFocus()
        onView(withId(R.id.chip15m)).perform(click())
        onView(withId(R.id.chip15m)).check(matches(isChecked()))
    }

    @Test
    fun startBlockingShowsActiveState() {
        navigateToFocus()
        onView(withId(R.id.chip15m)).perform(click())
        onView(withId(R.id.btnStartBlock)).perform(click())
        onView(withId(R.id.layoutActive)).check(matches(isDisplayed()))
        onView(withId(R.id.btnEmergencyStop)).check(matches(isDisplayed()))
    }
}
