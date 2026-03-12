package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RollingAverageTest {

  @Test
  fun exponentialSmoother_usesPreviousValue() {
    val smoother = ExponentialSmoother(alpha = 0.3f)

    assertThat(smoother.add(1f)).isEqualTo(1f)
    assertThat(smoother.add(0f)).isWithin(1e-6f).of(0.7f)
  }

  @Test
  fun stateHold_requiresRepeatedWinsUnlessConfidenceIsHigh() {
    val hold = StateHoldSmoother(requiredWins = 3, immediateThreshold = 0.70f)

    assertThat(hold.update(StateLabel.SETTLED, 0.60f)).isEqualTo(StateLabel.UNCERTAIN)
    assertThat(hold.update(StateLabel.SETTLED, 0.60f)).isEqualTo(StateLabel.UNCERTAIN)
    assertThat(hold.update(StateLabel.SETTLED, 0.60f)).isEqualTo(StateLabel.SETTLED)
    assertThat(hold.update(StateLabel.DROWSY, 0.80f)).isEqualTo(StateLabel.DROWSY)
  }
}
