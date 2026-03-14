package com.mordin.samathascope

import com.mordin.samathascope.scene.SceneState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SessionRecorderCsvTest {

  @Test
  fun header_includesNewClassifierColumns() {
    val header = recordedFeatureCsvHeader()

    assertThat(header).contains("meditation_proxy")
    assertThat(header).contains("displayed_state_label")
    assertThat(header).contains("artefact_blink_norm_hz")
    assertThat(header).contains("d_logit")
    assertThat(header).contains("displayed_drowsy_score")
    assertThat(header).contains("feedback_value")
    assertThat(header).contains("selected_game_id")
    assertThat(header).contains("game_correction_pulse")
    assertThat(header).contains("scene_progress")
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
      artefactCalibrationProfile = ArtefactCalibrationProfile(
        blinkNormalizationHz = 1.25f,
        emgNormalizationHfRatio = 0.42f,
        eyeMotionBlinkPeakHz = 2.0f,
        jawClenchHfPeakRatio = 0.7f,
        frownHfPeakRatio = 0.6f,
      ),
      rawProbabilities = StateProbabilities(0.1f, 0.2f, 0.7f, 0.1f, 0.1f, 0.0f),
      smoothedProbabilities = StateProbabilities(0.2f, 0.3f, 0.8f, 0.2f, 0.1f, 0.0f),
      rawStateLabel = StateLabel.SETTLED,
      displayedStateLabel = StateLabel.SETTLED,
      drowsinessEvidence = DrowsinessEvidence(
        tarContribution = 0.39f,
        tbrContribution = 0.18f,
        entropyContribution = -0.21f,
        abrContribution = -0.16f,
        logit = 0.20f,
      ),
      displayedDrowsyScore = 0.28f,
      alertness = 0.9f,
      control = 0.8f,
      settledness = 0.7f,
      meditationProxy = 0.6f,
      feedbackMetric = PlotType.MEDITATION_PROXY,
      feedbackValue = 0.55f,
      selectedGameId = GameId.FIRE_KEEPER,
      gameSignals = GameSignalSnapshot(
        stability = 0.62f,
        drift = 0.12f,
        noise = 0.15f,
        fatigue = 0.05f,
        precision = 0.71f,
        correctionPulse = 0.20f,
      ),
      sceneState = SceneState(
        calmness = 0.64f,
        focus = 0.58f,
        stability = 0.72f,
        intensity = 0.46f,
        drift = -0.08f,
        progress = 0.34f,
        calmnessRate = 0.04f,
        focusRate = -0.02f,
        intensityRate = 0.10f,
      ),
      gameSummary = "height=0.540;smoke=0.110",
    )

    val csv = row.toCsvRow()

    assertThat(csv).contains("SETTLED")
    assertThat(csv).contains("MEDITATION_PROXY")
    assertThat(csv).contains("FIRE_KEEPER")
    assertThat(csv).contains("1.250000")
    assertThat(csv).contains("0.200000")
    assertThat(csv).contains("0.340000")
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
