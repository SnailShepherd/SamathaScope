package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GamePhysicsTest {

  @Test
  fun clamp01_limitsRange() {
    assertThat(clamp01(-0.3f)).isEqualTo(0f)
    assertThat(clamp01(1.4f)).isEqualTo(1f)
    assertThat(clamp01(0.4f)).isEqualTo(0.4f)
  }

  @Test
  fun approach_movesTowardTarget() {
    val result = approach(current = 0.2f, target = 0.8f, factor = 0.5f)

    assertThat(result).isWithin(1e-6f).of(0.5f)
  }

  @Test
  fun sequenceFloat_isDeterministicAndNormalized() {
    val first = sequenceFloat(index = 3, salt = 7)
    val second = sequenceFloat(index = 3, salt = 7)

    assertThat(first).isEqualTo(second)
    assertThat(first).isAtLeast(0f)
    assertThat(first).isAtMost(1f)
  }

  @Test
  fun shouldShowBatteryRow_onlyWhenPresent() {
    assertThat(shouldShowBatteryRow(null)).isFalse()
    assertThat(shouldShowBatteryRow(40)).isTrue()
  }
}
