package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SessionRecorderCsvTest {

  @Test
  fun header_includesNewClassifierColumns() {
    val header = recordedFeatureCsvHeader()

    assertThat(header).contains("meditation_proxy")
    assertThat(header).contains("displayed_state_label")
    assertThat(header).contains("feedback_value")
    assertThat(header).contains("game_value")
  }

  @Test
  fun row_serializesEnumsAndProbabilities() {
    val row = RecordedFeatureRow(
      timestampMs = 123L,
      poorSignal = 10,
      notch50Enabled = true,
      features = feature(),
      zScores = FeatureZScores(logBeta = 0.1f, tbr = 0.2f, tar = 0.3f, abr = 0.4f, entropy = 0.5f, emg = 0.6f),
      quality = quality(),
      rawProbabilities = StateProbabilities(0.1f, 0.2f, 0.7f, 0.1f, 0.1f, 0.0f),
      smoothedProbabilities = StateProbabilities(0.2f, 0.3f, 0.8f, 0.2f, 0.1f, 0.0f),
      rawStateLabel = StateLabel.SETTLED,
      displayedStateLabel = StateLabel.SETTLED,
      alertness = 0.9f,
      control = 0.8f,
      settledness = 0.7f,
      meditationProxy = 0.6f,
      feedbackMetric = PlotType.MEDITATION_PROXY,
      feedbackValue = 0.55f,
      gameMetric = PlotType.CONTROL,
      gameValue = 0.45f,
    )

    val csv = row.toCsvRow()

    assertThat(csv).contains("SETTLED")
    assertThat(csv).contains("MEDITATION_PROXY")
    assertThat(csv).contains("CONTROL")
  }

  private fun feature(): EegFeatures {
    return EegFeatures(
      windowStartMs = 0L,
      windowEndMs = 8_000L,
      pTheta = 1f,
      pAlpha = 2f,
      pBeta = 3f,
      pHf = 0.5f,
      p4To13 = 4f,
      p4To30 = 5f,
      logTheta = 0.1f,
      logAlpha = 0.2f,
      logBeta = 0.3f,
      logHf = -0.4f,
      relativeTheta = 0.2f,
      relativeAlpha = 0.3f,
      relativeBeta = 0.4f,
      relativeHf = 0.1f,
      tbr = 0.1f,
      tar = 0.2f,
      abr = 0.3f,
      thetaPeakHz = 5f,
      alphaPeakHz = 10f,
      spectralEntropy = 0.6f,
      emg = -1f,
      hfRatio = 0.1f,
      blinkRateHz = 0.1f,
      clipFraction = 0f,
      lineNoiseRatio = 0.02f,
      maxGapMs = 20L,
    )
  }

  private fun quality(): QualityMetrics {
    return QualityMetrics(
      poorSignal = 10,
      contact = 0.2f,
      lineNoise = 0.1f,
      emg = 0.1f,
      blink = 0.1f,
      clip = 0f,
      stall = 0.05f,
      artefactScore = 0.15f,
      qualityConfidence = 0.85f,
      hfRatio = 0.1f,
      blinkRateHz = 0.1f,
      clipFraction = 0f,
      maxGapMs = 20L,
      isContaminated = false,
      isCalibrationClean = true,
    )
  }
}
