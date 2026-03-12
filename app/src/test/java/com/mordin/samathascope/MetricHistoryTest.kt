package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MetricHistoryTest {

  @Test
  fun series_respectsWindowLength() {
    val history = MetricHistory(maxSeconds = 600, pointsPerSecond = 1)

    repeat(100) { index ->
      history.add(
        mapOf(
          PlotType.MEDITATION_PROXY to index / 100f,
          PlotType.SETTLEDNESS to 0.5f,
          PlotType.CONTROL to 0.6f,
          PlotType.ALERTNESS to 0.7f,
          PlotType.DROWSY_SCORE to 0.2f,
          PlotType.ARTEFACT_SCORE to 0.1f,
          PlotType.QUALITY_CONFIDENCE to 0.9f,
          PlotType.EFFORTFUL_FOCUS_SCORE to 0.3f,
          PlotType.MIND_WANDERING_SCORE to 0.2f,
          PlotType.ESENSE_MEDITATION to 50f,
          PlotType.ESENSE_ATTENTION to 60f,
        )
      )
    }

    val series = history.series(PlotType.MEDITATION_PROXY, windowSeconds = 10)

    assertThat(series).hasSize(10)
  }
}
