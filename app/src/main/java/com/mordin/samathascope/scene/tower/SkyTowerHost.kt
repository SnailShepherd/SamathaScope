package com.mordin.samathascope.scene.tower

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.mordin.samathascope.scene.SceneState
import com.mordin.samathascope.scene.SceneSummary
import kotlinx.coroutines.isActive

@Composable
fun SkyTowerHost(
  runId: Int,
  sceneState: SceneState,
  running: Boolean,
  paused: Boolean,
  inputEnabled: Boolean,
  skyTowerSettings: SkyTowerSettings,
  onSummaryChanged: (SceneSummary) -> Unit,
) {
  val controller = remember {
    TowerSceneController().apply { reset() }
  }
  val latestSceneState by rememberUpdatedState(sceneState)
  val latestSkyTowerSettings by rememberUpdatedState(skyTowerSettings)
  val latestPaused by rememberUpdatedState(paused)
  var appliedSkyTowerSettings by remember {
    mutableStateOf(skyTowerSettings.clamped())
  }
  var snapshot by remember {
    mutableStateOf(
      TowerRenderSnapshot(
        bodies = emptyList(),
        carrier = TowerCarrierState(),
        dustBursts = emptyList(),
        impactFlash = 0f,
        cameraOffsetX = 0f,
        cameraOffsetY = 0f,
        towerHeight = 0,
      )
    )
  }
  var pendingTaps by remember { mutableIntStateOf(0) }

  LaunchedEffect(runId) {
    appliedSkyTowerSettings = latestSkyTowerSettings.clamped()
    controller.reset(appliedSkyTowerSettings)
    snapshot = controller.step(SceneState(), 0f, false)
    onSummaryChanged(controller.summary())
  }

  LaunchedEffect(running) {
    if (!running) {
      controller.reset(appliedSkyTowerSettings)
      snapshot = controller.step(SceneState(), 0f, false)
      onSummaryChanged(controller.summary())
    }
  }

  LaunchedEffect(skyTowerSettings, running) {
    val nextSettings = latestSkyTowerSettings.clamped()
    if (!running && nextSettings != appliedSkyTowerSettings) {
      appliedSkyTowerSettings = nextSettings
      controller.reset(appliedSkyTowerSettings)
      snapshot = controller.step(SceneState(), 0f, false)
      onSummaryChanged(controller.summary())
    }
  }

  LaunchedEffect(controller, running) {
    if (!running) return@LaunchedEffect
    var lastFrameNanos = 0L
    while (isActive) {
      withFrameNanos { frameNanos ->
        if (lastFrameNanos == 0L) {
          lastFrameNanos = frameNanos
        }
        val dtSeconds = ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(1f / 120f, 0.05f)
        lastFrameNanos = frameNanos
        if (running && !latestPaused) {
          if (pendingTaps > 0) {
            controller.queueRelease()
            pendingTaps = 0
          }
          snapshot = controller.step(
            sceneState = latestSceneState,
            dtSeconds = dtSeconds,
            running = true,
          )
          onSummaryChanged(controller.summary())
        }
      }
    }
  }

  val latestInputEnabled by rememberUpdatedState(inputEnabled)
  val onTap = remember {
    { 
      if (latestInputEnabled && !latestPaused) {
        pendingTaps += 1
      }
    }
  }

  TowerRenderer(
    snapshot = snapshot,
    inputEnabled = inputEnabled,
    running = running,
    paused = paused,
    onTap = onTap,
  )
}
