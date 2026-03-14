package com.mordin.samathascope

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

enum class GameId {
  SKY_TOWER,
  INK_GARDEN,
  FIRE_KEEPER,
  SCRIPTORIUM,
}

data class GameGuide(
  val title: String,
  val lines: List<String>,
)

fun GameId.displayName(): String {
  return when (this) {
    GameId.SKY_TOWER -> "Sky Tower"
    GameId.INK_GARDEN -> "Ink Garden"
    GameId.FIRE_KEEPER -> "Fire Keeper"
    GameId.SCRIPTORIUM -> "Scriptorium"
  }
}

fun GameId.description(): String {
  return when (this) {
    GameId.SKY_TOWER -> "Release one composite stone at a time and build a springy tower inside a contained playfield while friction and wobble fight for balance."
    GameId.INK_GARDEN -> "A sumi-e brush painting unfolds on white paper; your EEG shapes ink confidence, wash, and restraint."
    GameId.FIRE_KEEPER -> "A passive campfire scene shaped by steadiness, drift, correction, and fatigue."
    GameId.SCRIPTORIUM -> "A passive manuscript that writes itself more clearly when the state is stable."
  }
}

fun GameId.inputHint(): String {
  return when (this) {
    GameId.SKY_TOWER -> "Tap once to drop the hovering stone. Wait for it to land and calm down before the next one."
    else -> "Passive scene: settle, stay awake, and let the scene respond."
  }
}

fun GameId.startHint(): String = "Press Start to begin ${displayName()}."

fun GameId.guide(): GameGuide {
  return when (this) {
    GameId.SKY_TOWER -> GameGuide(
      title = "Sky Tower guide",
      lines = listOf(
        "One tap releases one composite stone. Wait for the carrier to return before tapping again.",
        "The custom stack simulator only locks a stone in when it actually lands on the platform or another stone.",
        "Settledness improves frictional grip, damping, and the way the tower recovers after each landing.",
        "Mind Wandering pushes the carrier off line and leans the stack sideways before and after release.",
        "Artefact Score makes the whole tower springier, wobblier, and slower to calm down.",
        "Invisible side walls keep misses inside the playfield instead of letting stones drift into the HUD.",
        "Effortful Focus gives a brief rescue pulse that helps a shaky stone catch a safer landing.",
      ),
    )

    GameId.INK_GARDEN -> GameGuide(
      title = "Ink Garden guide",
      lines = listOf(
        "This scene is passive after Start: one sumi-e composition reveals itself stroke by stroke.",
        "Settledness slows the pacing and leaves more breathing room, cleaner wash, and calmer negative space.",
        "Mind Wandering bends the brush path sideways and loosens branch confidence.",
        "Artefact Score roughens the ink edge, adds dry-brush breakup, and makes joints less composed.",
        "Effortful Focus briefly sharpens taper, darkens pooled ink, and helps the composition gather itself.",
      ),
    )

    GameId.FIRE_KEEPER -> GameGuide(
      title = "Fire Keeper guide",
      lines = listOf(
        "This scene stays passive once started.",
        "Settledness makes the flame upright, cohesive, and warm.",
        "Mind Wandering makes the flame lean, wander, and split unevenly.",
        "Artefact Score drives sparks, smoke bursts, and gusty jitter.",
        "Effortful Focus briefly rekindles the fire with a clean ember lift.",
      ),
    )

    GameId.SCRIPTORIUM -> GameGuide(
      title = "Scriptorium guide",
      lines = listOf(
        "This scene is passive after Start.",
        "Settledness improves legibility, continuity, and ornament completion.",
        "Mind Wandering causes wandering glyphs, crossed lines, and unfinished passages.",
        "Artefact Score appears as blotches, tears, and scratchy interruptions.",
        "Effortful Focus briefly sharpens the quill and reveals repaired details.",
      ),
    )
  }
}

fun GameId.isHybrid(): Boolean = this == GameId.SKY_TOWER

sealed interface GameEvent {
  data object Tap : GameEvent
}

data class GameSignalSnapshot(
  val stability: Float = 0f,
  val drift: Float = 0f,
  val noise: Float = 0f,
  val fatigue: Float = 0f,
  val precision: Float = 0f,
  val correctionPulse: Float = 0f,
  val stateLabel: StateLabel = StateLabel.UNCERTAIN,
  val poorSignal: Int = 255,
  val elapsedSeconds: Int = 0,
  val batteryPercent: Int? = null,
)

data class GameHudState(
  val title: String = GameId.SKY_TOWER.displayName(),
  val summaryLabel: String = "Tower height",
  val summaryValue: String = "0 stones",
  val inputHint: String = GameId.SKY_TOWER.startHint(),
  val inputEnabled: Boolean = false,
  val stabilityPercent: Int = 0,
  val driftPercent: Int = 0,
  val noisePercent: Int = 0,
  val fatiguePercent: Int = 0,
  val correctionPercent: Int = 0,
  val poorSignal: Int = 255,
  val elapsedSeconds: Int = 0,
  val batteryPercent: Int? = null,
  val stateLabel: StateLabel = StateLabel.UNCERTAIN,
)

data class GameAudioState(
  val ambience: Float = 0f,
  val motion: Float = 0f,
  val glitch: Float = 0f,
  val accent: Float = 0f,
  val warmth: Float = 0f,
  val muted: Boolean = true,
)

sealed interface GameRuntimeState {
  val timeSeconds: Float
}

data class TowerCarrier(
  val x: Float = 0.20f,
  val y: Float = TOWER_CARRIER_Y,
  val width: Float = TOWER_BLOCK_WIDTH,
  val direction: Float = 1f,
  val speed: Float = 0.21f,
  val visible: Boolean = true,
)

data class TowerBody(
  val id: Int,
  val x: Float,
  val y: Float,
  val width: Float,
  val height: Float = TOWER_BLOCK_HEIGHT,
  val angle: Float = 0f,
  val vx: Float = 0f,
  val vy: Float = 0f,
  val angularVelocity: Float = 0f,
  val sleeping: Boolean = false,
  val restSeconds: Float = 0f,
  val grounded: Boolean = false,
  val supportId: Int? = null,
)

data class SkyTowerRuntimeState(
  override val timeSeconds: Float = 0f,
  val carrier: TowerCarrier = TowerCarrier(),
  val bodies: List<TowerBody> = listOf(towerFoundation()),
  val activeBodyId: Int? = null,
  val spawnDelaySeconds: Float = 0f,
  val swaySeverity: Float = 0f,
  val tremor: Float = 0f,
  val placements: Int = 0,
  val misses: Int = 0,
  val lastError: Float = 0f,
  val nextBodyId: Int = 1,
) : GameRuntimeState

data class InkCell(
  val column: Int,
  val row: Int,
  val pigment: Float,
  val wetness: Float,
  val edge: Float,
)

data class InkBlotch(
  val x: Float,
  val y: Float,
  val radius: Float,
  val alpha: Float,
)

data class InkGardenRuntimeState(
  override val timeSeconds: Float = 0f,
  val cells: List<InkCell> = initialInkCells(),
  val splatters: List<InkBlotch> = emptyList(),
  val completion: Float = 0f,
  val bloom: Float = 0f,
  val granulation: Float = 0f,
  val spawnTimer: Float = 0f,
  val nextSeedIndex: Int = 0,
) : GameRuntimeState

data class FireKeeperRuntimeState(
  override val timeSeconds: Float = 0f,
  val flameHeight: Float = 0.40f,
  val lean: Float = 0f,
  val turbulence: Float = 0.12f,
  val emberLift: Float = 0.08f,
  val smokeDensity: Float = 0.05f,
  val warmth: Float = 0.45f,
) : GameRuntimeState

data class ScriptoriumRuntimeState(
  override val timeSeconds: Float = 0f,
  val progress: Float = 0.08f,
  val legibility: Float = 0.45f,
  val ornament: Float = 0.12f,
  val blotches: Float = 0.04f,
  val fade: Float = 0f,
  val revealedLines: Int = 1,
) : GameRuntimeState

interface GameController<TState : GameRuntimeState> {
  val id: GameId
  fun initialState(): TState
  fun step(state: TState, dtSeconds: Float, signals: GameSignalSnapshot, events: List<GameEvent>): TState
  fun hud(state: TState, signals: GameSignalSnapshot): GameHudState
  fun audio(state: TState, signals: GameSignalSnapshot): GameAudioState
}

interface AnyGameController {
  val id: GameId
  fun initialState(): GameRuntimeState
  fun step(state: GameRuntimeState, dtSeconds: Float, signals: GameSignalSnapshot, events: List<GameEvent>): GameRuntimeState
  fun hud(state: GameRuntimeState, signals: GameSignalSnapshot): GameHudState
  fun audio(state: GameRuntimeState, signals: GameSignalSnapshot): GameAudioState
}

private class ErasedGameController<TState : GameRuntimeState>(
  private val delegate: GameController<TState>,
) : AnyGameController {
  override val id: GameId = delegate.id

  override fun initialState(): GameRuntimeState = delegate.initialState()

  @Suppress("UNCHECKED_CAST")
  override fun step(
    state: GameRuntimeState,
    dtSeconds: Float,
    signals: GameSignalSnapshot,
    events: List<GameEvent>,
  ): GameRuntimeState {
    return delegate.step(state as TState, dtSeconds, signals, events)
  }

  @Suppress("UNCHECKED_CAST")
  override fun hud(state: GameRuntimeState, signals: GameSignalSnapshot): GameHudState {
    return delegate.hud(state as TState, signals)
  }

  @Suppress("UNCHECKED_CAST")
  override fun audio(state: GameRuntimeState, signals: GameSignalSnapshot): GameAudioState {
    return delegate.audio(state as TState, signals)
  }
}

fun <TState : GameRuntimeState> GameController<TState>.erase(): AnyGameController {
  return ErasedGameController(this)
}

class SkyTowerController : GameController<SkyTowerRuntimeState> {
  override val id: GameId = GameId.SKY_TOWER

  override fun initialState(): SkyTowerRuntimeState = SkyTowerRuntimeState()

  override fun step(
    state: SkyTowerRuntimeState,
    dtSeconds: Float,
    signals: GameSignalSnapshot,
    events: List<GameEvent>,
  ): SkyTowerRuntimeState {
    var next = if (events.any { it is GameEvent.Tap } && canReleaseCarrier(state)) {
      releaseCarrier(state, signals)
    } else {
      state
    }

    var remaining = dtSeconds.coerceIn(1f / 120f, 0.12f)
    while (remaining > 0f) {
      val substep = min(remaining, 1f / 60f)
      next = stepTowerPhysics(next, substep, signals)
      remaining -= substep
    }

    return finalizeTower(next, signals)
  }

  override fun hud(state: SkyTowerRuntimeState, signals: GameSignalSnapshot): GameHudState {
    return GameHudState(
      title = id.displayName(),
      summaryLabel = "Tower height",
      summaryValue = "${state.placements} blocks",
      inputHint = id.inputHint(),
      stabilityPercent = (signals.stability * 100f).roundToInt(),
      driftPercent = (signals.drift * 100f).roundToInt(),
      noisePercent = (signals.noise * 100f).roundToInt(),
      fatiguePercent = (signals.fatigue * 100f).roundToInt(),
      correctionPercent = (signals.correctionPulse * 100f).roundToInt(),
      poorSignal = signals.poorSignal,
      elapsedSeconds = signals.elapsedSeconds,
      batteryPercent = signals.batteryPercent,
      stateLabel = signals.stateLabel,
    )
  }

  override fun audio(state: SkyTowerRuntimeState, signals: GameSignalSnapshot): GameAudioState {
    val activeMotion = state.bodies.firstOrNull { it.id == state.activeBodyId }
    return GameAudioState(
      ambience = clamp01(0.18f + (state.placements * 0.05f) + (signals.stability * 0.18f)),
      motion = clamp01((signals.drift * 0.45f) + abs(activeMotion?.vx ?: 0f) + state.swaySeverity),
      glitch = clamp01((signals.noise * 0.70f) + (state.tremor * 0.45f)),
      accent = clamp01(signals.correctionPulse + (signals.precision * 0.18f)),
      warmth = clamp01(0.22f + (signals.stability * 0.32f) - (signals.fatigue * 0.10f)),
      muted = false,
    )
  }

  private fun canReleaseCarrier(state: SkyTowerRuntimeState): Boolean {
    return state.activeBodyId == null && state.spawnDelaySeconds <= 0f && state.carrier.visible
  }

  private fun releaseCarrier(
    state: SkyTowerRuntimeState,
    signals: GameSignalSnapshot,
  ): SkyTowerRuntimeState {
    val released = TowerBody(
      id = state.nextBodyId,
      x = state.carrier.x,
      y = state.carrier.y,
      width = state.carrier.width,
      vx = state.carrier.direction * state.carrier.speed * 0.18f,
      vy = 0f,
      angle = 0f,
      angularVelocity = (signals.drift - signals.stability) * 0.10f,
      sleeping = false,
    )
    return state.copy(
      bodies = state.bodies + released,
      activeBodyId = released.id,
      carrier = state.carrier.copy(visible = false),
      nextBodyId = state.nextBodyId + 1,
    )
  }

  private fun stepTowerPhysics(
    state: SkyTowerRuntimeState,
    dtSeconds: Float,
    signals: GameSignalSnapshot,
  ): SkyTowerRuntimeState {
    var carrier = state.carrier
    var spawnDelay = (state.spawnDelaySeconds - dtSeconds).coerceAtLeast(0f)
    val nextTime = state.timeSeconds + dtSeconds

    if (state.activeBodyId == null && spawnDelay <= 0f) {
      val driftWave = sin(((nextTime * (1.1f + (signals.drift * 1.8f))) + 0.6f).toDouble()).toFloat() *
        (0.02f + (signals.drift * 0.06f))
      val speed = 0.16f + (signals.drift * 0.10f) + ((1f - signals.stability) * 0.04f)
      var nextX = carrier.x + ((carrier.direction * speed) + driftWave) * dtSeconds
      var nextDirection = carrier.direction
      if (nextX <= 0.18f) {
        nextX = 0.18f
        nextDirection = 1f
      } else if (nextX >= 0.82f) {
        nextX = 0.82f
        nextDirection = -1f
      }
      carrier = carrier.copy(
        x = nextX,
        direction = nextDirection,
        speed = speed,
        visible = true,
      )
    }

    var tremor = damp(
      current = state.tremor,
      amountPerSecond = 2.2f + (signals.stability * 2.6f),
      dtSeconds = dtSeconds,
    )
    tremor = clamp01(tremor + (signals.noise * 0.018f))

    val updatedBodies = state.bodies.toMutableList()
    for (index in 1 until updatedBodies.size) {
      val body = updatedBodies[index]
      updatedBodies[index] = updateTowerBody(
        index = index,
        body = body,
        allBodies = updatedBodies,
        activeBodyId = state.activeBodyId,
        dtSeconds = dtSeconds,
        signals = signals,
        timeSeconds = nextTime,
      )
    }

    var nextBodies = updatedBodies.toList()
    var nextActiveBodyId = state.activeBodyId
    var nextPlacements = state.placements
    var nextMisses = state.misses
    var nextLastError = state.lastError

    val activeBody = nextBodies.firstOrNull { it.id == state.activeBodyId }
    if (activeBody != null) {
      when {
        activeBody.supportId != null && activeBody.sleeping -> {
          nextPlacements += 1
          nextActiveBodyId = null
          spawnDelay = 0.32f
          carrier = carrier.copy(
            visible = false,
            x = if (carrier.direction >= 0f) 0.22f else 0.78f,
            width = TOWER_BLOCK_WIDTH,
          )
          val support = nextBodies.firstOrNull { it.id == activeBody.supportId }
          nextLastError = support?.let { abs(activeBody.x - it.x) } ?: 0f
        }

        activeBody.grounded && activeBody.sleeping -> {
          nextBodies = nextBodies.filterNot { it.id == activeBody.id }
          nextActiveBodyId = null
          nextMisses += 1
          spawnDelay = 0.28f
          carrier = carrier.copy(
            visible = false,
            x = if (carrier.direction >= 0f) 0.22f else 0.78f,
            width = TOWER_BLOCK_WIDTH,
          )
          nextLastError = 0.18f
          tremor = clamp01(max(tremor, 0.18f + (signals.noise * 0.30f)))
        }

        activeBody.y > 1.08f || activeBody.x < -0.18f || activeBody.x > 1.18f -> {
          nextBodies = nextBodies.filterNot { it.id == activeBody.id }
          nextActiveBodyId = null
          nextMisses += 1
          spawnDelay = 0.28f
          carrier = carrier.copy(visible = false, width = TOWER_BLOCK_WIDTH)
          nextLastError = 0.18f
        }
      }
    }

    if (nextActiveBodyId == null && spawnDelay <= 0f) {
      carrier = carrier.copy(visible = true)
    }

    return state.copy(
      timeSeconds = nextTime,
      carrier = carrier,
      bodies = nextBodies,
      activeBodyId = nextActiveBodyId,
      spawnDelaySeconds = spawnDelay,
      swaySeverity = towerSwaySeverity(nextBodies),
      tremor = tremor,
      placements = nextPlacements,
      misses = nextMisses,
      lastError = nextLastError,
    )
  }

  private fun updateTowerBody(
    index: Int,
    body: TowerBody,
    allBodies: List<TowerBody>,
    activeBodyId: Int?,
    dtSeconds: Float,
    signals: GameSignalSnapshot,
    timeSeconds: Float,
  ): TowerBody {
    if (body.id != activeBodyId && body.sleeping && signals.noise < 0.72f) {
      return body
    }

    var vx = body.vx
    var vy = body.vy
    var angle = body.angle
    var angularVelocity = body.angularVelocity
    var grounded = false
    var supportId: Int? = null
    var restSeconds = body.restSeconds
    var sleeping = body.sleeping

    if (body.sleeping && signals.noise >= 0.72f && index >= allBodies.lastIndex - 1) {
      val wakePulse = sin(((timeSeconds * 11f) + body.id).toDouble()).toFloat()
      if (wakePulse > 0.92f) {
        vx += (wakePulse - 0.90f) * 0.18f * sign(wakePulse)
        angularVelocity += wakePulse * 0.22f
        sleeping = false
        restSeconds = 0f
      }
    }

    if (!sleeping) {
      val gravity = 1.20f + ((1f - signals.stability) * 0.18f)
      val wind = sin(((timeSeconds * (1.0f + (signals.drift * 1.6f))) + (body.id * 0.9f)).toDouble()).toFloat() *
        (0.05f + (signals.drift * 0.10f) + (signals.noise * 0.04f))
      vx += wind * dtSeconds
      vy += gravity * dtSeconds
      angularVelocity += wind * 0.9f * dtSeconds

      if (body.id == activeBodyId) {
        val support = bodiesBelow(body, allBodies).minByOrNull { it.y }
        if (support != null) {
          val guidance = 0.4f + (signals.precision * 1.0f) + (signals.correctionPulse * 1.3f)
          vx += (support.x - body.x) * guidance * dtSeconds
          angularVelocity += (support.x - body.x) * 0.6f * dtSeconds
        }
      }

      val linearDamping = (1f - ((0.45f + (signals.stability * 1.9f)) * dtSeconds)).coerceIn(0.75f, 0.99f)
      val angularDamping = (1f - ((0.70f + (signals.stability * 2.5f)) * dtSeconds)).coerceIn(0.72f, 0.995f)
      vx *= linearDamping
      angularVelocity *= angularDamping
      angle = (angle + (angularVelocity * dtSeconds)).coerceIn(-0.28f, 0.28f)
    }

    var x = body.x + (vx * dtSeconds)
    var y = body.y + (vy * dtSeconds)
    val contact = findSupportContact(
      body = body.copy(x = x, y = y, angle = angle),
      index = index,
      allBodies = allBodies,
    )

    if (contact != null) {
      y = contact.topY - (body.height / 2f)
      supportId = contact.supportId
      grounded = contact.grounded
      val offset = x - contact.supportCenter
      vx += offset * (0.8f + (signals.drift * 0.8f) - (signals.stability * 0.35f)) * dtSeconds
      angularVelocity += offset * (2.0f - (signals.stability * 0.9f))
      vy = if (vy > 0f) -vy * 0.04f else 0f
      vx *= 0.86f + (signals.stability * 0.08f)

      if (contact.supportRatio < TOWER_MIN_SUPPORT_RATIO) {
        vx += sign(offset.takeIf { it != 0f } ?: 1f) * 0.08f
        angularVelocity += sign(offset.takeIf { it != 0f } ?: 1f) * 0.05f
        restSeconds = 0f
        sleeping = false
      } else {
        val stable = abs(vx) < 0.02f && abs(vy) < 0.03f && abs(angularVelocity) < 0.10f && abs(offset) < 0.045f
        restSeconds = if (stable) restSeconds + dtSeconds else 0f
        sleeping = restSeconds >= 0.26f
      }
    } else {
      grounded = false
      supportId = null
      restSeconds = 0f
      sleeping = false
    }

    return body.copy(
      x = x.coerceIn(-0.24f, 1.24f),
      y = y,
      angle = angle,
      vx = vx,
      vy = vy,
      angularVelocity = angularVelocity,
      sleeping = sleeping,
      restSeconds = restSeconds,
      grounded = grounded,
      supportId = supportId,
    )
  }

  private fun finalizeTower(
    state: SkyTowerRuntimeState,
    signals: GameSignalSnapshot,
  ): SkyTowerRuntimeState {
    val wakeFactor = max(0f, (signals.noise - 0.62f) * 0.35f)
    if (wakeFactor <= 0f) return state
    val bodies = state.bodies.toMutableList()
    for (index in max(1, bodies.lastIndex - 2)..bodies.lastIndex) {
      if (index !in bodies.indices) continue
      val body = bodies[index]
      if (!body.sleeping) continue
      val pulse = sin(((state.timeSeconds * 8f) + body.id).toDouble()).toFloat()
      if (pulse > 0.96f) {
        bodies[index] = body.copy(
          sleeping = false,
          restSeconds = 0f,
          vx = body.vx + ((pulse - 0.95f) * wakeFactor),
          angularVelocity = body.angularVelocity + (pulse * wakeFactor),
        )
      }
    }
    return state.copy(bodies = bodies)
  }
}

class InkGardenController : GameController<InkGardenRuntimeState> {
  override val id: GameId = GameId.INK_GARDEN

  override fun initialState(): InkGardenRuntimeState = InkGardenRuntimeState()

  override fun step(
    state: InkGardenRuntimeState,
    dtSeconds: Float,
    signals: GameSignalSnapshot,
    events: List<GameEvent>,
  ): InkGardenRuntimeState {
    if (events.isNotEmpty()) {
      // Ink Garden stays passive.
    }
    val nextTime = state.timeSeconds + dtSeconds
    var spawnTimer = state.spawnTimer + dtSeconds
    var nextSeedIndex = state.nextSeedIndex

    val pigment = FloatArray(INK_CELL_COUNT)
    val wetness = FloatArray(INK_CELL_COUNT)
    val edge = FloatArray(INK_CELL_COUNT)
    state.cells.forEach { cell ->
      val index = inkIndex(cell.column, cell.row)
      pigment[index] = cell.pigment
      wetness[index] = cell.wetness
      edge[index] = cell.edge
    }

    val spawnEverySeconds = (1.10f - (signals.stability * 0.30f) + (signals.fatigue * 0.30f)).coerceIn(0.55f, 1.35f)
    while (spawnTimer >= spawnEverySeconds) {
      seedInkDrop(
        pigment = pigment,
        wetness = wetness,
        edge = edge,
        seedIndex = nextSeedIndex,
        intensity = 0.28f + (signals.stability * 0.14f) + (signals.correctionPulse * 0.08f),
        spread = 1.15f + (signals.drift * 0.55f),
      )
      nextSeedIndex += 1
      spawnTimer -= spawnEverySeconds
    }

    if (signals.correctionPulse > 0.10f) {
      seedFocusedBloom(pigment, wetness, edge, signals.correctionPulse)
    }

    val nextPigment = FloatArray(INK_CELL_COUNT)
    val nextWetness = FloatArray(INK_CELL_COUNT)
    val nextEdge = FloatArray(INK_CELL_COUNT)

    val diffusion = 0.18f + (signals.stability * 0.20f) + (signals.correctionPulse * 0.08f)
    val driftBias = sin((nextTime * 0.9f).toDouble()).toFloat() * (0.05f + (signals.drift * 0.18f))
    val evaporation = 0.02f + (signals.fatigue * 0.03f)
    val absorption = 0.015f + (signals.stability * 0.01f)
    val granulation = clamp01((signals.noise * 0.78f) + ((1f - signals.stability) * 0.12f))

    for (row in 0 until INK_GRID_ROWS) {
      for (column in 0 until INK_GRID_COLUMNS) {
        val index = inkIndex(column, row)
        val cellPigment = pigment[index]
        val cellWetness = wetness[index]
        val neighbors = neighborIndices(column, row)
        val averagePigment = if (neighbors.isEmpty()) cellPigment else neighbors.sumOf { pigment[it].toDouble() }.toFloat() / neighbors.size.toFloat()
        val averageWetness = if (neighbors.isEmpty()) cellWetness else neighbors.sumOf { wetness[it].toDouble() }.toFloat() / neighbors.size.toFloat()
        val driftColumn = when {
          driftBias > 0.02f -> min(column + 1, INK_GRID_COLUMNS - 1)
          driftBias < -0.02f -> max(column - 1, 0)
          else -> column
        }
        val driftIndex = inkIndex(driftColumn, min(row + 1, INK_GRID_ROWS - 1))
        val downwardIndex = inkIndex(column, min(row + 1, INK_GRID_ROWS - 1))
        val gradient = abs(cellPigment - averagePigment) + abs(cellWetness - averageWetness)
        val flowToLower = cellWetness * (0.06f + ((1f - signals.stability) * 0.03f))

        var nextCellWetness = cellWetness +
          ((averageWetness - cellWetness) * diffusion * dtSeconds * 3.8f) -
          (evaporation * dtSeconds) -
          (absorption * dtSeconds * 0.4f)
        nextCellWetness += wetness[driftIndex] * abs(driftBias) * dtSeconds * 0.28f

        var nextCellPigment = cellPigment +
          ((averagePigment - cellPigment) * diffusion * dtSeconds * 3.1f) +
          ((pigment[driftIndex] - cellPigment) * abs(driftBias) * dtSeconds * 1.4f) -
          (absorption * cellPigment * dtSeconds * 0.45f)

        if (downwardIndex != index) {
          nextCellPigment -= flowToLower * dtSeconds
          nextPigment[downwardIndex] += flowToLower * dtSeconds
          nextWetness[downwardIndex] += cellWetness * 0.02f * dtSeconds * 12f
        }

        val noiseFeather = if ((column + row + nextSeedIndex) % 5 == 0) {
          signals.noise * 0.05f
        } else {
          0f
        }
        nextEdge[index] = clamp01(
          approach(edge[index], clamp01((gradient * 0.85f) + noiseFeather + (granulation * 0.18f)), dtSeconds * 1.8f)
        )
        nextWetness[index] += nextCellWetness.coerceAtLeast(0f)
        nextPigment[index] += nextCellPigment.coerceAtLeast(0f)
      }
    }

    val cells = ArrayList<InkCell>(INK_CELL_COUNT)
    var completedCells = 0
    for (row in 0 until INK_GRID_ROWS) {
      for (column in 0 until INK_GRID_COLUMNS) {
        val index = inkIndex(column, row)
        val normalizedPigment = nextPigment[index].coerceIn(0f, 1f)
        val normalizedWetness = nextWetness[index].coerceIn(0f, 1f)
        val normalizedEdge = nextEdge[index].coerceIn(0f, 1f)
        if (normalizedPigment > 0.08f || normalizedWetness > 0.06f) {
          completedCells++
        }
        cells += InkCell(
          column = column,
          row = row,
          pigment = normalizedPigment,
          wetness = normalizedWetness,
          edge = normalizedEdge,
        )
      }
    }

    val splatters = buildList {
      addAll(state.splatters.takeLast(14).map {
        it.copy(alpha = (it.alpha - (dtSeconds * 0.14f)).coerceAtLeast(0f))
      }.filter { it.alpha > 0.02f })
      if (signals.noise > 0.22f) {
        val count = max(1, (signals.noise * 5f).roundToInt())
        repeat(count) { splatIndex ->
          val x = 0.12f + (sequenceFloat(nextSeedIndex + splatIndex, salt = 17) * 0.76f)
          val y = 0.18f + (sequenceFloat(nextSeedIndex + splatIndex, salt = 23) * 0.64f)
          add(
            InkBlotch(
              x = x,
              y = y,
              radius = 0.008f + (sequenceFloat(nextSeedIndex + splatIndex, salt = 31) * 0.020f),
              alpha = 0.08f + (signals.noise * 0.24f),
            )
          )
        }
      }
    }

    val completion = clamp01(
      (completedCells / INK_CELL_COUNT.toFloat()) +
        (signals.correctionPulse * 0.06f) -
        (signals.fatigue * 0.04f)
    )

    return state.copy(
      timeSeconds = nextTime,
      cells = cells,
      splatters = splatters.takeLast(24),
      completion = completion,
      bloom = approach(state.bloom, signals.correctionPulse, dtSeconds * 2.0f),
      granulation = approach(state.granulation, granulation, dtSeconds * 1.7f),
      spawnTimer = spawnTimer,
      nextSeedIndex = nextSeedIndex,
    )
  }

  override fun hud(state: InkGardenRuntimeState, signals: GameSignalSnapshot): GameHudState {
    return GameHudState(
      title = id.displayName(),
      summaryLabel = "Coverage",
      summaryValue = "${(state.completion * 100f).roundToInt()}%",
      inputHint = id.inputHint(),
      stabilityPercent = (signals.stability * 100f).roundToInt(),
      driftPercent = (signals.drift * 100f).roundToInt(),
      noisePercent = (signals.noise * 100f).roundToInt(),
      fatiguePercent = (signals.fatigue * 100f).roundToInt(),
      correctionPercent = (signals.correctionPulse * 100f).roundToInt(),
      poorSignal = signals.poorSignal,
      elapsedSeconds = signals.elapsedSeconds,
      batteryPercent = signals.batteryPercent,
      stateLabel = signals.stateLabel,
    )
  }

  override fun audio(state: InkGardenRuntimeState, signals: GameSignalSnapshot): GameAudioState {
    return GameAudioState(
      ambience = clamp01(0.14f + (state.completion * 0.40f)),
      motion = clamp01((signals.drift * 0.34f) + (state.bloom * 0.12f)),
      glitch = clamp01((signals.noise * 0.72f) + (state.granulation * 0.20f)),
      accent = clamp01((signals.correctionPulse * 0.85f) + (state.bloom * 0.18f)),
      warmth = clamp01(0.30f + (signals.stability * 0.20f) - (signals.fatigue * 0.15f)),
      muted = false,
    )
  }
}

class FireKeeperController : GameController<FireKeeperRuntimeState> {
  override val id: GameId = GameId.FIRE_KEEPER

  override fun initialState(): FireKeeperRuntimeState = FireKeeperRuntimeState()

  override fun step(
    state: FireKeeperRuntimeState,
    dtSeconds: Float,
    signals: GameSignalSnapshot,
    events: List<GameEvent>,
  ): FireKeeperRuntimeState {
    if (events.isNotEmpty()) {
      // Fire Keeper stays passive.
    }
    val nextTime = state.timeSeconds + dtSeconds
    val targetHeight = clamp01(0.28f + (signals.stability * 0.42f) + (signals.correctionPulse * 0.16f) - (signals.fatigue * 0.24f))
    val leanWave = sin((nextTime * (1.8f + (signals.drift * 2.4f))).toDouble()).toFloat() *
      (0.04f + (signals.drift * 0.12f) + (signals.noise * 0.05f))
    return state.copy(
      timeSeconds = nextTime,
      flameHeight = approach(state.flameHeight, targetHeight, dtSeconds * (1.8f + (signals.stability * 2.8f))),
      lean = approach(state.lean, leanWave, dtSeconds * 3.5f),
      turbulence = approach(
        state.turbulence,
        clamp01(0.10f + (signals.drift * 0.42f) + (signals.noise * 0.38f) - (signals.stability * 0.12f)),
        dtSeconds * 2.1f,
      ),
      emberLift = approach(state.emberLift, clamp01((signals.stability * 0.45f) + (signals.correctionPulse * 0.65f)), dtSeconds * 2.2f),
      smokeDensity = approach(state.smokeDensity, clamp01((signals.noise * 0.90f) + (signals.fatigue * 0.18f)), dtSeconds * 2.0f),
      warmth = approach(
        state.warmth,
        clamp01(0.25f + (signals.stability * 0.42f) + (signals.correctionPulse * 0.18f) - (signals.fatigue * 0.22f)),
        dtSeconds * 1.9f,
      ),
    )
  }

  override fun hud(state: FireKeeperRuntimeState, signals: GameSignalSnapshot): GameHudState {
    return GameHudState(
      title = id.displayName(),
      summaryLabel = "Fire height",
      summaryValue = "${(state.flameHeight * 100f).roundToInt()}%",
      inputHint = id.inputHint(),
      stabilityPercent = (signals.stability * 100f).roundToInt(),
      driftPercent = (signals.drift * 100f).roundToInt(),
      noisePercent = (signals.noise * 100f).roundToInt(),
      fatiguePercent = (signals.fatigue * 100f).roundToInt(),
      correctionPercent = (signals.correctionPulse * 100f).roundToInt(),
      poorSignal = signals.poorSignal,
      elapsedSeconds = signals.elapsedSeconds,
      batteryPercent = signals.batteryPercent,
      stateLabel = signals.stateLabel,
    )
  }

  override fun audio(state: FireKeeperRuntimeState, signals: GameSignalSnapshot): GameAudioState {
    return GameAudioState(
      ambience = clamp01(0.30f + (state.flameHeight * 0.40f)),
      motion = clamp01((state.turbulence * 0.90f) + (abs(state.lean) * 0.50f)),
      glitch = clamp01((state.smokeDensity * 0.80f) + (signals.noise * 0.35f)),
      accent = clamp01((signals.correctionPulse * 0.90f) + (state.emberLift * 0.35f)),
      warmth = clamp01(state.warmth + 0.15f),
      muted = false,
    )
  }
}

class ScriptoriumController : GameController<ScriptoriumRuntimeState> {
  override val id: GameId = GameId.SCRIPTORIUM

  override fun initialState(): ScriptoriumRuntimeState = ScriptoriumRuntimeState()

  override fun step(
    state: ScriptoriumRuntimeState,
    dtSeconds: Float,
    signals: GameSignalSnapshot,
    events: List<GameEvent>,
  ): ScriptoriumRuntimeState {
    if (events.isNotEmpty()) {
      // Scriptorium stays passive.
    }
    val nextTime = state.timeSeconds + dtSeconds
    val progressStep = (0.08f + (signals.stability * 0.24f) + (signals.correctionPulse * 0.10f) - (signals.fatigue * 0.07f)).coerceAtLeast(0.03f)
    val progress = clamp01(state.progress + (dtSeconds * progressStep))
    val legibility = approach(
      state.legibility,
      clamp01((signals.stability * 0.85f) - (signals.drift * 0.45f) - (signals.noise * 0.25f) + 0.35f),
      dtSeconds * 1.8f,
    )
    return state.copy(
      timeSeconds = nextTime,
      progress = progress,
      legibility = legibility,
      ornament = approach(
        state.ornament,
        clamp01((signals.stability * 0.55f) + (signals.correctionPulse * 0.65f)),
        dtSeconds * 1.8f,
      ),
      blotches = approach(state.blotches, clamp01((signals.noise * 0.90f) + (signals.drift * 0.12f)), dtSeconds * 2.0f),
      fade = approach(state.fade, clamp01(signals.fatigue * 0.95f), dtSeconds * 1.4f),
      revealedLines = ((progress * 6f).roundToInt()).coerceIn(1, 6),
    )
  }

  override fun hud(state: ScriptoriumRuntimeState, signals: GameSignalSnapshot): GameHudState {
    return GameHudState(
      title = id.displayName(),
      summaryLabel = "Manuscript",
      summaryValue = "${(state.progress * 100f).roundToInt()}%",
      inputHint = id.inputHint(),
      stabilityPercent = (signals.stability * 100f).roundToInt(),
      driftPercent = (signals.drift * 100f).roundToInt(),
      noisePercent = (signals.noise * 100f).roundToInt(),
      fatiguePercent = (signals.fatigue * 100f).roundToInt(),
      correctionPercent = (signals.correctionPulse * 100f).roundToInt(),
      poorSignal = signals.poorSignal,
      elapsedSeconds = signals.elapsedSeconds,
      batteryPercent = signals.batteryPercent,
      stateLabel = signals.stateLabel,
    )
  }

  override fun audio(state: ScriptoriumRuntimeState, signals: GameSignalSnapshot): GameAudioState {
    return GameAudioState(
      ambience = clamp01(0.18f + (state.progress * 0.36f)),
      motion = clamp01((1f - state.legibility) * 0.20f + (state.progress * 0.18f)),
      glitch = clamp01(state.blotches * 0.90f),
      accent = clamp01((signals.correctionPulse * 0.90f) + (state.ornament * 0.30f)),
      warmth = clamp01(0.28f + (state.ornament * 0.40f) - (state.fade * 0.18f)),
      muted = false,
    )
  }
}

object GameRegistry {
  val controllers: List<AnyGameController> = listOf(
    SkyTowerController().erase(),
    InkGardenController().erase(),
    FireKeeperController().erase(),
    ScriptoriumController().erase(),
  )

  private val controllersById = controllers.associateBy { it.id }

  fun controllerFor(id: GameId): AnyGameController = controllersById.getValue(id)
}

fun defaultGameRuntimeState(gameId: GameId = GameId.SKY_TOWER): GameRuntimeState {
  return GameRegistry.controllerFor(gameId).initialState()
}

fun defaultGameHudState(gameId: GameId = GameId.SKY_TOWER): GameHudState {
  val controller = GameRegistry.controllerFor(gameId)
  return controller.hud(controller.initialState(), GameSignalSnapshot()).copy(
    inputHint = gameId.startHint(),
  )
}

fun gameRuntimeSummary(state: GameRuntimeState): String {
  return when (state) {
    is SkyTowerRuntimeState -> "blocks=${state.placements};misses=${state.misses};sway=${String.format(Locale.US, "%.3f", state.swaySeverity)}"
    is InkGardenRuntimeState -> "coverage=${String.format(Locale.US, "%.3f", state.completion)};bloom=${String.format(Locale.US, "%.3f", state.bloom)}"
    is FireKeeperRuntimeState -> "height=${String.format(Locale.US, "%.3f", state.flameHeight)};smoke=${String.format(Locale.US, "%.3f", state.smokeDensity)}"
    is ScriptoriumRuntimeState -> "progress=${String.format(Locale.US, "%.3f", state.progress)};lines=${state.revealedLines}"
  }
}

private const val TOWER_GROUND_Y = 0.88f
private const val TOWER_BLOCK_HEIGHT = 0.055f
private const val TOWER_BLOCK_WIDTH = 0.18f
private const val TOWER_CARRIER_Y = 0.18f
private const val TOWER_MIN_SUPPORT_RATIO = 0.24f

private const val INK_GRID_COLUMNS = 24
private const val INK_GRID_ROWS = 16
private const val INK_CELL_COUNT = INK_GRID_COLUMNS * INK_GRID_ROWS

private data class TowerSupportContact(
  val supportId: Int?,
  val grounded: Boolean,
  val topY: Float,
  val supportCenter: Float,
  val supportRatio: Float,
)

private fun towerFoundation(): TowerBody {
  return TowerBody(
    id = 0,
    x = 0.50f,
    y = TOWER_GROUND_Y - (TOWER_BLOCK_HEIGHT / 2f),
    width = 0.28f,
    sleeping = true,
    grounded = true,
  )
}

private fun towerSwaySeverity(bodies: List<TowerBody>): Float {
  if (bodies.size <= 1) return 0f
  val upperBodies = bodies.drop(1)
  val averageAngle = upperBodies.sumOf { abs(it.angle).toDouble() }.toFloat() / upperBodies.size.toFloat()
  val averageMotion = upperBodies.sumOf { (abs(it.vx) + abs(it.angularVelocity)).toDouble() }.toFloat() / upperBodies.size.toFloat()
  return clamp01((averageAngle * 2.8f) + (averageMotion * 1.6f))
}

private fun bodiesBelow(body: TowerBody, allBodies: List<TowerBody>): List<TowerBody> {
  return allBodies.filter { it.id != body.id && it.y > body.y }
}

private fun findSupportContact(
  body: TowerBody,
  index: Int,
  allBodies: List<TowerBody>,
): TowerSupportContact? {
  val bottom = body.y + (body.height / 2f)
  if (bottom >= TOWER_GROUND_Y) {
    return TowerSupportContact(
      supportId = null,
      grounded = true,
      topY = TOWER_GROUND_Y,
      supportCenter = body.x,
      supportRatio = 1f,
    )
  }

  var best: TowerSupportContact? = null
  for (otherIndex in 0 until index) {
    val support = allBodies[otherIndex]
    if (support.id == body.id) continue
    if (support.y <= body.y) continue
    val topY = support.y - (support.height / 2f)
    val verticalGap = topY - bottom
    if (verticalGap > body.height * 0.75f) continue
    val overlap = overlapWidth(body.x, body.width, support.x, support.width)
    if (overlap <= 0f) continue
    val contact = TowerSupportContact(
      supportId = support.id,
      grounded = false,
      topY = topY,
      supportCenter = overlapCenter(body.x, body.width, support.x, support.width),
      supportRatio = overlap / body.width,
    )
    if (best == null || contact.topY < best.topY) {
      best = contact
    }
  }
  return best
}

private fun overlapWidth(centerA: Float, widthA: Float, centerB: Float, widthB: Float): Float {
  val left = max(centerA - (widthA / 2f), centerB - (widthB / 2f))
  val right = min(centerA + (widthA / 2f), centerB + (widthB / 2f))
  return (right - left).coerceAtLeast(0f)
}

private fun overlapCenter(centerA: Float, widthA: Float, centerB: Float, widthB: Float): Float {
  val left = max(centerA - (widthA / 2f), centerB - (widthB / 2f))
  val right = min(centerA + (widthA / 2f), centerB + (widthB / 2f))
  return (left + right) / 2f
}

private fun inkIndex(column: Int, row: Int): Int = (row * INK_GRID_COLUMNS) + column

private fun initialInkCells(): List<InkCell> {
  return buildList(INK_CELL_COUNT) {
    for (row in 0 until INK_GRID_ROWS) {
      for (column in 0 until INK_GRID_COLUMNS) {
        add(InkCell(column = column, row = row, pigment = 0f, wetness = 0f, edge = 0f))
      }
    }
  }
}

private fun neighborIndices(column: Int, row: Int): List<Int> {
  val indices = ArrayList<Int>(8)
  for (dRow in -1..1) {
    for (dColumn in -1..1) {
      if (dColumn == 0 && dRow == 0) continue
      val nextColumn = column + dColumn
      val nextRow = row + dRow
      if (nextColumn !in 0 until INK_GRID_COLUMNS || nextRow !in 0 until INK_GRID_ROWS) continue
      indices += inkIndex(nextColumn, nextRow)
    }
  }
  return indices
}

private fun seedInkDrop(
  pigment: FloatArray,
  wetness: FloatArray,
  edge: FloatArray,
  seedIndex: Int,
  intensity: Float,
  spread: Float,
) {
  val centerX = 0.30f + (sequenceFloat(seedIndex, salt = 5) * 0.40f)
  val centerY = 0.62f + (sequenceFloat(seedIndex, salt = 11) * 0.20f)
  val radiusCells = 1.4f + (sequenceFloat(seedIndex, salt = 17) * spread)
  for (row in 0 until INK_GRID_ROWS) {
    for (column in 0 until INK_GRID_COLUMNS) {
      val x = (column + 0.5f) / INK_GRID_COLUMNS.toFloat()
      val y = (row + 0.5f) / INK_GRID_ROWS.toFloat()
      val dx = x - centerX
      val dy = y - centerY
      val distance = (abs(dx) * 0.75f) + abs(dy)
      val influence = clamp01(1f - (distance * radiusCells * 1.8f))
      if (influence <= 0f) continue
      val index = inkIndex(column, row)
      pigment[index] = (pigment[index] + (influence * intensity)).coerceAtMost(1f)
      wetness[index] = (wetness[index] + (influence * (0.20f + (intensity * 0.50f)))).coerceAtMost(1f)
      edge[index] = max(edge[index], influence * 0.10f)
    }
  }
}

private fun seedFocusedBloom(
  pigment: FloatArray,
  wetness: FloatArray,
  edge: FloatArray,
  correctionPulse: Float,
) {
  val centerColumn = INK_GRID_COLUMNS / 2
  val centerRow = INK_GRID_ROWS / 2
  for (row in max(0, centerRow - 2)..min(INK_GRID_ROWS - 1, centerRow + 2)) {
    for (column in max(0, centerColumn - 2)..min(INK_GRID_COLUMNS - 1, centerColumn + 2)) {
      val index = inkIndex(column, row)
      pigment[index] = (pigment[index] + (correctionPulse * 0.16f)).coerceAtMost(1f)
      wetness[index] = (wetness[index] + (correctionPulse * 0.12f)).coerceAtMost(1f)
      edge[index] = max(edge[index], correctionPulse * 0.14f)
    }
  }
}
