package com.mordin.samathascope

data class Calibration(
  val raiP10: Float,
  val raiP90: Float,
  val aP10: Float,
  val aP90: Float,
)

class CalibrationManager(
  val calibrationSeconds: Int = 60,
  private val pointsPerSecond: Int = 4,
  private val adaptiveWindowSeconds: Int = 600,
  private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
  private var startedAtMs: Long = 0
  private val rai = ArrayList<Float>(calibrationSeconds * pointsPerSecond)
  private val a = ArrayList<Float>(calibrationSeconds * pointsPerSecond)

  private val adaptiveCapacity = (adaptiveWindowSeconds * pointsPerSecond).coerceAtLeast(40)
  private val adaptiveRai = ArrayDeque<Float>(adaptiveCapacity)
  private val adaptiveA = ArrayDeque<Float>(adaptiveCapacity)

  fun reset() {
    startedAtMs = nowMs()
    rai.clear()
    a.clear()
    adaptiveRai.clear()
    adaptiveA.clear()
  }

  fun addSample(rai: Float, artefactA: Float) {
    if (startedAtMs == 0L) startedAtMs = nowMs()
    if (isDone()) return
    this.rai.add(rai)
    this.a.add(artefactA)
  }

  fun remainingSeconds(): Int {
    if (startedAtMs == 0L) return calibrationSeconds
    val elapsed = ((nowMs() - startedAtMs) / 1000).toInt()
    return (calibrationSeconds - elapsed).coerceAtLeast(0)
  }

  fun isDone(): Boolean = remainingSeconds() <= 0 && rai.size >= 20

  fun buildCalibration(): Calibration {
    val raiP10 = percentile(rai, 0.10f)
    val raiP90 = percentile(rai, 0.90f)
    val aP10 = percentile(a, 0.10f)
    val aP90 = percentile(a, 0.90f)
    return Calibration(
      raiP10 = raiP10,
      raiP90 = if (raiP90 == raiP10) raiP10 + 1e-3f else raiP90,
      aP10 = aP10,
      aP90 = if (aP90 == aP10) aP10 + 1e-3f else aP90
    )
  }

  fun seedAdaptiveWindowFromCalibration() {
    if (adaptiveRai.isNotEmpty()) return
    for (i in rai.indices) {
      pushAdaptive(rai = rai[i], artefactA = a.getOrElse(i) { 0f })
    }
  }

  fun addAdaptiveSample(rai: Float, artefactA: Float, poorSignal: Int) {
    if (!isDone()) return
    if (poorSignal > 50) return
    if (artefactA > 0.30f) return
    pushAdaptive(rai = rai, artefactA = artefactA)
  }

  fun buildAdaptiveCalibration(): Calibration? {
    if (adaptiveRai.size < 20) return null
    val raiList = adaptiveRai.toList()
    val aList = adaptiveA.toList()
    val raiP10 = percentile(raiList, 0.10f)
    val raiP90 = percentile(raiList, 0.90f)
    val aP10 = percentile(aList, 0.10f)
    val aP90 = percentile(aList, 0.90f)
    return Calibration(
      raiP10 = raiP10,
      raiP90 = if (raiP90 == raiP10) raiP10 + 1e-3f else raiP90,
      aP10 = aP10,
      aP90 = if (aP90 == aP10) aP10 + 1e-3f else aP90,
    )
  }

  fun adaptiveSampleCount(): Int = adaptiveRai.size

  private fun pushAdaptive(rai: Float, artefactA: Float) {
    if (adaptiveRai.size >= adaptiveCapacity) {
      adaptiveRai.removeFirst()
      adaptiveA.removeFirst()
    }
    adaptiveRai.addLast(rai)
    adaptiveA.addLast(artefactA)
  }

  private fun percentile(list: List<Float>, p: Float): Float {
    if (list.isEmpty()) return 0f
    val sorted = list.sorted()
    val idx = (p * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
    return sorted[idx]
  }
}
