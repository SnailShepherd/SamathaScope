package com.mordin.samathascope.scene.godot

import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.UsedByGodot

class SamathaGodotPlugin(
  godot: Godot,
) : GodotPlugin(godot) {

  override fun getPluginName(): String = "SamathaBridge"

  @UsedByGodot
  fun getPayloadJson(): String {
    return GodotBridgeStore.payloadJson()
  }

  @UsedByGodot
  fun notifySceneReady(sceneId: String) {
    GodotBridgeStore.markSceneReady(sceneId)
  }

  @UsedByGodot
  fun notifySceneVisualReady(sceneId: String) {
    GodotBridgeStore.markSceneVisualReady(sceneId)
  }

  @UsedByGodot
  fun reportSceneError(message: String) {
    GodotBridgeStore.reportError(message)
  }

  @UsedByGodot
  fun reportInkGardenTelemetry(
    richness: Double,
    growthActive: Boolean,
    motifName: String,
    version: Int,
  ) {
    GodotBridgeStore.updateInkGardenTelemetry(
      richness = richness.toFloat(),
      growthActive = growthActive,
      motifName = motifName,
      version = version,
    )
  }
}
