package com.mordin.samathascope

/**
 * Collects one debug raw loop from the live headset: [sampleCount] raw samples plus the averages of
 * the headset's poor-signal and eSense values over the same stretch. Not thread-safe; the caller
 * serialises access.
 */
internal class DebugRawLoopCapture(sampleCount: Int) {
  private val buffer = IntArray(sampleCount)
  private var count = 0
  private val poorSignal = RunningAverage()
  private val attention = RunningAverage()
  private val meditation = RunningAverage()

  val samplesCaptured: Int get() = count
  val isFull: Boolean get() = count >= buffer.size

  fun reset() {
    count = 0
    poorSignal.reset()
    attention.reset()
    meditation.reset()
  }

  /** Adds a raw sample; ignored once the loop is full. */
  fun addSample(raw: Int) {
    if (isFull) return
    buffer[count++] = raw
  }

  fun addPoorSignal(value: Int) = poorSignal.add(value.coerceIn(0, 255))
  fun addAttention(value: Int) = attention.add(value.coerceIn(0, 100))
  fun addMeditation(value: Int) = meditation.add(value.coerceIn(0, 100))

  /** The captured loop; values never reported during the capture fall back to [fallback]. */
  fun buildRecord(fallback: DebugRawLoopMetadata): DebugRawLoopRecord {
    return DebugRawLoopRecord(
      samples = buffer.copyOf(count),
      metadata = DebugRawLoopMetadata(
        poorSignal = poorSignal.averageOr(fallback.poorSignal).coerceIn(0, 255),
        attention = attention.averageOr(fallback.attention).coerceIn(0, 100),
        meditation = meditation.averageOr(fallback.meditation).coerceIn(0, 100),
      ),
    )
  }

  private class RunningAverage {
    private var sum = 0L
    private var count = 0

    fun add(value: Int) {
      sum += value
      count++
    }

    fun reset() {
      sum = 0L
      count = 0
    }

    fun averageOr(fallback: Int): Int = if (count == 0) fallback else (sum / count).toInt()
  }
}
