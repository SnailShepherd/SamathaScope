package com.mordin.samathascope

/** Length of the clean-baseline calibration: the first half eyes open, the second eyes closed. */
internal const val CALIBRATION_SECONDS = 60

/** Length of each guided artefact-calibration prompt (look left/right, jaw clench, ...). */
internal const val ARTEFACT_PROMPT_SECONDS = 5

data class RobustBaselineStat(
  val median: Float,
  val mad: Float,
  val floor: Float,
) {
  fun z(value: Float): Float {
    val denominator = maxOf(mad, floor)
    return (0.6745f * (value - median) / denominator).coerceIn(-4f, 4f)
  }

  /** Linear blend: [weight] 0 keeps this stat, 1 gives [other]. */
  fun blend(other: RobustBaselineStat, weight: Float): RobustBaselineStat {
    val w = weight.coerceIn(0f, 1f)
    return RobustBaselineStat(
      median = median + w * (other.median - median),
      mad = mad + w * (other.mad - mad),
      floor = floor,
    )
  }
}

data class Calibration(
  val logBeta: RobustBaselineStat,
  val tbr: RobustBaselineStat,
  val tar: RobustBaselineStat,
  val abr: RobustBaselineStat,
  val entropy: RobustBaselineStat,
  val emg: RobustBaselineStat,
) {
  fun zScores(features: EegFeatures): FeatureZScores {
    return FeatureZScores(
      logBeta = logBeta.z(features.logBeta),
      tbr = tbr.z(features.tbr),
      tar = tar.z(features.tar),
      abr = abr.z(features.abr),
      entropy = entropy.z(features.spectralEntropy),
      emg = emg.z(features.emg),
    )
  }

  fun blend(other: Calibration, weight: Float): Calibration {
    return Calibration(
      logBeta = logBeta.blend(other.logBeta, weight),
      tbr = tbr.blend(other.tbr, weight),
      tar = tar.blend(other.tar, weight),
      abr = abr.blend(other.abr, weight),
      entropy = entropy.blend(other.entropy, weight),
      emg = emg.blend(other.emg, weight),
    )
  }
}

data class CalibrationFeatureSample(
  val features: EegFeatures,
  val quality: QualityMetrics,
  /** Calibration half the window ended in (eyes open first, then eyes closed). */
  val phase: SessionMode = SessionMode.EYES_CLOSED,
  /** True when the whole window lies inside that half, with no pause in it. */
  val pure: Boolean = true,
)

data class ArtefactCalibrationSample(
  val prompt: ArtefactPrompt,
  val features: EegFeatures,
)

/**
 * Clean-baseline calibration: [calibrationSeconds] split into an eyes-open half then an eyes-closed
 * half. Each half yields its own baseline and a session is scored against the half matching its
 * [SessionMode]. During the session the baseline adapts slowly (see [buildAdaptiveCalibration]).
 *
 * [nowMs] must be the same monotonic clock that stamps [EegFeatures.windowStartMs], so windows that
 * straddle the phase change or a pause can be recognised.
 */
class CalibrationManager(
  val calibrationSeconds: Int = CALIBRATION_SECONDS,
  private val pointsPerSecond: Int = 1,
  private val adaptiveWindowSeconds: Int = 1800,
  private val adaptivePriorWindows: Int = 600,
  private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
  private var startedAtMs: Long = UNSET
  private var pausedAtMs: Long = UNSET
  private var pausedAccumMs = 0L
  private var lastResumeMs: Long = UNSET
  private var eyesClosedStartMs: Long = UNSET
  private var finished = false
  private val samples = ArrayList<CalibrationFeatureSample>(calibrationSeconds * pointsPerSecond)

  private val adaptiveCapacity = (adaptiveWindowSeconds * pointsPerSecond).coerceAtLeast(20)
  private val adaptiveSamples = ArrayDeque<CalibrationFeatureSample>(adaptiveCapacity)
  private val artefactSamples = ArrayList<ArtefactCalibrationSample>(ArtefactPrompt.entries.size * 8)

  private val halfMs get() = calibrationSeconds * 1000L / 2

  fun reset() {
    startedAtMs = nowMs()
    pausedAtMs = UNSET
    pausedAccumMs = 0L
    lastResumeMs = startedAtMs
    eyesClosedStartMs = UNSET
    finished = false
    samples.clear()
    adaptiveSamples.clear()
    artefactSamples.clear()
  }

  /** Freezes the calibration timer, e.g. while the session is paused. */
  fun pause() {
    if (startedAtMs == UNSET || pausedAtMs != UNSET) return
    pausedAtMs = nowMs()
  }

  fun resume() {
    if (pausedAtMs == UNSET) return
    val now = nowMs()
    pausedAccumMs += (now - pausedAtMs).coerceAtLeast(0L)
    pausedAtMs = UNSET
    lastResumeMs = now
  }

  private fun activeElapsedMs(): Long {
    if (startedAtMs == UNSET) return 0L
    val reference = if (pausedAtMs != UNSET) pausedAtMs else nowMs()
    return (reference - startedAtMs - pausedAccumMs).coerceAtLeast(0L)
  }

  /** The half of calibration currently running. Eyes open first. */
  fun currentPhase(): SessionMode {
    val active = activeElapsedMs()
    if (active < halfMs) return SessionMode.EYES_OPEN
    if (eyesClosedStartMs == UNSET) {
      val reference = if (pausedAtMs != UNSET) pausedAtMs else nowMs()
      eyesClosedStartMs = reference - (active - halfMs)
    }
    return SessionMode.EYES_CLOSED
  }

  fun addSample(features: EegFeatures, quality: QualityMetrics) {
    if (startedAtMs == UNSET) reset()
    if (finished || isDone()) return
    val phase = currentPhase()
    val phaseStartMs = if (phase == SessionMode.EYES_OPEN) startedAtMs else eyesClosedStartMs
    val pure = features.windowStartMs >= maxOf(phaseStartMs, lastResumeMs)
    samples += CalibrationFeatureSample(features = features, quality = quality, phase = phase, pure = pure)
  }

  fun remainingSeconds(): Int {
    if (startedAtMs == UNSET) return calibrationSeconds
    val elapsed = (activeElapsedMs() / 1000L).toInt()
    return (calibrationSeconds - elapsed).coerceAtLeast(0)
  }

  fun isDone(): Boolean = remainingSeconds() <= 0 && samples.size >= MIN_BASELINE_SAMPLES

  /**
   * Ends the clean-calibration phase, either because the timer ran out or because the user skipped it,
   * and returns the baseline for [mode].
   *
   * It uses the windows recorded in the matching half, preferring those fully inside it. Returns null
   * when that half has too few windows for a trustworthy baseline (e.g. an early skip); the caller then
   * leaves the scorer uncalibrated (neutral z-scores) until [buildAdaptiveCalibration] has enough clean
   * session windows. The other half is never substituted: its eyes state would not match the session.
   */
  fun finish(mode: SessionMode): Calibration? {
    finished = true
    val phaseSamples = samples.filter { it.phase == mode }
    val pureSamples = phaseSamples.filter { it.pure }
    val source = when {
      pureSamples.size >= MIN_BASELINE_SAMPLES -> pureSamples
      phaseSamples.size >= MIN_BASELINE_SAMPLES -> phaseSamples
      else -> return null
    }
    return buildBaseline(preferClean(source))
  }

  /** Clean windows, backfilled with the least-contaminated others up to [MIN_BASELINE_SAMPLES]. */
  private fun preferClean(source: List<CalibrationFeatureSample>): List<CalibrationFeatureSample> {
    val clean = source.filter { it.quality.isCalibrationClean }
    if (clean.size >= MIN_BASELINE_SAMPLES) return clean
    val extrasNeeded = MIN_BASELINE_SAMPLES - clean.size
    return clean + source
      .filterNot { it.quality.isCalibrationClean }
      .sortedWith(sampleComparator())
      .take(extrasNeeded)
  }

  fun addAdaptiveSample(features: EegFeatures, quality: QualityMetrics) {
    if (!finished && !isDone()) return
    if (!quality.isCalibrationClean) return
    if (adaptiveSamples.size >= adaptiveCapacity) {
      adaptiveSamples.removeFirst()
    }
    adaptiveSamples.addLast(CalibrationFeatureSample(features = features, quality = quality))
  }

  /**
   * The baseline to score with right now, or null if there is nothing new to apply.
   *
   * With a calibration [prior], the result blends it with the robust stats of the clean session windows
   * from the last [adaptiveWindowSeconds]: the session's weight is n / (n + [adaptivePriorWindows]), so
   * it reaches 50% after 10 minutes of clean signal and at most 75% at a full 30-minute window. Slow
   * drift (electrode settling, fatigue) is followed while a sustained good state is not quickly
   * re-centred to neutral. Without a prior (calibration skipped) the session windows are used alone.
   */
  fun buildAdaptiveCalibration(prior: Calibration?): Calibration? {
    val count = adaptiveSamples.size
    if (count < MIN_BASELINE_SAMPLES) return null
    val session = buildBaseline(adaptiveSamples.toList())
    if (prior == null) return session
    val weight = count.toFloat() / (count + adaptivePriorWindows).toFloat()
    return prior.blend(session, weight)
  }

  fun adaptiveSampleCount(): Int = adaptiveSamples.size

  fun resetArtefactCapture() {
    artefactSamples.clear()
  }

  fun addArtefactSample(prompt: ArtefactPrompt, features: EegFeatures) {
    artefactSamples += ArtefactCalibrationSample(prompt = prompt, features = features)
  }

  fun buildArtefactCalibrationProfile(): ArtefactCalibrationProfile {
    val eyeMotionBlinkPeak = artefactSamples
      .filter { it.prompt == ArtefactPrompt.LOOK_LEFT_RIGHT || it.prompt == ArtefactPrompt.LOOK_UP_DOWN }
      .maxOfOrNull { it.features.blinkRateHz }
      ?: 0f
    val jawPeak = artefactSamples
      .filter { it.prompt == ArtefactPrompt.JAW_CLENCH }
      .maxOfOrNull { it.features.hfRatio }
      ?: 0f
    val frownPeak = artefactSamples
      .filter { it.prompt == ArtefactPrompt.FROWN }
      .maxOfOrNull { it.features.hfRatio }
      ?: 0f

    return ArtefactCalibrationProfile(
      blinkNormalizationHz = maxOf(1.0f, eyeMotionBlinkPeak * 0.6f),
      emgNormalizationHfRatio = maxOf(0.35f, maxOf(jawPeak, frownPeak) * 0.6f),
      eyeMotionBlinkPeakHz = eyeMotionBlinkPeak,
      jawClenchHfPeakRatio = jawPeak,
      frownHfPeakRatio = frownPeak,
    )
  }

  private fun buildBaseline(source: List<CalibrationFeatureSample>): Calibration {
    return Calibration(
      logBeta = buildStat(source.map { it.features.logBeta }, 0.10f),
      tbr = buildStat(source.map { it.features.tbr }, 0.08f),
      tar = buildStat(source.map { it.features.tar }, 0.08f),
      abr = buildStat(source.map { it.features.abr }, 0.08f),
      entropy = buildStat(source.map { it.features.spectralEntropy }, 0.03f),
      emg = buildStat(source.map { it.features.emg }, 0.08f),
    )
  }

  private fun buildStat(values: List<Float>, floor: Float): RobustBaselineStat {
    if (values.isEmpty()) {
      return RobustBaselineStat(median = 0f, mad = floor, floor = floor)
    }
    val median = median(values)
    val deviations = values.map { kotlin.math.abs(it - median) }
    return RobustBaselineStat(
      median = median,
      mad = median(deviations).coerceAtLeast(floor),
      floor = floor,
    )
  }

  private fun median(values: List<Float>): Float {
    if (values.isEmpty()) return 0f
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 0) {
      (sorted[middle - 1] + sorted[middle]) / 2f
    } else {
      sorted[middle]
    }
  }

  private fun sampleComparator(): Comparator<CalibrationFeatureSample> {
    return compareBy<CalibrationFeatureSample>(
      { it.quality.artefactScore },
      { it.quality.poorSignal },
      { it.features.blinkRateHz },
      { it.features.hfRatio },
      { it.features.clipFraction },
      { it.features.signalGapMs },
    )
  }

  private companion object {
    const val UNSET = Long.MIN_VALUE
    const val MIN_BASELINE_SAMPLES = 20
  }
}
