package com.mordin.samathascope.scene

import com.mordin.samathascope.ExponentialSmoother
import com.mordin.samathascope.approach
import com.mordin.samathascope.clamp01
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

class SceneStateProducer(
  private val cadenceMs: Long = 50L,
) {
  private val calmnessTargetSmoother = ExponentialSmoother(alpha = 0.20f)
  private val focusTargetSmoother = ExponentialSmoother(alpha = 0.22f)
  private val stabilityTargetSmoother = ExponentialSmoother(alpha = 0.18f)
  private val artefactTargetSmoother = ExponentialSmoother(alpha = 0.24f)
  private val intensityTargetSmoother = ExponentialSmoother(alpha = 0.26f)
  private val driftTargetSmoother = ExponentialSmoother(alpha = 0.16f)

  private val _state = MutableStateFlow(SceneState())
  val state: StateFlow<SceneState> = _state

  private var active = false
  private var frozen = false
  private var lastTickMs = 0L
  private var activeElapsedMs = 0L
  private var driftPhase = 0.0
  private var targetCalmness = 0f
  private var targetFocus = 0f
  private var targetStability = 0f
  private var targetArtefact = 0f
  private var targetIntensity = 0f
  private var targetDriftMagnitude = 0f
  private var lastEffortfulFocus = 0f
  private var pulseActivatedAtMs = Long.MIN_VALUE
  private var lastSettledness = 0f
  private var lastControl = 0f
  private var lastMindWandering = 0f

  fun reset() {
    calmnessTargetSmoother.reset()
    focusTargetSmoother.reset()
    stabilityTargetSmoother.reset()
    artefactTargetSmoother.reset()
    intensityTargetSmoother.reset()
    driftTargetSmoother.reset()
    _state.value = SceneState()
    active = false
    frozen = false
    lastTickMs = 0L
    activeElapsedMs = 0L
    driftPhase = 0.0
    targetCalmness = 0f
    targetFocus = 0f
    targetStability = 0f
    targetArtefact = 0f
    targetIntensity = 0f
    targetDriftMagnitude = 0f
    lastEffortfulFocus = 0f
    pulseActivatedAtMs = Long.MIN_VALUE
    lastSettledness = 0f
    lastControl = 0f
    lastMindWandering = 0f
  }

  fun restartScene(nowMs: Long) {
    activeElapsedMs = 0L
    lastTickMs = nowMs
    _state.value = _state.value.copy(
      progress = 0f,
      calmnessRate = 0f,
      focusRate = 0f,
      intensityRate = 0f,
    )
  }

  fun setPlayback(active: Boolean, frozen: Boolean) {
    this.active = active
    this.frozen = frozen
  }

  fun updateInputs(inputs: SceneSignalInputs) {
    val effortfulRise = inputs.effortfulFocus - lastEffortfulFocus
    val crossedThreshold = lastEffortfulFocus < 0.55f && inputs.effortfulFocus >= 0.55f
    if (crossedThreshold || effortfulRise >= 0.08f) {
      pulseActivatedAtMs = inputs.timestampMs
    }
    lastEffortfulFocus = inputs.effortfulFocus

    val pulse = correctionPulse(inputs.timestampMs)
    val jitter = (
      abs(inputs.settledness - lastSettledness) +
        abs(inputs.control - lastControl) +
        abs(inputs.mindWandering - lastMindWandering)
      ) / 3f
    val consistency = clamp01(1f - (jitter * 1.6f))

    val calmnessTarget = clamp01(
      (inputs.settledness * 0.62f) +
        (inputs.alertness * 0.20f) +
        (inputs.qualityConfidence * 0.18f) -
        (inputs.displayedDrowsyScore * 0.12f)
    )
    val focusTarget = clamp01(
      (inputs.control * inputs.qualityConfidence * 0.78f) +
        (pulse * 0.22f)
    )
    val stabilityTarget = clamp01(
      (inputs.qualityConfidence * 0.58f) +
        (consistency * 0.27f) +
        (inputs.settledness * 0.15f)
    )
    val artefactTarget = clamp01(inputs.artefactScore)
    val intensityTarget = clamp01(
      (pulse * 0.42f) +
        (focusTarget * 0.24f) +
        (inputs.alertness * 0.18f) +
        (inputs.mindWandering * 0.10f) +
        (artefactTarget * 0.08f) +
        ((1f - inputs.displayedDrowsyScore) * 0.06f)
    )
    val driftMagnitude = clamp01(inputs.mindWandering * inputs.qualityConfidence)

    lastSettledness = inputs.settledness
    lastControl = inputs.control
    lastMindWandering = inputs.mindWandering

    targetCalmness = calmnessTargetSmoother.add(calmnessTarget)
    targetFocus = focusTargetSmoother.add(focusTarget)
    targetStability = stabilityTargetSmoother.add(stabilityTarget)
    targetArtefact = artefactTargetSmoother.add(artefactTarget)
    targetIntensity = intensityTargetSmoother.add(intensityTarget)
    targetDriftMagnitude = driftTargetSmoother.add(driftMagnitude)
  }

  fun tick(nowMs: Long) {
    if (lastTickMs == 0L) {
      lastTickMs = nowMs
      return
    }

    val elapsedMs = (nowMs - lastTickMs).coerceAtLeast(0L)
    if (elapsedMs < cadenceMs) {
      return
    }
    lastTickMs = nowMs

    if (!active) {
      if (_state.value != SceneState()) {
        _state.value = SceneState()
      }
      return
    }

    if (frozen) {
      return
    }

    activeElapsedMs += elapsedMs
    val dtSeconds = elapsedMs / 1000f
    val previous = _state.value
    val nextCalmness = approach(previous.calmness, targetCalmness, dtSeconds * 2.0f)
    val nextFocus = approach(previous.focus, targetFocus, dtSeconds * 2.4f)
    val nextStability = approach(previous.stability, targetStability, dtSeconds * 2.0f)
    val nextArtefact = approach(previous.artefact, targetArtefact, dtSeconds * 3.0f)
    val nextIntensity = approach(previous.intensity, targetIntensity, dtSeconds * 2.6f)
    val nextDrift = signedDrift(targetDriftMagnitude, dtSeconds)
    val progressLinear = (activeElapsedMs / 360_000f).coerceIn(0f, 1f)
    val easedProgress = 1f - (1f - progressLinear).pow(2)

    val nextState = previous.copy(
      calmness = nextCalmness,
      focus = nextFocus,
      stability = nextStability,
      artefact = nextArtefact,
      intensity = nextIntensity,
      drift = nextDrift,
      progress = easedProgress,
      calmnessRate = rate(previous.calmness, nextCalmness, dtSeconds),
      focusRate = rate(previous.focus, nextFocus, dtSeconds),
      intensityRate = rate(previous.intensity, nextIntensity, dtSeconds),
    )

    _state.value = nextState
  }

  private fun correctionPulse(nowMs: Long): Float {
    if (pulseActivatedAtMs == Long.MIN_VALUE) return 0f
    return (1f - ((nowMs - pulseActivatedAtMs).coerceAtLeast(0L) / 1500f)).coerceIn(0f, 1f)
  }

  private fun signedDrift(magnitude: Float, dtSeconds: Float): Float {
    driftPhase += dtSeconds * (0.38 + (magnitude * 1.45))
    val oscillation = sin(driftPhase * PI).toFloat()
    return (oscillation * magnitude).coerceIn(-1f, 1f)
  }

  private fun rate(previous: Float, next: Float, dtSeconds: Float): Float {
    if (dtSeconds <= 0f) return 0f
    return ((next - previous) / dtSeconds / 2f).coerceIn(-1f, 1f)
  }
}
