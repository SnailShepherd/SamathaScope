package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import com.mordin.samathascope.scene.SceneState
import com.mordin.samathascope.scene.scriptorium.InkBrushStyle
import com.mordin.samathascope.scene.scriptorium.InkSceneController
import org.junit.Test

class InkSceneControllerTest {

  @Test
  fun firstRecipe_unlocksImmediately() {
    val controller = InkSceneController()

    assertThat(controller.recipeAt(0).unlockProgress).isEqualTo(0f)
  }

  @Test
  fun firstRecipe_isVisibleTextStroke() {
    val controller = InkSceneController()

    assertThat(controller.recipeAt(0).style).isEqualTo(InkBrushStyle.TEXT)
  }

  @Test
  fun buildStrokeInputSpecs_useConsistentStrokeUnitLengthPerBatch() {
    val controller = InkSceneController()

    val inputSpecs = controller.buildStrokeInputSpecs(
      recipe = controller.recipeAt(0),
      revealFraction = 1f,
      sceneState = SceneState(calmness = 0.7f, focus = 0.8f, stability = 0.9f, intensity = 0.4f),
    )

    assertThat(inputSpecs.size).isGreaterThan(1)
    val expectedStrokeUnitLength = inputSpecs.first().strokeUnitLengthCm
    inputSpecs.forEach { input ->
      assertThat(input.strokeUnitLengthCm).isWithin(0.0001f).of(expectedStrokeUnitLength)
    }
  }
}
