package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GamePhysicsTest {

  @Test
  fun step_movesAltitudeTowardTarget() {
    val start = LevitationState(altitude = 0.2f, velocity = 0f)

    val result = GamePhysics.step(
      state = start,
      target = 0.8f,
      dtSeconds = 0.05f,
    )

    assertThat(result.altitude).isGreaterThan(start.altitude)
  }

  @Test
  fun metricToTargetHeight_clampsRange() {
    assertThat(GamePhysics.metricToTargetHeight(-0.5f)).isEqualTo(0f)
    assertThat(GamePhysics.metricToTargetHeight(1.5f)).isEqualTo(1f)
  }

  @Test
  fun shouldShowBatteryRow_onlyWhenPresent() {
    assertThat(shouldShowBatteryRow(null)).isFalse()
    assertThat(shouldShowBatteryRow(40)).isTrue()
  }
}
