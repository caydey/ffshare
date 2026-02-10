package com.caydey.ffshare

import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for HandleMediaActivity.
 * Tests the encoding UI and user interactions.
 */
@RunWith(AndroidJUnit4::class)
class HandleMediaActivityTest {

    @Test
    fun activity_displaysCorrectViews() {
        // Create intent without media to test basic layout
        val intent = Intent(ApplicationProvider.getApplicationContext(), HandleMediaActivity::class.java)

        ActivityScenario.launch<HandleMediaActivity>(intent).use { scenario ->
            // Verify cancel button exists
            onView(withId(R.id.btnCancel))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun activity_showsCancelButton() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), HandleMediaActivity::class.java)

        ActivityScenario.launch<HandleMediaActivity>(intent).use { scenario ->
            onView(withId(R.id.btnCancel))
                .check(matches(withText(R.string.cancel_ffmpeg)))
        }
    }

    @Test
    fun activity_hasProgressViews() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), HandleMediaActivity::class.java)

        ActivityScenario.launch<HandleMediaActivity>(intent).use { scenario ->
            // Verify progress-related views exist
            onView(withId(R.id.txtInputFile)).check(matches(isDisplayed()))
            onView(withId(R.id.txtOutputFileSize)).check(matches(isDisplayed()))
            onView(withId(R.id.txtProcessedPercent)).check(matches(isDisplayed()))
        }
    }
}
