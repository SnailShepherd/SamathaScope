package com.mordin.samathascope

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun SelectedGameScene(
  ui: UiState,
  onGameTap: () -> Unit,
) {
  when (val state = ui.gameRuntimeState) {
    is SkyTowerRuntimeState -> SkyTowerScene(
      state = state,
      signals = ui.gameSignals,
      inputEnabled = ui.gameHudState.inputEnabled,
      onGameTap = onGameTap,
    )

    is InkGardenRuntimeState -> InkGardenScene(
      state = state,
      signals = ui.gameSignals,
    )

    is FireKeeperRuntimeState -> FireKeeperScene(
      state = state,
      signals = ui.gameSignals,
    )

    is ScriptoriumRuntimeState -> ScriptoriumScene(
      state = state,
      signals = ui.gameSignals,
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GamePickerRow(
  selected: GameId,
  onSelect: (GameId) -> Unit,
) {
  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    GameId.entries.forEach { gameId ->
      OutlinedButton(
        onClick = { onSelect(gameId) },
        modifier = Modifier.height(40.dp),
        border = BorderStroke(
          width = 2.dp,
          color = if (selected == gameId) {
            MaterialTheme.colorScheme.primary
          } else {
            MaterialTheme.colorScheme.outline
          },
        ),
      ) {
        Text(gameId.displayName())
      }
    }
  }
}

@Composable
private fun SkyTowerScene(
  state: SkyTowerRuntimeState,
  signals: GameSignalSnapshot,
  inputEnabled: Boolean,
  onGameTap: () -> Unit,
) {
  val skyTop = Color(0xFFDCEEFF)
  val skyBottom = Color(0xFFEEF5D9)

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(280.dp)
      .background(
        brush = Brush.verticalGradient(listOf(skyTop, skyBottom)),
        shape = RoundedCornerShape(4.dp),
      )
      .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
      .clickable(enabled = inputEnabled) { onGameTap() },
  ) {
    Canvas(
      modifier = Modifier
        .fillMaxSize()
        .padding(8.dp)
    ) {
      val w = size.width
      val h = size.height
      val groundY = h * 0.86f

      drawRect(
        brush = Brush.verticalGradient(
          listOf(Color(0x00FFFFFF), Color(0x22FFFFFF), Color(0x33A3C0D4)),
        ),
        topLeft = Offset.Zero,
        size = Size(w, groundY),
      )
      drawRect(
        color = Color(0xFFC3B38B),
        topLeft = Offset(0f, groundY),
        size = Size(w, h - groundY),
      )

      state.blocks.forEachIndexed { index, block ->
        val blockWidth = block.width * w
        val blockHeight = h * 0.05f
        val swayOffset = state.towerSway * w * (0.12f + (index * 0.02f))
        val centerX = (block.x * w) + swayOffset
        val topY = groundY - ((index + 1) * blockHeight)
        drawRoundRect(
          color = Color(0xFF8B5E3C),
          topLeft = Offset(centerX - (blockWidth / 2f), topY),
          size = Size(blockWidth, blockHeight),
          cornerRadius = CornerRadius(8f, 8f),
        )
      }

      val fallingWidth = w * 0.13f
      val fallingHeight = h * 0.05f
      val tremorOffset = sin(state.timeSeconds * 20f) * state.tremor * 10f
      val fallingCenter = Offset((state.fallingX * w) + tremorOffset, state.fallingY * h)
      drawRoundRect(
        color = if (state.fallingVelocityY == 0f) Color(0xFFD08C43) else Color(0xFFC16C4A),
        topLeft = Offset(fallingCenter.x - (fallingWidth / 2f), fallingCenter.y - (fallingHeight / 2f)),
        size = Size(fallingWidth, fallingHeight),
        cornerRadius = CornerRadius(10f, 10f),
      )

      if (signals.correctionPulse > 0f) {
        drawCircle(
          color = Color(0x55FFF0A8),
          radius = h * (0.08f + (signals.correctionPulse * 0.05f)),
          center = Offset(w * 0.5f, groundY - (state.blocks.size * h * 0.025f)),
        )
      }
    }

    Text(
      text = if (inputEnabled) "Tap to place" else "Session running required",
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(10.dp),
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Composable
private fun InkGardenScene(
  state: InkGardenRuntimeState,
  signals: GameSignalSnapshot,
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(280.dp)
      .background(
        brush = Brush.linearGradient(
          listOf(Color(0xFFF7F0DD), Color(0xFFE7E1CC), Color(0xFFF3F3EC)),
        ),
        shape = RoundedCornerShape(4.dp),
      )
      .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
  ) {
    Canvas(
      modifier = Modifier
        .fillMaxSize()
        .padding(10.dp)
    ) {
      val w = size.width
      val h = size.height

      state.seeds.forEachIndexed { index, seed ->
        val start = Offset(seed.x * w, seed.y * h)
        val length = h * (0.08f + (seed.age * 0.45f))
        val chaos = seed.chaos * 0.25f
        val branchPath = Path().apply {
          moveTo(start.x, start.y)
          for (step in 1..6) {
            val t = step / 6f
            val angle = seed.direction + (sin((state.timeSeconds + index) * 0.7f + (t * 2.6f)) * chaos)
            val x = start.x + (cos(angle.toDouble()).toFloat() * length * t * 0.55f)
            val y = start.y - (length * t)
            lineTo(x, y)
          }
        }
        drawPath(
          path = branchPath,
          color = Color(0xFF2F6C54).copy(alpha = 0.55f + (seed.bloom * 0.30f)),
          style = Stroke(width = 4f - (seed.chaos * 1.5f)),
        )
        val branchBounds = branchPath.getBounds()
        drawCircle(
          color = Color(0xFF214C3E).copy(alpha = 0.45f + (seed.bloom * 0.25f)),
          radius = h * (0.010f + (seed.bloom * 0.010f)),
          center = Offset(branchBounds.center.x, branchBounds.top),
        )
      }

      val splatterCount = max(1, (state.splatter * 8f).roundToInt())
      repeat(splatterCount) { index ->
        val x = w * (0.10f + (sequenceFloat(index, 11) * 0.80f))
        val y = h * (0.12f + (sequenceFloat(index, 13) * 0.72f))
        drawCircle(
          color = Color(0xFF2A4035).copy(alpha = 0.08f + (state.splatter * 0.18f)),
          radius = h * (0.005f + (sequenceFloat(index, 7) * 0.012f)),
          center = Offset(x, y),
        )
      }

      if (state.repairGlow > 0.05f) {
        drawCircle(
          color = Color(0x33D8F1CC),
          radius = h * (0.16f + (state.repairGlow * 0.06f)),
          center = Offset(w * 0.5f, h * 0.55f),
        )
      }
    }
  }
}

@Composable
private fun FireKeeperScene(
  state: FireKeeperRuntimeState,
  signals: GameSignalSnapshot,
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(280.dp)
      .background(
        brush = Brush.verticalGradient(
          listOf(Color(0xFF111720), Color(0xFF2C2219), Color(0xFF3C2B1D)),
        ),
        shape = RoundedCornerShape(4.dp),
      )
      .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
  ) {
    Canvas(
      modifier = Modifier
        .fillMaxSize()
        .padding(10.dp)
    ) {
      val w = size.width
      val h = size.height
      val baseY = h * 0.80f

      drawRoundRect(
        color = Color(0xFF4E3424),
        topLeft = Offset(w * 0.36f, baseY),
        size = Size(w * 0.28f, h * 0.05f),
        cornerRadius = CornerRadius(8f, 8f),
      )
      drawRoundRect(
        color = Color(0xFF6D4B35),
        topLeft = Offset(w * 0.30f, baseY + (h * 0.01f)),
        size = Size(w * 0.20f, h * 0.04f),
        cornerRadius = CornerRadius(8f, 8f),
      )

      val flameHeight = h * (0.18f + (state.flameHeight * 0.28f))
      val flameWidth = w * (0.12f + (state.turbulence * 0.08f))
      val leanPx = state.lean * w * 0.15f
      val flamePath = Path().apply {
        moveTo(w * 0.5f, baseY)
        quadraticTo(
          w * 0.42f + leanPx,
          baseY - (flameHeight * 0.45f),
          w * 0.5f + leanPx,
          baseY - flameHeight,
        )
        quadraticTo(
          w * 0.58f + leanPx,
          baseY - (flameHeight * 0.42f),
          w * 0.5f,
          baseY,
        )
      }
      drawPath(flamePath, brush = Brush.verticalGradient(listOf(Color(0xFFFF8C42), Color(0xFFFFD166), Color(0x99FF5F2E))))
      drawCircle(
        color = Color(0x44FFCF77),
        radius = flameWidth * (0.8f + (state.warmth * 0.8f)),
        center = Offset((w * 0.5f) + leanPx, baseY - (flameHeight * 0.5f)),
      )

      repeat(max(2, (state.emberLift * 10f).roundToInt())) { index ->
        val x = (w * 0.46f) + (sequenceFloat(index, 9) * w * 0.10f)
        val risePhase = (sequenceFloat(index, 21) + (state.timeSeconds * 0.10f)) % 1f
        val y = baseY - (risePhase * flameHeight * 1.4f)
        drawCircle(
          color = Color(0xFFFFC857).copy(alpha = 0.30f + (state.emberLift * 0.45f)),
          radius = h * (0.005f + (sequenceFloat(index, 5) * 0.008f)),
          center = Offset(x, y),
        )
      }

      repeat(max(1, (state.smokeDensity * 9f).roundToInt())) { index ->
        val x = (w * 0.40f) + (sequenceFloat(index, 3) * w * 0.20f)
        val y = baseY - (sequenceFloat(index, 15) * flameHeight * 1.8f)
        drawCircle(
          color = Color(0x33D7D7D7),
          radius = h * (0.015f + (sequenceFloat(index, 19) * 0.030f)),
          center = Offset(x, y),
        )
      }
    }
  }
}

@Composable
private fun ScriptoriumScene(
  state: ScriptoriumRuntimeState,
  signals: GameSignalSnapshot,
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(280.dp)
      .background(
        brush = Brush.verticalGradient(
          listOf(Color(0xFFF8F1D8), Color(0xFFF0E2B8), Color(0xFFE7D7A8)),
        ),
        shape = RoundedCornerShape(4.dp),
      )
      .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
  ) {
    Canvas(
      modifier = Modifier
        .fillMaxSize()
        .padding(12.dp)
    ) {
      val w = size.width
      val h = size.height
      val lineSpacing = h / 8f
      val wobble = (1f - state.legibility) * 8f

      for (index in 0 until state.revealedLines) {
        val startY = lineSpacing * (index + 1)
        val progress = (state.progress * 1.15f) - (index * 0.09f)
        val lineLength = (w * progress.coerceIn(0.08f, 0.90f))
        val path = Path().apply {
          moveTo(w * 0.08f, startY)
          for (segment in 1..10) {
            val t = segment / 10f
            val x = (w * 0.08f) + (lineLength * t)
            val y = startY + sin((state.timeSeconds * 1.2f) + (segment * 0.8f) + index) * wobble
            lineTo(x, y)
          }
        }
        drawPath(
          path = path,
          color = Color(0xFF3D2B1F).copy(alpha = 0.75f - (state.fade * 0.30f)),
          style = Stroke(width = 3f - (state.fade * 1.2f)),
        )
      }

      repeat(max(1, (state.blotches * 10f).roundToInt())) { index ->
        val x = w * (0.12f + (sequenceFloat(index, 23) * 0.74f))
        val y = h * (0.14f + (sequenceFloat(index, 29) * 0.68f))
        drawCircle(
          color = Color(0x66291A12),
          radius = h * (0.008f + (sequenceFloat(index, 31) * 0.020f)),
          center = Offset(x, y),
        )
      }

      if (state.ornament > 0.1f || signals.correctionPulse > 0.1f) {
        drawCircle(
          color = Color(0x55C89E3D),
          radius = h * (0.05f + (state.ornament * 0.05f)),
          center = Offset(w * 0.84f, h * 0.20f),
          style = Fill,
        )
        drawCircle(
          color = Color(0x44F7E7A1),
          radius = h * (0.08f + (signals.correctionPulse * 0.05f)),
          center = Offset(w * 0.84f, h * 0.20f),
        )
      }
    }
  }
}
