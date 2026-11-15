package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UserSettingsStoreTest {

  @Test
  fun saveAndLoad_roundTripsEverySetting() {
    val memory = InMemoryStore()
    val settings = UserSettings(
      audioEnabled = false,
      invertReward = true,
      gamma = 2.2f,
      gMinDb = -45,
      gMaxDb = -6,
      notch50Enabled = true,
      recordingEnabled = true,
      feedbackMetric = PlotType.ALERTNESS,
      visibleMetrics = linkedSetOf(PlotType.CONTROL, PlotType.MEDITATION_PROXY),
      themeMode = ThemeMode.DARK,
      developerOptionsEnabled = true,
    )

    UserSettingsStore(memory).save(settings)
    val loaded = UserSettingsStore(memory).load()

    assertThat(loaded).isEqualTo(settings)
    assertThat(loaded.visibleMetrics.toList()).containsExactly(PlotType.CONTROL, PlotType.MEDITATION_PROXY).inOrder()
  }

  @Test
  fun load_withNothingSaved_givesDefaults() {
    assertThat(UserSettingsStore(InMemoryStore()).load()).isEqualTo(UserSettings())
  }

  @Test
  fun load_replacesInvalidValuesWithDefaults() {
    val memory = InMemoryStore().apply {
      putString(UserSettingsStore.KEY_FEEDBACK_METRIC, PlotType.ARTEFACT_SCORE.name) // Not a feedback source.
      putString(UserSettingsStore.KEY_VISIBLE_METRICS, "RAW,NOT_A_METRIC")
      putFloat(UserSettingsStore.KEY_GAMMA, 9f)
      putInt(UserSettingsStore.KEY_G_MIN_DB, -10)
      putInt(UserSettingsStore.KEY_G_MAX_DB, -20) // Min above max.
      putString(UserSettingsStore.KEY_THEME, "sepia")
    }

    val loaded = UserSettingsStore(memory).load()
    val defaults = UserSettings()

    assertThat(loaded.feedbackMetric).isEqualTo(defaults.feedbackMetric)
    assertThat(loaded.visibleMetrics).isEqualTo(defaults.visibleMetrics)
    assertThat(loaded.gamma).isEqualTo(3.0f)
    assertThat(loaded.gMinDb).isEqualTo(defaults.gMinDb)
    assertThat(loaded.gMaxDb).isEqualTo(defaults.gMaxDb)
    assertThat(loaded.themeMode).isEqualTo(ThemeMode.SYSTEM)
  }

  private class InMemoryStore : KeyValueStore {
    private val values = mutableMapOf<String, Any>()

    override fun getInt(key: String, defaultValue: Int): Int = values[key] as? Int ?: defaultValue
    override fun getFloat(key: String, defaultValue: Float): Float = values[key] as? Float ?: defaultValue
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = values[key] as? Boolean ?: defaultValue
    override fun getString(key: String, defaultValue: String): String = values[key] as? String ?: defaultValue
    override fun putInt(key: String, value: Int) { values[key] = value }
    override fun putFloat(key: String, value: Float) { values[key] = value }
    override fun putBoolean(key: String, value: Boolean) { values[key] = value }
    override fun putString(key: String, value: String) { values[key] = value }
    override fun apply() = Unit
  }
}
