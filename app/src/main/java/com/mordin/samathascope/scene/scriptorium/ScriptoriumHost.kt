package com.mordin.samathascope.scene.scriptorium

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.mordin.samathascope.scene.SceneState
import com.mordin.samathascope.scene.SceneSummary
import kotlinx.coroutines.isActive

@Composable
fun ScriptoriumHost(
  runId: Int,
  sceneState: SceneState,
  running: Boolean,
  paused: Boolean,
  onSummaryChanged: (SceneSummary) -> Unit,
) {
  val controller = remember { InkSceneController().apply { reset() } }
  val latestSceneState by rememberUpdatedState(sceneState)
  val latestPaused by rememberUpdatedState(paused)
  var renderState by remember { mutableStateOf(InkRenderState()) }

  LaunchedEffect(runId) {
    controller.reset()
    renderState = controller.step(SceneState(), 0f, false)
    onSummaryChanged(controller.summary(SceneState()))
  }

  LaunchedEffect(running) {
    if (!running) {
      controller.reset()
      renderState = controller.step(SceneState(), 0f, false)
      onSummaryChanged(controller.summary(SceneState()))
    }
  }

  LaunchedEffect(controller, running) {
    var lastFrameNanos = 0L
    while (isActive) {
      withFrameNanos { frameNanos ->
        if (lastFrameNanos == 0L) {
          lastFrameNanos = frameNanos
        }
        val dtSeconds = ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(1f / 120f, 0.05f)
        lastFrameNanos = frameNanos
        if (running && !latestPaused) {
          renderState = controller.step(
            sceneState = latestSceneState,
            dtSeconds = dtSeconds,
            running = true,
          )
        } else if (!running) {
          renderState = controller.step(
            sceneState = latestSceneState,
            dtSeconds = dtSeconds,
            running = false,
          )
        }
        onSummaryChanged(controller.summary(latestSceneState))
      }
    }
  }

  InkSurface(state = renderState)
}
