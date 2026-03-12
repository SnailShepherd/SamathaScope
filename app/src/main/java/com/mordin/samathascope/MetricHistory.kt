package com.mordin.samathascope

class MetricHistory(
  private val maxSeconds: Int,
  private val pointsPerSecond: Int,
) {
  private val capacity = (maxSeconds * pointsPerSecond).coerceAtLeast(8)
  private val sources = PlotType.entries
    .filter { it != PlotType.RAW }
    .associateWith { ArrayList<Float>(capacity) }
    .toMutableMap()

  fun reset() {
    sources.values.forEach { it.clear() }
  }

  fun add(values: Map<PlotType, Float>) {
    for ((type, series) in sources) {
      push(series, values[type] ?: 0f)
    }
  }

  fun series(type: PlotType, windowSeconds: Int): List<Float> {
    if (type == PlotType.RAW) return emptyList()
    val maxPoints = (windowSeconds * pointsPerSecond).coerceAtLeast(2)
    val source = sources[type] ?: return emptyList()
    return PlotMath.takeFixedWindow(source, maxPoints)
  }

  private fun push(list: ArrayList<Float>, value: Float) {
    if (list.size >= capacity) list.removeAt(0)
    list += value
  }
}
