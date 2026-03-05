package com.mordin.samathascope

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainViewModelInstrumentedTest {

  @Test
  fun tabSelectionAndSettingsPanel_updateUiState() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = MainViewModel(app)

    vm.selectTab(AppTab.GAME)
    vm.setSettingsPanelVisible(true)

    val state = vm.ui.value
    assertEquals(AppTab.GAME, state.selectedTab)
    assertTrue(state.settingsPanelVisible)
  }

  @Test
  fun resetPlotSettings_restoresDefaults() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = MainViewModel(app)

    vm.setPlotWindowSeconds(PlotType.RAW, 10)
    vm.resetPlotSettings(PlotType.RAW)

    val state = vm.ui.value
    assertEquals(5, state.plotSettings.getValue(PlotType.RAW).windowSeconds)
  }
}
