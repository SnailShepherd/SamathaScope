package com.mordin.samathascope

data class PlotPoint(
  val x: Float,
  val y: Float,
)

object PlotMath {
  fun <T> takeFixedWindow(values: List<T>, maxPoints: Int): List<T> {
    return takeWindow(values = values, maxPoints = maxPoints, offsetPoints = 0)
  }

  fun <T> takeWindow(values: List<T>, maxPoints: Int, offsetPoints: Int): List<T> {
    if (maxPoints <= 0 || values.isEmpty()) return emptyList()
    val safeOffset = offsetPoints.coerceAtLeast(0)
    val endExclusive = (values.size - safeOffset).coerceAtLeast(0)
    if (endExclusive <= 0) return emptyList()
    val startInclusive = (endExclusive - maxPoints).coerceAtLeast(0)
    return values.subList(startInclusive, endExclusive)
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
