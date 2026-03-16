package com.mordin.samathascope.scene.tower

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@Composable
fun TowerRenderer(
  snapshot: TowerRenderSnapshot,
  inputEnabled: Boolean,
  running: Boolean,
  paused: Boolean,
  onTap: () -> Unit,
) {

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(320.dp)
      .testTag("sky_tower_canvas")
      .pointerInput(onTap) {
        awaitEachGesture {
          val down = awaitFirstDown()
          down.consume()
          val up = waitForUpOrCancellation()
          up?.consume()
          if (up != null) onTap()
        }
      },
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(start = 12.dp, end = 12.dp, top = 52.dp, bottom = 66.dp)
        .clipToBounds(),
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        drawTowerScene(snapshot = snapshot)
      }
    }

    Text(
      text = "Sky Tower",
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(start = 18.dp, top = 16.dp),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
      color = Color(0xFF3A3029),
    )
    Text(
      text = "${snapshot.towerHeight} stones",
      modifier = Modifier
        .align(Alignment.TopEnd)
        .testTag("sky_tower_height")
        .padding(end = 18.dp, top = 18.dp),
      style = MaterialTheme.typography.labelLarge,
      color = Color(0xFF6A5C4C),
    )
  }
}

private fun DrawScope.drawTowerScene(snapshot: TowerRenderSnapshot) {
  val highestTop = snapshot.bodies
    .filter { it.id != 0 }
    .minOfOrNull { it.y - (it.height * 0.5f) } ?: 9.7f

  // Viewport height stays constant; it scrolls up when the tower exceeds 60% of view
  val viewportHeight = VIEWPORT_HEIGHT
  val defaultWorldBottom = 12.8f
  val defaultWorldTop = defaultWorldBottom - viewportHeight

  // Tower height in world units from foundation down to highest stone
  val towerWorldHeight = TowerPhysics.FOUNDATION_Y - highestTop
  val viewThreshold = viewportHeight * SINK_THRESHOLD_RATIO

  val worldTop: Float
  val worldBottom: Float
  if (towerWorldHeight > viewThreshold) {
    // Scroll up: keep the highest stone visible with margin
    worldTop = (highestTop - 2.4f).coerceAtMost(defaultWorldTop)
    worldBottom = worldTop + viewportHeight
  } else {
    worldTop = defaultWorldTop
    worldBottom = defaultWorldBottom
  }

  val worldLeft = 0.5f
  val worldRight = 9.5f
  val viewport = TowerViewport(
    left = worldLeft,
    right = worldRight,
    top = worldTop,
    bottom = worldBottom,
    canvasWidth = size.width,
    canvasHeight = size.height,
  )

  drawRect(
    brush = Brush.verticalGradient(
      colors = listOf(
        Color(0xFFF7F1E5),
        Color(0xFFE5D9C5),
        Color(0xFFD3BE9B),
      ),
    ),
  )
  drawAtmosphere(viewport, snapshot.impactFlash)

  translate(
    left = snapshot.cameraOffsetX * size.width,
    top = snapshot.cameraOffsetY * size.height,
  ) {
    drawGround(viewport)
    drawDust(snapshot, viewport)

    val bodies = snapshot.bodies
      .filter { body ->
        // Cull bodies that are entirely outside the viewport (with margin)
        val bodyTop = body.y - body.height
        val bodyBottom = body.y + body.height
        bodyBottom >= worldTop - 1f && bodyTop <= worldBottom + 1f
      }
      .sortedWith(
        compareBy<TowerBodySnapshot> { it.id == 0 }
          .thenByDescending { it.y }
          .thenBy { it.id }
      )
    bodies.forEach { body ->
      val shadowLift = if (body.active) 1.25f else 0.78f
      drawStoneShadow(body, viewport, shadowLift)
    }
    bodies.forEach { body ->
      drawStoneBody(body = body, viewport = viewport)
    }

    if (snapshot.carrier.visible) {
      drawCarrierGuide(snapshot.carrier, viewport)
      drawCarrierStone(snapshot.carrier, viewport)
    }
  }

  if (snapshot.impactFlash > 0.01f) {
    drawRect(
      color = Color(0x24FFF3D4),
      alpha = snapshot.impactFlash * 0.32f,
      blendMode = BlendMode.Screen,
    )
  }
}

private fun DrawScope.drawAtmosphere(
  viewport: TowerViewport,
  impactFlash: Float,
) {
  val hazeColors = listOf(
    Color(0x35FFF6E0),
    Color(0x15FFF6E0),
    Color.Transparent,
  )
  drawCircle(
    brush = Brush.radialGradient(hazeColors),
    radius = min(size.width, size.height) * 0.33f,
    center = Offset(size.width * 0.26f, size.height * 0.16f),
  )
  drawOval(
    brush = Brush.verticalGradient(
      colors = listOf(
        Color(0x14FFFFFF),
        Color.Transparent,
      ),
    ),
    topLeft = Offset(size.width * 0.58f, size.height * 0.10f),
    size = androidx.compose.ui.geometry.Size(size.width * 0.24f, size.height * 0.10f),
  )
  drawOval(
    brush = Brush.verticalGradient(
      colors = listOf(
        Color(0x10FFFFFF),
        Color.Transparent,
      ),
    ),
    topLeft = Offset(size.width * 0.18f, size.height * 0.22f),
    size = androidx.compose.ui.geometry.Size(size.width * 0.28f, size.height * 0.08f),
  )
  if (impactFlash > 0.02f) {
    drawRect(
      brush = Brush.verticalGradient(
        colors = listOf(Color(0x22FFF0D7), Color.Transparent),
      ),
      topLeft = Offset.Zero,
      size = androidx.compose.ui.geometry.Size(size.width, viewport.worldToScreenY(5.1f)),
      alpha = impactFlash * 0.40f,
    )
  }
}

private fun DrawScope.drawGround(viewport: TowerViewport) {
  val groundTop = viewport.worldToScreenY(TowerPhysics.FOUNDATION_Y + 0.12f)
  drawRect(
    brush = Brush.verticalGradient(
      colors = listOf(
        Color(0x00D7C39E),
        Color(0x44B1906D),
        Color(0x88A17854),
      ),
    ),
    topLeft = Offset(0f, groundTop - size.height * 0.06f),
    size = androidx.compose.ui.geometry.Size(size.width, size.height - groundTop + size.height * 0.10f),
  )
  drawLine(
    color = Color(0x8A866A4E),
    start = Offset(0f, viewport.worldToScreenY(TowerPhysics.FOUNDATION_Y)),
    end = Offset(size.width, viewport.worldToScreenY(TowerPhysics.FOUNDATION_Y)),
    strokeWidth = 2.dp.toPx(),
  )
}

private fun DrawScope.drawDust(
  snapshot: TowerRenderSnapshot,
  viewport: TowerViewport,
) {
  snapshot.dustBursts.forEach { burst ->
    val center = viewport.worldToScreen(burst.x, burst.y)
    val radius = burst.radius * viewport.scaleX * 2.4f
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(
          Color(0x44C4A47A),
          Color(0x10A88358),
          Color.Transparent,
        ),
      ),
      center = center,
      radius = radius,
      alpha = burst.alpha,
    )
  }
}

private fun DrawScope.drawCarrierGuide(
  carrier: TowerCarrierState,
  viewport: TowerViewport,
) {
  val center = viewport.worldToScreen(carrier.x, carrier.y)
  val guideTop = viewport.worldToScreenY(viewport.top + 0.25f)
  drawLine(
    color = Color(0x33FFFFFF),
    start = Offset(center.x, guideTop),
    end = Offset(center.x, center.y - (carrier.height * viewport.scaleY * 0.88f)),
    strokeWidth = 1.5.dp.toPx(),
    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f)),
  )
}

private fun DrawScope.drawCarrierStone(
  carrier: TowerCarrierState,
  viewport: TowerViewport,
) {
  val preview = TowerBodySnapshot(
    id = 999,
    x = carrier.x,
    y = carrier.y,
    width = carrier.width,
    height = carrier.height,
    angle = sin(carrier.x.toDouble()).toFloat() * 0.02f,
    sleeping = false,
    active = true,
    shapeKind = carrier.shapeKind,
    shapeVertices = carrier.shapeVertices,
    shapeLobes = carrier.shapeLobes,
  )
  drawStoneShadow(preview, viewport, lift = 1.65f, alphaMultiplier = 0.60f)
  drawStoneBody(preview, viewport, alphaMultiplier = 0.94f)
}

private fun DrawScope.drawStoneShadow(
  body: TowerBodySnapshot,
  viewport: TowerViewport,
  lift: Float,
  alphaMultiplier: Float = 1f,
) {
  val center = viewport.worldToScreen(body.x, body.y)
  val shadowWidth = max(body.width * 0.58f, body.width * (0.48f + abs(body.angle) * 0.12f)) * viewport.scaleX
  val shadowHeight = max(body.height * 0.17f, 0.08f * lift) * viewport.scaleY
  val shadowTop = center.y + (body.height * viewport.scaleY * 0.47f) + (lift * viewport.scaleY * 0.06f)
  drawOval(
    color = Color(0x45000000),
    topLeft = Offset(center.x - shadowWidth * 0.5f, shadowTop),
    size = androidx.compose.ui.geometry.Size(shadowWidth, shadowHeight),
    alpha = (if (body.active) 0.40f else 0.28f) * alphaMultiplier,
  )
}

private fun DrawScope.drawStoneBody(
  body: TowerBodySnapshot,
  viewport: TowerViewport,
  alphaMultiplier: Float = 1f,
) {
  val palette = paletteFor(body)
  val center = viewport.worldToScreen(body.x, body.y)
  val rotationDegrees = (body.angle * (180f / PI.toFloat()))

  translate(center.x, center.y) {
    rotate(rotationDegrees) {
      val outlinePath = localPath(body.shapeVertices, viewport)
      val lobePaths = if (body.shapeLobes.isNotEmpty()) {
        body.shapeLobes.map { localPath(it, viewport) }
      } else {
        listOf(outlinePath)
      }

      drawPath(
        path = outlinePath,
        brush = Brush.verticalGradient(
          colors = listOf(
            mixColor(palette.highlight, palette.base, 0.35f),
            palette.base,
            mixColor(palette.shadow, palette.base, 0.20f),
          ),
          startY = -body.height * viewport.scaleY * 0.55f,
          endY = body.height * viewport.scaleY * 0.65f,
        ),
        alpha = alphaMultiplier,
      )

      clipPath(outlinePath) {
        lobePaths.forEachIndexed { index, lobePath ->
          val lobeMix = (index / max(lobePaths.lastIndex, 1).toFloat()).coerceIn(0f, 1f)
          drawPath(
            path = lobePath,
            brush = Brush.radialGradient(
              colors = listOf(
                mixColor(palette.highlight, palette.base, 0.18f + (lobeMix * 0.18f)),
                mixColor(palette.mid, palette.base, 0.48f),
                mixColor(palette.shadow, palette.mid, 0.22f + (lobeMix * 0.18f)),
              ),
              center = Offset(
                x = (-body.width * viewport.scaleX * 0.18f) + (index * body.width * viewport.scaleX * 0.10f),
                y = (-body.height * viewport.scaleY * 0.12f) + (index * body.height * viewport.scaleY * 0.05f),
              ),
              radius = max(body.width * viewport.scaleX, body.height * viewport.scaleY) * 0.82f,
            ),
            alpha = alphaMultiplier * 0.92f,
          )
        }

        repeat(4) { blotchIndex ->
          val blotchSeed = body.id * 31 + (blotchIndex * 17)
          val blotchX = sequenceFloat(blotchSeed, 7, -0.34f, 0.34f) * body.width * viewport.scaleX
          val blotchY = sequenceFloat(blotchSeed, 13, -0.24f, 0.26f) * body.height * viewport.scaleY
          val blotchRadius = body.width * viewport.scaleX *
            sequenceFloat(blotchSeed, 19, 0.07f, 0.16f)
          val blotchColor = if (blotchIndex == 1 && body.id % 3 == 0) {
            palette.accent
          } else if (blotchIndex == 2 && body.id % 4 == 0) {
            palette.moss
          } else {
            mixColor(palette.shadow, palette.mid, 0.34f)
          }
          drawCircle(
            color = blotchColor,
            center = Offset(blotchX, blotchY),
            radius = blotchRadius,
            alpha = alphaMultiplier * if (blotchColor == palette.accent) 0.34f else 0.16f,
          )
        }

        repeat(16) { speckIndex ->
          val speckSeed = body.id * 101 + speckIndex
          drawCircle(
            color = mixColor(palette.shadow, palette.base, 0.18f),
            center = Offset(
              x = sequenceFloat(speckSeed, 29, -0.46f, 0.46f) * body.width * viewport.scaleX,
              y = sequenceFloat(speckSeed, 37, -0.34f, 0.34f) * body.height * viewport.scaleY,
            ),
            radius = sequenceFloat(speckSeed, 41, 0.8f, 1.9f),
            alpha = alphaMultiplier * 0.20f,
          )
        }

        repeat(2) { crackIndex ->
          val crackSeed = body.id * 43 + (crackIndex * 23)
          val start = Offset(
            x = sequenceFloat(crackSeed, 47, -0.30f, 0.18f) * body.width * viewport.scaleX,
            y = sequenceFloat(crackSeed, 53, -0.08f, 0.18f) * body.height * viewport.scaleY,
          )
          val mid = Offset(
            x = start.x + (sequenceFloat(crackSeed, 59, 0.08f, 0.24f) * body.width * viewport.scaleX),
            y = start.y + (sequenceFloat(crackSeed, 61, -0.16f, 0.10f) * body.height * viewport.scaleY),
          )
          val end = Offset(
            x = mid.x + (sequenceFloat(crackSeed, 67, 0.05f, 0.18f) * body.width * viewport.scaleX),
            y = mid.y + (sequenceFloat(crackSeed, 71, -0.10f, 0.12f) * body.height * viewport.scaleY),
          )
          val crack = Path().apply {
            moveTo(start.x, start.y)
            quadraticTo(mid.x, mid.y, end.x, end.y)
          }
          drawPath(
            path = crack,
            color = palette.ink,
            style = Stroke(
              width = if (body.id == 0) 2.2f else 1.4f,
              cap = StrokeCap.Round,
            ),
            alpha = alphaMultiplier * 0.42f,
          )
        }

        val glowHeight = body.height * viewport.scaleY * 0.24f
        drawRect(
          brush = Brush.verticalGradient(
            colors = listOf(
              Color(0x30FFF7E5),
              Color.Transparent,
            ),
            startY = -body.height * viewport.scaleY * 0.52f,
            endY = -body.height * viewport.scaleY * 0.12f,
          ),
          topLeft = Offset(-body.width * viewport.scaleX * 0.56f, -body.height * viewport.scaleY * 0.54f),
          size = androidx.compose.ui.geometry.Size(body.width * viewport.scaleX * 1.12f, glowHeight),
          alpha = alphaMultiplier,
          blendMode = BlendMode.Screen,
        )
      }

      drawPath(
        path = outlinePath,
        color = mixColor(palette.shadow, palette.ink, 0.32f),
        style = Stroke(width = if (body.id == 0) 4.8f else 3.1f),
        alpha = alphaMultiplier * 0.38f,
      )
      if (body.active) {
        drawPath(
          path = outlinePath,
          color = Color(0x30FFF3D4),
          style = Stroke(width = 2.4f),
          alpha = alphaMultiplier,
        )
      }
    }
  }
}

private fun localPath(
  vertices: List<TowerPoint>,
  viewport: TowerViewport,
): Path {
  return Path().apply {
    vertices.forEachIndexed { index, point ->
      val x = point.x * viewport.scaleX
      val y = point.y * viewport.scaleY
      if (index == 0) {
        moveTo(x, y)
      } else {
        lineTo(x, y)
      }
    }
    close()
  }
}

private fun paletteFor(body: TowerBodySnapshot): StonePalette {
  if (body.id == 0) {
    return StonePalette(
      base = Color(0xFF8C7459),
      mid = Color(0xFF77614A),
      shadow = Color(0xFF4D3D30),
      highlight = Color(0xFFC3B194),
      accent = Color(0xFFE6A552),
      moss = Color(0xFF7F8A67),
      ink = Color(0xFF2A221B),
    )
  }
  val palettes = listOf(
    StonePalette(
      base = Color(0xFFB1A18A),
      mid = Color(0xFF93836F),
      shadow = Color(0xFF665647),
      highlight = Color(0xFFD2C4AE),
      accent = Color(0xFFE38F45),
      moss = Color(0xFF8A9775),
      ink = Color(0xFF332A23),
    ),
    StonePalette(
      base = Color(0xFF9AA08D),
      mid = Color(0xFF7F8471),
      shadow = Color(0xFF535846),
      highlight = Color(0xFFBEC5B6),
      accent = Color(0xFFE6A55D),
      moss = Color(0xFF6B7C59),
      ink = Color(0xFF2D3128),
    ),
    StonePalette(
      base = Color(0xFFA18A78),
      mid = Color(0xFF866E5A),
      shadow = Color(0xFF584739),
      highlight = Color(0xFFD0BBA7),
      accent = Color(0xFFD98F49),
      moss = Color(0xFF8C8A68),
      ink = Color(0xFF31261F),
    ),
    StonePalette(
      base = Color(0xFF7D8786),
      mid = Color(0xFF687170),
      shadow = Color(0xFF47504F),
      highlight = Color(0xFFAAB2B1),
      accent = Color(0xFFE18F4A),
      moss = Color(0xFF7B8660),
      ink = Color(0xFF262E2D),
    ),
    StonePalette(
      base = Color(0xFF9B847E),
      mid = Color(0xFF806A63),
      shadow = Color(0xFF58433E),
      highlight = Color(0xFFC9B2AB),
      accent = Color(0xFFE18B4B),
      moss = Color(0xFF8E9271),
      ink = Color(0xFF322723),
    ),
  )
  val paletteIndex = abs((body.id * 7) + body.shapeKind.ordinal).mod(palettes.size)
  return palettes[paletteIndex]
}

private fun mixColor(from: Color, to: Color, amount: Float): Color {
  val clamped = amount.coerceIn(0f, 1f)
  return Color(
    red = from.red + ((to.red - from.red) * clamped),
    green = from.green + ((to.green - from.green) * clamped),
    blue = from.blue + ((to.blue - from.blue) * clamped),
    alpha = from.alpha + ((to.alpha - from.alpha) * clamped),
  )
}

private fun sequenceFloat(
  seed: Int,
  salt: Int,
  min: Float,
  max: Float,
): Float {
  val mixed = seed xor (salt * 0x45D9F3B)
  val hashed = (mixed * 0x27D4EB2D) xor (mixed ushr 15)
  val normalized = (hashed and 0x7FFFFFFF).toFloat() / Int.MAX_VALUE.toFloat()
  return min + ((max - min) * normalized.coerceIn(0f, 1f))
}

private data class TowerViewport(
  val left: Float,
  val right: Float,
  val top: Float,
  val bottom: Float,
  val canvasWidth: Float,
  val canvasHeight: Float,
) {
  val scaleX: Float get() = canvasWidth / (right - left)
  val scaleY: Float get() = canvasHeight / (bottom - top)

  fun worldToScreen(
    x: Float,
    y: Float,
  ): Offset {
    return Offset(
      x = worldToScreenX(x),
      y = worldToScreenY(y),
    )
  }

  fun worldToScreenX(x: Float): Float = ((x - left) / (right - left)) * canvasWidth

  fun worldToScreenY(y: Float): Float = ((y - top) / (bottom - top)) * canvasHeight
}

private data class StonePalette(
  val base: Color,
  val mid: Color,
  val shadow: Color,
  val highlight: Color,
  val accent: Color,
  val moss: Color,
  val ink: Color,
)

private const val VIEWPORT_HEIGHT = 11.4f
private const val SINK_THRESHOLD_RATIO = 0.60f
