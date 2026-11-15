package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CalibrationManagerTest {

  private val W = ANALYSIS_WINDOW_SECONDS

  /** Runs a full 60 s calibration: eyes-open windows at [openLogBeta], eyes-closed at [closedLogBeta]. */
  private fun calibrate(
    manager: CalibrationManager,
    clock: LongArray,
    openLogBeta: Float = 0.2f,
    closedLogBeta: Float = 1.0f,
    straddleLogBeta: Float = closedLogBeta,
  ) {
    val scorer = ScoreModel()
    clock[0] = 0L
    manager.reset()
    // A window ending at t seconds starts at t - W.
    for (t in W..60) {
      clock[0] = t * 1_000L
      val startsInClosedHalf = t - W >= 30
      val logBeta = when {
        t <= 30 -> openLogBeta
        startsInClosedHalf -> closedLogBeta
        else -> straddleLogBeta
      }
      val features = feature(index = t - W, logBeta = logBeta)
      manager.addSample(features = features, quality = scorer.quality(poorSignal = 10, features = features))
    }
  }

  @Test
  fun finish_scoresAgainstTheHalfMatchingTheSessionMode() {
    val clock = longArrayOf(0L)
    val manager = CalibrationManager(calibrationSeconds = 60, nowMs = { clock[0] })

    // Windows straddling the switch to eyes closed carry an outlier value that must be left out.
    calibrate(manager, clock, openLogBeta = 0.2f, closedLogBeta = 1.0f, straddleLogBeta = 9f)

    assertThat(manager.isDone()).isTrue()
    assertThat(manager.finish(SessionMode.EYES_OPEN)!!.logBeta.median).isWithin(0.01f).of(0.2f)
    val closed = manager.finish(SessionMode.EYES_CLOSED)!!
    assertThat(closed.logBeta.median).isWithin(0.01f).of(1.0f)
    assertThat(closed.logBeta.mad).isLessThan(0.2f)
  }

  @Test
  fun adaptiveCalibration_acceptsOnlyCleanSamplesAndBlendsSlowlyWithPrior() {
    val clock = longArrayOf(0L)
    val manager = CalibrationManager(calibrationSeconds = 60, nowMs = { clock[0] })
    val scorer = ScoreModel()
    calibrate(manager, clock, closedLogBeta = 1.0f)
    val prior = manager.finish(SessionMode.EYES_CLOSED)!!

    repeat(20) {
      val dirtyPoorSignal = feature(index = 200 + it, logBeta = 3f, hfRatio = 0.15f)
      manager.addAdaptiveSample(dirtyPoorSignal, scorer.quality(poorSignal = 120, features = dirtyPoorSignal))
      val dirtyEmg = feature(index = 300 + it, logBeta = 3f, hfRatio = 0.50f)
      manager.addAdaptiveSample(dirtyEmg, scorer.quality(poorSignal = 10, features = dirtyEmg))
    }
    assertThat(manager.adaptiveSampleCount()).isEqualTo(0)

    // 10 minutes of clean signal at a new level moves the baseline halfway, not all the way.
    repeat(600) {
      val clean = feature(index = 400 + it, logBeta = 3.0f)
      manager.addAdaptiveSample(clean, scorer.quality(poorSignal = 10, features = clean))
    }
    val adaptive = manager.buildAdaptiveCalibration(prior)!!
    assertThat(adaptive.logBeta.median).isWithin(0.01f).of(2.0f)

    // The window is capped at 30 minutes.
    repeat(2_000) {
      val clean = feature(index = 1_000 + it, logBeta = 3.0f)
      manager.addAdaptiveSample(clean, scorer.quality(poorSignal = 10, features = clean))
    }
    assertThat(manager.adaptiveSampleCount()).isEqualTo(1_800)
    assertThat(manager.buildAdaptiveCalibration(prior)!!.logBeta.median).isWithin(0.01f).of(2.5f)
  }

  @Test
  fun earlySkip_returnsNoBaselineButAdaptiveWindowStillFills() {
    val clock = longArrayOf(0L)
    val manager = CalibrationManager(calibrationSeconds = 60, nowMs = { clock[0] })
    val scorer = ScoreModel()

    manager.reset()
    for (t in W..10) {
      clock[0] = t * 1_000L
      val features = feature(index = t - W, logBeta = 0.2f)
      manager.addSample(features = features, quality = scorer.quality(poorSignal = 10, features = features))
    }

    // Too few windows, and none in the eyes-closed half: no degenerate or mismatched baseline.
    assertThat(manager.finish(SessionMode.EYES_CLOSED)).isNull()
    assertThat(manager.buildAdaptiveCalibration(prior = null)).isNull()

    repeat(20) {
      val clean = feature(index = 100 + it, logBeta = 1.5f)
      manager.addAdaptiveSample(clean, scorer.quality(poorSignal = 10, features = clean))
    }
    val adaptive = manager.buildAdaptiveCalibration(prior = null)
    assertThat(adaptive).isNotNull()
    assertThat(adaptive!!.logBeta.median).isWithin(0.01f).of(1.5f)
  }

  @Test
  fun pause_freezesTimerAndPhase() {
    val clock = longArrayOf(0L)
    val manager = CalibrationManager(calibrationSeconds = 60, nowMs = { clock[0] })

    manager.reset()
    clock[0] = 10_000L
    manager.pause()
    clock[0] = 45_000L
    assertThat(manager.remainingSeconds()).isEqualTo(50)
    assertThat(manager.currentPhase()).isEqualTo(SessionMode.EYES_OPEN)

    manager.resume()
    clock[0] = 60_000L // 25 s of active time.
    assertThat(manager.remainingSeconds()).isEqualTo(35)
    assertThat(manager.currentPhase()).isEqualTo(SessionMode.EYES_OPEN)
    clock[0] = 66_000L // 31 s of active time.
    assertThat(manager.currentPhase()).isEqualTo(SessionMode.EYES_CLOSED)
  }

  @Test
  fun windowsContainingAPause_areNotPure() {
    val clock = longArrayOf(0L)
    val manager = CalibrationManager(calibrationSeconds = 60, nowMs = { clock[0] })
    val scorer = ScoreModel()

    manager.reset()
    clock[0] = 31_000L
    manager.pause()
    clock[0] = 40_000L
    manager.resume() // Active time 31 s: eyes closed, resumed at 40 s.
    // Eyes-closed windows: those starting before the resume hold paused data and should be avoided.
    for (t in 41..80) {
      clock[0] = t * 1_000L
      val logBeta = if (t - W >= 40) 1.0f else 9f
      val features = feature(index = t - W, logBeta = logBeta)
      manager.addSample(features = features, quality = scorer.quality(poorSignal = 10, features = features))
    }

    assertThat(manager.finish(SessionMode.EYES_CLOSED)!!.logBeta.median).isWithin(0.01f).of(1.0f)
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
    signalGapMs: Long = 20L,
  ): EegFeatures {
    return EegFeatures(
      windowStartMs = index * 1_000L,
      windowEndMs = index * 1_000L + W * 1_000L,
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
      signalGapMs = signalGapMs,
    )
  }
}
