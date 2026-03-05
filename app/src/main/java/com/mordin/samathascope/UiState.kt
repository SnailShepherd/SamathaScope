package com.mordin.samathascope

/**
 * What to plot (RAW waveform or a derived metric).
 *
 * NOTE: Feedback can use any of the *metric* entries; RAW is only meaningful for the plot.
 */
enum class PlotType {
  RAW,
  SAMATHA_S,
  ARTEFACT_A,
  RAI,
  ESENSE_MEDITATION,
  ESENSE_ATTENTION,
}

data class BondedDevice(
  val mac: String,
  val display: String,
)

data class UiState(
  val btPermissionGranted: Boolean = false,
  val bondedDevices: List<BondedDevice> = emptyList(),
  val selectedDeviceMac: String? = null,
  val connected: Boolean = false,

  // Stream / telemetry
  val poorSignal: Int = 255,
  val attention: Int = 0,
  val meditation: Int = 0,
  val samplesPerSecond: Float = 0f,
  val streamStallMs: Long = 0,

  // Raw preview (latest)
  val rawPreview: List<Int> = emptyList(),

  // Metric plot (history)
  val plotType: PlotType = PlotType.RAW,
  val plotHistory: List<Float> = emptyList(),
  val plotHistorySeconds: Int = 60, // how much history we show for metric plots

  // Session state
  val sessionRunning: Boolean = false,
  val sessionPaused: Boolean = false,
  val calibrating: Boolean = false,
  val calibrationRemainingSec: Int = 0,
  val sessionElapsedSec: Int = 0,

  // Scores / features
  val rai: Float = 0f,
  val scoreS: Float = 0f,
  val scoreA: Float = 0f,

  // Averages / stats (simple, like EEG Meditation app)
  val avgS: Float = 0f,
  val timeSge80Sec: Int = 0,

  // Artefact breakdown
  val aContact: Float = 0f,
  val aLine: Float = 0f,
  val aEmg: Float = 0f,
  val aBlink: Float = 0f,
  val aStall: Float = 0f,

  // Feedback selection
  val feedbackMetric: PlotType = PlotType.SAMATHA_S,

  // Controls
  val invertReward: Boolean = false,
  val artefactsReduceScore: Boolean = true,
  val crackleEnabled: Boolean = true,
  val gamma: Float = 1.6f,
  val gMinDb: Int = -30, // slightly louder default than before; you can still dial it down
  val gMaxDb: Int = -3,
  val crackleIntensity: Float = 0.6f,

  // Audio diagnostics
  val audioRunning: Boolean = false,
  val audioMuted: Boolean = true,
  val audioBaseDb: Float = -120f,

  // Recording
  val recordingEnabled: Boolean = false,
  val lastRecordingPath: String? = null,
)
