package com.mordin.samathascope

import kotlin.math.max
import kotlin.system.measureTimeMillis

data class Calibration(
  val raiP10: Float,
  val raiP90: Float,
  val aP10: Float,
  val aP90: Float,
)

class CalibrationManager(
  val calibrationSeconds: Int = 60
) {
  private val startMs = System.currentTimeMillis()
  private var startedAtMs: Long = 0
  private val rai = ArrayList<Float>(calibrationSeconds * 8)
  private val a = ArrayList<Float>(calibrationSeconds * 8)

  fun reset() {
    startedAtMs = System.currentTimeMillis()
    rai.clear()
    a.clear()
  }

  fun addSample(rai: Float, artefactA: Float) {
    if (isDone()) return
    this.rai.add(rai)
    this.a.add(artefactA)
  }

  fun remainingSeconds(): Int {
    if (startedAtMs == 0L) return calibrationSeconds
    val elapsed = ((System.currentTimeMillis() - startedAtMs) / 1000).toInt()
    return (calibrationSeconds - elapsed).coerceAtLeast(0)
  }

  fun isDone(): Boolean = remainingSeconds() <= 0 && rai.size >= 20

  fun buildCalibration(): Calibration {
    fun pct(list: List<Float>, p: Float): Float {
      if (list.isEmpty()) return 0f
      val sorted = list.sorted()
      val idx = ((p * (sorted.size - 1))).toInt().coerceIn(0, sorted.size - 1)
      return sorted[idx]
    }
    val raiP10 = pct(rai, 0.10f)
    val raiP90 = pct(rai, 0.90f)
    val aP10 = pct(a, 0.10f)
    val aP90 = pct(a, 0.90f)
    return Calibration(
      raiP10 = raiP10,
      raiP90 = if (raiP90 == raiP10) raiP10 + 1e-3f else raiP90,
      aP10 = aP10,
      aP90 = if (aP90 == aP10) aP10 + 1e-3f else aP90
    )
  }
}
