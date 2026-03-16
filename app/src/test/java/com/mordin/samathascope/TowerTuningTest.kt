package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import com.mordin.samathascope.scene.SceneState
import com.mordin.samathascope.scene.tower.SkyTowerSettings
import com.mordin.samathascope.scene.tower.TowerBlockShapeKind
import com.mordin.samathascope.scene.tower.createTowerBlockShape
import com.mordin.samathascope.scene.tower.resolveSkyTowerSettings
import com.mordin.samathascope.scene.tower.TowerPhysics
import com.mordin.samathascope.scene.tower.TowerPoint
import com.mordin.samathascope.scene.tower.TowerSceneController
import org.junit.Test
import kotlin.math.abs

class TowerTuningTest {

  @Test
  fun defaultSettings_produceNarrowerFoundationThanLegacyWidth() {
    val resolved = resolveSkyTowerSettings(SkyTowerSettings())

    assertThat(resolved.foundationWidth).isLessThan(3.4f)
  }

  @Test
  fun higherCarrierSpeedMultiplier_resolvesToFasterCarrierMovement() {
    val slow = resolveSkyTowerSettings(SkyTowerSettings(carrierSpeedMultiplier = 0.90f))
    val fast = resolveSkyTowerSettings(SkyTowerSettings(carrierSpeedMultiplier = 1.85f))

    assertThat(fast.carrierSpeedMultiplier).isGreaterThan(slow.carrierSpeedMultiplier)
  }

  @Test
  fun zeroIrregularity_usesCanonicalRectangleShape() {
    val shape = createTowerBlockShape(
      blockIndex = 3,
      settings = SkyTowerSettings(irregularity = 0f),
    )

    assertThat(shape.kind).isEqualTo(TowerBlockShapeKind.RECTANGLE)
    assertThat(shape.localVertices).hasSize(4)
    assertThat(shape.width).isEqualTo(1.55f)
  }

  @Test
  fun nonZeroIrregularity_staysWithinApprovedBoundedShapes() {
    val shapes = (1..5).map { index ->
      createTowerBlockShape(
        blockIndex = index,
        settings = SkyTowerSettings(irregularity = 1.0f),
      )
    }

    assertThat(shapes.map { it.kind }.toSet()).containsAtLeast(
      TowerBlockShapeKind.RIVER_STONE_B,
      TowerBlockShapeKind.RIVER_STONE_C,
      TowerBlockShapeKind.RIVER_STONE_D,
    )
    shapes.forEach { shape ->
      assertThat(shape.kind).isIn(
        listOf(
          TowerBlockShapeKind.RECTANGLE,
          TowerBlockShapeKind.RIVER_STONE_A,
          TowerBlockShapeKind.RIVER_STONE_B,
          TowerBlockShapeKind.RIVER_STONE_C,
          TowerBlockShapeKind.RIVER_STONE_D,
          TowerBlockShapeKind.RIVER_STONE_E,
          TowerBlockShapeKind.RIVER_STONE_F,
        )
      )
      assertThat(shape.width).isAtLeast(0.80f)
      assertThat(shape.width).isAtMost(2.60f)
      assertThat(shape.localVertices.size).isAtLeast(12)
      assertThat(shape.lobes.size).isEqualTo(1)
      assertThat(shape.topSupportWidth).isAtMost(shape.width)
      assertThat(shape.bottomSupportWidth).isAtMost(shape.width)
    }
  }

  @Test
  fun firstTwentyGeneratedStones_haveConvexFixturesFiniteMassAndValidSupportSpans() {
    val physics = TowerPhysics()
    val settings = SkyTowerSettings(irregularity = 1f)

    (1..20).forEach { blockIndex ->
      val shape = createTowerBlockShape(blockIndex = blockIndex, settings = settings)
      assertThat(shape.width).isGreaterThan(0f)
      assertThat(shape.height).isGreaterThan(0f)
      assertThat(shape.lobes).isNotEmpty()
      assertThat(shape.lobes.size).isEqualTo(1)
      assertThat(abs(polygonArea(shape.localVertices))).isGreaterThan(0.01f)
      shape.localVertices.forEach { point ->
        assertThat(point.x.isFinite()).isTrue()
        assertThat(point.y.isFinite()).isTrue()
      }
      shape.lobes.forEach { lobe ->
        assertThat(lobe.fixtureVertices.size).isAtLeast(8)
        assertThat(polygonArea(lobe.fixtureVertices)).isGreaterThan(0f)
      }
      assertThat(shape.topSupportWidth).isGreaterThan(0f)
      assertThat(shape.bottomSupportWidth).isGreaterThan(0f)
      assertThat(abs(shape.topSupportOffsetX)).isAtMost(shape.width * 0.5f)
      assertThat(abs(shape.bottomSupportOffsetX)).isAtMost(shape.width * 0.5f)

      physics.spawnReleasedBlock(
        x = 5f,
        y = 2.2f,
        shape = shape,
        linearVelocityX = 0f,
        angularVelocity = 0f,
      )
      repeat(10) {
        physics.step(SceneState(calmness = 0.8f, focus = 0.7f, stability = 0.85f), FRAME_DT)
      }
      assertThat(physics.previewNextBlockShape().width).isGreaterThan(0f)
    }
  }

  @Test
  fun higherArtefactCreatesMoreTowerWobbleAndSlowerRecovery() {
    val lowArtefact = measureIdleTowerMotion(artefact = 0.05f)
    val highArtefact = measureIdleTowerMotion(artefact = 0.95f)

    assertThat(lowArtefact.averageCameraOffset).isEqualTo(0f)
    assertThat(highArtefact.averageCameraOffset).isEqualTo(0f)

    val lowRecoveryFrames = settleFramesFor(artefact = 0.05f)
    val highRecoveryFrames = settleFramesFor(artefact = 0.95f)
    assertThat(lowRecoveryFrames).isAtLeast(1)
    assertThat(lowRecoveryFrames).isAtMost(360)
    assertThat(highRecoveryFrames).isAtLeast(1)
    assertThat(highRecoveryFrames).isAtMost(360)
  }

  @Test
  fun fasterFallAndFrictionGripStayInsideTargetRange() {
    val fallFrames = framesUntilBodyPassesY(targetY = 7.6f)
    assertThat(fallFrames).isAtMost(60)

    val slip = measureSupportedSlip()
    assertThat(slip.afterContactDrift).isAtMost(1.6f)
    assertThat(slip.afterContactDrift).isAtLeast(0.001f)
  }

  private fun measureIdleTowerMotion(artefact: Float): TowerMotionMetrics {
    val controller = TowerSceneController()
    controller.reset(SkyTowerSettings(irregularity = 1f))
    val buildState = steadyState(artefact = 0.08f)
    var snapshot = controller.step(buildState, FRAME_DT, running = true)

    repeat(4) {
      snapshot = waitForCenteredCarrier(controller, buildState)
      controller.queueRelease()
      snapshot = waitUntilTowerResolves(controller, buildState)
    }

    var cameraTotal = 0f
    var frameShiftTotal = 0f
    val previousXById = HashMap<Int, Float>()
    repeat(180) {
      snapshot = controller.step(steadyState(artefact = artefact), FRAME_DT, running = true)
      val movingBodies = snapshot.bodies.filter { it.id != 0 }
      cameraTotal += abs(snapshot.cameraOffsetX) + abs(snapshot.cameraOffsetY)
      movingBodies.forEach { body ->
        val previousX = previousXById.put(body.id, body.x)
        if (previousX != null) {
          frameShiftTotal += abs(body.x - previousX)
        }
      }
    }
    return TowerMotionMetrics(
      averageCameraOffset = cameraTotal / 180f,
      averageFrameShift = frameShiftTotal / 180f,
    )
  }

  private fun settleFramesFor(artefact: Float): Int {
    val physics = TowerPhysics()
    physics.spawnReleasedBlock(
      x = 5f,
      y = 2.2f,
      shape = physics.previewNextBlockShape(),
      linearVelocityX = 0.08f,
      angularVelocity = 0.02f,
    )
    repeat(360) { frame ->
      val result = physics.step(steadyState(artefact), FRAME_DT)
      if (result.activeSettled || result.activeFailed) {
        return frame + 1
      }
    }
    throw AssertionError("block never resolved for artefact=$artefact")
  }

  private fun framesUntilBodyPassesY(targetY: Float): Int {
    val physics = TowerPhysics()
    physics.spawnReleasedBlock(
      x = 5f,
      y = 2.2f,
      shape = physics.previewNextBlockShape(),
      linearVelocityX = 0f,
      angularVelocity = 0f,
    )
    repeat(120) { frame ->
      val result = physics.step(steadyState(artefact = 0.05f), FRAME_DT)
      val active = result.bodies.firstOrNull { it.active } ?: return frame + 1
      if (active.y >= targetY) {
        return frame + 1
      }
    }
    throw AssertionError("active body never passed target y=$targetY")
  }

  private fun measureSupportedSlip(): SlipMetrics {
    val physics = TowerPhysics()
    val stableState = steadyState(artefact = 0.10f)

    physics.spawnReleasedBlock(
      x = 5f,
      y = 2.2f,
      shape = physics.previewNextBlockShape(),
      linearVelocityX = 0f,
      angularVelocity = 0f,
    )
    var baseSettled = false
    for (frame in 0 until 360) {
      val result = physics.step(stableState, FRAME_DT)
      if (result.activeSettled || result.activeFailed) {
        baseSettled = result.activeSettled
        break
      }
    }
    assertThat(baseSettled).isTrue()

    physics.spawnReleasedBlock(
      x = 5.18f,
      y = 2.2f,
      shape = physics.previewNextBlockShape(),
      linearVelocityX = 0.42f,
      angularVelocity = 0.10f,
    )

    var firstContactX: Float? = null
    var lastTrackedX = 0f
    var previousY: Float? = null
    repeat(360) {
      val result = physics.step(stableState, FRAME_DT)
      val active = result.bodies.firstOrNull { it.active } ?: return SlipMetrics(
        afterContactDrift = abs((firstContactX ?: lastTrackedX) - lastTrackedX),
      )
      val previousCenterY = previousY
      if (previousCenterY != null && active.y >= 10f && abs(active.y - previousCenterY) <= 0.012f) {
        if (firstContactX == null) {
          firstContactX = active.x
        }
        lastTrackedX = active.x
      }
      previousY = active.y
    }
    throw AssertionError("supported slip measurement never completed")
  }

  private fun waitForCenteredCarrier(
    controller: TowerSceneController,
    sceneState: SceneState,
  ): com.mordin.samathascope.scene.tower.TowerRenderSnapshot {
    var snapshot = controller.step(sceneState, FRAME_DT, running = true)
    repeat(720) {
      if (snapshot.carrier.visible && abs(snapshot.carrier.x - 5f) <= 0.18f) {
        return snapshot
      }
      snapshot = controller.step(sceneState, FRAME_DT, running = true)
    }
    throw AssertionError("carrier never entered the centered drop window")
  }

  private fun waitUntilTowerResolves(
    controller: TowerSceneController,
    sceneState: SceneState,
  ): com.mordin.samathascope.scene.tower.TowerRenderSnapshot {
    var snapshot = controller.step(sceneState, FRAME_DT, running = true)
    var sawActive = snapshot.bodies.any { it.active }
    repeat(720) {
      snapshot = controller.step(sceneState, FRAME_DT, running = true)
      sawActive = sawActive || snapshot.bodies.any { it.active }
      if (sawActive && snapshot.bodies.none { it.active }) {
        return snapshot
      }
    }
    throw AssertionError("tower never resolved after release")
  }

  private fun steadyState(artefact: Float): SceneState {
    return SceneState(
      calmness = 0.86f,
      focus = 0.70f,
      stability = 0.88f,
      drift = 0.04f,
      artefact = artefact,
    )
  }

  private fun polygonArea(vertices: List<TowerPoint>): Float {
    var area = 0f
    vertices.forEachIndexed { index, current ->
      val next = vertices[(index + 1) % vertices.size]
      area += (current.x * next.y) - (next.x * current.y)
    }
    return area * 0.5f
  }

  private data class TowerMotionMetrics(
    val averageCameraOffset: Float,
    val averageFrameShift: Float,
  )

  private data class SlipMetrics(
    val afterContactDrift: Float,
  )

  companion object {
    private const val FRAME_DT = 1f / 60f
  }
}
