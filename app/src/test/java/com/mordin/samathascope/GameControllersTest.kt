package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GameControllersTest {

  @Test
  fun skyTower_carrierWaitsForTapBeforeRelease() {
    val controller = SkyTowerController()
    var state = controller.initialState()
    val startY = state.carrier.y

    repeat(20) {
      state = controller.step(
        state = state,
        dtSeconds = 0.1f,
        signals = GameSignalSnapshot(stability = 0.8f, drift = 0.1f, noise = 0.05f, precision = 0.7f),
        events = emptyList(),
      )
    }

    assertThat(state.activeBodyId).isNull()
    assertThat(state.carrier.visible).isTrue()
    assertThat(state.carrier.y).isEqualTo(startY)
    assertThat(state.placements).isEqualTo(0)
  }

  @Test
  fun skyTower_oneTapCreatesSingleReleasedBlock() {
    val controller = SkyTowerController()
    var state = controller.initialState()

    state = controller.step(
      state = state,
      dtSeconds = 0.1f,
      signals = GameSignalSnapshot(stability = 0.7f, drift = 0.2f, noise = 0.05f, precision = 0.8f),
      events = listOf(GameEvent.Tap, GameEvent.Tap),
    )

    assertThat(state.activeBodyId).isNotNull()
    assertThat(state.carrier.visible).isFalse()
    assertThat(state.bodies.size).isEqualTo(2)
  }

  @Test
  fun inkGarden_correctionPulseRaisesBloom() {
    val controller = InkGardenController()
    val state = controller.initialState()

    val next = controller.step(
      state = state,
      dtSeconds = 0.3f,
      signals = GameSignalSnapshot(stability = 0.7f, drift = 0.1f, noise = 0.1f, fatigue = 0f, correctionPulse = 1f),
      events = emptyList(),
    )

    assertThat(next.bloom).isGreaterThan(state.bloom)
  }

  @Test
  fun inkGarden_accumulatesPigmentAcrossCells() {
    val controller = InkGardenController()
    var state = controller.initialState()

    repeat(14) {
      state = controller.step(
        state = state,
        dtSeconds = 0.2f,
        signals = GameSignalSnapshot(stability = 0.8f, drift = 0.2f, noise = 0.1f, fatigue = 0.05f),
        events = emptyList(),
      )
    }

    assertThat(state.cells.count { it.pigment > 0.05f }).isGreaterThan(0)
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
