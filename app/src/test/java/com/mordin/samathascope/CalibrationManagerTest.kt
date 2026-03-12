package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CalibrationManagerTest {

  @Test
  fun adaptiveCalibration_acceptsOnlyCleanSamplesAndRolls() {
    var now = 0L
    val manager = CalibrationManager(
      calibrationSeconds = 60,
      pointsPerSecond = 1,
      adaptiveWindowSeconds = 600,
      nowMs = { now },
    )
    val scorer = ScoreModel()

    manager.reset()
    repeat(40) { index ->
      val features = feature(index = index, logBeta = 0.2f + index * 0.01f)
      manager.addSample(features = features, quality = scorer.quality(poorSignal = 10, features = features))
    }

    now = 61_000L
    assertThat(manager.isDone()).isTrue()

    val base = manager.buildCalibration()
    manager.seedAdaptiveWindowFromCalibration()
    val seededCount = manager.adaptiveSampleCount()
    assertThat(seededCount).isAtLeast(20)

    repeat(20) {
      val dirtyPoorSignal = feature(index = 200 + it, logBeta = 2f, hfRatio = 0.15f)
      manager.addAdaptiveSample(dirtyPoorSignal, scorer.quality(poorSignal = 120, features = dirtyPoorSignal))

      val dirtyEmg = feature(index = 300 + it, logBeta = 2f, hfRatio = 0.50f)
      manager.addAdaptiveSample(dirtyEmg, scorer.quality(poorSignal = 10, features = dirtyEmg))
    }
    assertThat(manager.adaptiveSampleCount()).isEqualTo(seededCount)

    repeat(200) {
      val clean = feature(index = 400 + it, logBeta = 2.0f)
      manager.addAdaptiveSample(clean, scorer.quality(poorSignal = 10, features = clean))
    }

    val adaptive = manager.buildAdaptiveCalibration()
    assertThat(adaptive).isNotNull()
    assertThat(adaptive!!.logBeta.median).isGreaterThan(base.logBeta.median)
    assertThat(manager.adaptiveSampleCount()).isAtMost(600)
  }

  @Test
  fun artefactCalibrationProfile_usesSeparateArtefactSamples() {
    val manager = CalibrationManager()

    manager.reset()
    manager.addArtefactSample(
      prompt = ArtefactPrompt.LOOK_LEFT_RIGHT,
      features = feature(index = 1, logBeta = 0.3f, blinkRateHz = 2.2f)
    )
    manager.addArtefactSample(
      prompt = ArtefactPrompt.LOOK_UP_DOWN,
      features = feature(index = 2, logBeta = 0.3f, blinkRateHz = 1.6f)
    )
    manager.addArtefactSample(
      prompt = ArtefactPrompt.JAW_CLENCH,
      features = feature(index = 3, logBeta = 0.3f, hfRatio = 0.82f)
    )
    manager.addArtefactSample(
      prompt = ArtefactPrompt.FROWN,
      features = feature(index = 4, logBeta = 0.3f, hfRatio = 0.68f)
    )

    val profile = manager.buildArtefactCalibrationProfile()

    assertThat(profile.eyeMotionBlinkPeakHz).isWithin(1e-6f).of(2.2f)
    assertThat(profile.blinkNormalizationHz).isWithin(1e-6f).of(1.32f)
    assertThat(profile.jawClenchHfPeakRatio).isWithin(1e-6f).of(0.82f)
    assertThat(profile.frownHfPeakRatio).isWithin(1e-6f).of(0.68f)
    assertThat(profile.emgNormalizationHfRatio).isWithin(1e-6f).of(0.492f)
  }

  private fun feature(
    index: Int,
    logBeta: Float,
    hfRatio: Float = 0.15f,
    blinkRateHz: Float = 0.1f,
    clipFraction: Float = 0f,
    maxGapMs: Long = 20L,
  ): EegFeatures {
    return EegFeatures(
      windowStartMs = index * 1_000L,
      windowEndMs = index * 1_000L + 8_000L,
      pTheta = 1.5f,
      pAlpha = 2.0f,
      pBeta = 1.2f,
      pHf = 0.4f,
      p4To13 = 2.4f,
      p4To30 = 4.7f,
      logTheta = 0.1f,
      logAlpha = 0.2f,
      logBeta = logBeta,
      logHf = -1.0f,
      relativeTheta = 0.3f,
      relativeAlpha = 0.4f,
      relativeBeta = 0.2f,
      relativeHf = hfRatio,
      tbr = 0.05f + index * 0.001f,
      tar = -0.02f + index * 0.001f,
      abr = 0.25f,
      thetaPeakHz = 5.5f,
      alphaPeakHz = 10f,
      spectralEntropy = 0.55f,
      emg = -1.2f,
      hfRatio = hfRatio,
      blinkRateHz = blinkRateHz,
      clipFraction = clipFraction,
      lineNoiseRatio = 0.02f,
      maxGapMs = maxGapMs,
    )
  }
}
