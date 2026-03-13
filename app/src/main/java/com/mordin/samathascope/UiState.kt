package com.mordin.samathascope

enum class PlotType {
  RAW,
  MEDITATION_PROXY,
  SETTLEDNESS,
  CONTROL,
  ALERTNESS,
  DROWSY_SCORE,
  ARTEFACT_SCORE,
  QUALITY_CONFIDENCE,
  EFFORTFUL_FOCUS_SCORE,
  MIND_WANDERING_SCORE,
  ESENSE_MEDITATION,
  ESENSE_ATTENTION,
}

enum class AppTab {
  DASHBOARD,
  SETTINGS,
  GAME,
  LEARN,
}

enum class CalibrationPhase {
  EYES_OPEN,
  EYES_CLOSED,
  BASELINE_COMPLETE,
}

enum class ArtefactPrompt {
  LOOK_LEFT_RIGHT,
  LOOK_UP_DOWN,
  JAW_CLENCH,
  FROWN,
  RELAX,
}

data class PlotSettings(
  val windowSeconds: Int,
  val yMin: Float,
  val yMax: Float,
  val isUserLocked: Boolean = false,
)

data class BondedDevice(
  val mac: String,
  val display: String,
)

data class ArtefactCalibrationUiState(
  val available: Boolean = false,
  val dismissed: Boolean = false,
  val running: Boolean = false,
  val completed: Boolean = false,
  val skipped: Boolean = false,
  val prompt: ArtefactPrompt? = null,
  val promptLabel: String = "",
  val remainingSec: Int = 0,
  val completedPrompts: Int = 0,
  val totalPrompts: Int = ArtefactPrompt.entries.size,
)
data class UiState(
  val selectedTab: AppTab = AppTab.DASHBOARD,

  val btPermissionGranted: Boolean = false,
  val bondedDevices: List<BondedDevice> = emptyList(),
  val selectedDeviceMac: String? = null,
  val connected: Boolean = false,

  val poorSignal: Int = 255,
  val attention: Int = 0,
  val meditation: Int = 0,
  val samplesPerSecond: Float = 0f,
  val streamStallMs: Long = 0,
  val batteryPercent: Int? = null,

  val rawPreview: List<Int> = emptyList(),

  val visibleMetrics: Set<PlotType> = defaultVisibleMetrics(),
  val selectedMetricInfo: PlotType = PlotType.MEDITATION_PROXY,
  val metricPlotSeries: Map<PlotType, List<Float>> = emptyMap(),
  val plotSettings: Map<PlotType, PlotSettings> = defaultPlotSettings(),
  val rawPlotOffsetSeconds: Int = 0,
  val metricPlotOffsetSeconds: Int = 0,

  val sessionRunning: Boolean = false,
  val sessionPaused: Boolean = false,
  val calibrating: Boolean = false,
  val calibrationPhase: CalibrationPhase? = null,
  val calibrationInstruction: String = "",
  val calibrationRemainingSec: Int = 0,
  val artefactCalibrationState: ArtefactCalibrationUiState = ArtefactCalibrationUiState(),
  val sessionElapsedSec: Int = 0,

  val meditationProxy: Float = 0f,
  val settledness: Float = 0f,
  val control: Float = 0f,
  val alertness: Float = 0f,
  val drowsyScore: Float = 0f,
  val artefactScore: Float = 0f,
  val qualityConfidence: Float = 0f,
  val mindWanderingScore: Float = 0f,
  val effortfulFocusScore: Float = 0f,
  val displayedStateLabel: StateLabel = StateLabel.UNCERTAIN,
  val drowsyTarContribution: Float = 0f,
  val drowsyTbrContribution: Float = 0f,
  val drowsyEntropyContribution: Float = 0f,
  val drowsyAbrContribution: Float = 0f,

  val avgMeditationProxy: Float = 0f,
  val timeMeditationProxyOver80Seconds: Int = 0,

  val artefactContact: Float = 0f,
  val artefactLine: Float = 0f,
  val artefactEmg: Float = 0f,
  val artefactBlink: Float = 0f,
  val artefactClip: Float = 0f,
  val artefactStall: Float = 0f,
  val artefactBlinkNormalizationHz: Float = 1f,
  val artefactEmgNormalizationHfRatio: Float = 0.35f,

  val feedbackMetric: PlotType = PlotType.MEDITATION_PROXY,

  val audioEnabled: Boolean = true,
  val invertReward: Boolean = false,
  val gamma: Float = 1.6f,
  val gMinDb: Int = -30,
  val gMaxDb: Int = -3,
  val notch50Enabled: Boolean = false,

  val audioRunning: Boolean = false,
  val audioMuted: Boolean = true,
  val audioBaseDb: Float = -120f,

  val recordingEnabled: Boolean = false,
  val lastRecordingPath: String? = null,

  val selectedGameId: GameId = GameId.SKY_TOWER,
  val gameRunning: Boolean = false,
  val gameSignals: GameSignalSnapshot = GameSignalSnapshot(),
  val gameRuntimeState: GameRuntimeState = defaultGameRuntimeState(),
  val gameAudioState: GameAudioState = GameAudioState(muted = true),
  val gameHudState: GameHudState = defaultGameHudState(),
)

fun defaultVisibleMetrics(): Set<PlotType> = linkedSetOf(
  PlotType.MEDITATION_PROXY,
  PlotType.SETTLEDNESS,
  PlotType.ALERTNESS,
  PlotType.ARTEFACT_SCORE,
)

fun defaultPlotSettings(): Map<PlotType, PlotSettings> {
  val defaultRaw = PlotSettings(
    windowSeconds = 5,
    yMin = -1200f,
    yMax = 1200f,
    isUserLocked = false,
  )
  val defaultMetric = PlotSettings(
    windowSeconds = 300,
    yMin = 0f,
    yMax = 100f,
    isUserLocked = false,
  )
  return mapOf(
    PlotType.RAW to defaultRaw,
    PlotType.MEDITATION_PROXY to defaultMetric,
    PlotType.SETTLEDNESS to defaultMetric,
    PlotType.CONTROL to defaultMetric,
    PlotType.ALERTNESS to defaultMetric,
    PlotType.DROWSY_SCORE to defaultMetric,
    PlotType.ARTEFACT_SCORE to defaultMetric,
    PlotType.QUALITY_CONFIDENCE to defaultMetric,
    PlotType.EFFORTFUL_FOCUS_SCORE to defaultMetric,
    PlotType.MIND_WANDERING_SCORE to defaultMetric,
    PlotType.ESENSE_MEDITATION to defaultMetric,
    PlotType.ESENSE_ATTENTION to defaultMetric,
  )
}
