package com.bravoscribe.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test: the app launches and lands on Login when there's no session.
 * Doesn't require the backend to be reachable — AuthViewModel's silent
 * refresh-on-launch (tryRestoreSession) fails gracefully to a logged-out
 * state on any network error, same as it does offline on a real device.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesToLoginScreenWhenLoggedOut() {
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodesWithText("Bravoscribe").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Bravoscribe").assertIsDisplayed()
        composeTestRule.onNodeWithText("Email").assertIsDisplayed()
        composeTestRule.onNodeWithText("Password").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sign In").assertIsDisplayed()
    }

    @Test
    fun signUpLinkNavigatesToRegisterScreen() {
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodesWithText("Sign up").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Sign up").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Sign Up").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Name").assertIsDisplayed()
    }
}
