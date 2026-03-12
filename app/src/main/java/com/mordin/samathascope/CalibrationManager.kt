package com.mordin.samathascope

data class RobustBaselineStat(
  val median: Float,
  val mad: Float,
  val floor: Float,
) {
  fun z(value: Float): Float {
    val denominator = maxOf(mad, floor)
    return (0.6745f * (value - median) / denominator).coerceIn(-4f, 4f)
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
}

data class CalibrationFeatureSample(
  val features: EegFeatures,
  val quality: QualityMetrics,
)

class CalibrationManager(
  val calibrationSeconds: Int = 60,
  private val pointsPerSecond: Int = 1,
  private val adaptiveWindowSeconds: Int = 600,
  private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
  private var startedAtMs: Long = 0L
  private val samples = ArrayList<CalibrationFeatureSample>(calibrationSeconds * pointsPerSecond)
  private var selectedCalibrationSamples: List<CalibrationFeatureSample> = emptyList()

  private val adaptiveCapacity = (adaptiveWindowSeconds * pointsPerSecond).coerceAtLeast(20)
  private val adaptiveSamples = ArrayDeque<CalibrationFeatureSample>(adaptiveCapacity)

  fun reset() {
    startedAtMs = nowMs()
    samples.clear()
    selectedCalibrationSamples = emptyList()
    adaptiveSamples.clear()
  }

  fun addSample(features: EegFeatures, quality: QualityMetrics) {
    if (startedAtMs == 0L) startedAtMs = nowMs()
    if (isDone()) return
    samples += CalibrationFeatureSample(features = features, quality = quality)
  }

  fun remainingSeconds(): Int {
    if (startedAtMs == 0L) return calibrationSeconds
    val elapsed = ((nowMs() - startedAtMs) / 1000L).toInt()
    return (calibrationSeconds - elapsed).coerceAtLeast(0)
  }

  fun isDone(): Boolean = remainingSeconds() <= 0 && samples.size >= 20

  fun buildCalibration(): Calibration {
    val preferred = samples.filter { it.quality.isCalibrationClean }
    selectedCalibrationSamples = if (preferred.size >= 20) {
      preferred
    } else {
      val extrasNeeded = (20 - preferred.size).coerceAtLeast(0)
      preferred + samples
        .filterNot { it.quality.isCalibrationClean }
        .sortedWith(sampleComparator())
        .take(extrasNeeded)
    }.ifEmpty {
      samples.sortedWith(sampleComparator()).take(20.coerceAtMost(samples.size))
    }
    return buildBaseline(selectedCalibrationSamples)
  }

  fun seedAdaptiveWindowFromCalibration() {
    if (adaptiveSamples.isNotEmpty()) return
    val seed = if (selectedCalibrationSamples.isNotEmpty()) selectedCalibrationSamples else samples.filter { it.quality.isCalibrationClean }
    for (sample in seed) {
      pushAdaptive(sample)
    }
  }

  fun addAdaptiveSample(features: EegFeatures, quality: QualityMetrics) {
    if (!isDone()) return
    if (!quality.isCalibrationClean) return
    pushAdaptive(CalibrationFeatureSample(features = features, quality = quality))
  }

  fun buildAdaptiveCalibration(): Calibration? {
    if (adaptiveSamples.size < 20) return null
    return buildBaseline(adaptiveSamples.toList())
  }

  fun adaptiveSampleCount(): Int = adaptiveSamples.size

  private fun pushAdaptive(sample: CalibrationFeatureSample) {
    if (adaptiveSamples.size >= adaptiveCapacity) {
      adaptiveSamples.removeFirst()
    }
    adaptiveSamples.addLast(sample)
  }

  private fun buildBaseline(source: List<CalibrationFeatureSample>): Calibration {
    val safeSource = if (source.isEmpty()) samples else source
    return Calibration(
      logBeta = buildStat(safeSource.map { it.features.logBeta }, 0.10f),
      tbr = buildStat(safeSource.map { it.features.tbr }, 0.08f),
      tar = buildStat(safeSource.map { it.features.tar }, 0.08f),
      abr = buildStat(safeSource.map { it.features.abr }, 0.08f),
      entropy = buildStat(safeSource.map { it.features.spectralEntropy }, 0.03f),
      emg = buildStat(safeSource.map { it.features.emg }, 0.08f),
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
      { it.features.maxGapMs },
    )
  }
}
