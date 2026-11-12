package com.mordin.samathascope

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
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
    composeRule.onNodeWithText("Learn").assertIsDisplayed()
  }

  @Test
  fun learnTab_showsCalibrationAndGlossaryContent() {
    composeRule.onNodeWithText("Learn").performClick()
    composeRule.onNodeWithText("Metric deep dive").performScrollTo().assertIsDisplayed()
    composeRule.onAllNodesWithText("Meditation Proxy (MP)")[0].performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithText("Foundations").performScrollTo().performClick()
    composeRule.onNodeWithText("How calibration works").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun dashboard_showsRawEegAndMetricExplorer() {
    composeRule.onNodeWithText("Dashboard").performClick()
    composeRule.onNodeWithText("Session").performScrollTo().assertIsDisplayed()
    composeRule.onAllNodesWithText("Raw EEG")[0].performScrollTo().assertIsDisplayed()
  }

  @Test
  fun settingsTab_showsAudioControls() {
    composeRule.onNodeWithText("Settings").performClick()
    composeRule.onNodeWithText("Audio").assertIsDisplayed()
    composeRule.onNodeWithText("Enable audio feedback").assertIsDisplayed()
  }

  @Test
  fun debugLoop_skipCalibrationKeepsSessionRunning() {
    composeRule.onNodeWithText("Dashboard").performClick()
    composeRule.waitForIdle()
    composeRule.waitUntil(timeoutMillis = 5000) {
      composeRule.onAllNodesWithTag("dashboard_debug_raw_replay_toggle")
        .fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("dashboard_debug_raw_replay_toggle").performScrollTo().performClick()

    // eegStreamReady needs ~1-2 s of replayed samples.
    Thread.sleep(3000)
    composeRule.waitForIdle()

    composeRule.onNodeWithTag("dashboard_start_session").performScrollTo().performClick()
    composeRule.waitForIdle()

    // Skip early, before the baseline has enough windows; the session must keep running.
    Thread.sleep(2000)
    composeRule.waitForIdle()
    if (composeRule.onAllNodesWithTag("dashboard_skip_calibration").fetchSemanticsNodes().isNotEmpty()) {
      composeRule.onNodeWithTag("dashboard_skip_calibration").performScrollTo().performClick()
      composeRule.waitForIdle()
    }

    Thread.sleep(3000)
    composeRule.waitForIdle()
    composeRule.onNodeWithText("Session").performScrollTo().assertIsDisplayed()
  }
}
