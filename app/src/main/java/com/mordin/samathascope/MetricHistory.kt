package com.mordin.samathascope

class MetricHistory(
  private val maxSeconds: Int,
  private val pointsPerSecond: Int
) {
  private val capacity = (maxSeconds * pointsPerSecond).coerceAtLeast(8)

  private val samatha = ArrayList<Float>(capacity)
  private val artefact = ArrayList<Float>(capacity)
  private val relaxedAlertness = ArrayList<Float>(capacity)
  private val meditation = ArrayList<Float>(capacity)
  private val attention = ArrayList<Float>(capacity)

  fun reset() {
    samatha.clear()
    artefact.clear()
    relaxedAlertness.clear()
    meditation.clear()
    attention.clear()
  }

  fun add(
    samathaScore: Float,
    artefactScore: Float,
    relaxedAlertnessIndex: Float,
    meditationValue: Int,
    attentionValue: Int,
  ) {
    push(samatha, samathaScore)
    push(artefact, artefactScore)
    push(relaxedAlertness, relaxedAlertnessIndex)
    push(meditation, meditationValue.toFloat())
    push(attention, attentionValue.toFloat())
  }

  fun series(type: PlotType, windowSeconds: Int): List<Float> {
    val maxPoints = (windowSeconds * pointsPerSecond).coerceAtLeast(2)
    val source = when (type) {
      PlotType.RAW -> emptyList()
      PlotType.SAMATHA_SCORE -> samatha
      PlotType.ARTEFACT_SCORE -> artefact
      PlotType.RELAXED_ALERTNESS_INDEX -> relaxedAlertness
      PlotType.ESENSE_MEDITATION -> meditation
      PlotType.ESENSE_ATTENTION -> attention
    }
    return PlotMath.takeFixedWindow(source, maxPoints)
  }

  private fun push(list: ArrayList<Float>, value: Float) {
    if (list.size >= capacity) list.removeAt(0)
    list.add(value)
  }
}
