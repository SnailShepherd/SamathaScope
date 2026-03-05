package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlotSettingsStoreTest {

  @Test
  fun saveAndLoad_roundTripsPerPlotValues() {
    val memory = InMemoryStore()
    val store = PlotSettingsStore(memory)

    val custom = PlotSettings(windowSeconds = 10, yMin = -1500f, yMax = 1500f, isUserLocked = true)
    store.save(PlotType.RAW, custom)

    val loaded = store.load()

    assertThat(loaded.getValue(PlotType.RAW)).isEqualTo(custom)
  }

  @Test
  fun load_usesDefaultsWhenMissing() {
    val memory = InMemoryStore()
    val store = PlotSettingsStore(memory)

    val loaded = store.load()

    assertThat(loaded.getValue(PlotType.SAMATHA_SCORE).windowSeconds).isEqualTo(300)
    assertThat(loaded.getValue(PlotType.RAW).windowSeconds).isEqualTo(5)
  }

  private class InMemoryStore : KeyValueStore {
    private val values = mutableMapOf<String, Any>()

    override fun getInt(key: String, defaultValue: Int): Int = values[key] as? Int ?: defaultValue

    override fun getFloat(key: String, defaultValue: Float): Float = values[key] as? Float ?: defaultValue

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = values[key] as? Boolean ?: defaultValue

    override fun putInt(key: String, value: Int) {
      values[key] = value
    }

    override fun putFloat(key: String, value: Float) {
      values[key] = value
    }

    override fun putBoolean(key: String, value: Boolean) {
      values[key] = value
    }

    override fun apply() = Unit
  }
}
