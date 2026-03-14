package com.mordin.samathascope

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.mordin.samathascope.scene.SceneState
import com.mordin.samathascope.scene.tower.SkyTowerSettings
import com.mordin.samathascope.scene.tower.TowerPhysics
import com.mordin.samathascope.scene.tower.TowerSceneController
import com.mordin.samathascope.scene.tower.TowerBodySnapshot
import org.junit.Test
import kotlin.math.abs

class TowerPhysicsTest {

  @Test
  fun releasedBlockEventuallySettlesOrFailsWithoutExploding() {
    val physics = TowerPhysics()
    val shape = physics.previewNextBlockShape()
    physics.spawnReleasedBlock(
      x = 5f,
      y = 2.2f,
      shape = shape,
      linearVelocityX = 0.08f,
      angularVelocity = 0.02f,
    )

    var result = physics.step(SceneState(calmness = 0.8f, focus = 0.7f, stability = 0.85f), 1f / 60f)
    var settledOrFailed = result.activeSettled || result.activeFailed
    for (step in 0 until 600) {
      result = physics.step(SceneState(calmness = 0.8f, focus = 0.7f, stability = 0.85f), 1f / 60f)
      if (result.activeSettled || result.activeFailed) {
        settledOrFailed = true
        break
      }
    }

    assertWithMessage("tower result=%s", result).that(settledOrFailed).isTrue()
    assertThat(result.bodies.size).isAtLeast(1)
    assertBodiesFinite(result.bodies)
  }

  @Test
  fun centeredDrops_keepBuildingAndNeverProduceNonFiniteSnapshots() {
    val controller = TowerSceneController()
    controller.reset(SkyTowerSettings(irregularity = 1f))
    val sceneState = settledSceneState(artefact = 0.12f)
    var snapshot = controller.step(sceneState, FRAME_DT, running = true)

    repeat(8) { dropIndex ->
      snapshot = waitForCenteredCarrier(controller, sceneState)
      controller.queueRelease()

      var sawActiveBody = false
      var resolved = false
      repeat(720) {
        snapshot = controller.step(sceneState, FRAME_DT, running = true)
        assertBodiesFinite(snapshot.bodies)
        val hasActiveBody = snapshot.bodies.any { it.active }
        sawActiveBody = sawActiveBody || hasActiveBody
        if (sawActiveBody && !hasActiveBody) {
          resolved = true
          return@repeat
        }
      }

      assertWithMessage("drop %s never resolved", dropIndex + 1).that(resolved).isTrue()
    }

    val placements = controller.summary().value.substringBefore(' ').toIntOrNull() ?: 0
    assertThat(placements).isAtLeast(6)
    assertThat(snapshot.towerHeight).isAtLeast(6)
  }

  @Test
  fun centeredDrops_stayInsidePlayfieldWhileStacking() {
    val controller = TowerSceneController()
    controller.reset(SkyTowerSettings(irregularity = 1f))
    val sceneState = settledSceneState(artefact = 0.12f)
    var snapshot = controller.step(sceneState, FRAME_DT, running = true)

    repeat(6) {
      snapshot = waitForCenteredCarrier(controller, sceneState)
      controller.queueRelease()
      var resolved = false
      for (frame in 0 until 720) {
        snapshot = controller.step(sceneState, FRAME_DT, running = true)
        snapshot.bodies.filter { body -> body.id != 0 }.forEach { body ->
          assertThat(body.x).isAtLeast(0.10f)
          assertThat(body.x).isAtMost(9.90f)
        }
        if (snapshot.bodies.none { body -> body.active }) {
          resolved = true
          break
        }
      }
      assertWithMessage("drop %s left the playfield without resolving", it + 1).that(resolved).isTrue()
    }

    assertThat(snapshot.towerHeight).isAtLeast(1)
  }

  private fun waitForCenteredCarrier(
    controller: TowerSceneController,
    sceneState: SceneState,
  ): com.mordin.samathascope.scene.tower.TowerRenderSnapshot {
    var snapshot = controller.step(sceneState, FRAME_DT, running = true)
    repeat(720) {
      assertBodiesFinite(snapshot.bodies)
      if (snapshot.carrier.visible && abs(snapshot.carrier.x - 5f) <= 0.18f) {
        return snapshot
      }
      snapshot = controller.step(sceneState, FRAME_DT, running = true)
    }
    throw AssertionError("carrier never returned to a centered release window")
  }

  private fun assertBodiesFinite(bodies: List<TowerBodySnapshot>) {
    bodies.forEach { body ->
      assertThat(body.x.isFinite()).isTrue()
      assertThat(body.y.isFinite()).isTrue()
      assertThat(body.angle.isFinite()).isTrue()
      assertThat(body.width).isGreaterThan(0f)
      assertThat(body.height).isGreaterThan(0f)
      assertThat(body.shapeVertices).isNotEmpty()
      assertThat(body.x).isAtLeast(-0.25f)
      assertThat(body.x).isAtMost(10.25f)
      assertThat(body.y).isAtMost(13.2f)
      body.shapeVertices.forEach { vertex ->
        assertThat(vertex.x.isFinite()).isTrue()
        assertThat(vertex.y.isFinite()).isTrue()
      }
      body.shapeLobes.flatten().forEach { vertex ->
        assertThat(vertex.x.isFinite()).isTrue()
        assertThat(vertex.y.isFinite()).isTrue()
      }
    }
  }

  private fun settledSceneState(artefact: Float): SceneState {
    return SceneState(
      calmness = 0.84f,
      focus = 0.72f,
      stability = 0.88f,
      drift = 0.04f,
      artefact = artefact,
    )
  }

  companion object {
    private const val FRAME_DT = 1f / 60f
  }
}
