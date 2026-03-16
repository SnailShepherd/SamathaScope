package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScoreModelTest {

  @Test
  fun quality_includesLineNoiseInTotalArtefact() {
    val scorer = ScoreModel()
    val baseFeatures = feature(lineNoiseRatio = 0.02f)
    val highLineFeatures = feature(lineNoiseRatio = 0.40f)

    val base = scorer.quality(poorSignal = 10, features = baseFeatures)
    val highLine = scorer.quality(poorSignal = 10, features = highLineFeatures)

    assertThat(highLine.lineNoise).isGreaterThan(base.lineNoise)
    assertThat(highLine.artefactScore).isGreaterThan(base.artefactScore)
  }

  @Test
  fun classify_marksContaminatedWindowsAndSuppressesProxy() {
    val scorer = ScoreModel()
    scorer.setCalibration(calibration())

    val output = scorer.classify(
      poorSignal = 40,
      features = feature(hfRatio = 0.50f, blinkRateHz = 0.9f, clipFraction = 0.02f),
    )

    assertThat(output.rawStateLabel).isEqualTo(StateLabel.SIGNAL_CONTAMINATED)
    assertThat(output.quality.isContaminated).isTrue()
    assertThat(output.meditationProxy).isLessThan(0.2f)
  }

  @Test
  fun classify_separatesDrowsyFromSettled() {
    val scorer = ScoreModel()
    scorer.setCalibration(calibration())

    val drowsy = scorer.classify(
      poorSignal = 10,
      features = feature(
        tbr = 0.8f,
        tar = 0.9f,
        abr = -0.6f,
        spectralEntropy = 0.1f,
        emg = -1.2f,
      ),
    )
    val settled = scorer.classify(
      poorSignal = 10,
      features = feature(
        tbr = -0.5f,
        tar = -0.4f,
        abr = 0.7f,
        spectralEntropy = 0.8f,
        emg = -1.4f,
      ),
    )

    assertThat(drowsy.rawStateLabel).isEqualTo(StateLabel.DROWSY)
    assertThat(drowsy.drowsyScore).isGreaterThan(0.65f)
    assertThat(settled.rawStateLabel).isEqualTo(StateLabel.SETTLED)
    assertThat(settled.meditationProxy).isGreaterThan(drowsy.meditationProxy)
  }

  private fun calibration(): Calibration {
    return Calibration(
      logBeta = RobustBaselineStat(median = 0f, mad = 0.1f, floor = 0.1f),
      tbr = RobustBaselineStat(median = 0f, mad = 0.1f, floor = 0.1f),
      tar = RobustBaselineStat(median = 0f, mad = 0.1f, floor = 0.1f),
      abr = RobustBaselineStat(median = 0f, mad = 0.1f, floor = 0.1f),
      entropy = RobustBaselineStat(median = 0.5f, mad = 0.1f, floor = 0.1f),
      emg = RobustBaselineStat(median = -1f, mad = 0.1f, floor = 0.1f),
    )
  }

  private fun feature(
    lineNoiseRatio: Float = 0.02f,
    hfRatio: Float = 0.15f,
    blinkRateHz: Float = 0.1f,
    clipFraction: Float = 0f,
    tbr: Float = 0f,
    tar: Float = 0f,
    abr: Float = 0.2f,
    spectralEntropy: Float = 0.6f,
    emg: Float = -1f,
  ): EegFeatures {
    return EegFeatures(
      windowStartMs = 0L,
      windowEndMs = 8_000L,
      pTheta = 1.5f,
      pAlpha = 2.0f,
      pBeta = 1.2f,
      pHf = 0.4f,
      p4To13 = 2.4f,
      p4To30 = 4.7f,
      logTheta = 0.1f,
      logAlpha = 0.2f,
      logBeta = 0.1f,
      logHf = -1.0f,
      relativeTheta = 0.3f,
      relativeAlpha = 0.4f,
      relativeBeta = 0.2f,
      relativeHf = hfRatio,
      tbr = tbr,
      tar = tar,
      abr = abr,
      thetaPeakHz = 5.5f,
      alphaPeakHz = 10f,
      spectralEntropy = spectralEntropy,
      emg = emg,
      hfRatio = hfRatio,
      blinkRateHz = blinkRateHz,
      clipFraction = clipFraction,
      lineNoiseRatio = lineNoiseRatio,
      maxGapMs = 20L,
    )
  }
}
