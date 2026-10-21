package com.myrizz.assistant

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun launchesWithoutCrashing() {
        rule.waitForIdle()
        assertNotNull(rule.activity)
    }
}
