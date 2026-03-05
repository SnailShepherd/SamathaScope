package com.mordin.samathascope

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class EegFeatures(
  val rai: Float,
  val alphaPower: Float,
  val hiPower: Float,
  val total145: Float,
  val line50: Float,
  val emgFrac: Float,
  val blinkScore: Float,
  val clipFrac: Float,
)

/**
 * Raw-first feature extraction using FFT band power on sliding windows.
 *
 * - sampleRate: MindWave Mobile 2 advertises 512 Hz sampling.
 * - window: 1024 samples (~2.0 s)
 * - hop: 128 samples (~0.25 s)  => 4 Hz updates
 */
class EegProcessor(
  private val sampleRateHz: Int,
  private val windowSize: Int = 1024,
  private val hopSize: Int = 128,
) {
  private val ring = IntArray(windowSize * 4) // ~8 seconds
  private var ringPos = 0
  private var totalSamples = 0
  private var hopCounter = 0

  private val previewSize = 512
  private val preview = IntArray(previewSize)
  private var previewPos = 0

  fun reset() {
    ringPos = 0
    totalSamples = 0
    hopCounter = 0
    previewPos = 0
  }

  fun rawPreview(): List<Int> {
    val out = IntArray(previewSize)
    val start = (previewPos - previewSize + previewSize) % previewSize
    for (i in 0 until previewSize) {
      out[i] = preview[(start + i) % previewSize]
    }
    return out.toList()
  }

  fun pushRaw(sample: Int, timestampMs: Long): EegFeatures? {
    ring[ringPos] = sample
    ringPos = (ringPos + 1) % ring.size
    totalSamples++

    preview[previewPos] = sample
    previewPos = (previewPos + 1) % previewSize

    hopCounter++
    if (totalSamples < windowSize) return null
    if (hopCounter < hopSize) return null
    hopCounter = 0

    // Extract last windowSize samples from ring (in order)
    val window = FloatArray(windowSize)
    val start = (ringPos - windowSize + ring.size) % ring.size
    for (i in 0 until windowSize) {
      window[i] = ring[(start + i) % ring.size].toFloat()
    }
    return computeFeatures(window)
  }

  private fun computeFeatures(x: FloatArray): EegFeatures {
    // Hanning window
    for (i in x.indices) {
      val w = 0.5f - 0.5f * cos(2.0 * Math.PI * i / (x.size - 1)).toFloat()
      x[i] *= w
    }

    // FFT (in-place into re/im)
    val re = x.copyOf()
    val im = FloatArray(x.size)

    fftRadix2(re, im)

    val n = x.size
    val df = sampleRateHz.toFloat() / n.toFloat()

    fun bin(freq: Float): Int = (freq / df).toInt().coerceIn(0, n/2)

    fun bandPower(f1: Float, f2: Float): Float {
      val b1 = bin(f1)
      val b2 = bin(f2)
      var s = 0.0f
      for (k in b1..b2) {
        val p = re[k]*re[k] + im[k]*im[k]
        s += p
      }
      return s
    }

    val alpha = bandPower(8f, 12f)
    val hi = bandPower(20f, 45f)
    val total145 = bandPower(1f, 45f)
    val line = bandPower(49f, 51f)

    val eps = 1e-6f
    val rai = ln((alpha + eps) / (hi + eps)).toFloat()
    val emgFrac = (hi / (total145 + eps)).coerceIn(0f, 1f)

    // Blink/transient proxy: derivative outliers (very crude, but works as "movement indicator")
    var mean = 0f
    for (i in 1 until x.size) mean += (x[i] - x[i-1])
    mean /= (x.size - 1)

    var varD = 0f
    for (i in 1 until x.size) {
      val d = (x[i] - x[i-1]) - mean
      varD += d*d
    }
    varD /= (x.size - 1)
    val sd = sqrt(varD + eps)

    var spikes = 0
    val thr = 6f * sd
    for (i in 1 until x.size) {
      val d = abs((x[i] - x[i-1]) - mean)
      if (d > thr) spikes++
    }
    val blinkScore = (spikes / 50f).coerceIn(0f, 1f) // heuristic scaling

    // Clipping proxy (MindWave often sits around ±2048-ish, but we don't hardcode too tightly)
    var clips = 0
    for (v in x) if (abs(v) > 1900f) clips++
    val clipFrac = (clips.toFloat() / x.size.toFloat()).coerceIn(0f, 1f)

    return EegFeatures(
      rai = rai,
      alphaPower = alpha,
      hiPower = hi,
      total145 = total145,
      line50 = line,
      emgFrac = emgFrac,
      blinkScore = blinkScore,
      clipFrac = clipFrac
    )
  }

  /**
   * Iterative radix-2 Cooley-Tukey FFT.
   * - re/im length must be power of 2.
   */
  private fun fftRadix2(re: FloatArray, im: FloatArray) {
    val n = re.size
    var j = 0
    for (i in 1 until n) {
      var bit = n shr 1
      while (j and bit != 0) {
        j = j xor bit
        bit = bit shr 1
      }
      j = j xor bit
      if (i < j) {
        val tr = re[i]; re[i] = re[j]; re[j] = tr
        val ti = im[i]; im[i] = im[j]; im[j] = ti
      }
    }

    var len = 2
    while (len <= n) {
      val ang = (-2.0 * Math.PI / len)
      val wlenRe = cos(ang).toFloat()
      val wlenIm = sin(ang).toFloat()
      var i = 0
      while (i < n) {
        var wRe = 1f
        var wIm = 0f
        for (k in 0 until (len/2)) {
          val uRe = re[i + k]
          val uIm = im[i + k]
          val vRe = re[i + k + len/2] * wRe - im[i + k + len/2] * wIm
          val vIm = re[i + k + len/2] * wIm + im[i + k + len/2] * wRe

          re[i + k] = uRe + vRe
          im[i + k] = uIm + vIm
          re[i + k + len/2] = uRe - vRe
          im[i + k + len/2] = uIm - vIm

          val nextWRe = wRe * wlenRe - wIm * wlenIm
          val nextWIm = wRe * wlenIm + wIm * wlenRe
          wRe = nextWRe
          wIm = nextWIm
        }
        i += len
      }
      len = len shl 1
    }
  }
}
