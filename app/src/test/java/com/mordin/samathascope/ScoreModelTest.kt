package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScoreModelTest {

  @Test
  fun totalArtefact_excludesLineNoiseContribution() {
    val scorer = ScoreModel()

    val baseFeatures = EegFeatures(
      rai = 0f,
      alphaPower = 1f,
      hiPower = 1f,
      total145 = 100f,
      line50 = 1f,
      emgFrac = 0.20f,
      blinkScore = 0.10f,
      clipFrac = 0f,
    )

    val highLineFeatures = baseFeatures.copy(line50 = 60f)

    val base = scorer.artefacts(poorSignal = 40, features = baseFeatures, stallMs = 100)
    val highLine = scorer.artefacts(poorSignal = 40, features = highLineFeatures, stallMs = 100)

    assertThat(highLine.line).isGreaterThan(base.line)
    assertThat(highLine.totalA).isWithin(1e-6f).of(base.totalA)
  }
}

