package com.mordin.samathascope.scene.tower

import com.mordin.samathascope.scene.SceneState
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class TowerBodySnapshot(
  val id: Int,
  val x: Float,
  val y: Float,
  val width: Float,
  val height: Float,
  val angle: Float,
  val sleeping: Boolean,
  val active: Boolean,
  val shapeKind: TowerBlockShapeKind,
  val shapeVertices: List<TowerPoint>,
  val shapeLobes: List<List<TowerPoint>>,
)

data class TowerImpactEvent(
  val x: Float,
  val y: Float,
  val intensity: Float,
)

data class TowerPhysicsStepResult(
  val bodies: List<TowerBodySnapshot>,
  val activeBodyId: Int?,
  val topY: Float,
  val activeSettled: Boolean,
  val activeFailed: Boolean,
  val impactEvents: List<TowerImpactEvent>,
)

class TowerPhysics {
  private var settings = SkyTowerSettings()
  private var foundationShape = createFoundationShape(settings)
  private val settledStones = mutableListOf<SimStone>()
  private var activeStone: SimStone? = null
  private var simulationTimeSeconds = 0f
  private var nextBodyId = 1

  init {
    reset()
  }

  fun reset(settings: SkyTowerSettings = this.settings) {
    this.settings = settings.clamped()
    foundationShape = createFoundationShape(this.settings)
    settledStones.clear()
    activeStone = null
    simulationTimeSeconds = 0f
    nextBodyId = 1
  }

  fun hasActiveBody(): Boolean = activeStone != null

  fun previewNextBlockShape(): TowerBlockShape {
    return createTowerBlockShape(nextBodyId, settings)
  }

  fun spawnReleasedBlock(
    x: Float,
    y: Float,
    shape: TowerBlockShape,
    linearVelocityX: Float,
    angularVelocity: Float,
  ): Int {
    val id = nextBodyId++

    // Allow test helpers to reseed the active body without waiting for resolution.
    if (activeStone != null) {
      activeStone = null
    }

    activeStone = SimStone(
      id = id,
      shape = shape,
      x = clampStoneX(x, shape),
      y = y,
      angle = angularVelocity * 0.05f,
      vx = linearVelocityX,
      vy = 0f,
      angularVelocity = angularVelocity,
      supportId = NO_SUPPORT_ID,
      supportRatio = 0f,
      anchorSupportOffsetX = 0f,
      anchorAngle = 0f,
    )
    return id
  }

  fun step(sceneState: SceneState, dtSeconds: Float): TowerPhysicsStepResult {
    simulationTimeSeconds += dtSeconds

    if (settledStones.isNotEmpty()) {
      updateSettledStones(sceneState, dtSeconds)
    }

    val impactEvents = mutableListOf<TowerImpactEvent>()
    var activeSettled = false
    var activeFailed = false

    activeStone?.let { stone ->
      val substepCount = max(1, ceil(dtSeconds / MAX_SIMULATION_STEP_SECONDS).toInt())
      val substepSeconds = dtSeconds / substepCount.toFloat()

      repeat(substepCount) {
        stepActiveStone(
          stone = stone,
          sceneState = sceneState,
          dtSeconds = substepSeconds,
          impactEvents = impactEvents,
        )

        if (shouldFailActiveStone(stone)) {
          activeStone = null
          activeFailed = true
          return@let
        }

        if (shouldSettleActiveStone(stone, sceneState)) {
          settleActiveStone(stone)
          activeStone = null
          activeSettled = true
          return@let
        }
      }
    }

    return TowerPhysicsStepResult(
      bodies = snapshotBodies(),
      activeBodyId = activeStone?.id,
      topY = currentTopY(),
      activeSettled = activeSettled,
      activeFailed = activeFailed,
      impactEvents = impactEvents,
    )
  }

  private fun updateSettledStones(
    sceneState: SceneState,
    dtSeconds: Float,
  ) {
    val updatedById = HashMap<Int, SimStone>()
    settledStones
      .sortedByDescending { it.y }
      .forEach { stone ->
        val support = supportSurfaceFor(stone.supportId, updatedById) ?: foundationSurface()
        val heightFactor = ((FOUNDATION_Y - stone.y) / 4.8f).coerceIn(0f, 1.35f)
        val swayWave = sin(
          (simulationTimeSeconds * (1.6f + sceneState.artefact * 2.4f) + (stone.id * 0.37f)).toDouble()
        ).toFloat()
        val swayTarget = swayWave *
          (0.010f + heightFactor * 0.030f) *
          (0.10f + sceneState.artefact * 0.44f)

        val desiredBottomSupportX = support.centerX + stone.anchorSupportOffsetX + swayTarget
        val currentBottomSupportX = bottomSupportCenterX(stone.x, stone.angle, stone.shape)
        val supportSpring = (desiredBottomSupportX - currentBottomSupportX) * (
          2.0f + support.supportWidth * 0.12f + sceneState.stability * 0.35f
          )
        val supportDamping = (support.velocityX - stone.vx) * (1.9f + sceneState.stability * 0.30f)

        stone.vx += (supportSpring + supportDamping) * dtSeconds
        stone.vx *= (1f - ((2.2f - sceneState.artefact * 0.30f) * dtSeconds)).coerceIn(0f, 1f)
        stone.x = clampStoneX(stone.x + stone.vx * dtSeconds, stone.shape)

        val clampedBottomSupportX = clampBottomSupportXToSupport(
          desiredBottomSupportX = bottomSupportCenterX(stone.x, stone.angle, stone.shape),
          bodyWidth = effectiveBottomSupportWidth(stone.shape, stone.angle),
          support = support,
          minimumRatio = MIN_SETTLED_SUPPORT_RATIO,
        )
        val bottomCorrection = clampedBottomSupportX - bottomSupportCenterX(stone.x, stone.angle, stone.shape)
        if (bottomCorrection != 0f) {
          stone.x = clampStoneX(stone.x + bottomCorrection, stone.shape)
          stone.vx *= 0.60f
        }

        val alignmentDelta = support.centerX + stone.anchorSupportOffsetX - bottomSupportCenterX(stone.x, stone.angle, stone.shape)
        val targetAngle = (
          stone.anchorAngle * 0.40f +
            support.angle * 0.22f +
            swayTarget * 0.18f +
            alignmentDelta * 0.10f
          ).coerceIn(-MAX_SETTLED_ANGLE, MAX_SETTLED_ANGLE)
        val angleSpring = (targetAngle - stone.angle) * (
          2.6f + heightFactor * 1.2f + sceneState.artefact * 0.50f
          )
        val angleDamping = stone.angularVelocity * (2.7f + sceneState.stability * 0.30f)
        stone.angularVelocity += (angleSpring - angleDamping) * dtSeconds
        stone.angularVelocity = stone.angularVelocity.coerceIn(-MAX_SETTLED_ANGULAR_SPEED, MAX_SETTLED_ANGULAR_SPEED)
        stone.angle = (stone.angle + stone.angularVelocity * dtSeconds).coerceIn(-MAX_SETTLED_ANGLE, MAX_SETTLED_ANGLE)
        stone.y = support.topY - (stone.shape.height * 0.5f)

        updatedById[stone.id] = stone
      }
  }

  private fun stepActiveStone(
    stone: SimStone,
    sceneState: SceneState,
    dtSeconds: Float,
    impactEvents: MutableList<TowerImpactEvent>,
  ) {
    val previousBottom = stone.y + (stone.shape.height * 0.5f)
    val previousVerticalVelocity = stone.vy

    val airDrift = sceneState.drift * (
      0.16f +
        ((1f - sceneState.calmness) * 0.10f) +
        (sceneState.artefact * 0.06f)
      )
    val airJitter = sin(
      (simulationTimeSeconds * (5.3f + sceneState.artefact * 5.8f) + stone.id * 0.61f).toDouble()
    ).toFloat() * sceneState.artefact * 0.04f

    stone.vx += (airDrift + airJitter) * dtSeconds
    stone.vx *= (1f - AIR_LINEAR_DAMPING * dtSeconds).coerceIn(0f, 1f)
    stone.vy = (stone.vy + (GRAVITY_Y * dtSeconds)).coerceAtMost(MAX_FALL_SPEED)
    stone.angularVelocity += (sceneState.drift * 0.14f + airJitter * 0.40f) * dtSeconds
    stone.angularVelocity *= (1f - AIR_ANGULAR_DAMPING * dtSeconds).coerceIn(0f, 1f)
    stone.vx = stone.vx.coerceIn(-MAX_HORIZONTAL_SPEED, MAX_HORIZONTAL_SPEED)
    stone.angularVelocity = stone.angularVelocity.coerceIn(-MAX_ACTIVE_ANGULAR_SPEED, MAX_ACTIVE_ANGULAR_SPEED)

    var nextAngle = (stone.angle + stone.angularVelocity * dtSeconds).coerceIn(-MAX_ACTIVE_ANGLE, MAX_ACTIVE_ANGLE)
    var nextX = stone.x + (stone.vx * dtSeconds)
    val clampedX = clampStoneX(nextX, stone.shape)
    if (clampedX != nextX) {
      nextX = clampedX
      stone.vx *= WALL_VELOCITY_DAMPING
      stone.angularVelocity *= 0.72f
      nextAngle *= 0.92f
    }
    var nextY = stone.y + (stone.vy * dtSeconds)

    val support = findSupportForActiveStone(
      shape = stone.shape,
      previousBottom = previousBottom,
      nextX = nextX,
      nextY = nextY,
      nextAngle = nextAngle,
    )

    if (support != null) {
      val supportWidth = support.surface.supportWidth
      val frictionBlend = (
        0.12f +
          (support.ratio * 0.24f) +
          (sceneState.stability * 0.12f) +
          ((1f - sceneState.artefact) * 0.08f)
        ).coerceIn(0.14f, 0.34f)

      val currentBottomSupportX = bottomSupportCenterX(nextX, nextAngle, stone.shape)
      val boundedBottomSupportX = clampBottomSupportXToSupport(
        desiredBottomSupportX = currentBottomSupportX,
        bodyWidth = effectiveBottomSupportWidth(stone.shape, nextAngle),
        support = support.surface,
        minimumRatio = MIN_CONTACT_RATIO,
      )
      nextX += boundedBottomSupportX - currentBottomSupportX

      val alignmentDelta = support.surface.centerX - bottomSupportCenterX(nextX, nextAngle, stone.shape)
      nextX = clampStoneX(nextX + (alignmentDelta * frictionBlend * 0.55f), stone.shape)
      nextY = support.surface.topY - (stone.shape.height * 0.5f)

      val refinedRatio = overlapRatio(
        x = bottomSupportCenterX(nextX, nextAngle, stone.shape),
        width = effectiveBottomSupportWidth(stone.shape, nextAngle),
        supportX = support.surface.centerX,
        supportWidth = supportWidth,
      )
      stone.supportId = support.surface.bodyId
      stone.supportRatio = refinedRatio
      stone.vy = 0f
      stone.vx = (stone.vx * 0.72f) + ((support.surface.velocityX - stone.vx) * (0.18f + refinedRatio * 0.12f))

      val targetAngle = (support.surface.angle * 0.28f + alignmentDelta * 0.14f).coerceIn(-0.24f, 0.24f)
      stone.angularVelocity = (stone.angularVelocity + ((targetAngle - nextAngle) * (1.6f + refinedRatio * 0.8f))) * 0.52f
      nextAngle = (nextAngle + ((targetAngle - nextAngle) * (0.24f + refinedRatio * 0.10f))).coerceIn(
        -MAX_ACTIVE_ANGLE,
        MAX_ACTIVE_ANGLE,
      )

      if (previousVerticalVelocity > IMPACT_EVENT_THRESHOLD) {
        impactEvents += TowerImpactEvent(
          x = nextX,
          y = nextY + (stone.shape.height * 0.45f),
          intensity = (previousVerticalVelocity / 9.0f).coerceIn(0f, 1f),
        )
      }

      val supportRelativeVelocity = abs(stone.vx - support.surface.velocityX)
      val angleStillness = abs(stone.angularVelocity)
      val stillEnough = supportRelativeVelocity < (0.06f + sceneState.calmness * 0.08f) &&
        angleStillness < (0.10f + (1f - sceneState.artefact) * 0.04f)
      stone.restTime = if (refinedRatio >= MIN_SETTLE_RATIO && stillEnough) {
        stone.restTime + dtSeconds
      } else {
        0f
      }
    } else {
      stone.supportId = NO_SUPPORT_ID
      stone.supportRatio = 0f
      stone.restTime = 0f
    }

    stone.x = nextX
    stone.y = nextY
    stone.angle = nextAngle
  }

  private fun shouldSettleActiveStone(
    stone: SimStone,
    sceneState: SceneState,
  ): Boolean {
    return stone.supportId != NO_SUPPORT_ID &&
      stone.supportRatio >= MIN_SETTLE_RATIO &&
      stone.restTime >= (0.18f + sceneState.artefact * 0.18f)
  }

  private fun shouldFailActiveStone(stone: SimStone): Boolean {
    return stone.y > FAILURE_FLOOR_Y ||
      stone.x < (PLAYFIELD_LEFT_X - 0.42f) ||
      stone.x > (PLAYFIELD_RIGHT_X + 0.42f)
  }

  private fun settleActiveStone(stone: SimStone) {
    val support = supportSurfaceFor(stone.supportId, emptyMap()) ?: foundationSurface()
    val restingAngle = stone.angle.coerceIn(-MAX_SETTLED_ANGLE, MAX_SETTLED_ANGLE)
    settledStones += SimStone(
      id = stone.id,
      shape = stone.shape,
      x = stone.x,
      y = support.topY - (stone.shape.height * 0.5f),
      angle = restingAngle,
      vx = stone.vx * 0.25f,
      vy = 0f,
      angularVelocity = stone.angularVelocity * 0.25f,
      supportId = support.bodyId,
      supportRatio = stone.supportRatio,
      anchorSupportOffsetX = bottomSupportCenterX(stone.x, restingAngle, stone.shape) - support.centerX,
      anchorAngle = restingAngle,
    )
  }

  private fun findSupportForActiveStone(
    shape: TowerBlockShape,
    previousBottom: Float,
    nextX: Float,
    nextY: Float,
    nextAngle: Float,
  ): ActiveSupport? {
    val nextBottom = nextY + (shape.height * 0.5f)
    val bottomSupportX = bottomSupportCenterX(nextX, nextAngle, shape)
    val bottomSupportWidth = effectiveBottomSupportWidth(shape, nextAngle)

    return allSupportSurfaces()
      .mapNotNull { surface ->
        val sweptIntoSurface = previousBottom <= surface.topY + CONTACT_SNAP_DISTANCE &&
          nextBottom >= surface.topY - CONTACT_SNAP_DISTANCE
        val nearSurface = abs(nextBottom - surface.topY) <= CONTACT_SNAP_DISTANCE
        if (!sweptIntoSurface && !nearSurface) {
          return@mapNotNull null
        }
        val ratio = overlapRatio(
          x = bottomSupportX,
          width = bottomSupportWidth,
          supportX = surface.centerX,
          supportWidth = surface.supportWidth,
        )
        if (ratio < MIN_CONTACT_RATIO) {
          return@mapNotNull null
        }
        ActiveSupport(surface = surface, ratio = ratio)
      }
      .minWithOrNull(
        compareBy<ActiveSupport> { it.surface.topY }
          .thenByDescending { it.ratio }
      )
  }

  private fun allSupportSurfaces(): List<TowerSupportSurface> {
    return buildList {
      add(foundationSurface())
      settledStones
        .sortedByDescending { it.y }
        .forEach { stone ->
          add(surfaceForStone(stone))
        }
    }
  }

  private fun supportSurfaceFor(
    supportId: Int,
    updatedById: Map<Int, SimStone>,
  ): TowerSupportSurface? {
    if (supportId == FOUNDATION_ID) {
      return foundationSurface()
    }
    val stone = updatedById[supportId] ?: settledStones.firstOrNull { it.id == supportId } ?: return null
    return surfaceForStone(stone)
  }

  private fun foundationSurface(): TowerSupportSurface {
    return TowerSupportSurface(
      bodyId = FOUNDATION_ID,
      centerX = FOUNDATION_CENTER_X + foundationShape.topSupportOffsetX,
      topY = FOUNDATION_Y - (foundationShape.height * 0.5f),
      supportWidth = foundationShape.topSupportWidth,
      velocityX = 0f,
      angle = 0f,
    )
  }

  private fun surfaceForStone(stone: SimStone): TowerSupportSurface {
    return TowerSupportSurface(
      bodyId = stone.id,
      centerX = topSupportCenterX(stone.x, stone.angle, stone.shape),
      topY = stone.y - (stone.shape.height * 0.5f),
      supportWidth = effectiveTopSupportWidth(stone.shape, stone.angle),
      velocityX = stone.vx,
      angle = stone.angle,
    )
  }

  private fun snapshotBodies(): List<TowerBodySnapshot> {
    val snapshots = ArrayList<TowerBodySnapshot>(settledStones.size + if (activeStone != null) 2 else 1)
    snapshots += TowerBodySnapshot(
      id = FOUNDATION_ID,
      x = FOUNDATION_CENTER_X,
      y = FOUNDATION_Y,
      width = foundationShape.width,
      height = foundationShape.height,
      angle = 0f,
      sleeping = true,
      active = false,
      shapeKind = foundationShape.kind,
      shapeVertices = foundationShape.localVertices,
      shapeLobes = foundationShape.lobes.map { it.fixtureVertices },
    )
    settledStones.forEach { stone ->
      snapshots += TowerBodySnapshot(
        id = stone.id,
        x = stone.x,
        y = stone.y,
        width = stone.shape.width,
        height = stone.shape.height,
        angle = stone.angle,
        sleeping = abs(stone.vx) < 0.015f && abs(stone.angularVelocity) < 0.015f,
        active = false,
        shapeKind = stone.shape.kind,
        shapeVertices = stone.shape.localVertices,
        shapeLobes = stone.shape.lobes.map { it.fixtureVertices },
      )
    }
    activeStone?.let { stone ->
      snapshots += TowerBodySnapshot(
        id = stone.id,
        x = stone.x,
        y = stone.y,
        width = stone.shape.width,
        height = stone.shape.height,
        angle = stone.angle,
        sleeping = false,
        active = true,
        shapeKind = stone.shape.kind,
        shapeVertices = stone.shape.localVertices,
        shapeLobes = stone.shape.lobes.map { it.fixtureVertices },
      )
    }
    return snapshots
  }

  private fun currentTopY(): Float {
    val highestStoneTop = buildList {
      settledStones.forEach { add(it.y - (it.shape.height * 0.5f)) }
      activeStone?.let { add(it.y - (it.shape.height * 0.5f)) }
    }.minOrNull()
    return highestStoneTop ?: FOUNDATION_Y
  }

  private fun clampStoneX(
    x: Float,
    shape: TowerBlockShape,
  ): Float {
    val halfWidth = shape.width * 0.5f
    return x.coerceIn(PLAYFIELD_LEFT_X + halfWidth, PLAYFIELD_RIGHT_X - halfWidth)
  }

  private fun topSupportCenterX(
    x: Float,
    angle: Float,
    shape: TowerBlockShape,
  ): Float {
    return x + shape.topSupportOffsetX + (sin(angle.toDouble()).toFloat() * shape.height * 0.40f)
  }

  private fun bottomSupportCenterX(
    x: Float,
    angle: Float,
    shape: TowerBlockShape,
  ): Float {
    return x + shape.bottomSupportOffsetX - (sin(angle.toDouble()).toFloat() * shape.height * 0.40f)
  }

  private fun effectiveTopSupportWidth(
    shape: TowerBlockShape,
    angle: Float,
  ): Float {
    return (shape.topSupportWidth * (1f - abs(angle) * 0.28f))
      .coerceIn(shape.topSupportWidth * 0.42f, shape.width)
  }

  private fun effectiveBottomSupportWidth(
    shape: TowerBlockShape,
    angle: Float,
  ): Float {
    return (shape.bottomSupportWidth * (1f - abs(angle) * 0.24f))
      .coerceIn(shape.bottomSupportWidth * 0.48f, shape.width)
  }

  private fun clampBottomSupportXToSupport(
    desiredBottomSupportX: Float,
    bodyWidth: Float,
    support: TowerSupportSurface,
    minimumRatio: Float,
  ): Float {
    val maxDelta = maxContactDeltaX(bodyWidth, support.supportWidth, minimumRatio)
    return desiredBottomSupportX.coerceIn(
      support.centerX - maxDelta,
      support.centerX + maxDelta,
    )
  }

  private fun maxContactDeltaX(
    bodyWidth: Float,
    supportWidth: Float,
    minimumRatio: Float,
  ): Float {
    val safeBodyWidth = bodyWidth.coerceAtLeast(0.001f)
    return max(0f, (supportWidth * 0.5f) + (safeBodyWidth * 0.5f) - (safeBodyWidth * minimumRatio))
  }

  private fun overlapRatio(
    x: Float,
    width: Float,
    supportX: Float,
    supportWidth: Float,
  ): Float {
    val safeWidth = width.coerceAtLeast(0.001f)
    val left = max(x - (safeWidth * 0.5f), supportX - (supportWidth * 0.5f))
    val right = min(x + (safeWidth * 0.5f), supportX + (supportWidth * 0.5f))
    return ((right - left).coerceAtLeast(0f) / safeWidth).coerceIn(0f, 1f)
  }

  private data class SimStone(
    val id: Int,
    val shape: TowerBlockShape,
    var x: Float,
    var y: Float,
    var angle: Float,
    var vx: Float,
    var vy: Float,
    var angularVelocity: Float,
    var supportId: Int,
    var supportRatio: Float,
    var anchorSupportOffsetX: Float,
    var anchorAngle: Float,
    var restTime: Float = 0f,
  )

  private data class TowerSupportSurface(
    val bodyId: Int,
    val centerX: Float,
    val topY: Float,
    val supportWidth: Float,
    val velocityX: Float,
    val angle: Float,
  )

  private data class ActiveSupport(
    val surface: TowerSupportSurface,
    val ratio: Float,
  )

  companion object {
    const val BLOCK_HEIGHT = 0.52f
    const val FOUNDATION_Y = 11.8f

    private const val FOUNDATION_ID = 0
    private const val NO_SUPPORT_ID = -1
    private const val FOUNDATION_CENTER_X = 5f
    private const val PLAYFIELD_LEFT_X = 0.5f
    private const val PLAYFIELD_RIGHT_X = 9.5f

    private const val GRAVITY_Y = 19.8f
    private const val AIR_LINEAR_DAMPING = 0.32f
    private const val AIR_ANGULAR_DAMPING = 1.10f
    private const val MAX_SIMULATION_STEP_SECONDS = 1f / 120f
    private const val MAX_HORIZONTAL_SPEED = 1.35f
    private const val MAX_FALL_SPEED = 9.8f
    private const val MAX_ACTIVE_ANGLE = 0.82f
    private const val MAX_SETTLED_ANGLE = 0.26f
    private const val MAX_ACTIVE_ANGULAR_SPEED = 2.6f
    private const val MAX_SETTLED_ANGULAR_SPEED = 1.4f
    private const val WALL_VELOCITY_DAMPING = 0.18f
    private const val CONTACT_SNAP_DISTANCE = 0.045f
    private const val IMPACT_EVENT_THRESHOLD = 1.25f
    private const val FAILURE_FLOOR_Y = 13.2f
    private const val MIN_CONTACT_RATIO = 0.06f
    private const val MIN_SETTLE_RATIO = 0.18f
    private const val MIN_SETTLED_SUPPORT_RATIO = 0.14f
  }
}
