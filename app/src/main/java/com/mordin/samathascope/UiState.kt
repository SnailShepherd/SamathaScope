package com.mordin.samathascope

enum class PlotType {
  RAW,
  SAMATHA_SCORE,
  ARTEFACT_SCORE,
  RELAXED_ALERTNESS_INDEX,
  ESENSE_MEDITATION,
  ESENSE_ATTENTION,
}

enum class AppTab {
  DASHBOARD,
  SIGNALS,
  GAME,
  LEARN,
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

data class GameState(
  val metric: PlotType = PlotType.SAMATHA_SCORE,
  val altitude: Float = 0.5f,
  val velocity: Float = 0f,
)

data class GameHudState(
  val metricValuePercent: Int = 0,
  val artefactPercent: Int = 0,
  val poorSignal: Int = 255,
  val elapsedSeconds: Int = 0,
  val batteryPercent: Int? = null,
)

data class UiState(
  val selectedTab: AppTab = AppTab.DASHBOARD,
  val settingsPanelVisible: Boolean = false,

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

  val plotType: PlotType = PlotType.RAW,
  val plotHistory: List<Float> = emptyList(),
  val plotSettings: Map<PlotType, PlotSettings> = defaultPlotSettings(),

  val sessionRunning: Boolean = false,
  val sessionPaused: Boolean = false,
  val calibrating: Boolean = false,
  val calibrationRemainingSec: Int = 0,
  val sessionElapsedSec: Int = 0,

  val relaxedAlertnessIndex: Float = 0f,
  val samathaScore: Float = 0f,
  val artefactScore: Float = 0f,

  val avgSamathaScore: Float = 0f,
  val timeSamathaOver80Seconds: Int = 0,

  val artefactContact: Float = 0f,
  val artefactLine: Float = 0f,
  val artefactEmg: Float = 0f,
  val artefactBlink: Float = 0f,
  val artefactStall: Float = 0f,

  val feedbackMetric: PlotType = PlotType.SAMATHA_SCORE,

  val invertReward: Boolean = false,
  val artefactsReduceScore: Boolean = true,
  val crackleEnabled: Boolean = true,
  val gamma: Float = 1.6f,
  val gMinDb: Int = -30,
  val gMaxDb: Int = -3,
  val crackleIntensity: Float = 0.6f,

  val audioRunning: Boolean = false,
  val audioMuted: Boolean = true,
  val audioBaseDb: Float = -120f,

  val recordingEnabled: Boolean = false,
  val lastRecordingPath: String? = null,

  val gameState: GameState = GameState(),
  val gameHudState: GameHudState = GameHudState(),
)

fun defaultPlotSettings(): Map<PlotType, PlotSettings> {
  val defaultRaw = PlotSettings(
    windowSeconds = 5,
    yMin = -1200f,
    yMax = 1200f,
    isUserLocked = false
  )
  val defaultMetric = PlotSettings(
    windowSeconds = 300,
    yMin = 0f,
    yMax = 100f,
    isUserLocked = false
  )
  return mapOf(
    PlotType.RAW to defaultRaw,
    PlotType.SAMATHA_SCORE to defaultMetric,
    PlotType.ARTEFACT_SCORE to defaultMetric,
    PlotType.RELAXED_ALERTNESS_INDEX to defaultMetric,
    PlotType.ESENSE_MEDITATION to defaultMetric,
    PlotType.ESENSE_ATTENTION to defaultMetric,
  )
}
