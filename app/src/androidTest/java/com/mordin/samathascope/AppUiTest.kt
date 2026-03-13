package com.mordin.samathascope

import androidx.compose.ui.test.assertExists
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
    composeRule.onNodeWithText("Dashboard").assertExists()
    composeRule.onNodeWithText("Settings").assertExists()
    composeRule.onNodeWithText("Game").assertExists()
    composeRule.onNodeWithText("Learn").assertExists()
  }

  @Test
  fun learnTab_showsCalibrationAndGlossaryContent() {
    composeRule.onNodeWithText("Learn").performClick()
    composeRule.onNodeWithText("How calibration works").assertExists()
    composeRule.onNodeWithText("Meditation Proxy (MP)").assertExists()
  }

  @Test
  fun dashboard_showsRawEegAndMetricExplorer() {
    composeRule.onNodeWithText("Dashboard").performClick()
    composeRule.onNodeWithText("Raw EEG").assertExists()
    composeRule.onNodeWithText("Metric explorer").assertExists()
    composeRule.onNodeWithText("Feedback source: Meditation Proxy").assertExists()
  }

  @Test
  fun settingsTab_showsAudioControls() {
    composeRule.onNodeWithText("Settings").performClick()
    composeRule.onNodeWithText("Audio").assertExists()
    composeRule.onNodeWithText("Enable audio feedback").assertExists()
  }

  @Test
  fun gameTab_showsPickerAndMappings() {
    composeRule.onNodeWithText("Game").performClick()
    composeRule.onNodeWithText("Sky Tower").assertExists()
    composeRule.onNodeWithText("Game picker").assertExists()
    composeRule.onNodeWithText("Start Sky Tower").assertExists()
    composeRule.onNodeWithText("Sky Tower guide").assertExists()
  }

  @Test
  fun gameTab_switchesBetweenHybridAndPassiveHints() {
    composeRule.onNodeWithText("Game").performClick()
    composeRule.onNodeWithText("Sky Tower guide").assertExists()
    composeRule.onNodeWithText("Fire Keeper").performClick()
    composeRule.onNodeWithText("Start Fire Keeper").assertExists()
    composeRule.onNodeWithText("Fire Keeper guide").assertExists()
  }
}
