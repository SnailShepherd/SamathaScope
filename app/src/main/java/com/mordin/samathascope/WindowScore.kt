package com.mordin.samathascope

// What one scored analysis window turns into: session statistics, plot history, the feedback value,
// the recorded CSV row and the dashboard state. Pure functions, so the ViewModel only sequences them.

/** Running session summary: the average Meditation Proxy and the time spent at or above 80%. */
internal class SessionStats {
  private var sum = 0f
  private var count = 0
  private var timeOver80Ms = 0L
  private var lastWindowAtMs = NO_WINDOW

  val averageMeditationProxy: Float get() = if (count == 0) 0f else sum / count
  val secondsOver80: Int get() = (timeOver80Ms / 1000L).toInt()

  fun reset() {
    sum = 0f
    count = 0
    timeOver80Ms = 0L
    lastWindowAtMs = NO_WINDOW
  }

  /** Call on resume: the paused stretch must not count towards the time over 80%. */
  fun markGap() {
    lastWindowAtMs = NO_WINDOW
  }

  fun add(meditationProxy: Float, atMs: Long) {
    sum += meditationProxy
    count++
    if (lastWindowAtMs != NO_WINDOW && meditationProxy >= OVER_THRESHOLD) {
      timeOver80Ms += (atMs - lastWindowAtMs).coerceAtLeast(0L)
    }
    lastWindowAtMs = atMs
  }

  private companion object {
    const val NO_WINDOW = Long.MIN_VALUE
    const val OVER_THRESHOLD = 0.80f
  }
}

/** The value each plotted metric gets for this window (0..1, eSense 0..100). */
internal fun SmoothedOutput.metricHistorySample(eSenseMeditation: Int, eSenseAttention: Int): Map<PlotType, Float> =
  mapOf(
    PlotType.MEDITATION_PROXY to meditationProxy,
    PlotType.SETTLEDNESS to settledness,
    PlotType.CONTROL to control,
    PlotType.ALERTNESS to alertness,
    PlotType.DROWSY_SCORE to displayedDrowsyScore,
    PlotType.ARTEFACT_SCORE to artefactScore,
    PlotType.QUALITY_CONFIDENCE to qualityConfidence,
    PlotType.EFFORTFUL_FOCUS_SCORE to probabilities.effortfulFocus,
    PlotType.MIND_WANDERING_SCORE to probabilities.mindWandering,
    PlotType.ESENSE_MEDITATION to eSenseMeditation.toFloat(),
    PlotType.ESENSE_ATTENTION to eSenseAttention.toFloat(),
  )

/** The audio feedback value for the chosen source; 0 for metrics that aren't feedback sources. */
internal fun SmoothedOutput.feedbackValue(source: PlotType): Float = when (source) {
  PlotType.MEDITATION_PROXY -> meditationProxy
  PlotType.SETTLEDNESS -> settledness
  PlotType.ALERTNESS -> alertness
  else -> 0f
}

/**
 * The features.csv row for this window. Times arrive on the monotonic clock; [wallClockOffsetMs]
 * turns them into wall-clock times for the recording.
 */
internal fun recordedFeatureRow(
  features: EegFeatures,
  output: ClassifierOutput,
  smoothed: SmoothedOutput,
  atMs: Long,
  wallClockOffsetMs: Long,
  poorSignal: Int,
  state: UiState,
  artefactProfile: ArtefactCalibrationProfile,
  feedbackValue: Float,
) = RecordedFeatureRow(
  timestampMs = atMs + wallClockOffsetMs,
  poorSignal = poorSignal,
  notch50Enabled = state.notch50Enabled,
  features = features.copy(
    windowStartMs = features.windowStartMs + wallClockOffsetMs,
    windowEndMs = features.windowEndMs + wallClockOffsetMs,
  ),
  zScores = output.zScores,
  quality = output.quality,
  artefactCalibrationProfile = artefactProfile,
  rawProbabilities = output.probabilities,
  smoothedProbabilities = smoothed.probabilities,
  rawStateLabel = output.rawStateLabel,
  displayedStateLabel = smoothed.displayedState,
  drowsinessEvidence = output.drowsinessEvidence,
  displayedDrowsyScore = smoothed.displayedDrowsyScore,
  alertness = smoothed.alertness,
  control = smoothed.control,
  settledness = smoothed.settledness,
  meditationProxy = smoothed.meditationProxy,
  feedbackMetric = state.feedbackMetric,
  feedbackValue = feedbackValue,
)

/** The dashboard state after scoring one window. */
internal fun UiState.withWindowScore(
  output: ClassifierOutput,
  smoothed: SmoothedOutput,
  stats: SessionStats,
  artefactProfile: ArtefactCalibrationProfile,
  audioAudible: Boolean,
  audioBaseDb: Float?,
): UiState = copy(
  meditationProxy = smoothed.meditationProxy,
  settledness = smoothed.settledness,
  control = smoothed.control,
  alertness = smoothed.alertness,
  drowsyScore = smoothed.displayedDrowsyScore,
  artefactScore = smoothed.artefactScore,
  qualityConfidence = smoothed.qualityConfidence,
  effortfulFocusScore = smoothed.probabilities.effortfulFocus,
  mindWanderingScore = smoothed.probabilities.mindWandering,
  displayedStateLabel = smoothed.displayedState,
  drowsyTarContribution = output.drowsinessEvidence.tarContribution,
  drowsyTbrContribution = output.drowsinessEvidence.tbrContribution,
  drowsyEntropyContribution = output.drowsinessEvidence.entropyContribution,
  drowsyAbrContribution = output.drowsinessEvidence.abrContribution,
  avgMeditationProxy = stats.averageMeditationProxy,
  timeMeditationProxyOver80Seconds = stats.secondsOver80,
  artefactContact = output.quality.contact,
  artefactLine = output.quality.lineNoise,
  artefactEmg = output.quality.emg,
  artefactBlink = output.quality.blink,
  artefactClip = output.quality.clip,
  artefactStall = output.quality.stall,
  artefactBlinkNormalizationHz = artefactProfile.blinkNormalizationHz,
  artefactEmgNormalizationHfRatio = artefactProfile.emgNormalizationHfRatio,
  audioRunning = audioAudible,
  audioMuted = !audioAudible,
  audioBaseDb = audioBaseDb ?: this.audioBaseDb,
)
