package com.mordin.samathascope

import android.content.Context
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Minimal session recorder:
 * - raw samples to raw16le file (signed 16-bit little-endian)
 * - features to CSV
 *
 * Stored in app-specific external files dir (no permissions needed).
 */
class SessionRecorder(private val ctx: Context) {

  lateinit var sessionDir: File
    private set

  private var rawOut: BufferedOutputStream? = null
  private var featOut: PrintWriter? = null

  fun start(sampleRateHz: Int) {
    val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    sessionDir = File(ctx.getExternalFilesDir(null), "sessions/$ts")
    sessionDir.mkdirs()

    // Meta
    File(sessionDir, "meta.txt").writeText(
      "sampleRateHz=$sampleRateHz\nformat=raw16le\n"
    )

    rawOut = BufferedOutputStream(FileOutputStream(File(sessionDir, "raw.raw16le")))
    featOut = PrintWriter(File(sessionDir, "features.csv")).apply {
      println("timestamp_ms,rai,score_s,score_a,poor_signal,a_contact,a_line,a_emg,a_blink,a_stall")
      flush()
    }
  }

  fun appendRaw(v: Short) {
    val out = rawOut ?: return
    // little-endian
    out.write(v.toInt() and 0xFF)
    out.write((v.toInt() shr 8) and 0xFF)
  }

  fun appendFeatures(
    timestampMs: Long,
    rai: Float,
    scoreS: Float,
    scoreA: Float,
    poorSignal: Int,
    aContact: Float,
    aLine: Float,
    aEmg: Float,
    aBlink: Float,
    aStall: Float,
  ) {
    val out = featOut ?: return
    out.println(
      listOf(
        timestampMs,
        "%.6f".format(rai),
        "%.6f".format(scoreS),
        "%.6f".format(scoreA),
        poorSignal,
        "%.6f".format(aContact),
        "%.6f".format(aLine),
        "%.6f".format(aEmg),
        "%.6f".format(aBlink),
        "%.6f".format(aStall),
      ).joinToString(",")
    )
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
