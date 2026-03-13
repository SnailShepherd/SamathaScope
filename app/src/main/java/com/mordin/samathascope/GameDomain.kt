package com.mordin.samathascope

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
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
    GameId.SKY_TOWER -> "Falling blocks, timed taps, and EEG-driven stability."
    GameId.INK_GARDEN -> "A passive ink ecosystem with droplets, trails, and splatters."
    GameId.FIRE_KEEPER -> "Passive campfire behaviour shaped by steadiness, drift, and fatigue."
    GameId.SCRIPTORIUM -> "A self-writing manuscript that sharpens or degrades with your state."
  }
}

fun GameId.inputHint(): String {
  return when (this) {
    GameId.SKY_TOWER -> "Tap to fire a short placement assist while the block is falling."
    else -> "Passive scene: breathe, settle, and let the scene respond."
  }
}

fun GameId.startHint(): String = "Press Start to begin ${displayName()}."

fun GameId.guide(): GameGuide {
  return when (this) {
    GameId.SKY_TOWER -> GameGuide(
      title = "Sky Tower guide",
      lines = listOf(
        "Tap Start, then tap to fire a brief placement assist while each block falls.",
        "Settledness damps sway and helps the stack absorb rough landings.",
        "Mind Wandering increases drift, wind, and misalignment pressure.",
        "Artefact Score becomes tremors and impact shake.",
        "Effortful Focus gives a short rescue correction when placement gets messy.",
      ),
    )

    GameId.INK_GARDEN -> GameGuide(
      title = "Ink Garden guide",
      lines = listOf(
        "This one is passive after Start: droplets seep, split, and leave ink trails on their own.",
        "Settledness makes the flow cohesive, smooth, and branch into cleaner forms.",
        "Mind Wandering makes tendrils wander sideways and fork more chaotically.",
        "Artefact Score throws splatters, jagged breaks, and noisy ink bursts.",
        "Effortful Focus briefly pulls the flow back together into luminous repair blooms.",
      ),
    )

    GameId.FIRE_KEEPER -> GameGuide(
      title = "Fire Keeper guide",
      lines = listOf(
        "This scene stays passive once started.",
        "Settledness makes the flame upright, cohesive, and warm.",
        "Mind Wandering makes the flame lean, flicker, and wander.",
        "Artefact Score drives sparks, gusts, and smoke bursts.",
        "Effortful Focus gives brief rekindling lifts through the ember bed.",
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
  val summaryValue: String = "0 blocks",
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

data class TowerBlock(
  val x: Float,
  val width: Float,
)

data class SkyTowerRuntimeState(
  override val timeSeconds: Float = 0f,
  val blocks: List<TowerBlock> = listOf(TowerBlock(x = 0.50f, width = 0.22f)),
  val activeX: Float = 0.22f,
  val activeY: Float = -0.12f,
  val activeVX: Float = 0.11f,
  val activeVY: Float = 0.12f,
  val activeWidth: Float = 0.18f,
  val assistSeconds: Float = 0f,
  val towerSway: Float = 0f,
  val tremor: Float = 0f,
  val placements: Int = 0,
  val misses: Int = 0,
  val lastError: Float = 0f,
  val spawnIndex: Int = 0,
) : GameRuntimeState

data class InkDroplet(
  val id: Int,
  val x: Float,
  val y: Float,
  val vx: Float,
  val vy: Float,
  val ink: Float,
  val age: Float,
  val branchBias: Float,
)

data class InkSegment(
  val startX: Float,
  val startY: Float,
  val endX: Float,
  val endY: Float,
  val width: Float,
  val alpha: Float,
)

data class InkBlotch(
  val x: Float,
  val y: Float,
  val radius: Float,
  val alpha: Float,
)

data class InkGardenRuntimeState(
  override val timeSeconds: Float = 0f,
  val droplets: List<InkDroplet> = listOf(
    InkDroplet(
      id = 0,
      x = 0.50f,
      y = 0.80f,
      vx = 0f,
      vy = -0.05f,
      ink = 0.95f,
      age = 0f,
      branchBias = 0f,
    )
  ),
  val segments: List<InkSegment> = emptyList(),
  val blotches: List<InkBlotch> = listOf(
    InkBlotch(x = 0.50f, y = 0.82f, radius = 0.020f, alpha = 0.18f)
  ),
  val spawnTimer: Float = 0f,
  val completion: Float = 0.06f,
  val splatter: Float = 0f,
  val repairGlow: Float = 0f,
  val nextDropletId: Int = 1,
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

  override fun initialState(): SkyTowerRuntimeState {
    return spawnedTowerState(
      state = SkyTowerRuntimeState(),
      width = 0.18f,
      spawnIndex = 0,
    )
  }

  override fun step(
    state: SkyTowerRuntimeState,
    dtSeconds: Float,
    signals: GameSignalSnapshot,
    events: List<GameEvent>,
  ): SkyTowerRuntimeState {
    val nextTime = state.timeSeconds + dtSeconds
    val tapped = events.any { it is GameEvent.Tap }
    val supportBlock = state.blocks.last()
    val supportCenter = supportBlock.x + (state.towerSway * 0.08f)
    val assistSeconds = when {
      tapped -> max(state.assistSeconds, 0.45f + (signals.precision * 0.35f))
      else -> (state.assistSeconds - dtSeconds).coerceAtLeast(0f)
    }
    val assistStrength = clamp01((assistSeconds / 0.75f) + (signals.correctionPulse * 0.70f))
    var towerSway = damp(
      current = state.towerSway,
      amountPerSecond = 2.4f + (signals.stability * 3.0f) + (assistStrength * 1.2f),
      dtSeconds = dtSeconds,
    )
    towerSway += sin(nextTime * 3.6f) * (0.002f + (signals.drift * 0.007f) + (state.tremor * 0.003f))
    var tremor = damp(
      current = state.tremor,
      amountPerSecond = 3.0f + (signals.stability * 3.5f),
      dtSeconds = dtSeconds,
    )
    tremor = clamp01(tremor + (signals.noise * 0.020f))

    val driftWave = sin((nextTime * 1.5f) + (state.spawnIndex * 0.8f)) * (0.12f + (signals.drift * 0.18f))
    val guidance = (0.8f + (signals.precision * 1.1f) + (signals.stability * 0.6f) + (assistStrength * 1.8f))
    var activeVX = state.activeVX + ((driftWave - state.activeVX) * dtSeconds * 1.8f)
    var activeVY = state.activeVY + ((0.64f + (signals.drift * 0.18f) + ((1f - signals.stability) * 0.14f)) * dtSeconds)
    var activeX = state.activeX
    var activeY = state.activeY

    if (assistStrength > 0.01f) {
      activeVX += (supportCenter - activeX) * guidance * dtSeconds
      activeVY *= (1f - (0.30f * assistStrength).coerceIn(0f, 0.22f))
    }

    activeX += activeVX * dtSeconds
    activeY += activeVY * dtSeconds

    if (activeX <= 0.10f) {
      activeX = 0.10f
      activeVX = abs(activeVX) * 0.65f
    } else if (activeX >= 0.90f) {
      activeX = 0.90f
      activeVX = -abs(activeVX) * 0.65f
    }

    val targetY = towerLandingCenterY(state.blocks.size)
    val placementWindow = 0.010f + (signals.precision * 0.030f) + (assistStrength * 0.030f)
    val perfectWindow = 0.010f + (signals.precision * 0.020f)
    val withinCatchBand = activeY in targetY..(targetY + (TOWER_BLOCK_HEIGHT * 0.90f))
    val eligibleToPlace = withinCatchBand && (assistStrength > 0.10f || abs(activeX - supportCenter) <= perfectWindow)

    if (eligibleToPlace) {
      val snappedX = approach(
        current = activeX,
        target = supportCenter,
        factor = (0.20f + (assistStrength * 0.50f) + (signals.precision * 0.18f)).coerceIn(0f, 0.90f),
      )
      val supportLeft = supportCenter - (supportBlock.width / 2f)
      val supportRight = supportCenter + (supportBlock.width / 2f)
      var activeLeft = snappedX - (state.activeWidth / 2f)
      var activeRight = snappedX + (state.activeWidth / 2f)
      val gap = max(supportLeft - activeRight, activeLeft - supportRight).coerceAtLeast(0f)

      if (gap <= placementWindow) {
        val snapFactor = (1f - (gap / placementWindow.coerceAtLeast(0.0001f))).coerceIn(0f, 1f)
        val rescuedX = approach(snappedX, supportCenter, 0.30f + (snapFactor * 0.45f))
        activeLeft = rescuedX - (state.activeWidth / 2f)
        activeRight = rescuedX + (state.activeWidth / 2f)
      }

      val overlapLeft = max(activeLeft, supportLeft)
      val overlapRight = min(activeRight, supportRight)
      val overlapWidth = overlapRight - overlapLeft
      if (overlapWidth >= 0.045f) {
        val placedX = (overlapLeft + overlapRight) / 2f
        val error = abs(placedX - supportCenter)
        val placedBlock = TowerBlock(
          x = placedX.coerceIn(0.10f, 0.90f),
          width = overlapWidth.coerceIn(0.07f, state.activeWidth),
        )
        val nextBlocks = state.blocks + placedBlock
        val nextTremor = clamp01(
          max(tremor, (signals.noise * 0.45f) + (error * 2.8f) + (abs(state.activeVX) * 0.25f))
        )
        val nextSway = towerSway + ((placedX - supportCenter) * (0.80f - (signals.stability * 0.32f)))
        return spawnedTowerState(
          state = state.copy(
            timeSeconds = nextTime,
            blocks = nextBlocks,
            assistSeconds = 0f,
            towerSway = nextSway.coerceIn(-0.22f, 0.22f),
            tremor = nextTremor,
            placements = state.placements + 1,
            lastError = error,
            spawnIndex = state.spawnIndex + 1,
          ),
          width = placedBlock.width,
          spawnIndex = state.spawnIndex + 1,
        )
      }
    }

    if (activeY >= TOWER_GROUND_Y + TOWER_BLOCK_HEIGHT) {
      val nextTremor = clamp01(max(tremor, 0.22f + (signals.noise * 0.40f)))
      return spawnedTowerState(
        state = state.copy(
          timeSeconds = nextTime,
          assistSeconds = 0f,
          towerSway = (towerSway + (activeVX * 0.03f)).coerceIn(-0.22f, 0.22f),
          tremor = nextTremor,
          misses = state.misses + 1,
          lastError = 0.18f,
          spawnIndex = state.spawnIndex + 1,
        ),
        width = supportBlock.width.coerceIn(0.08f, 0.18f),
        spawnIndex = state.spawnIndex + 1,
      )
    }

    return state.copy(
      timeSeconds = nextTime,
      activeX = activeX,
      activeY = activeY,
      activeVX = activeVX,
      activeVY = activeVY,
      assistSeconds = assistSeconds,
      towerSway = towerSway.coerceIn(-0.22f, 0.22f),
      tremor = tremor,
    )
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
    return GameAudioState(
      ambience = clamp01(0.18f + (state.placements * 0.04f) + (signals.stability * 0.24f)),
      motion = clamp01((signals.drift * 0.55f) + abs(state.activeVX) + (state.lastError * 2.2f)),
      glitch = clamp01(signals.noise + (state.tremor * 0.55f)),
      accent = clamp01(signals.correctionPulse + (state.assistSeconds * 0.70f)),
      warmth = clamp01(0.24f + (signals.stability * 0.34f) - (signals.fatigue * 0.12f)),
      muted = false,
    )
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
      // Ink Garden stays passive in v1.
    }
    val nextTime = state.timeSeconds + dtSeconds
    val growthRate = (0.45f + (signals.stability * 0.35f) + (signals.correctionPulse * 0.20f) - (signals.fatigue * 0.22f)).coerceAtLeast(0.12f)
    var spawnTimer = state.spawnTimer + (dtSeconds * growthRate)
    var nextDropletId = state.nextDropletId
    val droplets = state.droplets.toMutableList()
    val segments = state.segments.toMutableList()
    val blotches = state.blotches.toMutableList()

    while (spawnTimer >= 1f && droplets.size < 18) {
      droplets += InkDroplet(
        id = nextDropletId,
        x = 0.44f + (sequenceFloat(nextDropletId, salt = 5) * 0.12f),
        y = 0.80f,
        vx = (-0.05f + (sequenceFloat(nextDropletId, salt = 9) * 0.10f)),
        vy = -0.08f - (signals.stability * 0.03f),
        ink = 0.82f + (signals.correctionPulse * 0.10f),
        age = 0f,
        branchBias = (sequenceFloat(nextDropletId, salt = 12) - 0.5f) * 0.10f,
      )
      nextDropletId += 1
      spawnTimer -= 1f
    }

    val updatedDroplets = mutableListOf<InkDroplet>()
    droplets.forEach { droplet ->
      val oldX = droplet.x
      val oldY = droplet.y
      val cohesion = 0.82f + (signals.stability * 0.12f) - (signals.noise * 0.08f)
      val upwardPull = 0.20f + (signals.stability * 0.22f) + (signals.correctionPulse * 0.10f) - (signals.fatigue * 0.12f)
      val wander = sin((nextTime * (1.2f + (droplet.id * 0.03f))) + droplet.id) * (0.05f + (signals.drift * 0.16f) + (signals.noise * 0.08f))
      val nextVX = (droplet.vx * cohesion) + ((droplet.branchBias + wander) * dtSeconds * 2.2f)
      val nextVY = (droplet.vy * (0.88f + (signals.stability * 0.07f))) - (upwardPull * dtSeconds)
      val nextX = (droplet.x + (nextVX * dtSeconds)).coerceIn(0.08f, 0.92f)
      val nextY = droplet.y + (nextVY * dtSeconds)
      val nextInk = (droplet.ink - (dtSeconds * (0.12f + (signals.noise * 0.08f) + (signals.fatigue * 0.06f)))).coerceAtLeast(0f)
      val nextAge = droplet.age + dtSeconds

      segments += InkSegment(
        startX = oldX,
        startY = oldY,
        endX = nextX,
        endY = nextY,
        width = (0.006f + (nextInk * 0.012f) + (signals.stability * 0.004f) - (signals.noise * 0.002f)).coerceIn(0.004f, 0.020f),
        alpha = (0.12f + (nextInk * 0.42f) + (signals.correctionPulse * 0.10f)).coerceIn(0.10f, 0.72f),
      )

      val shouldBranch = droplet.age < 0.32f && nextAge >= 0.32f && signals.drift > 0.20f && updatedDroplets.size < 18
      if (shouldBranch) {
        val branchDirection = if ((droplet.id + nextDropletId) % 2 == 0) -1f else 1f
        val branchBias = branchDirection * (0.10f + (signals.drift * 0.16f) + (signals.noise * 0.04f))
        updatedDroplets += InkDroplet(
          id = nextDropletId,
          x = nextX,
          y = nextY,
          vx = nextVX + (branchBias * 0.22f),
          vy = nextVY * 0.85f,
          ink = nextInk * 0.72f,
          age = 0f,
          branchBias = branchBias,
        )
        nextDropletId += 1
      }

      val shouldSplatter = droplet.age < 0.16f && nextAge >= 0.16f && signals.noise > 0.24f
      if (shouldSplatter) {
        blotches += InkBlotch(
          x = nextX,
          y = nextY,
          radius = (0.006f + (signals.noise * 0.016f)).coerceAtMost(0.026f),
          alpha = (0.10f + (signals.noise * 0.22f)).coerceAtMost(0.45f),
        )
      }

      val keepsFlowing = nextInk > 0.08f && nextY > 0.10f && nextAge < 4.8f
      if (keepsFlowing) {
        updatedDroplets += droplet.copy(
          x = nextX,
          y = nextY,
          vx = nextVX,
          vy = nextVY,
          ink = nextInk,
          age = nextAge,
        )
      } else {
        blotches += InkBlotch(
          x = nextX.coerceIn(0.08f, 0.92f),
          y = nextY.coerceIn(0.10f, 0.92f),
          radius = (0.010f + (nextInk * 0.020f)).coerceIn(0.008f, 0.028f),
          alpha = (0.10f + (nextInk * 0.18f)).coerceIn(0.08f, 0.30f),
        )
      }
    }

    if (signals.correctionPulse > 0.35f && state.repairGlow < signals.correctionPulse) {
      blotches += InkBlotch(
        x = 0.50f,
        y = 0.58f,
        radius = 0.022f + (signals.correctionPulse * 0.020f),
        alpha = 0.16f + (signals.correctionPulse * 0.10f),
      )
    }

    val cappedSegments = segments.takeLast(280)
    val cappedBlotches = blotches.takeLast(42)
    val furthestY = min(
      updatedDroplets.minOfOrNull { it.y } ?: 0.82f,
      cappedSegments.minOfOrNull { min(it.startY, it.endY) } ?: 0.82f,
    )
    val verticalReach = ((0.82f - furthestY) / 0.72f).coerceIn(0f, 1f)
    val completion = clamp01(
      (cappedSegments.size / 240f) +
        (verticalReach * 0.55f) +
        (updatedDroplets.size * 0.01f) +
        (signals.correctionPulse * 0.05f) -
        (signals.fatigue * 0.05f)
    )

    return state.copy(
      timeSeconds = nextTime,
      droplets = updatedDroplets.takeLast(18),
      segments = cappedSegments,
      blotches = cappedBlotches,
      spawnTimer = spawnTimer,
      completion = completion,
      splatter = approach(state.splatter, signals.noise, dtSeconds * 1.8f),
      repairGlow = approach(state.repairGlow, signals.correctionPulse, dtSeconds * 2.1f),
      nextDropletId = nextDropletId,
    )
  }

  override fun hud(state: InkGardenRuntimeState, signals: GameSignalSnapshot): GameHudState {
    return GameHudState(
      title = id.displayName(),
      summaryLabel = "Growth",
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
      ambience = clamp01(0.16f + (state.completion * 0.44f)),
      motion = clamp01((signals.drift * 0.40f) + (state.droplets.size * 0.04f)),
      glitch = clamp01((signals.noise * 0.78f) + (state.splatter * 0.15f)),
      accent = clamp01((signals.correctionPulse * 0.82f) + (state.repairGlow * 0.34f)),
      warmth = clamp01(0.32f + (signals.stability * 0.24f) - (signals.fatigue * 0.16f)),
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
      // Fire Keeper stays passive in v1.
    }
    val nextTime = state.timeSeconds + dtSeconds
    val targetHeight = clamp01(0.28f + (signals.stability * 0.42f) + (signals.correctionPulse * 0.16f) - (signals.fatigue * 0.24f))
    val leanWave = sin(nextTime * (1.8f + (signals.drift * 2.4f))) * (0.04f + (signals.drift * 0.12f) + (signals.noise * 0.05f))
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
      // Scriptorium stays passive in v1.
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
    is SkyTowerRuntimeState -> "blocks=${state.placements};misses=${state.misses};lastError=${String.format(Locale.US, "%.3f", state.lastError)}"
    is InkGardenRuntimeState -> "droplets=${state.droplets.size};segments=${state.segments.size};completion=${String.format(Locale.US, "%.3f", state.completion)}"
    is FireKeeperRuntimeState -> "height=${String.format(Locale.US, "%.3f", state.flameHeight)};smoke=${String.format(Locale.US, "%.3f", state.smokeDensity)}"
    is ScriptoriumRuntimeState -> "progress=${String.format(Locale.US, "%.3f", state.progress)};lines=${state.revealedLines}"
  }
}

private const val TOWER_GROUND_Y = 0.86f
private const val TOWER_BLOCK_HEIGHT = 0.055f

private fun towerLandingCenterY(blockCount: Int): Float {
  return TOWER_GROUND_Y - (TOWER_BLOCK_HEIGHT * (blockCount + 0.5f))
}

private fun spawnedTowerState(
  state: SkyTowerRuntimeState,
  width: Float,
  spawnIndex: Int,
): SkyTowerRuntimeState {
  val spawnFromLeft = spawnIndex % 2 == 0
  return state.copy(
    activeX = if (spawnFromLeft) 0.22f else 0.78f,
    activeY = -0.12f,
    activeVX = if (spawnFromLeft) 0.11f else -0.11f,
    activeVY = 0.12f,
    activeWidth = width.coerceIn(0.08f, 0.18f),
  )
}
