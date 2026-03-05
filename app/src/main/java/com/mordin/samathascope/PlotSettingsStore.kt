package com.mordin.samathascope

import android.content.Context
import android.content.SharedPreferences

interface KeyValueStore {
  fun getInt(key: String, defaultValue: Int): Int
  fun getFloat(key: String, defaultValue: Float): Float
  fun getBoolean(key: String, defaultValue: Boolean): Boolean
  fun putInt(key: String, value: Int)
  fun putFloat(key: String, value: Float)
  fun putBoolean(key: String, value: Boolean)
  fun apply()
}

class SharedPreferencesStore(private val sharedPreferences: SharedPreferences) : KeyValueStore {
  private val editor: SharedPreferences.Editor
    get() = sharedPreferences.edit()

  override fun getInt(key: String, defaultValue: Int): Int = sharedPreferences.getInt(key, defaultValue)

  override fun getFloat(key: String, defaultValue: Float): Float = sharedPreferences.getFloat(key, defaultValue)

  override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
    sharedPreferences.getBoolean(key, defaultValue)

  override fun putInt(key: String, value: Int) {
    editor.putInt(key, value).apply()
  }

  override fun putFloat(key: String, value: Float) {
    editor.putFloat(key, value).apply()
  }

  override fun putBoolean(key: String, value: Boolean) {
    editor.putBoolean(key, value).apply()
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
