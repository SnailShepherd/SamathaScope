package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CalibrationCountdownTest {

  @Test
  fun countdownValue_onlyAppearsInFinalTenSecondsBeforeEyesClosed() {
    assertThat(calibrationEyesClosedCountdownValue(41)).isNull()
    assertThat(calibrationEyesClosedCountdownValue(40)).isEqualTo(10)
    assertThat(calibrationEyesClosedCountdownValue(35)).isEqualTo(5)
    assertThat(calibrationEyesClosedCountdownValue(31)).isEqualTo(1)
    assertThat(calibrationEyesClosedCountdownValue(30)).isNull()
  }
}
