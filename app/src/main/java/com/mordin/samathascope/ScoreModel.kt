package com.mordin.samathascope

/**
 * Artefact breakdown used both for:
 * - audio crackle intensity (A)
 * - diagnostics UI (separate bars)
 */
data class ArtefactBreakdown(
  val contact: Float,
  val line: Float,
  val emg: Float,
  val blink: Float,
  val stall: Float,
  val totalA: Float,
  val stallMs: Long,
)

/**
 * Turns raw-derived features into:
 * - artefact score A in [0, 1]
 * - training score S in [0, 1]
 *
 * Design goal: be robust on a single dry frontal electrode.
 * That means we treat high-frequency power (20-45 Hz) mostly as muscle/EMG contamination,
 * and we explicitly penalise it.
 */
class ScoreModel {

  /**
   * Calibration stores percentiles for normalising RAI to [0,1] for your baseline.
   *
   * Why percentiles (P10/P90) instead of mean/std?
   * - percentiles are less fragile with weird outliers (blinks, contact glitches)
   * - you do not need to assume any distribution
   */
  private var cal: Calibration? = null

  fun reset() { cal = null }

  fun setCalibration(c: Calibration) { cal = c }

  /**
   * Normalise RAI into [0,1] using calibration percentiles.
   * If calibration is missing, returns 0.5 (neutral).
   */
  fun normaliseRai(rai: Float): Float {
    val c = cal ?: return 0.5f
    return ((rai - c.raiP10) / (c.raiP90 - c.raiP10)).coerceIn(0f, 1f)
  }

  /**
   * Compute artefact score A in [0,1].
   *
   * Components:
   * - contact telemetry (PoorSignal): is the electrode connected?
   * - line noise around 50 Hz (diagnostics only)
   * - EMG proxy: fraction of 20-45 Hz power in total 1-45 Hz (jaw/forehead tension)
   * - blink/transient proxy: derivative spikes
   * - packet stalls: Bluetooth hiccups
   *
   * Weighting in totalA:
   * - contact 0.40
   * - emg 0.30
   * - blink 0.20
   * - stall 0.10
   */
  fun artefacts(poorSignal: Int, features: EegFeatures, stallMs: Long): ArtefactBreakdown {
    // PoorSignal is 0 good, higher means worse. NeuroSky uses 0..255.
    val contact = (poorSignal / 200f).coerceIn(0f, 1f)

    // 50 Hz line noise ratio relative to 1-45 Hz power (diagnostics only).
    val line = (features.line50 / (features.total145 + 1e-6f)).coerceIn(0f, 1f) * 3f
    val lineC = line.coerceIn(0f, 1f)

    // EMG proxy already in [0,1] (20-45 / 1-45).
    val emg = features.emgFrac.coerceIn(0f, 1f)

    // Blink/transient proxy already [0,1].
    val blink = features.blinkScore.coerceIn(0f, 1f)

    // Stall: map 0..500ms to 0..1 (stalls are audible and should be obvious).
    val stall = (stallMs / 500f).coerceIn(0f, 1f)

    val total = (
      0.40f * contact +
      0.30f * emg +
      0.20f * blink +
      0.10f * stall
    ).coerceIn(0f, 1f)

    return ArtefactBreakdown(
      contact = contact,
      line = lineC,
      emg = emg,
      blink = blink,
      stall = stall,
      totalA = total,
      stallMs = stallMs
    )
  }

  /**
   * Training score S in [0,1].
   *
   * Base is normalised RAI:
   *   RAI = ln(alpha(8-12) / hi(20-45))
   *
   * Then we optionally apply an artefact penalty.
   */
  fun scoreS(rai: Float, artefactA: Float, artefactsReduceScore: Boolean): Float {
    var s = normaliseRai(rai)

    if (artefactsReduceScore) {
      // Keep some gradient so feedback does not die completely at high artefact.
      s *= (1f - 0.7f * artefactA).coerceIn(0.05f, 1f)
    }
    return s.coerceIn(0f, 1f)
  }
}
