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
    composeRule.onNodeWithText("Signals").assertExists()
    composeRule.onNodeWithText("Game").assertExists()
    composeRule.onNodeWithText("Learn").assertExists()
  }

  @Test
  fun learnTab_showsSamathaCard() {
    composeRule.onNodeWithText("Learn").performClick()
    composeRule.onNodeWithText("Samatha in Theravada context").assertExists()
  }

  @Test
  fun dashboardLivePlotSelection_staysSyncedAcrossTabs() {
    composeRule.onNodeWithText("Dashboard").performClick()
    composeRule.onNodeWithText("Relaxed Alertness Index").performClick()
    composeRule.onNodeWithText("Showing: Relaxed Alertness Index").assertExists()

    composeRule.onNodeWithText("Signals").performClick()
    composeRule.onNodeWithText("Dashboard").performClick()

    composeRule.onNodeWithText("Showing: Relaxed Alertness Index").assertExists()
  }

  @Test
  fun gameTab_opensLevitationScene() {
    composeRule.onNodeWithText("Game").performClick()
    composeRule.onNodeWithText("Levitation scene").assertExists()
  }
}
