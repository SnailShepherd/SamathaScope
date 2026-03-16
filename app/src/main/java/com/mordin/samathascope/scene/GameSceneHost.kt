package com.mordin.samathascope.scene

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import com.mordin.samathascope.GameId
import com.mordin.samathascope.InkGardenSceneSettings
import com.mordin.samathascope.scene.godot.InkGardenTelemetry
import com.mordin.samathascope.scene.godot.GodotSceneHost
import com.mordin.samathascope.scene.scriptorium.ScriptoriumHost
import com.mordin.samathascope.scene.tower.SkyTowerSettings
import com.mordin.samathascope.scene.tower.SkyTowerHost

@Composable
fun GameSceneHost(
  gameId: GameId,
  runId: Int,
  sceneState: SceneState,
  running: Boolean,
  paused: Boolean,
  inputEnabled: Boolean,
  skyTowerSettings: SkyTowerSettings,
  inkGardenSceneSettings: InkGardenSceneSettings,
  inkGardenCompositionSeed: Int,
  onSummaryChanged: (SceneSummary) -> Unit,
  onInkGardenTelemetryChanged: (InkGardenTelemetry) -> Unit,
) {
  key(gameId) {
    when (gameId) {
      GameId.SKY_TOWER -> SkyTowerHost(
        runId = runId,
        sceneState = sceneState,
        running = running,
        paused = paused,
        inputEnabled = inputEnabled,
        skyTowerSettings = skyTowerSettings,
        onSummaryChanged = onSummaryChanged,
      )

      GameId.INK_GARDEN,
      GameId.FIRE_KEEPER -> GodotSceneHost(
        gameId = gameId,
        runId = runId,
        sceneState = sceneState,
        running = running,
        paused = paused,
        inkGardenSceneSettings = inkGardenSceneSettings,
        compositionSeed = inkGardenCompositionSeed,
        onSummaryChanged = onSummaryChanged,
        onInkGardenTelemetryChanged = onInkGardenTelemetryChanged,
      )

      GameId.SCRIPTORIUM -> ScriptoriumHost(
        runId = runId,
        sceneState = sceneState,
        running = running,
        paused = paused,
        onSummaryChanged = onSummaryChanged,
      )
    }
  }
}
