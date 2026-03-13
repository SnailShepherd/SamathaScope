package com.mordin.samathascope

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import java.util.Locale

enum class GameId {
  SKY_TOWER,
  INK_GARDEN,
  FIRE_KEEPER,
  SCRIPTORIUM,
}

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
    GameId.SKY_TOWER -> "Tap to place blocks while your signals steady the tower."
    GameId.INK_GARDEN -> "Passive growth where calm signals shape coherent brushwork."
    GameId.FIRE_KEEPER -> "Passive campfire that steadies, wanders, or smolders with your state."
    GameId.SCRIPTORIUM -> "A self-writing manuscript shaped by clarity, drift, and noise."
  }
}

fun GameId.inputHint(): String {
  return when (this) {
    GameId.SKY_TOWER -> "Tap to drop the moving block."
    else -> "Passive scene: breathe, settle, and watch the scene respond."
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
  val inputHint: String = GameId.SKY_TOWER.inputHint(),
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
  val blocks: List<TowerBlock> = emptyList(),
  val fallingX: Float = 0.20f,
  val fallingY: Float = 0.10f,
  val fallingVelocityY: Float = 0f,
  val fallingDirection: Float = 1f,
  val towerSway: Float = 0f,
  val tremor: Float = 0f,
  val graceSeconds: Float = 0f,
  val placements: Int = 0,
  val lastError: Float = 0f,
) : GameRuntimeState

data class InkGardenSeed(
  val x: Float,
  val y: Float,
  val age: Float,
  val direction: Float,
  val chaos: Float,
  val bloom: Float,
)

data class InkGardenRuntimeState(
  override val timeSeconds: Float = 0f,
  val seeds: List<InkGardenSeed> = listOf(
    InkGardenSeed(
      x = 0.50f,
      y = 0.78f,
      age = 0.12f,
      direction = -1.35f,
      chaos = 0f,
      bloom = 0.12f,
    )
  ),
  val spawnTimer: Float = 0f,
  val completion: Float = 0.08f,
  val splatter: Float = 0f,
  val repairGlow: Float = 0f,
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
    val nextTime = state.timeSeconds + dtSeconds
    val driftSpeed = 0.22f + (signals.drift * 0.18f) + ((1f - signals.stability) * 0.10f)
    val graceSeconds = max(state.graceSeconds - dtSeconds, signals.correctionPulse * 0.80f)
    var tremor = damp(state.tremor, amountPerSecond = 2.8f + (signals.stability * 2.4f), dtSeconds = dtSeconds)
    var towerSway = damp(state.towerSway, amountPerSecond = 1.8f + (signals.stability * 3.2f) + graceSeconds, dtSeconds = dtSeconds)
    towerSway += sin(nextTime * 4.3f) * (0.02f + (signals.drift * 0.08f))
    towerSway += sin(nextTime * 18f) * tremor * 0.03f
    tremor = clamp01(tremor + (signals.noise * 0.03f))

    val tapped = events.any { it is GameEvent.Tap }
    var fallingVelocityY = state.fallingVelocityY
    var fallingX = state.fallingX
    var fallingY = state.fallingY
    var fallingDirection = state.fallingDirection
    var blocks = state.blocks
    var placements = state.placements
    var lastError = state.lastError

    if (tapped && fallingVelocityY == 0f) {
      fallingVelocityY = 0.55f + (signals.drift * 0.20f)
    }

    if (fallingVelocityY == 0f) {
      fallingX += fallingDirection * driftSpeed * dtSeconds
      if (fallingX <= 0.16f) {
        fallingX = 0.16f
        fallingDirection = 1f
      } else if (fallingX >= 0.84f) {
        fallingX = 0.84f
        fallingDirection = -1f
      }
    } else {
      fallingY += fallingVelocityY * dtSeconds
      fallingVelocityY += (0.95f + (signals.drift * 0.20f)) * dtSeconds
      val landingY = (0.78f - (blocks.size * 0.056f)).coerceAtLeast(0.18f)
      if (fallingY >= landingY) {
        val targetX = blocks.lastOrNull()?.x ?: 0.50f
        val rawError = fallingX - targetX + (towerSway * 0.10f)
        val precisionWindow = 0.03f + (signals.precision * 0.10f) + (signals.correctionPulse * 0.05f)
        val correctedError = if (abs(rawError) <= precisionWindow * 1.4f) {
          rawError * (0.55f - (signals.correctionPulse * 0.25f)).coerceAtLeast(0.15f)
        } else {
          rawError
        }
        val placedX = (targetX + correctedError).coerceIn(0.16f, 0.84f)
        val width = (0.17f - (blocks.size * 0.005f)).coerceAtLeast(0.09f)
        blocks = (blocks + TowerBlock(x = placedX, width = width)).takeLast(18)
        placements += 1
        lastError = abs(correctedError)
        towerSway += correctedError * (0.9f - (signals.stability * 0.45f))
        tremor = clamp01(max(tremor, (signals.noise * 0.60f) + (abs(rawError) * 2.2f)))
        fallingX = if (placements % 2 == 0) 0.20f else 0.80f
        fallingY = 0.10f
        fallingVelocityY = 0f
        fallingDirection = if (placements % 2 == 0) 1f else -1f
      }
    }

    return state.copy(
      timeSeconds = nextTime,
      blocks = blocks,
      fallingX = fallingX,
      fallingY = fallingY,
      fallingVelocityY = fallingVelocityY,
      fallingDirection = fallingDirection,
      towerSway = towerSway.coerceIn(-0.22f, 0.22f),
      tremor = tremor,
      graceSeconds = graceSeconds,
      placements = placements,
      lastError = lastError,
    )
  }

  override fun hud(state: SkyTowerRuntimeState, signals: GameSignalSnapshot): GameHudState {
    return GameHudState(
      title = id.displayName(),
      summaryLabel = "Tower height",
      summaryValue = "${state.blocks.size} blocks",
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
      ambience = clamp01(0.20f + (state.blocks.size * 0.04f) + (signals.stability * 0.25f)),
      motion = clamp01((signals.drift * 0.70f) + (state.lastError * 2.0f)),
      glitch = clamp01(signals.noise + (state.tremor * 0.50f)),
      accent = clamp01(signals.correctionPulse + (1f - state.lastError * 3f).coerceIn(0f, 0.35f)),
      warmth = clamp01(0.25f + (signals.stability * 0.35f) - (signals.fatigue * 0.15f)),
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
    val nextTime = state.timeSeconds + dtSeconds
    val growthRate = (0.10f + (signals.stability * 0.30f) + (signals.correctionPulse * 0.14f) - (signals.fatigue * 0.08f)).coerceAtLeast(0.05f)
    var spawnTimer = state.spawnTimer + dtSeconds * (0.45f + (signals.stability * 0.25f) - (signals.fatigue * 0.12f)).coerceAtLeast(0.15f)
    var seeds = state.seeds
    if (spawnTimer >= 2.40f && seeds.size < 7) {
      val index = seeds.size
      val x = 0.16f + (sequenceFloat(index, salt = 4) * 0.68f)
      val direction = -1.9f + (sequenceFloat(index, salt = 7) * 0.9f)
      seeds = seeds + InkGardenSeed(
        x = x,
        y = 0.82f,
        age = 0.04f,
        direction = direction,
        chaos = signals.drift,
        bloom = signals.correctionPulse * 0.35f,
      )
      spawnTimer -= 2.40f
    }
    val updatedSeeds = seeds.map { seed ->
      seed.copy(
        age = clamp01(seed.age + (dtSeconds * growthRate)),
        chaos = approach(seed.chaos, (signals.drift * 0.90f) + (signals.noise * 0.45f), dtSeconds * 1.8f),
        bloom = approach(seed.bloom, (signals.stability * 0.35f) + (signals.correctionPulse * 0.70f), dtSeconds * 1.6f),
      )
    }
    val completion = clamp01((updatedSeeds.map { it.age }.average().toFloat() * 0.95f) + (signals.stability * 0.10f) - (signals.drift * 0.06f))
    return state.copy(
      timeSeconds = nextTime,
      seeds = updatedSeeds,
      spawnTimer = spawnTimer,
      completion = completion,
      splatter = approach(state.splatter, signals.noise, dtSeconds * 1.8f),
      repairGlow = approach(state.repairGlow, signals.correctionPulse, dtSeconds * 2.2f),
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
      ambience = clamp01(0.18f + (state.completion * 0.45f)),
      motion = clamp01((signals.drift * 0.45f) + (state.seeds.size * 0.05f)),
      glitch = clamp01(signals.noise * 0.85f),
      accent = clamp01((signals.correctionPulse * 0.85f) + (state.repairGlow * 0.40f)),
      warmth = clamp01(0.35f + (signals.stability * 0.25f) - (signals.fatigue * 0.18f)),
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
  return controller.hud(controller.initialState(), GameSignalSnapshot())
}

fun gameRuntimeSummary(state: GameRuntimeState): String {
  return when (state) {
    is SkyTowerRuntimeState -> "blocks=${state.blocks.size};lastError=${String.format(Locale.US, "%.3f", state.lastError)}"
    is InkGardenRuntimeState -> "seeds=${state.seeds.size};completion=${String.format(Locale.US, "%.3f", state.completion)}"
    is FireKeeperRuntimeState -> "height=${String.format(Locale.US, "%.3f", state.flameHeight)};smoke=${String.format(Locale.US, "%.3f", state.smokeDensity)}"
    is ScriptoriumRuntimeState -> "progress=${String.format(Locale.US, "%.3f", state.progress)};lines=${state.revealedLines}"
  }
}
