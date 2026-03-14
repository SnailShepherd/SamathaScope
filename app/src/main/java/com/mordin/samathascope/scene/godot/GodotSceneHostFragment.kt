package com.mordin.samathascope.scene.godot

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.mordin.samathascope.R
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import org.godotengine.godot.GodotFragment

class GodotSceneHostFragment : Fragment() {
  override fun onCreateView(
    inflater: android.view.LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    return FragmentContainerView(requireContext()).apply {
      id = R.id.godot_scene_runtime_container
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
      )
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    if (childFragmentManager.findFragmentByTag(GODOT_TAG) == null) {
      childFragmentManager.commitNow {
        replace(R.id.godot_scene_runtime_container, GodotFragment(), GODOT_TAG)
      }
    }
  }

  companion object {
    private const val GODOT_TAG = "samatha-godot-runtime"
  }
}
