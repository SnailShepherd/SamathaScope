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

data class InkGardenSceneSettings(
  val enhancedFxEnabled: Boolean = DEFAULT_ENHANCED_FX_ENABLED,
  val ghostTrailsEnabled: Boolean = DEFAULT_GHOST_TRAILS_ENABLED,
  val goldDustEnabled: Boolean = DEFAULT_GOLD_DUST_ENABLED,
  val effectTriggerThreshold: Float = DEFAULT_EFFECT_TRIGGER_THRESHOLD,
  val effectStrength: Float = DEFAULT_EFFECT_STRENGTH,
) {
  fun clamped(): InkGardenSceneSettings {
    return copy(
      effectTriggerThreshold = effectTriggerThreshold.coerceIn(EFFECT_TRIGGER_THRESHOLD_RANGE.start, EFFECT_TRIGGER_THRESHOLD_RANGE.endInclusive),
      effectStrength = effectStrength.coerceIn(EFFECT_STRENGTH_RANGE.start, EFFECT_STRENGTH_RANGE.endInclusive),
    )
  }

  companion object {
    val EFFECT_TRIGGER_THRESHOLD_RANGE = 0.35f..0.90f
    val EFFECT_STRENGTH_RANGE = 0.10f..1.00f

    const val DEFAULT_ENHANCED_FX_ENABLED = false
    const val DEFAULT_GHOST_TRAILS_ENABLED = true
    const val DEFAULT_GOLD_DUST_ENABLED = true
    const val DEFAULT_EFFECT_TRIGGER_THRESHOLD = 0.62f
    const val DEFAULT_EFFECT_STRENGTH = 0.48f
  }
}

data class InkGardenUiState(
  val refreshMode: InkGardenRefreshMode = InkGardenRefreshMode.AUTO,
  val sceneSettings: InkGardenSceneSettings = InkGardenSceneSettings(),
  val pictureVersion: Int = 0,
  val compositionSeed: Int = 0,
  val richness: Float = 0f,
  val growthActive: Boolean = false,
  val motifName: String? = null,
  val brushPresetName: String? = null,
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
