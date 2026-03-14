package com.mordin.samathascope.scene.scriptorium

import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.InProgressStroke
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import com.mordin.samathascope.clamp01
import com.mordin.samathascope.scene.SceneState
import com.mordin.samathascope.scene.SceneSummary
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

data class InkRenderState(
  val finishedStrokes: List<Stroke> = emptyList(),
  val activeStroke: InProgressStroke? = null,
  val paperWarmth: Float = 0.5f,
  val paperContrast: Float = 0.5f,
  val ornamentGlow: Float = 0f,
  val progress: Float = 0f,
)

data class InkStrokeInputSpec(
  val x: Float,
  val y: Float,
  val elapsedTimeMillis: Long,
  val pressure: Float,
  val strokeUnitLengthCm: Float,
)

class InkSceneController {
  private val pageComposer = PageComposer()
  private val recipes = pageComposer.composePage()
  private val finishedStrokes = mutableListOf<Stroke>()
  private var activeIndex = 0
  private var activeReveal = 0f
  private var currentInProgress: InProgressStroke? = null
  private var currentStrokeTimeMs = 0L
  private var renderState = InkRenderState()
  private var idlePreviewPhaseSeconds = 0f

  fun reset() {
    finishedStrokes.clear()
    activeIndex = 0
    activeReveal = 0f
    currentInProgress = null
    currentStrokeTimeMs = 0L
    renderState = InkRenderState()
    idlePreviewPhaseSeconds = 0f
  }

  fun step(sceneState: SceneState, dtSeconds: Float, running: Boolean): InkRenderState {
    if (!running) {
      idlePreviewPhaseSeconds += dtSeconds.coerceAtLeast(0f)
      val previewState = previewSceneState(sceneState)
      val previewStroke = if (finishedStrokes.isEmpty() && recipes.isNotEmpty()) {
        buildInProgressStroke(
          recipe = recipes.first(),
          revealFraction = idlePreviewRevealFraction(),
          sceneState = previewState,
        )
      } else {
        null
      }
      renderState = InkRenderState(
        finishedStrokes = finishedStrokes.toList(),
        activeStroke = previewStroke,
        paperWarmth = paperWarmth(previewState),
        paperContrast = paperContrast(previewState),
        ornamentGlow = max(previewState.intensity * 0.22f, 0.10f),
        progress = max(sceneState.progress, if (previewStroke != null) 0.18f else 0f),
      )
      return renderState
    }

    while (activeIndex < recipes.size && recipes[activeIndex].unlockProgress <= sceneState.progress + (sceneState.intensity * 0.05f)) {
      val recipe = recipes[activeIndex]
      activeReveal += dtSeconds * revealSpeed(sceneState, recipe)
      val revealFraction = activeReveal.coerceIn(0f, 1f)
      currentInProgress = buildInProgressStroke(recipe, revealFraction, sceneState)
      if (revealFraction >= 1f) {
        finishedStrokes += currentInProgress!!.toImmutable()
        currentInProgress = null
        activeIndex += 1
        activeReveal = 0f
        currentStrokeTimeMs = 0L
      }
      break
    }

    renderState = InkRenderState(
      finishedStrokes = finishedStrokes.toList(),
      activeStroke = currentInProgress,
      paperWarmth = paperWarmth(sceneState),
      paperContrast = paperContrast(sceneState),
      ornamentGlow = sceneState.intensity * 0.36f,
      progress = sceneState.progress,
    )
    return renderState
  }

  fun summary(sceneState: SceneState): SceneSummary {
    return SceneSummary(
      label = "Page fullness",
      value = "${(sceneState.progress * 100f).roundToInt()}%",
    )
  }

  internal fun recipeAt(index: Int): StrokeRecipe = recipes[index]

  internal fun buildStrokeInputSpecs(
    recipe: StrokeRecipe,
    revealFraction: Float,
    sceneState: SceneState,
  ): List<InkStrokeInputSpec> {
    val pointCount = (recipe.points.size * revealFraction).roundToInt().coerceAtLeast(2).coerceAtMost(recipe.points.size)
    val strokeUnitLengthCm = strokeUnitLengthCmFor(recipe)
    currentStrokeTimeMs = 0L
    return List(pointCount) { index ->
      val point = recipe.points[index]
      currentStrokeTimeMs += 16L
      InkStrokeInputSpec(
        x = point.x,
        y = point.y,
        elapsedTimeMillis = currentStrokeTimeMs,
        pressure = adjustPressure(point.pressure, recipe, sceneState),
        strokeUnitLengthCm = strokeUnitLengthCm,
      )
    }
  }

  internal fun buildStrokeInputBatch(
    recipe: StrokeRecipe,
    revealFraction: Float,
    sceneState: SceneState,
  ): MutableStrokeInputBatch {
    val inputSpecs = buildStrokeInputSpecs(recipe, revealFraction, sceneState)
    return MutableStrokeInputBatch().apply {
      inputSpecs.forEach { spec ->
        val input = StrokeInput.create(
          spec.x,
          spec.y,
          spec.elapsedTimeMillis,
          InputToolType.STYLUS,
          spec.strokeUnitLengthCm,
          spec.pressure,
        )
        check(kotlin.math.abs(input.strokeUnitLengthCm - spec.strokeUnitLengthCm) < 0.0001f) {
          "Scriptorium stroke batch mixed strokeUnitLength values."
        }
        add(input)
      }
    }
  }

  private fun buildInProgressStroke(
    recipe: StrokeRecipe,
    revealFraction: Float,
    sceneState: SceneState,
  ): InProgressStroke {
    val brush = brushFor(recipe, sceneState)
    val inProgress = InProgressStroke()
    inProgress.start(brush)
    val inputBatch = buildStrokeInputBatch(recipe, revealFraction, sceneState)
    inProgress.enqueueInputs(inputBatch, MutableStrokeInputBatch())
    inProgress.updateShape(currentStrokeTimeMs)
    if (revealFraction >= 1f) {
      inProgress.finishInput()
      if (inProgress.changesWithTime()) {
        inProgress.updateShape(currentStrokeTimeMs + 16L)
      }
    }
    return inProgress
  }

  private fun revealSpeed(sceneState: SceneState, recipe: StrokeRecipe): Float {
    val base = 0.52f + (sceneState.calmness * 0.18f) + (sceneState.focus * 0.24f) + (sceneState.stability * 0.10f)
    return when (recipe.style) {
      InkBrushStyle.TEXT -> base * 1.10f
      InkBrushStyle.FLOURISH -> base * 0.95f
      InkBrushStyle.GLOW -> base * 0.85f
    }
  }

  private fun brushFor(recipe: StrokeRecipe, sceneState: SceneState): Brush {
    val family = when (recipe.style) {
      InkBrushStyle.TEXT -> StockBrushes.pressurePen()
      InkBrushStyle.FLOURISH -> StockBrushes.marker()
      InkBrushStyle.GLOW -> StockBrushes.highlighter()
    }
    val size = when (recipe.style) {
      InkBrushStyle.TEXT -> recipe.baseSize + (sceneState.calmness * 0.005f)
      InkBrushStyle.FLOURISH -> recipe.baseSize + (sceneState.intensity * 0.006f)
      InkBrushStyle.GLOW -> recipe.baseSize + (sceneState.intensity * 0.010f)
    }
    return Brush.createWithColorIntArgb(
      family,
      recipe.colorArgb,
      size,
      (size * 0.2f).coerceAtLeast(0.001f),
    )
  }

  private fun adjustPressure(
    pressure: Float,
    recipe: StrokeRecipe,
    sceneState: SceneState,
  ): Float {
    val focusLift = sceneState.focus * 0.18f
    val stabilityLift = sceneState.stability * 0.08f
    val intensityLift = if (recipe.style != InkBrushStyle.TEXT) sceneState.intensity * 0.10f else 0f
    return clamp01(pressure + focusLift + stabilityLift + intensityLift)
  }

  private fun paperWarmth(sceneState: SceneState): Float {
    return (0.46f + (sceneState.calmness * 0.30f) + (sceneState.intensity * 0.08f)).coerceIn(0f, 1f)
  }

  private fun paperContrast(sceneState: SceneState): Float {
    return (0.55f - (sceneState.calmness * 0.16f) + (sceneState.focus * 0.10f)).coerceIn(0f, 1f)
  }

  private fun previewSceneState(sceneState: SceneState): SceneState {
    return sceneState.copy(
      calmness = max(sceneState.calmness, 0.64f),
      focus = max(sceneState.focus, 0.62f),
      stability = max(sceneState.stability, 0.68f),
      intensity = max(sceneState.intensity, 0.36f),
      progress = max(sceneState.progress, 0.18f),
    )
  }

  private fun idlePreviewRevealFraction(): Float {
    val pulse = ((sin(idlePreviewPhaseSeconds * 0.95f) + 1f) * 0.5f)
    return (0.62f + (pulse * 0.18f)).coerceIn(0.54f, 0.88f)
  }

  private fun strokeUnitLengthCmFor(recipe: StrokeRecipe): Float {
    return when (recipe.style) {
      InkBrushStyle.TEXT -> 0.46f
      InkBrushStyle.FLOURISH -> 0.50f
      InkBrushStyle.GLOW -> 0.54f
    }
  }
}
