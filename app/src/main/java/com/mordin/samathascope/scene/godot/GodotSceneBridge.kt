package com.mordin.samathascope.scene.godot

import android.util.Log
import com.mordin.samathascope.GameId
import com.mordin.samathascope.scene.SceneState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

data class GodotBridgePayload(
  val sceneId: String = "ink_garden",
  val running: Boolean = false,
  val paused: Boolean = true,
  val sceneState: SceneState = SceneState(),
  val version: Int = 0,
  val compositionSeed: Int = 0,
)

data class InkGardenTelemetry(
  val richness: Float = 0f,
  val growthActive: Boolean = false,
  val motifName: String? = null,
  val version: Int = 0,
)

data class GodotBridgeStatus(
  val readySceneId: String? = null,
  val visualReadySceneId: String? = null,
  val lastError: String? = null,
  val engineSetupCompleted: Boolean = false,
  val mainLoopStarted: Boolean = false,
  val inkGardenTelemetry: InkGardenTelemetry = InkGardenTelemetry(),
)

object GodotBridgeStore {
  private const val TAG = "SamathaGodot"
  private val _payload = MutableStateFlow(GodotBridgePayload())
  private val _status = MutableStateFlow(GodotBridgeStatus())

  val payload: StateFlow<GodotBridgePayload> = _payload
  val status: StateFlow<GodotBridgeStatus> = _status

  fun loadScene(sceneId: String, version: Int, compositionSeed: Int) {
    _payload.value = _payload.value.copy(
      sceneId = sceneId,
      version = version,
      compositionSeed = compositionSeed,
    )
    _status.value = _status.value.copy(
      readySceneId = null,
      visualReadySceneId = null,
      lastError = null,
      inkGardenTelemetry = InkGardenTelemetry(),
    )
  }

  fun setPlayback(running: Boolean, paused: Boolean) {
    _payload.value = _payload.value.copy(
      running = running,
      paused = paused,
    )
  }

  fun pushSceneState(state: SceneState) {
    _payload.value = _payload.value.copy(sceneState = state)
  }

  fun payloadJson(): String {
    val payload = _payload.value
    return JSONObject()
      .put("scene_id", payload.sceneId)
      .put("running", payload.running)
      .put("paused", payload.paused)
      .put("version", payload.version)
      .put("composition_seed", payload.compositionSeed)
      .put("calmness", payload.sceneState.calmness)
      .put("focus", payload.sceneState.focus)
      .put("stability", payload.sceneState.stability)
      .put("intensity", payload.sceneState.intensity)
      .put("drift", payload.sceneState.drift)
      .put("progress", payload.sceneState.progress)
      .put("calmness_rate", payload.sceneState.calmnessRate)
      .put("focus_rate", payload.sceneState.focusRate)
      .put("intensity_rate", payload.sceneState.intensityRate)
      .toString()
  }

  fun markSceneReady(sceneId: String) {
    Log.d(TAG, "Scene ready: $sceneId")
    _status.value = _status.value.copy(
      readySceneId = sceneId,
      lastError = null,
    )
  }

  fun markSceneVisualReady(sceneId: String) {
    Log.d(TAG, "Scene visually ready: $sceneId")
    _status.value = _status.value.copy(
      readySceneId = sceneId,
      visualReadySceneId = sceneId,
      lastError = null,
    )
  }

  fun reportError(message: String) {
    Log.e(TAG, "Scene error: $message")
    _status.value = _status.value.copy(lastError = message)
  }

  fun reportTimeout(sceneId: String) {
    if (_payload.value.sceneId != sceneId) {
      return
    }
    if (_status.value.visualReadySceneId == sceneId) {
      return
    }
    _status.value = _status.value.copy(
      lastError = when (sceneId) {
        "ink_garden" -> "Ink Garden did not paint its first frame."
        "fire" -> "Fire Keeper did not paint its first frame."
        else -> "The scene did not paint its first frame."
      }
    )
  }

  fun updateInkGardenTelemetry(
    richness: Float,
    growthActive: Boolean,
    motifName: String?,
    version: Int,
  ) {
    _status.value = _status.value.copy(
      inkGardenTelemetry = InkGardenTelemetry(
        richness = richness.coerceIn(0f, 1f),
        growthActive = growthActive,
        motifName = motifName?.takeIf { it.isNotBlank() },
        version = version,
      ),
      lastError = null,
    )
  }

  fun markEngineSetupCompleted() {
    _status.value = _status.value.copy(engineSetupCompleted = true)
  }

  fun markMainLoopStarted() {
    _status.value = _status.value.copy(
      engineSetupCompleted = true,
      mainLoopStarted = true,
      lastError = null,
    )
  }
}

class GodotSceneBridge {
  val status: StateFlow<GodotBridgeStatus> = GodotBridgeStore.status

  fun loadScene(gameId: GameId, version: Int, compositionSeed: Int = 0) {
    GodotBridgeStore.loadScene(
      sceneId = sceneIdFor(gameId),
      version = version,
      compositionSeed = compositionSeed,
    )
  }

  fun setPlayback(running: Boolean, paused: Boolean) {
    GodotBridgeStore.setPlayback(running = running, paused = paused)
  }

  fun pushSceneState(state: SceneState) {
    GodotBridgeStore.pushSceneState(state)
  }

  fun reportTimeout(gameId: GameId) {
    GodotBridgeStore.reportTimeout(sceneIdFor(gameId))
  }
}

internal fun sceneIdFor(gameId: GameId): String {
  return when (gameId) {
    GameId.INK_GARDEN -> "ink_garden"
    GameId.FIRE_KEEPER -> "fire"
    else -> "ink_garden"
  }
}
