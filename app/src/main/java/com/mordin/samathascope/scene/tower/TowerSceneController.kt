package com.mordin.samathascope.scene.tower

import com.mordin.samathascope.clamp01
import com.mordin.samathascope.scene.SceneState
import com.mordin.samathascope.scene.SceneSummary
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

data class TowerCarrierState(
  val x: Float = 5f,
  val y: Float = 2.05f,
  val width: Float = createTowerBlockShape(1, SkyTowerSettings()).width,
  val height: Float = createTowerBlockShape(1, SkyTowerSettings()).height,
  val shapeKind: TowerBlockShapeKind = createTowerBlockShape(1, SkyTowerSettings()).kind,
  val shapeVertices: List<TowerPoint> = createTowerBlockShape(1, SkyTowerSettings()).localVertices,
  val shapeLobes: List<List<TowerPoint>> = createTowerBlockShape(1, SkyTowerSettings()).lobes.map { it.fixtureVertices },
  val visible: Boolean = true,
)

data class TowerDustBurst(
  val x: Float,
  val y: Float,
  val radius: Float,
  val alpha: Float,
)

data class TowerRenderSnapshot(
  val bodies: List<TowerBodySnapshot>,
  val carrier: TowerCarrierState,
  val dustBursts: List<TowerDustBurst>,
  val impactFlash: Float,
  val cameraOffsetX: Float,
  val cameraOffsetY: Float,
  val towerHeight: Int,
)

class TowerSceneController {
  private val physics = TowerPhysics()

  private var settings = SkyTowerSettings()
  private var resolvedSettings = resolveSkyTowerSettings(settings)
  private var accumulatorSeconds = 0f
  private var elapsedSeconds = 0f
  private var carrierX = 5f
  private var carrierDirection = 1f
  private var spawnCooldownSeconds = 0f
  private var queuedRelease = false
  private var placements = 0
  private var misses = 0
  private var impactFlash = 0f
  private var dustBursts = emptyList<TowerDustBurst>()
  private var currentTopY = TowerPhysics.FOUNDATION_Y
  private var lastSnapshot = TowerRenderSnapshot(
    bodies = emptyList(),
    carrier = TowerCarrierState(),
    dustBursts = emptyList(),
    impactFlash = 0f,
    cameraOffsetX = 0f,
    cameraOffsetY = 0f,
    towerHeight = 0,
  )

  fun reset(settings: SkyTowerSettings = this.settings) {
    this.settings = settings.clamped()
    resolvedSettings = resolveSkyTowerSettings(this.settings)
    physics.reset(this.settings)
    accumulatorSeconds = 0f
    elapsedSeconds = 0f
    carrierX = 5f
    carrierDirection = 1f
    spawnCooldownSeconds = 0f
    queuedRelease = false
    placements = 0
    misses = 0
    impactFlash = 0f
    dustBursts = emptyList()
    currentTopY = TowerPhysics.FOUNDATION_Y
    val initialResult = physics.step(SceneState(), 0f)
    lastSnapshot = TowerRenderSnapshot(
      bodies = initialResult.bodies,
      carrier = carrierState(),
      dustBursts = emptyList(),
      impactFlash = 0f,
      cameraOffsetX = 0f,
      cameraOffsetY = 0f,
      towerHeight = 0,
    )
  }

  fun queueRelease() {
    queuedRelease = true
  }

  fun step(
    sceneState: SceneState,
    dtSeconds: Float,
    running: Boolean,
  ): TowerRenderSnapshot {
    if (!running) {
      lastSnapshot = lastSnapshot.copy(
        carrier = carrierState(visible = true),
        dustBursts = emptyList(),
        impactFlash = 0f,
        cameraOffsetX = 0f,
        cameraOffsetY = 0f,
      )
      return lastSnapshot
    }

    elapsedSeconds += dtSeconds
    accumulatorSeconds += dtSeconds
    spawnCooldownSeconds = (spawnCooldownSeconds - dtSeconds).coerceAtLeast(0f)

    // Dynamic carrier Y: hover above the current tower top
    val dynamicCarrierY = min(currentTopY - CARRIER_GAP_ABOVE_TOWER, CARRIER_Y_DEFAULT)
    val dynamicReleaseY = dynamicCarrierY + 0.19f

    val carrierWave = sin(
      (elapsedSeconds * (0.82f + (abs(sceneState.drift) * 0.70f) + (sceneState.artefact * 0.30f))).toDouble()
    ).toFloat()
    val swayAmplitude = 0.42f + (abs(sceneState.drift) * 0.34f) + (sceneState.artefact * 0.14f) - (sceneState.calmness * 0.08f)
    if (!physics.hasActiveBody() && spawnCooldownSeconds <= 0f) {
      val baseSpeed = (
        1.28f +
          (abs(sceneState.drift) * 0.42f) +
          (sceneState.artefact * 0.06f) -
          (sceneState.stability * 0.12f)
        ) * resolvedSettings.carrierSpeedMultiplier
      carrierX += (carrierDirection * baseSpeed + (carrierWave * swayAmplitude)) * dtSeconds
      if (carrierX <= CARRIER_MIN_X) {
        carrierX = CARRIER_MIN_X
        carrierDirection = 1f
      } else if (carrierX >= CARRIER_MAX_X) {
        carrierX = CARRIER_MAX_X
        carrierDirection = -1f
      }
    }

    if (queuedRelease && !physics.hasActiveBody() && spawnCooldownSeconds <= 0f) {
      queuedRelease = false
      val previewShape = physics.previewNextBlockShape()
      val releaseJitter = sin((elapsedSeconds * 5.4f).toDouble()).toFloat()
      physics.spawnReleasedBlock(
        x = carrierX,
        y = dynamicReleaseY,
        shape = previewShape,
        linearVelocityX = carrierDirection * (
          0.02f +
            ((1f - sceneState.calmness) * 0.03f) +
            (sceneState.artefact * 0.02f)
          ),
        angularVelocity = (
          sceneState.drift * (0.04f + ((1f - sceneState.focus) * 0.02f))
          ) + (releaseJitter * sceneState.artefact * 0.05f),
      )
    }

    while (accumulatorSeconds >= FIXED_TIMESTEP_SECONDS) {
      val result = physics.step(sceneState, FIXED_TIMESTEP_SECONDS)
      accumulatorSeconds -= FIXED_TIMESTEP_SECONDS
      currentTopY = result.topY
      handlePhysicsResult(result)
      lastSnapshot = buildSnapshot(result, dynamicCarrierY)
    }

    dustBursts = dustBursts.map {
      it.copy(
        radius = it.radius + (dtSeconds * 0.52f),
        alpha = (it.alpha - (dtSeconds * 1.35f)).coerceAtLeast(0f),
      )
    }.filter { it.alpha > 0.03f }

    impactFlash = (impactFlash - dtSeconds * 4.8f).coerceAtLeast(0f)

    return lastSnapshot.copy(
      dustBursts = dustBursts,
      impactFlash = impactFlash,
      cameraOffsetX = 0f,
      cameraOffsetY = 0f,
    )
  }

  fun summary(): SceneSummary {
    return SceneSummary(
      label = "Tower height",
      value = "$placements stones",
    )
  }

  private fun handlePhysicsResult(result: TowerPhysicsStepResult) {
    result.impactEvents.forEach { event ->
      dustBursts = (
        dustBursts + TowerDustBurst(
          x = event.x,
          y = event.y,
          radius = 0.14f + (event.intensity * 0.26f),
          alpha = 0.20f + (event.intensity * 0.34f),
        )
        ).takeLast(18)
      impactFlash = clamp01(maxOf(impactFlash, 0.28f + (event.intensity * 0.44f)))
    }
    if (result.activeSettled) {
      placements += 1
      spawnCooldownSeconds = 0.22f
    }
    if (result.activeFailed) {
      misses += 1
      spawnCooldownSeconds = 0.20f
    }
  }

  private fun buildSnapshot(
    result: TowerPhysicsStepResult,
    carrierY: Float = CARRIER_Y_DEFAULT,
  ): TowerRenderSnapshot {
    val towerHeight = result.bodies.count { it.id != 0 }
    return TowerRenderSnapshot(
      bodies = result.bodies,
      carrier = carrierState(visible = !physics.hasActiveBody(), carrierY = carrierY),
      dustBursts = dustBursts,
      impactFlash = impactFlash,
      cameraOffsetX = 0f,
      cameraOffsetY = 0f,
      towerHeight = towerHeight,
    )
  }

  private fun carrierState(
    visible: Boolean = true,
    carrierY: Float = CARRIER_Y_DEFAULT,
  ): TowerCarrierState {
    val previewShape = physics.previewNextBlockShape()
    return TowerCarrierState(
      x = carrierX,
      y = carrierY,
      width = previewShape.width,
      height = previewShape.height,
      shapeKind = previewShape.kind,
      shapeVertices = previewShape.localVertices,
      shapeLobes = previewShape.lobes.map { it.fixtureVertices },
      visible = visible,
    )
  }

  companion object {
    private const val CARRIER_Y_DEFAULT = 2.05f
    private const val CARRIER_GAP_ABOVE_TOWER = 2.0f
    private const val CARRIER_MIN_X = 2.35f
    private const val CARRIER_MAX_X = 7.65f
    const val FIXED_TIMESTEP_SECONDS = 1f / 60f
  }
}
