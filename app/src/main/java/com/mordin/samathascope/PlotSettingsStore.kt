package com.mordin.samathascope

import android.content.Context
import android.content.SharedPreferences
import com.mordin.samathascope.scene.tower.SkyTowerSettings

interface KeyValueStore {
  fun getInt(key: String, defaultValue: Int): Int
  fun getFloat(key: String, defaultValue: Float): Float
  fun getBoolean(key: String, defaultValue: Boolean): Boolean
  fun getString(key: String, defaultValue: String): String
  fun putInt(key: String, value: Int)
  fun putFloat(key: String, value: Float)
  fun putBoolean(key: String, value: Boolean)
  fun putString(key: String, value: String)
  fun apply()
}

class SharedPreferencesStore(private val sharedPreferences: SharedPreferences) : KeyValueStore {
  private val editor: SharedPreferences.Editor
    get() = sharedPreferences.edit()

  override fun getInt(key: String, defaultValue: Int): Int = sharedPreferences.getInt(key, defaultValue)

  override fun getFloat(key: String, defaultValue: Float): Float = sharedPreferences.getFloat(key, defaultValue)

  override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
    sharedPreferences.getBoolean(key, defaultValue)

  override fun getString(key: String, defaultValue: String): String =
    sharedPreferences.getString(key, defaultValue) ?: defaultValue

  override fun putInt(key: String, value: Int) {
    editor.putInt(key, value).apply()
  }

  override fun putFloat(key: String, value: Float) {
    editor.putFloat(key, value).apply()
  }

  override fun putBoolean(key: String, value: Boolean) {
    editor.putBoolean(key, value).apply()
  }

  override fun putString(key: String, value: String) {
    editor.putString(key, value).apply()
  }

  override fun apply() {
    // Writes are already applied eagerly.
  }
}

class PlotSettingsStore(private val store: KeyValueStore) {
  fun load(): Map<PlotType, PlotSettings> {
    val defaults = defaultPlotSettings()
    return PlotType.entries.associateWith { type ->
      val base = defaults.getValue(type)
      PlotSettings(
        windowSeconds = store.getInt(key(type, "window"), base.windowSeconds),
        yMin = store.getFloat(key(type, "y_min"), base.yMin),
        yMax = store.getFloat(key(type, "y_max"), base.yMax),
        isUserLocked = store.getBoolean(key(type, "locked"), base.isUserLocked)
      )
    }
  }

  fun save(type: PlotType, settings: PlotSettings) {
    store.putInt(key(type, "window"), settings.windowSeconds)
    store.putFloat(key(type, "y_min"), settings.yMin)
    store.putFloat(key(type, "y_max"), settings.yMax)
    store.putBoolean(key(type, "locked"), settings.isUserLocked)
    store.apply()
  }

  private fun key(type: PlotType, suffix: String): String {
    return "plot_${type.name.lowercase()}_$suffix"
  }
}

fun createPlotSettingsStore(context: Context): PlotSettingsStore {
  val prefs = context.getSharedPreferences("samathascope_settings", Context.MODE_PRIVATE)
  return PlotSettingsStore(SharedPreferencesStore(prefs))
}

class SkyTowerSettingsStore(private val store: KeyValueStore) {
  fun load(): SkyTowerSettings {
    return SkyTowerSettings(
      baseWidthScale = store.getFloat(KEY_BASE_WIDTH_SCALE, SkyTowerSettings.DEFAULT_BASE_WIDTH_SCALE),
      carrierSpeedMultiplier = store.getFloat(KEY_CARRIER_SPEED_MULTIPLIER, SkyTowerSettings.DEFAULT_CARRIER_SPEED_MULTIPLIER),
      irregularity = store.getFloat(KEY_IRREGULARITY, SkyTowerSettings.DEFAULT_IRREGULARITY),
    ).clamped()
  }

  fun save(settings: SkyTowerSettings) {
    val clamped = settings.clamped()
    store.putFloat(KEY_BASE_WIDTH_SCALE, clamped.baseWidthScale)
    store.putFloat(KEY_CARRIER_SPEED_MULTIPLIER, clamped.carrierSpeedMultiplier)
    store.putFloat(KEY_IRREGULARITY, clamped.irregularity)
    store.apply()
  }

  private companion object {
    const val KEY_BASE_WIDTH_SCALE = "sky_tower_base_width_scale"
    const val KEY_CARRIER_SPEED_MULTIPLIER = "sky_tower_carrier_speed_multiplier"
    const val KEY_IRREGULARITY = "sky_tower_irregularity"
  }
}

fun createSkyTowerSettingsStore(context: Context): SkyTowerSettingsStore {
  val prefs = context.getSharedPreferences("samathascope_settings", Context.MODE_PRIVATE)
  return SkyTowerSettingsStore(SharedPreferencesStore(prefs))
}

class NoiseColorStore(private val store: KeyValueStore) {
  fun load(): NoiseColor {
    return NoiseColor.fromStorage(store.getString(KEY_NOISE_COLOR, NoiseColor.WHITE.storageValue))
  }

  fun save(noiseColor: NoiseColor) {
    store.putString(KEY_NOISE_COLOR, noiseColor.storageValue)
    store.apply()
  }

  private companion object {
    const val KEY_NOISE_COLOR = "audio_noise_color"
  }
}

fun createNoiseColorStore(context: Context): NoiseColorStore {
  val prefs = context.getSharedPreferences("samathascope_settings", Context.MODE_PRIVATE)
  return NoiseColorStore(SharedPreferencesStore(prefs))
}
