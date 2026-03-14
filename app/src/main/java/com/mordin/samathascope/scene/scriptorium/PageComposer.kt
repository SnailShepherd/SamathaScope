package com.mordin.samathascope.scene.scriptorium

import kotlin.math.sin

enum class InkBrushStyle {
  TEXT,
  FLOURISH,
  GLOW,
}

data class InkPoint(
  val x: Float,
  val y: Float,
  val pressure: Float,
)

data class StrokeRecipe(
  val id: Int,
  val unlockProgress: Float,
  val style: InkBrushStyle,
  val colorArgb: Int,
  val baseSize: Float,
  val points: List<InkPoint>,
)

class PageComposer {
  fun composePage(): List<StrokeRecipe> {
    val recipes = ArrayList<StrokeRecipe>()
    var nextId = 0

    recipes += StrokeRecipe(
      id = nextId++,
      unlockProgress = 0.00f,
      style = InkBrushStyle.TEXT,
      colorArgb = 0xFF24150D.toInt(),
      baseSize = 0.020f,
      points = textLine(startY = 0.24f, phase = 0f),
    )
    recipes += StrokeRecipe(
      id = nextId++,
      unlockProgress = 0.02f,
      style = InkBrushStyle.FLOURISH,
      colorArgb = 0xFF5E3822.toInt(),
      baseSize = 0.013f,
      points = underline(startY = 0.278f, phase = 0f),
    )
    recipes += StrokeRecipe(
      id = nextId++,
      unlockProgress = 0.05f,
      style = InkBrushStyle.FLOURISH,
      colorArgb = 0xFFB89235.toInt(),
      baseSize = 0.024f,
      points = ornamentArc(
        cx = 0.18f,
        cy = 0.14f,
        radius = 0.08f,
        turns = 1.3f,
      ),
    )
    recipes += StrokeRecipe(
      id = nextId++,
      unlockProgress = 0.08f,
      style = InkBrushStyle.GLOW,
      colorArgb = 0x66EFD998,
      baseSize = 0.040f,
      points = ornamentArc(
        cx = 0.18f,
        cy = 0.14f,
        radius = 0.11f,
        turns = 1.1f,
      ),
    )

    repeat(4) { index ->
      val line = index + 1
      val startY = 0.24f + (line * 0.13f)
      recipes += StrokeRecipe(
        id = nextId++,
        unlockProgress = 0.12f + (index * 0.10f),
        style = InkBrushStyle.TEXT,
        colorArgb = 0xFF332116.toInt(),
        baseSize = 0.017f,
        points = textLine(startY, phase = line * 0.7f),
      )
      recipes += StrokeRecipe(
        id = nextId++,
        unlockProgress = 0.16f + (index * 0.10f),
        style = InkBrushStyle.FLOURISH,
        colorArgb = 0xFF5E3822.toInt(),
        baseSize = 0.012f,
        points = underline(startY + 0.038f, phase = line * 1.4f),
      )
    }

    recipes += StrokeRecipe(
      id = nextId,
      unlockProgress = 0.56f,
      style = InkBrushStyle.FLOURISH,
      colorArgb = 0xFFB89235.toInt(),
      baseSize = 0.020f,
      points = ornamentArc(
        cx = 0.84f,
        cy = 0.83f,
        radius = 0.09f,
        turns = 1.5f,
      ),
    )

    return recipes
  }

  private fun textLine(startY: Float, phase: Float): List<InkPoint> {
    val points = ArrayList<InkPoint>(56)
    for (index in 0 until 56) {
      val t = index / 55f
      val x = 0.10f + (t * 0.76f)
      val y = startY +
        (sin(((t * 9.0f) + phase).toDouble()).toFloat() * 0.010f) +
        (sin(((t * 27.0f) + phase * 0.5f).toDouble()).toFloat() * 0.0035f)
      val pressure = 0.46f + (sin(((t * 14.0f) + phase).toDouble()).toFloat() * 0.18f)
      points += InkPoint(x = x, y = y, pressure = pressure.coerceIn(0.24f, 0.88f))
    }
    return points
  }

  private fun underline(startY: Float, phase: Float): List<InkPoint> {
    val points = ArrayList<InkPoint>(24)
    for (index in 0 until 24) {
      val t = index / 23f
      val x = 0.12f + (t * 0.68f)
      val y = startY + (sin(((t * 6.2f) + phase).toDouble()).toFloat() * 0.012f)
      points += InkPoint(
        x = x,
        y = y,
        pressure = (0.36f + (sin((t * 3.14f).toDouble()).toFloat() * 0.12f)).coerceIn(0.2f, 0.6f),
      )
    }
    return points
  }

  private fun ornamentArc(
    cx: Float,
    cy: Float,
    radius: Float,
    turns: Float,
  ): List<InkPoint> {
    val points = ArrayList<InkPoint>(42)
    for (index in 0 until 42) {
      val t = index / 41f
      val angle = t * turns * (Math.PI * 2.0)
      val grow = 0.4f + (t * 0.6f)
      points += InkPoint(
        x = cx + (kotlin.math.cos(angle).toFloat() * radius * grow),
        y = cy + (kotlin.math.sin(angle).toFloat() * radius * grow * 0.62f),
        pressure = (0.30f + (t * 0.34f)).coerceIn(0.18f, 0.8f),
      )
    }
    return points
  }
}
