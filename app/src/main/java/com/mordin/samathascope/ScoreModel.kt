package com.mordin.samathascope

import kotlin.math.exp

enum class StateLabel {
  SIGNAL_CONTAMINATED,
  DROWSY,
  SETTLED,
  EFFORTFUL_FOCUS,
  MIND_WANDERING,
  UNCERTAIN,
}

data class QualityMetrics(
  val poorSignal: Int,
  val contact: Float,
  val lineNoise: Float,
  val emg: Float,
  val blink: Float,
  val clip: Float,
  val stall: Float,
  val artefactScore: Float,
  val qualityConfidence: Float,
  val hfRatio: Float,
  val blinkRateHz: Float,
  val clipFraction: Float,
  val maxGapMs: Long,
  val isContaminated: Boolean,
  val isCalibrationClean: Boolean,
)

data class ArtefactCalibrationProfile(
  val blinkNormalizationHz: Float = 1.0f,
  val emgNormalizationHfRatio: Float = 0.35f,
  val eyeMotionBlinkPeakHz: Float = 0f,
  val jawClenchHfPeakRatio: Float = 0f,
  val frownHfPeakRatio: Float = 0f,
)

data class FeatureZScores(
  val logBeta: Float,
  val tbr: Float,
  val tar: Float,
  val abr: Float,
  val entropy: Float,
  val emg: Float,
) {
  companion object {
    fun zero(): FeatureZScores = FeatureZScores(0f, 0f, 0f, 0f, 0f, 0f)
  }
}

data class StateProbabilities(
  val contaminated: Float,
  val drowsy: Float,
  val settled: Float,
  val effortfulFocus: Float,
  val mindWandering: Float,
  val uncertain: Float,
) {
  fun valueFor(label: StateLabel): Float {
    return when (label) {
      StateLabel.SIGNAL_CONTAMINATED -> contaminated
      StateLabel.DROWSY -> drowsy
      StateLabel.SETTLED -> settled
      StateLabel.EFFORTFUL_FOCUS -> effortfulFocus
      StateLabel.MIND_WANDERING -> mindWandering
      StateLabel.UNCERTAIN -> uncertain
    }
  }
}

data class ClassifierOutput(
  val rawStateLabel: StateLabel,
  val quality: QualityMetrics,
  val zScores: FeatureZScores,
  val probabilities: StateProbabilities,
  val drowsinessEvidence: DrowsinessEvidence,
  val drowsyScore: Float,
  val settledScore: Float,
  val effortfulFocusScore: Float,
  val mindWanderingScore: Float,
  val alertness: Float,
  val control: Float,
  val settledness: Float,
  val qualityConfidence: Float,
  val meditationProxy: Float,
)

data class DrowsinessEvidence(
  val tarContribution: Float,
  val tbrContribution: Float,
  val entropyContribution: Float,
  val abrContribution: Float,
  val logit: Float,
)

class ScoreModel {
  private var calibration: Calibration? = null
  private var artefactCalibrationProfile: ArtefactCalibrationProfile = ArtefactCalibrationProfile()

  fun reset() {
    calibration = null
    artefactCalibrationProfile = ArtefactCalibrationProfile()
  }

  fun setCalibration(calibration: Calibration) {
    this.calibration = calibration
  }

  fun setArtefactCalibrationProfile(profile: ArtefactCalibrationProfile) {
    artefactCalibrationProfile = profile
  }

  fun quality(poorSignal: Int, features: EegFeatures): QualityMetrics {
    val contact = clamp01(poorSignal / 50f)
    val line = clamp01(features.lineNoiseRatio * 5f)
    val personalizedEmgUpper = maxOf(0.35f, artefactCalibrationProfile.emgNormalizationHfRatio)
    val personalizedEmgSpan = (personalizedEmgUpper - 0.10f).coerceAtLeast(0.15f)
    val personalizedBlinkUpper = maxOf(1.0f, artefactCalibrationProfile.blinkNormalizationHz)
    val emg = clamp01((features.hfRatio - 0.10f) / personalizedEmgSpan)
    val blink = clamp01(features.blinkRateHz / personalizedBlinkUpper)
    val clip = clamp01(features.clipFraction / 0.01f)
    val stall = clamp01(features.maxGapMs / 500f)
    val artefactScore = clamp01(
      (0.30f * contact) +
        (0.10f * line) +
        (0.25f * emg) +
        (0.16f * blink) +
        (0.12f * clip) +
        (0.10f * stall)
    )
    val isContaminated = poorSignal > 25 ||
      features.clipFraction >= 0.01f ||
      features.maxGapMs >= 150L ||
      features.blinkRateHz >= 0.75f ||
      features.hfRatio >= 0.35f ||
      artefactScore > 0.45f
    val isCalibrationClean = !isContaminated &&
      poorSignal <= 25 &&
      features.hfRatio < 0.25f &&
      features.blinkRateHz < 0.30f &&
      features.clipFraction < 0.005f &&
      features.maxGapMs < 100L &&
      artefactScore <= 0.30f

    return QualityMetrics(
      poorSignal = poorSignal,
      contact = contact,
      lineNoise = line,
      emg = emg,
      blink = blink,
      clip = clip,
      stall = stall,
      artefactScore = artefactScore,
      qualityConfidence = clamp01(1f - artefactScore),
      hfRatio = features.hfRatio,
      blinkRateHz = features.blinkRateHz,
      clipFraction = features.clipFraction,
      maxGapMs = features.maxGapMs,
      isContaminated = isContaminated,
      isCalibrationClean = isCalibrationClean,
    )
  }

  fun zScores(features: EegFeatures): FeatureZScores {
    return calibration?.zScores(features) ?: FeatureZScores.zero()
  }

  fun classify(poorSignal: Int, features: EegFeatures): ClassifierOutput {
    val quality = quality(poorSignal = poorSignal, features = features)
    val z = zScores(features)

    val tarContribution = 1.3f * z.tar
    val tbrContribution = 0.9f * z.tbr
    val entropyContribution = -0.7f * z.entropy
    val abrContribution = -0.4f * z.abr
    val drowsyLogit = tarContribution + tbrContribution + entropyContribution + abrContribution
    val d = sigmoid(drowsyLogit)
    val m = sigmoid((1.0f * z.abr) - (0.6f * z.tar) + (0.4f * z.entropy) - (0.3f * z.emg))
    val f = sigmoid((-0.9f * z.abr) - (0.7f * z.tbr) + (0.4f * z.logBeta) - (0.2f * z.entropy))
    val w = sigmoid((0.9f * z.tbr) - (0.4f * z.abr) - (0.2f * z.entropy))

    val cleanProbabilities = StateProbabilities(
      contaminated = if (quality.isContaminated) 1f else quality.artefactScore,
      drowsy = if (quality.isContaminated) 0f else d,
      settled = if (quality.isContaminated || d > 0.65f) 0f else m,
      effortfulFocus = if (quality.isContaminated || d > 0.65f) 0f else f,
      mindWandering = if (quality.isContaminated || d > 0.65f) 0f else w,
      uncertain = if (quality.isContaminated || d > 0.65f) 0f else 1f - ((maxOf(m, f, w) / 0.55f).coerceIn(0f, 1f)),
    )

    val rawStateLabel = when {
      quality.isContaminated -> StateLabel.SIGNAL_CONTAMINATED
      d > 0.65f -> StateLabel.DROWSY
      m >= f && m >= w && m > 0.55f -> StateLabel.SETTLED
      f >= m && f >= w && f > 0.55f -> StateLabel.EFFORTFUL_FOCUS
      w > 0.55f -> StateLabel.MIND_WANDERING
      else -> StateLabel.UNCERTAIN
    }

    val alertness = clamp01(1f - d)
    val control = sigmoid(-z.tbr)
    // Settledness aims for relaxed-but-awake balance rather than theta-heavy drowsiness.
    val settledness = sigmoid(z.abr - (0.5f * z.tar))
    val meditationProxy = clamp01(settledness * alertness * quality.qualityConfidence)

    return ClassifierOutput(
      rawStateLabel = rawStateLabel,
      quality = quality,
      zScores = z,
      probabilities = cleanProbabilities,
      drowsinessEvidence = DrowsinessEvidence(
        tarContribution = tarContribution,
        tbrContribution = tbrContribution,
        entropyContribution = entropyContribution,
        abrContribution = abrContribution,
        logit = drowsyLogit,
      ),
      drowsyScore = d,
      settledScore = m,
      effortfulFocusScore = f,
      mindWanderingScore = w,
      alertness = alertness,
      control = control,
      settledness = settledness,
      qualityConfidence = quality.qualityConfidence,
      meditationProxy = meditationProxy,
    )
  }

  private fun clamp01(value: Float): Float = value.coerceIn(0f, 1f)

  private fun sigmoid(x: Float): Float {
    return (1f / (1f + exp(-x.toDouble()))).toFloat()
  }
}
