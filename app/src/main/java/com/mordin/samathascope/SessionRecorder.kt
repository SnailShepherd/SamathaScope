package com.mordin.samathascope

import android.content.Context
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecordedFeatureRow(
  val timestampMs: Long,
  val poorSignal: Int,
  val notch50Enabled: Boolean,
  val features: EegFeatures,
  val zScores: FeatureZScores,
  val quality: QualityMetrics,
  val artefactCalibrationProfile: ArtefactCalibrationProfile,
  val rawProbabilities: StateProbabilities,
  val smoothedProbabilities: StateProbabilities,
  val rawStateLabel: StateLabel,
  val displayedStateLabel: StateLabel,
  val drowsinessEvidence: DrowsinessEvidence,
  val displayedDrowsyScore: Float,
  val alertness: Float,
  val control: Float,
  val settledness: Float,
  val meditationProxy: Float,
  val feedbackMetric: PlotType,
  val feedbackValue: Float,
  val selectedGameId: GameId,
  val gameSignals: GameSignalSnapshot,
  val gameSummary: String,
)

fun recordedFeatureCsvHeader(): String {
  return listOf(
    "timestamp_ms",
    "window_start_ms",
    "window_end_ms",
    "poor_signal",
    "notch50_enabled",
    "p_theta",
    "p_alpha",
    "p_beta",
    "p_hf",
    "p_4_13",
    "p_4_30",
    "log_theta",
    "log_alpha",
    "log_beta",
    "log_hf",
    "rel_theta",
    "rel_alpha",
    "rel_beta",
    "rel_hf",
    "tbr",
    "tar",
    "abr",
    "theta_peak_hz",
    "alpha_peak_hz",
    "spectral_entropy",
    "emg",
    "hf_ratio",
    "blink_rate_hz",
    "clip_fraction",
    "line_noise_ratio",
    "max_gap_ms",
    "z_log_beta",
    "z_tbr",
    "z_tar",
    "z_abr",
    "z_entropy",
    "z_emg",
    "a_contact",
    "a_line",
    "a_emg",
    "a_blink",
    "a_clip",
    "a_stall",
    "artefact_blink_norm_hz",
    "artefact_emg_norm_hf_ratio",
    "artefact_eye_motion_peak_hz",
    "artefact_jaw_peak_hf_ratio",
    "artefact_frown_peak_hf_ratio",
    "artefact_score",
    "quality_confidence",
    "d_tar_term",
    "d_tbr_term",
    "d_entropy_term",
    "d_abr_term",
    "d_logit",
    "raw_contaminated",
    "raw_drowsy",
    "raw_settled",
    "raw_effortful_focus",
    "raw_mind_wandering",
    "raw_uncertain",
    "smoothed_contaminated",
    "smoothed_drowsy",
    "smoothed_settled",
    "smoothed_effortful_focus",
    "smoothed_mind_wandering",
    "smoothed_uncertain",
    "displayed_drowsy_score",
    "alertness",
    "control",
    "settledness",
    "meditation_proxy",
    "raw_state_label",
    "displayed_state_label",
    "feedback_metric",
    "feedback_value",
    "selected_game_id",
    "game_stability",
    "game_drift",
    "game_noise",
    "game_fatigue",
    "game_precision",
    "game_correction_pulse",
    "game_summary",
  ).joinToString(",")
}

fun RecordedFeatureRow.toCsvRow(): String {
  fun float(value: Float): String = String.format(Locale.US, "%.6f", value)

  return listOf(
    timestampMs,
    features.windowStartMs,
    features.windowEndMs,
    poorSignal,
    notch50Enabled,
    float(features.pTheta),
    float(features.pAlpha),
    float(features.pBeta),
    float(features.pHf),
    float(features.p4To13),
    float(features.p4To30),
    float(features.logTheta),
    float(features.logAlpha),
    float(features.logBeta),
    float(features.logHf),
    float(features.relativeTheta),
    float(features.relativeAlpha),
    float(features.relativeBeta),
    float(features.relativeHf),
    float(features.tbr),
    float(features.tar),
    float(features.abr),
    float(features.thetaPeakHz),
    float(features.alphaPeakHz),
    float(features.spectralEntropy),
    float(features.emg),
    float(features.hfRatio),
    float(features.blinkRateHz),
    float(features.clipFraction),
    float(features.lineNoiseRatio),
    features.maxGapMs,
    float(zScores.logBeta),
    float(zScores.tbr),
    float(zScores.tar),
    float(zScores.abr),
    float(zScores.entropy),
    float(zScores.emg),
    float(quality.contact),
    float(quality.lineNoise),
    float(quality.emg),
    float(quality.blink),
    float(quality.clip),
    float(quality.stall),
    float(artefactCalibrationProfile.blinkNormalizationHz),
    float(artefactCalibrationProfile.emgNormalizationHfRatio),
    float(artefactCalibrationProfile.eyeMotionBlinkPeakHz),
    float(artefactCalibrationProfile.jawClenchHfPeakRatio),
    float(artefactCalibrationProfile.frownHfPeakRatio),
    float(quality.artefactScore),
    float(quality.qualityConfidence),
    float(drowsinessEvidence.tarContribution),
    float(drowsinessEvidence.tbrContribution),
    float(drowsinessEvidence.entropyContribution),
    float(drowsinessEvidence.abrContribution),
    float(drowsinessEvidence.logit),
    float(rawProbabilities.contaminated),
    float(rawProbabilities.drowsy),
    float(rawProbabilities.settled),
    float(rawProbabilities.effortfulFocus),
    float(rawProbabilities.mindWandering),
    float(rawProbabilities.uncertain),
    float(smoothedProbabilities.contaminated),
    float(smoothedProbabilities.drowsy),
    float(smoothedProbabilities.settled),
    float(smoothedProbabilities.effortfulFocus),
    float(smoothedProbabilities.mindWandering),
    float(smoothedProbabilities.uncertain),
    float(displayedDrowsyScore),
    float(alertness),
    float(control),
    float(settledness),
    float(meditationProxy),
    rawStateLabel.name,
    displayedStateLabel.name,
    feedbackMetric.name,
    float(feedbackValue),
    selectedGameId.name,
    float(gameSignals.stability),
    float(gameSignals.drift),
    float(gameSignals.noise),
    float(gameSignals.fatigue),
    float(gameSignals.precision),
    float(gameSignals.correctionPulse),
    gameSummary.replace(',', ';'),
  ).joinToString(",")
}

class SessionRecorder(private val ctx: Context) {

  lateinit var sessionDir: File
    private set

  private var rawOut: BufferedOutputStream? = null
  private var featOut: PrintWriter? = null

  fun start(sampleRateHz: Int) {
    val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    sessionDir = File(ctx.getExternalFilesDir(null), "sessions/$ts")
    sessionDir.mkdirs()

    File(sessionDir, "meta.txt").writeText(
      "sampleRateHz=$sampleRateHz\nformat=raw16le\n"
    )

    rawOut = BufferedOutputStream(FileOutputStream(File(sessionDir, "raw.raw16le")))
    featOut = PrintWriter(File(sessionDir, "features.csv")).apply {
      println(recordedFeatureCsvHeader())
      flush()
    }
  }

  fun appendRaw(v: Short) {
    val out = rawOut ?: return
    out.write(v.toInt() and 0xFF)
    out.write((v.toInt() shr 8) and 0xFF)
  }

  fun appendFeatures(row: RecordedFeatureRow) {
    val out = featOut ?: return
    out.println(row.toCsvRow())
    out.flush()
  }

  fun stop() {
    try { rawOut?.flush() } catch (_: Throwable) {}
    try { rawOut?.close() } catch (_: Throwable) {}
    rawOut = null
    try { featOut?.flush() } catch (_: Throwable) {}
    try { featOut?.close() } catch (_: Throwable) {}
    featOut = null
  }
}
