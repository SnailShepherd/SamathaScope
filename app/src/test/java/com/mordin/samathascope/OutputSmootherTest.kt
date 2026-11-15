package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OutputSmootherTest {

  @Test
  fun firstUpdate_passesValuesThroughThenSmooths() {
    val smoother = OutputSmoother()

    val first = smoother.update(output(settledness = 0.2f))
    val second = smoother.update(output(settledness = 1.0f))

    assertThat(first.settledness).isWithin(1e-6f).of(0.2f)
    // alpha 0.3: 0.3 * 1.0 + 0.7 * 0.2
    assertThat(second.settledness).isWithin(1e-6f).of(0.44f)
  }

  @Test
  fun drowsyLabel_needsFiveDrowsyWindowsUnlessStrong() {
    val smoother = OutputSmoother()
    val shown = (1..5).map { smoother.update(output(label = StateLabel.DROWSY, drowsy = 0.70f)).displayedState }

    assertThat(shown.take(4)).doesNotContain(StateLabel.DROWSY)
    assertThat(shown.last()).isEqualTo(StateLabel.DROWSY)
  }

  @Test
  fun strongDrowsiness_isShownAtOnce() {
    val smoother = OutputSmoother()

    val shown = smoother.update(output(label = StateLabel.DROWSY, drowsy = 0.95f, drowsyProbability = 0.95f))

    assertThat(shown.displayedState).isEqualTo(StateLabel.DROWSY)
  }

  @Test
  fun displayedDrowsiness_isCappedInCalmSettledWindows() {
    val smoother = OutputSmoother()

    val calm = smoother.update(output(drowsy = 0.9f, settledness = 0.8f, qualityConfidence = 0.9f))

    assertThat(calm.displayedDrowsyScore).isWithin(1e-6f).of(0.45f)
  }

  @Test
  fun reset_forgetsHistory() {
    val smoother = OutputSmoother()
    repeat(4) { smoother.update(output(label = StateLabel.DROWSY, drowsy = 0.70f, settledness = 0.1f)) }

    smoother.reset()
    val afterReset = smoother.update(output(label = StateLabel.DROWSY, drowsy = 0.70f, settledness = 0.9f))

    assertThat(afterReset.displayedState).isNotEqualTo(StateLabel.DROWSY)
    assertThat(afterReset.settledness).isWithin(1e-6f).of(0.9f)
  }

  private fun output(
    label: StateLabel = StateLabel.SETTLED,
    drowsy: Float = 0.2f,
    drowsyProbability: Float = drowsy,
    settledness: Float = 0.5f,
    qualityConfidence: Float = 0.9f,
  ): ClassifierOutput {
    val quality = QualityMetrics(
      poorSignal = 0, contact = 0f, lineNoise = 0f, emg = 0f, blink = 0f, clip = 0f, stall = 0f,
      artefactScore = 1f - qualityConfidence, qualityConfidence = qualityConfidence, hfRatio = 0.1f,
      blinkRateHz = 0f, clipFraction = 0f, signalGapMs = 0L, isContaminated = false, isCalibrationClean = true,
    )
    return ClassifierOutput(
      rawStateLabel = label,
      quality = quality,
      zScores = FeatureZScores.zero(),
      probabilities = StateProbabilities(0f, drowsyProbability, 0.5f, 0.1f, 0.1f, 0.1f),
      drowsinessEvidence = DrowsinessEvidence(0f, 0f, 0f, 0f, 0f),
      drowsyScore = drowsy,
      settledScore = 0.5f,
      effortfulFocusScore = 0.1f,
      mindWanderingScore = 0.1f,
      alertness = 1f - drowsy,
      control = 0.5f,
      settledness = settledness,
      qualityConfidence = qualityConfidence,
      meditationProxy = 0.5f,
    )
  }
}
