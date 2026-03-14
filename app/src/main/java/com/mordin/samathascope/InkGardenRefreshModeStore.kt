package com.mordin.samathascope

import android.content.Context

class InkGardenRefreshModeStore(private val store: KeyValueStore) {
  fun load(): InkGardenRefreshMode {
    return InkGardenRefreshMode.fromStorage(
      store.getString(KEY_REFRESH_MODE, InkGardenRefreshMode.AUTO.storageValue)
    )
  }

  fun save(mode: InkGardenRefreshMode) {
    store.putString(KEY_REFRESH_MODE, mode.storageValue)
    store.apply()
  }

  private companion object {
    const val KEY_REFRESH_MODE = "ink_garden_refresh_mode"
  }
}

fun createInkGardenRefreshModeStore(context: Context): InkGardenRefreshModeStore {
  val prefs = context.getSharedPreferences("samathascope_settings", Context.MODE_PRIVATE)
  return InkGardenRefreshModeStore(SharedPreferencesStore(prefs))
}
