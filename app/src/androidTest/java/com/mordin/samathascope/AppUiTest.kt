package com.mordin.samathascope

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    composeRule.onNodeWithText("How calibration works").assertIsDisplayed()
    composeRule.onNodeWithText("Meditation Proxy (MP)").assertIsDisplayed()
  }

  @Test
  fun dashboard_showsRawEegAndMetricExplorer() {
    composeRule.onNodeWithText("Dashboard").performClick()
    composeRule.onNodeWithText("Raw EEG").assertIsDisplayed()
    composeRule.onNodeWithText("Metric explorer").assertIsDisplayed()
    composeRule.onNodeWithText("Session feedback source").assertIsDisplayed()
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
    composeRule.onNodeWithText("Sky Tower").assertIsDisplayed()
    composeRule.onNodeWithText("Game picker").assertIsDisplayed()
    composeRule.onNodeWithText("Start Sky Tower").assertIsDisplayed()
    composeRule.onNodeWithText("Sky Tower guide").assertIsDisplayed()
  }

  @Test
  fun gameTab_switchesBetweenHybridAndPassiveHints() {
    composeRule.onNodeWithText("Game").performClick()
    composeRule.onNodeWithText("Sky Tower guide").assertIsDisplayed()
    composeRule.onNodeWithText("Fire Keeper").performClick()
    composeRule.onNodeWithText("Start Fire Keeper").assertIsDisplayed()
    composeRule.onNodeWithText("Fire Keeper guide").assertIsDisplayed()
  }
}
