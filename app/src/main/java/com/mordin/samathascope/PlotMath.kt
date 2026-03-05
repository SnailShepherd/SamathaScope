package com.mordin.samathascope

data class PlotPoint(
  val x: Float,
  val y: Float,
)

object PlotMath {
  fun takeFixedWindow(values: List<Float>, maxPoints: Int): List<Float> {
    if (maxPoints <= 0) return emptyList()
    if (values.size <= maxPoints) return values
    return values.takeLast(maxPoints)
  }

  fun takeFixedWindow(values: List<Int>, maxPoints: Int): List<Int> {
    if (maxPoints <= 0) return emptyList()
    if (values.size <= maxPoints) return values
    return values.takeLast(maxPoints)
  }

  fun toPlotPoints(
    values: List<Float>,
    yMin: Float,
    yMax: Float,
    width: Float,
    height: Float,
  ): List<PlotPoint> {
    if (values.size < 2) return emptyList()
    val safeMax = if (yMax <= yMin) yMin + 1f else yMax
    val step = width / (values.size - 1).toFloat()
    return values.mapIndexed { index, value ->
      val clamped = value.coerceIn(yMin, safeMax)
      val x = index * step
      val y = height - ((clamped - yMin) / (safeMax - yMin)) * height
      PlotPoint(x = x, y = y)
    }
  }

  fun defaultRawRange(): Pair<Float, Float> = -1200f to 1200f

  fun calibratedRawRangeFromSd(sd: Double): Pair<Float, Float> {
    val amplitude = (sd * 6.0).coerceIn(300.0, 3500.0).toFloat()
    return -amplitude to amplitude
  }
}
