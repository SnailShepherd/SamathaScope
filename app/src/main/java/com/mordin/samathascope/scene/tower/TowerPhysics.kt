package com.mordin.samathascope.scene.tower

import com.mordin.samathascope.scene.SceneState
import org.dyn4j.dynamics.Body
import org.dyn4j.dynamics.BodyFixture
import org.dyn4j.geometry.MassType
import org.dyn4j.geometry.Polygon
import org.dyn4j.geometry.Rectangle
import org.dyn4j.geometry.Vector2
import org.dyn4j.world.World
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sign

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
  private val world = World<Body>()
  private var settings = SkyTowerSettings()
  private var foundationShape = createFoundationShape(settings)
  private var foundationBody: Body? = null
  private var leftWallBody: Body? = null
  private var rightWallBody: Body? = null
  private var catchFloorBody: Body? = null
  private val dynamicBodiesById = LinkedHashMap<Int, Body>()
  private val bodyIdMap = IdentityHashMap<Body, Int>()
  private val bodyShapeMap = IdentityHashMap<Body, TowerBlockShape>()
  private var activeBody: Body? = null
  private var activeBodyId: Int? = null
  private var activeStillFrames = 0
  private var accumulatorSeconds = 0f
  private var nextBodyId = 1

  init {
    reset()
  }

  fun reset(settings: SkyTowerSettings = this.settings) {
    this.settings = settings.clamped()
    foundationShape = createFoundationShape(this.settings)
    world.removeAllBodies()
    world.gravity = Vector2(0.0, -GRAVITY_MAGNITUDE)
    foundationBody = null
    leftWallBody = null
    rightWallBody = null
    catchFloorBody = null
    dynamicBodiesById.clear()
    bodyIdMap.clear()
    bodyShapeMap.clear()
    activeBody = null
    activeBodyId = null
    activeStillFrames = 0
    accumulatorSeconds = 0f
    nextBodyId = 1
    addStaticBodies()
  }

  fun hasActiveBody(): Boolean = activeBody != null

  fun removeTopSettledStone(): Boolean {
    val candidate = dynamicBodiesById
      .asSequence()
      .filter { (id, _) -> activeBodyId == null || id != activeBodyId }
      .minByOrNull { (_, body) -> bodyTopWorldY(body) }
      ?: return false
    removeDynamicBody(candidate.key, candidate.value)
    return true
  }

  fun settledStoneCount(): Int {
    return dynamicBodiesById.size - if (activeBodyId != null) 1 else 0
  }

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
    val body = Body()
    val fixture = BodyFixture(createConvexPolygon(shape.localVertices))
    fixture.friction = STONE_FRICTION
    fixture.restitution = STONE_RESTITUTION
    body.addFixture(fixture)
    body.linearDamping = STONE_LINEAR_DAMPING
    body.angularDamping = STONE_ANGULAR_DAMPING
    body.setMass(MassType.NORMAL)

    val spawnX = clampStoneX(x, shape)
    body.translate(spawnX.toDouble(), worldToPhysicsY(y))
    body.setLinearVelocity(linearVelocityX.toDouble(), 0.0)
    body.angularVelocity = (-angularVelocity).toDouble()

    world.addBody(body)
    dynamicBodiesById[id] = body
    bodyIdMap[body] = id
    bodyShapeMap[body] = shape

    activeBody = body
    activeBodyId = id
    activeStillFrames = 0

    return id
  }

  fun step(sceneState: SceneState, dtSeconds: Float): TowerPhysicsStepResult {
    val impactEvents = mutableListOf<TowerImpactEvent>()
    var activeSettled = false
    var activeFailed = false

    accumulatorSeconds += dtSeconds.coerceIn(0f, MAX_ACCUMULATED_DT)
    while (accumulatorSeconds >= FIXED_TIMESTEP_SECONDS) {
      val activeBeforeStep = activeBody
      val preStepVelocityY = activeBeforeStep?.linearVelocity?.y ?: 0.0

      world.update(FIXED_TIMESTEP_SECONDS.toDouble())
      accumulatorSeconds -= FIXED_TIMESTEP_SECONDS

      val activeAfterStep = activeBody
      if (activeBeforeStep != null && activeAfterStep != null && activeBeforeStep === activeAfterStep) {
        detectImpactEvent(
          body = activeAfterStep,
          preStepVelocityY = preStepVelocityY,
          impactEvents = impactEvents,
        )

        if (isOutOfBounds(activeAfterStep)) {
          val id = activeBodyId
          if (id != null) {
            removeDynamicBody(id, activeAfterStep)
          }
          activeBody = null
          activeBodyId = null
          activeStillFrames = 0
          activeFailed = true
          break
        }

        if (isAtRestEnough(activeAfterStep)) {
          activeStillFrames += 1
        } else {
          activeStillFrames = 0
        }
        if (activeStillFrames >= SETTLE_FRAMES_REQUIRED) {
          activeBody = null
          activeBodyId = null
          activeStillFrames = 0
          activeSettled = true
          break
        }
      }
    }

    return TowerPhysicsStepResult(
      bodies = snapshotBodies(),
      activeBodyId = activeBodyId,
      topY = currentTopY(),
      activeSettled = activeSettled,
      activeFailed = activeFailed,
      impactEvents = impactEvents,
    )
  }

  private fun snapshotBodies(): List<TowerBodySnapshot> {
    val snapshots = ArrayList<TowerBodySnapshot>(dynamicBodiesById.size + 1)
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
    dynamicBodiesById.entries
      .sortedBy { it.key }
      .forEach { (id, body) ->
        val shape = bodyShapeMap[body] ?: return@forEach
        val worldX = body.worldCenter.x.toFloat()
        val worldY = physicsToWorldY(body.worldCenter.y)
        val isActive = activeBodyId == id

      snapshots += TowerBodySnapshot(
        id = id,
        x = worldX,
        y = worldY,
        width = shape.width,
        height = shape.height,
        angle = (-body.transform.rotationAngle).toFloat(),
        sleeping = body.isAtRest,
        active = isActive,
        shapeKind = shape.kind,
        shapeVertices = shape.localVertices,
        shapeLobes = shape.lobes.map { it.fixtureVertices },
      )
    }
    return snapshots
  }

  private fun currentTopY(): Float {
    val highestStoneTop = dynamicBodiesById.minOfOrNull { (_, body) ->
      bodyTopWorldY(body)
    }
    return highestStoneTop ?: FOUNDATION_Y
  }

  private fun clampStoneX(
    x: Float,
    shape: TowerBlockShape,
  ): Float {
    val halfWidth = shape.width * 0.5f
    return x.coerceIn(PLAYFIELD_LEFT_X + halfWidth, PLAYFIELD_RIGHT_X - halfWidth)
  }

  private fun bodyTopWorldY(body: Body): Float {
    val shape = bodyShapeMap[body] ?: return FOUNDATION_Y
    return physicsToWorldY(body.worldCenter.y) - (shape.height * 0.5f)
  }

  private fun worldToPhysicsY(worldY: Float): Double {
    return (FOUNDATION_Y - worldY).toDouble()
  }

  private fun physicsToWorldY(physicsY: Double): Float {
    return FOUNDATION_Y - physicsY.toFloat()
  }

  private fun detectImpactEvent(
    body: Body,
    preStepVelocityY: Double,
    impactEvents: MutableList<TowerImpactEvent>,
  ) {
    if (preStepVelocityY >= -IMPACT_EVENT_THRESHOLD_VELOCITY) {
      return
    }
    val postStepVelocityY = body.linearVelocity.y
    val strongSlowdown = postStepVelocityY > preStepVelocityY * IMPACT_SLOWDOWN_RATIO
    if (!strongSlowdown) {
      return
    }
    val shape = bodyShapeMap[body] ?: return
    impactEvents += TowerImpactEvent(
      x = body.worldCenter.x.toFloat(),
      y = physicsToWorldY(body.worldCenter.y) + (shape.height * 0.45f),
      intensity = ((-preStepVelocityY) / IMPACT_EVENT_NORMALIZER).toFloat().coerceIn(0f, 1f),
    )
  }

  private fun isAtRestEnough(body: Body): Boolean {
    val linear = body.linearVelocity
    val linearSpeed = hypot(linear.x, linear.y)
    return linearSpeed <= SETTLE_LINEAR_SPEED_THRESHOLD &&
      abs(body.angularVelocity) <= SETTLE_ANGULAR_SPEED_THRESHOLD
  }

  private fun isOutOfBounds(body: Body): Boolean {
    val worldX = body.worldCenter.x.toFloat()
    val worldY = physicsToWorldY(body.worldCenter.y)
    return worldX < (PLAYFIELD_LEFT_X - OUT_OF_BOUNDS_MARGIN_X) ||
      worldX > (PLAYFIELD_RIGHT_X + OUT_OF_BOUNDS_MARGIN_X) ||
      worldY > FAILURE_FLOOR_Y
  }

  private fun removeDynamicBody(
    id: Int,
    body: Body,
  ) {
    dynamicBodiesById.remove(id)
    bodyIdMap.remove(body)
    bodyShapeMap.remove(body)
    world.removeBody(body)
  }

  private fun addStaticBodies() {
    val resolved = resolveSkyTowerSettings(settings)
    foundationShape = rectangleShape(width = resolved.foundationWidth, height = FOUNDATION_HEIGHT)

    val foundation = Body()
    val foundationFixture = BodyFixture(
      Rectangle(foundationShape.width.toDouble(), foundationShape.height.toDouble()),
    )
    foundationFixture.friction = STONE_FRICTION
    foundationFixture.restitution = STONE_RESTITUTION
    foundation.addFixture(foundationFixture)
    foundation.setMass(MassType.INFINITE)
    foundation.translate(FOUNDATION_CENTER_X.toDouble(), worldToPhysicsY(FOUNDATION_Y))
    world.addBody(foundation)
    foundationBody = foundation

    val wallHeight = WALL_HEIGHT.toDouble()
    val wallThickness = WALL_THICKNESS.toDouble()
    val wallCenterY = WALL_CENTER_PHYSICS_Y

    val leftWall = Body()
    val leftFixture = BodyFixture(Rectangle(wallThickness, wallHeight))
    leftFixture.friction = STONE_FRICTION
    leftFixture.restitution = STONE_RESTITUTION
    leftWall.addFixture(leftFixture)
    leftWall.setMass(MassType.INFINITE)
    leftWall.translate((PLAYFIELD_LEFT_X - (WALL_THICKNESS * 0.5f)).toDouble(), wallCenterY)
    world.addBody(leftWall)
    leftWallBody = leftWall

    val rightWall = Body()
    val rightFixture = BodyFixture(Rectangle(wallThickness, wallHeight))
    rightFixture.friction = STONE_FRICTION
    rightFixture.restitution = STONE_RESTITUTION
    rightWall.addFixture(rightFixture)
    rightWall.setMass(MassType.INFINITE)
    rightWall.translate((PLAYFIELD_RIGHT_X + (WALL_THICKNESS * 0.5f)).toDouble(), wallCenterY)
    world.addBody(rightWall)
    rightWallBody = rightWall

    val catchFloor = Body()
    val catchFixture = BodyFixture(Rectangle(CATCH_FLOOR_WIDTH.toDouble(), CATCH_FLOOR_HEIGHT.toDouble()))
    catchFixture.friction = STONE_FRICTION
    catchFixture.restitution = STONE_RESTITUTION
    catchFloor.addFixture(catchFixture)
    catchFloor.setMass(MassType.INFINITE)
    catchFloor.translate(FOUNDATION_CENTER_X.toDouble(), CATCH_FLOOR_PHYSICS_Y)
    world.addBody(catchFloor)
    catchFloorBody = catchFloor
  }

  private fun createConvexPolygon(vertices: List<TowerPoint>): Polygon {
    val candidate = toPhysicsLocalPoints(vertices)
    return try {
      Polygon(*candidate.toTypedArray())
    } catch (_: IllegalArgumentException) {
      val hull = convexHull(candidate)
      if (hull.size >= 3) {
        Polygon(*hull.toTypedArray())
      } else {
        boundingPolygon(candidate)
      }
    }
  }

  private fun boundingPolygon(points: List<Vector2>): Polygon {
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    val safeMaxX = if (maxX - minX < MIN_POLYGON_SPAN) minX + MIN_POLYGON_SPAN else maxX
    val safeMaxY = if (maxY - minY < MIN_POLYGON_SPAN) minY + MIN_POLYGON_SPAN else maxY
    return Polygon(
      Vector2(minX, minY),
      Vector2(safeMaxX, minY),
      Vector2(safeMaxX, safeMaxY),
      Vector2(minX, safeMaxY),
    )
  }

  private fun toPhysicsLocalPoints(vertices: List<TowerPoint>): List<Vector2> {
    // Flip Y from screen-space to dyn4j coordinates; reverse to maintain CCW winding.
    return vertices
      .asReversed()
      .map { point -> Vector2(point.x.toDouble(), (-point.y).toDouble()) }
  }

  private fun convexHull(points: List<Vector2>): List<Vector2> {
    if (points.size <= 3) {
      return points
    }

    val sorted = points
      .distinctBy { it.x.toString() + ":" + it.y.toString() }
      .sortedWith(compareBy<Vector2> { it.x }.thenBy { it.y })
    if (sorted.size <= 3) {
      return sorted
    }

    val lower = mutableListOf<Vector2>()
    sorted.forEach { point ->
      while (lower.size >= 2 && cross(lower[lower.lastIndex - 1], lower.last(), point) <= 0.0) {
        lower.removeAt(lower.lastIndex)
      }
      lower += point
    }

    val upper = mutableListOf<Vector2>()
    sorted.asReversed().forEach { point ->
      while (upper.size >= 2 && cross(upper[upper.lastIndex - 1], upper.last(), point) <= 0.0) {
        upper.removeAt(upper.lastIndex)
      }
      upper += point
    }

    if (lower.isEmpty() || upper.isEmpty()) {
      return sorted.take(3)
    }

    val hull = mutableListOf<Vector2>()
    hull.addAll(lower.dropLast(1))
    hull.addAll(upper.dropLast(1))
    return if (hull.size >= 3) hull else sorted.take(3)
  }

  private fun cross(
    a: Vector2,
    b: Vector2,
    c: Vector2,
  ): Double {
    return ((b.x - a.x) * (c.y - a.y)) - ((b.y - a.y) * (c.x - a.x))
  }

  companion object {
    const val BLOCK_HEIGHT = 0.78f
    const val FOUNDATION_Y = 11.8f

    private const val FOUNDATION_ID = 0
    private const val FOUNDATION_CENTER_X = 5f
    private const val PLAYFIELD_LEFT_X = 0.5f
    private const val PLAYFIELD_RIGHT_X = 9.5f

    private const val FIXED_TIMESTEP_SECONDS = 1f / 60f
    private const val MAX_ACCUMULATED_DT = 0.25f
    private const val GRAVITY_MAGNITUDE = 18.0

    private const val STONE_FRICTION = 0.85
    private const val STONE_RESTITUTION = 0.04
    private const val STONE_LINEAR_DAMPING = 1.2
    private const val STONE_ANGULAR_DAMPING = 2.0

    private const val SETTLE_LINEAR_SPEED_THRESHOLD = 0.08
    private const val SETTLE_ANGULAR_SPEED_THRESHOLD = 0.10
    private const val SETTLE_FRAMES_REQUIRED = 20

    private const val IMPACT_EVENT_THRESHOLD_VELOCITY = 1.6
    private const val IMPACT_SLOWDOWN_RATIO = 0.35
    private const val IMPACT_EVENT_NORMALIZER = 9.0

    private const val FAILURE_FLOOR_Y = 13.2f
    private const val OUT_OF_BOUNDS_MARGIN_X = 1.2f

    private const val FOUNDATION_HEIGHT = 0.62f

    private const val WALL_THICKNESS = 0.30f
    private const val WALL_HEIGHT = 26f
    private const val WALL_CENTER_PHYSICS_Y = 3.0

    private const val CATCH_FLOOR_WIDTH = 20f
    private const val CATCH_FLOOR_HEIGHT = 0.5f
    private const val CATCH_FLOOR_PHYSICS_Y = -6.0

    private const val MIN_POLYGON_SPAN = 1e-3
  }
}
