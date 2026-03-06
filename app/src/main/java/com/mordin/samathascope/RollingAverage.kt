package com.mordin.samathascope

/**
 * Fixed-size moving average with O(1) updates.
 */
class RollingAverage(
  private val capacity: Int,
) {
  private val values = ArrayDeque<Float>(capacity.coerceAtLeast(1))
  private var sum = 0.0

  fun reset() {
    values.clear()
    sum = 0.0
  }

  fun add(value: Float): Float {
    if (values.size >= capacity) {
      sum -= values.removeFirst()
    }
    values.addLast(value)
    sum += value
    return average()
  }

  fun average(): Float {
    if (values.isEmpty()) return 0f
    return (sum / values.size.toDouble()).toFloat()
  }

  fun size(): Int = values.size
}

