package com.mordin.samathascope

data class PlotPoint(
  val x: Float,
  val y: Float,
)

/**
 * Turns horizontal drag distance into whole pan steps (seconds), keeping the sub-step remainder so
 * slow drags still pan and fast drags pan proportionally.
 */
class DragPanAccumulator(private val pixelsPerStep: Float = 18f) {
  private var carry = 0f

  fun add(dragPx: Float): Int {
    carry += dragPx
    val steps = (carry / pixelsPerStep).toInt()
    carry -= steps * pixelsPerStep
    return steps
  }
}

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

  /**
   * Shrinks [values] to at most [maxPoints] for drawing. Each bucket contributes its minimum and
   * maximum, in the order they occur, so spikes such as blinks survive decimation.
   */
  fun minMaxDecimate(values: IntArray, maxPoints: Int): IntArray {
    if (values.size <= maxPoints || maxPoints < 2) return values
    val buckets = maxPoints / 2
    val out = IntArray(buckets * 2)
    for (b in 0 until buckets) {
      val start = (b.toLong() * values.size / buckets).toInt()
      val end = ((b + 1).toLong() * values.size / buckets).toInt()
      var minIndex = start
      var maxIndex = start
      for (i in start until end) {
        if (values[i] < values[minIndex]) minIndex = i
        if (values[i] > values[maxIndex]) maxIndex = i
      }
      out[b * 2] = values[minOf(minIndex, maxIndex)]
      out[b * 2 + 1] = values[maxOf(minIndex, maxIndex)]
    }
    return out
  }

  fun defaultRawRange(): Pair<Float, Float> = -1200f to 1200f

  fun calibratedRawRangeFromSd(sd: Double): Pair<Float, Float> {
    val amplitude = (sd * 6.0).coerceIn(300.0, 3500.0).toFloat()
    return -amplitude to amplitude
  }
}
