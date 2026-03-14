package com.mordin.samathascope.scene

import com.mordin.samathascope.GameId
import com.mordin.samathascope.StateLabel
import com.mordin.samathascope.displayName
import com.mordin.samathascope.startHint

data class SceneState(
  val calmness: Float = 0f,
  val focus: Float = 0f,
  val stability: Float = 0f,
  val artefact: Float = 0f,
  val intensity: Float = 0f,
  val drift: Float = 0f,
  val progress: Float = 0f,
  val calmnessRate: Float = 0f,
  val focusRate: Float = 0f,
  val intensityRate: Float = 0f,
)

data class SceneSignalInputs(
  val settledness: Float,
  val alertness: Float,
  val control: Float,
  val qualityConfidence: Float,
  val artefactScore: Float,
  val effortfulFocus: Float,
  val mindWandering: Float,
  val displayedDrowsyScore: Float,
  val timestampMs: Long,
)

data class SceneHudState(
  val title: String = GameId.SKY_TOWER.displayName(),
  val inputHint: String = GameId.SKY_TOWER.startHint(),
  val inputEnabled: Boolean = false,
  val calmnessPercent: Int = 0,
  val focusPercent: Int = 0,
  val stabilityPercent: Int = 0,
  val intensityPercent: Int = 0,
  val driftSignedPercent: Int = 0,
  val poorSignal: Int = 255,
  val elapsedSeconds: Int = 0,
  val batteryPercent: Int? = null,
  val stateLabel: StateLabel = StateLabel.UNCERTAIN,
)

data class SceneSummary(
  val label: String,
  val value: String,
)

fun GameId.isGodotScene(): Boolean {
  return this == GameId.INK_GARDEN || this == GameId.FIRE_KEEPER
}

fun GameId.defaultSummary(sceneState: SceneState = SceneState()): SceneSummary {
  return when (this) {
    GameId.SKY_TOWER -> SceneSummary("Tower height", "0 stones")
    GameId.INK_GARDEN -> SceneSummary("Garden richness", "${(sceneState.progress * 100f).toInt()}%")
    GameId.FIRE_KEEPER -> SceneSummary("Flame poise", "${(sceneState.stability * 100f).toInt()}%")
    GameId.SCRIPTORIUM -> SceneSummary("Page fullness", "${(sceneState.progress * 100f).toInt()}%")
  }
}
