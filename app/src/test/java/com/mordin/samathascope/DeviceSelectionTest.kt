package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeviceSelectionTest {

  private class MemoryStore : KeyValueStore {
    val values = mutableMapOf<String, Any>()
    override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
    override fun getFloat(key: String, defaultValue: Float) = values[key] as? Float ?: defaultValue
    override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
    override fun getString(key: String, defaultValue: String) = values[key] as? String ?: defaultValue
    override fun putInt(key: String, value: Int) { values[key] = value }
    override fun putFloat(key: String, value: Float) { values[key] = value }
    override fun putBoolean(key: String, value: Boolean) { values[key] = value }
    override fun putString(key: String, value: String) { values[key] = value }
    override fun apply() = Unit
  }

  @Test
  fun store_roundTripsLastSelection() {
    val store = DeviceSelectionStore(MemoryStore())
    assertThat(store.load()).isNull()
    store.save("AA:BB:CC:DD:EE:02")
    assertThat(store.load()).isEqualTo("AA:BB:CC:DD:EE:02")
  }

  @Test
  fun rememberedDevice_winsOverFirstBondedOnStartup() {
    val bonded = listOf("AA:01", "AA:02", "AA:03")
    assertThat(resolveSelectedDeviceMac(current = null, remembered = "AA:02", bonded = bonded))
      .isEqualTo("AA:02")
  }

  @Test
  fun currentSelection_isKeptAcrossRefreshes() {
    val bonded = listOf("AA:01", "AA:02", "AA:03")
    assertThat(resolveSelectedDeviceMac(current = "AA:03", remembered = "AA:02", bonded = bonded))
      .isEqualTo("AA:03")
  }

  @Test
  fun unpairedRememberedDevice_fallsBackToFirstBonded() {
    val bonded = listOf("AA:01", "AA:02")
    assertThat(resolveSelectedDeviceMac(current = null, remembered = "ZZ:99", bonded = bonded))
      .isEqualTo("AA:01")
  }

  @Test
  fun emptyBondedList_keepsRememberedChoice() {
    assertThat(resolveSelectedDeviceMac(current = null, remembered = "AA:02", bonded = emptyList()))
      .isEqualTo("AA:02")
  }
}
