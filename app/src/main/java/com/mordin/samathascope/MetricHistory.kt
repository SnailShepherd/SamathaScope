package com.mordin.samathascope

import kotlin.math.roundToInt

/**
 * Lightweight fixed-length time series storage for live plots.
 *
 * v0.1 goal: show a stable, usable chart without allocating tons of objects or keeping hours of data in RAM.
 *
 * We store points at a fixed rate (pointsPerSecond) and keep maxSeconds of history.
 * The UI receives plain Float lists for drawing.
 */
class MetricHistory(
  private val maxSeconds: Int,
  private val pointsPerSecond: Int
) {
  private val capacity = (maxSeconds * pointsPerSecond).coerceAtLeast(8)

  private val s = ArrayList<Float>(capacity)
  private val a = ArrayList<Float>(capacity)
  private val rai = ArrayList<Float>(capacity)
  private val med = ArrayList<Float>(capacity)
  private val att = ArrayList<Float>(capacity)

  fun reset() {
    s.clear(); a.clear(); rai.clear(); med.clear(); att.clear()
  }

  fun add(scoreS: Float, scoreA: Float, rai: Float, meditation: Int, attention: Int) {
    push(s, scoreS)
    push(a, scoreA)
    push(this.rai, rai)
    push(med, meditation.toFloat())
    push(att, attention.toFloat())
  }

  fun seriesS(): List<Float> = s.toList()
  fun seriesA(): List<Float> = a.toList()
  fun seriesRai(): List<Float> = rai.toList()
  fun seriesMeditation(): List<Float> = med.toList()
  fun seriesAttention(): List<Float> = att.toList()

  private fun push(list: ArrayList<Float>, v: Float) {
    if (list.size >= capacity) list.removeAt(0)
    list.add(v)
  }
}
