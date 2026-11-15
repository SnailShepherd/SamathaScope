package com.mordin.samathascope

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Session folders, metadata and export. Sessions live under <app files>/sessions/<yyyyMMdd_HHmmss>/.

private fun appFilesRoot(context: Context): File = context.getExternalFilesDir(null) ?: context.filesDir

fun sessionsRoot(context: Context): File = File(appFilesRoot(context), "sessions")

/** The most recently started recorded session, or null if there is none. */
fun latestSessionDir(context: Context): File? = latestSessionDir(sessionsRoot(context))

internal fun latestSessionDir(root: File): File? =
  root.listFiles { file -> file.isDirectory && !file.listFiles().isNullOrEmpty() }
    ?.maxByOrNull { it.name } // Names are timestamps, so the greatest is the newest.

/**
 * Zips [sessionDir] into the app's exports folder (shared with other apps through the FileProvider)
 * and returns the zip. Exports older than [EXPORT_MAX_AGE_MS] are deleted; newer ones are kept,
 * because the app a session was shared to may read the file only later (e.g. when an email is sent).
 */
fun zipSessionForSharing(context: Context, sessionDir: File): File {
  val exportsDir = File(appFilesRoot(context), EXPORTS_DIR)
  pruneOldExports(exportsDir, nowMs = System.currentTimeMillis())
  exportsDir.mkdirs()
  val zip = File(exportsDir, "samathascope_session_${sessionDir.name}.zip")
  zipDirectory(sessionDir, zip)
  return zip
}

internal const val EXPORTS_DIR = "exports"
internal const val EXPORT_MAX_AGE_MS = 24L * 60L * 60L * 1000L

internal fun pruneOldExports(exportsDir: File, nowMs: Long, maxAgeMs: Long = EXPORT_MAX_AGE_MS) {
  exportsDir.listFiles { file -> file.isFile && nowMs - file.lastModified() > maxAgeMs }
    ?.forEach { it.delete() }
}

/** Writes every file in [dir] (non-recursive) into [out], under a folder named after [dir]. */
internal fun zipDirectory(dir: File, out: File) {
  ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { zip ->
    dir.listFiles { file -> file.isFile }?.sortedBy { it.name }?.forEach { file ->
      zip.putNextEntry(ZipEntry("${dir.name}/${file.name}"))
      file.inputStream().use { it.copyTo(zip) }
      zip.closeEntry()
    }
  }
}

internal fun formatMetadata(metadata: Map<String, String>): String =
  metadata.entries.joinToString(separator = "") { (key, value) -> "$key=${value.replace('\n', ' ')}\n" }

private fun isoTimestamp(wallMs: Long): String =
  SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date(wallMs))

internal fun sessionStartMetadata(appVersion: String, startedAtWallMs: Long, state: UiState): Map<String, String> =
  linkedMapOf(
    "app_version" to appVersion,
    "started_at" to isoTimestamp(startedAtWallMs),
    "analysis_window_s" to "$ANALYSIS_WINDOW_SECONDS",
    "session_mode" to state.sessionMode.storageValue,
    "notch_enabled" to "${state.notch50Enabled}",
    "feedback_metric" to state.feedbackMetric.name,
    "audio_enabled" to "${state.audioEnabled}",
    "lost_samples" to "held_previous_value",
  )

/** The clean baseline the session is scored against, written when calibration ends. */
internal fun baselineMetadata(baseline: Calibration?, skipped: Boolean): Map<String, String> {
  val out = linkedMapOf(
    "calibration" to if (skipped) "skipped" else "completed",
    "baseline" to if (baseline == null) "none_provisional" else "calibration",
  )
  baseline ?: return out
  listOf(
    "log_beta" to baseline.logBeta,
    "tbr" to baseline.tbr,
    "tar" to baseline.tar,
    "abr" to baseline.abr,
    "entropy" to baseline.entropy,
    "emg" to baseline.emg,
  ).forEach { (name, stat) ->
    out["baseline_${name}_median"] = String.format(Locale.US, "%.6f", stat.median)
    out["baseline_${name}_mad"] = String.format(Locale.US, "%.6f", stat.mad)
  }
  return out
}

internal fun sessionEndMetadata(endedAtWallMs: Long, durationMs: Long): Map<String, String> =
  linkedMapOf(
    "ended_at" to isoTimestamp(endedAtWallMs),
    "duration_s" to "${(durationMs / 1000L).coerceAtLeast(0L)}",
  )

fun appVersionName(context: Context): String {
  return try {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
      @Suppress("DEPRECATION")
      context.packageManager.getPackageInfo(context.packageName, 0)
    }
    info.versionName ?: "unknown"
  } catch (_: Exception) {
    "unknown"
  }
}
