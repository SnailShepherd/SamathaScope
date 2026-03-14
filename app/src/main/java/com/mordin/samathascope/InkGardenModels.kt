package com.mordin.samathascope

enum class InkGardenRefreshMode(val storageValue: String) {
  AUTO("auto"),
  MANUAL("manual");

  companion object {
    fun fromStorage(value: String?): InkGardenRefreshMode {
      return entries.firstOrNull { it.storageValue == value } ?: AUTO
    }
  }
}

data class InkGardenUiState(
  val refreshMode: InkGardenRefreshMode = InkGardenRefreshMode.AUTO,
  val pictureVersion: Int = 0,
  val compositionSeed: Int = 0,
  val richness: Float = 0f,
  val growthActive: Boolean = false,
  val motifName: String? = null,
)

internal data class InkGardenRefreshTracker(
  val peakRichness: Float = 0f,
  val lastImprovementAtMs: Long = 0L,
)

internal fun updateInkGardenRefreshTracker(
  tracker: InkGardenRefreshTracker,
  richness: Float,
  nowMs: Long,
  improvementThreshold: Float = 0.015f,
): InkGardenRefreshTracker {
  val normalizedRichness = richness.coerceIn(0f, 1f)
  return if (normalizedRichness >= tracker.peakRichness + improvementThreshold) {
    InkGardenRefreshTracker(
      peakRichness = normalizedRichness,
      lastImprovementAtMs = nowMs,
    )
  } else {
    tracker
  }
}

internal fun shouldAutoRefreshInkGarden(
  mode: InkGardenRefreshMode,
  eligible: Boolean,
  richness: Float,
  tracker: InkGardenRefreshTracker,
  nowMs: Long,
  minRichness: Float = 0.70f,
  plateauMs: Long = 5_000L,
): Boolean {
  if (mode != InkGardenRefreshMode.AUTO || !eligible) {
    return false
  }
  if (richness < minRichness) {
    return false
  }
  if (tracker.lastImprovementAtMs == 0L) {
    return false
  }
  return nowMs - tracker.lastImprovementAtMs >= plateauMs
}
