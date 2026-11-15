package com.mordin.samathascope

import kotlin.math.min

/** Classifier output after smoothing, plus the state label the dashboard should show. */
internal data class SmoothedOutput(
  val probabilities: StateProbabilities,
  val alertness: Float,
  val control: Float,
  val settledness: Float,
  val artefactScore: Float,
  val qualityConfidence: Float,
  val meditationProxy: Float,
  val displayedDrowsyScore: Float,
  val displayedState: StateLabel,
)

/**
 * Smooths the per-window classifier output (one update per second) and decides the displayed state.
 * Drowsiness is shown more cautiously than the raw classifier: it is capped during clean, settled
 * windows and the DROWSY label needs either a strong reading or several drowsy windows in a row.
 */
internal class OutputSmoother {
  private val contaminated = ExponentialSmoother(alpha = BASE_ALPHA)
  private val drowsy = ExponentialSmoother(alpha = BASE_ALPHA)
  private val settled = ExponentialSmoother(alpha = BASE_ALPHA)
  private val effortfulFocus = ExponentialSmoother(alpha = BASE_ALPHA)
  private val mindWandering = ExponentialSmoother(alpha = BASE_ALPHA)
  private val uncertain = ExponentialSmoother(alpha = BASE_ALPHA)
  private val alertness = ExponentialSmoother(alpha = BASE_ALPHA)
  private val control = ExponentialSmoother(alpha = BASE_ALPHA)
  private val settledness = ExponentialSmoother(alpha = BASE_ALPHA)
  private val artefact = ExponentialSmoother(alpha = BASE_ALPHA)
  private val qualityConfidence = ExponentialSmoother(alpha = BASE_ALPHA)
  private val meditationProxy = ExponentialSmoother(alpha = BASE_ALPHA)
  private val displayedDrowsy = ExponentialSmoother(alpha = DISPLAYED_DROWSY_ALPHA)
  private val stateHold = StateHoldSmoother()
  private var drowsyWins = 0

  fun reset() {
    listOf(
      contaminated, drowsy, settled, effortfulFocus, mindWandering, uncertain,
      alertness, control, settledness, artefact, qualityConfidence, meditationProxy, displayedDrowsy,
    ).forEach { it.reset() }
    stateHold.reset()
    drowsyWins = 0
  }

  fun update(output: ClassifierOutput): SmoothedOutput {
    val probabilities = StateProbabilities(
      contaminated = contaminated.add(output.probabilities.contaminated),
      drowsy = drowsy.add(output.probabilities.drowsy),
      settled = settled.add(output.probabilities.settled),
      effortfulFocus = effortfulFocus.add(output.probabilities.effortfulFocus),
      mindWandering = mindWandering.add(output.probabilities.mindWandering),
      uncertain = uncertain.add(output.probabilities.uncertain),
    )
    val gatedState = gatedDisplayState(output)
    return SmoothedOutput(
      probabilities = probabilities,
      alertness = alertness.add(output.alertness),
      control = control.add(output.control),
      settledness = settledness.add(output.settledness),
      artefactScore = artefact.add(output.quality.artefactScore),
      qualityConfidence = qualityConfidence.add(output.qualityConfidence),
      meditationProxy = meditationProxy.add(output.meditationProxy),
      displayedDrowsyScore = displayedDrowsy.add(cappedDisplayedDrowsy(output)),
      displayedState = stateHold.update(candidate = gatedState, confidence = probabilities.valueFor(gatedState)),
    )
  }

  private fun cappedDisplayedDrowsy(output: ClassifierOutput): Float {
    val entropyNotSuppressed = output.zScores.entropy > -0.30f
    val calmSettledWindow = !output.quality.isContaminated &&
      output.settledness > 0.60f &&
      output.qualityConfidence > 0.70f &&
      entropyNotSuppressed
    return if (calmSettledWindow) min(output.drowsyScore, CALM_DROWSY_CAP) else output.drowsyScore
  }

  private fun gatedDisplayState(output: ClassifierOutput): StateLabel {
    if (output.rawStateLabel != StateLabel.DROWSY) {
      drowsyWins = 0
      return output.rawStateLabel
    }
    if (output.quality.isContaminated) {
      drowsyWins = 0
      return StateLabel.SIGNAL_CONTAMINATED
    }
    drowsyWins++
    return if (output.drowsyScore > STRONG_DROWSY || drowsyWins >= DROWSY_WINS_REQUIRED) {
      StateLabel.DROWSY
    } else {
      StateLabel.UNCERTAIN
    }
  }

  private companion object {
    const val BASE_ALPHA = 0.3f
    const val DISPLAYED_DROWSY_ALPHA = 0.15f
    const val CALM_DROWSY_CAP = 0.45f
    const val STRONG_DROWSY = 0.80f
    const val DROWSY_WINS_REQUIRED = 5
  }
}
