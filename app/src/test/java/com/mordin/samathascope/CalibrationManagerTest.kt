package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CalibrationManagerTest {

  @Test
  fun adaptiveCalibration_acceptsOnlyCleanSamplesAndRolls() {
    var now = 0L
    val manager = CalibrationManager(
      calibrationSeconds = 60,
      pointsPerSecond = 4,
      adaptiveWindowSeconds = 600,
      nowMs = { now },
    )

    manager.reset()
    repeat(40) { i ->
      manager.addSample(rai = i.toFloat(), artefactA = 0.2f)
    }

    now = 61_000L
    assertThat(manager.isDone()).isTrue()

    val base = manager.buildCalibration()
    manager.seedAdaptiveWindowFromCalibration()
    val seededCount = manager.adaptiveSampleCount()
    assertThat(seededCount).isAtLeast(20)

    repeat(100) {
      manager.addAdaptiveSample(rai = 200f, artefactA = 0.1f, poorSignal = 120)
      manager.addAdaptiveSample(rai = 200f, artefactA = 0.7f, poorSignal = 10)
    }
    assertThat(manager.adaptiveSampleCount()).isEqualTo(seededCount)

    repeat(300) {
      manager.addAdaptiveSample(rai = 200f, artefactA = 0.2f, poorSignal = 10)
    }

    val adaptive = manager.buildAdaptiveCalibration()
    assertThat(adaptive).isNotNull()
    assertThat(adaptive!!.raiP10).isGreaterThan(base.raiP10)
    assertThat(manager.adaptiveSampleCount()).isAtMost(600 * 4)
  }
}

