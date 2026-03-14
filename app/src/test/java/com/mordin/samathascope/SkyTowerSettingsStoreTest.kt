package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import com.mordin.samathascope.scene.tower.SkyTowerSettings
import org.junit.Test

class SkyTowerSettingsStoreTest {

  @Test
  fun saveAndLoad_roundTripsSkyTowerSettings() {
    val memory = InMemoryStore()
    val store = SkyTowerSettingsStore(memory)
    val custom = SkyTowerSettings(
      baseWidthScale = 0.76f,
      carrierSpeedMultiplier = 1.34f,
      irregularity = 0.48f,
    )

    store.save(custom)

    assertThat(store.load()).isEqualTo(custom)
  }

  @Test
  fun load_usesSkyTowerDefaultsWhenMissing() {
    val store = SkyTowerSettingsStore(InMemoryStore())

    val loaded = store.load()

    assertThat(loaded.baseWidthScale).isEqualTo(SkyTowerSettings.DEFAULT_BASE_WIDTH_SCALE)
    assertThat(loaded.carrierSpeedMultiplier).isEqualTo(SkyTowerSettings.DEFAULT_CARRIER_SPEED_MULTIPLIER)
    assertThat(loaded.irregularity).isEqualTo(SkyTowerSettings.DEFAULT_IRREGULARITY)
  }

  private class InMemoryStore : KeyValueStore {
    private val values = mutableMapOf<String, Any>()

    override fun getInt(key: String, defaultValue: Int): Int = values[key] as? Int ?: defaultValue

    override fun getFloat(key: String, defaultValue: Float): Float = values[key] as? Float ?: defaultValue

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = values[key] as? Boolean ?: defaultValue

    override fun getString(key: String, defaultValue: String): String = values[key] as? String ?: defaultValue

    override fun putInt(key: String, value: Int) {
      values[key] = value
    }

    override fun putFloat(key: String, value: Float) {
      values[key] = value
    }

    override fun putBoolean(key: String, value: Boolean) {
      values[key] = value
    }

    override fun putString(key: String, value: String) {
      values[key] = value
    }

    override fun apply() = Unit
  }
}
