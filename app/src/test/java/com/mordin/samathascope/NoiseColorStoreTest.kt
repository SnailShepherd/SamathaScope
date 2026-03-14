package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NoiseColorStoreTest {

  @Test
  fun saveAndLoad_roundTripsNoiseColor() {
    val memory = InMemoryStore()
    val store = NoiseColorStore(memory)

    store.save(NoiseColor.BLUE)

    assertThat(store.load()).isEqualTo(NoiseColor.BLUE)
  }

  @Test
  fun load_defaultsToWhiteWhenMissing() {
    val store = NoiseColorStore(InMemoryStore())

    assertThat(store.load()).isEqualTo(NoiseColor.WHITE)
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
