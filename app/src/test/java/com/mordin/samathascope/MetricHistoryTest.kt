package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MetricHistoryTest {

  @Test
  fun series_respectsWindowLength() {
    val history = MetricHistory(maxSeconds = 600, pointsPerSecond = 4)

    repeat(100) { index ->
      history.add(
        samathaScore = index / 100f,
        artefactScore = 0.2f,
        relaxedAlertnessIndex = 0.3f,
        meditationValue = 50,
        attentionValue = 60,
      )
    }

    val series = history.series(PlotType.SAMATHA_SCORE, windowSeconds = 10)

    assertThat(series).hasSize(40)
  }
}
