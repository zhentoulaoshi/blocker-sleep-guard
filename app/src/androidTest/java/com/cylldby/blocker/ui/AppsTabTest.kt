package com.cylldby.blocker.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
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
class AppsTabTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun fabIsVisible() {
        onView(withId(R.id.fab_add_app)).check(matches(isDisplayed()))
    }

    @Test
    fun fabClickOpensDialog() {
        onView(withId(R.id.fab_add_app)).perform(click())
        onView(withText("Select app to block")).check(matches(isDisplayed()))
    }
}
