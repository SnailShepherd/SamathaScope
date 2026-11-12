package com.mordin.samathascope

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
      SamathaScopeTheme {
        val vm: MainViewModel = viewModel()
        App(vm)
      }
    }
  }
}
