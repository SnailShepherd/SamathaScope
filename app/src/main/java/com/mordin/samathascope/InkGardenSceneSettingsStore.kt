package com.mordin.samathascope

import android.content.Context

class InkGardenSceneSettingsStore(private val store: KeyValueStore) {
  fun load(): InkGardenSceneSettings {
    return InkGardenSceneSettings(
      enhancedFxEnabled = store.getBoolean(KEY_ENHANCED_FX_ENABLED, InkGardenSceneSettings.DEFAULT_ENHANCED_FX_ENABLED),
      ghostTrailsEnabled = store.getBoolean(KEY_GHOST_TRAILS_ENABLED, InkGardenSceneSettings.DEFAULT_GHOST_TRAILS_ENABLED),
      goldDustEnabled = store.getBoolean(KEY_GOLD_DUST_ENABLED, InkGardenSceneSettings.DEFAULT_GOLD_DUST_ENABLED),
      effectTriggerThreshold = store.getFloat(KEY_EFFECT_TRIGGER_THRESHOLD, InkGardenSceneSettings.DEFAULT_EFFECT_TRIGGER_THRESHOLD),
      effectStrength = store.getFloat(KEY_EFFECT_STRENGTH, InkGardenSceneSettings.DEFAULT_EFFECT_STRENGTH),
    ).clamped()
  }

  fun save(settings: InkGardenSceneSettings) {
    val clamped = settings.clamped()
    store.putBoolean(KEY_ENHANCED_FX_ENABLED, clamped.enhancedFxEnabled)
    store.putBoolean(KEY_GHOST_TRAILS_ENABLED, clamped.ghostTrailsEnabled)
    store.putBoolean(KEY_GOLD_DUST_ENABLED, clamped.goldDustEnabled)
    store.putFloat(KEY_EFFECT_TRIGGER_THRESHOLD, clamped.effectTriggerThreshold)
    store.putFloat(KEY_EFFECT_STRENGTH, clamped.effectStrength)
    store.apply()
  }

  private companion object {
    const val KEY_ENHANCED_FX_ENABLED = "ink_garden_enhanced_fx_enabled"
    const val KEY_GHOST_TRAILS_ENABLED = "ink_garden_ghost_trails_enabled"
    const val KEY_GOLD_DUST_ENABLED = "ink_garden_gold_dust_enabled"
    const val KEY_EFFECT_TRIGGER_THRESHOLD = "ink_garden_effect_trigger_threshold"
    const val KEY_EFFECT_STRENGTH = "ink_garden_effect_strength"
  }
}

fun createInkGardenSceneSettingsStore(context: Context): InkGardenSceneSettingsStore {
  val prefs = context.getSharedPreferences("samathascope_settings", Context.MODE_PRIVATE)
  return InkGardenSceneSettingsStore(SharedPreferencesStore(prefs))
}