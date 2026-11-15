package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WindowScoreTest {

  @Test
  fun sessionStats_averagesAndCountsTimeOver80() {
    val stats = SessionStats()

    stats.add(0.9f, atMs = 1_000L)
    stats.add(0.9f, atMs = 2_000L) // +1 s over 80%.
    stats.add(0.5f, atMs = 3_000L) // Below: not counted.
    stats.add(0.85f, atMs = 4_000L) // +1 s.

    assertThat(stats.averageMeditationProxy).isWithin(1e-6f).of((0.9f + 0.9f + 0.5f + 0.85f) / 4f)
    assertThat(stats.secondsOver80).isEqualTo(2)
  }

  @Test
  fun sessionStats_gapAfterPauseIsNotCounted() {
    val stats = SessionStats()
    stats.add(0.9f, atMs = 1_000L)

    stats.markGap()
    stats.add(0.9f, atMs = 60_000L) // Resumed after a minute's pause.
    stats.add(0.9f, atMs = 61_000L)

    assertThat(stats.secondsOver80).isEqualTo(1)
  }

  @Test
  fun feedbackValue_followsTheChosenSource() {
    val smoothed = smoothed(meditationProxy = 0.6f, settledness = 0.7f, alertness = 0.8f)

    assertThat(smoothed.feedbackValue(PlotType.MEDITATION_PROXY)).isEqualTo(0.6f)
    assertThat(smoothed.feedbackValue(PlotType.SETTLEDNESS)).isEqualTo(0.7f)
    assertThat(smoothed.feedbackValue(PlotType.ALERTNESS)).isEqualTo(0.8f)
    assertThat(smoothed.feedbackValue(PlotType.CONTROL)).isEqualTo(0f)
  }

  @Test
  fun metricHistorySample_coversEveryPlottedMetric() {
    val sample = smoothed(meditationProxy = 0.6f).metricHistorySample(eSenseMeditation = 40, eSenseAttention = 70)

    assertThat(sample.keys).containsExactlyElementsIn(PlotType.entries.filter { it != PlotType.RAW })
    assertThat(sample[PlotType.MEDITATION_PROXY]).isEqualTo(0.6f)
    assertThat(sample[PlotType.ESENSE_ATTENTION]).isEqualTo(70f)
  }

  private fun smoothed(meditationProxy: Float = 0.5f, settledness: Float = 0.5f, alertness: Float = 0.5f) =
    SmoothedOutput(
      probabilities = StateProbabilities(0f, 0.1f, 0.5f, 0.2f, 0.3f, 0.1f),
      alertness = alertness,
      control = 0.5f,
      settledness = settledness,
      artefactScore = 0.1f,
      qualityConfidence = 0.9f,
      meditationProxy = meditationProxy,
      displayedDrowsyScore = 0.2f,
      displayedState = StateLabel.SETTLED,
    )
}
