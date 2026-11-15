package com.mordin.samathascope

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mordin.samathascope.ui.theme.SamathaScopeTheme

/**
 * Main entry point.
 *
 * We keep Activity code minimal: Compose UI + ViewModel.
 */
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      val vm: MainViewModel = viewModel()
      // Only the theme setting: the root shouldn't recompose on every UI-state update.
      val themeMode by vm.themeMode.collectAsStateWithLifecycle()
      val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
      }
      // Status/navigation bar icons must contrast with the in-app theme, which may differ from the
      // system one.
      DisposableEffect(darkTheme) {
        val style = if (darkTheme) {
          SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
          SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose {}
      }
      SamathaScopeTheme(darkTheme = darkTheme) {
        App(vm)
      }
    }
  }
}
