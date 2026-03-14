package com.mordin.samathascope

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mordin.samathascope.scene.godot.SamathaGodotPlugin
import com.mordin.samathascope.ui.theme.SamathaScopeTheme
import org.godotengine.godot.Godot
import org.godotengine.godot.GodotHost
import org.godotengine.godot.plugin.GodotPlugin

/**
 * Main entry point.
 *
 * We keep Activity code minimal: Compose UI + ViewModel.
 */
class MainActivity : FragmentActivity(), GodotHost {
  private var godot: Godot? = null
  private var godotPlugin: SamathaGodotPlugin? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      SamathaScopeTheme {
        val vm: MainViewModel = viewModel()
        App(vm)
      }
    }
  }

  override fun getActivity(): Activity = this

  override fun getGodot(): Godot {
    return godot ?: Godot.getInstance(this).also { godot = it }
  }

  override fun getHostPlugins(godot: Godot): MutableSet<GodotPlugin> {
    this.godot = godot
    val plugin = godotPlugin ?: SamathaGodotPlugin(godot).also { godotPlugin = it }
    return mutableSetOf(plugin)
  }

  override fun onGodotSetupCompleted() {
    super.onGodotSetupCompleted()
    com.mordin.samathascope.scene.godot.GodotBridgeStore.markEngineSetupCompleted()
  }

  override fun onGodotMainLoopStarted() {
    super.onGodotMainLoopStarted()
    com.mordin.samathascope.scene.godot.GodotBridgeStore.markMainLoopStarted()
  }
}
