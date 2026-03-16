package com.mordin.samathascope

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test

class AppUiTest {

  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun tabLabels_areVisible() {
    composeRule.onNodeWithText("Dashboard").assertIsDisplayed()
    composeRule.onNodeWithText("Settings").assertIsDisplayed()
    composeRule.onNodeWithText("Game").assertIsDisplayed()
    composeRule.onNodeWithText("Learn").assertIsDisplayed()
  }

  @Test
  fun learnTab_showsCalibrationAndGlossaryContent() {
    composeRule.onNodeWithText("Learn").performClick()
    composeRule.onNodeWithText("Metric deep dive").assertIsDisplayed()
    composeRule.onAllNodesWithText("Meditation Proxy (MP)")[0].assertIsDisplayed()
    composeRule.onNodeWithText("Foundations").performClick()
    composeRule.onNodeWithText("How calibration works").assertIsDisplayed()
  }

  @Test
  fun dashboard_showsRawEegAndMetricExplorer() {
    composeRule.onNodeWithText("Dashboard").performClick()
    composeRule.onNodeWithText("Session").assertIsDisplayed()
    composeRule.onNodeWithText("Raw EEG").assertIsDisplayed()
  }

  @Test
  fun settingsTab_showsAudioControls() {
    composeRule.onNodeWithText("Settings").performClick()
    composeRule.onNodeWithText("Audio").assertIsDisplayed()
    composeRule.onNodeWithText("Enable audio feedback").assertIsDisplayed()
  }

  @Test
  fun gameTab_showsPickerAndMappings() {
    composeRule.onNodeWithText("Game").performClick()
    composeRule.onAllNodesWithText("Sky Tower", substring = false)[0].assertIsDisplayed()
    composeRule.onNodeWithText("Game picker").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("Start Sky Tower").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("Sky Tower guide").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun gameTab_switchesBetweenHybridAndPassiveHints() {
    composeRule.onNodeWithText("Game").performClick()
    composeRule.onNodeWithText("Sky Tower guide").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("Fire Keeper").performScrollTo().performClick()
    composeRule.onNodeWithText("Start Fire Keeper").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("Fire Keeper guide").performScrollTo().assertIsDisplayed()
  }
}
