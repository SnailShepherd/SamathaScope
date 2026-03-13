package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GameControllersTest {

  @Test
  fun skyTower_placesBlockAfterTap() {
    val controller = SkyTowerController()
    var state = controller.initialState()
    val signals = GameSignalSnapshot(
      stability = 0.8f,
      drift = 0.1f,
      noise = 0.05f,
      fatigue = 0.1f,
      precision = 0.8f,
      correctionPulse = 0.6f,
    )

    repeat(24) { index ->
      val events = if (index % 3 == 0) listOf(GameEvent.Tap) else emptyList()
      state = controller.step(state, dtSeconds = 0.1f, signals = signals, events = events)
    }

    assertThat(state.placements).isGreaterThan(0)
  }

  @Test
  fun inkGarden_correctionPulseRaisesRepairGlow() {
    val controller = InkGardenController()
    val state = controller.initialState()

    val next = controller.step(
      state = state,
      dtSeconds = 0.3f,
      signals = GameSignalSnapshot(stability = 0.7f, drift = 0.1f, noise = 0.1f, fatigue = 0f, correctionPulse = 1f),
      events = emptyList(),
    )

    assertThat(next.repairGlow).isGreaterThan(state.repairGlow)
  }

  @Test
  fun inkGarden_generatesVisibleInkSegments() {
    val controller = InkGardenController()
    var state = controller.initialState()

    repeat(12) {
      state = controller.step(
        state = state,
        dtSeconds = 0.2f,
        signals = GameSignalSnapshot(stability = 0.8f, drift = 0.2f, noise = 0.1f, fatigue = 0.05f),
        events = emptyList(),
      )
    }

    assertThat(state.segments).isNotEmpty()
  }

  @Test
  fun fireKeeper_noiseIncreasesSmoke() {
    val controller = FireKeeperController()
    val calm = controller.step(
      state = controller.initialState(),
      dtSeconds = 0.5f,
      signals = GameSignalSnapshot(stability = 0.8f, drift = 0.1f, noise = 0.05f, fatigue = 0.1f),
      events = emptyList(),
    )
    val noisy = controller.step(
      state = controller.initialState(),
      dtSeconds = 0.5f,
      signals = GameSignalSnapshot(stability = 0.3f, drift = 0.4f, noise = 0.8f, fatigue = 0.1f),
      events = emptyList(),
    )

    assertThat(noisy.smokeDensity).isGreaterThan(calm.smokeDensity)
  }

  @Test
  fun scriptorium_fatigueRaisesFade() {
    val controller = ScriptoriumController()
    val rested = controller.step(
      state = controller.initialState(),
      dtSeconds = 0.5f,
      signals = GameSignalSnapshot(stability = 0.7f, drift = 0.2f, noise = 0.1f, fatigue = 0.1f),
      events = emptyList(),
    )
    val tired = controller.step(
      state = controller.initialState(),
      dtSeconds = 0.5f,
      signals = GameSignalSnapshot(stability = 0.7f, drift = 0.2f, noise = 0.1f, fatigue = 0.8f),
      events = emptyList(),
    )

    assertThat(tired.fade).isGreaterThan(rested.fade)
  }
}
