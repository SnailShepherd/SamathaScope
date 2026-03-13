package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GameSignalMapperTest {

  @Test
  fun mapper_buildsStableSignalsFromClassifierOutputs() {
    val mapper = GameSignalMapper()

    mapper.updateFromClassifier(
      settledness = 0.80f,
      qualityConfidence = 0.90f,
      mindWandering = 0.20f,
      artefactScore = 0.10f,
      displayedDrowsyScore = 0.20f,
      control = 0.70f,
      effortfulFocus = 0.25f,
      timestampMs = 1_000L,
    )

    val snapshot = mapper.snapshot(
      nowMs = 1_000L,
      stateLabel = StateLabel.SETTLED,
      poorSignal = 5,
      elapsedSeconds = 12,
      batteryPercent = 66,
    )

    assertThat(snapshot.stability).isWithin(1e-6f).of(0.72f)
    assertThat(snapshot.drift).isWithin(1e-6f).of(0.18f)
    assertThat(snapshot.noise).isWithin(1e-6f).of(0.10f)
    assertThat(snapshot.fatigue).isEqualTo(0f)
    assertThat(snapshot.precision).isWithin(1e-6f).of(0.63f)
    assertThat(snapshot.stateLabel).isEqualTo(StateLabel.SETTLED)
  }

  @Test
  fun mapper_triggersAndDecaysCorrectionPulse() {
    val mapper = GameSignalMapper()

    mapper.updateFromClassifier(
      settledness = 0.4f,
      qualityConfidence = 0.8f,
      mindWandering = 0.3f,
      artefactScore = 0.2f,
      displayedDrowsyScore = 0.2f,
      control = 0.5f,
      effortfulFocus = 0.20f,
      timestampMs = 1_000L,
    )
    mapper.updateFromClassifier(
      settledness = 0.4f,
      qualityConfidence = 0.8f,
      mindWandering = 0.3f,
      artefactScore = 0.2f,
      displayedDrowsyScore = 0.2f,
      control = 0.5f,
      effortfulFocus = 0.62f,
      timestampMs = 2_000L,
    )

    val peak = mapper.snapshot(2_000L, StateLabel.EFFORTFUL_FOCUS, 10, 8, null)
    val decayed = mapper.snapshot(3_200L, StateLabel.EFFORTFUL_FOCUS, 10, 9, null)

    assertThat(peak.correctionPulse).isWithin(1e-6f).of(1f)
    assertThat(decayed.correctionPulse).isLessThan(0.3f)
  }

  @Test
  fun mapper_appliesSlowFatigueGate() {
    val mapper = GameSignalMapper()

    repeat(4) { index ->
      mapper.updateFromClassifier(
        settledness = 0.3f,
        qualityConfidence = 0.8f,
        mindWandering = 0.4f,
        artefactScore = 0.1f,
        displayedDrowsyScore = 0.85f,
        control = 0.4f,
        effortfulFocus = 0.2f,
        timestampMs = 1_000L + (index * 1_000L),
      )
    }

    val snapshot = mapper.snapshot(5_000L, StateLabel.DROWSY, 12, 20, null)

    assertThat(snapshot.fatigue).isGreaterThan(0.2f)
  }
}
