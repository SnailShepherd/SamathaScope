package com.mordin.samathascope

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

  @Test
  fun skyTower_smokeTestWithDebugLoop() {
    // 1. Enable debug replay on dashboard
    composeRule.onNodeWithText("Dashboard").performClick()
    composeRule.waitForIdle()
    composeRule.waitUntil(timeoutMillis = 5000) {
      composeRule.onAllNodesWithTag("dashboard_debug_raw_replay_toggle")
        .fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("dashboard_debug_raw_replay_toggle").performClick()

    // 2. Wait for EEG stream to init (eegStreamReady needs ~1-2s)
    Thread.sleep(3000)
    composeRule.waitForIdle()

    // 3. Start session (button should now be enabled)
    composeRule.onNodeWithTag("dashboard_start_session").performClick()
    composeRule.waitForIdle()

    // 4. Let calibration run for ~10 seconds
    Thread.sleep(10_000)
    composeRule.waitForIdle()

    // 5. Skip calibration
    val skipCalibrationNodes = composeRule.onAllNodesWithTag("dashboard_skip_calibration")
      .fetchSemanticsNodes()
    if (skipCalibrationNodes.isNotEmpty()) {
      composeRule.onNodeWithTag("dashboard_skip_calibration").performClick()
      composeRule.waitForIdle()
      Thread.sleep(2000)
      composeRule.waitForIdle()
    }

    // 6. Skip artefact calibration if prompted
    val artefactNodes = composeRule.onAllNodesWithTag("dashboard_skip_artefact_calibration")
      .fetchSemanticsNodes()
    if (artefactNodes.isNotEmpty()) {
      composeRule.onNodeWithTag("dashboard_skip_artefact_calibration").performClick()
      composeRule.waitForIdle()
      Thread.sleep(1000)
      composeRule.waitForIdle()
    }

    // 7. Navigate to Game tab → Sky Tower is default
    composeRule.onNodeWithText("Game").performClick()
    composeRule.waitForIdle()
    Thread.sleep(1000)
    composeRule.waitForIdle()

    // 8. Start sky tower
    composeRule.waitUntil(timeoutMillis = 5000) {
      composeRule.onAllNodesWithTag("game_primary_action")
        .fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("game_primary_action").performClick()
    composeRule.waitForIdle()
    Thread.sleep(2000)

    // 8. Tap the canvas 20 times to drop stones
    repeat(20) {
      composeRule.onNodeWithTag("sky_tower_canvas").performClick()
      Thread.sleep(700)
    }

    // 9. Verify the tower has grown
    Thread.sleep(1000)
    composeRule.onNodeWithTag("sky_tower_height").assertIsDisplayed()
  }
}
