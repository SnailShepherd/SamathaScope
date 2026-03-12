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
  fun gameTab_opensLanternScene() {
    composeRule.onNodeWithText("Game").performClick()
    composeRule.onNodeWithText("Lantern scene").assertExists()
    composeRule.onNodeWithText("Shared feedback source").assertExists()
  }
}
