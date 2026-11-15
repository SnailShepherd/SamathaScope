package com.mordin.samathascope

import android.content.Context
import android.content.SharedPreferences

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

/**
 * Writes go into one pending editor and reach SharedPreferences together on [apply], so saving a
 * whole settings object (several keys, possibly many times a second while a slider moves) is one
 * commit rather than one per key. Every store calls [apply] after its puts.
 */
class SharedPreferencesStore(private val sharedPreferences: SharedPreferences) : KeyValueStore {
  private var pending: SharedPreferences.Editor? = null

  private val editor: SharedPreferences.Editor
    get() = pending ?: sharedPreferences.edit().also { pending = it }

  override fun getInt(key: String, defaultValue: Int): Int = sharedPreferences.getInt(key, defaultValue)

  override fun getFloat(key: String, defaultValue: Float): Float = sharedPreferences.getFloat(key, defaultValue)

  override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
    sharedPreferences.getBoolean(key, defaultValue)

  override fun getString(key: String, defaultValue: String): String =
    sharedPreferences.getString(key, defaultValue) ?: defaultValue

  @Synchronized
  override fun putInt(key: String, value: Int) {
    editor.putInt(key, value)
  }

  @Synchronized
  override fun putFloat(key: String, value: Float) {
    editor.putFloat(key, value)
  }

  @Synchronized
  override fun putBoolean(key: String, value: Boolean) {
    editor.putBoolean(key, value)
  }

  @Synchronized
  override fun putString(key: String, value: String) {
    editor.putString(key, value)
  }

  @Synchronized
  override fun apply() {
    pending?.apply()
    pending = null
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

/** Remembers the last Bluetooth headset the user picked so the device menu survives app restarts. */
class DeviceSelectionStore(private val store: KeyValueStore) {
  fun load(): String? = store.getString(KEY_SELECTED_DEVICE_MAC, "").takeIf { it.isNotBlank() }

  fun save(mac: String) {
    store.putString(KEY_SELECTED_DEVICE_MAC, mac)
    store.apply()
  }

  private companion object {
    const val KEY_SELECTED_DEVICE_MAC = "bt_selected_device_mac"
  }
}

/**
 * Picks which bonded headset the device menu shows: keep the current choice if it is still paired,
 * otherwise the one remembered from the last run, otherwise the first paired device.
 */
internal fun resolveSelectedDeviceMac(
  current: String?,
  remembered: String?,
  bonded: List<String>,
): String? {
  if (current != null && current in bonded) return current
  if (remembered != null && remembered in bonded) return remembered
  // Keep an unpaired remembered choice visible-but-unresolved rather than silently switching devices
  // when the list is empty (e.g. Bluetooth still off at startup).
  if (bonded.isEmpty()) return current ?: remembered
  return bonded.first()
}

fun createDeviceSelectionStore(context: Context): DeviceSelectionStore {
  val prefs = context.getSharedPreferences("samathascope_settings", Context.MODE_PRIVATE)
  return DeviceSelectionStore(SharedPreferencesStore(prefs))
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

/** Remembers whether sessions are run with eyes closed or open. */
class SessionModeStore(private val store: KeyValueStore) {
  fun load(): SessionMode = SessionMode.fromStorage(store.getString(KEY_SESSION_MODE, SessionMode.EYES_CLOSED.storageValue))

  fun save(mode: SessionMode) {
    store.putString(KEY_SESSION_MODE, mode.storageValue)
    store.apply()
  }

  private companion object {
    const val KEY_SESSION_MODE = "session_mode"
  }
}

fun createSessionModeStore(context: Context): SessionModeStore {
  val prefs = context.getSharedPreferences("samathascope_settings", Context.MODE_PRIVATE)
  return SessionModeStore(SharedPreferencesStore(prefs))
}

/** User preferences that aren't covered by a more specific store above. */
data class UserSettings(
  val audioEnabled: Boolean = true,
  val invertReward: Boolean = false,
  val gamma: Float = 1.6f,
  val gMinDb: Int = -30,
  val gMaxDb: Int = -3,
  val notch50Enabled: Boolean = false,
  val recordingEnabled: Boolean = false,
  val feedbackMetric: PlotType = PlotType.MEDITATION_PROXY,
  val visibleMetrics: Set<PlotType> = defaultVisibleMetrics(),
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val developerOptionsEnabled: Boolean = false,
)

/** Persists [UserSettings]. Values that are out of range or unknown on load fall back to defaults. */
class UserSettingsStore(private val store: KeyValueStore) {
  fun load(): UserSettings {
    val defaults = UserSettings()
    val gMin = store.getInt(KEY_G_MIN_DB, defaults.gMinDb).coerceIn(G_MIN_DB_RANGE)
    val gMax = store.getInt(KEY_G_MAX_DB, defaults.gMaxDb).coerceIn(G_MAX_DB_RANGE)
    val feedback = PlotType.entries.firstOrNull { it.name == store.getString(KEY_FEEDBACK_METRIC, "") }
      ?.takeIf { it in MetricGlossary.feedbackSourceMetrics() }
      ?: defaults.feedbackMetric
    val visible = store.getString(KEY_VISIBLE_METRICS, "")
      .split(',')
      .mapNotNull { name -> PlotType.entries.firstOrNull { it.name == name.trim() } }
      .filter { it != PlotType.RAW }
      .toCollection(LinkedHashSet())
      .ifEmpty { defaults.visibleMetrics }
    return UserSettings(
      audioEnabled = store.getBoolean(KEY_AUDIO_ENABLED, defaults.audioEnabled),
      invertReward = store.getBoolean(KEY_INVERT_REWARD, defaults.invertReward),
      gamma = store.getFloat(KEY_GAMMA, defaults.gamma).coerceIn(GAMMA_RANGE),
      gMinDb = if (gMin < gMax) gMin else defaults.gMinDb,
      gMaxDb = if (gMin < gMax) gMax else defaults.gMaxDb,
      notch50Enabled = store.getBoolean(KEY_NOTCH, defaults.notch50Enabled),
      recordingEnabled = store.getBoolean(KEY_RECORDING, defaults.recordingEnabled),
      feedbackMetric = feedback,
      visibleMetrics = visible,
      themeMode = ThemeMode.fromStorage(store.getString(KEY_THEME, defaults.themeMode.storageValue)),
      developerOptionsEnabled = store.getBoolean(KEY_DEVELOPER_OPTIONS, defaults.developerOptionsEnabled),
    )
  }

  fun save(settings: UserSettings) {
    store.putBoolean(KEY_AUDIO_ENABLED, settings.audioEnabled)
    store.putBoolean(KEY_INVERT_REWARD, settings.invertReward)
    store.putFloat(KEY_GAMMA, settings.gamma)
    store.putInt(KEY_G_MIN_DB, settings.gMinDb)
    store.putInt(KEY_G_MAX_DB, settings.gMaxDb)
    store.putBoolean(KEY_NOTCH, settings.notch50Enabled)
    store.putBoolean(KEY_RECORDING, settings.recordingEnabled)
    store.putString(KEY_FEEDBACK_METRIC, settings.feedbackMetric.name)
    store.putString(KEY_VISIBLE_METRICS, settings.visibleMetrics.joinToString(",") { it.name })
    store.putString(KEY_THEME, settings.themeMode.storageValue)
    store.putBoolean(KEY_DEVELOPER_OPTIONS, settings.developerOptionsEnabled)
    store.apply()
  }

  internal companion object {
    // Same ranges as the Settings sliders.
    val GAMMA_RANGE = 0.6f..3.0f
    val G_MIN_DB_RANGE = -60..-10
    val G_MAX_DB_RANGE = -30..0
    const val KEY_AUDIO_ENABLED = "audio_enabled"
    const val KEY_INVERT_REWARD = "audio_invert_reward"
    const val KEY_GAMMA = "audio_gamma"
    const val KEY_G_MIN_DB = "audio_g_min_db"
    const val KEY_G_MAX_DB = "audio_g_max_db"
    const val KEY_NOTCH = "filter_notch_enabled"
    const val KEY_RECORDING = "recording_enabled"
    const val KEY_FEEDBACK_METRIC = "feedback_metric"
    const val KEY_VISIBLE_METRICS = "visible_metrics"
    const val KEY_THEME = "theme_mode"
    const val KEY_DEVELOPER_OPTIONS = "developer_options_enabled"
  }
}

fun createUserSettingsStore(context: Context): UserSettingsStore {
  val prefs = context.getSharedPreferences("samathascope_settings", Context.MODE_PRIVATE)
  return UserSettingsStore(SharedPreferencesStore(prefs))
}
