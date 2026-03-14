package com.mordin.samathascope.scene

import com.mordin.samathascope.GameAudioState
import com.mordin.samathascope.GameId
import com.mordin.samathascope.StateLabel
import com.mordin.samathascope.displayName
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

fun buildSceneHudState(
  gameId: GameId,
  sceneState: SceneState,
  stateLabel: StateLabel,
  poorSignal: Int,
  elapsedSeconds: Int,
  batteryPercent: Int?,
  inputEnabled: Boolean,
  inputHint: String,
): SceneHudState {
  return SceneHudState(
    title = gameId.displayName(),
    inputHint = inputHint,
    inputEnabled = inputEnabled,
    calmnessPercent = (sceneState.calmness * 100f).roundToInt(),
    focusPercent = (sceneState.focus * 100f).roundToInt(),
    stabilityPercent = (sceneState.stability * 100f).roundToInt(),
    intensityPercent = (sceneState.intensity * 100f).roundToInt(),
    driftSignedPercent = (sceneState.drift * 100f).roundToInt(),
    poorSignal = poorSignal,
    elapsedSeconds = elapsedSeconds,
    batteryPercent = batteryPercent,
    stateLabel = stateLabel,
  )
}

fun buildSceneAudioState(
  gameId: GameId,
  sceneState: SceneState,
  muted: Boolean,
): GameAudioState {
  val drift = sceneState.drift.absoluteValue
  return when (gameId) {
    GameId.SKY_TOWER -> GameAudioState(
      ambience = 0.18f + (sceneState.progress * 0.28f) + (sceneState.stability * 0.22f),
      motion = 0.10f + (drift * 0.60f) + (sceneState.intensity * 0.12f),
      glitch = 0.04f + ((1f - sceneState.stability) * 0.14f),
      accent = 0.08f + (sceneState.intensity * 0.55f),
      warmth = 0.20f + (sceneState.calmness * 0.44f),
      muted = muted,
    )

    GameId.INK_GARDEN -> GameAudioState(
      ambience = 0.16f + (sceneState.progress * 0.36f),
      motion = 0.08f + (drift * 0.34f) + (sceneState.intensity * 0.16f),
      glitch = 0.02f + ((1f - sceneState.stability) * 0.10f),
      accent = 0.10f + (sceneState.intensity * 0.60f),
      warmth = 0.28f + (sceneState.calmness * 0.32f),
      muted = muted,
    )

    GameId.FIRE_KEEPER -> GameAudioState(
      ambience = 0.28f + (sceneState.intensity * 0.36f),
      motion = 0.12f + (drift * 0.38f) + (sceneState.intensity * 0.22f),
      glitch = 0.04f + ((1f - sceneState.stability) * 0.08f),
      accent = 0.14f + (sceneState.focus * 0.18f) + (sceneState.intensity * 0.48f),
      warmth = 0.30f + (sceneState.calmness * 0.18f) + (sceneState.intensity * 0.18f),
      muted = muted,
    )

    GameId.SCRIPTORIUM -> GameAudioState(
      ambience = 0.20f + (sceneState.progress * 0.34f),
      motion = 0.06f + (drift * 0.18f) + (sceneState.focus * 0.12f),
      glitch = 0.02f + ((1f - sceneState.stability) * 0.06f),
      accent = 0.08f + (sceneState.intensity * 0.42f),
      warmth = 0.26f + (sceneState.calmness * 0.38f),
      muted = muted,
    )
  }.sanitized()
}

private fun GameAudioState.sanitized(): GameAudioState {
  return copy(
    ambience = ambience.coerceIn(0f, 1f),
    motion = motion.coerceIn(0f, 1f),
    glitch = glitch.coerceIn(0f, 1f),
    accent = accent.coerceIn(0f, 1f),
    warmth = warmth.coerceIn(0f, 1f),
  )
}
