package com.mordin.samathascope

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainViewModelInstrumentedTest {

  @Test
  fun tabSelection_updatesUiState() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = MainViewModel(app)

    vm.selectTab(AppTab.GAME)

    val state = vm.ui.value
    assertEquals(AppTab.GAME, state.selectedTab)
  }

  @Test
  fun metricWindow_updateAppliesToMetricPlotsButKeepsRawWindow() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = MainViewModel(app)
    val initialRawWindow = vm.ui.value.plotSettings.getValue(PlotType.RAW).windowSeconds

    vm.setMetricWindowSeconds(180)

    val state = vm.ui.value
    assertEquals(180, state.plotSettings.getValue(PlotType.MEDITATION_PROXY).windowSeconds)
    assertEquals(180, state.plotSettings.getValue(PlotType.CONTROL).windowSeconds)
    assertEquals(initialRawWindow, state.plotSettings.getValue(PlotType.RAW).windowSeconds)
  }

  @Test
  fun audioToggle_updatesSharedAudioState() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = MainViewModel(app)

    vm.setAudioEnabled(false)

    val state = vm.ui.value
    assertFalse(state.audioEnabled)
    assertFalse(state.audioRunning)
  }

  @Test
  fun selectGame_updatesSelectedSceneAndHud() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = MainViewModel(app)

    vm.selectGame(GameId.SCRIPTORIUM)

    val state = vm.ui.value
    assertEquals(GameId.SCRIPTORIUM, state.selectedGameId)
    assertFalse(state.gameRunning)
    assertEquals("Scriptorium", state.sceneHudState.title)
    assertTrue(state.sceneHudState.inputHint.contains("Start a headset session"))
  }
}
