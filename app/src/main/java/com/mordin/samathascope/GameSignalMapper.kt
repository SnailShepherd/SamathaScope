package com.mordin.samathascope

class GameSignalMapper {
  private val fatigueSmoother = ExponentialSmoother(alpha = 0.15f)

  private var stability = 0f
  private var drift = 0f
  private var noise = 0f
  private var fatigue = 0f
  private var precision = 0f
  private var lastEffortfulFocus = 0f
  private var pulseActivatedAtMs = Long.MIN_VALUE

  fun reset() {
    fatigueSmoother.reset()
    stability = 0f
    drift = 0f
    noise = 0f
    fatigue = 0f
    precision = 0f
    lastEffortfulFocus = 0f
    pulseActivatedAtMs = Long.MIN_VALUE
  }

  fun updateFromClassifier(
    settledness: Float,
    qualityConfidence: Float,
    mindWandering: Float,
    artefactScore: Float,
    displayedDrowsyScore: Float,
    control: Float,
    effortfulFocus: Float,
    timestampMs: Long,
  ) {
    stability = clamp01(settledness * qualityConfidence)
    drift = clamp01(mindWandering * qualityConfidence)
    noise = clamp01(artefactScore)
    precision = clamp01(control * qualityConfidence)
    val fatigueGate = ((displayedDrowsyScore - 0.60f) / 0.40f).coerceIn(0f, 1f)
    fatigue = fatigueSmoother.add(fatigueGate)
    val effortfulRise = effortfulFocus - lastEffortfulFocus
    val crossedThreshold = lastEffortfulFocus < 0.55f && effortfulFocus >= 0.55f
    if (crossedThreshold || effortfulRise >= 0.08f) {
      pulseActivatedAtMs = timestampMs
    }
    lastEffortfulFocus = effortfulFocus
  }

  fun snapshot(
    nowMs: Long,
    stateLabel: StateLabel,
    poorSignal: Int,
    elapsedSeconds: Int,
    batteryPercent: Int?,
  ): GameSignalSnapshot {
    val correctionPulse = if (pulseActivatedAtMs == Long.MIN_VALUE) {
      0f
    } else {
      (1f - ((nowMs - pulseActivatedAtMs).coerceAtLeast(0L) / 1500f)).coerceIn(0f, 1f)
    }
    return GameSignalSnapshot(
      stability = stability,
      drift = drift,
      noise = noise,
      fatigue = fatigue,
      precision = precision,
      correctionPulse = correctionPulse,
      stateLabel = stateLabel,
      poorSignal = poorSignal,
      elapsedSeconds = elapsedSeconds,
      batteryPercent = batteryPercent,
    )
  }
}
