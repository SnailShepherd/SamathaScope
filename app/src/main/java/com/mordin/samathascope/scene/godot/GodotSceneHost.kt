package com.mordin.samathascope.scene.godot

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import com.mordin.samathascope.R
import com.mordin.samathascope.GameId
import com.mordin.samathascope.scene.SceneState
import com.mordin.samathascope.scene.SceneSummary
import com.mordin.samathascope.scene.defaultSummary
import kotlinx.coroutines.delay

@Composable
fun GodotSceneHost(
  gameId: GameId,
  runId: Int,
  sceneState: SceneState,
  running: Boolean,
  paused: Boolean,
  compositionSeed: Int = 0,
  onSummaryChanged: (SceneSummary) -> Unit,
  onInkGardenTelemetryChanged: (InkGardenTelemetry) -> Unit = {},
) {
  val bridge = remember { GodotSceneBridge() }
  val status by bridge.status.collectAsState()
  val activity = LocalContext.current as FragmentActivity
  val expectedSceneId = remember(gameId) { sceneIdFor(gameId) }
  var sceneWarmupComplete by remember(gameId, runId) { mutableStateOf(false) }
  val isVisuallyReady = status.visualReadySceneId == expectedSceneId ||
    (status.mainLoopStarted && sceneWarmupComplete && status.lastError == null)
  var containerView by remember { mutableStateOf<FragmentContainerView?>(null) }

  LaunchedEffect(gameId, runId, compositionSeed) {
    bridge.loadScene(gameId, version = runId, compositionSeed = compositionSeed)
    onSummaryChanged(
      when (gameId) {
        GameId.INK_GARDEN -> SceneSummary("Garden richness", "0%")
        else -> gameId.defaultSummary(sceneState)
      }
    )
  }

  LaunchedEffect(gameId, runId, status.mainLoopStarted) {
    sceneWarmupComplete = false
    if (!status.mainLoopStarted) {
      return@LaunchedEffect
    }
    delay(SCENE_WARMUP_DELAY_MS)
    sceneWarmupComplete = true
  }

  LaunchedEffect(sceneState, running, paused, gameId) {
    bridge.setPlayback(running = running, paused = paused)
    bridge.pushSceneState(sceneState)
    onSummaryChanged(
      when (gameId) {
        GameId.INK_GARDEN -> {
          val telemetry = status.inkGardenTelemetry
          SceneSummary("Garden richness", "${(telemetry.richness * 100f).toInt()}%")
        }
        GameId.FIRE_KEEPER -> SceneSummary("Flame poise", "${(sceneState.stability * 100f).toInt()}%")
        else -> gameId.defaultSummary(sceneState)
      }
    )
  }

  LaunchedEffect(status.inkGardenTelemetry, gameId) {
    if (gameId != GameId.INK_GARDEN) {
      return@LaunchedEffect
    }
    onInkGardenTelemetryChanged(status.inkGardenTelemetry)
    onSummaryChanged(
      SceneSummary(
        label = "Garden richness",
        value = "${(status.inkGardenTelemetry.richness * 100f).toInt()}%",
      )
    )
  }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(320.dp)
      .background(
        brush = placeholderBrushFor(gameId),
        shape = RoundedCornerShape(6.dp),
      )
      .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
  ) {
    AndroidView(
      modifier = Modifier
        .fillMaxSize()
        .alpha(if (isVisuallyReady) 1f else 0.01f),
      factory = { context ->
        FragmentContainerView(context).apply {
          id = R.id.godot_scene_host_container
          containerView = this
        }
      },
      update = { view ->
        containerView = view
      },
    )

    if (!isVisuallyReady || status.lastError != null) {
      PlaceholderSurface(
        gameId = gameId,
        running = running,
        error = status.lastError,
      )
    }

    if (status.lastError != null) {
      Text(
        text = status.lastError ?: "",
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(top = 14.dp),
        color = Color(0xFFFFD6CC),
      )
    }
  }

  DisposableEffect(activity, containerView) {
    val view = containerView
    if (view != null) {
      val attachHost = Runnable {
        if (!view.isAttachedToWindow || activity.supportFragmentManager.isStateSaved) {
          return@Runnable
        }
        if (activity.supportFragmentManager.findFragmentByTag(HOST_TAG) == null) {
          activity.supportFragmentManager.commitNow {
            replace(R.id.godot_scene_host_container, GodotSceneHostFragment(), HOST_TAG)
          }
        }
      }
      view.post(attachHost)
      onDispose {
        view.removeCallbacks(attachHost)
      }
    } else {
      onDispose {
      // Keep the Godot runtime fragment alive across scene switches.
      }
    }
  }
}

private const val HOST_TAG = "samatha-godot-host"
private const val SCENE_WARMUP_DELAY_MS = 350L

@Composable
private fun PlaceholderSurface(
  gameId: GameId,
  running: Boolean,
  error: String?,
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(brush = placeholderBrushFor(gameId)),
  ) {
    Box(
      modifier = Modifier
        .align(Alignment.Center)
        .fillMaxWidth(0.88f)
        .fillMaxHeight(0.82f)
        .background(
          color = placeholderPanelColorFor(gameId),
          shape = RoundedCornerShape(18.dp),
        )
        .border(1.dp, placeholderPanelStrokeFor(gameId), RoundedCornerShape(18.dp)),
    )
    Text(
      text = when {
        error != null -> error
        gameId == GameId.INK_GARDEN && running -> "Preparing the first ink layer..."
        gameId == GameId.INK_GARDEN -> "Paper is primed. Press Start to reveal the painting."
        running -> "Preparing the scene..."
        else -> "Press Start to reveal the scene."
      },
      modifier = Modifier
        .align(Alignment.Center)
        .padding(horizontal = 28.dp),
      color = placeholderTextColorFor(gameId, error != null),
      style = MaterialTheme.typography.bodyMedium,
    )
  }
}

private fun placeholderBrushFor(gameId: GameId): Brush {
  return when (gameId) {
    GameId.INK_GARDEN -> {
      Brush.verticalGradient(listOf(Color(0xFFF7F3EA), Color(0xFFF1ECE1), Color(0xFFEAE4D6)))
    }
    else -> {
      Brush.verticalGradient(listOf(Color(0xFF1A1410), Color(0xFF221912), Color(0xFF2B1C12)))
    }
  }
}

private fun placeholderPanelColorFor(gameId: GameId): Color {
  return when (gameId) {
    GameId.INK_GARDEN -> Color(0x0E1E1813)
    else -> Color(0x24110E0A)
  }
}

private fun placeholderPanelStrokeFor(gameId: GameId): Color {
  return when (gameId) {
    GameId.INK_GARDEN -> Color(0x1F574D43)
    else -> Color(0x26E1B57B)
  }
}

private fun placeholderTextColorFor(gameId: GameId, isError: Boolean): Color {
  if (isError) {
    return Color(0xFF8A3029)
  }
  return when (gameId) {
    GameId.INK_GARDEN -> Color(0xFF41372F)
    else -> Color(0xFFF3DDC2)
  }
}
